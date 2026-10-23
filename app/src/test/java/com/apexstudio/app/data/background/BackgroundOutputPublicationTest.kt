package com.apexstudio.app.data.background

import java.io.File
import java.nio.file.FileAlreadyExistsException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackgroundOutputPublicationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun completedOutputIsPublishedAndTemporaryDisappears() {
        val tmp = folder.newFile("run.tmp").apply { writeText("new video") }
        val out = File(folder.root, "output.mp4")
        assertEquals(out, publishBackgroundOutput(tmp, out))
        assertEquals("new video", out.readText())
        assertFalse(tmp.exists())
    }
    @Test fun existingHistoryReferencedOutputIsNeverOverwritten() {
        val tmp = folder.newFile("run.tmp").apply { writeText("replacement") }
        val out = folder.newFile("output.mp4").apply { writeText("original") }
        publishBackgroundOutput(tmp, out)
        assertEquals("original", out.readText())
        assertFalse(tmp.exists())
    }
    @Test fun damagedExistingOutputIsNotOverwrittenEither() {
        val tmp = folder.newFile("run.tmp").apply { writeText("replacement") }
        val out = folder.newFile("output.mp4")
        try {
            publishBackgroundOutput(tmp, out)
            fail("An empty existing path must be handled by the controller, not overwritten")
        } catch (_: FileAlreadyExistsException) { }
        assertEquals(0L, out.length())
        assertEquals("replacement", tmp.readText())
    }
    @Test fun emptyTemporaryNeverCreatesPublishedOutput() {
        val tmp = folder.newFile("run.tmp")
        val out = File(folder.root, "output.mp4")
        try { publishBackgroundOutput(tmp, out); fail("Empty output") }
        catch (_: IllegalArgumentException) { }
        assertFalse(out.exists())
    }
}
