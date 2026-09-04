package com.apexstudio.app.data.template

import kotlinx.serialization.Serializable

/**
 * A production-ready "transmission" preset: one bundled 3D LUT
 * (looked up via [FilterManifest.presetById]) combined with one
 * FX preset (looked up via [com.apexstudio.app.data.fx.FxPreset.byId])
 * and one transition type (looked up via
 * [com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType]).
 *
 * Loaded from `assets/transmission_templates.json` by
 * [TimelineTemplateManager.loadTransmissionTemplates]. The catalog
 * is validated against the live manifest + FX enum at load time so
 * renaming a LUT or FX surfaces a `missing-id` warning instead of a
 * silent crash in the editor.
 *
 * Distinct from [TimelineTemplate]:
 *  - `TimelineTemplate` is a *full project*: ordered clips with
 *    URIs, per-clip filters/effects, transitions between named
 *    clips, audio tracks, text overlays.
 *  - `TransmissionTemplate` is a *single-look preset*: a LUT +
 *    FX + transition combo the user applies to whatever clips
 *    they currently have on the timeline.
 */
@Serializable
data class TransmissionTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val resolution: String = "1080p",
    val fps: Int = 60,
    val aspectRatio: String = "16:9",
    val filterId: String,
    val filterIntensity: Float = 1f,
    val fxPresetId: String,
    val fxIntensity: Float = 1f,
    val transitionType: String, // matches TransitionType.id values: "cross", "wipe", "zoom", "slide", "glitch"
    val transitionDurationMs: Long = 500L,
    val defaultIntensity: Float = 1f,
    val previewAccentArgb: Long = 0xFF6E5BFFL,
    val tags: List<String> = emptyList()
)