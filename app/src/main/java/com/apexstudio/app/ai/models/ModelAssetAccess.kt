package com.apexstudio.app.ai.models

import android.content.Context
import java.io.InputStream

/** Shared synchronous asset provider for model adapters; call from an IO worker. */
object ModelAssetAccess {
    /**
     * Opens a checksum-verified installed model file when present; otherwise opens the
     * original APK asset. Missing stripped assets fail normally until the user downloads
     * that model pack. This method never initiates network activity.
     */
    fun open(context: Context, assetPath: String): InputStream {
        require(assetPath.startsWith("models/") && !assetPath.contains("..")) { "Invalid model asset path" }
        val app = context.applicationContext
        val installed = OnDemandModelPacks.forApp(app).verifiedInstalledFileForAsset(assetPath)
        return installed?.inputStream() ?: app.assets.open(assetPath)
    }

    /** Verified installed model path, or null; does not download or access bundled bytes. */
    fun installedFile(context: Context, assetPath: String) =
        OnDemandModelPacks.forApp(context.applicationContext).verifiedInstalledFileForAsset(assetPath)
}
