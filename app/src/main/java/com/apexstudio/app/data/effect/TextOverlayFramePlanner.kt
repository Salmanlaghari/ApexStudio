package com.apexstudio.app.data.effect

import com.apexstudio.app.data.text.TextAnimEngine
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Selects only captions that contribute pixels at a presentation timestamp.
 * The returned overlay order is deliberately the source-list order because
 * TextSpriteRenderer composites later overlays over earlier ones.
 */
internal object TextOverlayFramePlanner {
    data class Plan(
        val overlays: List<TextOverlay>,
        /** Null for an empty frame; otherwise changes whenever rendered pixels may change. */
        val cacheKey: List<OverlayVisualKey>?
    )

    data class OverlayVisualKey(
        val id: String,
        val animationState: Any,
        val mask: com.apexstudio.app.domain.model.VideoMask?
    )

    fun plan(overlays: List<TextOverlay>, timeMs: Long): Plan {
        val active = overlays.filter { it.text.isNotBlank() && it.isActiveAt(timeMs) }
        if (active.isEmpty()) return Plan(emptyList(), null)

        // Full state covers intro/outro animation and keyframes. The evaluated
        // mask is included in full (rather than a partial hash) so mask-only
        // changes also invalidate the one batched sprite.
        val key = active.map { overlay ->
            OverlayVisualKey(
                id = overlay.id,
                animationState = TextAnimEngine.stateKeyFull(overlay, timeMs),
                mask = overlay.effectiveMaskAt(timeMs)
            )
        }
        return Plan(active, key)
    }
}
