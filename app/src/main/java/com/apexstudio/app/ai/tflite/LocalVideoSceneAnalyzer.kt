package com.apexstudio.app.ai.tflite

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.apexstudio.app.ai.AiCancellationToken
import com.apexstudio.app.ai.autoreframe.AutoCropSuggestion
import com.apexstudio.app.ai.autoreframe.SignalAutoCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.math.max

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
            .filter { it.timestampMs > sourceStartMs && it.timestampMs < sourceEndMs }
            .map { SceneCutSuggestion("cut-${it.timestampMs}", it.timestampMs, it.changeScore) }
        val trims = analysis.highlights.mapNotNull { highlight ->
            val start = highlight.startMs.coerceAtLeast(sourceStartMs)
            val end = highlight.endMs.coerceAtMost(sourceEndMs)
            if (end - start < 100L) null else SmartTrimSuggestion(
                id = "trim-$start-$end",
                startMs = start,
                endMs = end,
                score = highlight.score,
                rationale = "Heuristic highlight window: ${highlight.reason}; review the frames before applying."
            )
        }.distinctBy { it.startMs to it.endMs }.sortedByDescending { it.score }
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
        require(sampleIntervalMs > 0 && maximumSamples >= 2 && lumaWidth > 0 && lumaHeight > 0)
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
        cancellation.throwIfCancelled()
        val duration = sourceEndMs - sourceStartMs
        val boundedStep = max(sampleIntervalMs, (duration + maximumSamples - 1L) / maximumSamples)
        val timestamps = generateSequence(sourceStartMs) { previous ->
            if (previous > sourceEndMs - boundedStep) null else previous + boundedStep
        }.toMutableList()
        if (timestamps.lastOrNull() != sourceEndMs - 1L && sourceEndMs - 1L > (timestamps.lastOrNull() ?: -1L)) {
            timestamps += sourceEndMs - 1L
        }
        // The duration-derived step caps the count; this final guard also protects unusual arithmetic inputs.
        val sampleTimes = timestamps.distinct().take(maximumSamples)
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<LumaFrame>(sampleTimes.size)
        var firstDecodedFrame: LumaFrame? = null
        var sourceAspect = 1f
        try {
            val parsedUri = Uri.parse(uri)
            val scheme = parsedUri.scheme
            require(scheme == null || scheme.lowercase(java.util.Locale.ROOT) in setOf("content", "file", "android.resource")) {
                "Offline analysis accepts only local content/file URIs; remote media is not fetched."
            }
            val localUri = if (parsedUri.scheme == null) Uri.fromFile(File(uri)) else parsedUri
            retriever.setDataSource(context, localUri)
            sampleTimes.forEachIndexed { index, timestampMs ->
                cancellation.throwIfCancelled()
                val bitmap = retriever.getFrameAtTime(timestampMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    try {
                        val frame = bitmap.toLumaFrame(timestampMs, lumaWidth, lumaHeight)
                        frames += frame
                        if (firstDecodedFrame == null) {
                            firstDecodedFrame = frame
                            sourceAspect = bitmap.width.toFloat() / bitmap.height
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress((index + 1f) / sampleTimes.size.coerceAtLeast(1))
            }
        } finally {
            retriever.release()
        }
        cancellation.throwIfCancelled()
        check(frames.isNotEmpty()) { "No video frames could be decoded for offline analysis" }
        val analysis = LocalVideoAnalysis.analyze(
            frames = frames,
            cancellation = cancellation
        )
        val suggestions = LocalSceneSuggestionMapper.map(analysis, sourceStartMs, sourceEndMs)
        // This is a luma-gradient centroid suggestion, not face/subject detection.
        val crop = firstDecodedFrame?.let {
            SignalAutoCrop.suggest(it, outputAspectRatio = 9f / 16f, sourceAspectRatio = sourceAspect)
        }
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
