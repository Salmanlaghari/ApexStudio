package com.apexstudio.app.domain.model.timeline

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KaraokePhraseGroupTimelineSyncTest {
    @Test
    fun phraseGroupIdSurvivesLegacyToTypedAndTypedToLegacySynchronization() {
        val clip = MediaClip(
            id = "clip", name = "clip", uri = "file:///clip.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(TextOverlay.of(id = "slice", text = "Hello world")
                .copy(karaokePhraseGroupId = "phrase-one"))
        )
        val initial = Project("project", "Project", 1_000L, clips = listOf(clip)).syncSharedTimeline()
        val typed = initial.timeline.tracks.filterIsInstance<TextTrack>().single().items.single()
        assertEquals("phrase-one", typed.karaokePhraseGroupId)

        val editedTimeline = initial.timeline.copy(tracks = initial.timeline.tracks.map { track ->
            if (track is TextTrack) track.copy(items = track.items.map {
                it.copy(karaokePhraseGroupId = "typed-edit")
            }) else track
        })
        val resynchronized = initial.copy(timeline = editedTimeline).syncSharedTimeline()

        assertEquals("typed-edit", resynchronized.clips.single().textOverlays.single().karaokePhraseGroupId)
    }
}
