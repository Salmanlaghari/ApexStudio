package com.apexstudio.app.camerakit

import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraKitDeliveryRouteTest {
    @Test fun pendingAndDownloadingStatusesMapToProgress() {
        assertEquals(DeliveryPhase.PENDING, deliveryPhase(SplitInstallSessionStatus.PENDING))
        assertEquals(DeliveryPhase.DOWNLOADING, deliveryPhase(SplitInstallSessionStatus.DOWNLOADING))
        assertEquals(DeliveryPhase.DOWNLOADING, deliveryPhase(SplitInstallSessionStatus.DOWNLOADED))
    }

    @Test fun installAndTerminalStatusesMapCorrectly() {
        assertEquals(DeliveryPhase.INSTALLING, deliveryPhase(SplitInstallSessionStatus.INSTALLING))
        assertEquals(DeliveryPhase.INSTALLED, deliveryPhase(SplitInstallSessionStatus.INSTALLED))
        assertEquals(DeliveryPhase.CANCELED, deliveryPhase(SplitInstallSessionStatus.CANCELED))
        assertEquals(DeliveryPhase.FAILED, deliveryPhase(SplitInstallSessionStatus.FAILED))
    }

    @Test fun playConfirmationNeverBecomesAnInertPendingSpinner() {
        assertEquals(DeliveryPhase.CONFIRMATION_REQUIRED, deliveryPhase(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION))
    }

    @Test fun cancelRequestIsNotReportedCompleteBeforePlayConfirmsIt() {
        assertEquals(DeliveryPhase.CANCELING, deliveryPhase(SplitInstallSessionStatus.CANCELING))
    }
}
