package tools.mo3ta.salo.data.time

internal data class FinalMinutesTick(
    val shouldFlush: Boolean,
    val showNewRound: Boolean,
    val nextDelayMillis: Long,
)

internal fun computeFinalMinutesTick(remainingSeconds: Long): FinalMinutesTick {
    if (remainingSeconds <= 0) {
        return FinalMinutesTick(shouldFlush = true, showNewRound = true, nextDelayMillis = 0)
    }
    if (remainingSeconds <= 600) {
        return FinalMinutesTick(shouldFlush = true, showNewRound = false, nextDelayMillis = 60_000L)
    }
    val msUntilFinalMinutes = (remainingSeconds - 600) * 1000
    return FinalMinutesTick(
        shouldFlush = false,
        showNewRound = false,
        nextDelayMillis = msUntilFinalMinutes.coerceAtMost(60_000L),
    )
}
