package com.apexstudio.app.ai.mediapipe

import android.content.Context
import com.apexstudio.app.ai.models.ModelAssetAccess
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Access to the exact, checksum-pinned MediaPipe models shipped in `assets/models/mediapipe`.
 * Materialization is local-only: no network client or downloader exists in this class.
 */
object BundledMediaPipeModels {
    enum class Model(val assetName: String, val sha256: String) {
        FACE("mediapipe-face-landmarker.task", "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"),
        POSE("mediapipe-pose-landmarker.task", "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"),
        HAND("mediapipe-hand-landmarker.task", "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"),
        SELFIE_SEGMENTER("selfie_segmenter.tflite", "191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b"),
    }

    private const val ASSET_DIRECTORY = "models/mediapipe"

    /**
     * Return a verified app-private file for a bundled model. An existing file is reused only if its
     * SHA-256 still matches; corruption triggers a fresh verified copy from the packaged asset.
     * Copy uses a temporary sibling followed by rename, so readers never observe a partial file.
     * Cancellation polls around chunk reads and publication, not during blocking IO or lock waits.
     */
    @Synchronized
    fun modelFile(context: Context, model: Model, checkCancelled: () -> Unit = {}): File {
        checkCancelled()
        val app = context.applicationContext
        ModelAssetAccess.installedFile(app, "$ASSET_DIRECTORY/${model.assetName}")?.let { installed ->
            check(sha256(installed, checkCancelled) == model.sha256) { "Downloaded MediaPipe model failed SHA-256 verification" }
            return installed
        }
        val directory = File(app.noBackupFilesDir, "mediapipe/models").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Cannot create MediaPipe model directory")
        }
        val destination = File(directory, model.assetName)
        if (destination.isFile && sha256(destination, checkCancelled) == model.sha256) {
            checkCancelled()
            return destination
        }

        val temporary = File(directory, "${model.assetName}.tmp")
        try {
            checkCancelled()
            ModelAssetAccess.open(app, "$ASSET_DIRECTORY/${model.assetName}").use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        checkCancelled()
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(sha256(temporary, checkCancelled) == model.sha256) {
                "Bundled MediaPipe asset failed SHA-256 verification: ${model.assetName}"
            }
            checkCancelled()
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Cannot replace invalid MediaPipe model: ${model.assetName}")
            }
            if (!temporary.renameTo(destination)) {
                // Some file systems may not support rename replacement; preserve atomicity by failing.
                throw IllegalStateException("Cannot atomically install MediaPipe model: ${model.assetName}")
            }
            checkCancelled()
            return destination
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** Explicit alias for callers that prefer a verb. */
    fun materialize(context: Context, model: Model, checkCancelled: () -> Unit = {}): File =
        modelFile(context, model, checkCancelled)

    fun openFace(context: Context, maxFaces: Int = 3, checkCancelled: () -> Unit = {}): MediaPipeLandmarkers.FaceDetector {
        val file = modelFile(context, Model.FACE, checkCancelled)
        checkCancelled()
        return MediaPipeLandmarkers.openFace(context, file, maxFaces)
    }

    fun openPose(context: Context, maxPoses: Int = 1): MediaPipeLandmarkers.PoseDetector =
        MediaPipeLandmarkers.openPose(context, modelFile(context, Model.POSE), maxPoses)

    fun openHands(context: Context, maxHands: Int = 2): MediaPipeLandmarkers.HandDetector =
        MediaPipeLandmarkers.openHands(context, modelFile(context, Model.HAND), maxHands)

    fun openPortraitEffects(context: Context): MediaPipePortraitEffects =
        MediaPipePortraitEffects.open(context, modelFile(context, Model.SELFIE_SEGMENTER))

    private fun sha256(file: File, checkCancelled: () -> Unit): String {
        checkCancelled()
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                checkCancelled()
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
