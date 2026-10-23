package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Project

/**
 * Resolve an editor route without crossing project boundaries. A missing explicit ID is an error
 * (null result), not permission to fall back to the first project in the repository.
 */
internal fun resolveEditorProject(projects: List<Project>, requestedProjectId: String?): Project? =
    if (requestedProjectId == null) projects.firstOrNull()
    else projects.firstOrNull { it.id == requestedProjectId }
