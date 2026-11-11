package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class AnimatedStickerGridTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun availablePackCardShowsCountAndStartsDownload() {
        val pack = PackManifestRepo.RemotePack(
            id = "animated-stickers-vol-01",
            name = "ApexStudio Animated Stickers · Vol. 01",
            description = "25 original looping Lottie stickers · Love ×10, Party ×10, Nature ×5.",
            category = "stickers",
            sizeBytes = 27_829L,
            url = "https://example.invalid/stickers.zip",
            sha256 = "a".repeat(64),
            entry = "manifest.json",
            itemCount = 25
        )
        var downloadCount = 0
        compose.setContent {
            StickerPackDownloadCard(
                item = PackManager.PackUiItem(pack, PackManager.PackState.NotDownloaded),
                onDownload = { downloadCount++ },
                onCancel = {},
                onRepair = {}
            )
        }

        compose.onNodeWithText("25 stickers · ${PackManifestRepo.formatSize(pack.sizeBytes)}").assertIsDisplayed()
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, downloadCount) }
    }

    @Test
    fun activeDownloadCardShowsProgressAndCancelAction() {
        val pack = PackManifestRepo.RemotePack(
            id = "animated-stickers-vol-01",
            name = "ApexStudio Animated Stickers · Vol. 01",
            category = "stickers",
            sizeBytes = 100L,
            url = "https://example.invalid/stickers.zip",
            sha256 = "a".repeat(64),
            entry = "manifest.json",
            itemCount = 25
        )
        var cancelCount = 0
        compose.setContent {
            StickerPackDownloadCard(
                item = PackManager.PackUiItem(pack, PackManager.PackState.Downloading(50L, 100L)),
                onDownload = {},
                onCancel = { cancelCount++ },
                onRepair = {}
            )
        }

        compose.onNodeWithText("50% downloaded").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelCount) }
    }
}
