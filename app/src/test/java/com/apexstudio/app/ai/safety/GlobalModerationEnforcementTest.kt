package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class GlobalModerationEnforcementTest {
    private fun assertDenied(action: ProtectedAiAction): AiModelUnavailableException = try {
        GlobalModerationEnforcement.requireApproved(action)
        throw AssertionError("${action.name} must remain unavailable outside its enabled scope")
    } catch (failure: AiModelUnavailableException) {
        failure
    }

    @Test fun readinessDoesNotInventAClassifierRequirementOrUnlockGenericApis() {
        assertFalse(GlobalModerationEnforcement.readiness.approved)
        assertTrue(GlobalModerationEnforcement.readiness.requiredCategories.isEmpty())
        assertTrue(GlobalModerationEnforcement.readiness.message.contains("does not impose a blanket", ignoreCase = true))
    }

    @Test fun everyRawProtectedActionFailsOutsideItsIntegratedScope() {
        ProtectedAiAction.entries.forEach { action ->
            val failure = assertDenied(action)
            assertEquals(GlobalModerationEnforcement.statusMessage(action), failure.message)
            assertTrue(failure.message.orEmpty().isNotBlank())
        }
    }

    @Test fun statusMessageDescribesTheVideoOnlyScopeWithoutOfferingABypass() {
        val message = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.ONNX_UPSCALING)
        assertTrue(message.contains("existing project video", ignoreCase = true))
        assertTrue(message.contains("standalone still-image inference is unavailable", ignoreCase = true))
        assertFalse(message.contains("override", ignoreCase = true))
        assertFalse(message.contains("continue anyway", ignoreCase = true))
    }

    @Test fun existingProjectVideoScopeAllowsOnlyUpscalingAndPropagatesAcrossCoroutines(): Unit = runBlocking {
        GlobalModerationEnforcement.withExistingProjectVideoUpscaling {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
            ProtectedAiAction.entries.filterNot { it == ProtectedAiAction.ONNX_UPSCALING }.forEach { action ->
                assertTrue(assertDenied(action).message.orEmpty().isNotBlank())
            }
            withContext(Dispatchers.Default) {
                GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
                assertTrue(assertDenied(ProtectedAiAction.ONNX_BACKGROUND_REMOVAL).message.orEmpty().isNotBlank())
            }

            val unrelatedThreadFailure = AtomicReference<Throwable?>()
            Thread {
                try {
                    GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
                } catch (failure: Throwable) {
                    unrelatedThreadFailure.set(failure)
                }
            }.apply { start(); join() }
            assertTrue(unrelatedThreadFailure.get() is AiModelUnavailableException)
        }
        assertDenied(ProtectedAiAction.ONNX_UPSCALING)
    }

    @Test fun scopeIsRestoredAfterFailureAndCancellation(): Unit = runBlocking {
        val expected = IllegalStateException("inference failed")
        try {
            GlobalModerationEnforcement.withExistingProjectVideoUpscaling {
                GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
                throw expected
            }
            throw AssertionError("Expected inference failure")
        } catch (actual: IllegalStateException) {
            // Coroutine debug stack recovery may copy the exception instance.
            assertEquals(expected.javaClass, actual.javaClass)
            assertEquals(expected.message, actual.message)
        }
        assertDenied(ProtectedAiAction.ONNX_UPSCALING)

        try {
            GlobalModerationEnforcement.withExistingProjectVideoUpscaling {
                GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
                throw CancellationException("cancelled")
            }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation must not leave the ThreadLocal capability behind.
        }
        assertDenied(ProtectedAiAction.ONNX_UPSCALING)
    }
}
