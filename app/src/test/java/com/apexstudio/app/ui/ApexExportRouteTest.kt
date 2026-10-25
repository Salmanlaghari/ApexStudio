package com.apexstudio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ApexExportRouteTest {
    @Test fun `export selects its own content destination instead of the embedded editor workspace`() {
        for (tab in listOf("edit", "color", "audio", "home")) {
            assertEquals("export", apexContentTab(tab, true))
            assertEquals(tab, apexContentTab(tab, false))
        }
    }

    @Test fun `closing export returns to the originating content without changing the project route`() {
        val currentTab = "edit"
        assertEquals("export", apexContentTab(currentTab, true))
        assertEquals("edit", apexContentTab(currentTab, false))
    }
}
