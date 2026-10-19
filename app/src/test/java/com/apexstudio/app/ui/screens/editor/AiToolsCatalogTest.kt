package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.ai.AiAvailability
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import org.junit.Assert.*
import org.junit.Test

class AiToolsCatalogTest {
    @Test fun everyRequestedFeatureHasAnOffCardUntilValidatedEditorIntegrationExists() {
        val inventory = AiFeatureId.entries.map { AiFeatureAvailability(it, AiAvailability.READY, "Local backend available") }
        val cards = aiToolCards(inventory)
        assertEquals(AiFeatureId.entries.size, cards.size)
        assertEquals(cards.size, cards.map { it.id }.distinct().size)
        assertTrue(cards.all { !it.ready && !it.enabled })
        assertTrue(cards.all { !it.unavailableReason.isNullOrBlank() })
    }

    @Test fun availableAlgorithmCannotBypassMissingModeration() {
        val cards = aiToolCards(listOf(
            AiFeatureAvailability(AiFeatureId.SMART_TRIM, AiAvailability.READY, "Signal analysis"),
            AiFeatureAvailability(AiFeatureId.CONTENT_MODERATION, AiAvailability.ENGINE_UNAVAILABLE, "No safety classifier")
        ))
        val smartTrim = cards.first { it.id == "SMART_TRIM" }
        assertFalse(smartTrim.ready)
        assertTrue(smartTrim.unavailableReason!!.contains("classifier"))
    }

    @Test fun modelErrorsAreNotHiddenBehindGenericSafetyMessage() {
        val cards = aiToolCards(listOf(AiFeatureAvailability(
            AiFeatureId.SUBTITLES, AiAvailability.MODEL_MISSING, "Whisper files are missing"
        )))
        assertEquals("Whisper files are missing", cards.single().unavailableReason)
        assertFalse(cards.single().enabled)
    }
}
