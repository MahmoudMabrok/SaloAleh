package tools.mo3ta.salo.mushaf

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MushafPackUiTest {

    private fun ui(status: Int, downloaded: Long = 0, total: Long = 0, transfer: Int = 0, error: Int = 0) =
        packUiFor(status, downloaded, total, transfer, error)

    @Test
    fun completedIsReady() = assertEquals(PackUi.Ready, ui(AssetPackStatus.COMPLETED))

    @Test
    fun notInstalledAndUnknownNeedAFetch() {
        assertEquals(PackUi.NotInstalled, ui(AssetPackStatus.NOT_INSTALLED))
        assertEquals(PackUi.NotInstalled, ui(AssetPackStatus.UNKNOWN))
    }

    @Test
    fun pendingHasNoPercent() =
        assertEquals(PackUi.Downloading(null, 0, 0, false), ui(AssetPackStatus.PENDING))

    @Test
    fun downloadingReportsPercentOfBytes() {
        assertEquals(
            PackUi.Downloading(25, 25, 100, false),
            ui(AssetPackStatus.DOWNLOADING, downloaded = 25, total = 100),
        )
        assertEquals(
            PackUi.Downloading(null, 5, 0, false),
            ui(AssetPackStatus.DOWNLOADING, downloaded = 5, total = 0),
        )
    }

    @Test
    fun transferringUsesTransferPercent() = assertEquals(
        PackUi.Downloading(60, 100, 100, true),
        ui(AssetPackStatus.TRANSFERRING, downloaded = 100, total = 100, transfer = 60),
    )

    @Test
    fun wifiAndUserConfirmationAskTheUser() {
        assertEquals(PackUi.NeedsConfirmation(wifi = true), ui(AssetPackStatus.WAITING_FOR_WIFI))
        assertEquals(PackUi.NeedsConfirmation(wifi = false), ui(AssetPackStatus.REQUIRES_USER_CONFIRMATION))
    }

    @Test
    fun failedAndCanceledOfferRetry() {
        assertEquals(
            PackUi.Failed(AssetPackErrorCode.NETWORK_ERROR, canceled = false),
            ui(AssetPackStatus.FAILED, error = AssetPackErrorCode.NETWORK_ERROR),
        )
        assertEquals(PackUi.Failed(0, canceled = true), ui(AssetPackStatus.CANCELED))
    }
}
