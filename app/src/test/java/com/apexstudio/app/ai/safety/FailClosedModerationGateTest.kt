package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailClosedModerationGateTest {
    private val all = SafetyCategory.entries.toSet()
    private fun classifier(validated: Boolean = true, values: Map<SafetyCategory, Float> = all.associateWith { .01f }) =
        object : ValidatedModerationClassifier {
            override val metadata = ClassifierMetadata(validated, all, "local-model", "1")
            override fun classify(imageBytes: ByteArray) = ModerationScores(values)
        }

    @Test fun missingOrUnvalidatedClassifierFailsClosed() {
        assertDenied { FailClosedModerationGate(null).requireAllowed(byteArrayOf(1)) }
        assertDenied { FailClosedModerationGate(classifier(validated = false)).requireAllowed(byteArrayOf(1)) }
    }

    @Test fun incompleteScoresFailClosedAndAnyPolicyCategoryBlocks() {
        assertDenied { FailClosedModerationGate(classifier(values = mapOf(SafetyCategory.SEXUAL to .01f))).requireAllowed(byteArrayOf(1)) }
        val scores = all.associateWith { if (it == SafetyCategory.HATEFUL) .9f else .01f }
        val decision = FailClosedModerationGate(classifier(values = scores)).requireAllowed(byteArrayOf(1))
        assertFalse(decision.allowed)
        assertTrue(SafetyCategory.HATEFUL in decision.flagged)
    }

    @Test fun validBelowThresholdOutputAllows() {
        assertTrue(FailClosedModerationGate(classifier()).requireAllowed(byteArrayOf(1)).allowed)
    }

    private fun assertDenied(block: () -> Unit) {
        try { block(); throw AssertionError("Expected fail-closed denial") }
        catch (_: AiModelUnavailableException) { }
    }
}
