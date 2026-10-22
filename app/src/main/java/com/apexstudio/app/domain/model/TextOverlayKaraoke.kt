package com.apexstudio.app.domain.model

import java.security.MessageDigest

/** Pure helpers shared by Compose preview and Android Canvas export rendering. */
data class KaraokeHighlightRange(val startUtf16: Int, val endUtf16Exclusive: Int)

object TextOverlayKaraoke {
    /** Respect every layer's actual local window, including legacy inclusive end timestamps. */
    fun isVisibleForEditor(overlay: TextOverlay, clipOutputTimeMs: Long): Boolean =
        overlay.isActiveAt(clipOutputTimeMs)

    fun fingerprint(sourceText: String): String = MessageDigest.getInstance("SHA-256")
        .digest(sourceText.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Null means absent, malformed, or stale metadata; never highlight edited text. */
    fun validRange(overlay: TextOverlay): KaraokeHighlightRange? {
        val start = overlay.highlightStartUtf16 ?: return null
        val end = overlay.highlightEndUtf16 ?: return null
        if (overlay.highlightSourceFingerprint != fingerprint(overlay.text)) return null
        if (start < 0 || end <= start || end > overlay.text.length) return null
        if (overlay.text.substring(start, end).isBlank()) return null
        return KaraokeHighlightRange(start, end)
    }
}
