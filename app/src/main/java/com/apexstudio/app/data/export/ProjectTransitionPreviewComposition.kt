package com.apexstudio.app.data.export

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.apexstudio.app.data.gl.PackedTransitionGlEffect
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.domain.model.ClipType
import androidx.media3.common.util.UnstableApi

/**
 * Builds the real multi-sequence player preview for a transition-bearing V1 timeline.
 * Each visual input keeps its own effect graph; main-clip audio is rebuilt as one
 * non-overlapping sequence with a hard cut at each transition midpoint.
 */
@UnstableApi
internal fun buildProjectTransitionPreviewComposition(
    context: Context,
    plan: ProjectTransitionTimeline.Plan,
    outputWidth: Int,
    outputHeight: Int,
    previewEffectsByClipId: Map<String, List<Effect>>
): Composition {
    require(plan.transitions.isNotEmpty()) { "A transition preview needs at least one transition" }
    require(plan.entries.all { it.clip.type == ClipType.VIDEO }) {
        "Multi-input transition preview currently requires video-only main-track clips"
    }

    val videoSequences = plan.entries.map { entry ->
        val clip = entry.clip
        val mediaItem = MediaItem.Builder()
            .setUri(MediaUriResolver.resolvePlayableUri(context, clip.uri))
            .setMediaId("${clip.id}_transition_video")
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(entry.sourceStartMs)
                    .setEndPositionMs(entry.sourceEndMs)
                    .build()
            )
            .build()
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), previewEffectsByClipId[clip.id].orEmpty()))
            .build()
        val startMs = entry.timelineStartMs.coerceAtLeast(0L)
        val gapUs = if (startMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else startMs * 1_000L
        EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addGap(gapUs)
            .addItem(editedItem)
            .build()
    }

    val audioSequence = plan.audioSegments.takeIf { it.isNotEmpty() }?.let { segments ->
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var cursorMs = 0L
        segments.forEach { segment ->
            if (segment.timelineStartMs > cursorMs) {
                val gapMs = segment.timelineStartMs - cursorMs
                val gapUs = if (gapMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else gapMs * 1_000L
                builder.addGap(gapUs)
            }
            val clip = segment.clip
            val mediaItem = MediaItem.Builder()
                .setUri(MediaUriResolver.resolvePlayableUri(context, clip.uri))
                .setMediaId("${clip.id}_transition_audio")
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(segment.sourceStartMs)
                        .setEndPositionMs(segment.sourceEndMs)
                        .build()
                )
                .build()
            val audioProcessors = listOfNotNull(ExportEngine.volumeProcessorFor(clip.volume))
            builder.addItem(
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, emptyList()))
                    .build()
            )
            cursorMs = segment.timelineEndMs
        }
        builder.build()
    }

    val sequences = videoSequences + listOfNotNull(audioSequence)
    return Composition.Builder(sequences)
        .setVideoCompositorSettings(ProjectTransitionCompositorSettings(plan, outputWidth, outputHeight))
        .setEffects(Effects(emptyList(), listOf(PackedTransitionGlEffect(plan))))
        .build()
}

@UnstableApi
internal fun createProjectTransitionPreviewPlayer(context: Context): CompositionPlayer =
    CompositionPlayer.Builder(context)
        .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        .build()
