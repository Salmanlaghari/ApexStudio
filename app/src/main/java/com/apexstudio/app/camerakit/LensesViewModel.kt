package com.apexstudio.app.camerakit

import androidx.lifecycle.ViewModel
import com.snap.camerakit.Session
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.apply
import com.snap.camerakit.lenses.clear
import com.snap.camerakit.lenses.observe
import com.snap.camerakit.lenses.whenHasSome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

/** UI-friendly projection of a Camera Kit lens. */
data class LensItem(
    val id: String,
    val groupId: String,
    val name: String,
    val iconUri: String?
)

/** Maps a Camera Kit [LensesComponent.Lens] to its UI projection. */
fun LensesComponent.Lens.toLensItem(): LensItem = LensItem(
    id = id,
    groupId = groupId,
    name = name.orEmpty().ifBlank { id },
    iconUri = iconUri?.toString()
)

/**
 * Observes the Camera Kit lens repository for [CameraKitConfig.DEMO_LENS_GROUP_ID] and
 * applies/clears lenses on the bound session.
 */
class LensesViewModel : ViewModel() {
    private val _lenses = MutableStateFlow<List<LensItem>>(emptyList())
    val lenses: StateFlow<List<LensItem>> = _lenses.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Written from the repository callback thread, read from the main thread:
    // always swapped as a whole immutable reference so readers never see a torn map.
    @Volatile
    private var lensById: Map<String, LensesComponent.Lens> = emptyMap()
    private var repositoryHandle: Closeable? = null
    private var session: Session? = null

    /** Starts observing lenses for the demo lens group on [session]. */
    fun bind(session: Session) {
        if (this.session === session) return
        unbind()
        this.session = session
        try {
            repositoryHandle = session.lenses.repository.observe(
                LensesComponent.Repository.QueryCriteria.Available(CameraKitConfig.DEMO_LENS_GROUP_ID)
            ) { result ->
                when (result) {
                    is LensesComponent.Repository.Result.None ->
                        _error.value = "No lenses found for this lens group."
                    else -> result.whenHasSome { lensList ->
                        lensById = lensList.associateBy { it.id }
                        _lenses.value = lensList.map { it.toLensItem() }
                        _error.value = null
                    }
                }
            }
        } catch (t: Throwable) {
            _error.value = "Could not load lenses: ${t.message}"
        }
    }

    /** Applies the lens with [id] to the camera preview. */
    fun applyLens(id: String) {
        val lens = lensById[id] ?: return
        _applying.value = true
        try {
            session?.lenses?.processor?.apply(lens) { success ->
                _applying.value = false
                if (success) {
                    _selectedId.value = id
                    _error.value = null
                } else {
                    _error.value = "Lens failed to apply"
                }
            }
        } catch (t: Throwable) {
            _applying.value = false
            _error.value = "Lens failed to apply: ${t.message}"
        }
    }

    /** Removes the currently applied lens. */
    fun clearLens() {
        try {
            session?.lenses?.processor?.clear()
        } catch (_: Throwable) { }
        _selectedId.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    /** Stops observing the repository. The session itself is closed by its owner. */
    fun unbind() {
        runCatching { repositoryHandle?.close() }
        repositoryHandle = null
        session = null
    }

    override fun onCleared() {
        unbind()
    }
}
