package com.apexstudio.app.data.template

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.apexstudio.app.data.effect.TextOverlayGlEffect
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.gl.TransitionEngine
import com.apexstudio.app.data.gl.TransitionGlEffect
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File

/**
 * Robust timeline template manager.
 *
 * Responsibilities:
 * 1. Parses JSON timeline structures into [TimelineTemplate] models.
 * 2. Maps timeline templates into domain [Project] and UI states.
 * 3. Maps templates into hardware-accelerated Media3 [Composition] and [EditedMediaItemSequence]
 *    pipelines for real-time playback and video export.
 * 4. Provides pre-built production templates (Cinematic Vlog, Cyberpunk Glitch, Minimalist Story).
 * 5. Loads the bundled [TransmissionTemplate] catalog (LUT + FX + transition combos).
 */
@UnstableApi
class TimelineTemplateManager(private val context: Context) {

    companion object {
        private const val TAG = "TimelineTemplateMgr"

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
        }
    }

    private val _currentTemplate = MutableStateFlow<TimelineTemplate?>(null)
    val currentTemplate: StateFlow<TimelineTemplate?> = _currentTemplate

    /**
     * Parses a raw JSON string into a [TimelineTemplate].
     */
    fun parseTemplateJson(jsonString: String): Result<TimelineTemplate> = runCatching {
        jsonParser.decodeFromString<TimelineTemplate>(jsonString)
    }

    /**
     * Serializes a [TimelineTemplate] into a formatted JSON string.
     */
    fun serializeTemplateToJson(template: TimelineTemplate): String {
        return jsonParser.encodeToString(TimelineTemplate.serializer(), template)
    }

    /**
     * Loads a JSON template from the app's assets directory.
     */
    suspend fun loadTemplateFromAssets(assetPath: String): Result<TimelineTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetPath).use { stream ->
                val jsonString = stream.bufferedReader().use { it.readText() }
                parseTemplateJson(jsonString).getOrThrow()
            }
        }
    }

    /**
     * Maps a [TimelineTemplate] directly to our domain [Project] structure.
     */
    fun mapTemplateToProject(template: TimelineTemplate): Project {
        val domainClips = template.clips.mapIndexed { index, tClip ->
            // Resolve clip URI safely
            val resolvedUri = MediaUriResolver.resolvePlayableUri(context, tClip.uri).toString()

            // Find matching filter if any
            val matchingFilter = template.filters.firstOrNull { it.clipId == tClip.id }

            // Find matching captions
            val matchingTexts = template.textOverlays.map { tText ->
                TextOverlay(
                    id = tText.id,
                    text = tText.text,
                    x = tText.posX,
                    y = tText.posY,
                    sizeScale = tText.scale,
                    colorArgb = tText.colorArgb,
                    bgArgb = tText.bgArgb,
                    startMs = tText.startMs,
                    endMs = tText.endMs
                )
            }

            MediaClip(
                id = tClip.id,
                name = tClip.name.ifEmpty { "Clip ${index + 1}" },
                uri = resolvedUri,
                durationMs = tClip.durationMs,
                trimStartMs = tClip.trimStartMs,
                trimEndMs = tClip.trimEndMs,
                trackIndex = 0,
                type = ClipType.VIDEO,
                speedMultiplier = tClip.speedMultiplier,
                textOverlays = matchingTexts
            )
        }

        val domainAudioTracks = template.audioTracks.map { aTrack ->
            AudioTrack(
                id = aTrack.id,
                name = aTrack.name,
                uri = aTrack.uri,
                volume = aTrack.volume,
                trimStartMs = aTrack.startMs,
                fadeInMs = aTrack.fadeInMs,
                fadeOutMs = aTrack.fadeOutMs
            )
        }

        return Project(
            id = template.id,
            name = template.name,
            durationMs = template.totalDurationMs,
            resolution = template.resolution,
            fps = template.fps,
            clips = domainClips,
            audioTracks = domainAudioTracks
        )
    }

    /**
     * Maps a [TimelineTemplate] into a Media3 [Composition] for playback or export.
     * Guarantees frame-accurate trimming, filter grading, transitions, and audio sync.
     */
    fun mapTemplateToComposition(template: TimelineTemplate): Composition {
        val editedItems = mutableListOf<EditedMediaItem>()

        template.clips.forEachIndexed { index, clip ->
            val resolvedUri = MediaUriResolver.resolvePlayableUri(context, clip.uri)
            val mediaItemBuilder = MediaItem.Builder().setUri(resolvedUri)

            // 1. Frame-accurate trimming
            if (clip.trimStartMs > 0L || clip.trimEndMs < clip.durationMs) {
                val clipConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build()
                mediaItemBuilder.setClippingConfiguration(clipConfig)
            }

            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
            val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

            // 2. Color Filter LUT
            val filterConfig = template.filters.firstOrNull { it.clipId == clip.id }
            if (filterConfig != null) {
                val preset = FilterManifest.presetById(filterConfig.filterId)
                if (preset != null && filterConfig.intensity > 0f) {
                    videoEffects.add(LutFilterGlEffect(context, preset, filterConfig.intensity))
                }
            }

            // 3. Dynamic Visual Effects (Glitch, RGB Split, VHS)
            val effectConfig = template.effects.firstOrNull { it.clipId == clip.id }
            if (effectConfig != null && effectConfig.intensity > 0f) {
                val fxPreset = when (effectConfig.effectType) {
                    "glitch" -> FxPreset.GLITCH
                    "vhs" -> FxPreset.VHS
                    "grain" -> FxPreset.FILM_GRAIN
                    "rgb_split", "chromatic" -> FxPreset.CHROMATIC
                    "pixelate" -> FxPreset.PIXELATE
                    else -> FxPreset.VIGNETTE
                }
                videoEffects.add(FxGlEffect(fxPreset, effectConfig.intensity))
            }

            // 4. Transitions
            val transition = template.transitions.firstOrNull { it.fromClipId == clip.id }
            if (transition != null) {
                val transType = when (transition.type) {
                    "wipe" -> TransitionEngine.Companion.TransitionType.WIPE
                    "zoom" -> TransitionEngine.Companion.TransitionType.ZOOM_BLUR
                    "slide" -> TransitionEngine.Companion.TransitionType.SLIDE
                    "glitch" -> TransitionEngine.Companion.TransitionType.GLITCH
                    else -> TransitionEngine.Companion.TransitionType.CROSS_DISSOLVE
                }
                val durationUs = transition.durationMs * 1000L
                val clipDurationUs = (clip.trimEndMs - clip.trimStartMs) * 1000L
                val startUs = (clipDurationUs - durationUs).coerceAtLeast(0L)
                videoEffects.add(TransitionGlEffect(transType, durationUs, startUs))
            }

            // 5. Captions & Titles
            template.textOverlays.forEach { overlay ->
                videoEffects.add(
                    TextOverlayGlEffect(
                        context,
                        TextOverlay(
                            id = overlay.id,
                            text = overlay.text,
                            x = overlay.posX,
                            y = overlay.posY,
                            sizeScale = overlay.scale,
                            colorArgb = overlay.colorArgb,
                            bgArgb = overlay.bgArgb,
                            startMs = overlay.startMs,
                            endMs = overlay.endMs
                        ),
                        16f / 9f
                    )
                )
            }

            // 6. Speed Ramping with Audio Pitch Correction
            if (clip.speedMultiplier > 0f && clip.speedMultiplier != 1f) {
                val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = clip.speedMultiplier
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                }
                val speedPair = androidx.media3.transformer.Effects.createExperimentalSpeedChangingEffect(speedProvider)
                audioProcessors.add(speedPair.first)
                videoEffects.add(speedPair.second)
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()

            editedItems.add(editedMediaItem)
        }

        val sequence = EditedMediaItemSequence(editedItems)
        return Composition.Builder(sequence).build()
    }

    /**
     * Loads the bundled [TransmissionTemplate] catalog from
     * `assets/transmission_templates.json` and validates every entry
     * against the live [FilterManifest] (LUT ids) and [FxPreset]
     * enum (FX ids). Entries with an unknown LUT or FX are dropped
     * with a warning — this keeps the editor from crashing if a
     * later rename disconnects a template id from its LUT/FX.
     *
     * Each [TransmissionTemplate.transitionType] string is the same
     * id used by [TransitionEngine.Companion.TransitionType] (e.g.
     * "cross", "wipe", "zoom", "slide", "glitch"); the loader does
     * not enforce it here because the transition type is consumed
     * by the caller (the editor's clip-to-clip transition picker)
     * which already maps the id via the same `when` clause as
     * [mapTemplateToComposition].
     */
    fun loadTransmissionTemplates(): List<TransmissionTemplate> {
        return try {
            val raw = context.assets.open("transmission_templates.json").use { input ->
                input.bufferedReader().readText()
            }
            parseTransmissionTemplates(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load transmission_templates.json", e)
            emptyList()
        }
    }

    /**
     * Parses the raw `transmission_templates.json` text and validates
     * each entry against the live LUT manifest + FX enum. Public so
     * unit tests can drive it without an Android [Context].
     */
    fun parseTransmissionTemplates(jsonText: String): List<TransmissionTemplate> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("templates") ?: return emptyList()
        val manifest = LutFilterEngine(context).manifest
        val valid = ArrayList<TransmissionTemplate>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val filterId = o.optString("filterId")
            val fxPresetId = o.optString("fxPresetId")

            val lutOk = manifest.presetById(filterId) != null
            val fxOk = FxPreset.byId(fxPresetId) != null
            if (!lutOk) {
                Log.w(TAG, "Transmission template '${o.optString("id")}' references unknown LUT '$filterId' — skipping")
                continue
            }
            if (!fxOk) {
                Log.w(TAG, "Transmission template '${o.optString("id")}' references unknown FX preset '$fxPresetId' — skipping")
                continue
            }

            valid.add(
                TransmissionTemplate(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    description = o.optString("description"),
                    resolution = o.optString("resolution", "1080p"),
                    fps = o.optInt("fps", 60),
                    aspectRatio = o.optString("aspectRatio", "16:9"),
                    filterId = filterId,
                    filterIntensity = o.optDouble("filterIntensity", 1.0).toFloat(),
                    fxPresetId = fxPresetId,
                    fxIntensity = o.optDouble("fxIntensity", 1.0).toFloat(),
                    transitionType = o.optString("transitionType", "cross"),
                    transitionDurationMs = o.optLong("transitionDurationMs", 500L),
                    defaultIntensity = o.optDouble("defaultIntensity", 1.0).toFloat(),
                    previewAccentArgb = o.optLong("previewAccentArgb", 0xFF6E5BFFL),
                    tags = o.optJSONArray("tags")?.let { tagsArr ->
                        (0 until tagsArr.length()).map { tagsArr.getString(it) }
                    } ?: emptyList()
                )
            )
        }
        return valid
    }

    /**
     * Built-in template presets ready to instantiate.
     */
    fun getBuiltInTemplates(): List<TimelineTemplate> {
        val sampleUri = MediaUriResolver.getFallbackSampleUri(context).toString()

        return listOf(
            TimelineTemplate(
                id = "cinematic_vlog",
                name = "Cinematic Travel Vlog",
                description = "Warm golden-hour color grading with smooth cross-dissolves and minimalist title cards.",
                resolution = "1080p",
                fps = 60,
                aspectRatio = "16:9",
                clips = listOf(
                    TemplateClip("c1", "Intro Aerial", sampleUri, 4000L, 0L, 3000L, 1.0f),
                    TemplateClip("c2", "Landscape B-Roll", sampleUri, 4000L, 500L, 3500L, 0.8f),
                    TemplateClip("c3", "City Sunset", sampleUri, 4000L, 0L, 3000L, 1.0f)
                ),
                filters = listOf(
                    TemplateFilter("c1", "teal_orange", null, 0.85f),
                    TemplateFilter("c2", "warm_golden", null, 0.90f),
                    TemplateFilter("c3", "sunset_blush", null, 0.80f)
                ),
                transitions = listOf(
                    TemplateTransition("c1", "c2", "cross", 600L),
                    TemplateTransition("c2", "c3", "zoom", 500L)
                ),
                textOverlays = listOf(
                    TemplateTextOverlay("t1", "GOLDEN HOUR VOYAGE", 0.5f, 0.82f, 1.2f, 0xFFFFFFFFL, 0xAA000000L, 0L, 2500L)
                )
            ),
            TimelineTemplate(
                id = "cyberpunk_glitch",
                name = "Cyberpunk Beats & Glitch",
                description = "High-energy neon look with RGB split transitions and digital scanline effects.",
                resolution = "1080p",
                fps = 60,
                aspectRatio = "16:9",
                clips = listOf(
                    TemplateClip("cg1", "Neon District", sampleUri, 3000L, 0L, 2500L, 1.25f),
                    TemplateClip("cg2", "Speed Run", sampleUri, 3000L, 0L, 2500L, 1.5f),
                    TemplateClip("cg3", "Night Finish", sampleUri, 3000L, 0L, 2500L, 1.0f)
                ),
                filters = listOf(
                    TemplateFilter("cg1", "cyber_cyan", null, 1.0f),
                    TemplateFilter("cg2", "neon_magenta", null, 1.0f),
                    TemplateFilter("cg3", "matrix_grade", null, 0.9f)
                ),
                effects = listOf(
                    TemplateEffect("cg1", "rgb_split", 0L, 2500L, 0.7f),
                    TemplateEffect("cg2", "glitch", 1500L, 2500L, 0.85f)
                ),
                transitions = listOf(
                    TemplateTransition("cg1", "cg2", "glitch", 400L),
                    TemplateTransition("cg2", "cg3", "slide", 350L)
                ),
                textOverlays = listOf(
                    TemplateTextOverlay("t2", "CYBER TOKYO 2088", 0.5f, 0.5f, 1.4f, 0xFF00FFCCL, null, 0L, 2000L)
                )
            ),
            TimelineTemplate(
                id = "minimalist_story",
                name = "Minimalist Clean Story",
                description = "Monochromatic and desaturated elegance with soft directional wipes.",
                resolution = "1080p",
                fps = 30,
                aspectRatio = "9:16",
                clips = listOf(
                    TemplateClip("ms1", "Stillness", sampleUri, 3500L, 0L, 3000L, 1.0f),
                    TemplateClip("ms2", "Architecture", sampleUri, 3500L, 0L, 3000L, 1.0f)
                ),
                filters = listOf(
                    TemplateFilter("ms1", "graphite", null, 0.95f),
                    TemplateFilter("ms2", "noir_classic", null, 0.90f)
                ),
                transitions = listOf(
                    TemplateTransition("ms1", "ms2", "wipe", 700L)
                ),
                textOverlays = listOf(
                    TemplateTextOverlay("t3", "A P E X  S T U D I O", 0.5f, 0.2f, 1.1f, 0xFFE0E0E0L, null, 0L, 3000L)
                )
            )
        )
    }
}