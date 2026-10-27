package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurve
import kotlin.math.abs

internal enum class OriginalVideoAudioBlockReason(val userMessage: String) {
    NOT_MAIN_VIDEO("Select a main-track video clip to separate its audio."),
    TIME_REMAP("Reset speed and ramps to 1× before separating audio so the detached track stays in sync."),
    INVALID_TRIM("The selected video has an invalid trim range."),
    ALREADY_SEPARATED("This video's audio is already on the timeline."),
    NO_READABLE_AUDIO("This video has no readable embedded audio track."),
}

internal sealed interface OriginalVideoAudioExtractionPlan {
    data class Ready(val track: AudioTrack, val mutedSourceClip: MediaClip) : OriginalVideoAudioExtractionPlan
    data class Blocked(val reason: OriginalVideoAudioBlockReason) : OriginalVideoAudioExtractionPlan
}

/**
 * Build the editable A0 track and muted source clip without touching project state.
 * Time-remapped clips are deliberately blocked: a detached constant-speed audio
 * track cannot match a slow-motion or variable-speed video render.
 */
internal fun planOriginalVideoAudioExtraction(
    clip: MediaClip,
    sourceUri: String,
    timelineStartMs: Long,
    existingTracks: List<AudioTrack>,
    sourceHasAudio: Boolean,
    trackId: String,
): OriginalVideoAudioExtractionPlan {
    if (clip.type != ClipType.VIDEO || clip.trackIndex != 0) {
        return OriginalVideoAudioExtractionPlan.Blocked(OriginalVideoAudioBlockReason.NOT_MAIN_VIDEO)
    }
    val constantNormalSpeed = abs(clip.speedMultiplier - 1f) <= 0.0001f &&
        clip.speedCurve == SpeedCurve.LINEAR &&
        abs(clip.rampStartSpeed - 1f) <= 0.0001f &&
        abs(clip.rampEndSpeed - 1f) <= 0.0001f &&
        clip.speedPoints.isEmpty()
    if (!constantNormalSpeed) {
        return OriginalVideoAudioExtractionPlan.Blocked(OriginalVideoAudioBlockReason.TIME_REMAP)
    }
    if (clip.durationMs <= 0L || clip.trimStartMs < 0L || clip.trimEndMs <= clip.trimStartMs ||
        clip.trimEndMs > clip.durationMs
    ) {
        return OriginalVideoAudioExtractionPlan.Blocked(OriginalVideoAudioBlockReason.INVALID_TRIM)
    }
    if (existingTracks.any { it.sourceVideoClipId == clip.id }) {
        return OriginalVideoAudioExtractionPlan.Blocked(OriginalVideoAudioBlockReason.ALREADY_SEPARATED)
    }
    if (!sourceHasAudio) {
        return OriginalVideoAudioExtractionPlan.Blocked(OriginalVideoAudioBlockReason.NO_READABLE_AUDIO)
    }

    val track = AudioTrack(
        id = trackId,
        name = "${clip.name.ifBlank { "Video" }} · audio",
        uri = sourceUri,
        volume = clip.volume.coerceIn(0f, 1f),
        trimStartMs = clip.trimStartMs,
        trimEndMs = clip.trimEndMs,
        startTimeMs = timelineStartMs.coerceAtLeast(0L),
        sourceDurationMs = clip.durationMs,
        kind = AudioTrack.Kind.ORIGINAL_VIDEO,
        sourceVideoClipId = clip.id,
    )
    return OriginalVideoAudioExtractionPlan.Ready(
        track = track,
        mutedSourceClip = clip.copy(volume = 0f),
    )
}
