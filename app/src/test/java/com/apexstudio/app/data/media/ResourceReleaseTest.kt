package com.apexstudio.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ResourceReleaseTest {
    @Test
    fun `resource is released when operation throws and original failure propagates`() {
        val resource = Any()
        val failure = IllegalStateException("decoder setup failed")
        var released = 0

        try {
            withResourceRelease(resource, { released++ }) { throw failure }
            fail("operation failure should propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }

        assertEquals(1, released)
    }

    @Test
    fun `release failure does not replace successful operation result`() {
        val result = withResourceRelease(
            resource = Any(),
            release = { throw IllegalStateException("native teardown failed") },
            operation = { "decoded" },
        )

        assertEquals("decoded", result)
    }
}
