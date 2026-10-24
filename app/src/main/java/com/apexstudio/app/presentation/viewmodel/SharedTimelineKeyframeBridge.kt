package com.apexstudio.app.presentation.viewmodel

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.AudioTrack as LegacyAudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe as LegacyKeyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.StickerOverlay as LegacyStickerOverlay
import com.apexstudio.app.domain.model.TextOverlay as LegacyTextOverlay
import com.apexstudio.app.domain.model.timeline.AudioClip as SharedAudioClip
import com.apexstudio.app.domain.model.timeline.AudioTrack as SharedAudioTrack
import com.apexstudio.app.domain.model.timeline.BlendMode
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe as SharedKeyframe
import com.apexstudio.app.domain.model.timeline.OverlayItem
import com.apexstudio.app.domain.model.timeline.OverlayTrack
import com.apexstudio.app.domain.model.timeline.Timeline
import com.apexstudio.app.domain.model.timeline.TextItem
import com.apexstudio.app.domain.model.timeline.TextTrack
import com.apexstudio.app.domain.model.timeline.StickerItem
import com.apexstudio.app.domain.model.timeline.StickerTrack
import com.apexstudio.app.domain.model.timeline.Transform
import com.apexstudio.app.domain.model.timeline.VideoClip
import com.apexstudio.app.domain.model.timeline.VideoTrack
import com.apexstudio.app.domain.model.timeline.XY
import com.apexstudio.app.domain.model.timeline.animationAt
import com.apexstudio.app.domain.model.timeline.animationKeyframeTimes
import com.apexstudio.app.domain.model.timeline.addAnimationKeyframeAt
import com.apexstudio.app.domain.model.timeline.curveAt
import com.apexstudio.app.domain.model.timeline.moveAnimationKeyframe
import com.apexstudio.app.domain.model.timeline.removeKeyframeAt
import com.apexstudio.app.domain.model.timeline.removeAnimationKeyframeAt
import com.apexstudio.app.domain.model.timeline.setAnimationCurveAt
import com.apexstudio.app.domain.model.timeline.upsertKeyframe
import kotlin.math.max

/**
 * Project.timeline is the editor's canonical keyframe representation. This compatibility bridge
 * keeps older preview/export consumers synchronized until their render APIs accept typed tracks.
 */
internal fun Project.syncSharedTimeline(
    importLegacyAnimation: Boolean = false,
    refreshLegacyAnimation: Boolean = false
): Project {
    val oldVideoClips = timeline.tracks.filterIsInstance<VideoTrack>().flatMap { it.clips }
        .associateBy(VideoClip::id)
    val oldOverlayItems = timeline.tracks.filterIsInstance<OverlayTrack>().flatMap { it.items }
        .associateBy(OverlayItem::id)
    val oldTextTracks = timeline.tracks.filterIsInstance<TextTrack>()
    val oldTextItems = oldTextTracks.flatMap { it.items }.associateBy(TextItem::id)
    val oldStickerTracks = timeline.tracks.filterIsInstance<StickerTrack>()
    val oldStickerItems = oldStickerTracks.flatMap { it.items }.associateBy(StickerItem::id)
    val videoClips = clips.filter { it.type != ClipType.OVERLAY && it.trackIndex == 0 && it.type in setOf(ClipType.VIDEO, ClipType.IMAGE) }
        .map { legacy -> legacy.toSharedVideoClip(oldVideoClips[legacy.id], importLegacyAnimation, refreshLegacyAnimation) }
    val overlayGroups = clips.filter { it.type == ClipType.OVERLAY || it.trackIndex > 0 }
        .groupBy { it.trackIndex.coerceAtLeast(1) }
        .toSortedMap()
    val visualTracks = buildList {
        if (videoClips.isNotEmpty()) {
            val id = timeline.tracks.filterIsInstance<VideoTrack>().firstOrNull { it.lane == 0 }?.id
                ?: "${id}-video-v1"
            add(VideoTrack(id = id, lane = 0, clips = videoClips))
        }
        overlayGroups.forEach { (lane, legacyClips) ->
            val oldTrack = timeline.tracks.filterIsInstance<OverlayTrack>().firstOrNull { it.lane == lane }
            val items = legacyClips.mapNotNull { legacy ->
                val original = oldOverlayItems[legacy.id]
                when (legacy.type) {
                    ClipType.IMAGE -> OverlayItem.Image(
                        id = legacy.id,
                        uri = Uri.parse(legacy.uri),
                        inMs = legacy.timelineOffsetMs,
                        outMs = legacy.timelineEndMs(),
                        transform = Transform(
                            position = XY(legacy.pipX, legacy.pipY),
                            scale = legacy.pipScale,
                            rotationDeg = legacy.pipRotationDeg
                        ),
                        opacity = legacy.pipOpacity
                    )
                    else -> {
                        val prior = (original as? OverlayItem.Video)?.toSharedVideoClip()
                        legacy.toSharedVideoClip(prior, importLegacyAnimation, refreshLegacyAnimation)
                            .toOverlayVideo(original as? OverlayItem.Video)
                    }
                }
            }
            add(
                OverlayTrack(
                    id = oldTrack?.id ?: "${id}-overlay-$lane",
                    lane = lane,
                    items = items,
                    isVisible = oldTrack?.isVisible ?: true,
                    isLocked = oldTrack?.isLocked ?: false,
                    opacity = oldTrack?.opacity ?: 1f,
                    opacityKeyframes = oldTrack?.opacityKeyframes.orEmpty(),
                    blendMode = oldTrack?.blendMode ?: BlendMode.NORMAL
                )
            )
        }
    }

    val existingAudio = timeline.tracks.filterIsInstance<SharedAudioTrack>().associateBy { it.id }
    val visualLaneNumbers = visualTracks.map { it.lane }.toSet()
    var nextLane = max(10, (visualLaneNumbers.maxOrNull() ?: 0) + 1)
    val audioTracks = audioTracks.map { legacy ->
        val old = existingAudio["shared-audio-${legacy.id}"]
        val pan = old?.pan ?: legacy.pan
        val panKeyframes = old?.panKeyframes?.takeIf { it.isNotEmpty() } ?: legacy.panKeyframes
        val end = legacy.startTimeMs + (legacy.sourceEndMs - legacy.trimStartMs).coerceAtLeast(0L)
        val audio = SharedAudioClip(
            id = legacy.id,
            uri = Uri.parse(legacy.uri),
            inMs = legacy.startTimeMs.coerceAtLeast(0L),
            outMs = max(legacy.startTimeMs, end),
            trimStartMs = legacy.trimStartMs,
            volume = legacy.volume,
            volumeKeyframes = legacy.volumeKeyframes.sortedBy { it.timeMs }.map {
                SharedKeyframe(it.timeMs, it.volume, Curve.LINEAR)
            },
            pan = pan,
            panKeyframes = panKeyframes
        )
        SharedAudioTrack(
            id = "shared-audio-${legacy.id}",
            lane = nextLane++,
            clips = listOf(audio),
            pan = pan,
            panKeyframes = panKeyframes,
            ducking = old?.ducking
        )
    }

    val legacyTextEntries = clips.flatMap { owner -> owner.textOverlays.map { owner to it } }
    val textIds = legacyTextEntries.map { (owner, overlay) -> sharedTextItemId(owner.id, overlay) }.toSet()
    val sharedTextItems = legacyTextEntries.distinctBy { (owner, overlay) -> sharedTextItemId(owner.id, overlay) }
        .map { (owner, overlay) ->
            val itemId = sharedTextItemId(owner.id, overlay)
            val previous = oldTextItems[itemId]
            val shouldImport = (previous == null && importLegacyAnimation) || refreshLegacyAnimation
            overlay.toSharedTextItem(itemId, previous, shouldImport)
        } + oldTextItems.values.filterNot { it.id in textIds }
    val textTrack = sharedTextItems.takeIf { it.isNotEmpty() }?.let { items ->
        val old = oldTextTracks.firstOrNull()
        TextTrack(id = old?.id ?: "${id}-text", lane = old?.lane ?: nextLane++, items = items)
    }

    val legacyStickerEntries = (stickers + clips.flatMap { it.stickers }).distinctBy(LegacyStickerOverlay::id)
    val stickerIds = legacyStickerEntries.map(LegacyStickerOverlay::id).toSet()
    val sharedStickerItems = legacyStickerEntries.map { legacy ->
        val previous = oldStickerItems[legacy.id]
        val shouldImport = (previous == null && importLegacyAnimation) || refreshLegacyAnimation
        legacy.toSharedStickerItem(previous, shouldImport)
    } + oldStickerItems.values.filterNot { it.id in stickerIds }
    val stickerTrack = sharedStickerItems.takeIf { it.isNotEmpty() }?.let { items ->
        val old = oldStickerTracks.firstOrNull()
        StickerTrack(id = old?.id ?: "${id}-stickers", lane = old?.lane ?: nextLane++, items = items)
    }

    val retainedTracks = timeline.tracks.filter {
        it !is VideoTrack && it !is OverlayTrack && it !is SharedAudioTrack && it !is TextTrack && it !is StickerTrack
    }
    val usedLanes = (visualTracks.map { it.lane } + audioTracks.map { it.lane }).toMutableSet()
    val canonicalTypedTracks = listOfNotNull(textTrack, stickerTrack)
    val nonCollidingTypedTracks = canonicalTypedTracks.map { track ->
        if (track.lane !in usedLanes) {
            usedLanes += track.lane
            track
        } else {
            while (nextLane in usedLanes) nextLane++
            val moved = track.withLane(nextLane++)
            usedLanes += moved.lane
            moved
        }
    }
    val nonCollidingRetained = retainedTracks.map { track ->
        if (track.lane !in usedLanes) {
            usedLanes += track.lane
            track
        } else {
            while (nextLane in usedLanes) nextLane++
            val moved = track.withLane(nextLane++)
            usedLanes += moved.lane
            moved
        }
    }
    return copy(timeline = Timeline(visualTracks + audioTracks + nonCollidingTypedTracks + nonCollidingRetained))
}

internal fun Project.sharedVideoClip(clipId: String): VideoClip? =
    timeline.tracks.filterIsInstance<VideoTrack>().asSequence().flatMap { it.clips.asSequence() }
        .firstOrNull { it.id == clipId }
        ?: timeline.tracks.filterIsInstance<OverlayTrack>().asSequence().flatMap { it.items.asSequence() }
            .filterIsInstance<OverlayItem.Video>().firstOrNull { it.id == clipId }?.toSharedVideoClip()
        ?: clips.firstOrNull { it.id == clipId }?.let { it.toSharedVideoClip(null, importLegacyAnimation = true) }

internal fun Project.updateSharedVideoClip(clipId: String, transform: (VideoClip) -> VideoClip): Project {
    val originalLegacy = clips.firstOrNull { it.id == clipId } ?: return this
    val current = sharedVideoClip(clipId) ?: originalLegacy.toSharedVideoClip(null, importLegacyAnimation = true)
    val updated = transform(current)
    val updatedLegacy = originalLegacy.copy(keyframes = updated.toLegacyKeyframeTrack(originalLegacy.keyframes))
    val updatedClips = clips.map { if (it.id == clipId) updatedLegacy else it }
    val tracks = timeline.tracks.map { track ->
        when (track) {
            is VideoTrack -> track.copy(clips = track.clips.map { if (it.id == clipId) updated else it })
            is OverlayTrack -> track.copy(items = track.items.map { item ->
                if (item.id == clipId && item is OverlayItem.Video) updated.toOverlayVideo(item) else item
            })
            else -> track
        }
    }
    return copy(clips = updatedClips, timeline = Timeline(tracks))
}

internal fun Project.sharedTextItem(ownerClipId: String, overlayId: String): TextItem? {
    val overlay = clips.firstOrNull { it.id == ownerClipId }?.textOverlays
        ?.firstOrNull { it.id == overlayId } ?: return null
    val sharedId = sharedTextItemId(ownerClipId, overlay)
    return timeline.tracks.filterIsInstance<TextTrack>().asSequence()
        .flatMap { it.items.asSequence() }.firstOrNull { it.id == sharedId }
}

internal fun Project.updateSharedTextItem(
    ownerClipId: String,
    overlayId: String,
    transform: (TextItem) -> TextItem
): Project {
    val normalized = syncSharedTimeline(importLegacyAnimation = true)
    val owner = normalized.clips.firstOrNull { it.id == ownerClipId } ?: return this
    val overlay = owner.textOverlays.firstOrNull { it.id == overlayId } ?: return this
    val sharedId = sharedTextItemId(ownerClipId, overlay)
    val current = normalized.sharedTextItem(ownerClipId, overlayId) ?: return this
    val updated = transform(current)
    val legacy = overlay.copy(keyframes = updated.toLegacyKeyframeTrack(overlay))
    val updatedClips = normalized.clips.map { clip ->
        if (clip.id != ownerClipId) clip else clip.copy(textOverlays = clip.textOverlays.map {
            if (it.id == overlayId) legacy else it
        })
    }
    val tracks = normalized.timeline.tracks.map { track ->
        if (track is TextTrack) track.copy(items = track.items.map { if (it.id == sharedId) updated else it }) else track
    }
    return normalized.copy(clips = updatedClips, timeline = Timeline(tracks))
}

internal fun Project.sharedStickerItem(stickerId: String): StickerItem? =
    timeline.tracks.filterIsInstance<StickerTrack>().asSequence()
        .flatMap { it.items.asSequence() }.firstOrNull { it.id == stickerId }

internal fun Project.updateSharedStickerItem(
    stickerId: String,
    transform: (StickerItem) -> StickerItem
): Project {
    val normalized = syncSharedTimeline(importLegacyAnimation = true)
    val legacy = (normalized.stickers + normalized.clips.flatMap { it.stickers })
        .firstOrNull { it.id == stickerId } ?: return this
    val current = normalized.sharedStickerItem(stickerId) ?: return this
    val updated = transform(current)
    val legacyUpdated = legacy.copy(keyframes = updated.toLegacyKeyframeTrack(legacy))
    val updatedStickers = normalized.stickers.map { if (it.id == stickerId) legacyUpdated else it }
    val updatedClips = normalized.clips.map { clip ->
        if (clip.stickers.none { it.id == stickerId }) clip else clip.copy(stickers = clip.stickers.map {
            if (it.id == stickerId) legacyUpdated else it
        })
    }
    val tracks = normalized.timeline.tracks.map { track ->
        if (track is StickerTrack) track.copy(items = track.items.map { if (it.id == stickerId) updated else it }) else track
    }
    return normalized.copy(stickers = updatedStickers, clips = updatedClips, timeline = Timeline(tracks))
}

internal fun Project.sharedKeyframeTrackFor(clipId: String, fallback: KeyframeTrack): KeyframeTrack =
    sharedVideoClip(clipId)?.toUiKeyframeTrack(fallback) ?: fallback

internal fun Project.updateSharedKeyframeTrack(
    clipId: String,
    transform: (VideoClip) -> VideoClip
): Project = updateSharedVideoClip(clipId, transform)

internal fun VideoClip.animationTransformAt(timeMs: Long): AnimatedTransform = animationAt(timeMs).let {
    AnimatedTransform(
        translateX = it.position.x,
        translateY = it.position.y,
        scale = it.scale,
        rotationDeg = it.rotationDeg,
        opacity = it.opacity,
        volume = it.volume,
        effectIntensity = it.effectIntensity,
        filterIntensity = it.filterIntensity,
        saturation = it.saturation
    )
}

internal fun VideoClip.replaceLegacyKeyframeAt(keyframe: LegacyKeyframe): VideoClip {
    val time = keyframe.timeMs
    val curve = keyframe.curve.toSharedCurve()
    return copy(
        transform = transform.copy(
            positionKeyframes = transform.positionKeyframes.upsertKeyframe(
                SharedKeyframe(time, XY(keyframe.translateX, keyframe.translateY), curve)
            ),
            scaleKeyframes = transform.scaleKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.scale, curve)),
            rotationKeyframes = transform.rotationKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.rotationDeg, curve))
        ),
        opacityKeyframes = opacityKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.opacity, curve)),
        volumeKeyframes = volumeKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.volume, curve)),
        filterKeyframes = filterKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.filterIntensity, curve)),
        effectIntensityKeyframes = effectIntensityKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.effectIntensity, curve)),
        saturationKeyframes = if (keyframe.saturation == null) {
            saturationKeyframes.removeKeyframeAt(time)
        } else {
            saturationKeyframes.upsertKeyframe(SharedKeyframe(time, keyframe.saturation, curve))
        }
    )
}

internal fun VideoClip.clearAnimationKeyframes(): VideoClip = copy(
    transform = transform.copy(positionKeyframes = emptyList(), scaleKeyframes = emptyList(), rotationKeyframes = emptyList()),
    opacityKeyframes = emptyList(),
    volumeKeyframes = emptyList(),
    panKeyframes = emptyList(),
    filterKeyframes = emptyList(),
    effectIntensityKeyframes = emptyList(),
    lutKeyframes = emptyList(),
    saturationKeyframes = emptyList(),
    mask = when (val currentMask = mask) {
        is com.apexstudio.app.domain.model.timeline.Mask.Shape -> currentMask.copy(keyframes = emptyList())
        else -> currentMask
    },
    drawingLayer = drawingLayer?.copy(
        globalAlphaKeyframes = emptyList(),
        strokes = drawingLayer.strokes.map { stroke ->
            stroke.copy(
                alphaKeyframes = emptyList(),
                transform = stroke.transform.copy(
                    positionKeyframes = emptyList(),
                    scaleKeyframes = emptyList(),
                    rotationKeyframes = emptyList()
                )
            )
        }
    )
)

internal fun Project.applySharedKeyframeValues(clipId: String, keyframe: LegacyKeyframe): Project =
    updateSharedVideoClip(clipId) { it.replaceLegacyKeyframeAt(keyframe) }

private fun MediaClip.toSharedVideoClip(
    existing: VideoClip?,
    importLegacyAnimation: Boolean,
    refreshLegacyAnimation: Boolean = false
): VideoClip {
    val legacyKeyframes = keyframes.sorted().keyframes
    val shouldImport = existing == null || refreshLegacyAnimation ||
        (importLegacyAnimation && existing.animationKeyframeTimes().isEmpty())
    val imported = if (shouldImport) legacyKeyframes.toTypedVideoAnimation() else null
    val outputDuration = if (speedMultiplier.isFinite() && speedMultiplier > 0f) {
        ((trimEndMs - trimStartMs).coerceAtLeast(0L).toDouble() / speedMultiplier.toDouble()).toLong()
    } else durationMs.coerceAtLeast(0L)
    val inMs = timelineOffsetMs.coerceAtLeast(0L)
    val outMs = if (outputDuration > 0L && inMs > Long.MAX_VALUE - outputDuration) Long.MAX_VALUE else inMs + outputDuration
    val overlay = type == ClipType.OVERLAY || trackIndex > 0
    val legacyTransform = if (overlay) {
        Transform(position = XY(pipX, pipY), scale = pipScale, rotationDeg = pipRotationDeg)
    } else {
        existing?.transform ?: Transform()
    }
    return VideoClip(
        id = id,
        uri = Uri.parse(uri),
        inMs = inMs,
        outMs = outMs,
        trimStartMs = trimStartMs,
        speed = speedMultiplier,
        transform = legacyTransform.copy(
            positionKeyframes = imported?.transform?.positionKeyframes ?: existing?.transform?.positionKeyframes.orEmpty(),
            scaleKeyframes = imported?.transform?.scaleKeyframes ?: existing?.transform?.scaleKeyframes.orEmpty(),
            rotationKeyframes = imported?.transform?.rotationKeyframes ?: existing?.transform?.rotationKeyframes.orEmpty()
        ),
        opacity = if (overlay) pipOpacity else existing?.opacity ?: 1f,
        opacityKeyframes = imported?.opacityKeyframes ?: existing?.opacityKeyframes.orEmpty(),
        filter = existing?.filter,
        filterIntensity = existing?.filterIntensity ?: 1f,
        filterKeyframes = imported?.filterKeyframes ?: existing?.filterKeyframes.orEmpty(),
        effectIntensity = existing?.effectIntensity ?: 1f,
        effectIntensityKeyframes = imported?.effectIntensityKeyframes ?: existing?.effectIntensityKeyframes.orEmpty(),
        lutId = existing?.lutId,
        lutIntensity = existing?.lutIntensity ?: 1f,
        lutKeyframes = existing?.lutKeyframes.orEmpty(),
        saturation = existing?.saturation ?: 1f,
        saturationKeyframes = imported?.saturationKeyframes ?: existing?.saturationKeyframes.orEmpty(),
        mask = existing?.mask,
        chromaKey = existing?.chromaKey,
        blendMode = existing?.blendMode ?: BlendMode.NORMAL,
        volume = volume,
        volumeKeyframes = imported?.volumeKeyframes ?: existing?.volumeKeyframes.orEmpty(),
        pan = existing?.pan ?: 0f,
        panKeyframes = existing?.panKeyframes.orEmpty(),
        drawingLayer = existing?.drawingLayer
    )
}

private fun OverlayItem.Video.toSharedVideoClip(): VideoClip = VideoClip(
    id = id,
    uri = uri,
    inMs = inMs,
    outMs = outMs,
    trimStartMs = trimStartMs,
    speed = speed,
    transform = transform,
    opacity = opacity,
    opacityKeyframes = opacityKeyframes,
    filter = filter,
    filterIntensity = filterIntensity,
    filterKeyframes = filterKeyframes,
    effectIntensity = effectIntensity,
    effectIntensityKeyframes = effectIntensityKeyframes,
    lutId = lutId,
    lutIntensity = lutIntensity,
    lutKeyframes = lutKeyframes,
    saturation = saturation,
    saturationKeyframes = saturationKeyframes,
    mask = mask,
    chromaKey = chromaKey,
    blendMode = blendMode,
    drawingLayer = drawingLayer
)

private fun VideoClip.toOverlayVideo(existing: OverlayItem.Video?): OverlayItem.Video = OverlayItem.Video(
    id = id,
    uri = uri,
    inMs = inMs,
    outMs = outMs,
    trimStartMs = trimStartMs,
    speed = speed,
    transform = transform,
    opacity = opacity,
    opacityKeyframes = opacityKeyframes,
    filter = filter,
    filterIntensity = filterIntensity,
    filterKeyframes = filterKeyframes,
    effectIntensity = effectIntensity,
    effectIntensityKeyframes = effectIntensityKeyframes,
    lutId = lutId,
    lutIntensity = lutIntensity,
    lutKeyframes = lutKeyframes,
    saturation = saturation,
    saturationKeyframes = saturationKeyframes,
    mask = mask,
    chromaKey = chromaKey,
    blendMode = blendMode,
    drawingLayer = drawingLayer
).let { converted -> existing?.let { converted.copy(id = it.id) } ?: converted }

private fun KeyframeTrack.toTypedVideoAnimation(): VideoClip {
    val ordered = keyframes.sortedBy(LegacyKeyframe::timeMs)
    fun curveForIncoming(index: Int): Curve = ordered.getOrNull(index + 1)?.curve?.toSharedCurve()
        ?: ordered.getOrNull(index)?.curve?.toSharedCurve() ?: Curve.LINEAR
    val positions = ordered.mapIndexed { index, key ->
        SharedKeyframe(key.timeMs, XY(key.translateX, key.translateY), curveForIncoming(index))
    }
    val scales = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.scale, curveForIncoming(index)) }
    val rotations = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.rotationDeg, curveForIncoming(index)) }
    val opacities = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.opacity, curveForIncoming(index)) }
    val volumes = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.volume, curveForIncoming(index)) }
    val filters = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.filterIntensity, curveForIncoming(index)) }
    val effects = ordered.mapIndexed { index, key -> SharedKeyframe(key.timeMs, key.effectIntensity, curveForIncoming(index)) }
    val pinnedSaturation = ordered.filter { it.saturation != null }
    val saturations = pinnedSaturation.mapIndexed { index, key ->
        val nextCurve = pinnedSaturation.getOrNull(index + 1)?.curve?.toSharedCurve()
            ?: key.curve.toSharedCurve()
        SharedKeyframe(key.timeMs, key.saturation ?: 1f, nextCurve)
    }
    return VideoClip(
        id = "legacy",
        uri = Uri.parse(""),
        inMs = 0L,
        outMs = 0L,
        transform = Transform(
            positionKeyframes = positions,
            scaleKeyframes = scales,
            rotationKeyframes = rotations
        ),
        opacityKeyframes = opacities,
        volumeKeyframes = volumes,
        filterKeyframes = filters,
        effectIntensityKeyframes = effects,
        saturationKeyframes = saturations
    )
}

private fun VideoClip.toLegacyKeyframeTrack(existing: KeyframeTrack): KeyframeTrack {
    val times = buildSet {
        addAll(transform.positionKeyframes.map(SharedKeyframe<XY>::timeMs))
        addAll(transform.scaleKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(transform.rotationKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(opacityKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(volumeKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(filterKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(effectIntensityKeyframes.map(SharedKeyframe<Float>::timeMs))
        addAll(saturationKeyframes.map(SharedKeyframe<Float>::timeMs))
    }.sorted()
    if (times.isEmpty()) return KeyframeTrack()
    val existingByTime = existing.keyframes.associateBy(LegacyKeyframe::timeMs)
    val output = times.mapIndexed { index, time ->
        val value = animationAt(time)
        val previousCurve = times.getOrNull(index - 1)?.let(::curveAt)
        LegacyKeyframe(
            id = existingByTime[time]?.id ?: "shared-$id-$time",
            timeMs = time,
            translateX = value.position.x,
            translateY = value.position.y,
            scale = value.scale,
            rotationDeg = value.rotationDeg,
            opacity = value.opacity,
            volume = value.volume,
            effectIntensity = value.effectIntensity,
            filterIntensity = value.filterIntensity,
            saturation = value.saturation.takeIf { saturationKeyframes.isNotEmpty() },
            curve = previousCurve?.toLegacyCurve() ?: KeyframeCurve.LINEAR
        )
    }
    return KeyframeTrack(output).sorted()
}

private fun sharedTextItemId(ownerClipId: String, overlay: LegacyTextOverlay): String =
    overlay.projectTextLayerId?.let { "project-text:$it" } ?: "$ownerClipId:${overlay.id}"

private fun LegacyTextOverlay.toSharedTextItem(
    sharedId: String,
    previous: TextItem?,
    importAnimation: Boolean
): TextItem {
    val ordered = keyframes.keyframes.sortedBy(LegacyKeyframe::timeMs)
    fun curveAt(index: Int): Curve = ordered.getOrNull(index + 1)?.curve?.toSharedCurve()
        ?: ordered.getOrNull(index)?.curve?.toSharedCurve() ?: Curve.LINEAR
    return TextItem(
        id = sharedId,
        text = text,
        position = XY(x, y),
        positionKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, XY(x + key.translateX, y + key.translateY), curveAt(index))
        } else previous?.positionKeyframes.orEmpty(),
        scale = sizeScale,
        scaleKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, sizeScale * key.scale, curveAt(index))
        } else previous?.scaleKeyframes.orEmpty(),
        rotationDeg = rotationDeg,
        rotationKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, rotationDeg + key.rotationDeg, curveAt(index))
        } else previous?.rotationKeyframes.orEmpty(),
        color = Color(colorArgb),
        colorKeyframes = previous?.colorKeyframes.orEmpty(),
        alpha = opacity,
        alphaKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, opacity * key.opacity, curveAt(index))
        } else previous?.alphaKeyframes.orEmpty(),
        startMs = startMs,
        endMs = endMs
    )
}

private fun LegacyStickerOverlay.toSharedStickerItem(
    previous: StickerItem?,
    importAnimation: Boolean
): StickerItem {
    val ordered = keyframes.keyframes.sortedBy(LegacyKeyframe::timeMs)
    fun curveAt(index: Int): Curve = ordered.getOrNull(index + 1)?.curve?.toSharedCurve()
        ?: ordered.getOrNull(index)?.curve?.toSharedCurve() ?: Curve.LINEAR
    return StickerItem(
        id = id,
        symbolOrUri = assetPath ?: symbolOrUri,
        position = XY(x, y),
        positionKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, XY(x + key.translateX, y + key.translateY), curveAt(index))
        } else previous?.positionKeyframes.orEmpty(),
        scale = sizeScale,
        scaleKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, sizeScale * key.scale, curveAt(index))
        } else previous?.scaleKeyframes.orEmpty(),
        rotationDeg = rotationDeg,
        rotationKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, rotationDeg + key.rotationDeg, curveAt(index))
        } else previous?.rotationKeyframes.orEmpty(),
        alpha = opacity,
        alphaKeyframes = if (importAnimation) ordered.mapIndexed { index, key ->
            SharedKeyframe(key.timeMs, opacity * key.opacity, curveAt(index))
        } else previous?.alphaKeyframes.orEmpty(),
        startMs = startMs,
        endMs = endMs
    )
}

private fun TextItem.toLegacyKeyframeTrack(existing: LegacyTextOverlay): KeyframeTrack =
    toLegacyOverlayKeyframeTrack(
        existing.keyframes,
        id,
        animationKeyframeTimes(),
        { time -> positionAt(time).let { XY(it.x - existing.x, it.y - existing.y) } },
        { time -> safeRatio(scaleAt(time), existing.sizeScale) },
        { time -> rotationAt(time) - existing.rotationDeg },
        { time -> safeRatio(alphaAt(time), existing.opacity) },
        { time -> curveAt(time) }
    )

private fun StickerItem.toLegacyKeyframeTrack(existing: LegacyStickerOverlay): KeyframeTrack =
    toLegacyOverlayKeyframeTrack(
        existing.keyframes,
        id,
        animationKeyframeTimes(),
        { time -> positionAt(time).let { XY(it.x - existing.x, it.y - existing.y) } },
        { time -> safeRatio(scaleAt(time), existing.sizeScale) },
        { time -> rotationAt(time) - existing.rotationDeg },
        { time -> safeRatio(alphaAt(time), existing.opacity) },
        { time -> curveAt(time) }
    )

private fun toLegacyOverlayKeyframeTrack(
    existing: KeyframeTrack,
    sharedId: String,
    times: Set<Long>,
    positionOffsetAt: (Long) -> XY,
    scaleRatioAt: (Long) -> Float,
    rotationOffsetAt: (Long) -> Float,
    alphaRatioAt: (Long) -> Float,
    curveAt: (Long) -> Curve
): KeyframeTrack {
    if (times.isEmpty()) return KeyframeTrack()
    val ordered = times.sorted()
    val existingByTime = existing.keyframes.associateBy(LegacyKeyframe::timeMs)
    return KeyframeTrack(ordered.mapIndexed { index, time ->
        val offset = positionOffsetAt(time)
        val previousCurve = ordered.getOrNull(index - 1)?.let(curveAt)?.toLegacyCurve()
        LegacyKeyframe(
            id = existingByTime[time]?.id ?: "shared-$sharedId-$time",
            timeMs = time,
            translateX = offset.x,
            translateY = offset.y,
            scale = scaleRatioAt(time).takeIf { it.isFinite() } ?: 1f,
            rotationDeg = rotationOffsetAt(time).takeIf { it.isFinite() } ?: 0f,
            opacity = alphaRatioAt(time).takeIf { it.isFinite() } ?: 1f,
            curve = previousCurve ?: KeyframeCurve.LINEAR
        )
    }).sorted()
}

private fun TextItem.curveAt(timeMs: Long): Curve =
    positionKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: scaleKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: rotationKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: colorKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: alphaKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: Curve.LINEAR

private fun StickerItem.curveAt(timeMs: Long): Curve =
    positionKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: scaleKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: rotationKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: alphaKeyframes.firstOrNull { it.timeMs == timeMs }?.curve
        ?: Curve.LINEAR

private fun safeRatio(value: Float, base: Float): Float =
    if (base.isFinite() && base > 0.0001f) value / base else 1f

private fun VideoClip.toUiKeyframeTrack(existing: KeyframeTrack): KeyframeTrack {
    val times = animationKeyframeTimes()
    if (times.isEmpty()) return KeyframeTrack()
    val existingByTime = existing.keyframes.associateBy(LegacyKeyframe::timeMs)
    return KeyframeTrack(
        times.map { time ->
            val value = animationAt(time)
            LegacyKeyframe(
                id = existingByTime[time]?.id ?: "shared-$id-$time",
                timeMs = time,
                translateX = value.position.x,
                translateY = value.position.y,
                scale = value.scale,
                rotationDeg = value.rotationDeg,
                opacity = value.opacity,
                volume = value.volume,
                effectIntensity = value.effectIntensity,
                filterIntensity = value.filterIntensity,
                saturation = value.saturation.takeIf { saturationKeyframes.isNotEmpty() },
                curve = curveAt(time).toLegacyCurve()
            )
        }
    ).sorted()
}

private fun MediaClip.timelineEndMs(): Long {
    val speed = speedMultiplier.takeIf { it.isFinite() && it > 0f } ?: 1f
    val span = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    val length = (span.toDouble() / speed.toDouble()).toLong()
    return if (length > 0L && timelineOffsetMs > Long.MAX_VALUE - length) Long.MAX_VALUE else timelineOffsetMs + length
}

private fun com.apexstudio.app.domain.model.timeline.Track.withLane(newLane: Int) = when (this) {
    is VideoTrack -> copy(lane = newLane)
    is SharedAudioTrack -> copy(lane = newLane)
    is com.apexstudio.app.domain.model.timeline.TextTrack -> copy(lane = newLane)
    is com.apexstudio.app.domain.model.timeline.StickerTrack -> copy(lane = newLane)
    is OverlayTrack -> copy(lane = newLane)
}

private fun KeyframeCurve.toSharedCurve(): Curve = when (this) {
    KeyframeCurve.LINEAR -> Curve.LINEAR
    KeyframeCurve.EASE_IN -> Curve.EASE_IN
    KeyframeCurve.EASE_OUT -> Curve.EASE_OUT
    else -> Curve.SMOOTH
}

private fun Curve.toLegacyCurve(): KeyframeCurve = when (this) {
    Curve.LINEAR -> KeyframeCurve.LINEAR
    Curve.SMOOTH -> KeyframeCurve.BEZIER
    Curve.EASE_IN -> KeyframeCurve.EASE_IN
    Curve.EASE_OUT -> KeyframeCurve.EASE_OUT
}
