package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.data.color.ColorStudioGrade
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorVideoExporterTest {
    @Test fun sharedLutAndSharpnessAreComposedIntoTheStandaloneVideoFilter() {
        assertEquals(
            "lut3d=file=/tmp/grade.cube:interp=trilinear",
            ColorVideoExporter.buildVideoFilter("/tmp/grade.cube", ColorStudioGrade())
        )
        assertEquals(
            "lut3d=file=/tmp/grade.cube:interp=trilinear,format=gbrp," +
                "convolution=0m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "1m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "2m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "0rdiv=1:1rdiv=1:2rdiv=1,format=yuv420p",
            ColorVideoExporter.buildVideoFilter("/tmp/grade.cube", ColorStudioGrade(sharpness = 1f))
        )
    }

    @Test fun acceptsOnlyExplicitlyVerifiedSdrEightBitFormats() {
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "bt709"))
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("NV12", "IEC61966-2-1"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p10le", "bt709"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "smpte2084"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "unknown"))
    }

    @Test fun cancellationDrainIsBoundedAndLateNativeCompletionCleansStagedFiles() = runBlocking {
        val stagedInput = File.createTempFile("color_test_", ".media").apply { writeText("staged") }
        val lease = ColorVideoExporter.NativeFileLease().apply { register(stagedInput) }
        var completion: ((String?, Throwable?) -> Unit)? = null
        val cancelCalls = AtomicInteger()
        val drainTimedOut = AtomicBoolean(false)
        val job = launch {
            try {
                ColorVideoExporter.executeCancellableNativeSession<String>(
                    startSession = { onComplete -> completion = onComplete; 18L },
                    cancelSession = { id -> assertEquals(18L, id); cancelCalls.incrementAndGet() },
                    onNativeFinished = lease::markNativeFinished,
                    drainTimeoutMillis = 25L,
                    onDrainTimeout = { drainTimedOut.set(true) }
                )
            } finally {
                lease.requestCleanup()
            }
        }
        try {
            yield()
            job.cancelAndJoin()
            assertEquals(1, cancelCalls.get())
            assertTrue("Cancellation should finish after the bounded drain", drainTimedOut.get())
            assertTrue("Native input must remain until FFmpeg signals completion", stagedInput.isFile)
            assertNotNull(completion)
            completion!!.invoke("stopped", null)
            assertFalse("Late native completion should release staged inputs", stagedInput.exists())
        } finally {
            lease.markNativeFinished()
            lease.requestCleanup()
            stagedInput.delete()
        }
    }

    @Test fun cancellationDrainReturnsImmediatelyWhenNativeCallbackArrives() = runBlocking {
        val stagedInput = File.createTempFile("color_test_", ".media").apply { writeText("staged") }
        val lease = ColorVideoExporter.NativeFileLease().apply { register(stagedInput) }
        var completion: ((String?, Throwable?) -> Unit)? = null
        val cancelCalls = AtomicInteger()
        val drainTimedOut = AtomicBoolean(false)
        val job = launch {
            try {
                ColorVideoExporter.executeCancellableNativeSession<String>(
                    startSession = { onComplete -> completion = onComplete; 29L },
                    cancelSession = { id ->
                        assertEquals(29L, id)
                        cancelCalls.incrementAndGet()
                        completion?.invoke("stopped", null)
                    },
                    onNativeFinished = lease::markNativeFinished,
                    drainTimeoutMillis = 1_000L,
                    onDrainTimeout = { drainTimedOut.set(true) }
                )
            } finally {
                lease.requestCleanup()
            }
        }
        try {
            yield()
            job.cancelAndJoin()
            assertEquals(1, cancelCalls.get())
            assertFalse("A delivered callback must avoid the timeout", drainTimedOut.get())
            assertFalse("Inputs should be removed after native completion", stagedInput.exists())
        } finally {
            lease.markNativeFinished()
            lease.requestCleanup()
            stagedInput.delete()
        }
    }

    @Test fun publishesCompletedColorOutputWithTheExistingFinalNamePrefix() {
        val directory = Files.createTempDirectory("color-publish-test").toFile()
        try {
            val pending = File.createTempFile("color_pending_", ".mp4", directory)
            val content = byteArrayOf(1, 2, 3, 4)
            pending.writeBytes(content)

            val published = ColorVideoExporter.publishPendingOutput(pending)

            assertTrue(published.isFile)
            assertEquals(directory, published.parentFile)
            assertTrue(published.name.startsWith("color_"))
            assertFalse(published.name.startsWith("color_pending_"))
            assertArrayEquals(content, published.readBytes())
            assertFalse(pending.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
