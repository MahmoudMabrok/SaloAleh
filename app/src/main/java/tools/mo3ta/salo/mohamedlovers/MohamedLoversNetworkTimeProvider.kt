package com.elsharif.dailyseventy.domain.mohamedlovers

import android.content.Context
import android.util.Log
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MohamedLoversNetworkTimeProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val kronosClock: KronosClock = AndroidClockFactory.createKronosClock(
        context = context,
        ntpHosts = listOf(
            "time.google.com",
            "0.africa.pool.ntp.org",
            "1.africa.pool.ntp.org",
        ),
    )

    private val cairoZoneId = ZoneId.of("Africa/Cairo")

    fun prime() {
        kronosClock.syncInBackground()
    }

    fun getCompetitionWindow(): MohamedLoversCompetitionWindow {
        val networkTimeMs = kronosClock.getCurrentNtpTimeMs()
        if (networkTimeMs == null) {
            Log.w(
                "TestTest",
                "MohamedLoversNTP: kronos getCurrentNtpTimeMs=null, NTP not synced yet. roundKey will be null and flush will be blocked. Hosts=time.google.com, africa.pool.ntp.org",
            )
            prime()
            return MohamedLoversCompetitionWindow(
                message = "جارٍ مزامنة الوقت من الشبكة.",
            )
        }

        val networkNow = Instant.ofEpochMilli(networkTimeMs).atZone(cairoZoneId)
        val roundEnd = nextRoundBoundary(networkNow)
        val isFridayBonus = networkNow.dayOfWeek == DayOfWeek.FRIDAY &&
            networkNow.hour < ROUND_BOUNDARY_HOUR
        return MohamedLoversCompetitionWindow(
            networkNow = networkNow,
            isFridayBonus = isFridayBonus,
            roundKey = roundEnd.toLocalDate().toString(),
            roundEnd = roundEnd,
            message = null,
        )
    }

    private fun nextRoundBoundary(now: ZonedDateTime): ZonedDateTime {
        val daysUntilFriday = ((DayOfWeek.FRIDAY.value - now.dayOfWeek.value + 7) % 7).toLong()
        val candidate = now.toLocalDate()
            .plusDays(daysUntilFriday)
            .atTime(ROUND_BOUNDARY_HOUR, 0)
            .atZone(cairoZoneId)
        return if (!now.isBefore(candidate)) candidate.plusDays(7) else candidate
    }

    private companion object {
        const val ROUND_BOUNDARY_HOUR = 18
    }
}
