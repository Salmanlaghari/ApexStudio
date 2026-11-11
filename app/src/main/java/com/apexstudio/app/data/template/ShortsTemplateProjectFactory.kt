package com.apexstudio.app.data.template

import com.apexstudio.app.data.packs.download.DownloadEngine
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoFitMode
import java.io.File
import java.util.UUID

/** Pure transformation from one imported-video project plus a verified music file. */
object ShortsTemplateProjectFactory {
    fun apply(
        project: Project,
        template: ShortsTemplate,
        asset: ShortsMusicAsset,
        downloadedMusic: File,
    ): Project {
        require(asset.id == template.musicAssetId) {
            "Music asset ${asset.id} does not match template ${template.id}"
        }
        require(downloadedMusic.name == asset.fileName) {
            "Downloaded Shorts music filename does not match manifest"
        }
        return apply(project, template, downloadedMusic, asset)
    }

    private fun apply(
        project: Project,
        template: ShortsTemplate,
        downloadedMusic: File,
        asset: ShortsMusicAsset,
    ): Project {
        require(downloadedMusic.isFile) { "Shorts music file does not exist: ${downloadedMusic.path}" }
        require(asset.bytes > 0L && downloadedMusic.length() == asset.bytes) {
            "Downloaded Shorts music size does not match manifest"
        }
        require(asset.sha256.matches(Regex("[0-9a-fA-F]{64}")) && DownloadEngine.verifySha256(downloadedMusic, asset.sha256)) {
            "Downloaded Shorts music SHA-256 does not match manifest"
        }
        require(project.clips.size == 1 && project.clips.single().type == ClipType.VIDEO) {
            "Shorts templates require exactly one main VIDEO clip"
        }
        val original = project.clips.single()
        val sourceStart = original.trimStartMs.coerceAtLeast(0L)
        val sourceEnd = original.trimEndMs.coerceAtMost(original.durationMs)
        require(sourceEnd > sourceStart) { "Main VIDEO clip has no playable trim range" }
        val trackName = asset.trackTitle
        val assetDurationMs = asset.durationMs
        val duration = minOf(template.targetDurationMs, sourceEnd - sourceStart)
        require(duration > 0L) { "Main VIDEO clip is empty" }
        val clipEnd = sourceStart + duration
        val clip = original.copy(
            trimStartMs = sourceStart,
            trimEndMs = clipEnd,
            videoFitMode = VideoFitMode.COVER,
            textOverlays = template.sampleOverlays(duration),
        )
        val fade = minOf(500L, duration / 4L)
        val musicSourceDuration = assetDurationMs.takeIf { it > 0L } ?: duration
        val musicDuration = minOf(duration, musicSourceDuration)
        val music = AudioTrack(
            id = "${template.id}-music-${UUID.randomUUID()}",
            name = trackName,
            uri = downloadedMusic.toURI().toString(),
            volume = 0.35f,
            trimStartMs = 0L,
            trimEndMs = musicDuration,
            startTimeMs = 0L,
            sourceDurationMs = musicSourceDuration,
            fadeInMs = fade,
            fadeOutMs = fade,
            kind = AudioTrack.Kind.MUSIC,
            sourceLicense = asset.license,
            sourceLicenseUrl = asset.licenseUrl,
            attributionRequired = asset.attributionRequired,
            attributionText = asset.creditText,
            sourcePageUrl = asset.sourcePageUrl,
            sourceChanges = asset.changes,
        )
        return project.copy(
            durationMs = duration,
            clips = listOf(clip),
            audioTracks = listOf(music),
            videoAspectRatio = 9f / 16f,
            lastTransmissionTemplateId = template.visualPresetId,
        )
    }
}
