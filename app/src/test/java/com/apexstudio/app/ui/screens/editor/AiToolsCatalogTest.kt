package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.ai.AiAvailability
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import org.junit.Assert.*
import org.junit.Test

class AiToolsCatalogTest {
    @Test fun backendInventoryIsNotAGlobalUnlockOrAModerationRequirement() {
        val cards = aiToolCards(AiFeatureId.entries.map {
            AiFeatureAvailability(it, AiAvailability.READY, "Local backend available")
        })
        assertEquals(AiFeatureId.entries.size, cards.size)
        assertEquals(cards.size, cards.map { it.id }.distinct().size)
        assertTrue(cards.all { it.statusLabel == AI_PROTECTED_STATUS_LABEL })
        assertTrue(AI_PROTECTED_BLOCKER.contains("selected project media"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("Features shown as unavailable will not run"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("Review suggestions before applying"))
    }

    @Test fun suggestionsHavePlainLabelsAndNeutralAvailabilityStatus() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.AUTO_CROP, AiAvailability.READY, "Signal analysis"
        ))).single()
        assertEquals("Crop suggestions", card.title)
        assertEquals("Available", card.detail)
    }

    @Test fun unavailableFeatureIsNotPresentedAsReadyOrRunable() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.SUBTITLES, AiAvailability.MODEL_MISSING, "Whisper files are missing"
        ))).single()
        assertEquals("Not available yet", card.detail)
        assertEquals(AI_PROTECTED_STATUS_LABEL, card.statusLabel)
        assertTrue(card.unavailableReason.startsWith("Create captions from speech"))
        assertFalse(card.unavailableReason.contains("Whisper"))
    }

    @Test fun upscaleSeparatesVideoEntryFromScopedPhotoEnhanceAction() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.ONNX_UPSCALING, AiAvailability.READY, "ESPCN locally available"
        ))).single()
        assertTrue(card.unavailableReason.contains("Open Video enhancement"))
        assertTrue(card.unavailableReason.contains("Photo Enhance"))
        assertTrue(card.unavailableReason.contains("4.2 megapixels"))
    }

    @Test fun unreviewedArtisticStyleIsClearlyUnavailable() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.ONNX_STYLE_TRANSFER, AiAvailability.READY, "Model bytes present"
        ))).single()
        assertTrue(card.unavailableReason.contains("not available yet"))
        assertTrue(card.unavailableReason.contains("Standard filters"))
    }

    @Test fun liveCameraRemainsOutOfScopeAndReviewHasSeparateEntry() {
        assertEquals("Review video suggestions", LOCAL_REVIEW_ACTION_LABEL)
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.GESTURE_CONTROL, AiAvailability.READY, "Model bytes present"
        ))).single()
        assertTrue(card.detail.contains("Live camera editing is not available here"))
    }

    @Test fun onlyExistingVideoWorkflowIdsHaveCardDestinations() {
        listOf("SMART_TRIM", "SCENE_ANALYSIS", "STYLE_ANALYSIS", "AUTO_CROP", "FACE_TRACKING")
            .forEach { assertEquals(AiToolAction.LOCAL_ANALYSIS, aiToolActionForId(it)) }
        assertEquals(AiToolAction.VIDEO_UPSCALE, aiToolActionForId("ONNX_UPSCALING"))
        assertEquals(AiToolAction.CAPTIONS, aiToolActionForId("SUBTITLES"))
        assertEquals(AiToolAction.CAPTIONS, aiToolActionForId("WORD_TIMED_SUBTITLES"))
        assertEquals(AiToolAction.BACKGROUND_REPLACE, aiToolActionForId("BACKGROUND_REPLACE"))
        listOf("FACE_AWARE_REFRAME", "FACE_BLUR", "MEDIAPIPE_HAND", "ONNX_STYLE_TRANSFER", "ONNX_BACKGROUND_REMOVAL", "DEPTH_2D_TO_3D")
            .forEach { assertNull("$it must not be routed to a fake action", aiToolActionForId(it)) }
    }

    @Test fun faceReframeExplainsScopedStillPhotoActionAndRestrictedVideoAction() {
        val reframe = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.FACE_AWARE_REFRAME, AiAvailability.READY, "Face model present"
        ))).single()
        assertTrue(reframe.unavailableReason.contains("Review face-framing suggestions"))
        assertTrue(reframe.unavailableReason.contains("not available yet"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("some actions are still restricted"))
    }
}
