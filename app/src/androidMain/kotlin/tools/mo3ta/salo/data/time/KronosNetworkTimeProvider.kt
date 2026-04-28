package tools.mo3ta.salo.data.time

import android.content.Context
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import kotlinx.datetime.Instant
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

class KronosNetworkTimeProvider(context: Context) : NetworkTimeProvider {

    private val kronosClock: KronosClock = AndroidClockFactory.createKronosClock(
        context = context,
        ntpHosts = listOf("time.google.com", "0.africa.pool.ntp.org", "1.africa.pool.ntp.org"),
    )

    override fun prime() {
        kronosClock.syncInBackground()
    }

    override fun getCompetitionWindow(): MohamedLoversCompetitionWindow {
        val ms = kronosClock.getCurrentNtpTimeMs() ?: run {
            prime()
            return MohamedLoversCompetitionWindow(message = "جارٍ مزامنة الوقت من الشبكة.")
        }
        return buildCompetitionWindow(Instant.fromEpochMilliseconds(ms))
    }
}
