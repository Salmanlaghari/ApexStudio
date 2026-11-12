package com.apexstudio.app.data.packs.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the PR #230 fix-forward: malformed pack data must be
 * rejected with a failure reason, never by throwing out of the install path
 * (the old `require()`/`check()` calls crashed the download coroutine).
 */
class PackInstallValidationTest {

    @Test
    fun sizeMismatchIsRejectedWithMessageNotException() {
        assertEquals(
            "Pack size mismatch: expected 100 bytes, received 90",
            packSizeFailure(expectedBytes = 100L, actualBytes = 90L)
        )
    }

    @Test
    fun matchingSizeIsAccepted() {
        assertNull(packSizeFailure(expectedBytes = 100L, actualBytes = 100L))
    }

    @Test
    fun unknownManifestSizeAcceptsAnyDownloadedSize() {
        assertNull(packSizeFailure(expectedBytes = 0L, actualBytes = 123L))
        assertNull(packSizeFailure(expectedBytes = -1L, actualBytes = 0L))
    }

    @Test
    fun entriesInsidePackDirAreAccepted() {
        val root = "/data/packs/stickers"
        assertTrue(
            isCanonicalPathInside("/data/packs/stickers${File.separator}manifest.json", root)
        )
        assertTrue(
            isCanonicalPathInside(
                "/data/packs/stickers${File.separator}items${File.separator}heart.json",
                root
            )
        )
    }

    @Test
    fun zipSlipEntriesAreRejected() {
        val root = "/data/packs/stickers"
        // Entry that lands in the parent dir instead of the pack dir.
        assertFalse(isCanonicalPathInside("/data/packs/evil.json", root))
        // Entry somewhere else entirely.
        assertFalse(isCanonicalPathInside("/data/other/file.json", root))
        // A sibling directory that merely shares the name prefix must not pass.
        assertFalse(isCanonicalPathInside("/data/packs/stickers-evil/file.json", root))
    }
}
