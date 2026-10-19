package com.apexstudio.app.data.export

import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence

/** A processed source and its project-time placement, independent of source clipping. */
@UnstableApi
internal data class PlacedAudioItem(val item: EditedMediaItem, val timelinePositionMs: Long)

/** Production Media3 structure builder; no decoding or device-codec acceptance is implied. */
@UnstableApi
internal object StandaloneAudioComposition {
    fun build(items: List<PlacedAudioItem>): Composition {
        require(items.isNotEmpty()) { "No audio sources to export" }
        val sequences = items.map { placed ->
            require(placed.item.removeVideo) { "Standalone audio items must remove video" }
            val builder = EditedMediaItemSequence.Builder()
            val offsetUs = ExportAudioTimelineMapping.timelinePositionUs(placed.timelinePositionMs)
            if (offsetUs > 0L) builder.addGap(offsetUs)
            builder.addItem(placed.item).build()
        }
        return Composition.Builder(sequences)
            .experimentalSetForceAudioTrack(true)
            .build()
    }
}
