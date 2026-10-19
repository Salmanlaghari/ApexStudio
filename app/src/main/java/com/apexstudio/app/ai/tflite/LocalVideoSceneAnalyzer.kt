package com.apexstudio.app.ai.tflite

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.LocalMediaSourcePolicy
import com.apexstudio.app.ai.OfflineVideoFrameDecoder
import com.apexstudio.app.ai.autoreframe.AutoCropSuggestion
import com.apexstudio.app.ai.autoreframe.SignalAutoCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** A user-reviewable candidate trim expressed in the source video's millisecond timeline. */
data class SmartTrimSuggestion(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val score: Float,
    val rationale: String
)

/** One detected source-time scene boundary; it is not a learned-model prediction. */
data class SceneCutSuggestion(val id: String, val timestampMs: Long, val changeScore: Float)

data class LocalSceneAnalysisResult(
    val cuts: List<SceneCutSuggestion>,
    val smartTrims: List<SmartTrimSuggestion>,
    val autoCrop: AutoCropSuggestion?,
    val sampledFrameCount: Int
)

data class LocalSceneSuggestions(
    val cuts: List<SceneCutSuggestion>,
    val smartTrims: List<SmartTrimSuggestion>
)

/** Pure mapper kept separate from Android decoding so source bounds are unit-testable. */
object LocalSceneSuggestionMapper {
    fun map(analysis: SceneAnalysis, sourceStartMs: Long, sourceEndMs: Long): LocalSceneSuggestions {
        require(sourceStartMs >= 0L && sourceEndMs > sourceStartMs)
        val cuts = analysis.boundaries
            .filter { it.timestampMs > sourceStartMs && it.timestampMs < sourceEndMs &&
                it.changeScore.isFinite() && it.changeScore in 0f..1f }
            .sortedWith(compareBy<SceneBoundary> { it.timestampMs }.thenByDescending { it.changeScore })
            .distinctBy { it.timestampMs }
            .map { SceneCutSuggestion("cut-${it.timestampMs}", it.timestampMs, it.changeScore) }
        val trims = analysis.highlights.mapNotNull { highlight ->
            // Validate before subtraction: malformed persisted/native-adapter values can otherwise
            // wrap a reversed interval into a positive duration and reach the review UI.
            if (highlight.startMs < 0L || highlight.endMs <= highlight.startMs ||
                !highlight.score.isFinite() || highlight.score !in 0f..1f) return@mapNotNull null
            val start = highlight.startMs.coerceAtLeast(sourceStartMs)
            val end = highlight.endMs.coerceAtMost(sourceEndMs)
            if (end <= start || end - start < 100L) null else SmartTrimSuggestion(
                id = "trim-$start-$end",
                startMs = start,
                endMs = end,
                score = highlight.score,
                rationale = "Heuristic highlight window: ${highlight.reason}; review the frames before applying."
            )
        }.sortedByDescending { it.score }.distinctBy { it.startMs to it.endMs }
        return LocalSceneSuggestions(cuts, trims)
    }
}

/**
 * Decodes real frames on-device through Android's platform video decoder and passes sampled luma
 * into deterministic histogram/frame-difference heuristics. No model inference, network request,
 * content moderation, or automatic edit is performed. All proposed actions remain review-gated.
 */
class LocalVideoSceneAnalyzer(
    private val sampleIntervalMs: Long = 1_000L,
    private val maximumSamples: Int = 360,
    private val lumaWidth: Int = 32,
    private val lumaHeight: Int = 18
) {
    init {
        require(sampleIntervalMs > 0 && maximumSamples in 2..10_000 && lumaWidth > 0 && lumaHeight > 0)
        require(lumaWidth.toLong() * lumaHeight.toLong() <= 16_777_216L / maximumSamples) {
            "Configured luma samples exceed the safe analysis memory limit"
        }
    }

    suspend fun analyze(
        context: Context,
        uri: String,
        sourceStartMs: Long,
        sourceEndMs: Long,
        cancellation: AiCancellationToken = AiCancellationToken(),
        onProgress: (Float) -> Unit = {}
    ): LocalSceneAnalysisResult = withContext(Dispatchers.IO) {
        require(sourceStartMs >= 0L && sourceEndMs > sourceStartMs) { "Invalid source trim range" }
        require(sourceEndMs - 1L <= Long.MAX_VALUE / 1_000L) { "Source timestamps exceed the platform decoder range" }
        cancellation.throwIfCancelled()
        currentCoroutineContext().ensureActive()
        val sampleTimes = LocalSceneSampling.timestamps(sourceStartMs, sourceEndMs, sampleIntervalMs, maximumSamples)
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<LumaFrame>(sampleTimes.size)
        var firstDecodedFrame: LumaFrame? = null
        var sourceAspect = 1f
        try {
            val parsedUri = Uri.parse(uri)
            require(LocalMediaSourcePolicy.accepts(parsedUri.scheme, parsedUri.authority)) {
                "Offline analysis accepts local file paths and content/resource providers, not network URLs or remote file authorities."
            }
            val localUri = if (parsedUri.scheme == null) Uri.fromFile(File(uri)) else parsedUri
            retriever.setDataSource(context, localUri)
            sampleTimes.forEachIndexed { index, timestampMs ->
                cancellation.throwIfCancelled()
                currentCoroutineContext().ensureActive()
                val decoded = OfflineVideoFrameDecoder.decode(retriever, timestampMs * 1_000L, maximumDimension = 640)
                if (decoded != null) {
                    val bitmap = decoded.bitmap
                    try {
                        cancellation.throwIfCancelled()
                        currentCoroutineContext().ensureActive()
                        val frame = bitmap.toLumaFrame(timestampMs, lumaWidth, lumaHeight)
                        frames += frame
                        if (firstDecodedFrame == null) {
                            firstDecodedFrame = frame
                            sourceAspect = decoded.sourceWidth.toFloat() / decoded.sourceHeight
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                cancellation.throwIfCancelled()
                currentCoroutineContext().ensureActive()
                onProgress((index + 1f) / sampleTimes.size.coerceAtLeast(1))
            }
        } finally {
            retriever.release()
        }
        cancellation.throwIfCancelled()
        currentCoroutineContext().ensureActive()
        check(frames.isNotEmpty()) { "No video frames could be decoded for offline analysis" }
        val analysis = LocalVideoAnalysis.analyze(
            frames = frames,
            cancellation = cancellation,
            exclusiveEndMs = sourceEndMs,
        )
        val suggestions = LocalSceneSuggestionMapper.map(analysis, sourceStartMs, sourceEndMs)
        // This is a luma-gradient centroid suggestion, not face/subject detection.
        val crop = firstDecodedFrame?.let {
            SignalAutoCrop.suggest(it, outputAspectRatio = 9f / 16f, sourceAspectRatio = sourceAspect)
        }
        cancellation.throwIfCancelled()
        currentCoroutineContext().ensureActive()
        LocalSceneAnalysisResult(suggestions.cuts, suggestions.smartTrims, crop, frames.size)
    }

    private fun Bitmap.toLumaFrame(timestampMs: Long, targetWidth: Int, targetHeight: Int): LumaFrame {
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            val luma = FloatArray(pixels.size) { index ->
                val pixel = pixels[index]
                (0.2126f * ((pixel ushr 16) and 0xff) +
                    0.7152f * ((pixel ushr 8) and 0xff) +
                    0.0722f * (pixel and 0xff)) / 255f
            }
            return LumaFrame(timestampMs, targetWidth, targetHeight, luma)
        } finally {
            if (scaled !== this) scaled.recycle()
        }
    }
}
