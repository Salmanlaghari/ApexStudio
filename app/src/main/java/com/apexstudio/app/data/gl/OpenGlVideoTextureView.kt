package com.apexstudio.app.data.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import androidx.media3.exoplayer.ExoPlayer

/**
 * Custom hardware-accelerated TextureView backed by [OpenGlLutRenderer].
 *
 * Implements [TextureView.SurfaceTextureListener] with lifecycle management:
 * - Creates OpenGL ES 3.0 context on surface available.
 * - Bridges ExoPlayer decoding surface seamlessly with zero-copy OES texture streaming.
 * - Releases resources on surface destroyed, completely preventing memory leaks, black screens, or frozen frames.
 */
class OpenGlVideoTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var renderer: OpenGlLutRenderer? = null
    private var attachedPlayer: ExoPlayer? = null
    private var activeSurface: Surface? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun attachPlayer(player: ExoPlayer?) {
        attachedPlayer = player
    }

    fun setLutAsset(assetPath: String?, intensity: Float = 1f) {
        renderer?.setLutAsset(assetPath, intensity)
    }

    fun setFilterIntensity(intensity: Float) {
        renderer?.setFilterIntensity(intensity)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(surfaceTexture)
        activeSurface = surface

        renderer = OpenGlLutRenderer(context).apply {
            onVideoSurfaceReadyListener = { videoInputSurface ->
                post {
                    attachedPlayer?.setVideoSurface(videoInputSurface)
                }
            }
            onSurfaceCreated(surface, width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderer?.onSurfaceChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        attachedPlayer?.setVideoSurface(null)
        renderer?.onSurfaceDestroyed()
        renderer?.release()
        renderer = null
        activeSurface?.release()
        activeSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Handled internally by OpenGlLutRenderer via onFrameAvailable
    }

    fun release() {
        attachedPlayer?.setVideoSurface(null)
        attachedPlayer = null
        renderer?.release()
        renderer = null
        activeSurface?.release()
        activeSurface = null
    }
}
