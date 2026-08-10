package tools.mo3ta.dhikrmodel

import java.util.ArrayDeque

internal class ScoreSmoother(private val spec: SmoothingSpec) {
    private var ema: Float? = null
    private val values = ArrayDeque<Float>()

    fun reset() {
        ema = null
        values.clear()
    }

    fun push(raw: Float): Float = when (spec.mode) {
        "none" -> raw
        "ema" -> {
            val next = ema?.let { spec.emaAlpha * raw + (1f - spec.emaAlpha) * it } ?: raw
            ema = next
            next
        }
        "moving_average" -> {
            values.addLast(raw)
            while (values.size > spec.window.coerceAtLeast(1)) values.removeFirst()
            values.sum() / values.size
        }
        else -> throw DhikrModelException("Unsupported smoothing mode '${spec.mode}'")
    }
}

internal data class DetectorPush(val detected: Boolean, val state: DhikrDetectorState)

/** Exact online mirror of `DhikrSpeech/src/streaming.py::EventDetector`. */
internal class EventDetector(
    private val spec: DetectorSpec,
    private val windowSeconds: Double,
) {
    private var state = DhikrDetectorState.IDLE
    private var hits = 0
    private var below = 0
    private var candidateStart = 0.0
    private var cooldownUntil = 0.0

    fun reset() {
        state = DhikrDetectorState.IDLE
        hits = 0
        below = 0
        candidateStart = 0.0
        cooldownUntil = 0.0
    }

    fun push(time: Double, score: Float): DetectorPush {
        if (state == DhikrDetectorState.COOLDOWN) {
            if (time < cooldownUntil - TIME_EPSILON) return DetectorPush(false, state)
            state = DhikrDetectorState.IDLE
        }

        if (state == DhikrDetectorState.IDLE) {
            if (score >= spec.activationThreshold) {
                candidateStart = time
                hits = 1
                below = 0
                state = DhikrDetectorState.CANDIDATE
                return maybeConfirm(time)
            }
            return DetectorPush(false, state)
        }

        if (state == DhikrDetectorState.CANDIDATE) {
            if (score >= spec.activationThreshold) {
                hits += 1
                return maybeConfirm(time)
            }
            if (score >= spec.releaseThreshold) return DetectorPush(false, state)
            state = DhikrDetectorState.IDLE
            hits = 0
            return DetectorPush(false, state)
        }

        if (state == DhikrDetectorState.CONFIRMED) {
            if (score >= spec.releaseThreshold) {
                below = 0
                return DetectorPush(false, state)
            }
            below += 1
            if (below >= spec.releaseWindows) {
                state = DhikrDetectorState.COOLDOWN
                cooldownUntil = time + spec.cooldownMs / 1000.0
                hits = 0
                below = 0
            }
        }
        return DetectorPush(false, state)
    }

    private fun maybeConfirm(time: Double): DetectorPush {
        if (hits < spec.minConsecutiveHits) return DetectorPush(false, state)
        if (time - candidateStart < spec.minEventSeconds - TIME_EPSILON) {
            return DetectorPush(false, state)
        }
        state = DhikrDetectorState.CONFIRMED
        below = 0
        return DetectorPush(true, state)
    }

    private companion object {
        const val TIME_EPSILON = 1e-6
    }
}
