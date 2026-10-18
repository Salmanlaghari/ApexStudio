package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailClosedModerationGateTest {
    private val all = SafetyCategory.entries.toSet()
    private fun classifier(labels: Set<SafetyCategory> = all, values: Map<SafetyCategory, Float> = all.associateWith { .01f }) =
        object : ValidatedModerationClassifier {
            override val metadata = ClassifierMetadata("arbitrary-filename.onnx", "1", "a".repeat(64), labels, "some unverified claim")
            override fun classify(imageBytes: ByteArray) = ModerationScores(values)
        }

    @Test fun missingClassifierFailsClosed() {
        assertDenied { FailClosedModerationGate(null).requireAllowed(byteArrayOf(1)) }
    }

    @Test fun emptyInputIsDeniedBeforeClassifierInvocation() {
        var calls = 0
        val counting = object : ValidatedModerationClassifier {
            override val metadata = ClassifierMetadata("classifier", "1", "a".repeat(64), all, "evidence")
            override fun classify(imageBytes: ByteArray): ModerationScores {
                calls++
                return ModerationScores(all.associateWith { 0f })
            }
        }
        assertDenied { FailClosedModerationGate(counting).requireAllowed(byteArrayOf()) }
        assertTrue(calls == 0)
    }

    @Test fun partialNsflNsFWSemanticsCannotClaimHateOrPassGate() {
        val onlyVisibleCategories = setOf(SafetyCategory.SEXUAL, SafetyCategory.VIOLENT)
        assertDenied {
            FailClosedModerationGate(classifier(labels = onlyVisibleCategories,
                values = mapOf(SafetyCategory.SEXUAL to .01f, SafetyCategory.VIOLENT to .01f)))
                .requireAllowed(byteArrayOf(1))
        }
    }

    @Test fun arbitraryModelNameAndFabricatedAllClearScoresAreNotApproval() {
        // The app approval catalog is intentionally empty until an exact artifact passes validation.
        assertDenied { FailClosedModerationGate(classifier()).requireAllowed(byteArrayOf(1)) }
    }

    @Test fun nonFiniteAndOutOfRangeScoresAreCoveredWhenApprovalExists() {
        // Gate fails earlier on unapproved artifact, so malformed outputs can never authorize action either.
        val bad = all.associateWith { Float.NaN }
        assertDenied { FailClosedModerationGate(classifier(values = bad)).requireAllowed(byteArrayOf(1)) }
    }

    @Test fun thresholdsMustCoverAllCategoriesAndBeFinite() {
        try {
            FailClosedModerationGate(classifier(), mapOf(SafetyCategory.SEXUAL to Float.NaN))
            throw AssertionError("Expected invalid policy to fail")
        } catch (_: IllegalArgumentException) { }
    }

    private fun assertDenied(block: () -> Unit) {
        try { block(); throw AssertionError("Expected fail-closed denial") }
        catch (_: AiModelUnavailableException) { }
    }
}
