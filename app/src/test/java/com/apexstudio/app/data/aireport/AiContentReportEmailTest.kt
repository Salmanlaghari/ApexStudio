package com.apexstudio.app.data.aireport

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiContentReportEmailTest {
    private val target = AiContentReportTarget("project-ref", "clip-ref", "VIDEO", "overlay-ref")

    @Test fun draftUsesSendToExactContactAndContainsOnlyReferences() {
        val intent = aiContentReportEmailIntent(target, AiContentReportReason.HATEFUL)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:laghariprince2@gmail.com", intent.dataString)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Reason: Hateful content"))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Clip reference: clip-ref"))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Text overlay reference: overlay-ref"))
        assertNull(intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM))
        assertNull(intent.clipData)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertEquals(setOf(Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT), intent.extras!!.keySet())
    }

    @Test fun missingEmailAppReturnsFalseForLocalOnlyFallback() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(openAiContentReportEmail(context, target, AiContentReportReason.OTHER))
    }

    @Test fun blockedEmailAppReturnsFalseWithoutClaimingSent() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw SecurityException() }
        }
        assertFalse(openAiContentReportEmail(context, target, AiContentReportReason.OTHER))
    }
}
