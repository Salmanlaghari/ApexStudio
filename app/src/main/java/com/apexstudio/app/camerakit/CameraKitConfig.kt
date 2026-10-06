package com.apexstudio.app.camerakit

import com.apexstudio.app.BuildConfig

/**
 * Static configuration for the Snap Camera Kit SDK integration.
 *
 * The API token is injected at build time into [BuildConfig.SNAP_CAMERA_KIT_TOKEN]:
 * Gradle reads it from the `SNAP_CAMERA_KIT_TOKEN` environment variable (CI injects it
 * from the `SNAP_CAMERA_KIT_TOKEN` GitHub Actions secret) and falls back to
 * `local.properties` for local builds. It is never hardcoded here.
 */
object CameraKitConfig {
    /** Demo Lens Group ID created in the Snap developer portal for the "Apexstudio" app. */
    const val DEMO_LENS_GROUP_ID = "d33b8566-1687-43d5-8974-d9d8be37bfb3"

    /** Staging API token from BuildConfig; blank when not configured. */
    val apiToken: String
        get() = BuildConfig.SNAP_CAMERA_KIT_TOKEN

    /** True when a (staging) API token is available for this build. */
    val isConfigured: Boolean
        get() = apiToken.isNotBlank()
}
