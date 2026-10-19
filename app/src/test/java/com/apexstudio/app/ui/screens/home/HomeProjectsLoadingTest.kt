package com.apexstudio.app.ui.screens.home

import com.apexstudio.app.domain.model.Project
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeProjectsLoadingTest {
    @Test
    fun emptySavedProjectsLoadsFallbackOnBackgroundDispatcher() = runTest {
        val ioDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "home-project-fallback-io")
        }.asCoroutineDispatcher()
        val fallbackProjects = listOf(project("fallback"))
        var fallbackThreadName: String? = null

        try {
            val loaded = resolveHomeProjects(
                savedProjects = emptyList(),
                loadFallback = {
                    fallbackThreadName = Thread.currentThread().name
                    fallbackProjects
                },
                ioDispatcher = ioDispatcher
            )

            assertEquals(fallbackProjects, loaded)
            assertEquals("home-project-fallback-io", fallbackThreadName)
        } finally {
            ioDispatcher.close()
        }
    }

    @Test
    fun savedProjectsAreReturnedWithoutLoadingFallback() = runTest {
        val savedProjects = listOf(project("saved"))
        var fallbackWasCalled = false

        val loaded = resolveHomeProjects(savedProjects) {
            fallbackWasCalled = true
            listOf(project("fallback"))
        }

        assertEquals(savedProjects, loaded)
        assertFalse("Fallback should only be loaded when no projects are saved", fallbackWasCalled)
    }

    private fun project(id: String) = Project(
        id = id,
        name = "Project $id",
        durationMs = 1_000L
    )
}
