package com.apexstudio.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.editorLayoutDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "apex_editor_layout")

/**
 * Persists the editor layout choice (New contextual vs Classic legacy).
 *
 * The Classic layout keeps the pre-contextual-toolbar editor UI
 * ([BottomEditToolbar] and the original transport row) available as a
 * backup, switchable from Settings. Default is the new contextual layout.
 */
class EditorLayoutPrefs(private val context: Context) {

    private val classicKey = booleanPreferencesKey("classic_editor_layout")

    /** True → Classic (old) editor UI; false → New contextual UI (default). */
    val classicEditorLayout: Flow<Boolean> =
        context.editorLayoutDataStore.data.map { prefs -> prefs[classicKey] ?: false }

    suspend fun setClassicEditorLayout(classic: Boolean) {
        context.editorLayoutDataStore.edit { prefs -> prefs[classicKey] = classic }
    }
}
