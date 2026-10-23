package com.apexstudio.app.ai.models

import android.content.res.AssetManager
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Source fixtures, independent of APK exclusions. Native AssetManager requires an APK/ZIP, not a directory. */
internal object SourceModelAssets {
    private val fixtureArchive: File by lazy {
        val assetsRoot = sequenceOf(File("src/main/assets"), File("app/src/main/assets"))
            .firstOrNull { File(it, "models").isDirectory }
            ?: error("Could not locate source-controlled app/src/main/assets")
        File.createTempFile("apex-source-model-fixtures-", ".apk").apply {
            deleteOnExit()
            ZipOutputStream(outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                File(assetsRoot, "models").walkTopDown().filter { it.isFile }.forEach { source ->
                    zip.putNextEntry(ZipEntry("assets/" + source.relativeTo(assetsRoot).invariantSeparatorsPath))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun assetManager(): AssetManager {
        val manager = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).apply { isAccessible = true }
        val cookie = addAssetPath.invoke(manager, fixtureArchive.absolutePath) as Int
        check(cookie != 0) { "Could not mount source model fixture APK" }
        return manager
    }
}
