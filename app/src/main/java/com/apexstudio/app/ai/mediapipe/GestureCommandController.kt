package com.apexstudio.app.ai.mediapipe

import kotlin.math.abs

/** The caller must explicitly arm a named editing session before gesture commands can be produced. */
data class GestureCommandSession(val id: String, val armed: Boolean)

/** One normalized hand observation. A missing/untracked observation is a tracking-loss reset. */
data class GestureCommandSample(
    val timestampMs: Long,
    val tracked: Boolean,
    val handCenterX: Float?,
    /** Thumb/index tip distance divided by palm scale. */
    val pinchRatio: Float?,
)

/** Values are requests, not direct editor mutations; consumers must still validate their own context. */
sealed class GestureCommand {
    /** Signed fraction of the current clip duration; positive trims the right edge, negative the left. */
    data class TrimByFraction(val delta: Float) : GestureCommand()

    /** Multiplicative zoom request. Values are intentionally bounded around 1. */
    data class ZoomByFactor(val factor: Float) : GestureCommand()
}

/**
 * Deterministic temporal gesture mapper. No camera, UI, or editor dependencies are used here.
 * Swipe emits one bounded trim request after a sustained horizontal motion. Pinch emits bounded,
 * incremental zoom requests only after a stable pinch and a material ratio change. Call [reset]
 * when the owning editing context is discarded.
 */
class GestureCommandController(
    private val config: Config = Config(),
) {
    data class Config(
        val swipeDistanceThreshold: Float = 0.18f,
        val swipeRearmDistance: Float = 0.07f,
        val minSwipeSamples: Int = 3,
        val minSwipeDurationMs: Long = 100L,
        val maxSwipeDurationMs: Long = 900L,
        val maxSampleGapMs: Long = 250L,
        val maxTrimFraction: Float = 0.10f,
        val trimFractionPerNormalizedUnit: Float = 0.25f,
        val minTrimFraction: Float = 0.01f,
        val pinchEnterRatio: Float = 0.25f,
        val pinchExitRatio: Float = 0.36f,
        val minPinchSamples: Int = 3,
        val minPinchDurationMs: Long = 100L,
        val pinchZoomChangeThreshold: Float = 1.12f,
        val minZoomFactor: Float = 0.80f,
        val maxZoomFactor: Float = 1.25f,
        val maxPinchRatio: Float = 4f,
    ) {
        init {
            require(swipeDistanceThreshold.isFinite() && swipeDistanceThreshold in 0.05f..0.5f)
            require(swipeRearmDistance.isFinite() && swipeRearmDistance >= 0f && swipeRearmDistance < swipeDistanceThreshold)
            require(minSwipeSamples >= 3 && minSwipeDurationMs >= 50 && maxSwipeDurationMs >= minSwipeDurationMs)
            require(maxSampleGapMs > 0)
            require(maxTrimFraction.isFinite() && maxTrimFraction in 0.001f..0.25f)
            require(minTrimFraction.isFinite() && minTrimFraction in 0f..maxTrimFraction)
            require(trimFractionPerNormalizedUnit.isFinite() && trimFractionPerNormalizedUnit > 0f)
            require(pinchEnterRatio.isFinite() && pinchEnterRatio > 0f)
            require(pinchExitRatio.isFinite() && pinchExitRatio > pinchEnterRatio)
            require(minPinchSamples >= 3 && minPinchDurationMs >= 50)
            require(pinchZoomChangeThreshold.isFinite() && pinchZoomChangeThreshold in 1.01f..2f)
            require(minZoomFactor.isFinite() && maxZoomFactor.isFinite() && minZoomFactor in 0.5f..1f)
            require(maxZoomFactor in 1f..2f && minZoomFactor < maxZoomFactor)
            require(maxPinchRatio.isFinite() && maxPinchRatio > pinchExitRatio && maxPinchRatio <= 10f)
        }
    }

    private var sessionId: String? = null
    private var previousTimestamp: Long? = null
    private var swipeStartX: Float? = null
    private var swipePreviousX: Float? = null
    private var swipeStartTime: Long = 0L
    private var swipeSamples: Int = 0
    private var swipeDirection: Int = 0
    private var swipeLatched: Boolean = false
    private var rearmSamples: Int = 0
    private var pinchCandidateStartTime: Long = 0L
    private var pinchCandidateSamples: Int = 0
    private var pinchActive: Boolean = false
    private var pinchBaseline: Float? = null

    /** Returns at most one safe command for this observation; null means no action. */
    fun update(session: GestureCommandSession?, sample: GestureCommandSample?): GestureCommand? {
        if (session == null || !session.armed || session.id.isBlank()) {
            reset()
            return null
        }
        if (sessionId != session.id) {
            reset()
            sessionId = session.id
        }
        if (sample == null || !sample.tracked) {
            resetMotion()
            return null
        }
        val x = sample.handCenterX
        val ratio = sample.pinchRatio
        if (sample.timestampMs < 0L || x == null || ratio == null || !x.isFinite() || x !in 0f..1f ||
            !ratio.isFinite() || ratio <= 0f || ratio > config.maxPinchRatio
        ) {
            resetMotion()
            return null
        }
        val priorTime = previousTimestamp
        if (priorTime != null && sample.timestampMs <= priorTime) {
            // Reject duplicates and backward time instead of allowing stale samples to accumulate.
            resetMotion()
            return null
        }
        if (priorTime != null && sample.timestampMs - priorTime > config.maxSampleGapMs) {
            resetMotion()
        }
        previousTimestamp = sample.timestampMs

        if (pinchActive) {
            if (ratio > config.pinchExitRatio) {
                clearPinch()
                // Release does not also count as the first sample of a swipe.
                startSwipe(x, sample.timestampMs)
                return null
            }
            val baseline = pinchBaseline ?: ratio.also { pinchBaseline = it }
            val factor = ratio / baseline
            if (factor >= config.pinchZoomChangeThreshold || factor <= 1f / config.pinchZoomChangeThreshold) {
                pinchBaseline = ratio
                return GestureCommand.ZoomByFactor(
                    factor.coerceIn(config.minZoomFactor, config.maxZoomFactor),
                )
            }
            return null
        }

        if (ratio <= config.pinchEnterRatio) {
            if (pinchCandidateSamples == 0) {
                pinchCandidateStartTime = sample.timestampMs
                clearSwipeCandidate()
            }
            pinchCandidateSamples++
            if (pinchCandidateSamples >= config.minPinchSamples &&
                sample.timestampMs - pinchCandidateStartTime >= config.minPinchDurationMs
            ) {
                pinchActive = true
                pinchBaseline = ratio
                clearSwipeCandidate()
            }
            return null // A possible pinch suppresses swipe recognition until it resolves.
        }
        if (pinchCandidateSamples > 0 && ratio <= config.pinchExitRatio) {
            pinchCandidateSamples++
            if (pinchCandidateSamples >= config.minPinchSamples &&
                sample.timestampMs - pinchCandidateStartTime >= config.minPinchDurationMs
            ) {
                pinchActive = true
                pinchBaseline = ratio
                clearSwipeCandidate()
            }
            return null
        }
        pinchCandidateSamples = 0

        return updateSwipe(x, sample.timestampMs)
    }

    /** Clears session and temporal history. Disarmed or changed sessions also clear automatically. */
    fun reset() {
        sessionId = null
        resetMotion()
    }

    private fun updateSwipe(x: Float, timestampMs: Long): GestureCommand? {
        val start = swipeStartX
        if (start == null) {
            startSwipe(x, timestampMs)
            return null
        }
        if (swipeLatched) {
            if (abs(x - start) <= config.swipeRearmDistance) rearmSamples++ else rearmSamples = 0
            if (rearmSamples >= config.minSwipeSamples) startSwipe(x, timestampMs)
            swipePreviousX = x
            return null
        }
        val previous = swipePreviousX ?: start
        val step = x - previous
        val directionNow = when {
            x > start -> 1
            x < start -> -1
            else -> 0
        }
        if (swipeDirection != 0 && directionNow != 0 && directionNow != swipeDirection && abs(step) > 0.04f) {
            startSwipe(x, timestampMs)
            return null
        }
        if (directionNow != 0) swipeDirection = directionNow
        swipePreviousX = x
        swipeSamples++
        val elapsed = timestampMs - swipeStartTime
        if (elapsed > config.maxSwipeDurationMs) {
            startSwipe(x, timestampMs)
            return null
        }
        val displacement = x - start
        if (swipeSamples < config.minSwipeSamples || elapsed < config.minSwipeDurationMs ||
            abs(displacement) < config.swipeDistanceThreshold
        ) return null

        val amount = (abs(displacement) * config.trimFractionPerNormalizedUnit)
            .coerceAtLeast(config.minTrimFraction)
            .coerceAtMost(config.maxTrimFraction)
        swipeLatched = true
        rearmSamples = 0
        return GestureCommand.TrimByFraction(if (displacement >= 0f) amount else -amount)
    }

    private fun startSwipe(x: Float, timestampMs: Long) {
        swipeStartX = x
        swipePreviousX = x
        swipeStartTime = timestampMs
        swipeSamples = 1
        swipeDirection = 0
        swipeLatched = false
        rearmSamples = 0
    }

    private fun clearSwipeCandidate() {
        swipeStartX = null
        swipePreviousX = null
        swipeSamples = 0
        swipeDirection = 0
        swipeLatched = false
        rearmSamples = 0
    }

    private fun clearPinch() {
        pinchActive = false
        pinchBaseline = null
        pinchCandidateSamples = 0
    }

    private fun resetMotion() {
        previousTimestamp = null
        clearSwipeCandidate()
        clearPinch()
    }
}
