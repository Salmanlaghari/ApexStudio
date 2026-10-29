package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineAudioLanePartitionTest {
    @Test
    fun `embedded source audio music and voice effects are assigned to distinct lanes`() {
        val source = AudioTrack("a0", "Video audio", "content://video", kind = AudioTrack.Kind.ORIGINAL_VIDEO,
            sourceVideoClipId = "v1")
        val music = AudioTrack("a1", "Song", "file:///song.m4a", kind = AudioTrack.Kind.MUSIC)
        val voice = AudioTrack("a2", "Voice", "file:///voice.wav", kind = AudioTrack.Kind.SFX)

        val lanes = partitionAudioTracksForTimeline(listOf(source, music, voice))

        assertEquals(listOf(source), lanes.originalVideoAudio)
        assertEquals(listOf(music), lanes.music)
        assertEquals(listOf(voice), lanes.voiceAndSfx)
    }
}
