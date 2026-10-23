package com.apexstudio.app.ai.mediapipe

import kotlin.math.hypot

/**
 * Lifecycle boundary for camera gesture sessions. The caller explicitly arms a non-blank session;
 * disarm, permission loss, lifecycle stop, or an ambiguous/lost hand resets the existing temporal
 * debounce controller. This adapter only produces bounded requests, never mutates editor state.
 */
class GestureSessionAdapter(
    private val controller: GestureCommandController = GestureCommandController(),
) {
    private var activeSession: GestureCommandSession? = null

    /** Starts a new explicitly user-authorized session and resets any old temporal gesture history. */
    @Synchronized
    fun arm(sessionId: String) {
        require(sessionId.isNotBlank()) { "An armed gesture session needs a non-blank editor context id" }
        controller.reset()
        activeSession = GestureCommandSession(sessionId, armed = true)
    }

    /** Resets all debounce state. Safe to call repeatedly from cancellation/permission cleanup. */
    @Synchronized
    fun disarm() {
        activeSession = null
        controller.reset()
    }

    /**
     * Consume an IMAGE-mode detector result. Exactly one well-formed hand is required: no hand or
     * multiple hands is treated as tracking loss so hand switches cannot complete a swipe/pinch.
     * `mirrorHorizontal` should match a front-camera preview's mirrored presentation.
     */
    @Synchronized
    fun update(
        timestampMs: Long,
        hands: List<HandDetection>,
        mirrorHorizontal: Boolean = false,
    ): GestureCommand? {
        val session = activeSession ?: return null
        val sample = handSample(timestampMs, hands, mirrorHorizontal)
        return controller.update(session, sample)
    }

    @Synchronized
    fun isArmed(): Boolean = activeSession?.armed == true

    companion object {
        /** Pure landmark-to-sample mapper, exposed for deterministic tests and alternate frame sources. */
        fun handSample(
            timestampMs: Long,
            hands: List<HandDetection>,
            mirrorHorizontal: Boolean = false,
        ): GestureCommandSample {
            val points = hands.singleOrNull()?.landmarks
            if (points == null || points.size < 21) {
                return GestureCommandSample(timestampMs, tracked = false, handCenterX = null, pinchRatio = null)
            }
            val wrist = points[0]
            val palm = points[9]
            val thumbTip = points[4]
            val indexTip = points[8]
            val coords = listOf(wrist, palm, thumbTip, indexTip)
            if (coords.any { !it.x.isFinite() || !it.y.isFinite() }) {
                return GestureCommandSample(timestampMs, tracked = false, handCenterX = null, pinchRatio = null)
            }
            val palmScale = distance(wrist, palm)
            if (!palmScale.isFinite() || palmScale <= 1e-4f) {
                return GestureCommandSample(timestampMs, tracked = false, handCenterX = null, pinchRatio = null)
            }
            val centerX = ((wrist.x + palm.x) * 0.5f).let { if (mirrorHorizontal) 1f - it else it }
            val pinchRatio = distance(thumbTip, indexTip) / palmScale
            if (!centerX.isFinite() || centerX !in 0f..1f || !pinchRatio.isFinite() || pinchRatio <= 0f) {
                return GestureCommandSample(timestampMs, tracked = false, handCenterX = null, pinchRatio = null)
            }
            return GestureCommandSample(timestampMs, tracked = true, handCenterX = centerX, pinchRatio = pinchRatio)
        }

        private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float = hypot(a.x - b.x, a.y - b.y)
    }
}
