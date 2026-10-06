package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.TextOverlay

/**
 * Shared text-animation math for ApexStudio's pro text tools.
 *
 * A single engine drives BOTH surfaces so preview and export can
 * never drift apart:
 *
 *  - **Editor preview** — [com.apexstudio.app.ui.screens.editor.AnimatedTextOverlayView]
 *    evaluates [compute] at the playhead and applies the state via
 *    `graphicsLayer`.
 *  - **Export** — [com.apexstudio.app.data.effect.TextOverlayGlEffect]
 *    re-rasterises the caption sprite through
 *    [TextSpriteRenderer] whenever [stateKey] changes between frames.
 *
 * Easing curves mirror the app's existing animation system
 * ([com.apexstudio.app.domain.model.KeyframeCurve]): POP_SPRING uses
 * the exact damped-spring settle the keyframe engine uses for
 * CapCut-style "Spring" entrances, and SLIDE_BOUNCE uses its
 * easeOutBack overshoot.
 */
object TextAnimEngine {

    /**
     * Animated appearance of one overlay at a single moment.
     *
     * Translations are fractions of the frame (X of width, Y of
     * height), blur is a fraction of the frame height, rotations are
     * degrees. [charsToShow] == null means the full string is drawn
     * (typewriter is the only preset that truncates).
     */
    data class State(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val transXFrac: Float = 0f,
        val transYFrac: Float = 0f,
        val rotXDeg: Float = 0f,
        val rotYDeg: Float = 0f,
        val rotZDeg: Float = 0f,
        val blurFrac: Float = 0f,
        val charsToShow: Int? = null
    ) {
        companion object {
            val Identity = State()
        }

        /** Combines an intro/loop state with an outro state. */
        operator fun plus(outro: State): State = State(
            alpha = (alpha * outro.alpha).coerceIn(0f, 1f),
            scaleX = scaleX * outro.scaleX,
            scaleY = scaleY * outro.scaleY,
            transXFrac = transXFrac + outro.transXFrac,
            transYFrac = transYFrac + outro.transYFrac,
            rotXDeg = rotXDeg + outro.rotXDeg,
            rotYDeg = rotYDeg + outro.rotYDeg,
            rotZDeg = rotZDeg + outro.rotZDeg,
            blurFrac = (blurFrac + outro.blurFrac).coerceAtLeast(0f),
            charsToShow = outro.charsToShow ?: charsToShow
        )
    }

    // ------------------------------------------------------------------
    // Easing — same family as KeyframeCurve so text entrances feel like
    // the rest of the app's animation system.
    // ------------------------------------------------------------------

    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    private fun easeInCubic(t: Float): Float = t * t * t

    /** Ease-out with a single overshoot past 1.0 (KeyframeCurve.OVERSHOOT). */
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Damped spring oscillation settling at 1.0 (KeyframeCurve.SPRING). */
    private fun springSettle(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        return 1f - kotlin.math.exp(-6f * t) * kotlin.math.cos(11f * t)
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Full animated state of [overlay] at [timeMs] (clip timeline ms):
     * intro/loop animation from [TextOverlay.animationType] timed at
     * the clip start, plus the outro from
     * [TextOverlay.outroAnimationType] timed at the clip end.
     *
     * @param forExport the GL export path can't do true perspective
     *   rotation, so legacy 3D presets degrade to 2D squash/tilt
     *   analogues that preserve the timing and feel.
     */
    fun compute(overlay: TextOverlay, timeMs: Long, forExport: Boolean = false): State {
        val intro = computeIntro(overlay, timeMs, forExport)
        val outro = computeOutro(overlay, timeMs)
        return intro + outro
    }

    /**
     * True when the overlay's appearance can change over time, i.e.
     * the export path must re-evaluate it per frame instead of
     * baking one static sprite.
     */
    fun isAnimated(overlay: TextOverlay): Boolean {
        if (!normalizeIntro(overlay.animationType).equals("NONE", ignoreCase = true)) return true
        if (!normalizeOutro(overlay.outroAnimationType).equals("NONE", ignoreCase = true)) return true
        return false
    }

    /**
     * Cache key for the export path: re-rasterise the sprite only
     * when this changes. Looping animations quantise time to ~40ms
     * buckets so a pulse doesn't force more than ~25 texture uploads
     * per second.
     */
    fun stateKey(overlay: TextOverlay, timeMs: Long): Any {
        val type = normalizeIntro(overlay.animationType)
        val t = if (type == "PULSE" || type == "BOUNCE") (timeMs / 40L) * 40L else timeMs
        val s = compute(overlay, t, forExport = true)
        return listOf(
            (s.alpha * 255f).toInt(),
            (s.scaleX * 1000f).toInt(),
            (s.scaleY * 1000f).toInt(),
            (s.transXFrac * 10000f).toInt(),
            (s.transYFrac * 10000f).toInt(),
            (s.rotXDeg * 10f).toInt(),
            (s.rotYDeg * 10f).toInt(),
            (s.rotZDeg * 10f).toInt(),
            (s.blurFrac * 10000f).toInt(),
            s.charsToShow ?: -1
        )
    }

    // ------------------------------------------------------------------
    // Intro / loop
    // ------------------------------------------------------------------

    /** Canonical intro ids; legacy ids from older panels map here. */
    fun normalizeIntro(type: String): String = when (type.uppercase()) {
        "FADE" -> "FADE_IN"
        "POP" -> "POP_SPRING"
        else -> type.uppercase()
    }

    fun normalizeOutro(type: String): String = type.uppercase()

    private fun computeIntro(overlay: TextOverlay, timeMs: Long, forExport: Boolean): State {
        val type = normalizeIntro(overlay.animationType)
        if (type == "NONE") return State.Identity
        val dur = overlay.animationDurationMs.coerceAtLeast(200L)
        val elapsed = (timeMs - overlay.startMs).coerceAtLeast(0L)
        val p = (elapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        return when (type) {
            "FADE_IN" -> State(alpha = easeOutCubic(p))
            "SLIDE_UP" -> State(
                alpha = easeOutCubic(p),
                transYFrac = (1f - easeOutCubic(p)) * 0.08f
            )
            "TYPEWRITER" -> {
                val total = overlay.text.length
                val chars = (total * easeOutCubic(p)).toInt().coerceIn(0, total)
                State(charsToShow = chars)
            }
            "POP_SPRING" -> {
                val s = springSettle(p)
                State(
                    alpha = p.coerceIn(0f, 1f),
                    scaleX = 0.3f + 0.7f * s,
                    scaleY = 0.3f + 0.7f * s
                )
            }
            "BLUR_IN" -> State(
                alpha = easeOutCubic(p),
                blurFrac = (1f - easeOutCubic(p)) * 0.035f,
                scaleX = 0.92f + 0.08f * p,
                scaleY = 0.92f + 0.08f * p
            )
            "SLIDE_BOUNCE" -> {
                // Rises from below with an easeOutBack overshoot so it
                // pops slightly past its resting point, then settles.
                val settle = easeOutBack(p).coerceIn(-0.2f, 1.2f)
                State(
                    alpha = p.coerceIn(0f, 1f),
                    transYFrac = (1f - settle) * 0.12f
                )
            }
            // Looping accents — run for the whole visible window.
            "PULSE" -> {
                val loop = kotlin.math.sin(
                    (timeMs - overlay.startMs).toFloat() / 1200f * 2f * kotlin.math.PI.toFloat()
                )
                val s = 1f + 0.05f * loop
                State(scaleX = s, scaleY = s)
            }
            "BOUNCE" -> {
                val loop = kotlin.math.abs(
                    kotlin.math.sin(
                        (timeMs - overlay.startMs).toFloat() / 900f * kotlin.math.PI.toFloat()
                    )
                )
                State(transYFrac = -0.02f * loop)
            }
            // Legacy 3D presets: full perspective rotation in the
            // Compose preview; 2D squash/tilt analogues in export so
            // the MP4 still visibly animates with the same timing.
            "3D_FLIP_X", "FLIP_3D_X" -> if (forExport) State(
                alpha = p,
                scaleY = kotlin.math.sin(p * kotlin.math.PI.toFloat() / 2f).coerceIn(0.01f, 1f),
                scaleX = 0.7f + 0.3f * p
            ) else State(
                alpha = p,
                rotXDeg = (1f - p) * 90f,
                scaleX = 0.6f + 0.4f * p,
                scaleY = 0.6f + 0.4f * p
            )
            "3D_ROTATE_Y", "ROTATE_3D_Y" -> if (forExport) State(
                alpha = p,
                scaleX = kotlin.math.sin(p * kotlin.math.PI.toFloat() / 2f).coerceIn(0.01f, 1f),
                scaleY = 0.7f + 0.3f * p
            ) else State(
                alpha = p,
                rotYDeg = (1f - p) * 90f,
                scaleX = 0.6f + 0.4f * p,
                scaleY = 0.6f + 0.4f * p
            )
            "3D_DEPTH_WARP", "DEPTH_WARP" -> {
                val w = p * p
                if (forExport) State(alpha = p, scaleX = 0.15f + 0.85f * w, scaleY = 0.15f + 0.85f * w)
                else State(
                    alpha = p,
                    scaleX = 0.15f + 0.85f * w,
                    scaleY = 0.15f + 0.85f * w,
                    rotXDeg = (1f - p) * 35f,
                    rotYDeg = (1f - p) * -25f
                )
            }
            "3D_SWING", "SWING_3D" -> {
                val swing = kotlin.math.sin(p * kotlin.math.PI.toFloat() * 2.5f) * (1f - p)
                if (forExport) State(rotZDeg = swing * 15f)
                else State(rotYDeg = swing * 45f, rotZDeg = swing * 15f)
            }
            "3D_TUMBLE", "TUMBLE_3D" -> if (forExport) State(
                alpha = p,
                rotZDeg = (1f - p) * 120f,
                scaleX = 0.4f + 0.6f * p,
                scaleY = 0.4f + 0.6f * p
            ) else State(
                alpha = p,
                rotXDeg = (1f - p) * 120f,
                rotYDeg = (1f - p) * 120f,
                scaleX = 0.4f + 0.6f * p,
                scaleY = 0.4f + 0.6f * p
            )
            "3D_ISOMETRIC", "ISOMETRIC_3D" -> if (forExport) State(
                alpha = p,
                rotZDeg = -8f,
                scaleX = 0.7f + 0.3f * p,
                scaleY = 0.7f + 0.3f * p
            ) else State(
                alpha = p,
                rotXDeg = 25f,
                rotYDeg = -25f,
                scaleX = 0.7f + 0.3f * p,
                scaleY = 0.7f + 0.3f * p
            )
            else -> State.Identity
        }
    }

    // ------------------------------------------------------------------
    // Outro — timed at the END of the overlay's window.
    // ------------------------------------------------------------------

    private fun computeOutro(overlay: TextOverlay, timeMs: Long): State {
        val type = normalizeOutro(overlay.outroAnimationType)
        if (type == "NONE") return State.Identity
        if (overlay.endMs == Long.MAX_VALUE) return State.Identity
        val dur = overlay.outroAnimationDurationMs.coerceAtLeast(150L)
        val outroStart = overlay.endMs - dur
        if (timeMs < outroStart) return State.Identity
        val q = ((timeMs - outroStart).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        return when (type) {
            "FADE_OUT" -> State(alpha = 1f - easeInCubic(q))
            "SLIDE_DOWN" -> State(
                alpha = 1f - q,
                transYFrac = easeInCubic(q) * 0.08f
            )
            "SHRINK" -> {
                val s = (1f - easeInCubic(q) * 0.92f).coerceIn(0.08f, 1f)
                State(alpha = 1f - q, scaleX = s, scaleY = s)
            }
            else -> State.Identity
        }
    }
}
