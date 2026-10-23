package com.apexstudio.app.data.export

import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class CaptionExportBatchTest {
    @Test fun `five hundred measured word windows create one text effect not five hundred`() {
        val captions = (0 until 500).map { i ->
            TextOverlay(id = "word-$i", text = "a long phrase with measured words",
                startMs = i * 200L, endMs = (i + 1) * 200L)
        }
        val engine = ExportEngine(RuntimeEnvironment.getApplication())
        assertEquals(1, engine.buildCaptionExportEffects(captions, 16f / 9f).size)
    }

    @Test fun `empty and whitespace-only caption collections allocate no effect`() {
        val engine = ExportEngine(RuntimeEnvironment.getApplication())
        assertTrue(engine.buildCaptionExportEffects(emptyList(), 1f).isEmpty())
        assertTrue(engine.buildCaptionExportEffects(listOf(TextOverlay(id = "blank", text = "  \n")), 1f).isEmpty())
    }

    @Test fun `ordinary overlapping text and karaoke retain a single batch`() {
        val engine = ExportEngine(RuntimeEnvironment.getApplication())
        val text = listOf(
            TextOverlay(id = "ordinary", text = "Title", startMs = 0L, endMs = 10_000L),
            TextOverlay(id = "word", text = "hello world", startMs = 500L, endMs = 900L)
        )
        assertEquals(1, engine.buildCaptionExportEffects(text, 9f / 16f).size)
    }
}
