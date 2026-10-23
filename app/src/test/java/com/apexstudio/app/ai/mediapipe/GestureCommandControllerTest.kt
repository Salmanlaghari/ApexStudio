package com.apexstudio.app.ai.mediapipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureCommandControllerTest {
    private val armed = GestureCommandSession("edit-1", armed = true)

    @Test
    fun `unarmed or missing session never produces commands`() {
        val controller = GestureCommandController()
        assertNull(controller.update(GestureCommandSession("edit-1", armed = false), sample(0, 0.2f)))
        assertNull(controller.update(null, sample(100, 0.8f)))
        assertNull(controller.update(armed, sample(200, 0.2f))) // starts a fresh baseline
    }

    @Test
    fun `one noisy large displacement is not an automatic trim`() {
        val controller = GestureCommandController()
        assertNull(controller.update(armed, sample(0, 0.25f)))
        assertNull(controller.update(armed, sample(120, 0.8f)))
    }

    @Test
    fun `sustained swipe produces one signed bounded trim and remains latched`() {
        val controller = GestureCommandController()
        assertNull(controller.update(armed, sample(0, 0.25f)))
        assertNull(controller.update(armed, sample(60, 0.36f)))
        val command = controller.update(armed, sample(120, 0.50f)) as GestureCommand.TrimByFraction
        assertEquals(0.0625f, command.delta, 0.0001f)
        assertNull(controller.update(armed, sample(180, 0.95f)))

        val reverse = GestureCommandController()
        reverse.update(armed, sample(0, 0.75f))
        reverse.update(armed, sample(60, 0.65f))
        val negative = reverse.update(armed, sample(120, 0.50f)) as GestureCommand.TrimByFraction
        assertTrue(negative.delta < 0f)
        assertTrue(kotlin.math.abs(negative.delta) <= 0.10f)
    }

    @Test
    fun `pinch requires stable samples and hysteresis then emits bounded incremental zoom`() {
        val controller = GestureCommandController()
        assertNull(controller.update(armed, sample(0, 0.5f, ratio = 0.23f)))
        assertNull(controller.update(armed, sample(50, 0.5f, ratio = 0.22f)))
        assertNull(controller.update(armed, sample(100, 0.5f, ratio = 0.20f))) // only arms pinch
        val zoomIn = controller.update(armed, sample(150, 0.5f, ratio = 0.24f)) as GestureCommand.ZoomByFactor
        assertEquals(1.20f, zoomIn.factor, 0.0001f)
        val zoomOut = controller.update(armed, sample(200, 0.5f, ratio = 0.05f)) as GestureCommand.ZoomByFactor
        assertEquals(0.80f, zoomOut.factor, 0.0001f) // extreme ratios are bounded
        // Hysteresis keeps the pinch active below 0.36; it does not suppress a material zoom.
        val stillPinched = controller.update(armed, sample(250, 0.5f, ratio = 0.34f)) as GestureCommand.ZoomByFactor
        assertEquals(1.25f, stillPinched.factor, 0.0001f)
        assertNull(controller.update(armed, sample(275, 0.5f, ratio = 0.35f))) // stable incremental ratio
        assertNull(controller.update(armed, sample(300, 0.5f, ratio = 0.40f))) // release only
    }

    @Test
    fun `tracking loss resets swipe debounce and pinch state`() {
        val controller = GestureCommandController()
        controller.update(armed, sample(0, 0.2f))
        controller.update(armed, sample(60, 0.3f))
        assertNull(controller.update(armed, GestureCommandSample(80, tracked = false, handCenterX = null, pinchRatio = null)))
        assertNull(controller.update(armed, sample(100, 0.8f)))
        assertNull(controller.update(armed, sample(160, 0.7f)))
        val freshSwipe = controller.update(armed, sample(220, 0.6f))
        assertTrue(freshSwipe is GestureCommand.TrimByFraction) // only fresh post-loss samples count

        controller.update(armed, sample(300, 0.6f, ratio = 0.2f))
        controller.update(armed, sample(350, 0.6f, ratio = 0.2f))
        controller.update(armed, sample(400, 0.6f, ratio = 0.2f))
        assertNull(controller.update(armed, sample(450, 0.6f, ratio = 0.22f))) // small change stays below threshold
        val zoom = controller.update(armed, sample(500, 0.6f, ratio = 0.28f)) as GestureCommand.ZoomByFactor
        assertEquals(1.25f, zoom.factor, 0.0001f)
    }

    @Test
    fun `timestamps must increase and long gaps reset temporal candidates`() {
        val controller = GestureCommandController()
        controller.update(armed, sample(100, 0.2f))
        assertNull(controller.update(armed, sample(90, 0.8f))) // rejected; no stale movement
        assertNull(controller.update(armed, sample(90, 0.2f))) // new baseline after rejection
        assertNull(controller.update(armed, sample(150, 0.3f)))
        val freshGesture = controller.update(armed, sample(220, 0.4f))
        assertTrue(freshGesture is GestureCommand.TrimByFraction) // timing restarts at the accepted baseline
        // Large gap resets even if the next point would otherwise finish a swipe.
        assertNull(controller.update(armed, sample(600, 0.9f)))
    }

    @Test
    fun `nonfinite and out of range observations reset rather than issuing commands`() {
        val controller = GestureCommandController()
        controller.update(armed, sample(0, 0.2f))
        assertNull(controller.update(armed, sample(50, Float.NaN)))
        assertNull(controller.update(armed, sample(100, 0.8f)))
        assertNull(controller.update(armed, sample(150, 1.1f)))
        assertNull(controller.update(armed, sample(200, 0.5f, ratio = Float.POSITIVE_INFINITY)))
    }

    @Test
    fun `session change clears partial motion and configuration rejects unsafe bounds`() {
        val controller = GestureCommandController()
        controller.update(armed, sample(0, 0.2f))
        controller.update(armed, sample(60, 0.3f))
        assertNull(controller.update(GestureCommandSession("edit-2", true), sample(120, 0.8f)))
        assertNull(controller.update(GestureCommandSession("edit-2", true), sample(180, 0.6f)))

        val invalid = runCatching { GestureCommandController(GestureCommandController.Config(maxTrimFraction = 0.5f)) }
        assertFalse(invalid.isSuccess)
    }

    private fun sample(time: Long, x: Float, ratio: Float = 0.8f) =
        GestureCommandSample(time, tracked = true, handCenterX = x, pinchRatio = ratio)
}
