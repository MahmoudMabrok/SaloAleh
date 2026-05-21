package tools.mo3ta.salo.presentation

enum class TakbeerPhase { SETUP, RUNNING }

data class TakbeerSessionUiState(
    val phase: TakbeerPhase = TakbeerPhase.SETUP,
    val peopleCountInput: String = DEFAULT_PEOPLE.toString(),
    val peopleCount: Int = DEFAULT_PEOPLE,
    val currentTurn: Int = -1,
    val roundsCompleted: Int = 0,
    val awaitingUser: Boolean = false,
) {
    /** Parsed participant count, or null when the input is outside the allowed range. */
    val validCount: Int?
        get() = peopleCountInput.toIntOrNull()?.takeIf { it in MIN_PEOPLE..MAX_PEOPLE }

    val canStart: Boolean get() = validCount != null

    /** The user always occupies the last position in the ring. */
    val userIndex: Int get() = peopleCount - 1

    val isUserTurn: Boolean get() = phase == TakbeerPhase.RUNNING && currentTurn == userIndex

    companion object {
        const val MIN_PEOPLE = 2
        const val MAX_PEOPLE = 10
        const val DEFAULT_PEOPLE = 5
    }
}
