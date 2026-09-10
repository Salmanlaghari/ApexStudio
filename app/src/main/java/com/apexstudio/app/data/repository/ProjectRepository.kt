package com.apexstudio.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.projectDataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_projects")

/**
 * Persists [Project]s to DataStore using kotlinx-serialization JSON.
 *
 * The store is a single string key (`projects_json`) holding the
 * full list of projects as one JSON blob. This is intentionally
 * simple — no Room, no KSP, no schema migrations. A small app with
 * a handful of projects doesn't need a relational schema; the
 * editor reads / writes the blob atomically inside one
 * `prefs.edit { }` block so concurrent auto-saves are safe.
 *
 * `loadAll()` is exposed as a suspending Flow for the Home screen
 * to render a live list of saved projects, and as a synchronous
 * `loadAllNow()` for callers that already have a coroutine in
 * scope and want a one-shot read at startup.
 */
class ProjectRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val serializer = ListSerializer(Project.serializer())
    private val key = stringPreferencesKey("projects_json")

    /** Stream of saved projects, empty list if nothing stored yet. */
    fun loadAll(): Flow<List<Project>> = context.projectDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    /** Synchronous one-shot read for callers that can't suspend. */
    fun loadAllNow(): List<Project> = runBlocking { loadAll().first() }

    /** Look up a single project by id. Returns null if not found. */
    suspend fun findById(projectId: String): Project? =
        loadAll().first().firstOrNull { it.id == projectId }

    /** Insert or replace a project (matched by id). */
    suspend fun saveProject(project: Project) {
        context.projectDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            val idx = current.indexOfFirst { it.id == project.id }
            if (idx >= 0) current[idx] = project else current.add(project)
            prefs[key] = json.encodeToString(serializer, current)
        }
    }

    /** Remove a project. No-op if not found. */
    suspend fun deleteProject(projectId: String) {
        context.projectDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            current.removeAll { it.id == projectId }
            prefs[key] = json.encodeToString(serializer, current)
        }
    }

    private fun decode(raw: String?): List<Project> = if (raw.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            // Treat a corrupt blob as empty — a single bad write
            // shouldn't brick the whole project list. The next save
            // will overwrite it.
            emptyList()
        }
    }
}
