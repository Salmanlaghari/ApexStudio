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
        assertTrue(AI_PROTECTED_BLOCKER.contains("scoped existing-video editor actions"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("Other model inventory is not a processing control"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("Photo AI Enhance"))
        assertFalse(AI_PROTECTED_BLOCKER.contains("all-category moderation"))
    }

    @Test fun deterministicAnalysisIsNotFalselyLabelledAsLearnedAI() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.AUTO_CROP, AiAvailability.READY, "Signal analysis"
        ))).single()
        assertTrue(card.title.contains("not subject AI"))
        assertTrue(card.detail.startsWith("Local backend detected"))
    }

    @Test fun modelErrorsRemainVisible() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.SUBTITLES, AiAvailability.MODEL_MISSING, "Whisper files are missing"
        ))).single()
        assertTrue(card.detail.contains("Whisper files are missing"))
        assertEquals(AI_PROTECTED_STATUS_LABEL, card.statusLabel)
        assertTrue(card.unavailableReason.startsWith(
            "Available in Text → Captions for an existing project video."
        ))
        assertTrue(card.unavailableReason.contains("model readiness and audio access are checked when run"))
    }

    @Test fun upscaleSeparatesVideoEntryFromScopedPhotoEnhanceAction() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.ONNX_UPSCALING, AiAvailability.READY, "ESPCN locally available"
        ))).single()
        assertTrue(card.unavailableReason.contains("Open AI Video Upscale"))
        assertTrue(card.unavailableReason.contains("Photo AI Enhance"))
        assertTrue(card.unavailableReason.contains("4.2 megapixels"))
    }

    @Test fun unreviewedArtisticModelRemainsUnavailableForActualLicenseAndIntegrationReasons() {
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.ONNX_STYLE_TRANSFER, AiAvailability.READY, "Model bytes present"
        ))).single()
        assertTrue(card.unavailableReason.contains("license/provenance"))
        assertTrue(card.unavailableReason.contains("full-video integration"))
    }

    @Test fun liveCameraRemainsOutOfScopeAndReviewHasSeparateEntry() {
        assertEquals("Open offline scene + face review", LOCAL_REVIEW_ACTION_LABEL)
        val card = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.GESTURE_CONTROL, AiAvailability.READY, "Model bytes present"
        ))).single()
        assertTrue(card.detail.contains("outside the existing-project-video scope"))
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
        assertTrue(reframe.unavailableReason.contains("Still photos can use Face crop"))
        assertTrue(reframe.unavailableReason.contains("video-track application remains review-restricted"))
        assertTrue(AI_PROTECTED_BLOCKER.contains("no integrated, tested depth model"))
    }
}
