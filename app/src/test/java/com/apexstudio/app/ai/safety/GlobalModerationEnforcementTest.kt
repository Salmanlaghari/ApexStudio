package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalModerationEnforcementTest {
    @Test fun productionReadinessDoesNotClaimApprovalWithEmptyReviewedCatalog() {
        assertFalse(GlobalModerationEnforcement.readiness.approved)
        assertEquals(SafetyCategory.entries.toSet(), GlobalModerationEnforcement.readiness.requiredCategories)
        listOf("sexual", "violent", "hateful").forEach {
            assertTrue(GlobalModerationEnforcement.readiness.message.contains(it, ignoreCase = true))
        }
    }

    @Test fun everyProtectedActionFailsBeforeProcessingWithActionableStatus() {
        ProtectedAiAction.entries.forEach { action ->
            try {
                GlobalModerationEnforcement.requireApproved(action)
                throw AssertionError("${action.name} must fail closed without validated moderation")
            } catch (failure: AiModelUnavailableException) {
                assertTrue(failure.message.orEmpty().contains(action.displayName))
                assertTrue(failure.message.orEmpty().contains("No protected AI processing was run"))
                assertTrue(failure.message.orEmpty().contains("Keep editing manually"))
            }
        }
    }

    @Test fun statusMessageNamesAllRequiredCategoriesAndDoesNotOfferABypass() {
        val message = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.WHISPER_CAPTIONS)
        assertTrue(message.contains("sexual", ignoreCase = true))
        assertTrue(message.contains("violent", ignoreCase = true))
        assertTrue(message.contains("hateful", ignoreCase = true))
        assertTrue(message.contains("reviewed app update", ignoreCase = true))
        assertFalse(message.contains("override", ignoreCase = true))
        assertFalse(message.contains("continue anyway", ignoreCase = true))
    }
}
