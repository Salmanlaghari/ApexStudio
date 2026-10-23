package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEngineLifecycleTest {
    @Test
    fun releaseStillRunsWhenStoppingRecorderThrows() {
        val calls = mutableListOf<String>()
        val recorder = Any()

        stopAndReleaseSafely(
            recorder,
            stop = { calls += "stop"; error("recorder was not recording") },
            release = { calls += "release" }
        )

        assertEquals(listOf("stop", "release"), calls)
    }

    @Test
    fun absentRecorderDoesNotInvokeCleanupCallbacks() {
        var cleanupCalls = 0

        stopAndReleaseSafely<Any>(null, stop = { cleanupCalls++ }, release = { cleanupCalls++ })

        assertEquals(0, cleanupCalls)
    }
}
