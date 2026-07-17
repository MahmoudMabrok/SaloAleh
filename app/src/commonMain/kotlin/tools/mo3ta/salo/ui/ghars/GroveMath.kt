package tools.mo3ta.salo.ui.ghars

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure-Kotlin grove arithmetic — no Compose. Everything the renderer needs to be
 * deterministic lives here so it can be unit-tested without a canvas.
 *
 * The wrap is what keeps this cheap: the screen never draws more than [GROVE_SIZE]
 * palms, so render cost is a constant independent of the score.
 */

const val GROVE_SIZE = 50 // palms per grove; hard drawing ceiling
val ROW_CAPACITY = intArrayOf(8, 10, 14, 18) // palms per depth row, nearest first; sums to 50
const val ROW_DEPTH_FALLOFF = 0.72f // scale multiplier per row back
const val HAZE_FALLOFF = 0.855f // atmospheric haze falloff base per row back
const val HAZE_MAX = 0.62f // atmospheric haze cap on the furthest row

/**
 * mulberry32 — the exact PRNG the HTML mockup uses, ported bit-for-bit so a palm
 * looks identical on every device forever. Kotlin `Int` arithmetic already wraps at
 * 32 bits (two's complement), matching JS `Math.imul`.
 */
class Mulberry32(private var state: Int) {
    fun next(): Float {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

data class PalmParams(
    val heightScale: Float,
    val lean: Float,
    val frondCount: Int,
    val trunkBow: Float,
    val jitter: Float,
    val swayPhase: Float,
    val bearsDates: Boolean, // (index + 1) % GROVE_SIZE == 0 — the grove-completing palm
)

/** Deterministic per-palm parameters, seeded on the palm's absolute index. */
fun palmParams(index: Int): PalmParams {
    val seed = (((index + 1).toLong() * 2654435761L) and 0xFFFFFFFFL).toInt()
    val r = Mulberry32(seed)
    val a = r.next(); val b = r.next(); val c = r.next()
    val d = r.next(); val e = r.next(); val f = r.next()
    return PalmParams(
        heightScale = 0.82f + a * 0.40f,
        lean = (b - 0.5f) * 0.22f,
        frondCount = 12 + (c * 5).toInt(),
        trunkBow = (d - 0.5f) * 0.50f,
        jitter = (e - 0.5f) * 0.55f,
        swayPhase = f * 6.283f,
        bearsDates = (index + 1) % GROVE_SIZE == 0,
    )
}

/** Palms visible on screen for [count] taps — never exceeds [GROVE_SIZE]. */
fun shownPalms(count: Int): Int = if (count <= 0) 0 else ((count - 1) % GROVE_SIZE) + 1

/** Absolute index of the first palm of the grove currently being planted. */
fun groveStartIndex(count: Int): Int =
    if (count <= 0) 0 else ((count - 1) / GROVE_SIZE) * GROVE_SIZE

/** Groves fully planted today. */
fun completedGroves(count: Int): Int = if (count <= 0) 0 else count / GROVE_SIZE

/**
 * How many complete groves (بساتين) the user owns for a given lifetime palm total.
 * A grove is finished the moment its 25th palm goes in, so this is a plain floor-divide —
 * the same arithmetic as [completedGroves] but applied to the lifetime accumulator so the
 * gardens gallery can show every grove ever grown, not just today's.
 */
fun gardensOwned(lifetimePalms: Int): Int = if (lifetimePalms <= 0) 0 else lifetimePalms / GROVE_SIZE

/**
 * Absolute index of the first palm of garden [garden] (0-based). Feeding this + a slot
 * (0 until [GROVE_SIZE]) into [palmParams] reproduces the exact palms that were planted when
 * that grove was grown, so garden N looks the same on every device, forever — and its final
 * palm always bears dates, since `(gardenFirstPalmIndex(garden) + (GROVE_SIZE - 1) + 1) % GROVE_SIZE == 0`.
 */
fun gardenFirstPalmIndex(garden: Int): Int = garden * GROVE_SIZE

/** True on the tasbeeh that has just sent a completed grove receding to the horizon. */
fun completesGrove(count: Int): Boolean = count > 1 && (count - 1) % GROVE_SIZE == 0

/**
 * How many palms sit in each depth row (nearest first) for [shown] visible palms.
 * Rows fill front-first: a palm is planted at the end of the current row, and once that
 * row is full the next palm opens the row behind it. A planted palm never moves again.
 */
fun rowOccupancy(shown: Int): IntArray {
    val take = IntArray(ROW_CAPACITY.size)
    var rem = shown
    for (b in ROW_CAPACITY.indices) {
        val c = min(rem, ROW_CAPACITY[b])
        take[b] = c
        rem -= c
        if (rem <= 0) break
    }
    return take
}

/** Index of the first slot held by row [band] — fixed, because rows fill front-first. */
fun rowStart(band: Int): Int {
    var s = 0
    for (q in 0 until band) s += ROW_CAPACITY[q]
    return s
}

/** The depth row that slot [j] (0-based within the grove) stands in. */
fun rowOfSlot(j: Int): Int {
    var acc = 0
    for (b in ROW_CAPACITY.indices) {
        acc += ROW_CAPACITY[b]
        if (j < acc) return b
    }
    return ROW_CAPACITY.lastIndex
}

/** Where slot [j] sits inside its own row, counting from the row's left edge. */
fun slotInRow(j: Int): Int = j - rowStart(rowOfSlot(j))

/** Depth scale of row [band] — front row is 1, each row back multiplies by the falloff. */
fun rowScale(band: Int): Float = ROW_DEPTH_FALLOFF.pow(band)

/** Atmospheric haze of row [band], capped so the furthest row never fully dissolves. */
fun rowHaze(band: Int): Float = min(HAZE_MAX, 1f - HAZE_FALLOFF.pow(band))

/**
 * Fraction (0..1) of the usable width where the palm at slot [j] stands. Rows are
 * staggered into a quincunx like a real date grove.
 * The renderer maps this to a pixel x via: margin + fraction * (width - 2*margin).
 */
fun palmXFraction(j: Int, jitter: Float): Float {
    val band = rowOfSlot(j)
    val cap = ROW_CAPACITY[band]
    val stagger = (band % 2) * 0.5f
    return (slotInRow(j) + 0.5f + stagger) / cap + jitter / cap * 0.8f
}
