package com.apexstudio.app.data.photoedit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.apexstudio.app.domain.model.PhotoCropRect
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.VideoAdjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPreviewExportParityTest {
    @Test fun persistedAiImageAndEditsRenderIdenticallyInPreviewAndExportAndWriteComparisonPng(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sourceFile = File(context.cacheDir, "photo_parity_original.png")
        val enhancedDirectory = File(context.filesDir, "photo_ai_upscale").apply { mkdirs() }
        val enhancedFile = File(enhancedDirectory, "photo_parity_enhanced.png")
        val original = markerBitmap(128, 96, enhanced = false)
        val enhanced = markerBitmap(256, 192, enhanced = true)
        try {
            sourceFile.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            enhancedFile.outputStream().use { assertTrue(enhanced.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            original.recycle()
            enhanced.recycle()
        }

        val originalUri = Uri.fromFile(sourceFile).toString()
        val enhancedUri = Uri.fromFile(enhancedFile).toString()
        val settings = PhotoEditSettings(
            crop = PhotoCropRect(0.12f, 0.08f, 0.88f, 0.92f),
            cropAspectPreset = "free",
            rotationSteps = 1,
            flipHorizontal = true,
            aiUpscaledImageUri = enhancedUri,
            adjustments = VideoAdjustments(brightness = 0.08f, contrast = 1.07f, saturation = 1.04f)
        )
        assertEquals(originalUri, PhotoEditRenderer.resolveSourceUri(
            context, originalUri, settings.copy(aiUpscaledImageUri = sourceFile.toURI().toString())
        ))
        val previewSourceUri = PhotoEditRenderer.resolveSourceUri(context, originalUri, settings)
        assertEquals(enhancedUri, previewSourceUri)
        val previewSource = PhotoEditRenderer.loadBitmap(context, previewSourceUri, maxDim = 1280)!!
        val preview = PhotoEditRenderer.applyEdits(previewSource, settings, null)
        val export = PhotoEditRenderer.renderEdited(context, originalUri, settings, maxDim = 1280)!!
        try {
            assertEquals(preview.width, export.width)
            assertEquals(preview.height, export.height)
            assertArrayEquals(bitmapPixels(preview), bitmapPixels(export))
            writeSideBySideArtifact(preview, export)
        } finally {
            if (preview !== previewSource) preview.recycle()
            previewSource.recycle()
            export.recycle()
        }
    }

    private fun markerBitmap(width: Int, height: Int, enhanced: Boolean): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val colors = if (enhanced) {
                intArrayOf(Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.WHITE)
            } else {
                intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK)
            }
            for (y in 0 until height) for (x in 0 until width) {
                val quadrant = (if (x >= width / 2) 1 else 0) + (if (y >= height / 2) 2 else 0)
                val stripe = ((x / 13) + (y / 11)) % 2 == 0
                val color = if (stripe) colors[quadrant] else Color.rgb(
                    Color.red(colors[quadrant]) / 2,
                    Color.green(colors[quadrant]) / 2,
                    Color.blue(colors[quadrant]) / 2
                )
                setPixel(x, y, color)
            }
        }

    private fun bitmapPixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun writeSideBySideArtifact(preview: Bitmap, export: Bitmap) {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val output = File(root, "app/build/reports/photo-preview-export/preview-export-side-by-side.png")
        output.parentFile!!.mkdirs()
        val scale = 2
        val labelHeight = 36
        val gutter = 24
        val panelWidth = preview.width * scale
        val panelHeight = preview.height * scale
        val comparison = Bitmap.createBitmap(panelWidth * 2 + gutter, panelHeight + labelHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(comparison)
            canvas.drawColor(Color.rgb(22, 24, 32))
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            canvas.drawText("PREVIEW", 8f, 25f, text)
            canvas.drawText("EXPORT", (panelWidth + gutter + 8).toFloat(), 25f, text)
            val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(preview, null, Rect(0, labelHeight, panelWidth, labelHeight + panelHeight), imagePaint)
            canvas.drawBitmap(export, null,
                Rect(panelWidth + gutter, labelHeight, panelWidth * 2 + gutter, labelHeight + panelHeight), imagePaint)
            val divider = Paint().apply { color = Color.rgb(90, 96, 112); strokeWidth = 2f }
            canvas.drawLine((panelWidth + gutter / 2).toFloat(), 0f,
                (panelWidth + gutter / 2).toFloat(), comparison.height.toFloat(), divider)
            output.outputStream().use { assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue(output.isFile && output.length() > 0L)
        } finally {
            comparison.recycle()
        }
    }
}
