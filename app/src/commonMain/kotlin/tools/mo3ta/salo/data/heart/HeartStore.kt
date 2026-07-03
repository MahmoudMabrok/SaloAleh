package tools.mo3ta.salo.data.heart

import com.russhwolf.settings.Settings

class HeartStore(private val settings: Settings) {

    fun getScore(): Int = settings.getInt(KEY_SCORE, 0)

    fun getAnchorTs(): Long = settings.getLong(KEY_ANCHOR_TS, 0L)

    fun save(score: Int, anchorTs: Long) {
        settings.putInt(KEY_SCORE, score)
        settings.putLong(KEY_ANCHOR_TS, anchorTs)
    }

    private companion object {
        const val KEY_SCORE = "heart_score"
        const val KEY_ANCHOR_TS = "heart_anchor_ts"
    }
}
