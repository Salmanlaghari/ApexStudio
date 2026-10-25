package com.apexstudio.app.data.captions

import com.apexstudio.app.domain.model.KaraokeHighlightRange
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** The only supported caption backgrounds in this first style set. */
enum class CaptionStyleBackground { NONE, ROUNDED_BOX }

enum class CaptionWordAnimation {
    FADE_IN,
    KARAOKE_FILL,
    WORD_POP,
    TYPEWRITER,
    BOUNCE
}

/** A complete, deterministic look for one caption track. */
data class CaptionStyle(
    val id: String,
    val name: String,
    val description: String,
    val fontWeight: Int,
    val fontSizeScale: Float,
    val textColorArgb: Long,
    val highlightColorArgb: Long,
    val background: CaptionStyleBackground,
    val backgroundColorArgb: Long,
    val positionY: Float,
    val wordAnimation: CaptionWordAnimation
) {
    fun applyTo(overlay: TextOverlay): TextOverlay = overlay.copy(
        captionStyleId = id,
        captionFontWeight = fontWeight,
        isBold = fontWeight >= 600,
        sizeScale = fontSizeScale,
        colorArgb = textColorArgb,
        highlightColorArgb = highlightColorArgb,
        bgArgb = if (background == CaptionStyleBackground.ROUNDED_BOX) backgroundColorArgb else null,
        y = positionY,
        gradientStartArgb = null,
        gradientEndArgb = null
    )
}

/** Exactly five curated caption looks; unknown IDs safely use Classic behavior. */
object CaptionStyles {
    const val CLASSIC_ID = "classic"
    const val KARAOKE_FILL_ID = "karaoke_fill"
    const val WORD_POP_ID = "word_pop"
    const val TYPEWRITER_ID = "typewriter"
    const val BOUNCE_ID = "bounce"

    val presets: List<CaptionStyle> = listOf(
        CaptionStyle(
            CLASSIC_ID, "Classic", "Plain lines with a soft fade-in", 700, 0.90f,
            0xFFFFFFFFL, 0xFFFFFFFFL, CaptionStyleBackground.NONE, 0x00000000L,
            0.82f, CaptionWordAnimation.FADE_IN
        ),
        CaptionStyle(
            KARAOKE_FILL_ID, "Karaoke Fill", "Highlight each spoken word", 700, 0.94f,
            0xFFFFFFFFL, 0xFFFFD740L, CaptionStyleBackground.ROUNDED_BOX, 0xB3000000L,
            0.82f, CaptionWordAnimation.KARAOKE_FILL
        ),
        CaptionStyle(
            WORD_POP_ID, "Word Pop", "Pop the current word", 800, 1.00f,
            0xFFFFFFFFL, 0xFF58E6FFL, CaptionStyleBackground.ROUNDED_BOX, 0xB3000000L,
            0.80f, CaptionWordAnimation.WORD_POP
        ),
        CaptionStyle(
            TYPEWRITER_ID, "Typewriter", "Reveal words as they are spoken", 600, 0.92f,
            0xFFFFFFFFL, 0xFF58E6FFL, CaptionStyleBackground.NONE, 0x00000000L,
            0.82f, CaptionWordAnimation.TYPEWRITER
        ),
        CaptionStyle(
            BOUNCE_ID, "Bounce", "Give the current word a small bounce", 700, 0.96f,
            0xFFFFFFFFL, 0xFFFFD740L, CaptionStyleBackground.ROUNDED_BOX, 0xB3000000L,
            0.80f, CaptionWordAnimation.BOUNCE
        )
    )

    fun find(id: String?): CaptionStyle? = presets.firstOrNull { it.id == id }
    fun orClassic(id: String?): CaptionStyle = find(id) ?: presets.first()
}

/** Word timing interval in milliseconds and source-text UTF-16 offsets. End time is exclusive. */
data class CaptionWordTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val startUtf16: Int = 0,
    val endUtf16Exclusive: Int = text.length
)

/** Result of evaluating one set of timed words at a clip timestamp. */
data class CaptionTimingFrame(
    val visibleWordIndices: List<Int> = emptyList(),
    val activeWordIndex: Int? = null,
    val highlightedWordIndex: Int? = null,
    val alpha: Float = 0f,
    val scale: Float = 1f,
    val verticalOffsetFraction: Float = 0f
)

/** Pure timing math used by the editor preview, exported frames, and JVM unit tests. */
object CaptionStyleTiming {
    private const val FADE_IN_MS = 160f

    fun evaluate(words: List<CaptionWordTiming>, style: CaptionStyle, timeMs: Long): CaptionTimingFrame {
        if (words.isEmpty() || words.any { it.endMs <= it.startMs }) return CaptionTimingFrame()
        val ordered = words.withIndex().sortedBy { it.value.startMs }
        val firstStart = ordered.first().value.startMs
        val lastEnd = ordered.maxOf { it.value.endMs }
        if (timeMs < firstStart || timeMs >= lastEnd) return CaptionTimingFrame()

        val activeOriginalIndex = ordered.lastOrNull { (_, word) ->
            timeMs >= word.startMs && timeMs < word.endMs
        }?.index
        val visible = when (style.wordAnimation) {
            CaptionWordAnimation.TYPEWRITER -> words.indices.filter { words[it].startMs <= timeMs }
            else -> words.indices.toList()
        }
        val highlighted = when (style.wordAnimation) {
            CaptionWordAnimation.KARAOKE_FILL,
            CaptionWordAnimation.WORD_POP,
            CaptionWordAnimation.BOUNCE -> activeOriginalIndex
            CaptionWordAnimation.FADE_IN,
            CaptionWordAnimation.TYPEWRITER -> null
        }
        val active = activeOriginalIndex?.let(words::get)
        val progress = active?.let { intervalProgress(it.startMs, it.endMs, timeMs) } ?: 0f
        val alpha = if (style.wordAnimation == CaptionWordAnimation.FADE_IN) {
            ((timeMs - firstStart).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        } else 1f
        val scale = if (style.wordAnimation == CaptionWordAnimation.WORD_POP && active != null) {
            wordPopScale(progress)
        } else 1f
        val offset = if (style.wordAnimation == CaptionWordAnimation.BOUNCE && active != null) {
            bounceOffset(progress)
        } else 0f
        return CaptionTimingFrame(visible, activeOriginalIndex, highlighted, alpha, scale, offset)
    }

    internal fun intervalProgress(startMs: Long, endMs: Long, timeMs: Long): Float =
        ((timeMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    internal fun wordPopScale(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val pulse = sin((p * PI).toFloat()).coerceAtLeast(0f) * exp(-3f * p)
        return 1f + 0.28f * pulse
    }

    internal fun bounceOffset(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val hop = abs(sin((p * 3.5f * PI).toFloat())) * (1f - p)
        return -0.018f * hop
    }
}

/** Style-specific visual state for one timed text overlay. */
data class CaptionOverlayFrame(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val verticalOffsetFraction: Float = 0f,
    val visibleChars: Int? = null,
    val highlightRange: KaraokeHighlightRange? = null
)

/** Adapter from the persisted caption overlays to the shared preview/export timing math. */
object CaptionStyleEngine {
    fun frameForOverlay(
        overlay: TextOverlay,
        timeMs: Long,
        trackOverlays: List<TextOverlay> = listOf(overlay)
    ): CaptionOverlayFrame {
        val style = CaptionStyles.find(overlay.captionStyleId) ?: return CaptionOverlayFrame()
        val validRange = TextOverlayKaraoke.validRange(overlay)
        val wordRange = validRange ?: singleWordRange(overlay.text, style)
        val endExclusive = endExclusive(overlay)
        val oneWordTiming = CaptionWordTiming(
            text = wordRange?.let { overlay.text.substring(it.startUtf16, it.endUtf16Exclusive) } ?: overlay.text,
            startMs = overlay.startMs,
            endMs = endExclusive,
            startUtf16 = wordRange?.startUtf16 ?: 0,
            endUtf16Exclusive = wordRange?.endUtf16Exclusive ?: overlay.text.length
        )
        val oneWordFrame = CaptionStyleTiming.evaluate(listOf(oneWordTiming), style, timeMs)

        val typewriterEnd = if (style.wordAnimation == CaptionWordAnimation.TYPEWRITER) {
            typewriterVisibleEnd(overlay, timeMs, trackOverlays, validRange, style)
        } else null
        val highlight = when (style.wordAnimation) {
            CaptionWordAnimation.KARAOKE_FILL,
            CaptionWordAnimation.WORD_POP,
            CaptionWordAnimation.BOUNCE -> wordRange
            CaptionWordAnimation.FADE_IN,
            CaptionWordAnimation.TYPEWRITER -> null
        }
        val fadeStartMs = if (style.wordAnimation == CaptionWordAnimation.FADE_IN &&
            overlay.highlightSourceFingerprint != null) {
            trackOverlays.asSequence()
                .filter { it.text == overlay.text && it.highlightSourceFingerprint == overlay.highlightSourceFingerprint }
                .minOfOrNull { it.startMs } ?: overlay.startMs
        } else overlay.startMs
        val styleAlpha = if (style.wordAnimation == CaptionWordAnimation.FADE_IN) {
            ((timeMs - fadeStartMs).toFloat() / 160f).coerceIn(0f, 1f)
        } else oneWordFrame.alpha
        return CaptionOverlayFrame(
            alpha = styleAlpha,
            scale = oneWordFrame.scale,
            verticalOffsetFraction = oneWordFrame.verticalOffsetFraction,
            visibleChars = typewriterEnd,
            highlightRange = highlight
        )
    }

    /** Compact cache value; style animations must invalidate the exported sprite as time advances. */
    fun stateKey(overlay: TextOverlay, timeMs: Long, trackOverlays: List<TextOverlay> = listOf(overlay)): Any {
        val frame = frameForOverlay(overlay, timeMs, trackOverlays)
        return listOf(
            (frame.alpha * 1000f).toInt(),
            (frame.scale * 1000f).toInt(),
            (frame.verticalOffsetFraction * 10000f).toInt(),
            frame.visibleChars ?: -1,
            frame.highlightRange?.startUtf16 ?: -1,
            frame.highlightRange?.endUtf16Exclusive ?: -1
        )
    }

    private fun typewriterVisibleEnd(
        overlay: TextOverlay,
        timeMs: Long,
        trackOverlays: List<TextOverlay>,
        fallbackRange: KaraokeHighlightRange?,
        style: CaptionStyle
    ): Int {
        if (overlay.highlightSourceFingerprint == null) {
            return fallbackRange?.endUtf16Exclusive ?: overlay.text.length
        }
        val samePhrase = trackOverlays.filter {
            it.text == overlay.text &&
                it.highlightSourceFingerprint == overlay.highlightSourceFingerprint &&
                it.captionStyleId == overlay.captionStyleId
        }.sortedBy { it.startMs }
        val cluster = ArrayList<TextOverlay>()
        var clusterEnd = Long.MIN_VALUE
        var target: List<TextOverlay>? = null
        samePhrase.forEach { candidate ->
            val candidateEnd = endExclusive(candidate)
            if (cluster.isNotEmpty() && candidate.startMs > clusterEnd) {
                if (cluster.any { it.id == overlay.id }) target = cluster.toList()
                cluster.clear()
                clusterEnd = Long.MIN_VALUE
            }
            cluster += candidate
            clusterEnd = maxOf(clusterEnd, candidateEnd)
        }
        if (cluster.any { it.id == overlay.id }) target = cluster.toList()
        val wordTimings = target.orEmpty().mapNotNull { candidate ->
            val range = TextOverlayKaraoke.validRange(candidate) ?: return@mapNotNull null
            CaptionWordTiming(
                text = candidate.text.substring(range.startUtf16, range.endUtf16Exclusive),
                startMs = candidate.startMs,
                endMs = endExclusive(candidate),
                startUtf16 = range.startUtf16,
                endUtf16Exclusive = range.endUtf16Exclusive
            )
        }.distinctBy { Triple(it.startMs, it.endMs, it.startUtf16) }
        if (wordTimings.isEmpty()) return fallbackRange?.endUtf16Exclusive ?: overlay.text.length
        if (timeMs >= wordTimings.maxOf { it.endMs }) return overlay.text.length
        val frame = CaptionStyleTiming.evaluate(wordTimings, style, timeMs)
        val visibleEnd = frame.visibleWordIndices.maxOfOrNull { wordTimings[it].endUtf16Exclusive }
            ?: return 0
        return if (frame.visibleWordIndices.size == wordTimings.size && timeMs >= wordTimings.maxOf { it.endMs }) {
            overlay.text.length
        } else visibleEnd.coerceIn(0, overlay.text.length)
    }

    private fun singleWordRange(text: String, style: CaptionStyle): KaraokeHighlightRange? {
        val isWordAnimation = style.wordAnimation == CaptionWordAnimation.KARAOKE_FILL ||
            style.wordAnimation == CaptionWordAnimation.WORD_POP ||
            style.wordAnimation == CaptionWordAnimation.BOUNCE
        return if (isWordAnimation && text.isNotBlank() && text.trim().none(Char::isWhitespace)) {
            KaraokeHighlightRange(0, text.length)
        } else null
    }

    private fun endExclusive(overlay: TextOverlay): Long = when {
        overlay.rangeEndExclusive -> overlay.endMs
        overlay.endMs == Long.MAX_VALUE -> Long.MAX_VALUE
        else -> overlay.endMs + 1L
    }
}
