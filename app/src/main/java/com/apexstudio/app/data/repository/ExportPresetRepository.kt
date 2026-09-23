package com.apexstudio.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apexstudio.app.domain.model.ExportPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.exportPresetDataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_export_presets")

/**
 * Persists custom and built-in export configuration presets to DataStore.
 */
class ExportPresetRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val serializer = ListSerializer(ExportPreset.serializer())
    private val key = stringPreferencesKey("custom_export_presets_json")

    /**
     * Returns all export presets (built-in default presets + user-created custom presets).
     */
    fun loadPresets(): Flow<List<ExportPreset>> = context.exportPresetDataStore.data.map { prefs ->
        val raw = prefs[key]
        val customPresets = decode(raw)
        // Combine default presets with user custom presets (custom presets first for easy access)
        customPresets + ExportPreset.DefaultPresets
    }

    suspend fun loadPresetsNow(): List<ExportPreset> {
        val prefs = context.exportPresetDataStore.data.first()
        val customPresets = decode(prefs[key])
        return customPresets + ExportPreset.DefaultPresets
    }

    /**
     * Save a new custom preset or update an existing one.
     */
    suspend fun saveCustomPreset(preset: ExportPreset) {
        context.exportPresetDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            val index = current.indexOfFirst { it.id == preset.id }
            val itemToSave = preset.copy(isCustom = true)
            if (index >= 0) {
                current[index] = itemToSave
            } else {
                current.add(0, itemToSave)
            }
            prefs[key] = json.encodeToString(serializer, current)
        }
    }

    /**
     * Delete a custom preset by ID.
     */
    suspend fun deleteCustomPreset(presetId: String) {
        context.exportPresetDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            current.removeAll { it.id == presetId }
            prefs[key] = json.encodeToString(serializer, current)
        }
    }

    private fun decode(raw: String?): List<ExportPreset> = if (raw.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
