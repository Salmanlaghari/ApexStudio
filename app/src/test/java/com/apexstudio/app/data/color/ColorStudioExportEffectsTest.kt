package com.apexstudio.app.data.color

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.adjust.AdjustmentsGlEffect
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorStudioExportEffectsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `project export uses shared LUT then spatial sharpness when enabled`() {
        val effects = createColorStudioVideoEffects(
            context,
            ColorStudioGrade(hdr = 0.4f, sharpness = 0.7f)
        )

        assertEquals(2, effects.size)
        assertTrue(effects[0] is LutFilterGlEffect)
        assertTrue(effects[1] is AdjustmentsGlEffect)
    }

    @Test
    fun `project export omits only spatial stage at zero sharpness`() {
        val effects = createColorStudioVideoEffects(context, ColorStudioGrade(hdr = 0.4f))
        assertEquals(1, effects.size)
        assertTrue(effects.single() is LutFilterGlEffect)
    }

    @Test
    fun `project export effect builder refuses malformed grade`() {
        val failure = runCatching {
            createColorStudioVideoEffects(context, ColorStudioGrade(sharpness = Float.NaN))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `malformed persisted grade skips preview effect construction`() {
        var constructed = false
        var reportedFailure: Exception? = null

        val effects = safeProjectColorStudioPreviewEffects(
            ColorStudioGrade(brightness = Float.NaN),
            createEffects = { constructed = true; listOf("preview") },
            onFailure = { reportedFailure = it }
        )

        assertTrue(effects.isEmpty())
        assertFalse(constructed)
        assertTrue(reportedFailure is IllegalArgumentException)
    }

    @Test
    fun `preview effect construction failure falls back without throwing`() {
        var reportedFailure: Exception? = null

        val effects = safeProjectColorStudioPreviewEffects(
            ColorStudioGrade(),
            createEffects = { throw IllegalArgumentException("malformed LUT source") },
            onFailure = { reportedFailure = it }
        )

        assertTrue(effects.isEmpty())
        assertTrue(reportedFailure is IllegalArgumentException)
    }

    @Test
    fun `transition grade effects are separate for main videos and exclude stills and PiP`() {
        val first = MediaClip("first", "First", "file:///first.mp4", 1_000L, trimEndMs = 1_000L)
        val second = first.copy(id = "second", name = "Second", uri = "file:///second.mp4")
        val pip = first.copy(id = "pip", trackIndex = 1)
        val still = first.copy(id = "still", type = ClipType.IMAGE)

        val effectsByClip = projectColorGradeEffectsByClipId(listOf(first, second, pip, still)) {
            listOf(Any())
        }

        assertEquals(setOf(first.id, second.id), effectsByClip.keys)
        assertNotSame(effectsByClip.getValue(first.id).single(), effectsByClip.getValue(second.id).single())
    }
}
