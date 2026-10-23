package com.apexstudio.app.ui.screens.editor

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.effect.PreviewingSingleInputVideoGraph
import com.apexstudio.app.data.audio.DenoiseAudioProcessor

/**
 * Editor-only renderers: keep the shared denoise audio chain while replacing Media3's
 * default video sink (which throws UnsupportedOperationException from setVideoEffects)
 * with the effect-capable playback graph. Media3 1.5.0 does not retrofit that graph
 * when ExoPlayer.setVideoEffects is called; it must be installed when the renderer is built.
 */
@OptIn(UnstableApi::class)
internal class EffectPreviewRenderersFactory(
    context: Context,
    private val processorProvider: () -> DenoiseAudioProcessor?,
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        // PlaybackVideoGraphWrapper needs the renderer's timing policy, while the renderer
        // needs the graph as its sink. The delegating evaluator resolves this construction
        // cycle before the renderer is enabled (the first time timing is queried).
        val evaluator = RendererTimingEvaluator()
        val frameReleaseControl = VideoFrameReleaseControl(
            context,
            evaluator,
            allowedVideoJoiningTimeMs,
        )
        val videoGraph = PlaybackVideoGraphWrapper.Builder(context, frameReleaseControl)
            .setPreviewingVideoGraphFactory(PreviewingSingleInputVideoGraph.Factory())
            .build()
        val renderer = MediaCodecVideoRenderer(
            context,
            DefaultMediaCodecAdapterFactory(context),
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
            30f,
            videoGraph,
        )
        evaluator.renderer = renderer
        // Use the platform MediaCodec decoder and the graph-backed renderer only. The
        // app does not enable video extension renderers; preserving that default is
        // preferable to adding a second renderer with a non-effect-capable sink.
        out.add(renderer)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        val processor = processorProvider()
            ?: return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(processor))
            .build()
    }

    private class RendererTimingEvaluator : VideoFrameReleaseControl.FrameTimingEvaluator {
        lateinit var renderer: MediaCodecVideoRenderer

        override fun shouldForceReleaseFrame(earlyUs: Long, elapsedSinceLastReleaseUs: Long): Boolean =
            renderer.shouldForceReleaseFrame(earlyUs, elapsedSinceLastReleaseUs)

        override fun shouldDropFrame(earlyUs: Long, elapsedSinceLastReleaseUs: Long, isLastBuffer: Boolean): Boolean =
            renderer.shouldDropFrame(earlyUs, elapsedSinceLastReleaseUs, isLastBuffer)

        override fun shouldIgnoreFrame(
            earlyUs: Long,
            elapsedSinceLastReleaseUs: Long,
            elapsedRealtimeUs: Long,
            isLastBuffer: Boolean,
            shouldBeDropped: Boolean,
        ): Boolean = renderer.shouldIgnoreFrame(
            earlyUs,
            elapsedSinceLastReleaseUs,
            elapsedRealtimeUs,
            isLastBuffer,
            shouldBeDropped,
        )
    }
}
