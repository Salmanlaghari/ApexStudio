package com.apexstudio.app.ui.screens.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Requests portrait only while the editor route is composed, then restores the activity's prior request. */
@Composable
internal fun EditorPortraitOrientationEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val previousOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose { activity.requestedOrientation = previousOrientation }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        val wrapper = current as? ContextWrapper ?: return null
        val next = wrapper.baseContext
        if (next === current) return null
        current = next
    }
}
