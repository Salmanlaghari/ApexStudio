package com.apexstudio.app.camerakit

import android.content.Context
import android.util.Log
import android.view.ViewStub
import androidx.lifecycle.LifecycleOwner
import com.snap.camerakit.Session
import com.snap.camerakit.invoke
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the lifecycle of a Snap Camera Kit [Session] wired to a CameraX preview source.
 *
 * Usage: call [start] once the CAMERA permission is granted, then [close] when the UI
 * goes away. [Session.close] is essential — it releases all resources Camera Kit acquired.
 */
class CameraKitSessionManager(context: Context) {
    private companion object {
        const val TAG = "CameraKitSessionManager"
    }

    private val appContext: Context = context.applicationContext
    private var imageProcessorSource: CameraXImageProcessorSource? = null
    private var cameraKitSession: Session? = null

    /** Last session-start failure, if any. Null when the session started cleanly. */
    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError.asStateFlow()

    /** The live session, or null before [start] / after [close]. */
    val session: Session?
        get() = cameraKitSession

    var frontCamera: Boolean = true
        private set

    /**
     * Builds the CameraX source, creates the Camera Kit session with the API token and
     * attaches it to [viewStub] (Camera Kit inflates its preview UI into the stub).
     * Must be called after the camera permission is granted.
     *
     * @return true if the session started; false if session creation or preview start
     * threw — the failure is also published on [startError] instead of crashing the app.
     */
    fun start(viewStub: ViewStub, lifecycleOwner: LifecycleOwner, frontCamera: Boolean = true): Boolean {
        if (cameraKitSession != null) return true
        this.frontCamera = frontCamera
        return try {
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
            _startError.value = null
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start Camera Kit session", t)
            // Tear down the half-built state so a later retry starts clean.
            runCatching { imageProcessorSource?.stopPreview() }
            imageProcessorSource = null
            cameraKitSession = null
            _startError.value = "Could not start camera: ${t.message}"
            false
        }
    }

    /** Toggles between the front and back camera while the preview is running. */
    fun flipCamera() {
        frontCamera = !frontCamera
        // Stop the current preview before restarting it on the other camera so
        // CameraX doesn't hold two camera sessions (resource leak).
        runCatching { imageProcessorSource?.stopPreview() }
        imageProcessorSource?.startPreview(frontCamera)
    }

    /** Clears the last session-start error (e.g. after the user dismisses the banner). */
    fun dismissStartError() {
        _startError.value = null
    }

    /** Stops the preview and disposes the session. Safe to call more than once. */
    fun close() {        runCatching { imageProcessorSource?.stopPreview() }
        runCatching { cameraKitSession?.close() }
        imageProcessorSource = null
        cameraKitSession = null
    }
}
