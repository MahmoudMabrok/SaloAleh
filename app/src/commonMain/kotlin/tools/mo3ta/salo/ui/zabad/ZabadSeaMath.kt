package tools.mo3ta.salo.ui.zabad

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

val ZABAD_SEA_CAP: Duration = 12.hours
const val SEA_FLOOR = 0.74f
const val SEA_CEIL = 0.44f
const val FOAM_MIN = 2
const val FOAM_MAX = 24

data class ZabadSeaState(val waterLevel: Float, val foamCount: Int, val murk: Float)

fun calculateZabadSea(elapsed: Duration): ZabadSeaState {
    val progress = (elapsed.inWholeMilliseconds.coerceAtLeast(0).toDouble() /
        ZABAD_SEA_CAP.inWholeMilliseconds).coerceIn(0.0, 1.0).toFloat()
    return ZabadSeaState(
        waterLevel = SEA_FLOOR + (SEA_CEIL - SEA_FLOOR) * progress,
        foamCount = (FOAM_MIN + (FOAM_MAX - FOAM_MIN) * progress).roundToInt(),
        murk = progress,
    )
}
