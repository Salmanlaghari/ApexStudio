package com.apexstudio.app.ui.screens.colortools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ColorComparisonOutputOwnerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun replacementAndCloseRemoveOnlyUnsharedGeneratedComparisons() {
        val source = file("source.mp4")
        val before = file("before.mp4")
        val after = file("after.mp4")
        val nextBefore = file("next-before.mp4")
        val nextAfter = file("next-after.mp4")
        val owner = ColorComparisonOutputOwner()
        owner.replace(before, after)
        owner.replace(nextBefore, nextAfter)
        assertFalse(before.exists())
        assertFalse(after.exists())
        assertTrue(nextAfter.exists())
        owner.dispose()
        assertFalse(nextBefore.exists())
        assertFalse(nextAfter.exists())
        assertTrue(source.exists())
    }

    @Test fun shareAttemptRetainsExactAfterForRetryButNotSubsequentUnsharedPairs() {
        val before = file("before.mp4")
        val after = file("after.mp4")
        val nextBefore = file("next-before.mp4")
        val nextAfter = file("next-after.mp4")
        val owner = ColorComparisonOutputOwner()
        owner.replace(before, after)
        owner.retainAfterForShare()
        owner.replace(nextBefore, nextAfter)
        owner.dispose()
        assertFalse(before.exists())
        assertTrue(after.exists())
        assertFalse(nextBefore.exists())
        assertFalse(nextAfter.exists())
    }

    private fun file(name: String): File = folder.newFile(name).apply { writeText("test-only output") }
}
