package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.ai.AiAvailability
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import org.junit.Assert.*
import org.junit.Test

class AiToolsCatalogTest {
    @Test fun everyProtectedFeatureShowsSafetyBlockAndHasNoToggleState() {
        val inventory = AiFeatureId.entries.map { AiFeatureAvailability(it, AiAvailability.READY, "Local backend available") }
        val cards = aiToolCards(inventory)
        assertEquals(AiFeatureId.entries.size, cards.size)
        assertEquals(cards.size, cards.map { it.id }.distinct().size)
        assertTrue(cards.all { it.statusLabel == AI_PROTECTED_STATUS_LABEL })
        assertTrue(cards.all { it.unavailableReason == AI_PROTECTED_BLOCKER })
        assertTrue(AI_PROTECTED_BLOCKER.contains("sexual, violent, and hateful"))
    }

    @Test fun availableAlgorithmCannotBypassMissingModeration() {
        val cards = aiToolCards(listOf(
            AiFeatureAvailability(AiFeatureId.SMART_TRIM, AiAvailability.READY, "Signal analysis"),
            AiFeatureAvailability(AiFeatureId.CONTENT_MODERATION, AiAvailability.ENGINE_UNAVAILABLE, "No safety classifier")
        ))
        val smartTrim = cards.first { it.id == "SMART_TRIM" }
        assertEquals(AI_PROTECTED_STATUS_LABEL, smartTrim.statusLabel)
        assertTrue(smartTrim.unavailableReason.contains("validated all-category moderation"))
        assertTrue(smartTrim.detail.startsWith("Local backend detected"))
    }

    @Test fun modelErrorsAreNotHiddenBehindGenericSafetyMessage() {
        val cards = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.SUBTITLES, AiAvailability.MODEL_MISSING, "Whisper files are missing"
        )))
        assertEquals(AI_PROTECTED_STATUS_LABEL, cards.single().statusLabel)
        assertTrue(cards.single().detail.contains("Whisper files are missing"))
        assertTrue(cards.single().unavailableReason.contains("sexual, violent, and hateful"))
    }

    @Test fun localReviewEntryIsClearlySeparateFromProtectedFeatureCards() {
        assertEquals("Open offline scene + face review", LOCAL_REVIEW_ACTION_LABEL)
        val protectedCard = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.SCENE_ANALYSIS, AiAvailability.READY, "Local backend available"
        ))).single()
        assertEquals(AI_PROTECTED_STATUS_LABEL, protectedCard.statusLabel)
        assertFalse(protectedCard.title.contains("Available local review"))
    }
}
