package com.apexstudio.app.ai

import java.util.Locale

/**
 * URI admission for offline analysis, before any decoder or model is opened.
 * content:// is resolved by the user-selected provider; that provider may itself use remote storage.
 * This policy prevents direct network schemes and remote file/UNC authorities, not provider traffic.
 */
object LocalMediaSourcePolicy {
    fun accepts(scheme: String?, authority: String?): Boolean = when (scheme?.lowercase(Locale.ROOT)) {
        null -> authority.isNullOrEmpty()
        "file" -> authority.isNullOrEmpty() || authority.equals("localhost", ignoreCase = true)
        "content", "android.resource" -> !authority.isNullOrBlank()
        else -> false
    }
}
