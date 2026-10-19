package com.apexstudio.app.ui.screens.editor

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecordingTimingTest {
    @Test
    fun elapsedTimeExcludesPausedIntervalAndUsesMonotonicInput() {
        val clock = RecordingClock()
        clock.start(nowMs = 100L)
        assertEquals(350L, clock.elapsedMs(nowMs = 450L))

        clock.pause(nowMs = 600L)
        assertEquals(500L, clock.elapsedMs(nowMs = 900L))

        clock.resume(nowMs = 1_000L)
        assertEquals(750L, clock.elapsedMs(nowMs = 1_250L))
        assertEquals(800L, clock.stop(nowMs = 1_300L))
        assertEquals(800L, clock.elapsedMs(nowMs = 9_000L))
    }

    @Test
    fun timerIgnoresNegativeClockDeltaAndResetClearsSession() {
        val clock = RecordingClock()
        clock.start(nowMs = 500L)
        assertEquals(0L, clock.elapsedMs(nowMs = 400L))
        clock.pause(nowMs = 400L)
        assertEquals(0L, clock.elapsedMs(nowMs = 1_000L))
        clock.reset()
        assertEquals(0L, clock.elapsedMs(nowMs = 1_200L))
    }

    @Test
    fun savePolicyRejectsStopFailuresShortFilesAndEmptyOutput() {
        assertFalse(VoiceRecordingSavePolicy.shouldKeep(false, 4_000L, 20_000L))
        assertFalse(VoiceRecordingSavePolicy.shouldKeep(true, 999L, 20_000L))
        assertFalse(VoiceRecordingSavePolicy.shouldKeep(true, 1_000L, 0L))
        assertTrue(VoiceRecordingSavePolicy.shouldKeep(true, 1_000L, 1L))
    }

    @Test
    fun permissionCallbackCannotStartRecordingAfterDismissalOrInBackground() {
        val guard = RecordingLifecycleGuard()
        assertFalse(guard.canStart(Lifecycle.State.RESUMED))
        guard.attach()
        assertFalse(guard.canStart(Lifecycle.State.DESTROYED))
        assertFalse(guard.canStart(Lifecycle.State.CREATED))
        assertFalse(guard.canStart(Lifecycle.State.STARTED))
        assertTrue(guard.canStart(Lifecycle.State.RESUMED))
        guard.dispose()
        assertFalse(guard.canStart(Lifecycle.State.RESUMED))
    }

    @Test
    fun leavingForegroundPausesClockAndReturningDoesNotAutoResume() {
        val guard = RecordingLifecycleGuard().apply { attach() }
        val clock = RecordingClock().apply { start(100L) }
        guard.onEvent(Lifecycle.Event.ON_PAUSE) { clock.pause(600L) }
        assertEquals(500L, clock.elapsedMs(4_000L))
        guard.onEvent(Lifecycle.Event.ON_STOP) { clock.pause(4_000L) }
        guard.onEvent(Lifecycle.Event.ON_START) { error("must not record on start") }
        guard.onEvent(Lifecycle.Event.ON_RESUME) { error("must not auto-resume") }
        assertEquals(500L, clock.elapsedMs(9_000L))
        clock.resume(10_000L)
        assertEquals(750L, clock.elapsedMs(10_250L))
    }

    @Test
    fun detachedLifecycleDoesNotDeliverRecordingCallbacks() {
        val guard = RecordingLifecycleGuard().apply { attach(); dispose() }
        guard.onEvent(Lifecycle.Event.ON_DESTROY) { error("disposed panel callback") }
        guard.attach()
        var stopped = false
        guard.onEvent(Lifecycle.Event.ON_DESTROY) { stopped = true }
        assertTrue(stopped)
    }
}
