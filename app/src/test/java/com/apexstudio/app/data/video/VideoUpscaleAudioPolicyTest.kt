package com.apexstudio.app.data.video

import org.junit.Assert.*
import org.junit.Test

class VideoUpscaleAudioPolicyTest {
    @Test fun aacWithinVideoDurationIsPassthroughEligible() {
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 500_000L, 1_000L)
        VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 1_000_999L, 1_000L)
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

    @Test fun audioTailBeyondVideoIsRejectedRatherThanExtendingOutput() {
        try {
            VideoUpscaleEngine.validateAudioPassthrough("audio/mp4a-latm", 2_000_000L, 1_000L)
            fail("Audio-only tail must not be introduced")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("extends beyond the video"))
        }
    }

    @Test fun packetBoundaryIsExclusiveAndNegativeOrEndTimestampsAreNotWritten() {
        assertFalse(VideoUpscaleEngine.audioSampleWithinVideo(-1L, 1_000L))
        assertTrue(VideoUpscaleEngine.audioSampleWithinVideo(0L, 1_000L))
        assertTrue(VideoUpscaleEngine.audioSampleWithinVideo(999_999L, 1_000L))
        assertFalse(VideoUpscaleEngine.audioSampleWithinVideo(1_000_000L, 1_000L))
        assertFalse(VideoUpscaleEngine.audioSampleWithinVideo(Long.MAX_VALUE, 1_000L))
    }
}
