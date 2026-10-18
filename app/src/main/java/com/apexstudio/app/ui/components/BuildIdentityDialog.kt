package com.apexstudio.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.apexstudio.app.BuildConfig

/** Embedded at build time, so a screenshot identifies the APK actually running. */
object BuildIdentity {
    val shortCommit: String get() = BuildConfig.SOURCE_COMMIT.take(8)
    val description: String get() = "ApexStudio ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Commit: ${BuildConfig.SOURCE_COMMIT}\n" +
        "Ref: ${BuildConfig.SOURCE_REF}\n" +
        "Run: ${BuildConfig.BUILD_RUN}\n" +
        "Variant: ${BuildConfig.BUILD_TYPE}"
}

@Composable
fun BuildIdentityDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Installed build") },
        text = { Text(BuildIdentity.description) },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(BuildIdentity.description)) }) {
                Text("Copy build info")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
