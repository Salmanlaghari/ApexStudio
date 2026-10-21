package com.apexstudio.app.data.color

import android.graphics.Bitmap
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorStudioLutTest {
    @Test fun identityPreservesRgbAndAlphaAtBothEdgesAndInterior() {
        val lut = ColorStudioLut(ColorStudioGrade())
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x77648FC8, 0xFF1198EB.toInt()).forEach { color ->
            val result = lut.processColor(color)
            assertEquals(color ushr 24, result ushr 24)
            for (shift in listOf(0, 8, 16)) assertTrue(abs(((color ushr shift) and 255) - ((result ushr shift) and 255)) <= 1)
        }
    }

    @Test fun processedBitmapIsFreshAndOriginalBytesRemainUntouched() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, 0xFF445566.toInt()); source.setPixel(1, 0, 0xFFAABBCC.toInt())
        val original = source.getPixel(0, 0)
        val result = ColorStudioLut(ColorStudioGrade(brightness = 0.2f)).process(source)
        assertNotSame(source, result)
        assertEquals(original, source.getPixel(0, 0))
        assertNotEquals(original, result.getPixel(0, 0))
        assertFalse(source.isRecycled)
    }

    @Test fun combinedGradeKeepsAlphaAndClampsChannelsWhileApplyingStandardContrastAndBrightness() {
        val source = 0x3764A0E0
        val flatContrast = ColorStudioLut(ColorStudioGrade(contrast = 0f)).processColor(source)
        assertEquals(source ushr 24, flatContrast ushr 24)
        for (shift in listOf(0, 8, 16)) assertEquals(128, (flatContrast ushr shift) and 255)

        val lifted = ColorStudioLut(ColorStudioGrade(brightness = .2f)).processColor(0x801A1A1A.toInt())
        assertEquals(0x80, lifted ushr 24)
        assertTrue(((lifted ushr 16) and 255) > 26)
        assertTrue(((lifted ushr 16) and 255) in 0..255)
        assertTrue(((lifted ushr 8) and 255) in 0..255)
        assertTrue((lifted and 255) in 0..255)
    }

    @Test fun alphaIsPreservedForEveryPixelInFreshProcessedBitmap() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, 0x00123456)
        source.setPixel(1, 0, 0x7F789ABC)
        source.setPixel(2, 0, 0xFFDEF012.toInt())
        val before = IntArray(3)
        source.getPixels(before, 0, 3, 0, 0, 3, 1)
        val result = ColorStudioLut(ColorStudioGrade(brightness = .15f,
            shadows = ColorWheelVector(.7f, -.2f))).process(source)
        val after = IntArray(3)
        result.getPixels(after, 0, 3, 0, 0, 3, 1)
        for (i in before.indices) assertEquals(before[i] ushr 24, after[i] ushr 24)
        assertArrayEquals(before, run { IntArray(3).also { source.getPixels(it, 0, 3, 0, 0, 3, 1) } })
    }

    @Test fun allThreeWheelsAndEveryCurveChannelChangeRealPixels() {
        val original = 0xFF668899.toInt()
        val baseline = ColorStudioLut(ColorStudioGrade()).processColor(original)
        val grades = listOf(
            ColorStudioGrade(shadows = ColorWheelVector(0.8f, 0.2f)),
            ColorStudioGrade(midtones = ColorWheelVector(0.8f, 0.2f)),
            ColorStudioGrade(highlights = ColorWheelVector(0.8f, 0.2f)),
            ColorStudioGrade(rCurve = listOf(0f, 0.5f, 0.75f, 0.9f, 1f)),
            ColorStudioGrade(gCurve = listOf(0f, 0.5f, 0.75f, 0.9f, 1f)),
            ColorStudioGrade(bCurve = listOf(0f, 0.5f, 0.75f, 0.9f, 1f)),
            ColorStudioGrade(lumaCurve = listOf(0f, 0.5f, 0.75f, 0.9f, 1f)),
            ColorStudioGrade(contrast = 1.6f), ColorStudioGrade(saturation = 0f)
        )
        grades.forEach { assertNotEquals(it.toString(), baseline, ColorStudioLut(it).processColor(original)) }
    }

    @Test fun cubeFileUsesSameLatticeAndStandardRedFastestOrdering() {
        val lut = ColorStudioLut(ColorStudioGrade(brightness = 0.1f, midtones = ColorWheelVector(0.5f, -0.3f)), size = 3)
        val file = File.createTempFile("color-studio-", ".cube")
        try {
            lut.writeCube(file)
            val lines = file.readLines()
            assertTrue(lines.contains("LUT_3D_SIZE 3"))
            val samples = lines.filter { it.firstOrNull()?.isDigit() == true }.map { row -> row.split(' ').map(String::toFloat) }
            assertEquals(27, samples.size)
            listOf(0, 2, 6, 8, 18, 20, 24, 26).forEach { index ->
                val r = if (index % 3 == 0) 0 else 255
                val g = if (index / 3 % 3 == 0) 0 else 255
                val b = if (index / 9 == 0) 0 else 255
                val pixel = lut.processColor(-0x1000000 or (r shl 16) or (g shl 8) or b)
                for (c in 0..2) assertEquals(samples[index][c] * 255f, ((pixel ushr (16 - 8 * c)) and 255).toFloat(), 0.51f)
            }
        } finally { file.delete() }
    }

    @Test fun exportLutAndProcessedFrameAreAvailableForHostFfmpegPixelComparison() {
        // Deterministic test chart only; never user media or an app placeholder.
        val dir = File("build/outputs/color-lut-parity").apply { mkdirs() }
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0..15) for (x in 0..15) source.setPixel(x, y,
            -0x1000000 or ((x * 17) shl 16) or ((y * 17) shl 8) or ((x * 11 + y * 7) % 256))
        val lut = ColorStudioLut(ColorStudioGrade(brightness = 0.07f, contrast = 1.15f,
            saturation = 1.2f, shadows = ColorWheelVector(-0.35f, 0.3f),
            midtones = ColorWheelVector(0.2f, -0.3f), highlights = ColorWheelVector(0.3f, 0.1f),
            rCurve = listOf(0f, 0.2f, 0.6f, 0.8f, 1f), lumaCurve = listOf(0f, 0.22f, 0.52f, 0.78f, 1f)))
        File(dir, "original.png").outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val processed = lut.process(source)
        File(dir, "processed-preview.png").outputStream().use { assertTrue(processed.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        lut.writeCube(File(dir, "combined-grade.cube"))
    }

    @Test fun invalidInputsNeverPoisonPixelsOrCubeExport() {
        val invalid = ColorStudioGrade(brightness = Float.NaN, contrast = Float.POSITIVE_INFINITY,
            saturation = Float.NaN, shadows = ColorWheelVector(Float.NaN, Float.NEGATIVE_INFINITY),
            rCurve = listOf(Float.NaN))
        assertEquals(ColorStudioLut(ColorStudioGrade()).processColor(0xFF123456.toInt()),
            ColorStudioLut(invalid).processColor(0xFF123456.toInt()))
    }

    @Test fun projectGradeValidationRejectsNonFiniteAndOutOfRangeValues() {
        assertTrue(ColorStudioGrade().hasValidRenderParameters())
        assertFalse(ColorStudioGrade(brightness = Float.NaN).hasValidRenderParameters())
        assertFalse(ColorStudioGrade(contrast = 2.1f).hasValidRenderParameters())
        assertFalse(ColorStudioGrade(shadows = ColorWheelVector(1.1f, 0f)).hasValidRenderParameters())
        assertFalse(ColorStudioGrade(gCurve = listOf(0f, 0.2f, Float.NaN, 0.8f, 1f)).hasValidRenderParameters())
    }

    @Test fun generatedGlTextureMatchesTheSharedCpuLutAtEveryLatticeNode() {
        val grade = ColorStudioGrade(brightness = 0.08f, contrast = 1.2f,
            shadows = ColorWheelVector(-0.3f, 0.2f), lumaCurve = listOf(0f, 0.2f, 0.55f, 0.8f, 1f))
        val size = 5
        val lut = ColorStudioLut(grade, size)
        val texture = grade.toLutTexture(size)
        assertEquals(size * size, texture.width)
        assertEquals(size, texture.height)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val channel = { i: Int -> (i * 255 + (size - 1) / 2) / (size - 1) }
            val input = 0xFF000000.toInt() or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            assertEquals(lut.processColor(input), texture.pixels[r * texture.width + b * size + g])
        }
    }
}
