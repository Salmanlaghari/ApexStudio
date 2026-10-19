package com.apexstudio.app.camerakit

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Play-delivered host for Camera Kit, kept out of the base APK until requested. */
class CameraKitActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        com.google.android.play.core.splitcompat.SplitCompat.installActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LensesScreen(onBack = { finish() })
        }
    }
}
