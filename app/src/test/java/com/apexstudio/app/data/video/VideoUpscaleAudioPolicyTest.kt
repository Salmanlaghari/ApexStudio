package com.apexstudio.app.data.video

import org.junit.Assert.*
import org.junit.Test

class VideoUpscaleAudioPolicyTest {
    @Test fun longerEqualAndShorterAacTracksAreEligibleForBoundaryTrim() {
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 2_000_000L, 1_000L)
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 1_000_000L, 1_000L)
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 500_000L, 1_000L)
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", null, 1_000L)
    }

    @Test fun unsupportedAudioFailsExplicitlyInsteadOfDroppingSound() {
        listOf("audio/opus", "audio/vorbis", "audio/raw", null).forEach { mime ->
            try {
                VideoUpscaleEngine.validateAudioPassthrough(mime, 500_000L, 1_000L)
                fail("Unsupported audio must fail before processing")
            } catch (failure: IllegalArgumentException) {
                assertTrue(failure.message.orEmpty().contains("AAC"))
            }
        }
    }

    @Test fun completeAacPacketsMustEndAtOrBeforeProcessedVideoBoundary() {
        val videoEndUs = 1_000_000L
        // A 44.1 kHz AAC-LC packet is 1024 samples (about 23.22 ms).
        val packetDurationUs = VideoUpscaleEngine.aacPacketDurationUs(44_100, 2)
        assertEquals(23_220L, packetDurationUs)

        // Long source audio is safely trimmed; packets that fit remain synchronized from PTS zero.
        assertTrue(VideoUpscaleEngine.audioPacketEndsWithinVideo(976_780L, packetDurationUs!!, videoEndUs))
        assertFalse(VideoUpscaleEngine.audioPacketEndsWithinVideo(976_781L, packetDurationUs, videoEndUs))
        assertFalse(VideoUpscaleEngine.audioPacketEndsWithinVideo(videoEndUs, packetDurationUs, videoEndUs))

        // Equal-length audio retains a packet ending exactly at video EOS; shorter audio is retained.
        assertTrue(VideoUpscaleEngine.audioPacketEndsWithinVideo(464_399L, packetDurationUs, videoEndUs))
        assertFalse(VideoUpscaleEngine.audioPacketEndsWithinVideo(-1L, packetDurationUs, videoEndUs))
        assertFalse(VideoUpscaleEngine.audioPacketEndsWithinVideo(0L, 0L, videoEndUs))
        assertFalse(VideoUpscaleEngine.audioPacketEndsWithinVideo(Long.MAX_VALUE, packetDurationUs, videoEndUs))
    }
}
