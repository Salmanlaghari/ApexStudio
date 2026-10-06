package com.apexstudio.app.camerakit

import android.content.Context
import android.view.ViewStub
import androidx.lifecycle.LifecycleOwner
import com.snap.camerakit.Session
import com.snap.camerakit.invoke
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource

/**
 * Owns the lifecycle of a Snap Camera Kit [Session] wired to a CameraX preview source.
 *
 * Usage: call [start] once the CAMERA permission is granted, then [close] when the UI
 * goes away. [Session.close] is essential — it releases all resources Camera Kit acquired.
 */
class CameraKitSessionManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private var imageProcessorSource: CameraXImageProcessorSource? = null
    private var cameraKitSession: Session? = null

    /** The live session, or null before [start] / after [close]. */
    val session: Session?
        get() = cameraKitSession

    var frontCamera: Boolean = true
        private set

    /**
     * Builds the CameraX source, creates the Camera Kit session with the API token and
     * attaches it to [viewStub] (Camera Kit inflates its preview UI into the stub).
     * Must be called after the camera permission is granted.
     */
    fun start(viewStub: ViewStub, lifecycleOwner: LifecycleOwner, frontCamera: Boolean = true) {
        if (cameraKitSession != null) return
        this.frontCamera = frontCamera
        val source = CameraXImageProcessorSource(
            context = appContext,
            lifecycleOwner = lifecycleOwner
        )
        imageProcessorSource = source
        cameraKitSession = Session(appContext) {
            apiToken(CameraKitConfig.apiToken)
            imageProcessorSource(source)
            attachTo(viewStub)
        }
        source.startPreview(frontCamera)
    }

    /** Toggles between the front and back camera while the preview is running. */
    fun flipCamera() {
        frontCamera = !frontCamera
        // Stop the current preview before restarting it on the other camera so
        // CameraX doesn't hold two camera sessions (resource leak).
        runCatching { imageProcessorSource?.stopPreview() }
        imageProcessorSource?.startPreview(frontCamera)
    }

    /** Stops the preview and disposes the session. Safe to call more than once. */
    fun close() {
        runCatching { imageProcessorSource?.stopPreview() }
        runCatching { cameraKitSession?.close() }
        imageProcessorSource = null
        cameraKitSession = null
    }
}
