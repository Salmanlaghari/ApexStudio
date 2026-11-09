package com.apexstudio.app.domain.model.timeline

import com.apexstudio.app.domain.model.Project

/**
 * Upgrade legacy projects to the current typed timeline schema, or refresh the
 * compatibility projection for projects whose typed timeline is already authoritative.
 * The separate timing-model version is deliberately left unchanged.
 */
internal fun Project.withCurrentTimelineAuthority(): Project {
    val currentVersion = Project.CURRENT_TIMELINE_AUTHORITY_VERSION
    val normalized = if (timelineAuthorityVersion < currentVersion) {
        syncSharedTimeline(
            importLegacyAnimation = true,
            refreshLegacyAnimation = true,
            refreshLegacyClipFields = true,
            refreshLegacyAudioFields = true,
            refreshLegacyTextStickerFields = true
        )
    } else {
        syncSharedTimeline()
    }
    return normalized.copy(timelineAuthorityVersion = maxOf(timelineAuthorityVersion, currentVersion))
}
