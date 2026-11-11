package com.apexstudio.app.data.stickers

import android.content.Context
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.apexstudio.app.data.packs.download.PackManager
import java.io.File

/** Resolves bundled Lottie assets and SHA-verified files installed by [PackManager]. */
object StickerAssetResolver {

    const val PACK_URI_PREFIX = "stickerpack://"

    private val packIdPattern = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")

    /** Stable project path for a file inside an already verified sticker pack. */
    fun packUri(packId: String, relativePath: String): String {
        require(packIdPattern.matches(packId)) { "Invalid sticker pack id" }
        require(isSafeRelativePath(relativePath)) { "Invalid sticker asset path" }
        return "$PACK_URI_PREFIX$packId/$relativePath"
    }

    fun isPackUri(assetPath: String?): Boolean = assetPath?.startsWith(PACK_URI_PREFIX) == true

    /** Resolve only files contained by an installed pack directory. */
    fun downloadedFile(context: Context, assetPath: String?): File? {
        if (!isPackUri(assetPath)) return null
        val remainder = assetPath!!.removePrefix(PACK_URI_PREFIX)
        val slash = remainder.indexOf('/')
        if (slash <= 0 || slash == remainder.lastIndex) return null
        val packId = remainder.substring(0, slash)
        val relativePath = remainder.substring(slash + 1)
        if (!packIdPattern.matches(packId) || !isSafeRelativePath(relativePath)) return null

        return try {
            val root = PackManager.packDir(context.applicationContext, packId).canonicalFile
            val candidate = File(root, relativePath).canonicalFile
            if (!candidate.isFile || !candidate.path.startsWith(root.path + File.separator)) null
            else candidate
        } catch (_: Exception) {
            null
        }
    }

    /** True for a bundled Lottie JSON or a stickerpack URI; other JSON files are not stickers. */
    fun isLottieSticker(assetPath: String?): Boolean =
        assetPath?.endsWith(".json", ignoreCase = true) == true &&
            (assetPath.startsWith("lottie/") ||
                assetPath.startsWith("stickers/animated/") ||
                isPackUri(assetPath))

    /** Composition source used by the picker and editor canvas. */
    fun compositionSpec(context: Context, assetPath: String): LottieCompositionSpec =
        if (isPackUri(assetPath)) {
            LottieCompositionSpec.File(downloadedFile(context, assetPath)?.absolutePath ?: assetPath)
        } else {
            LottieCompositionSpec.Asset(assetPath)
        }

    /** Synchronously parse the exact composition used by the export frame cache. */
    fun loadComposition(context: Context, assetPath: String): LottieComposition? {
        return try {
            if (isPackUri(assetPath)) {
                val file = downloadedFile(context, assetPath) ?: return null
                val cacheKey = "stickerpack:$assetPath:${file.length()}:${file.lastModified()}"
                file.inputStream().use { input ->
                    LottieCompositionFactory.fromJsonInputStreamSync(input, cacheKey).value
                }
            } else {
                LottieCompositionFactory.fromAssetSync(context, assetPath).value
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
}
