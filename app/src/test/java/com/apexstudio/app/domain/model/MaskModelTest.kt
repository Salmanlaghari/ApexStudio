package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Bug 4 mask model: the ten-mode polygon source
 * of truth, the preset recipes and the keyframe interpolation that
 * makes a mask animate.
 *
 * Both renderers consume ONE polygon, so a regression here shows up in
 * consumed by `MaskPreviewOverlay` (Composed Path) AND handed to the
 * export shader as the `uPoly` fill, so a regression here shows up in
 * two renderers at once rather than one.
 */
class MaskModelTest {

    @Test
    fun `polygonal modes produce a closed polygon that fits the uniform`() {
        val polygonal = listOf(MaskShape.HEART, MaskShape.STAR, MaskShape.TRIANGLE)
        polygonal.forEach { shape ->
            val outline = MaskModel.outline(shape)
            assertTrue("$shape must be a polygon", outline.isNotEmpty())
            assertTrue(
                "$shape has ${outline.size} points, must fit uPoly[${MaskModel.MAX_POINTS}]",
                outline.size <= MaskModel.MAX_POINTS
            )
            assertEquals(
                "$shape polygon must close on itself (last == first) so both sides draw consecutive edges",
                outline.first(), outline.last()
            )
            outline.forEach { p ->
                assertTrue("$shape point x outside envelope: ${p.x}", p.x.isFinite())
                assertTrue("$shape point y outside envelope: ${p.y}", p.y.isFinite())
                assertTrue("$shape point x out of [-1,1]: ${p.x}", p.x in -1.001f..1.001f)
                assertTrue("$shape point y out of [-1,1]: ${p.y}", p.y in -1.001f..1.001f)
            }
        }
    }

    @Test
    fun `heart has lobes above and a tip below`() {
        val outline = MaskModel.outline(MaskShape.HEART)
        val ys = outline.map { it.y }
        val xs = outline.map { it.x }
        // y grows downward: the notch between the lobes is above the
        // pointed tip, so the tip has the MAXIMUM y.
        assertTrue("heart tip must sit at the bottom, got max y ${ys.max()}", ys.max() > 0.5f)
        assertTrue("heart notch must sit above the tip (y grows downward)", ys.min() < -0.05f)
        // Wide across the lobes, narrow apex.
        assertEquals("heart left lobe", -1f, xs.min(), 0.01f)
        assertEquals("heart right lobe", 1f, xs.max(), 0.01f)
    }

    @Test
    fun `star alternates five tips and five valleys`() {
        val pts = MaskModel.outline(MaskShape.STAR)
        // 10 real vertices + 1 duplicate closing point
        assertEquals(11, pts.size)
        val body = pts.dropLast(1)
        val radii = body.map { kotlin.math.sqrt(it.x * it.x + it.y * it.y) }
        assertEquals(
            "five outer tips must sit at radius 1",
            5, radii.count { k -> kotlin.math.abs(k - 1f) < 0.01f }
        )
        assertEquals(
            "five inner valleys must sit at radius 0.5 (standard star ratio)",
            5, radii.count { k -> kotlin.math.abs(k - 0.5f) < 0.01f }
        )
        // Tips fan evenly; pinning the FIRST one at 12 o'clock is what
        // makes the star read upright in a y-down screen frame.
        assertEquals("first tip must point straight up", -1f, body.first().y, 0.001f)
        assertEquals(0f, body.first().x, 0.001f)
    }

    @Test
    fun `triangle points upward in screen coordinates`() {
        val pts = MaskModel.outline(MaskShape.TRIANGLE)
        assertEquals(4, pts.size)
        val apex = pts.first()
        assertEquals("apex must be at the top (y grows downward)", -1f, apex.y, 0.001f)
        assertEquals(0f, apex.x, 0.001f)
    }

    @Test
    fun `analytic modes are not polygonal`() {
        val analytic = listOf(
            MaskShape.RECTANGLE, MaskShape.ELLIPSE, MaskShape.CIRCLE,
            MaskShape.LINEAR, MaskShape.MIRROR, MaskShape.BLUR_FACE
        )
        analytic.forEach { shape ->
            assertTrue("$shape must hand off to the SDF path, not a polygon", MaskModel.outline(shape).isEmpty())
        }
    }

    @Test
    fun `pen needs three distinct finite points before it becomes a region`() {
        val one = listOf(MaskPoint(0f, 0f))
        val twoDiverge = listOf(MaskPoint(0f, 0f), MaskPoint(1f, 1f), MaskPoint(0f, 0f))
        val good = listOf(MaskPoint(-0.5f, 0.5f), MaskPoint(0.5f, 0.6f), MaskPoint(0f, -0.5f))
        assertTrue("one point is not an area", MaskModel.outline(MaskShape.DRAW, one).isEmpty())
        assertTrue("duplicates collapse below three distinct points", MaskModel.outline(MaskShape.DRAW, twoDiverge).isEmpty())
        val closed = MaskModel.outline(MaskShape.DRAW, good)
        assertTrue("three distinct points bound an area", closed.size >= 4)
        assertEquals(closed.first(), closed.last())
    }

    @Test
    fun `pen trim keeps the uniform bound`() {
        val many = (0..60).map { i ->
            MaskPoint(i / 60f, (i % 12) / 12f)
        }
        val out = MaskModel.outline(MaskShape.DRAW, many)
        assertTrue(out.size <= MaskModel.MAX_POINTS)
        assertTrue(out.isNotEmpty())
    }

    @Test
    fun `normalized pen capture returns frame geometry and a non-empty shape polygon`() {
        val capture = MaskModel.capturePen(
            listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.25f), MaskPoint(0.5f, 0.75f)),
            frameWidth = 1920f,
            frameHeight = 1080f
        )
        assertEquals(0.5f, capture.centerX, 0.0001f)
        assertEquals(0.5f, capture.centerY, 0.0001f)
        assertEquals(0.5f, capture.sizeX, 0.0001f)
        assertEquals(0.5f, capture.sizeY, 0.0001f)
        assertTrue(MaskModel.outline(MaskShape.DRAW, capture.points).size >= 4)
        assertTrue(capture.points.all { it.x in -1f..1f && it.y in -1f..1f })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalized pen capture rejects a degenerate path`() {
        MaskModel.capturePen(
            listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.5f, 0.5f), MaskPoint(0.5f, 0.5f)),
            frameWidth = 640f,
            frameHeight = 480f
        )
    }

    @Test
    fun `blur faces is explicitly unavailable rather than an analytic oval`() {
        assertTrue(!MaskModel.isAvailable(MaskShape.BLUR_FACE))
        assertTrue(MaskModel.isAvailable(MaskShape.CIRCLE))
        assertTrue(MaskModel.BLUR_FACES_UNAVAILABLE_REASON.contains("face-region"))
    }
}
