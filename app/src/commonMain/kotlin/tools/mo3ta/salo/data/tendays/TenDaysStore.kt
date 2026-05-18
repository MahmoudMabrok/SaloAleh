package tools.mo3ta.salo.data.tendays

import com.russhwolf.settings.Settings

class TenDaysStore(private val settings: Settings) {

    fun getDhikrCount(day: Int, dhikr: DhikrType): Int =
        settings.getInt(key(day, dhikr.key), 0)

    fun incrementDhikr(day: Int, dhikr: DhikrType): Int {
        val k = key(day, dhikr.key)
        val updated = settings.getInt(k, 0) + 1
        settings.putInt(k, updated)
        return updated
    }

    fun getTakbeerCount(day: Int): Int =
        settings.getInt(key(day, "takbeer"), 0)

    fun incrementTakbeer(day: Int): Int {
        val k = key(day, "takbeer")
        val updated = settings.getInt(k, 0) + 1
        settings.putInt(k, updated)
        return updated
    }

    fun isFasting(day: Int): Boolean =
        settings.getBoolean(key(day, "fasting"), false)

    fun setFasting(day: Int, value: Boolean) =
        settings.putBoolean(key(day, "fasting"), value)

    fun isSadaqah(day: Int): Boolean =
        settings.getBoolean(key(day, "sadaqah"), false)

    fun setSadaqah(day: Int, value: Boolean) =
        settings.putBoolean(key(day, "sadaqah"), value)

    fun isAutoPlayTakbeer(): Boolean =
        settings.getBoolean(KEY_AUTO_PLAY, false)

    fun setAutoPlayTakbeer(enabled: Boolean) =
        settings.putBoolean(KEY_AUTO_PLAY, enabled)

    private fun key(day: Int, suffix: String) = "tenDays_day${day}_$suffix"

    private companion object {
        const val KEY_AUTO_PLAY = "tenDays_autoPlayTakbeer"
    }
}

enum class DhikrType(val key: String, val label: String) {
    SubhanAllah("subhanallah", "سبحان الله"),
    Alhamdulillah("alhamdulillah", "الحمد لله"),
    AllahuAkbar("allahuakbar", "الله أكبر"),
    LaIlahaIllallah("lailaha", "لا إله إلا الله"),
    LaHawla("lahawla", "لا حول ولا قوة إلا بالله"),
}
