package com.apexstudio.app.domain.model.timeline

import android.net.Uri
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.AudioTrack as LegacyAudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe as LegacyKeyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.MaskPoint
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.MaskKeyframe as LegacyMaskKeyframe
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.toLegacyDrawingLayer
import com.apexstudio.app.domain.model.toTypedDrawingLayer
import com.apexstudio.app.domain.model.StickerOverlay as LegacyStickerOverlay
import com.apexstudio.app.domain.model.TextOverlay as LegacyTextOverlay
import com.apexstudio.app.domain.model.timeline.AudioClip as SharedAudioClip
import com.apexstudio.app.domain.model.timeline.AudioTrack as SharedAudioTrack
import com.apexstudio.app.domain.model.timeline.BlendMode
import com.apexstudio.app.domain.model.timeline.Mask
import com.apexstudio.app.domain.model.timeline.MaskKeyframe as SharedMaskKeyframe
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

private fun ChromaKey.toLegacySettings() = ChromaKeySettings(
    enabled = enabled,
    keyColorArgb = color.toArgb().toLong(),
    similarity = tolerance,
    smoothness = softness,
    spillSuppression = spill,
    depth3D = depth3D,
    tiltX = tiltX,
    tiltY = tiltY,
    backgroundType = backgroundType,
    customBackgroundUri = customBackgroundUri
)

private fun ChromaKeySettings.toTypedChromaKey() = ChromaKey(
    tolerance = similarity,
    softness = smoothness,
    spill = spillSuppression,
    color = Color(keyColorArgb),
    enabled = enabled,
    depth3D = depth3D,
    tiltX = tiltX,
    tiltY = tiltY,
    backgroundType = backgroundType,
    customBackgroundUri = customBackgroundUri
)

/**
 * Project.timeline is the editor's canonical keyframe representation. This compatibility bridge
 * keeps older preview/export consumers synchronized until their render APIs accept typed tracks.
 */
internal fun Project.syncSharedTimeline(
    importLegacyAnimation: Boolean = false,
    refreshLegacyAnimation: Boolean = false,
    refreshLegacyClipFields: Boolean = false,
    refreshLegacyAudioFields: Boolean = false,
    refreshLegacyAudioTrackIds: Set<String> = emptySet(),
    preservedAudioDucking: Map<String, Ducking?> = emptyMap(),
    refreshLegacyTextStickerFields: Boolean = false,
    refreshLegacyDrawingClipIds: Set<String> = emptySet()
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
        .map { legacy ->
            legacy.toSharedVideoClip(
                oldVideoClips[legacy.id], importLegacyAnimation, refreshLegacyAnimation,
                videoDrawings.firstOrNull { it.clipId == legacy.id }, refreshLegacyClipFields,
                legacy.id in refreshLegacyDrawingClipIds
            )
        }
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
                val priorImage = original as? OverlayItem.Image
                val originalImage = priorImage?.takeUnless { refreshLegacyClipFields }
                val importedImageAnimation = legacy.keyframes.sorted().toTypedVideoAnimation()
                when (legacy.type) {
                    ClipType.IMAGE -> OverlayItem.Image(
                        id = legacy.id,
                        uri = originalImage?.uri ?: Uri.parse(legacy.uri),
                        inMs = originalImage?.inMs ?: legacy.timelineOffsetMs,
                        outMs = originalImage?.outMs ?: legacy.timelineEndMs(),
                        sourceName = originalImage?.sourceName?.takeIf { it.isNotEmpty() } ?: legacy.name,
                        sourceDurationMs = originalImage?.sourceDurationMs?.takeIf { it > 0L } ?: legacy.durationMs,
                        trimStartMs = originalImage?.trimStartMs ?: legacy.trimStartMs,
                        trimEndMs = originalImage?.trimEndMs?.takeIf { it > 0L } ?: legacy.trimEndMs,
                        thumbnail = originalImage?.thumbnail ?: legacy.thumbnail,
                        groupId = originalImage?.groupId ?: legacy.groupId,
                        transform = originalImage?.transform ?: Transform(
                            position = XY(legacy.pipX, legacy.pipY),
                            scale = legacy.pipScale,
                            rotationDeg = legacy.pipRotationDeg,
                            positionKeyframes = priorImage?.transform?.positionKeyframes
                                ?: importedImageAnimation.transform.positionKeyframes,
                            scaleKeyframes = priorImage?.transform?.scaleKeyframes
                                ?: importedImageAnimation.transform.scaleKeyframes,
                            rotationKeyframes = priorImage?.transform?.rotationKeyframes
                                ?: importedImageAnimation.transform.rotationKeyframes
                        ).let { base ->
                            if (originalImage != null) base else base.copy(
                                position = XY(legacy.pipX, legacy.pipY),
                                scale = legacy.pipScale,
                                rotationDeg = legacy.pipRotationDeg
                            )
                        },
                        opacity = originalImage?.opacity ?: legacy.pipOpacity,
                        opacityKeyframes = priorImage?.opacityKeyframes ?: importedImageAnimation.opacityKeyframes,
                        filter = priorImage?.filter,
                        filterIntensity = priorImage?.filterIntensity ?: importedImageAnimation.filterIntensity,
                        filterKeyframes = priorImage?.filterKeyframes ?: importedImageAnimation.filterKeyframes,
                        effectIntensity = priorImage?.effectIntensity ?: importedImageAnimation.effectIntensity,
                        effectIntensityKeyframes = priorImage?.effectIntensityKeyframes
                            ?: importedImageAnimation.effectIntensityKeyframes,
                        lutId = priorImage?.lutId,
                        lutIntensity = priorImage?.lutIntensity ?: importedImageAnimation.lutIntensity,
                        lutKeyframes = priorImage?.lutKeyframes ?: importedImageAnimation.lutKeyframes,
                        saturation = priorImage?.saturation ?: importedImageAnimation.saturation,
                        saturationKeyframes = priorImage?.saturationKeyframes ?: importedImageAnimation.saturationKeyframes,
                        mask = priorImage?.mask ?: legacy.mask?.toTypedMask(),
                        chromaKey = priorImage?.chromaKey ?: legacy.chromaKeySettings?.toTypedChromaKey(),
                        blendMode = priorImage?.blendMode ?: BlendMode.NORMAL,
                        drawingLayer = priorImage?.drawingLayer
                            ?: videoDrawings.firstOrNull { it.clipId == legacy.id }?.toTypedDrawingLayer(legacy.id),
                        denoiseEnabled = originalImage?.denoiseEnabled ?: legacy.denoiseEnabled,
                        denoiseStrength = originalImage?.denoiseStrength ?: legacy.denoiseStrength,
                        backgroundReplace = originalImage?.backgroundReplace ?: legacy.backgroundReplace,
                        gpuFilterConfig = originalImage?.gpuFilterConfig ?: legacy.gpuFilterConfig,
                        projectVideoEffectId = originalImage?.projectVideoEffectId ?: legacy.projectVideoEffectId,
                        videoCrop = originalImage?.videoCrop ?: legacy.videoCrop,
                        faceCropTrack = originalImage?.faceCropTrack ?: legacy.faceCropTrack,
                        pipX = originalImage?.pipX ?: legacy.pipX, pipY = originalImage?.pipY ?: legacy.pipY,
                        pipScale = originalImage?.pipScale ?: legacy.pipScale,
                        pipRotationDeg = originalImage?.pipRotationDeg ?: legacy.pipRotationDeg,
                        pipOpacity = originalImage?.pipOpacity ?: legacy.pipOpacity,
                        overlayAspect = originalImage?.overlayAspect ?: legacy.overlayAspect,
                        overlayBlend = originalImage?.overlayBlend ?: legacy.overlayBlend.toTypedBlend(),
                        overlayBorderWidth = originalImage?.overlayBorderWidth ?: legacy.overlayBorderWidth,
                        overlayBorderColor = originalImage?.overlayBorderColor ?: legacy.overlayBorderColor,
                        overlayShadow = originalImage?.overlayShadow ?: legacy.overlayShadow,
                        overlayFlipH = originalImage?.overlayFlipH ?: legacy.overlayFlipH,
                        overlayFlipV = originalImage?.overlayFlipV ?: legacy.overlayFlipV,
                        overlayVolume = originalImage?.overlayVolume ?: legacy.overlayVolume,
                        photoEdit = originalImage?.photoEdit ?: legacy.photoEdit,
                        workspaceEffects = originalImage?.workspaceEffects ?: legacy.workspaceEffects,
                        videoFitMode = originalImage?.videoFitMode ?: legacy.videoFitMode
                    )
                    else -> {
                        val prior = (original as? OverlayItem.Video)?.toSharedVideoClip()
                        legacy.toSharedVideoClip(
                            prior, importLegacyAnimation, refreshLegacyAnimation,
                            videoDrawings.firstOrNull { it.clipId == legacy.id }, refreshLegacyClipFields,
                            legacy.id in refreshLegacyDrawingClipIds
                        )
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
    val projectedAudio = audioTracks.map { legacy ->
        val old = existingAudio["shared-audio-${legacy.id}"]
        val oldClip = old?.clips?.firstOrNull { it.id == legacy.id } ?: old?.clips?.firstOrNull()
        val refreshAudio = refreshLegacyAudioFields || legacy.id in refreshLegacyAudioTrackIds
        val typed = if (old == null) {
            legacy.toSharedAudioTrack(nextLane++, legacy.id)
        } else if (refreshAudio) {
            legacy.toSharedAudioTrack(old.lane, legacy.id).copy(ducking = old.ducking)
        } else old
        val clip = oldClip ?: legacy.toSharedAudioClip(legacy.id)
        val effective = if (old == null || refreshAudio) typed.copy(
            ducking = preservedAudioDucking[legacy.id] ?: typed.ducking
        ) else typed.copy(
            clips = typed.clips.ifEmpty { listOf(clip) }
        )
        effective.toLegacyAudioTrack(legacy) to effective
    }
    val audioTracks = projectedAudio.map { it.second }
    val projectedLegacyAudioTracks = projectedAudio.map { it.first }

    val legacyTextEntries = clips.flatMap { owner -> owner.textOverlays.map { owner to it } }
    val textIds = legacyTextEntries.map { (owner, overlay) -> sharedTextItemId(owner.id, overlay) }.toSet()
    val sharedTextItems = legacyTextEntries.distinctBy { (owner, overlay) -> sharedTextItemId(owner.id, overlay) }
        .map { (owner, overlay) ->
            val itemId = sharedTextItemId(owner.id, overlay)
            val previous = oldTextItems[itemId]
            val shouldImport = (previous == null && importLegacyAnimation) || refreshLegacyAnimation
            val effective = if (refreshLegacyAnimation || refreshLegacyTextStickerFields) overlay
                else previous?.toLegacyTextOverlay(overlay) ?: overlay
            effective.toSharedTextItem(itemId, previous, shouldImport)
        } + oldTextItems.values.filterNot {
            it.id in textIds || (refreshLegacyTextStickerFields && it.legacyBacked)
        }
    val textTrack = sharedTextItems.takeIf { it.isNotEmpty() }?.let { items ->
        val old = oldTextTracks.firstOrNull()
        TextTrack(id = old?.id ?: "${id}-text", lane = old?.lane ?: nextLane++, items = items)
    }

    val legacyStickerEntries = (stickers + clips.flatMap { it.stickers }).distinctBy(LegacyStickerOverlay::id)
    val stickerIds = legacyStickerEntries.map(LegacyStickerOverlay::id).toSet()
    val sharedStickerItems = legacyStickerEntries.map { legacy ->
        val previous = oldStickerItems[legacy.id]
        val shouldImport = (previous == null && importLegacyAnimation) || refreshLegacyAnimation
        val effective = if (refreshLegacyAnimation || refreshLegacyTextStickerFields) legacy
            else previous?.toLegacyStickerOverlay(legacy) ?: legacy
        effective.toSharedStickerItem(previous, shouldImport)
    } + oldStickerItems.values.filterNot {
        it.id in stickerIds || (refreshLegacyTextStickerFields && it.legacyBacked)
    }
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
    val typedTextById = sharedTextItems.associateBy(TextItem::id)
    val typedVideoById: Map<String, VideoClip> = visualTracks.filterIsInstance<VideoTrack>().flatMap { it.clips }.associateBy(VideoClip::id)
    val typedOverlayById = visualTracks.filterIsInstance<OverlayTrack>().flatMap { it.items }
        .filterIsInstance<OverlayItem.Video>().associateBy(OverlayItem.Video::id)
    val typedImagesById: Map<String, OverlayItem.Image> = visualTracks.filterIsInstance<OverlayTrack>().flatMap { it.items }
        .filterIsInstance<OverlayItem.Image>().associateBy(OverlayItem.Image::id)
    val typedOverlayVideosById: Map<String, OverlayItem.Video> = visualTracks.filterIsInstance<OverlayTrack>().flatMap { it.items }
        .filterIsInstance<OverlayItem.Video>().associateBy(OverlayItem.Video::id)
    val projectedClips: List<MediaClip> = clips.map { clip: MediaClip ->
        val typedVideo = typedVideoById[clip.id]
        val typedOverlay = typedOverlayVideosById[clip.id]
        val typedImage = typedImagesById[clip.id]
        val videoProjection = typedVideo?.takeUnless { refreshLegacyClipFields }
        val overlayProjection = typedOverlay?.takeUnless { refreshLegacyClipFields }
        val imageProjection = typedImage?.takeUnless { refreshLegacyClipFields }
        val typedAnimation = typedVideo ?: typedOverlay?.toSharedVideoClip() ?: typedImage?.toSharedVideoClip()
        clip.copy(
            name = videoProjection?.sourceName?.takeIf { it.isNotEmpty() } ?: overlayProjection?.sourceName?.takeIf { it.isNotEmpty() } ?: imageProjection?.sourceName?.takeIf { it.isNotEmpty() } ?: clip.name,
            uri = (videoProjection?.uri ?: overlayProjection?.uri ?: imageProjection?.uri)?.toString() ?: clip.uri,
            durationMs = videoProjection?.sourceDurationMs?.takeIf { it > 0L } ?: overlayProjection?.sourceDurationMs?.takeIf { it > 0L } ?: imageProjection?.sourceDurationMs?.takeIf { it > 0L } ?: clip.durationMs,
            trimStartMs = videoProjection?.trimStartMs ?: overlayProjection?.trimStartMs ?: imageProjection?.trimStartMs ?: clip.trimStartMs,
            trimEndMs = videoProjection?.trimEndMs?.takeIf { it > 0L } ?: overlayProjection?.trimEndMs?.takeIf { it > 0L } ?: imageProjection?.trimEndMs?.takeIf { it > 0L } ?: clip.trimEndMs,
            thumbnail = videoProjection?.thumbnail ?: overlayProjection?.thumbnail ?: imageProjection?.thumbnail ?: clip.thumbnail,
            timelineOffsetMs = videoProjection?.inMs ?: overlayProjection?.inMs ?: imageProjection?.inMs ?: clip.timelineOffsetMs,
            groupId = videoProjection?.groupId ?: overlayProjection?.groupId ?: imageProjection?.groupId ?: clip.groupId,
            speedMultiplier = videoProjection?.speed ?: overlayProjection?.speed ?: clip.speedMultiplier,
            speedCurve = videoProjection?.speedCurve ?: overlayProjection?.speedCurve ?: clip.speedCurve,
            rampStartSpeed = videoProjection?.rampStartSpeed ?: overlayProjection?.rampStartSpeed ?: clip.rampStartSpeed,
            rampEndSpeed = videoProjection?.rampEndSpeed ?: overlayProjection?.rampEndSpeed ?: clip.rampEndSpeed,
            speedPoints = videoProjection?.speedPoints ?: overlayProjection?.speedPoints ?: clip.speedPoints,
            type = videoProjection?.mediaType ?: if (imageProjection != null) ClipType.IMAGE else clip.type,
            keyframes = typedAnimation?.toLegacyKeyframeTrack(clip.keyframes) ?: clip.keyframes,
            mask = if (refreshLegacyClipFields) clip.mask else timeline.tracks.filterIsInstance<VideoTrack>().flatMap { it.clips }
                .firstOrNull { it.id == clip.id }?.mask?.toLegacyMask()
                ?: timeline.tracks.filterIsInstance<OverlayTrack>().flatMap { it.items }
                    .firstOrNull { it.id == clip.id }?.let { item -> when (item) {
                        is OverlayItem.Video -> item.mask?.toLegacyMask()
                        is OverlayItem.Image -> item.mask?.toLegacyMask()
                    } }
                ?: clip.mask,
            textOverlays = clip.textOverlays.map { overlay ->
                typedTextById[sharedTextItemId(clip.id, overlay)]?.toLegacyTextOverlay(overlay) ?: overlay
            },
            stickers = clip.stickers.map { sticker ->
                oldStickerItems[sticker.id]?.toLegacyStickerOverlay(sticker) ?: sticker
            },
            denoiseEnabled = videoProjection?.denoiseEnabled ?: overlayProjection?.denoiseEnabled ?: imageProjection?.denoiseEnabled ?: clip.denoiseEnabled,
            denoiseStrength = videoProjection?.denoiseStrength ?: overlayProjection?.denoiseStrength ?: imageProjection?.denoiseStrength ?: clip.denoiseStrength,
            backgroundReplace = videoProjection?.backgroundReplace ?: overlayProjection?.backgroundReplace ?: imageProjection?.backgroundReplace ?: clip.backgroundReplace,
            gpuFilterConfig = videoProjection?.gpuFilterConfig ?: overlayProjection?.gpuFilterConfig ?: imageProjection?.gpuFilterConfig ?: clip.gpuFilterConfig,
            chromaKeySettings = videoProjection?.chromaKey?.toLegacySettings()
                ?: overlayProjection?.chromaKey?.toLegacySettings()
                ?: clip.chromaKeySettings,
            projectVideoEffectId = videoProjection?.projectVideoEffectId ?: overlayProjection?.projectVideoEffectId ?: imageProjection?.projectVideoEffectId ?: clip.projectVideoEffectId,
            videoCrop = videoProjection?.videoCrop ?: overlayProjection?.videoCrop ?: imageProjection?.videoCrop ?: clip.videoCrop,
            faceCropTrack = videoProjection?.faceCropTrack ?: overlayProjection?.faceCropTrack ?: imageProjection?.faceCropTrack ?: clip.faceCropTrack,
            pipX = videoProjection?.pipX ?: overlayProjection?.pipX ?: imageProjection?.pipX ?: clip.pipX,
            pipY = videoProjection?.pipY ?: overlayProjection?.pipY ?: imageProjection?.pipY ?: clip.pipY,
            pipScale = videoProjection?.pipScale ?: overlayProjection?.pipScale ?: imageProjection?.pipScale ?: clip.pipScale,
            pipRotationDeg = videoProjection?.pipRotationDeg ?: overlayProjection?.pipRotationDeg ?: imageProjection?.pipRotationDeg ?: clip.pipRotationDeg,
            pipOpacity = videoProjection?.pipOpacity ?: overlayProjection?.pipOpacity ?: imageProjection?.pipOpacity ?: clip.pipOpacity,
            overlayAspect = videoProjection?.overlayAspect ?: overlayProjection?.overlayAspect ?: imageProjection?.overlayAspect ?: clip.overlayAspect,
            overlayBlend = videoProjection?.overlayBlend?.toLegacyBlend() ?: overlayProjection?.overlayBlend?.toLegacyBlend() ?: imageProjection?.overlayBlend?.toLegacyBlend() ?: clip.overlayBlend,
            overlayBorderWidth = videoProjection?.overlayBorderWidth ?: overlayProjection?.overlayBorderWidth ?: imageProjection?.overlayBorderWidth ?: clip.overlayBorderWidth,
            overlayBorderColor = videoProjection?.overlayBorderColor ?: overlayProjection?.overlayBorderColor ?: imageProjection?.overlayBorderColor ?: clip.overlayBorderColor,
            overlayShadow = videoProjection?.overlayShadow ?: overlayProjection?.overlayShadow ?: imageProjection?.overlayShadow ?: clip.overlayShadow,
            overlayFlipH = videoProjection?.overlayFlipH ?: overlayProjection?.overlayFlipH ?: imageProjection?.overlayFlipH ?: clip.overlayFlipH,
            overlayFlipV = videoProjection?.overlayFlipV ?: overlayProjection?.overlayFlipV ?: imageProjection?.overlayFlipV ?: clip.overlayFlipV,
            overlayVolume = videoProjection?.overlayVolume ?: overlayProjection?.overlayVolume ?: imageProjection?.overlayVolume ?: clip.overlayVolume,
            photoEdit = videoProjection?.photoEdit ?: overlayProjection?.photoEdit ?: imageProjection?.photoEdit ?: clip.photoEdit,
            workspaceEffects = videoProjection?.workspaceEffects ?: overlayProjection?.workspaceEffects ?: imageProjection?.workspaceEffects ?: clip.workspaceEffects,
            videoFitMode = videoProjection?.videoFitMode ?: overlayProjection?.videoFitMode ?: imageProjection?.videoFitMode ?: clip.videoFitMode
        )
    }
    val activeClipIds = clips.map(MediaClip::id).toSet()
    val projectedDrawings = videoDrawings.filter { it.clipId == null || it.clipId in activeClipIds }.map { layer ->
        val typed = typedVideoById[layer.clipId]?.drawingLayer
            ?: typedOverlayById[layer.clipId]?.drawingLayer
            ?: typedImagesById[layer.clipId]?.drawingLayer
        typed?.toLegacyDrawingLayer(layer) ?: layer
    }.let { existing ->
        val existingOwners = existing.mapNotNull(VideoDrawingLayer::clipId).toSet()
        val typedOnly = (typedVideoById.values.mapNotNull { it.drawingLayer } +
            typedOverlayById.values.mapNotNull { it.drawingLayer } +
            typedImagesById.values.mapNotNull { it.drawingLayer })
            .filter {
                it.ownerClipId != null && it.ownerClipId in activeClipIds &&
                    it.ownerClipId !in existingOwners && it.ownerClipId !in refreshLegacyDrawingClipIds
            }
            .distinctBy { it.ownerClipId }
            .map { it.toLegacyDrawingLayer() }
        existing + typedOnly
    }
    val typedStickerById = sharedStickerItems.associateBy(StickerItem::id)
    val projectedStickers = stickers.map { sticker ->
        typedStickerById[sticker.id]?.toLegacyStickerOverlay(sticker) ?: sticker
    }
    return copy(
        clips = projectedClips,
        videoDrawings = projectedDrawings,
        stickers = projectedStickers,
        audioTracks = projectedLegacyAudioTracks,
        timeline = Timeline(visualTracks + audioTracks + nonCollidingTypedTracks + nonCollidingRetained)
    )
}

private fun LegacyAudioTrack.toSharedAudioTrack(lane: Int, clipId: String): SharedAudioTrack {
    val clip = toSharedAudioClip(clipId)
    return SharedAudioTrack(
        id = "shared-audio-$id", lane = lane, clips = listOf(clip), name = name,
        volume = volume, isMuted = isMuted, isSolo = isSolo,
        volumeKeyframes = volumeKeyframes.sortedBy { it.timeMs }.map { SharedKeyframe(it.timeMs, it.volume, Curve.LINEAR) },
        pan = pan, panKeyframes = panKeyframes, kind = kind.toSharedAudioKind()
    )
}

private fun LegacyAudioTrack.toSharedAudioClip(clipId: String): SharedAudioClip {
    val duration = (trimEndMs - trimStartMs).takeIf { it > 0L }
        ?: (sourceEndMs - trimStartMs).coerceAtLeast(0L)
    val end = startTimeMs.coerceAtLeast(0L) + duration
    return SharedAudioClip(
        id = clipId, uri = Uri.parse(uri), inMs = startTimeMs.coerceAtLeast(0L),
        outMs = max(startTimeMs, end), trimStartMs = trimStartMs, trimEndMs = trimEndMs,
        sourceDurationMs = sourceDurationMs, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs,
        volume = volume,
        volumeKeyframes = volumeKeyframes.sortedBy { it.timeMs }.map { SharedKeyframe(it.timeMs, it.volume, Curve.LINEAR) },
        pan = pan, panKeyframes = panKeyframes
    )
}

/** Apply only fields changed by one legacy audio UI mutation to an existing typed lane. */
internal fun applyLegacyAudioTrackChanges(
    before: LegacyAudioTrack,
    after: LegacyAudioTrack,
    typed: SharedAudioTrack
): SharedAudioTrack {
    var result = typed
    if (before.name != after.name) result = result.copy(name = after.name)
    if (before.volume != after.volume) result = result.copy(
        volume = after.volume,
        clips = result.clips.map { it.copy(volume = after.volume) }
    )
    if (before.isMuted != after.isMuted) result = result.copy(isMuted = after.isMuted)
    if (before.isSolo != after.isSolo) result = result.copy(isSolo = after.isSolo)
    if (before.volumeKeyframes != after.volumeKeyframes) {
        val keys = after.volumeKeyframes.sortedBy { it.timeMs }
            .map { SharedKeyframe(it.timeMs, it.volume, Curve.LINEAR) }
        result = result.copy(
            volumeKeyframes = keys,
            clips = result.clips.map { it.copy(volumeKeyframes = keys) }
        )
    }
    if (before.pan != after.pan) result = result.copy(
        pan = after.pan,
        clips = result.clips.map { it.copy(pan = after.pan) }
    )
    if (before.panKeyframes != after.panKeyframes) result = result.copy(
        panKeyframes = after.panKeyframes,
        clips = result.clips.map { it.copy(panKeyframes = after.panKeyframes) }
    )
    if (before.kind != after.kind) result = result.copy(kind = after.kind.toSharedAudioKind())

    val placementChanged = before.uri != after.uri ||
        before.trimStartMs != after.trimStartMs || before.trimEndMs != after.trimEndMs ||
        before.startTimeMs != after.startTimeMs || before.sourceDurationMs != after.sourceDurationMs ||
        before.fadeInMs != after.fadeInMs || before.fadeOutMs != after.fadeOutMs
    if (placementChanged) {
        val duration = (after.trimEndMs - after.trimStartMs).takeIf { it > 0L }
            ?: (after.sourceEndMs - after.trimStartMs).coerceAtLeast(0L)
        result = result.copy(clips = result.clips.map { clip ->
            clip.copy(
                uri = if (before.uri != after.uri) Uri.parse(after.uri) else clip.uri,
                inMs = if (before.startTimeMs != after.startTimeMs) after.startTimeMs.coerceAtLeast(0L) else clip.inMs,
                outMs = if (before.startTimeMs != after.startTimeMs || before.trimStartMs != after.trimStartMs || before.trimEndMs != after.trimEndMs)
                    after.startTimeMs.coerceAtLeast(0L) + duration else clip.outMs,
                trimStartMs = if (before.trimStartMs != after.trimStartMs) after.trimStartMs else clip.trimStartMs,
                trimEndMs = if (before.trimEndMs != after.trimEndMs) after.trimEndMs else clip.trimEndMs,
                sourceDurationMs = if (before.sourceDurationMs != after.sourceDurationMs) after.sourceDurationMs else clip.sourceDurationMs,
                fadeInMs = if (before.fadeInMs != after.fadeInMs) after.fadeInMs else clip.fadeInMs,
                fadeOutMs = if (before.fadeOutMs != after.fadeOutMs) after.fadeOutMs else clip.fadeOutMs
            )
        })
    }
    return result
}

private fun LegacyAudioTrack.Kind.toSharedAudioKind() = when (this) {
    LegacyAudioTrack.Kind.ORIGINAL_VIDEO -> SharedAudioTrack.AudioKind.ORIGINAL_VIDEO
    LegacyAudioTrack.Kind.MUSIC -> SharedAudioTrack.AudioKind.MUSIC
    LegacyAudioTrack.Kind.SFX -> SharedAudioTrack.AudioKind.SFX
}

private fun SharedAudioTrack.toLegacyAudioTrack(existing: LegacyAudioTrack): LegacyAudioTrack {
    val clip = clips.firstOrNull()
    return existing.copy(
        name = name, uri = clip?.uri?.toString() ?: existing.uri, volume = volume,
        isMuted = isMuted, isSolo = isSolo,
        volumeKeyframes = volumeKeyframes.mapIndexed { index, key ->
            com.apexstudio.app.domain.model.VolumeKeyframe(
                id = existing.volumeKeyframes.getOrNull(index)?.id ?: "typed-volume-$index-${key.timeMs}",
                timeMs = key.timeMs, volume = key.value
            )
        },
        pan = pan, panKeyframes = panKeyframes,
        trimStartMs = clip?.trimStartMs ?: existing.trimStartMs,
        trimEndMs = clip?.let { it.trimStartMs + (it.outMs - it.inMs).coerceAtLeast(0L) }
            ?: existing.trimEndMs,
        startTimeMs = clip?.inMs ?: existing.startTimeMs,
        sourceDurationMs = clip?.sourceDurationMs ?: existing.sourceDurationMs,
        fadeInMs = clip?.fadeInMs ?: existing.fadeInMs,
        fadeOutMs = clip?.fadeOutMs ?: existing.fadeOutMs,
        kind = kind.toLegacyAudioKind()
    )
}

private fun SharedAudioTrack.AudioKind.toLegacyAudioKind() = when (this) {
    SharedAudioTrack.AudioKind.ORIGINAL_VIDEO -> LegacyAudioTrack.Kind.ORIGINAL_VIDEO
    SharedAudioTrack.AudioKind.MUSIC -> LegacyAudioTrack.Kind.MUSIC
    SharedAudioTrack.AudioKind.SFX -> LegacyAudioTrack.Kind.SFX
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
    val existingDrawing = videoDrawings.firstOrNull { it.clipId == clipId }
    val updatedLegacy = originalLegacy.copy(
        mask = updated.mask?.toLegacyMask(),
        keyframes = updated.toLegacyKeyframeTrack(originalLegacy.keyframes)
    )
    val updatedDrawing = updated.drawingLayer?.toLegacyDrawingLayer(existingDrawing)
    val updatedDrawings = when {
        updated.drawingLayer == null -> videoDrawings.filterNot { it.clipId == clipId }
        updatedDrawing == null -> videoDrawings
        existingDrawing == null -> videoDrawings + updatedDrawing
        else -> videoDrawings.map { if (it.id == existingDrawing.id) updatedDrawing else it }
    }
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
    return copy(clips = updatedClips, videoDrawings = updatedDrawings, timeline = Timeline(tracks))
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
    val legacy = updated.toLegacyTextOverlay(overlay)
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
    val legacyUpdated = updated.toLegacyStickerOverlay(legacy)
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
    refreshLegacyAnimation: Boolean = false,
    legacyDrawing: VideoDrawingLayer? = null,
    refreshLegacyClipFields: Boolean = false,
    refreshLegacyDrawing: Boolean = false
): VideoClip {
    val legacyTrack = keyframes.sorted()
    val shouldImport = existing == null || refreshLegacyAnimation ||
        (importLegacyAnimation && existing.animationKeyframeTimes().isEmpty())
    val imported = if (shouldImport) legacyTrack.toTypedVideoAnimation() else null
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
        inMs = if (refreshLegacyClipFields) inMs else existing?.inMs ?: inMs,
        outMs = if (refreshLegacyClipFields) outMs else existing?.outMs ?: outMs,
        trimStartMs = if (refreshLegacyClipFields) trimStartMs else existing?.trimStartMs ?: trimStartMs,
        speed = if (refreshLegacyClipFields) speedMultiplier else existing?.speed ?: speedMultiplier,
        sourceName = if (refreshLegacyClipFields) name else existing?.sourceName?.takeIf { it.isNotEmpty() } ?: name,
        sourceDurationMs = if (refreshLegacyClipFields) durationMs else existing?.sourceDurationMs?.takeIf { it > 0L } ?: durationMs,
        trimEndMs = if (refreshLegacyClipFields) trimEndMs else existing?.trimEndMs?.takeIf { it > 0L } ?: trimEndMs,
        thumbnail = if (refreshLegacyClipFields) thumbnail else existing?.thumbnail ?: thumbnail,
        groupId = if (refreshLegacyClipFields) groupId else existing?.groupId ?: groupId,
        speedCurve = if (refreshLegacyClipFields) speedCurve else existing?.speedCurve ?: speedCurve,
        rampStartSpeed = if (refreshLegacyClipFields) rampStartSpeed else existing?.rampStartSpeed ?: rampStartSpeed,
        rampEndSpeed = if (refreshLegacyClipFields) rampEndSpeed else existing?.rampEndSpeed ?: rampEndSpeed,
        speedPoints = if (refreshLegacyClipFields) speedPoints else existing?.speedPoints ?: speedPoints,
        mediaType = if (refreshLegacyClipFields) {
            if (type == ClipType.IMAGE) ClipType.IMAGE else ClipType.VIDEO
        } else existing?.mediaType ?: if (type == ClipType.IMAGE) ClipType.IMAGE else ClipType.VIDEO,
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
        mask = if (refreshLegacyClipFields) mask?.toTypedMask() else existing?.mask ?: mask?.toTypedMask(),
        chromaKey = if (refreshLegacyClipFields) {
            chromaKeySettings?.toTypedChromaKey() ?: existing?.chromaKey
        } else {
            existing?.chromaKey ?: chromaKeySettings?.toTypedChromaKey()
        },
        blendMode = existing?.blendMode ?: BlendMode.NORMAL,
        volume = volume,
        volumeKeyframes = imported?.volumeKeyframes ?: existing?.volumeKeyframes.orEmpty(),
        pan = existing?.pan ?: 0f,
        panKeyframes = existing?.panKeyframes.orEmpty(),
        drawingLayer = if (refreshLegacyDrawing) legacyDrawing?.toTypedDrawingLayer(id)
            else existing?.drawingLayer ?: legacyDrawing?.toTypedDrawingLayer(id),
        denoiseEnabled = if (refreshLegacyClipFields) denoiseEnabled else existing?.denoiseEnabled ?: denoiseEnabled,
        denoiseStrength = if (refreshLegacyClipFields) denoiseStrength else existing?.denoiseStrength ?: denoiseStrength,
        backgroundReplace = if (refreshLegacyClipFields) backgroundReplace else existing?.backgroundReplace ?: backgroundReplace,
        gpuFilterConfig = if (refreshLegacyClipFields) gpuFilterConfig else existing?.gpuFilterConfig ?: gpuFilterConfig,
        projectVideoEffectId = if (refreshLegacyClipFields) projectVideoEffectId else existing?.projectVideoEffectId ?: projectVideoEffectId,
        videoCrop = if (refreshLegacyClipFields) videoCrop else existing?.videoCrop ?: videoCrop,
        faceCropTrack = if (refreshLegacyClipFields) faceCropTrack else existing?.faceCropTrack ?: faceCropTrack,
        pipX = if (refreshLegacyClipFields) pipX else existing?.pipX ?: pipX, pipY = if (refreshLegacyClipFields) pipY else existing?.pipY ?: pipY,
        pipScale = if (refreshLegacyClipFields) pipScale else existing?.pipScale ?: pipScale,
        pipRotationDeg = if (refreshLegacyClipFields) pipRotationDeg else existing?.pipRotationDeg ?: pipRotationDeg,
        pipOpacity = if (refreshLegacyClipFields) pipOpacity else existing?.pipOpacity ?: pipOpacity,
        overlayAspect = if (refreshLegacyClipFields) overlayAspect else existing?.overlayAspect ?: overlayAspect,
        overlayBlend = if (refreshLegacyClipFields) overlayBlend.toTypedBlend() else existing?.overlayBlend ?: overlayBlend.toTypedBlend(),
        overlayBorderWidth = if (refreshLegacyClipFields) overlayBorderWidth else existing?.overlayBorderWidth ?: overlayBorderWidth,
        overlayBorderColor = if (refreshLegacyClipFields) overlayBorderColor else existing?.overlayBorderColor ?: overlayBorderColor,
        overlayShadow = if (refreshLegacyClipFields) overlayShadow else existing?.overlayShadow ?: overlayShadow,
        overlayFlipH = if (refreshLegacyClipFields) overlayFlipH else existing?.overlayFlipH ?: overlayFlipH,
        overlayFlipV = if (refreshLegacyClipFields) overlayFlipV else existing?.overlayFlipV ?: overlayFlipV,
        overlayVolume = if (refreshLegacyClipFields) overlayVolume else existing?.overlayVolume ?: overlayVolume,
        photoEdit = if (refreshLegacyClipFields) photoEdit else existing?.photoEdit ?: photoEdit,
        workspaceEffects = if (refreshLegacyClipFields) workspaceEffects else existing?.workspaceEffects ?: workspaceEffects,
        videoFitMode = if (refreshLegacyClipFields) videoFitMode else existing?.videoFitMode ?: videoFitMode
    )
}

private fun OverlayItem.Video.toSharedVideoClip(): VideoClip = VideoClip(
    id = id,
    uri = uri,
    inMs = inMs,
    outMs = outMs,
    trimStartMs = trimStartMs,
    speed = speed,
    sourceName = sourceName,
    sourceDurationMs = sourceDurationMs,
    trimEndMs = trimEndMs,
    thumbnail = thumbnail,
    groupId = groupId,
    speedCurve = speedCurve,
    rampStartSpeed = rampStartSpeed,
    rampEndSpeed = rampEndSpeed,
    speedPoints = speedPoints,
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
    drawingLayer = drawingLayer,
    denoiseEnabled = denoiseEnabled,
    denoiseStrength = denoiseStrength,
    backgroundReplace = backgroundReplace,
    gpuFilterConfig = gpuFilterConfig,
    projectVideoEffectId = projectVideoEffectId,
    videoCrop = videoCrop,
    faceCropTrack = faceCropTrack,
    pipX = pipX,
    pipY = pipY,
    pipScale = pipScale,
    pipRotationDeg = pipRotationDeg,
    pipOpacity = pipOpacity,
    overlayAspect = overlayAspect,
    overlayBlend = overlayBlend,
    overlayBorderWidth = overlayBorderWidth,
    overlayBorderColor = overlayBorderColor,
    overlayShadow = overlayShadow,
    overlayFlipH = overlayFlipH,
    overlayFlipV = overlayFlipV,
    overlayVolume = overlayVolume,
    photoEdit = photoEdit,
    workspaceEffects = workspaceEffects,
    videoFitMode = videoFitMode
)

private fun OverlayItem.Image.toSharedVideoClip(): VideoClip = VideoClip(
    id = id,
    uri = uri,
    inMs = inMs,
    outMs = outMs,
    trimStartMs = trimStartMs,
    sourceName = sourceName,
    sourceDurationMs = sourceDurationMs,
    trimEndMs = trimEndMs,
    thumbnail = thumbnail,
    groupId = groupId,
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
    drawingLayer = drawingLayer,
    denoiseEnabled = denoiseEnabled,
    denoiseStrength = denoiseStrength,
    backgroundReplace = backgroundReplace,
    gpuFilterConfig = gpuFilterConfig,
    projectVideoEffectId = projectVideoEffectId,
    videoCrop = videoCrop,
    faceCropTrack = faceCropTrack,
    pipX = pipX,
    pipY = pipY,
    pipScale = pipScale,
    pipRotationDeg = pipRotationDeg,
    pipOpacity = pipOpacity,
    overlayAspect = overlayAspect,
    overlayBlend = overlayBlend,
    overlayBorderWidth = overlayBorderWidth,
    overlayBorderColor = overlayBorderColor,
    overlayShadow = overlayShadow,
    overlayFlipH = overlayFlipH,
    overlayFlipV = overlayFlipV,
    overlayVolume = overlayVolume,
    photoEdit = photoEdit,
    workspaceEffects = workspaceEffects,
    videoFitMode = videoFitMode
)

private fun VideoClip.toOverlayVideo(existing: OverlayItem.Video?): OverlayItem.Video = OverlayItem.Video(
    id = id,
    uri = uri,
    inMs = inMs,
    outMs = outMs,
    trimStartMs = trimStartMs,
    speed = speed,
    sourceName = sourceName,
    sourceDurationMs = sourceDurationMs,
    trimEndMs = trimEndMs,
    thumbnail = thumbnail,
    groupId = groupId,
    speedCurve = speedCurve,
    rampStartSpeed = rampStartSpeed,
    rampEndSpeed = rampEndSpeed,
    speedPoints = speedPoints,
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
    drawingLayer = drawingLayer,
    denoiseEnabled = denoiseEnabled, denoiseStrength = denoiseStrength, backgroundReplace = backgroundReplace,
    gpuFilterConfig = gpuFilterConfig, projectVideoEffectId = projectVideoEffectId, videoCrop = videoCrop,
    faceCropTrack = faceCropTrack, pipX = pipX, pipY = pipY, pipScale = pipScale, pipRotationDeg = pipRotationDeg,
    pipOpacity = pipOpacity, overlayAspect = overlayAspect, overlayBlend = overlayBlend,
    overlayBorderWidth = overlayBorderWidth, overlayBorderColor = overlayBorderColor, overlayShadow = overlayShadow,
    overlayFlipH = overlayFlipH, overlayFlipV = overlayFlipV, overlayVolume = overlayVolume,
    photoEdit = photoEdit, workspaceEffects = workspaceEffects, videoFitMode = videoFitMode
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
        val existingKey = existingByTime[time]
        val segmentCurve = times.getOrNull(index - 1)?.let(::curveAt)?.toLegacyCurve()
        val legacyCurve = when {
            existingKey == null -> segmentCurve ?: KeyframeCurve.LINEAR
            segmentCurve == null || segmentCurve == existingKey.curve -> existingKey.curve
            else -> segmentCurve
        }
        LegacyKeyframe(
            id = existingKey?.id ?: "shared-$id-$time",
            timeMs = time,
            translateX = value.position.x,
            translateY = value.position.y,
            scale = value.scale,
            rotationDeg = value.rotationDeg,
            opacity = value.opacity,
            volume = value.volume,
            effectIntensity = value.effectIntensity,
            filterIntensity = value.filterIntensity,
            saturation = value.saturation.takeIf { saturationKeyframes.any { key -> key.timeMs == time } },
            curve = legacyCurve
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
        bgArgb = bgArgb,
        strokeColorArgb = strokeColorArgb,
        shadowColorArgb = shadowColorArgb,
        fontFamily = fontFamily,
        isBold = isBold,
        isItalic = isItalic,
        presetId = presetId,
        animationType = animationType,
        animationDurationMs = animationDurationMs,
        outroAnimationType = outroAnimationType,
        outroAnimationDurationMs = outroAnimationDurationMs,
        textAlign = textAlign,
        letterSpacingEm = letterSpacingEm,
        gradientStartArgb = gradientStartArgb,
        gradientEndArgb = gradientEndArgb,
        mask = mask?.toTypedMask(),
        maskKeyframes = maskKeyframes,
        highlightStartUtf16 = highlightStartUtf16,
        highlightEndUtf16 = highlightEndUtf16,
        highlightSourceFingerprint = highlightSourceFingerprint,
        highlightColorArgb = highlightColorArgb,
        animationWindowStartMs = animationWindowStartMs,
        animationWindowEndMs = animationWindowEndMs,
        projectTextLayerId = projectTextLayerId,
        rangeEndExclusive = rangeEndExclusive,
        startMs = startMs,
        endMs = endMs,
        legacyBacked = true
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
        symbolOrUri = symbolOrUri,
        category = category,
        name = name,
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
        assetPath = assetPath,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        cutoutShape = cutoutShape,
        startMs = startMs,
        endMs = endMs,
        legacyBacked = true
    )
}

private fun TextItem.toLegacyTextOverlay(existing: LegacyTextOverlay): LegacyTextOverlay =
    existing.copy(
        text = text,
        x = position.x,
        y = position.y,
        sizeScale = scale,
        colorArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
        bgArgb = bgArgb,
        strokeColorArgb = strokeColorArgb,
        shadowColorArgb = shadowColorArgb,
        fontFamily = fontFamily,
        isBold = isBold,
        isItalic = isItalic,
        presetId = presetId,
        animationType = animationType,
        animationDurationMs = animationDurationMs,
        outroAnimationType = outroAnimationType,
        outroAnimationDurationMs = outroAnimationDurationMs,
        rotationDeg = rotationDeg,
        opacity = alpha,
        textAlign = textAlign,
        letterSpacingEm = letterSpacingEm,
        gradientStartArgb = gradientStartArgb,
        gradientEndArgb = gradientEndArgb,
        mask = mask?.toLegacyMask(),
        maskKeyframes = maskKeyframes,
        highlightStartUtf16 = highlightStartUtf16,
        highlightEndUtf16 = highlightEndUtf16,
        highlightSourceFingerprint = highlightSourceFingerprint,
        highlightColorArgb = highlightColorArgb,
        animationWindowStartMs = animationWindowStartMs,
        animationWindowEndMs = animationWindowEndMs,
        projectTextLayerId = projectTextLayerId,
        rangeEndExclusive = rangeEndExclusive,
        // Linked project text is one typed style/animation item split across
        // clips. Each legacy segment still owns its local active window.
        startMs = if (projectTextLayerId != null && existing.projectTextLayerId == projectTextLayerId) existing.startMs else startMs,
        endMs = if (projectTextLayerId != null && existing.projectTextLayerId == projectTextLayerId) existing.endMs else endMs,
        keyframes = toLegacyKeyframeTrack(existing)
    )

private fun StickerItem.toLegacyStickerOverlay(existing: LegacyStickerOverlay): LegacyStickerOverlay =
    existing.copy(
        symbolOrUri = symbolOrUri,
        category = category,
        name = name,
        x = position.x,
        y = position.y,
        sizeScale = scale,
        rotationDeg = rotationDeg,
        opacity = alpha,
        assetPath = assetPath,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        cutoutShape = cutoutShape,
        startMs = startMs,
        endMs = endMs,
        keyframes = toLegacyKeyframeTrack(existing)
    )

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


/** Explicit compatibility projection: typed timeline masks never store a VideoMask wrapper. */
internal fun VideoMask.toTypedMask(): Mask {
    val rect = RectF(centerX - sizeX / 2f, centerY - sizeY / 2f, centerX + sizeX / 2f, centerY + sizeY / 2f)
    val keys = maskKeyframes.map { it.toSharedMaskKeyframe() }
    return when (shape) {
        MaskShape.DRAW -> Mask.Pen(
            path = Path().apply { drawPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } },
            points = drawPoints.map { XY(it.x, it.y) }, feather = feather, inverted = invert,
            enabled = enabled, opacity = opacity, rotationDeg = rotationDeg, presetId = presetId,
            maskKeyframes = keys
        )
        MaskShape.BLUR_FACE -> Mask.BlurFaces(enabled, centerX, centerY, sizeX, sizeY, feather, invert, opacity, rotationDeg, presetId, keys)
        else -> Mask.Shape(shape.toTypedKind(), rect, feather, invert, emptyList(), enabled, opacity, rotationDeg, presetId, keys)
    }
}

internal fun Mask.toLegacyMask(): VideoMask = when (this) {
    is Mask.Shape -> VideoMask(
        shape = kind.toLegacyShape(), centerX = rect.centerX(), centerY = rect.centerY(),
        sizeX = rect.width(), sizeY = rect.height(), feather = feather, invert = inverted,
        opacity = opacity, enabled = enabled, rotationDeg = rotationDeg, presetId = presetId,
        maskKeyframes = maskKeyframes.map { it.toLegacyMaskKeyframe() }
            .ifEmpty { keyframes.mapIndexed { i, k -> LegacyMaskKeyframe("typed-$i-${k.timeMs}", k.timeMs, k.value.centerX(), k.value.centerY(), k.value.width(), k.value.height(), rotationDeg, feather, opacity, inverted) } }
    )
    is Mask.Pen -> VideoMask(
        shape = MaskShape.DRAW, feather = feather, invert = inverted, opacity = opacity,
        enabled = enabled, rotationDeg = rotationDeg, presetId = presetId,
        drawPoints = points.map { MaskPoint(it.x, it.y) }, maskKeyframes = maskKeyframes.map { it.toLegacyMaskKeyframe() }
    )
    is Mask.BlurFaces -> VideoMask(
        shape = MaskShape.BLUR_FACE, centerX = centerX, centerY = centerY, sizeX = sizeX, sizeY = sizeY,
        feather = feather, invert = inverted, opacity = opacity, enabled = enabled,
        rotationDeg = rotationDeg, presetId = presetId, maskKeyframes = maskKeyframes.map { it.toLegacyMaskKeyframe() }
    )
}

private fun LegacyMaskKeyframe.toSharedMaskKeyframe() = SharedMaskKeyframe(
    id, timeMs, RectF(centerX - sizeX / 2f, centerY - sizeY / 2f, centerX + sizeX / 2f, centerY + sizeY / 2f),
    rotationDeg, feather, opacity, invert, Curve.LINEAR
)

private fun SharedMaskKeyframe.toLegacyMaskKeyframe() = LegacyMaskKeyframe(
    id, timeMs, rect.centerX(), rect.centerY(), rect.width(), rect.height(), rotationDeg, feather, opacity, inverted
)

private fun MaskShape.toTypedKind() = when (this) {
    MaskShape.CIRCLE -> Mask.Kind.CIRCLE
    MaskShape.RECTANGLE -> Mask.Kind.RECTANGLE
    MaskShape.HEART -> Mask.Kind.HEART
    MaskShape.STAR -> Mask.Kind.STAR
    MaskShape.TRIANGLE -> Mask.Kind.TRIANGLE
    MaskShape.ELLIPSE -> Mask.Kind.ELLIPSE
    MaskShape.LINEAR -> Mask.Kind.HORIZONTAL
    MaskShape.MIRROR -> Mask.Kind.MIRROR
    MaskShape.DRAW, MaskShape.BLUR_FACE -> Mask.Kind.RECTANGLE
}

private fun Mask.Kind.toLegacyShape() = when (this) {
    Mask.Kind.CIRCLE -> MaskShape.CIRCLE
    Mask.Kind.RECTANGLE -> MaskShape.RECTANGLE
    Mask.Kind.HEART -> MaskShape.HEART
    Mask.Kind.STAR -> MaskShape.STAR
    Mask.Kind.TRIANGLE -> MaskShape.TRIANGLE
    Mask.Kind.ELLIPSE -> MaskShape.ELLIPSE
    Mask.Kind.HORIZONTAL -> MaskShape.LINEAR
    Mask.Kind.MIRROR -> MaskShape.MIRROR
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

private fun com.apexstudio.app.domain.model.OverlayBlend.toTypedBlend(): BlendMode = when (this) {
    com.apexstudio.app.domain.model.OverlayBlend.NORMAL -> BlendMode.NORMAL
    com.apexstudio.app.domain.model.OverlayBlend.SCREEN -> BlendMode.SCREEN
    com.apexstudio.app.domain.model.OverlayBlend.MULTIPLY -> BlendMode.MULTIPLY
    com.apexstudio.app.domain.model.OverlayBlend.OVERLAY -> BlendMode.OVERLAY
    com.apexstudio.app.domain.model.OverlayBlend.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
}
private fun BlendMode.toLegacyBlend(): com.apexstudio.app.domain.model.OverlayBlend = when (this) {
    BlendMode.NORMAL -> com.apexstudio.app.domain.model.OverlayBlend.NORMAL
    BlendMode.SCREEN -> com.apexstudio.app.domain.model.OverlayBlend.SCREEN
    BlendMode.MULTIPLY -> com.apexstudio.app.domain.model.OverlayBlend.MULTIPLY
    BlendMode.OVERLAY -> com.apexstudio.app.domain.model.OverlayBlend.OVERLAY
    BlendMode.SOFT_LIGHT -> com.apexstudio.app.domain.model.OverlayBlend.SOFT_LIGHT
}
