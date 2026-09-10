package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Handles custom uploaded/provided thumbnail images for filter presets (Option B).
 * Automatically crops provided images to 1:1 filter card aspect ratio and caches them.
 */
object FilterThumbnailAssetHandler {
    private const val TAG = "FilterThumbHandler"
    private const val THUMB_DIMEN = 128

    private val memoryCache = mutableMapOf<String, ImageBitmap>()

    fun getCustomThumbnail(context: Context, filterId: String?): ImageBitmap? {
        if (filterId == null) return null
        memoryCache[filterId]?.let { return it }

        // 1. Check custom uploaded/provided thumbnail in app files directory
        try {
            val customDir = File(context.filesDir, "filter_thumbnails")
            val candidatePng = File(customDir, "$filterId.png")
            val candidateJpg = File(customDir, "$filterId.jpg")
            val targetFile = if (candidatePng.exists()) candidatePng else if (candidateJpg.exists()) candidateJpg else null

            if (targetFile != null && targetFile.length() > 0L) {
                val rawBitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
                if (rawBitmap != null) {
                    val cropped = cropToSquare(rawBitmap, THUMB_DIMEN)
                    val imgBmp = cropped.asImageBitmap()
                    memoryCache[filterId] = imgBmp
                    return imgBmp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking custom thumbnail for $filterId in filesDir", e)
        }

        // 2. Check bundled asset folder "filter_thumbnails/<filterId>.png"
        try {
            val assetPath = "filter_thumbnails/$filterId.png"
            context.assets.open(assetPath).use { stream ->
                val rawBitmap = BitmapFactory.decodeStream(stream)
                if (rawBitmap != null) {
                    val cropped = cropToSquare(rawBitmap, THUMB_DIMEN)
                    val imgBmp = cropped.asImageBitmap()
                    memoryCache[filterId] = imgBmp
                    return imgBmp
                }
            }
        } catch (_: Exception) {
            // Asset not present, return null for fallback
        }

        return null
    }

    /**
     * Upload or provide a custom thumbnail image for a filter preset.
     * Automatically crops to 1:1 card ratio and saves to persistent storage.
     */
    fun registerCustomThumbnail(context: Context, filterId: String, bitmap: Bitmap): ImageBitmap {
        val cropped = cropToSquare(bitmap, THUMB_DIMEN)
        val customDir = File(context.filesDir, "filter_thumbnails").apply { mkdirs() }
        val targetFile = File(customDir, "$filterId.png")
        try {
            FileOutputStream(targetFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving custom thumbnail for $filterId", e)
        }
        val imgBmp = cropped.asImageBitmap()
        memoryCache[filterId] = imgBmp
        return imgBmp
    }

    private fun cropToSquare(src: Bitmap, targetSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        val cropSide = min(w, h)
        val x = (w - cropSide) / 2
        val y = (h - cropSide) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cropSide, cropSide)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped != src && cropped != scaled) {
            cropped.recycle()
        }
        return scaled
    }
}
