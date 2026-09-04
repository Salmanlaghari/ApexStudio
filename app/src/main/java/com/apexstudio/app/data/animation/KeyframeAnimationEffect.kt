package com.apexstudio.app.data.animation

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.KeyframeTrack

/**
 * Animates a single clip across the timeline by interpolating a
 * [KeyframeTrack] at the current presentation time and applying
 * the resulting [AnimatedTransform] as a 4x4 matrix to every video
 * frame.
 *
 * Wired into both the live ExoPlayer preview
 * (`ExoPlayer.setVideoEffects(...)`) and the Media3 Transformer
 * export pipeline — the same `GlEffect` instance is re-evaluated
 * for every frame, so whatever the user sees on screen is exactly
 * what bakes into the MP4.
 *
 * `currentTimeMs` is the wall-clock timeline position. It is set
 * from the editor's `LaunchedEffect(state.playerPositionMs)` so
 * the preview animates in lockstep with ExoPlayer's playhead.
 */
@UnstableApi
class KeyframeAnimationEffect(
    private val trackProvider: () -> KeyframeTrack
) {

    /** Build the list of Media3 effects for the current state. */
    fun buildEffects(): List<Effect> {
        val track = trackProvider()
        if (track.isEmpty()) return emptyList()
        val effect = KeyframeMatrixEffect(track)
        return listOf<Effect>(effect)
    }

    /**
     * Media3-style matrix-only effect. Composes with any other
     * `GlEffect` (LUT, speed) in the same `Effects` list. The
     * `getMatrix` callback is invoked by Media3 on the GL thread
     * for every video frame, with the actual frame's presentation
     * timestamp — so the resulting matrix is always in lockstep
     * with the playhead.
     */
    @UnstableApi
    private class KeyframeMatrixEffect(
        private val track: KeyframeTrack
    ) : MatrixTransformation {

        override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
            val timeMs = presentationTimeUs / 1000L
            val t = track.interpolateAt(timeMs)
            return transformToMatrix(t)
        }
    }

    companion object {
        /**
         * Convert a 2D translate / scale / rotation (in normalized
         * output-space units, 0..1) into an Android `Matrix` the
         * Media3 shader program understands.
         *
         * Translate is in normalized output coordinates (0 = frame
         * center, ±1 = half-frame). Scale is multiplied around the
         * frame center so the clip zooms toward / away from the
         * middle instead of the top-left corner.
         */
        fun transformToMatrix(t: AnimatedTransform): android.graphics.Matrix {
            val m = android.graphics.Matrix()
            m.postTranslate(t.translateX, t.translateY)
            m.postScale(t.scale, t.scale, 0f, 0f)
            m.postRotate(t.rotationDeg, 0f, 0f)
            return m
        }
    }
}
