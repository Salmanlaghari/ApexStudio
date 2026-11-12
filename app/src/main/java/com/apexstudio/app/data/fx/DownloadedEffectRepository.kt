package com.apexstudio.app.data.fx

import android.content.Context
import android.util.Log
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Numeric-only effect data; shader source is always compiled from app-owned code. */
data class DownloadedFxEffect(
    val id: String,
    val label: String,
    val category: String,
    val family: String,
    val a: Float,
    val b: Float,
    val c: Float,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val isAnimated: Boolean,
    val defaultSpeed: Float
)

/** Loads only hash-verified, bounded parameter profiles for existing shader families. */
object DownloadedEffectRepository {
    const val PAYLOAD_TYPE = "fx_profiles_v1"
    const val PACK_ID = "apex-fx-vol-01"
    private const val TAG = "DownloadedEffects"
    private const val MAX_ENTRY_BYTES = 128 * 1024L
    private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    private val SAFE_HASH = Regex("[0-9a-f]{64}")
    private val SAFE_CATEGORY = Regex("[a-zA-Z0-9 &_-]{1,40}")

    private val _effects = MutableStateFlow<List<DownloadedFxEffect>>(emptyList())
    val effects: StateFlow<List<DownloadedFxEffect>> = _effects.asStateFlow()
    private data class VerifiedSource(
        val packId: String,
        val root: File,
        val entry: String,
        val sha256: String
    )
    @Volatile private var verifiedSources: Map<String, VerifiedSource> = emptyMap()

    /**
     * Pure in-memory lookup: no file I/O, no state mutation, safe to call
     * from any thread (including synchronous state updates and export
     * snapshot builders). Returns the effect only if it was loaded by
     * [refreshInstalled] from a digest-verified source. This lookup does NOT
     * re-check the installed file's digest — that integrity re-verification
     * (and the pack invalidation it may trigger) lives exclusively in the
     * explicit [findVerified] call.
     */
    fun find(id: String?): DownloadedFxEffect? {
        val target = id ?: return null
        if (!verifiedSources.containsKey(target)) return null
        return _effects.value.firstOrNull { it.id == target }
    }

    /**
     * Explicit integrity-checked lookup: re-reads the installed entry on the
     * IO dispatcher and verifies it still matches its pinned digest. If the
     * entry is stale or tampered, the owning pack is invalidated (registry
     * state changes) and null is returned. This is the ONLY lookup allowed to
     * touch disk or mutate state — call it from an IO-backed effect,
     * `produceState`, or a ViewModel/repository path, never directly during
     * Compose composition.
     */
    suspend fun findVerified(id: String?): DownloadedFxEffect? = withContext(Dispatchers.IO) {
        val target = id ?: return@withContext null
        val effect = find(target) ?: return@withContext null
        val source = verifiedSources[target] ?: return@withContext null
        if (!isSourceCurrent(source)) {
            invalidatePack(source.packId)
            return@withContext null
        }
        effect
    }

    fun hasPack(packId: String): Boolean =
        _effects.value.any { it.id.startsWith("dl_${packId}_") }

    /** Replaces the dynamic catalog from installed packs present in the current trusted manifest. */
    suspend fun refreshInstalled(
        context: Context,
        catalog: PackManifestRepo.PackManifestFile? = null
    ): Int = withContext(Dispatchers.IO) {
        val manifest = catalog ?: PackManifestRepo.fetchManifest(context)
        val installedIds = PackManager.installedPackIds(context)
        val installedDirs = PackManager.packsRoot(context).listFiles { f -> f.isDirectory }
            ?.map { it.name }?.toSet().orEmpty()
        val eligiblePacks = manifest.packs.filter {
                SAFE_ID.matches(it.id) && it.category == "effects" &&
                    it.payloadType == PAYLOAD_TYPE && it.id in installedIds && it.id in installedDirs
            }
        val sources = mutableMapOf<String, VerifiedSource>()
        val loaded = eligiblePacks.flatMap { pack ->
            val root = PackManager.packDir(context, pack.id).canonicalFile
            val parsed = loadPackEffects(root, pack)
            val source = VerifiedSource(pack.id, root, pack.entry, pack.entrySha256.lowercase())
            parsed.forEach { sources[it.id] = source }
            parsed
        }.distinctBy { it.id }
        verifiedSources = sources
        _effects.value = loaded
        loaded.size
    }

    private fun isSourceCurrent(source: VerifiedSource): Boolean = runCatching {
        val file = resolveUnder(source.root, source.entry) ?: return@runCatching false
        file.isFile && file.length() in 1L..MAX_ENTRY_BYTES &&
            sha256(file.readBytes()) == source.sha256
    }.getOrDefault(false)

    private fun invalidatePack(packId: String) {
        val prefix = "dl_${packId}_"
        verifiedSources = verifiedSources.filterKeys { !it.startsWith(prefix) }
        _effects.value = _effects.value.filterNot { it.id.startsWith(prefix) }
    }

    /** Shared by runtime loading and tests; never accepts arbitrary GLSL or file paths. */
    internal fun loadPackEffects(root: File, pack: PackManifestRepo.RemotePack): List<DownloadedFxEffect> {
        if (!SAFE_ID.matches(pack.id) || pack.category != "effects" || pack.payloadType != PAYLOAD_TYPE) return emptyList()
        val expectedHash = pack.entrySha256.lowercase()
        if (!SAFE_HASH.matches(expectedHash)) return emptyList()
        val file = resolveUnder(root, pack.entry) ?: return emptyList()
        if (!file.isFile || file.length() !in 1L..MAX_ENTRY_BYTES) return emptyList()
        return try {
            val bytes = file.readBytes()
            if (bytes.size.toLong() != file.length() || sha256(bytes) != expectedHash) return emptyList()
            val document = JSONObject(bytes.toString(Charsets.UTF_8))
            if (document.optInt("schemaVersion") != 1) return emptyList()
            val attribution = document.optString("attribution")
            if (attribution.isBlank() || attribution.length > 300) return emptyList()
            val items = document.optJSONArray("effects") ?: return emptyList()
            if (pack.itemCount <= 0 || items.length() != pack.itemCount) return emptyList()

            val seen = mutableSetOf<String>()
            val result = ArrayList<DownloadedFxEffect>(items.length())
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: return emptyList()
                val sourceId = item.optString("id")
                val label = item.optString("name")
                val category = item.optString("category")
                val family = item.optString("family")
                val a = finiteFloat(item, "a") ?: return emptyList()
                val b = finiteFloat(item, "b") ?: return emptyList()
                val c = finiteFloat(item, "c") ?: return emptyList()
                val tint = item.optJSONArray("tint") ?: return emptyList()
                if (!SAFE_ID.matches(sourceId) || !seen.add(sourceId) || FxPreset.byId(sourceId) != null) return emptyList()
                if (label.isBlank() || label.length > 60 || !SAFE_CATEGORY.matches(category)) return emptyList()
                if (!ModernFxShaders.supportsFamily(family)) return emptyList()
                if (!parametersInRange(family, a, b, c)) return emptyList()
                if (tint.length() != 3) return emptyList()
                val tintR = finiteTint(tint, 0) ?: return emptyList()
                val tintG = finiteTint(tint, 1) ?: return emptyList()
                val tintB = finiteTint(tint, 2) ?: return emptyList()
                val animated = item.optBoolean("animated", false)
                val speed = finiteFloat(item, "defaultSpeed") ?: return emptyList()
                if (animated && speed !in 0.1f..4f) return emptyList()
                if (!animated && speed != 1f) return emptyList()
                result += DownloadedFxEffect(
                    id = "dl_${pack.id}_$sourceId",
                    label = label,
                    category = category,
                    family = family,
                    a = a,
                    b = b,
                    c = c,
                    tintR = tintR,
                    tintG = tintG,
                    tintB = tintB,
                    isAnimated = animated,
                    defaultSpeed = speed
                )
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Invalid effect pack entry: ${pack.id}/${pack.entry}", e)
            emptyList()
        }
    }

    private fun finiteFloat(item: JSONObject, key: String): Float? {
        val value = item.optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }?.toFloat()?.takeIf { it.isFinite() }
    }

    private fun finiteTint(tint: JSONArray, index: Int): Float? {
        val value = tint.optDouble(index, Double.NaN)
        return value.takeIf { it.isFinite() && it in 0.0..1.0 }?.toFloat()
    }

    private fun parametersInRange(family: String, a: Float, b: Float, c: Float): Boolean = when (family) {
        "PRISM" -> a in 0.1f..8f && b in 0f..0.5f && c in 0f..1f
        "SPLIT_TONE" -> a in 0f..1f && b in 0.01f..0.9f && c in 0f..1f && a + b <= 0.99f
        "RIPPLE" -> a in 0.1f..12f && b in 0f..0.05f && c in 0.1f..12f
        "MOSAIC" -> a in 1f..24f && b in 1f..24f && c in 0f..0.5f
        "CRT" -> a in 1f..360f && b in 0f..2f && c in 0f..1f
        "HALATION" -> a in 0f..1f && b in 0.5f..20f && c in 0f..1f
        "INK" -> a in 0f..1f && b in 0.1f..2f && c in 0f..1f
        "MIRROR" -> a in 1f..24f && b in -6.2832f..6.2832f && c in 0f..1f
        "GLITCH" -> a in 1f..32f && b in 0f..0.05f && c in 0f..1f
        "STREAK" -> a in -6.2832f..6.2832f && b in 0f..2f && c in 0f..2f
        else -> false
    }

    private fun resolveUnder(root: File, relative: String): File? {
        if (relative.isBlank() || relative.length > 240 || relative.startsWith('/') || '\\' in relative) return null
        if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val base = root.canonicalFile
        val entry = File(base, relative).canonicalFile
        return entry.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
