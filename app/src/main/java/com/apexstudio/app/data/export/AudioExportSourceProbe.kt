package com.apexstudio.app.data.export

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException

/** Reads container tracks, not filename extensions; called during IO preparation only. */
internal object AudioExportSourceProbe {
    fun hasAudio(context: Context, uri: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == null) extractor.setDataSource(uri)
            else extractor.setDataSource(context, parsed, null)
            (0 until extractor.trackCount).any { index ->
                isAudioMime(extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("AudioExportSourceProbe", "Source contains no readable audio", failure)
            false
        } finally {
            extractor.release()
        }
    }

    internal fun isAudioMime(mime: String?): Boolean = mime?.startsWith("audio/") == true
}

internal data class AudioExportSelection(
    val sources: List<ExportEngine.AudioExportSource>,
    val skippedSources: Int
) {
    val warning: String?
        get() = when (skippedSources) {
            0 -> null
            1 -> "Audio saved, but 1 source contained no readable audio and was omitted. Check the saved mix before sharing."
            else -> "Audio saved, but $skippedSources sources contained no readable audio and were omitted. Check the saved mix before sharing."
        }
}

/** Preserve project offsets when silent/image sources are omitted; never concatenate around gaps. */
internal fun selectAudioExportSources(
    sources: List<ExportEngine.AudioExportSource>,
    hasAudio: (String) -> Boolean
): AudioExportSelection {
    val accepted = sources.filter { source -> hasAudio(source.uri) }
    return AudioExportSelection(accepted, sources.size - accepted.size)
}
