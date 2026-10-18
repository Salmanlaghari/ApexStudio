package com.apexstudio.app.ui.screens.editor

import android.content.Context
import android.view.TextureView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPlayerViewFactoryTest {
    @Test
    fun factoryInflatesSharedPlayerLayoutWithTextureViewWhenPlayerIsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playerView = createOverlayPlayerView(context, null)
        assertTrue(
            "view_player.xml must provide a TextureView surface",
            playerView.videoSurfaceView is TextureView,
        )
    }
}
