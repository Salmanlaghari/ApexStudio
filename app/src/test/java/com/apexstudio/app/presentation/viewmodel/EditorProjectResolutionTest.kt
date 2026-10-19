package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorProjectResolutionTest {
    private val first = Project(id = "project-a", name = "A", durationMs = 1_000L)
    private val second = Project(id = "project-b", name = "B", durationMs = 2_000L)

    @Test
    fun `explicit project selection returns only its exact match`() {
        assertEquals(second, resolveEditorProject(listOf(first, second), "project-b"))
    }

    @Test
    fun `missing explicit project selection does not fall through to another project`() {
        assertNull(resolveEditorProject(listOf(first, second), "deleted-project"))
    }

    @Test
    fun `no explicit selection preserves first-project default`() {
        assertEquals(first, resolveEditorProject(listOf(first, second), null))
        assertNull(resolveEditorProject(emptyList(), null))
    }
}
