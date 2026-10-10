package com.apexstudio.app.data.packs

import android.content.Context
import android.util.Log
import com.apexstudio.app.data.filter.GpuColorProfile
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase 2: Content Pack loader.
 *
 * Loads the 10 license-clean content packs shipped under `assets/packs/`
 * (generated originally for ApexStudio Pro — see `design/pro/packs/`).
 * All packs are parsed leniently (ignoreUnknownKeys) so a malformed pack
 * never crashes the app — it is simply skipped.
 *
 * Packs:
 * - filters (52 parametric) -> [PackFilter] -> converted to [GpuColorProfile]
 * - transitions (30)         -> [PackTransition]
 * - effects (25)             -> [PackEffect]
 * - edit-presets (20)        -> [PackPreset] (generic id/name/params)
 * - adjust-presets (30)      -> [PackPreset]
 * - image-tools              -> [ImageToolsPack]
 * - erase                    -> [PackPreset]
 * - color-scopes             -> [ColorScopesPack]
 * - 3d-luts (30 .cube)       -> auto-registered via FilterManifest LUT scan
 * - bg-remover               -> [BgRemoverPack]
 */
object PackLoader {

    private const val TAG = "PackLoader"
    private const val PACKS_DIR = "packs"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------- Filter pack ----------

    @Serializable
    data class PackFilterParams(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val warmth: Float = 0f,
        val tint: Float = 0f,
        val vignette: Float = 0f,
        val grain: Float = 0f,
        val fade: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f
    )

    @Serializable
    data class PackFilter(
        val id: String,
        val name: String,
        val category: String = "General",
        val params: PackFilterParams = PackFilterParams()
    )

    @Serializable
    private data class FilterPackFile(
        val filters: List<PackFilter> = emptyList()
    )

    // ---------- Transition pack ----------

    @Serializable
    data class PackTransition(
        val id: String,
        val name: String,
        val category: String = "Basic",
        val glsl: String = "fade",
        val duration: Float = 0.6f
    )

    @Serializable
    private data class TransitionPackFile(
        val transitions: List<PackTransition> = emptyList()
    )

    // ---------- Effects pack ----------

    @Serializable
    data class PackEffect(
        val id: String,
        val name: String,
        val category: String = "General",
        val type: String = "shader",
        val glsl_file: String = ""
    )

    @Serializable
    private data class EffectsPackFile(
        val effects: List<PackEffect> = emptyList()
    )

    // ---------- Generic preset packs (edit / adjust / erase) ----------

    @Serializable
    data class PackPreset(
        val id: String,
        val name: String,
        val category: String = "General",
        val params: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class PresetPackFile(
        val presets: List<PackPreset> = emptyList()
    )

    // ---------- Edit presets (have type/description/keyframes at top level) ----------

    @Serializable
    data class EditPreset(
        val id: String,
        val name: String,
        val type: String = "speed_ramp",
        val description: String = "",
        val category: String = "General"
    )

    @Serializable
    private data class EditPresetPackFile(
        val presets: List<EditPreset> = emptyList()
    )

    // ---------- Image tools ----------

    @Serializable
    data class ImageToolsPack(
        val crop_ratios: List<String> = emptyList(),
        val rotate_flip: List<String> = emptyList(),
        val heal_presets: List<String> = emptyList(),
        val liquify_presets: List<String> = emptyList(),
        val perspective_presets: List<String> = emptyList(),
        val denoise_levels: List<String> = emptyList(),
        val sharpen_presets: List<String> = emptyList()
    )

    // ---------- Color scopes ----------

    @Serializable
    data class ColorScopesPack(
        val scopes: List<String> = emptyList()
    )

    // ---------- BG remover ----------

    @Serializable
    data class BgOption(
        val id: String,
        val name: String,
        val type: String = "solid",
        val color: String = "#000000",
        val radius: Int = 15
    )

    @Serializable
    data class EdgeRefinement(
        val id: String,
        val name: String,
        val feather: Int = 0,
        val smooth: Int = 0
    )

    @Serializable
    data class BgRemoverPack(
        val engine: String = "",
        val backgrounds: List<BgOption> = emptyList(),
        val edge_refinement: List<EdgeRefinement> = emptyList()
    )

    // ---------- Loading ----------

    private fun readAsset(context: Context, path: String): String? = try {
        context.assets.open("$PACKS_DIR/$path").use { it.bufferedReader().readText() }
    } catch (e: Exception) {
        Log.w(TAG, "Pack file missing: $path")
        null
    }

    /** All 52 parametric filters from the Filter Pack. */
    fun loadFilters(context: Context): List<PackFilter> = try {
        val text = readAsset(context, "filters/filters.json") ?: return emptyList()
        json.decodeFromString<FilterPackFile>(text).filters
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse filters pack", e)
        emptyList()
    }

    /** All 30 transitions from the Transition Pack. */
    fun loadTransitions(context: Context): List<PackTransition> = try {
        val text = readAsset(context, "transitions/transitions.json") ?: return emptyList()
        json.decodeFromString<TransitionPackFile>(text).transitions
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse transitions pack", e)
        emptyList()
    }

    /** All 25 effects from the Effects Pack. */
    fun loadEffects(context: Context): List<PackEffect> = try {
        val text = readAsset(context, "effects/effects.json") ?: return emptyList()
        json.decodeFromString<EffectsPackFile>(text).effects
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse effects pack", e)
        emptyList()
    }

    /** Generic preset loader for edit-presets / adjust-presets. */
    fun loadPresets(context: Context, packPath: String): List<PackPreset> = try {
        val text = readAsset(context, packPath) ?: return emptyList()
        json.decodeFromString<PresetPackFile>(text).presets
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse preset pack: $packPath", e)
        emptyList()
    }

    /** Edit presets (20) — type/description live at the preset level. */
    fun loadEditPresets(context: Context): List<EditPreset> = try {
        val text = readAsset(context, "edit-presets/presets.json") ?: return emptyList()
        json.decodeFromString<EditPresetPackFile>(text).presets
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse edit-presets pack", e)
        emptyList()
    }

    // ---------- Adjust presets (numeric adjustment fields) ----------

    @Serializable
    data class AdjustPreset(
        val id: String,
        val name: String,
        val category: String = "General",
        val brightness: Float = 0f,
        val contrast: Float = 0f,
        val saturation: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f,
        val warmth: Float = 0f,
        val tint: Float = 0f
    )

    @Serializable
    private data class AdjustPresetPackFile(
        val presets: List<AdjustPreset> = emptyList()
    )

    /** Adjust presets (30) — numeric fields map to [VideoAdjustments]. */
    fun loadAdjustPresets(context: Context): List<AdjustPreset> = try {
        val text = readAsset(context, "adjust-presets/presets.json") ?: return emptyList()
        json.decodeFromString<AdjustPresetPackFile>(text).presets
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse adjust-presets pack", e)
        emptyList()
    }

    /** Color recovery presets (15) — same numeric schema as adjust presets. */
    fun loadRecoveryPresets(context: Context): List<AdjustPreset> = try {
        val text = readAsset(context, "color-scopes/recovery-presets.json") ?: return emptyList()
        json.decodeFromString<AdjustPresetPackFile>(text).presets
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse recovery presets", e)
        emptyList()
    }

    @Serializable
    data class ScopeDef(
        val id: String,
        val name: String,
        val description: String = ""
    )

    @Serializable
    private data class ScopesFile(val scopes: List<ScopeDef> = emptyList())

    /** Color scope definitions (waveform, vectorscope, ...). */
    fun loadScopeDefs(context: Context): List<ScopeDef> = try {
        val text = readAsset(context, "color-scopes/scopes.json") ?: return emptyList()
        json.decodeFromString<ScopesFile>(text).scopes
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse scopes", e)
        emptyList()
    }

    /** Erase pack (configs.json variant with "configs" key). */
    fun loadEraseConfigs(context: Context): List<PackPreset> = try {
        val text = readAsset(context, "erase/configs.json") ?: return emptyList()
        val root = json.parseToJsonElement(text).jsonObject
        val arr = root["configs"] ?: root["presets"]
            ?: return emptyList()
        arr.jsonArray.mapNotNull { el ->
            try {
                val obj = el.jsonObject
                PackPreset(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    category = obj["category"]?.jsonPrimitive?.content ?: "General"
                )
            } catch (_: Exception) { null }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse erase pack", e)
        emptyList()
    }

    fun loadImageTools(context: Context): ImageToolsPack = try {
        val text = readAsset(context, "image-tools/tools.json") ?: return ImageToolsPack()
        json.decodeFromString<ImageToolsPack>(text)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse image-tools pack", e)
        ImageToolsPack()
    }

    fun loadColorScopes(context: Context): ColorScopesPack = try {
        val text = readAsset(context, "color-scopes/scopes.json") ?: return ColorScopesPack()
        json.decodeFromString<ColorScopesPack>(text)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse color-scopes pack", e)
        ColorScopesPack()
    }

    fun loadColorRecoveryPresets(context: Context): List<PackPreset> =
        loadPresets(context, "color-scopes/recovery-presets.json")

    fun loadBgRemover(context: Context): BgRemoverPack = try {
        val text = readAsset(context, "bg-remover/config.json") ?: return BgRemoverPack()
        json.decodeFromString<BgRemoverPack>(text)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse bg-remover pack", e)
        BgRemoverPack()
    }

    // ---------- Conversion to GpuColorProfile ----------

    /**
     * Converts pack parametric filters to [GpuColorProfile] so they render
     * in the existing GPU filter studio (intensity slider supported via
     * [GpuColorProfile.applyWithIntensity]).
     *
     * Mapping: warmth -> temperature (5000K +/- warmth*1500), tint stays,
     * vignette -> VIGNETTE stylistic effect, grain -> FILM_GRAIN overlay
     * is approximated via stylistic effect when grain > 0.25.
     */
    fun toColorProfiles(filters: List<PackFilter>): List<GpuColorProfile> =
        filters.map { f ->
            val p = f.params
            val stylistic = when {
                p.vignette >= 0.3f -> StylisticEffectType.VIGNETTE
                p.fade >= 0.15f -> StylisticEffectType.SEPIA
                else -> StylisticEffectType.NONE
            }
            GpuColorProfile(
                id = "pack_${f.id}",
                name = f.name,
                category = f.category,
                description = "Pack filter: ${f.name}",
                iconEmoji = "✨",
                gradientColors = listOf(0xFF00E5FF, 0xFF7C3AED),
                config = GpuFilterConfig(
                    brightness = p.brightness,
                    contrast = p.contrast,
                    saturation = p.saturation,
                    temperature = 5000f + p.warmth * 1500f,
                    tint = p.tint * 100f,
                    exposure = p.highlights * 0.5f,
                    highlights = 1.0f - p.highlights * 0.5f,
                    shadows = p.shadows,
                    stylisticEffect = stylistic,
                    stylisticIntensity = maxOf(p.vignette, p.grain, p.fade).coerceIn(0f, 1f),
                    activeProfileId = "pack_${f.id}"
                )
            )
        }

    /**
     * Loads pack filters and registers them with [GpuColorProfiles].
     * Idempotent — safe to call on every panel open.
     */
    fun ensurePackProfilesRegistered(context: Context) {
        if (com.apexstudio.app.data.filter.GpuColorProfiles.hasPackProfiles()) return
        val profiles = toColorProfiles(loadFilters(context))
        com.apexstudio.app.data.filter.GpuColorProfiles.registerPackProfiles(profiles)
        Log.i(TAG, "Registered ${profiles.size} pack filter profiles")
    }

    /**
     * Loads pack transitions via [PackLoader] and registers them with
     * [TransitionLibrary]. Idempotent — safe to call on every picker open.
     */
    fun ensurePackTransitionsRegistered(context: Context) {
        val lib = com.apexstudio.app.domain.model.TransitionLibrary
        if (lib.hasPackTransitions()) return
        lib.registerPackTransitions(loadTransitions(context))
        Log.i(TAG, "Pack transitions registered")
    }
}
