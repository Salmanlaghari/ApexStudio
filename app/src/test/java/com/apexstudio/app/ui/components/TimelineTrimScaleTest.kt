package com.apexstudio.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTrimScaleTest {
    @Test
    fun `trim drag conversion accounts for physical pixel density`() {
        val msToDp = 0.1f // 100 dp per second
        val density = 2f
        val deltaPx = sourceTrimDeltaToPx(1_000L, msToDp, density)

        assertEquals(200f, deltaPx, 0.001f)
        assertEquals(1_000L, sourceTrimDeltaToMs(deltaPx, msToDp, density))
    }

    @Test
    fun `trim conversion preserves negative drags at unit density`() {
        val msToDp = 0.25f
        val density = 1f
        val deltaPx = sourceTrimDeltaToPx(-400L, msToDp, density)

        assertEquals(-100f, deltaPx, 0.001f)
        assertEquals(-400L, sourceTrimDeltaToMs(deltaPx, msToDp, density))
    }

    @Test
    fun `same dp distance commits same time across densities and zoom levels`() {
        listOf(1f, 1.5f, 2f, 3.5f).forEach { density ->
            listOf(0.125f, 0.25f, 0.5f).forEach { scale ->
                assertEquals(800L, sourceTrimDeltaToMs(800f * scale * density, scale, density))
            }
        }
    }

    @Test
    fun `invalid drag and timeline scale do not produce a source edit`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(0L, sourceTrimDeltaToMs(invalid, .1f, 2f))
            assertEquals(0L, sourceTrimDeltaToMs(10f, invalid, 2f))
            assertEquals(0L, sourceTrimDeltaToMs(10f, .1f, invalid))
        }
        assertEquals(0L, sourceTrimDeltaToMs(10f, 0f, 2f))
        assertEquals(0L, sourceTrimDeltaToMs(10f, -.1f, 2f))
        assertEquals(0L, sourceTrimDeltaToMs(10f, .1f, 0f))
    }
}
