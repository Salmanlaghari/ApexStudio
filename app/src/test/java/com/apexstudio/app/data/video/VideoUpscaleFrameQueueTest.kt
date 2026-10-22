package com.apexstudio.app.data.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VideoUpscaleFrameQueueTest {
    @Test fun fullFrameQueueBackpressuresUntilConsumerFreesRoomInsteadOfDroppingFrame() {
        val queue = ArrayBlockingQueue<Int>(1).apply { add(1) }
        val stopping = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val accepted = AtomicBoolean(false)

        Thread {
            started.countDown()
            accepted.set(VideoUpscaleEngine.enqueueFrameWithoutDropping(queue, 2, stopping))
            finished.countDown()
        }.start()

        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertFalse("Producer must wait while the bounded queue is full", finished.await(150, TimeUnit.MILLISECONDS))
        assertEquals(1, queue.take())
        assertTrue("Producer should resume once inference consumes a frame", finished.await(1, TimeUnit.SECONDS))
        assertTrue("The pending decoded frame must be queued, not discarded", accepted.get())
        assertEquals(2, queue.take())
    }

    @Test fun blockedFrameProducerStopsAndReportsFrameNotQueuedDuringShutdown() {
        val queue = ArrayBlockingQueue<Int>(1).apply { add(1) }
        val stopping = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val accepted = AtomicBoolean(true)

        Thread {
            started.countDown()
            accepted.set(VideoUpscaleEngine.enqueueFrameWithoutDropping(queue, 2, stopping))
            finished.countDown()
        }.start()

        assertTrue(started.await(1, TimeUnit.SECONDS))
        stopping.set(true)
        assertTrue("Shutdown must release a blocked producer promptly", finished.await(1, TimeUnit.SECONDS))
        assertFalse("A frame rejected during shutdown must be returned for cleanup", accepted.get())
        assertEquals(1, queue.take())
    }
}
