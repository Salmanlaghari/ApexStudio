package com.apexstudio.app.data.aireport

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

const val AI_REPORT_CONTACT = "laghariprince2@gmail.com"

/** Opens a user-reviewed draft only. Never attaches media or sends email automatically. */
fun aiContentReportEmailIntent(target: AiContentReportTarget, reason: AiContentReportReason): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$AI_REPORT_CONTACT")
        putExtra(Intent.EXTRA_SUBJECT, "ApexStudio — AI content report")
        putExtra(Intent.EXTRA_TEXT, """
            Reason: ${reason.label}
            Project reference: ${target.projectId}
            Clip reference: ${target.clipId}
            Clip type: ${target.clipType}
            ${target.textOverlayId?.let { "Text overlay reference: $it" } ?: ""}

            Please describe the issue before sending this email.
            No media attachment or project content has been included.
        """.trimIndent())
    }

fun openAiContentReportEmail(context: Context, target: AiContentReportTarget, reason: AiContentReportReason): Boolean =
    try {
        context.startActivity(aiContentReportEmailIntent(target, reason))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
