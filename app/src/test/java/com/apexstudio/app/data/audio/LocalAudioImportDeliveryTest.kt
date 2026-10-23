package com.apexstudio.app.data.audio

import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAudioImportDeliveryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun result(file: File) = ImportedLocalAudio(file.absolutePath, "Phone music", 5_000L)

    @Test
    fun successfulDeliveryTransfersOwnershipToCaller() = runTest {
        val target = folder.newFile("music.m4a")
        target.writeText("fixture")
        val expected = result(target)
        val imported = deliverLocalAudioImport(StandardTestDispatcher(testScheduler)) { own ->
            own(target)
            expected
        }
        assertEquals(expected, imported)
        assertTrue(target.exists())
    }

    @Test
    fun cancellationDuringReturnDispatchDeletesCompletedCopy() = runTest {
        val target = folder.newFile("cancelled.m4a")
        var reachedCopyEnd = false
        var delivered = false
        val caller = launch {
            val callerScope = this
            deliverLocalAudioImport(StandardTestDispatcher(testScheduler)) { own ->
                own(target)
                target.writeText("completed copy")
                reachedCopyEnd = true
                // The native/copy work has completed, but the editor disappears
                // before withContext can dispatch its result back to the caller.
                callerScope.cancel()
                result(target)
            }
            delivered = true
        }
        runCurrent()
        caller.join()
        assertTrue(reachedCopyEnd)
        assertTrue(caller.isCancelled)
        assertFalse(delivered)
        assertFalse(target.exists())
    }

    @Test
    fun failureCleansBothPartialAndFinalPathsWithoutChangingError() = runTest {
        val partial = folder.newFile("failed.part")
        val target = folder.newFile("failed.m4a")
        val original = IOException("provider failed")
        var actual: Throwable? = null
        try {
            deliverLocalAudioImport(StandardTestDispatcher(testScheduler)) { own ->
                own(partial)
                own(target)
                throw original
            }
        } catch (error: Throwable) {
            actual = error
        }
        // Coroutine debug stack recovery may copy an IOException across
        // dispatcher boundaries. Its type/message, not identity, must survive.
        assertEquals(original.javaClass, actual?.javaClass)
        assertEquals(original.message, actual?.message)
        assertFalse(partial.exists())
        assertFalse(target.exists())
    }

    @Test
    fun cancellationDoesNotDeleteUnregisteredExistingAudio() = runTest {
        val existing = folder.newFile("previous-import.m4a")
        val target = folder.newFile("new-import.m4a")
        val caller = launch {
            val callerScope = this
            deliverLocalAudioImport(StandardTestDispatcher(testScheduler)) { own ->
                own(target)
                callerScope.cancel()
                result(target)
            }
        }
        runCurrent()
        caller.join()
        assertFalse(target.exists())
        assertTrue(existing.exists())
    }
}
