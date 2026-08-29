package com.apexstudio.app.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioWaveformData(
    val samples: FloatArray,
    val durationMs: Long,
    val peakLevel: Float
)

class MediaAnalyzer {

    suspend fun analyzeAudioWaveform(uri: String, context: Context, sampleCount: Int = 200): AudioWaveformData =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, android.net.Uri.parse(uri))
                val trackIndex = findAudioTrack(extractor)
                if (trackIndex < 0) {
                    return@withContext AudioWaveformData(FloatArray(sampleCount), 0L, 0f)
                }
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val durationUs = format.getLong(MediaFormat.KEY_DURATION) ?: 0L
                val durationMs = durationUs / 1000

                val samples = FloatArray(sampleCount)
                val bufferSize = 4096
                val byteBuffer = java.nio.ByteBuffer.allocate(bufferSize)
                var totalSamples = 0
                var sampleIndex = 0

                while (extractor.sampleSize > 0 && sampleIndex < sampleCount) {
                    byteBuffer.clear()
                    val readSize = extractor.readSampleData(byteBuffer, 0)
                    if (readSize <= 0) break
                    var i = 0
                    while (i + 1 < readSize && sampleIndex < sampleCount) {
                        val sample = byteBuffer.short.toFloat() / 32768f
                        samples[sampleIndex] = kotlin.math.abs(sample)
                        sampleIndex++
                        i += 2
                    }
                    extractor.advance()
                }

                while (sampleIndex < sampleCount) {
                    samples[sampleIndex] = 0f
                    sampleIndex++
                }

                val peak = if (samples.isNotEmpty()) samples.maxOrNull() ?: 0f else 0f
                AudioWaveformData(samples, durationMs, peak)
            } catch (e: Exception) {
                AudioWaveformData(FloatArray(sampleCount), 0L, 0f)
            } finally {
                try { extractor.release() } catch (_: Exception) { }
            }
        }

    suspend fun getMediaDuration(uri: String, context: Context): Long =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L
                retriever.release()
                duration
            } catch (e: Exception) {
                0L
            }
        }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }
}
