package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.apexstudio.app.data.export.ProjectTransitionTimeline
import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath

/** A new item must not inherit the preceding item's instantaneous speed-curve rate. */
internal fun bindEditorPreviewItem(
    player: Player,
    item: MediaItem,
    clip: MediaClip,
    clips: List<MediaClip>,
    projectPositionMs: Long,
    playing: Boolean,
    transitionPlan: ProjectTransitionTimeline.Plan? = null
) {
    player.setMediaItem(item)
    player.prepare()
    player.playbackParameters = PlaybackParameters(SpeedCurveMath.sanitizeConstantSpeed(clip.speedMultiplier))
    player.seekTo(
        transitionPlan?.sourceTimeAt(projectPositionMs)
            ?: ClipTimeMapping.sourceAtProjectTime(clips, clip, projectPositionMs)
    )
    player.playWhenReady = playing
}

/** Never replay the previous source while a project seek is waiting for its new item binding. */
internal fun resumeEditorPreview(
    player: Player,
    clips: List<MediaClip>,
    projectPositionMs: Long,
    transitionPlan: ProjectTransitionTimeline.Plan? = null
) {
    val clip = transitionPlan?.entryAt(projectPositionMs)?.clip ?: timelinePreviewClip(clips, projectPositionMs)
    if (clip?.type != ClipType.VIDEO || player.currentMediaItem?.mediaId != clip.id) {
        player.pause()
        return
    }
    if (player.playbackState == Player.STATE_ENDED) {
        player.seekTo(
            transitionPlan?.sourceTimeAt(projectPositionMs)
                ?: ClipTimeMapping.sourceAtProjectTime(clips, clip, projectPositionMs)
        )
    }
    player.play()
}
