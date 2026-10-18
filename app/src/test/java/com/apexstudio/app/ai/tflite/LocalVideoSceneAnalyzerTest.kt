package com.apexstudio.app.ai.tflite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoSceneAnalyzerTest {
    @Test fun suggestionsAreBoundedToAnalyzedSourceWindowAndSortedByMeasuredScore() {
        val analysis = SceneAnalysis(
            boundaries = listOf(
                SceneBoundary(500L, .9f), // exactly at start: not a cut inside the window
                SceneBoundary(1_000L, .7f),
                SceneBoundary(2_500L, .8f),
                SceneBoundary(3_000L, .9f) // exactly at end
            ),
            highlights = listOf(
                Highlight(0L, 1_000L, .35f, "visual activity"),
                Highlight(1_500L, 2_800L, .82f, "audio energy"),
                Highlight(2_950L, 3_000L, .99f, "too short after intersection")
            )
        )

        val suggestions = LocalSceneSuggestionMapper.map(analysis, 500L, 3_000L)

        assertEquals(listOf(1_000L, 2_500L), suggestions.cuts.map { it.timestampMs })
        assertEquals(listOf(1_500L, 500L), suggestions.smartTrims.map { it.startMs })
        assertEquals(2_800L, suggestions.smartTrims.first().endMs)
        assertTrue(suggestions.smartTrims.first().rationale.contains("Heuristic"))
        assertTrue(suggestions.smartTrims.first().rationale.contains("review"))
        assertEquals(.82f, suggestions.smartTrims.first().score, .0001f)
    }

    @Test fun mapperDropsOutOfRangeCutsAndEmptyOrTooShortTrimWindows() {
        val analysis = SceneAnalysis(
            boundaries = listOf(SceneBoundary(50L, .8f), SceneBoundary(400L, .6f), SceneBoundary(900L, .9f)),
            highlights = listOf(
                Highlight(10L, 20L, 1f, "tiny"),
                Highlight(50L, 550L, .5f, "partially overlaps"),
                Highlight(850L, 950L, .4f, "only 50ms overlaps")
            )
        )
        val suggestions = LocalSceneSuggestionMapper.map(analysis, 100L, 900L)
        assertEquals(listOf(400L), suggestions.cuts.map { it.timestampMs })
        assertEquals(1, suggestions.smartTrims.size)
        assertEquals(100L, suggestions.smartTrims.single().startMs)
        assertEquals(550L, suggestions.smartTrims.single().endMs)
    }
}
