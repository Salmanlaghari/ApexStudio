package com.apexstudio.app.data.export

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.Process
import androidx.media3.common.MimeTypes
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** The exact encoder selected for a high-resolution export; a null codec is never a 8K plan. */
internal data class ExportVideoEncoderPlan(
    val mimeType: String,
    val codecName: String
)

internal sealed interface EightKExportAvailability {
    data object Checking : EightKExportAvailability
    data class Supported(val plan: ExportVideoEncoderPlan) : EightKExportAvailability
    data class Unsupported(val reason: String) : EightKExportAvailability
}

internal data class EightKCapabilityRequest(
    val aspectRatio: String,
    val fps: Int,
    val bitrateBps: Int
)

internal fun eightKCapabilityRequestForSettings(
    aspectRatio: String,
    fps: Int,
    quality: String,
    bitrateMbps: Int
): EightKCapabilityRequest {
    val config = ExportEngine.ExportConfig(
        resolution = "8K",
        fps = fps,
        quality = quality,
        aspectRatio = aspectRatio,
        bitrateMbps = bitrateMbps
    )
    return EightKCapabilityRequest(aspectRatio, fps, exportTargetBitrate(config))
}

internal data class EightKEncoderCandidate(
    val mimeType: String,
    val codecName: String,
    val hardwareAccelerated: Boolean,
    val supportsSizeAndRate: Boolean,
    val bitrateMin: Int,
    val bitrateMax: Int
)

internal data class EightKDeviceGates(
    val is64BitProcess: Boolean,
    val exposesHardwareEncoderFlag: Boolean,
    val isLowRamDevice: Boolean?,
    val appMemoryClassMb: Int?,
    val totalMemoryBytes: Long?,
    val availableMemoryBytes: Long?,
    val maxTextureSize: Int?,
    val maxRenderbufferSize: Int?,
    val maxViewportWidth: Int?,
    val maxViewportHeight: Int?
)

private const val MIN_APP_MEMORY_CLASS_MB = 512
private const val MIN_TOTAL_RAM_BYTES = 8L * 1024L * 1024L * 1024L
private const val MIN_AVAILABLE_RAM_BYTES = 2L * 1024L * 1024L * 1024L
private val SUPPORTED_8K_MIMES = listOf(MimeTypes.VIDEO_H265, MediaFormat.MIMETYPE_VIDEO_AVC)

/** Pure policy used by the Android probe and deterministic tests. */
internal fun evaluateEightKCapability(
    request: EightKCapabilityRequest,
    gates: EightKDeviceGates,
    candidates: List<EightKEncoderCandidate>
): EightKExportAvailability {
    if (request.aspectRatio !in setOf("16:9", "9:16")) {
        return EightKExportAvailability.Unsupported("8K UHD is available only for 16:9 or 9:16 output; square output is not standard 8K.")
    }
    if (request.fps !in 24..120 || request.bitrateBps <= 0) {
        return EightKExportAvailability.Unsupported("Choose a valid frame rate and bitrate before checking 8K support.")
    }
    if (!gates.is64BitProcess) {
        return EightKExportAvailability.Unsupported("8K export requires ApexStudio to run as a 64-bit process.")
    }
    if (!gates.exposesHardwareEncoderFlag) {
        return EightKExportAvailability.Unsupported("This Android version cannot reliably identify hardware 8K encoders.")
    }
    if (gates.isLowRamDevice != false) {
        return EightKExportAvailability.Unsupported("8K export is unavailable on low-RAM devices or when device memory status cannot be verified.")
    }
    val appMemoryClassMb = gates.appMemoryClassMb
        ?: return EightKExportAvailability.Unsupported("App memory limits could not be verified for 8K export.")
    if (appMemoryClassMb < MIN_APP_MEMORY_CLASS_MB) {
        return EightKExportAvailability.Unsupported("8K export needs at least ${MIN_APP_MEMORY_CLASS_MB} MB of app memory on this device.")
    }
    val totalMemoryBytes = gates.totalMemoryBytes
        ?: return EightKExportAvailability.Unsupported("Device memory status could not be verified for 8K export.")
    val availableMemoryBytes = gates.availableMemoryBytes
        ?: return EightKExportAvailability.Unsupported("Device memory status could not be verified for 8K export.")
    if (totalMemoryBytes < MIN_TOTAL_RAM_BYTES) {
        return EightKExportAvailability.Unsupported("8K export requires a device with at least 8 GB of RAM.")
    }
    if (availableMemoryBytes < MIN_AVAILABLE_RAM_BYTES) {
        return EightKExportAvailability.Unsupported("Not enough free device memory for a safe 8K render. Close other apps and try again.")
    }

    val (width, height) = com.apexstudio.app.data.effect.AspectCropTransformation.targetSizeFor("8K", request.aspectRatio)
    val longEdge = maxOf(width, height)
    val hasTextureAndRenderbufferCapacity =
        (gates.maxTextureSize ?: 0) >= longEdge && (gates.maxRenderbufferSize ?: 0) >= longEdge
    val viewportFitsOutput =
        (gates.maxViewportWidth ?: 0) >= width && (gates.maxViewportHeight ?: 0) >= height
    if (!hasTextureAndRenderbufferCapacity || !viewportFitsOutput) {
        return EightKExportAvailability.Unsupported("The GPU render limits could not be verified for ${width}×${height} output.")
    }

    val selected = candidates
        .filter { candidate ->
            candidate.hardwareAccelerated &&
                candidate.supportsSizeAndRate &&
                request.bitrateBps in candidate.bitrateMin..candidate.bitrateMax &&
                candidate.mimeType in SUPPORTED_8K_MIMES
        }
        .sortedBy { candidate -> if (candidate.mimeType == MimeTypes.VIDEO_H265) 0 else 1 }
        .firstOrNull()
        ?: return EightKExportAvailability.Unsupported(
            "No hardware HEVC or H.264 encoder reports support for ${width}×${height} at ${request.fps} fps and ${request.bitrateBps / 1_000_000} Mbps."
        )
    return EightKExportAvailability.Supported(
        ExportVideoEncoderPlan(selected.mimeType, selected.codecName)
    )
}

/** Device probe for the exact render request. Run with [probeAndCache] from an IO coroutine. */
internal object Export8kCapability {
    private val cache = ConcurrentHashMap<EightKCapabilityRequest, EightKExportAvailability>()

    suspend fun probeAndCache(context: Context, request: EightKCapabilityRequest): EightKExportAvailability =
        withContext(Dispatchers.IO) {
            probe(context.applicationContext, request).also { cache[request] = it }
        }

    fun cached(request: EightKCapabilityRequest): EightKExportAvailability? = cache[request]

    /** Recheck the exact cached plan at the renderer boundary; direct engine callers cannot bypass the UI gate. */
    fun configFailure(context: Context, config: ExportEngine.ExportConfig): String? {
        if (com.apexstudio.app.domain.model.ExportResolution.canonical(config.resolution) != "8K") return null
        if (config.isPhotoExport) {
            return "8K still-photo export is not available yet; the photo renderer is memory-limited to 4K. Choose 4K or lower."
        }
        val request = eightKCapabilityRequestForSettings(
            config.aspectRatio, config.fps, config.quality, config.bitrateMbps
        )
        val availability = cache[request] ?: return "8K support has not been verified for these exact export settings. Reopen Export and wait for the capability check."
        val plan = when (availability) {
            is EightKExportAvailability.Supported -> availability.plan
            is EightKExportAvailability.Unsupported -> return availability.reason
            EightKExportAvailability.Checking -> return "Checking device support for 8K. Wait for the check to finish, then retry."
        }
        if (config.videoMimeType != plan.mimeType || config.videoEncoderName != plan.codecName) {
            return "The 8K encoder plan changed after verification. Reopen Export and retry the capability check."
        }
        return currentMemoryFailure(context)
    }

    /** Fresh memory check at export start; the UI's earlier encoder/GPU result may be stale. */
    fun currentMemoryFailure(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return "Device memory status could not be verified for 8K export."
            if (manager.isLowRamDevice) return "8K export is unavailable on low-RAM devices."
            if (manager.memoryClass < MIN_APP_MEMORY_CLASS_MB) {
                return "8K export needs at least ${MIN_APP_MEMORY_CLASS_MB} MB of app memory on this device."
            }
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            if (memory.totalMem < MIN_TOTAL_RAM_BYTES) {
                "8K export requires a device with at least 8 GB of RAM."
            } else if (memory.availMem < MIN_AVAILABLE_RAM_BYTES) {
                "Not enough free device memory for a safe 8K render. Close other apps and try again."
            } else null
        } catch (_: Exception) {
            "Device memory status could not be verified for 8K export."
        }
    }

    internal fun clearCacheForTests() = cache.clear()
    internal fun cacheForTests(request: EightKCapabilityRequest, result: EightKExportAvailability) {
        cache[request] = result
    }

    private fun probe(context: Context, request: EightKCapabilityRequest): EightKExportAvailability {
        val (width, height) = com.apexstudio.app.data.effect.AspectCropTransformation.targetSizeFor(
            "8K", request.aspectRatio
        )
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        val memoryRead = runCatching { manager?.getMemoryInfo(memory) }.isSuccess && manager != null
        val gl = probeGlesLimits()
        val gates = EightKDeviceGates(
            is64BitProcess = runCatching { Process.is64Bit() }.getOrDefault(false),
            exposesHardwareEncoderFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            isLowRamDevice = manager?.let { runCatching { it.isLowRamDevice }.getOrNull() },
            appMemoryClassMb = manager?.let { runCatching { it.memoryClass }.getOrNull() },
            totalMemoryBytes = memory.totalMem.takeIf { memoryRead },
            availableMemoryBytes = memory.availMem.takeIf { memoryRead },
            maxTextureSize = gl?.maxTextureSize,
            maxRenderbufferSize = gl?.maxRenderbufferSize,
            maxViewportWidth = gl?.maxViewportWidth,
            maxViewportHeight = gl?.maxViewportHeight
        )
        val encoders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .filter { it.isEncoder }
                    .flatMap { info ->
                        SUPPORTED_8K_MIMES.filter { mime ->
                            info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                        }.mapNotNull { mime ->
                            runCatching {
                                val codecCapabilities = info.getCapabilitiesForType(mime)
                                val video = codecCapabilities.videoCapabilities
                                if (video == null) null else EightKEncoderCandidate(
                                    mimeType = mime,
                                    codecName = info.name,
                                    hardwareAccelerated = info.isHardwareAccelerated,
                                    supportsSizeAndRate = video.areSizeAndRateSupported(width, height, request.fps.toDouble()),
                                    bitrateMin = video.bitrateRange.lower,
                                    bitrateMax = video.bitrateRange.upper
                                )
                            }.getOrNull()
                        }
                    }
            }.getOrDefault(emptyList())
        } else emptyList()
        return evaluateEightKCapability(request, gates, encoders)
    }

    private data class GlesLimits(
        val maxTextureSize: Int,
        val maxRenderbufferSize: Int,
        val maxViewportWidth: Int,
        val maxViewportHeight: Int
    )

    /** Creates a 1×1 ES2 pbuffer solely to query render limits, then releases its context/surface. */
    private fun probeGlesLimits(): GlesLimits? {
        var display: EGLDisplay? = null
        var config: EGLConfig? = null
        var eglContext: EGLContext? = null
        var surface: EGLSurface? = null
        var initialized = false
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
            initialized = true
            val configAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, configCount, 0) || configCount[0] < 1) {
                return null
            }
            config = configs[0] ?: return null
            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) return null
            val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttributes, 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return null

            val maxTexture = IntArray(1)
            val maxRenderbuffer = IntArray(1)
            val maxViewport = IntArray(2)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            GLES20.glGetIntegerv(GLES20.GL_MAX_RENDERBUFFER_SIZE, maxRenderbuffer, 0)
            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, maxViewport, 0)
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR ||
                maxTexture[0] <= 0 || maxRenderbuffer[0] <= 0 || maxViewport.any { it <= 0 }
            ) return null
            return GlesLimits(maxTexture[0], maxRenderbuffer[0], maxViewport[0], maxViewport[1])
        } catch (_: Exception) {
            return null
        } finally {
            val activeDisplay = display
            if (activeDisplay != null && activeDisplay != EGL14.EGL_NO_DISPLAY) {
                runCatching {
                    EGL14.eglMakeCurrent(
                        activeDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                }
                surface?.takeIf { it != EGL14.EGL_NO_SURFACE }?.let { eglSurface ->
                    runCatching { EGL14.eglDestroySurface(activeDisplay, eglSurface) }
                }
                eglContext?.takeIf { it != EGL14.EGL_NO_CONTEXT }?.let { context ->
                    runCatching { EGL14.eglDestroyContext(activeDisplay, context) }
                }
                if (initialized) runCatching { EGL14.eglTerminate(activeDisplay) }
            }
            runCatching { EGL14.eglReleaseThread() }
        }
    }

}

/** Pin Media3 to the exact codec validated by [Export8kCapability]; no silent encoder substitution. */
internal fun encoderSelectorForPlan(plan: ExportVideoEncoderPlan): androidx.media3.transformer.EncoderSelector =
    androidx.media3.transformer.EncoderSelector { mimeType ->
        if (!mimeType.equals(plan.mimeType, ignoreCase = true)) {
            androidx.media3.transformer.EncoderSelector.DEFAULT.selectEncoderInfos(mimeType)
        } else {
            val match = runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull {
                    it.name == plan.codecName && it.isEncoder &&
                        it.supportedTypes.any { type -> type.equals(plan.mimeType, ignoreCase = true) }
                }
            }.getOrNull()
            if (match == null) {
                throw IllegalStateException(
                    "The verified 8K encoder ${plan.codecName} is no longer available. Reopen Export and retry the capability check."
                )
            }
            ImmutableList.of(match)
        }
    }
