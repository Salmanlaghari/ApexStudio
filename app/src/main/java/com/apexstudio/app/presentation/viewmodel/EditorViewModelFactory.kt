package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import com.apexstudio.app.data.repository.MediaRepository

class EditorViewModelFactory(
    private val repo: MediaRepository = MediaRepository,
    private val context: Context? = null,
    private val projectId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(repo, context, projectId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val extrasContext = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Context
        val suppliedContext = extrasContext ?: context
        val applicationContext = suppliedContext?.applicationContext ?: suppliedContext
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(repo, applicationContext, projectId, extras.createSavedStateHandle()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
