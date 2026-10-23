package com.apexstudio.app.data.export

/** A usable video is not proof that every requested audio source was included. */
internal object AudioMixExportNotice {
    const val MIX_FAILED = "Video saved, but the added audio could not be mixed. The saved video does not include the requested added audio. Retry the export before sharing."

    fun forSkippedTracks(skippedTracks: Int): String? = when {
        skippedTracks <= 0 -> null
        skippedTracks == 1 -> "Video saved, but 1 added audio track was skipped (unreadable, empty or outside the exported range). Check the saved audio before sharing."
        else -> "Video saved, but $skippedTracks added audio tracks were skipped (unreadable, empty or outside the exported range). Check the saved audio before sharing."
    }
}
