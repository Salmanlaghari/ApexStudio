package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException

/** Categories that must all be evaluated before any protected AI operation may proceed. */
enum class SafetyCategory { SEXUAL, VIOLENT, HATEFUL }
data class ClassifierMetadata(val validated: Boolean, val labels: Set<SafetyCategory>, val modelId: String, val modelVersion: String)
data class ModerationScores(val scores: Map<SafetyCategory, Float>)
data class ModerationDecision(val allowed: Boolean, val flagged: Set<SafetyCategory>, val scores: Map<SafetyCategory, Float>)

/** An adapter around a real, independently validated on-device classifier (not an image heuristic). */
interface ValidatedModerationClassifier {
    val metadata: ClassifierMetadata
    fun classify(imageBytes: ByteArray): ModerationScores
}

/**
 * Fail-closed policy boundary. A missing model, unvalidated metadata, absent labels, classifier
 * exceptions, non-finite scores, or an over-threshold score all deny. Supply explicit thresholds
 * from a documented validation policy; defaults are intentionally strict.
 */
class FailClosedModerationGate(
    private val classifier: ValidatedModerationClassifier?,
    private val thresholds: Map<SafetyCategory, Float> = SafetyCategory.entries.associateWith { 0.20f }
) {
    init {
        require(thresholds.keys.containsAll(SafetyCategory.entries))
        require(thresholds.values.all { it.isFinite() && it in 0f..1f })
    }

    fun requireAllowed(imageBytes: ByteArray): ModerationDecision {
        val model = classifier ?: throw AiModelUnavailableException("Content safety unavailable: no validated local classifier is configured; protected AI action denied.")
        val meta = model.metadata
        if (!meta.validated || meta.modelId.isBlank() || meta.modelVersion.isBlank() || !meta.labels.containsAll(SafetyCategory.entries)) {
            throw AiModelUnavailableException("Content safety unavailable: classifier metadata/label coverage is not validated; protected AI action denied.")
        }
        val scores = try { model.classify(imageBytes) } catch (e: Exception) {
            throw AiModelUnavailableException("Content safety classifier failed; protected AI action denied (${e.message ?: "inference error"}).")
        }
        if (!scores.scores.keys.containsAll(SafetyCategory.entries) || scores.scores.values.any { !it.isFinite() || it !in 0f..1f }) {
            throw AiModelUnavailableException("Content safety returned incomplete or invalid scores; protected AI action denied.")
        }
        val flagged = SafetyCategory.entries.filterTo(linkedSetOf()) { scores.scores.getValue(it) >= thresholds.getValue(it) }
        return ModerationDecision(allowed = flagged.isEmpty(), flagged = flagged, scores = scores.scores)
    }
}
