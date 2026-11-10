package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.apexstudio.app.domain.model.AudioTrack
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Unit tests for the pure helpers in [MusicExportMixer].
 * The MediaCodec pipeline still needs on-device acceptance;
 * building an APK or running Robolectric does not exercise real codecs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicExportMixerTest {

    @Test
    fun `long AAC output is spooled and replayed one sample at a time`() {
        val directory = java.nio.file.Files.createTempDirectory("apex-aac-spool-").toFile()
        val file = File(directory, "samples.bin")
        val count = 50_000
        try {
            EncodedAudioSampleSpool(file).use { spool ->
                repeat(count) { index ->
                    spool.append(EncodedAudioSample(
                        data = byteArrayOf(index.toByte(), (index ushr 8).toByte()),
                        ptsUs = index * 23_219L,
                        flags = index and 1
                    ))
                }
                assertEquals(count, spool.sampleCount)
                spool.finishWriting()
                assertTrue("AAC payloads must be persisted, not retained in an in-memory list", file.length() > count * 2L)

                repeat(count) { index ->
                    val sample = spool.readNext()!!
                    assertArrayEquals(byteArrayOf(index.toByte(), (index ushr 8).toByte()), sample.data)
                    assertEquals(index * 23_219L, sample.ptsUs)
                    assertEquals(index and 1, sample.flags)
                }
                assertNull(spool.readNext())
            }
            assertFalse("temporary AAC spool is deleted after use", file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `known long mixes are accepted only within the WAV intermediate frame limit`() {
        val sampleRate = 44_100
        val maxFrames = MusicExportMixer.MAX_WAV_FRAMES.toLong()
        val withinLimitUs = maxFrames * 1_000_000L / sampleRate

        assertTrue(mixDurationFitsWav(withinLimitUs, sampleRate, maxFrames))
        assertFalse(mixDurationFitsWav(withinLimitUs + 1_000_000L, sampleRate, maxFrames))
        assertNull(mixDurationFrames(0L, sampleRate))
        assertTrue(mixDurationFitsWav(0L, sampleRate, maxFrames))
    }

    @Test
    fun `audio track enumeration failure releases its extractor before retry`() {
        val extractor = Any()
        val enumerationFailure = IllegalStateException("track format unavailable")
        var releases = 0

        val failure = runCatching {
            prepareWithCleanup(
                resource = extractor,
                prepare = { throw enumerationFailure },
                cleanup = { releases++ }
            )
        }.exceptionOrNull()

        assertSame(enumerationFailure, failure)
        assertEquals(1, releases)

        repeat(2) {
            withExportResource(
                acquire = { Any() },
                cleanup = { releases++ }
            ) { /* next export owns an independent extractor */ }
        }
        assertEquals("failed preparation and both retries release their handles", 3, releases)
    }

    @Test
    fun `muxer construction failure releases the already-open video extractor`() {
        var extractorReleases = 0
        var muxerReleases = 0
        val muxerFailure = IllegalStateException("cannot open mux output")

        val failure = runCatching {
            withExportResource(
                acquire = { "video extractor" },
                cleanup = { extractorReleases++ }
            ) {
                withExportResource(
                    acquire = { throw muxerFailure },
                    cleanup = { muxerReleases++ }
                ) { /* unreachable */ }
            }
        }.exceptionOrNull()

        assertSame(muxerFailure, failure)
        assertEquals(1, extractorReleases)
        assertEquals(0, muxerReleases)
    }

    @Test
    fun `cancellation while muxing releases both native handles`() {
        var extractorReleases = 0
        var muxerReleases = 0
        val cancellation = CancellationException("export cancelled")

        val failure = runCatching {
            withExportResource(
                acquire = { "video extractor" },
                cleanup = { extractorReleases++ }
            ) {
                withExportResource(
                    acquire = { "muxer" },
                    cleanup = { muxerReleases++ }
                ) { throw cancellation }
            }
        }.exceptionOrNull()

        assertSame(cancellation, failure)
        assertEquals(1, extractorReleases)
        assertEquals(1, muxerReleases)
    }

    @Test
    fun `undecodable original audio preserves the input instead of muxing music only`() {
        val directory = java.nio.file.Files.createTempDirectory("apex-audio-fallback-").toFile()
        val inputVideo = File(directory, "input.mp4")
        val outputVideo = File(directory, "mixed.mp4")
        val originalBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        try {
            inputVideo.writeBytes(originalBytes)
            val result = requireNotNull(MusicExportMixer.preserveOriginalVideoWhenAudioCannotBeDecoded(
                inputVideo = inputVideo,
                outputVideo = outputVideo,
                inputHasAudioTrack = true,
                originalAudioDecoded = false,
                requestedTrackCount = 2
            ))

            assertArrayEquals("The original container must be copied without re-muxing", originalBytes, outputVideo.readBytes())
            assertEquals(0, result.mixedTracks)
            assertEquals(2, result.skippedTracks)
            assertEquals(AudioMixExportNotice.ORIGINAL_AUDIO_PRESERVED, result.warning)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `original audio fallback is not used when the input has no audio or decoding succeeded`() {
        val inputVideo = File("/not-used-input.mp4")
        val outputVideo = File("/not-used-output.mp4")
        assertNull(MusicExportMixer.preserveOriginalVideoWhenAudioCannotBeDecoded(
            inputVideo, outputVideo, inputHasAudioTrack = false,
            originalAudioDecoded = false, requestedTrackCount = 1
        ))
        assertNull(MusicExportMixer.preserveOriginalVideoWhenAudioCannotBeDecoded(
            inputVideo, outputVideo, inputHasAudioTrack = true,
            originalAudioDecoded = true, requestedTrackCount = 1
        ))
    }

    private fun env(
        posMs: Long,
        playMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Float {
        val posFrames = (posMs * 44100) / 1000
        val playFrames = (playMs * 44100) / 1000
        return MusicExportMixer.fadeEnvelope(posFrames, playFrames, fadeInMs, fadeOutMs)
    }

    @Test
    fun `no fades means full volume everywhere`() {
        assertEquals(1f, env(0, 10_000, 0, 0))
        assertEquals(1f, env(5_000, 10_000, 0, 0))
        assertEquals(1f, env(9_999, 10_000, 0, 0))
    }

    @Test
    fun `fade in ramps linearly from zero`() {
        assertEquals(0f, env(0, 10_000, 2_000, 0))
        assertEquals(0.5f, env(1_000, 10_000, 2_000, 0), 0.01f)
        assertEquals(1f, env(2_000, 10_000, 2_000, 0))
        assertEquals(1f, env(5_000, 10_000, 2_000, 0))
    }

    @Test
    fun `fade out ramps linearly to zero`() {
        assertEquals(1f, env(0, 10_000, 0, 2_000))
        assertEquals(1f, env(7_999, 10_000, 0, 2_000))
        assertEquals(0.5f, env(9_000, 10_000, 0, 2_000), 0.01f)
        assertEquals(0f, env(10_000, 10_000, 0, 2_000))
    }

    @Test
    fun `fades combine by taking the minimum`() {
        // 2s fade-in + 2s fade-out on a 3s window: middle is the bottleneck.
        assertEquals(0.5f, env(1_000, 3_000, 2_000, 2_000), 0.01f)
        assertEquals(0.25f, env(500, 3_000, 2_000, 2_000), 0.01f)
    }

    @Test
    fun `extreme persisted fade durations saturate instead of overflowing`() {
        val playFrames = 10_000L * 44_100L / 1_000L
        assertEquals(
            0f,
            MusicExportMixer.fadeEnvelope(0L, playFrames, Long.MAX_VALUE, 0L)
        )
        assertEquals(
            0f,
            MusicExportMixer.fadeEnvelope(0L, playFrames, 0L, Long.MAX_VALUE),
            0.001f
        )
    }

    @Test
    fun `degenerate windows are silent`() {
        assertEquals(0f, env(0, 0, 1_000, 1_000))
        assertEquals(0f, env(0, -5, 0, 0))
    }

    @Test
    fun `mixer applies canonical mute and solo policy without counting exclusions as failures`() {
        fun track(id: String, muted: Boolean = false, solo: Boolean = false) = AudioTrack(
            id = id, name = id, uri = "file:///$id.m4a", isMuted = muted, isSolo = solo
        )
        val audible = track("audible")
        val muted = track("muted", muted = true)
        val solo = track("solo", solo = true)
        val nonSolo = track("non-solo")

        assertEquals(listOf(audible), selectEnabledMusicTracks(listOf(audible, muted)))
        assertEquals(listOf(solo), selectEnabledMusicTracks(listOf(nonSolo, solo)))

        // A muted solo silences non-solo tracks too; no track should then be mixed.
        assertEquals(emptyList<AudioTrack>(), selectEnabledMusicTracks(listOf(nonSolo, solo.copy(isMuted = true))))
    }

    @Test
    fun `multiple enabled music and voice tracks remain separate mixer sources`() {
        val music = AudioTrack(
            id = "music", name = "Music", uri = "file:///music.m4a",
            trimEndMs = 60_000L, sourceDurationMs = 60_000L
        )
        val voice = AudioTrack(
            id = "voice", name = "Voice", uri = "file:///voice.m4a",
            trimEndMs = 8_000L, sourceDurationMs = 8_000L, startTimeMs = 3_000L,
            kind = AudioTrack.Kind.SFX
        )
        val muted = music.copy(id = "muted", name = "Muted", uri = "file:///muted.m4a", isMuted = true)

        assertEquals(listOf(music, voice), selectEnabledMusicTracks(listOf(music, voice, muted)))
    }

    @Test
    fun `procedural SFX remains enabled in the existing video audio mixer`() {
        val sfx = AudioTrack(
            id = "effect",
            name = "Whoosh",
            uri = "/cache/sfx/whoosh_650.wav",
            startTimeMs = 2_500L,
            trimEndMs = 650L,
            sourceDurationMs = 650L,
            kind = AudioTrack.Kind.SFX,
            sfxEffectId = "whoosh"
        )

        assertEquals(listOf(sfx), selectEnabledMusicTracks(listOf(sfx)))
        assertEquals(emptyList<AudioTrack>(), selectEnabledMusicTracks(listOf(sfx.copy(isMuted = true))))
    }

    @Test
    fun `timeline milliseconds map to frames without multiplication overflow`() {
        assertEquals(0L, ExportAudioTimelineMapping.millisToFrames(-1L, 44_100))
        assertEquals(44_100L, ExportAudioTimelineMapping.millisToFrames(1_000L, 44_100))
        assertEquals(44L, ExportAudioTimelineMapping.millisToFrames(1L, 44_100))
        assertEquals(
            Long.MAX_VALUE,
            ExportAudioTimelineMapping.millisToFrames(Long.MAX_VALUE, 44_100)
        )
    }

    @Test
    fun `source trim seek milliseconds convert to microseconds without wrapping`() {
        assertEquals(1_250_000L, ExportAudioTimelineMapping.millisToMicros(1_250L))
        assertEquals(0L, ExportAudioTimelineMapping.millisToMicros(-1L))
        assertEquals(
            Long.MAX_VALUE,
            ExportAudioTimelineMapping.millisToMicros(Long.MAX_VALUE)
        )
        assertEquals(
            Long.MAX_VALUE,
            ExportAudioTimelineMapping.millisToMicros(Long.MAX_VALUE / 1_000L + 1L)
        )
    }

    @Test
    fun `placed span end saturates instead of making long sources inactive`() {
        assertEquals(300L, ExportAudioTimelineMapping.endFrame(100L, 200L))
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.endFrame(44_100L, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.endFrame(Long.MAX_VALUE - 10L, 11L))
        assertEquals(100L, ExportAudioTimelineMapping.endFrame(100L, -1L))
    }

    @Test
    fun `source trim mapping clamps negative start and rejects empty interval`() {
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(0L, 22_050L),
            ExportAudioTimelineMapping.sourceTrim(-500L, 500L, 44_100)
        )
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(2_000L, 0L),
            ExportAudioTimelineMapping.sourceTrim(2_000L, 1_000L, 44_100)
        )
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(0L, Long.MAX_VALUE),
            ExportAudioTimelineMapping.sourceTrim(0L, Long.MAX_VALUE, 44_100)
        )
    }

    @Test
    fun `source speed span normalizes trim and saturates microsecond overflow`() {
        assertEquals(4_000_000L, ExportAudioTimelineMapping.sourceSpanUs(1_250L, 5_250L))
        assertEquals(500_000L, ExportAudioTimelineMapping.sourceSpanUs(-500L, 500L))
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.sourceSpanUs(0L, Long.MAX_VALUE))
        assertNull(ExportAudioTimelineMapping.sourceSpanUs(2_000L, 1_000L))
        assertNull(ExportAudioTimelineMapping.sourceSpanUs(-500L, 0L))
    }

    @Test
    fun `speed change boundaries advance and stop at the clipped span end`() {
        assertEquals(10_000L, ExportAudioTimelineMapping.nextSpeedChangeTimeUs(0L, 25_000L))
        assertEquals(20_000L, ExportAudioTimelineMapping.nextSpeedChangeTimeUs(10_000L, 25_000L))
        assertEquals(25_000L, ExportAudioTimelineMapping.nextSpeedChangeTimeUs(20_000L, 25_000L))
        assertNull(ExportAudioTimelineMapping.nextSpeedChangeTimeUs(25_000L, 25_000L))
        assertNull(ExportAudioTimelineMapping.nextSpeedChangeTimeUs(30_000L, 25_000L))
    }

    @Test
    fun `speed change boundary does not overflow near maximum timestamp`() {
        assertEquals(
            Long.MAX_VALUE,
            ExportAudioTimelineMapping.nextSpeedChangeTimeUs(Long.MAX_VALUE - 1L, Long.MAX_VALUE)
        )
        assertNull(ExportAudioTimelineMapping.nextSpeedChangeTimeUs(Long.MAX_VALUE, Long.MAX_VALUE))
        assertNull(ExportAudioTimelineMapping.nextSpeedChangeTimeUs(0L, 0L))
    }
}
