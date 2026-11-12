package com.apexstudio.app.ui.screens.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apexstudio.app.data.captions.CaptionFillerDetector
import com.apexstudio.app.ui.theme.ApexThemeOption
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.apexSettingsStore by preferencesDataStore(name = "apex_settings")

data class LocalCreatorProfile(
    val displayName: String = "Creator",
    val avatarId: String = "violet"
)

/** Small persistent settings independent of the project store. */
object SettingsPreferences {
    private val themeKey = stringPreferencesKey("theme")
    private val captionFillerWordsKey = stringPreferencesKey("caption_filler_words")
    private val creatorDisplayNameKey = stringPreferencesKey("local_creator_name")
    private val creatorAvatarKey = stringPreferencesKey("local_creator_avatar")

    fun localCreatorProfile(context: Context): Flow<LocalCreatorProfile> =
        context.applicationContext.apexSettingsStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.map { preferences ->
            LocalCreatorProfile(
                displayName = preferences[creatorDisplayNameKey]?.trim()?.take(24)?.ifBlank { "Creator" } ?: "Creator",
                avatarId = preferences[creatorAvatarKey]?.takeIf { it in LOCAL_AVATAR_IDS } ?: "violet"
            )
        }

    suspend fun setLocalCreatorProfile(context: Context, profile: LocalCreatorProfile) {
        val displayName = profile.displayName.replace('\n', ' ').replace('\r', ' ').trim().take(24).ifBlank { "Creator" }
        val avatarId = profile.avatarId.takeIf { it in LOCAL_AVATAR_IDS } ?: "violet"
        context.applicationContext.apexSettingsStore.edit { preferences ->
            preferences[creatorDisplayNameKey] = displayName
            preferences[creatorAvatarKey] = avatarId
        }
    }

    fun theme(context: Context): Flow<ApexThemeOption> =
        context.applicationContext.apexSettingsStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.map { preferences ->
            ApexThemeOption.fromStoredValue(preferences[themeKey])
        }

    suspend fun setTheme(context: Context, theme: ApexThemeOption) {
        context.applicationContext.apexSettingsStore.edit { preferences ->
            preferences[themeKey] = theme.storedValue
        }
    }

    /** Filler vocabulary is app-wide, not stored in individual project files. */
    fun captionFillerWords(context: Context): Flow<List<String>> =
        context.applicationContext.apexSettingsStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.map { preferences ->
            preferences[captionFillerWordsKey]?.let { stored ->
                CaptionFillerDetector.normalizeList(stored.split('\n'))
            } ?: CaptionFillerDetector.defaultWords
        }

    suspend fun setCaptionFillerWords(context: Context, words: Iterable<String>) {
        val stored = CaptionFillerDetector.normalizeList(words).joinToString("\n")
        context.applicationContext.apexSettingsStore.edit { preferences ->
            preferences[captionFillerWordsKey] = stored
        }
    }

    private val LOCAL_AVATAR_IDS = setOf("violet", "cyan", "amber")
}

/** In-app privacy summary based on the current app source and manifest. */
object SettingsPolicyCopy {
    data class Section(val title: String, val body: String)

    val sections = listOf(
        Section(
            "Media and projects",
            "Projects and editing data are stored in app-private on-device storage. ApexStudio uses Android's system media picker for media you choose; editing and effects operate on selected media on your device. Exports are written to an Android media destination when you choose to export. Cached previews, downloaded packs, recordings, and crash diagnostics may also be stored locally."
        ),
        Section(
            "Network and optional services",
            "If you use the music library, ApexStudio requests catalog information and may download tracks from Jamendo. If you browse or download optional content packs, it fetches the catalog from GitHub and the selected pack from its published host. The optional AR Camera Kit uses Snap's SDK and camera access when you choose that feature; review the provider's current privacy information. These services receive ordinary connection metadata (such as your IP address)."
        ),
        Section(
            "Sharing, backups, and retention",
            "ApexStudio has no account or project-sync feature. The optional creator name and avatar in the dashboard profile are stored locally and are not synced. This code inspection found no base-app media-upload or crash-reporting service; sharing media or contacting us is initiated by you through Android or your chosen email app. Android device backup and transfer behavior can depend on your device and system settings. Project records remain until you delete them or clear app data; exports and other files may need to be removed separately using Android."
        ),
        Section(
            "AI and third-party components",
            "The inspected editing features apply effects or recognition to media you select; no prompt-based generative AI service or cloud media-generation endpoint was found in the base app. Optional third-party components and future app versions may have different data practices, so this summary is not a substitute for reviewing their disclosures."
        ),
        Section(
            "Questions",
            "For privacy questions, contact laghariprince2@gmail.com. Before release, the developer must also publish a public privacy-policy URL and make the Play Console Data safety declarations match the shipped app, SDKs, optional features, and actual data flows."
        )
    )
}
