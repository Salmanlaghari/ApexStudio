package com.apexstudio.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaSourcePolicyTest {
    @Test fun localPathsAndLocalFileUrisAreAllowed() {
        assertTrue(LocalMediaSourcePolicy.accepts(null, null))
        assertTrue(LocalMediaSourcePolicy.accepts("file", null))
        assertTrue(LocalMediaSourcePolicy.accepts("file", ""))
        assertTrue(LocalMediaSourcePolicy.accepts("FILE", "LOCALHOST"))
    }

    @Test fun remoteFileAuthoritiesAndProtocolRelativeHostsAreRejected() {
        listOf("example.com", "192.168.1.1", "localhost:8080", "user@localhost").forEach { host ->
            assertFalse(host, LocalMediaSourcePolicy.accepts("file", host))
            assertFalse(host, LocalMediaSourcePolicy.accepts(null, host))
        }
    }

    @Test fun selectedContentAndAppResourceProvidersAreAllowed() {
        assertTrue(LocalMediaSourcePolicy.accepts("content", "com.android.providers.media.documents"))
        assertTrue(LocalMediaSourcePolicy.accepts("ANDROID.RESOURCE", "com.apexstudio.app"))
    }

    @Test fun providersWithoutAuthoritiesAreRejected() {
        listOf("content", "android.resource").forEach { scheme ->
            listOf(null, "", " ").forEach { authority ->
                assertFalse(LocalMediaSourcePolicy.accepts(scheme, authority))
            }
        }
    }

    @Test fun networkAndUnknownSchemesAreRejected() {
        listOf("http", "https", "HTTP", "ftp", "smb", "rtsp", "data", "javascript", "custom").forEach { scheme ->
            assertFalse(scheme, LocalMediaSourcePolicy.accepts(scheme, "example.com"))
        }
    }
}
