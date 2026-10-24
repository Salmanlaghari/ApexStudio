package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
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
        assertTrue(GlobalModerationEnforcement.readiness.message.contains("selected project images", ignoreCase = true))
    }

    @Test fun everyRawProtectedActionFailsOutsideItsIntegratedScope() {
        ProtectedAiAction.entries.forEach { action ->
            val failure = assertDenied(action)
            assertEquals(GlobalModerationEnforcement.statusMessage(action), failure.message)
            assertTrue(failure.message.orEmpty().isNotBlank())
        }
    }

    @Test fun statusMessageDescribesSeparatedPhotoAndVideoUpscaleActionsWithoutOfferingABypass() {
        val message = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.ONNX_UPSCALING)
        assertTrue(message.contains("Photo AI Enhance", ignoreCase = true))
        assertTrue(message.contains("video-frame upscaling", ignoreCase = true))
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

    @Test fun captionsAndEnglishAlignmentAreLimitedToTheSameProjectVideoUri(): Unit = runBlocking {
        val clip = MediaClip("video", "Video", "content://project/video", 10_000L,
            trimEndMs = 10_000L, type = ClipType.VIDEO)
        GlobalModerationEnforcement.withExistingProjectVideoCaptions(clip) {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS, clip.uri)
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS)
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WORD_ALIGNMENT, clip.uri)
            assertDenied(ProtectedAiAction.BACKGROUND_REPLACEMENT)
            try {
                GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS, "content://other/video")
                throw AssertionError("A different media URI must stay out of scope")
            } catch (_: AiModelUnavailableException) { }
        }
        assertDenied(ProtectedAiAction.WHISPER_CAPTIONS)
        assertDenied(ProtectedAiAction.WORD_ALIGNMENT)
        try {
            GlobalModerationEnforcement.withExistingProjectVideoCaptions(clip.copy(type = ClipType.IMAGE)) { }
            throw AssertionError("Caption scope must reject still images")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun faceReviewAndBackgroundCompositingStaySeparateFromReframeAndGenerativeActions(): Unit = runBlocking {
        val clip = MediaClip("video", "Video", "file:///project/video.mp4", 10_000L,
            trimEndMs = 10_000L, type = ClipType.VIDEO)
        GlobalModerationEnforcement.withExistingProjectVideoBackgroundReplacement(clip.uri) {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.BACKGROUND_REPLACEMENT, clip.uri)
            assertDenied(ProtectedAiAction.ONNX_STYLE_TRANSFER)
            assertDenied(ProtectedAiAction.WHISPER_CAPTIONS)
        }
        GlobalModerationEnforcement.withExistingProjectVideoFaceReview(clip) {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.FACE_TRACK_REVIEW, clip.uri)
            assertDenied(ProtectedAiAction.FACE_AWARE_REFRAME)
            assertDenied(ProtectedAiAction.GESTURE_EDITING)
        }
        assertDenied(ProtectedAiAction.BACKGROUND_REPLACEMENT)
        assertDenied(ProtectedAiAction.FACE_TRACK_REVIEW)
    }

    @Test fun faceCropRunsOnlyForTheExactExistingProjectImageInsidePhotoScope(): Unit = runBlocking {
        val image = MediaClip("photo", "Photo", "content://project/photo", 3_000L,
            trimEndMs = 3_000L, type = ClipType.IMAGE)
        ImageAiScope.withExistingProjectImage(image, ProtectedAiAction.FACE_AWARE_REFRAME) {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.FACE_AWARE_REFRAME, image.uri)
            assertDenied(ProtectedAiAction.ONNX_UPSCALING)
            try {
                GlobalModerationEnforcement.requireApproved(ProtectedAiAction.FACE_AWARE_REFRAME, "content://other/photo")
                throw AssertionError("A different image URI must stay outside the face-crop scope")
            } catch (_: AiModelUnavailableException) { }
        }
        assertDenied(ProtectedAiAction.FACE_AWARE_REFRAME)
        try {
            ImageAiScope.withExistingProjectImage(image.copy(type = ClipType.VIDEO), ProtectedAiAction.FACE_AWARE_REFRAME) { }
            throw AssertionError("Still-photo face-crop scope must reject video clips")
        } catch (_: IllegalArgumentException) { }
    }
}
