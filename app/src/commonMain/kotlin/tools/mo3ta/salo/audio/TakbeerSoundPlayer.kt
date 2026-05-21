package tools.mo3ta.salo.audio

/**
 * Plays the short takbeer audio clip used by the group takbeer session — one
 * clip per participant turn.
 */
interface TakbeerSoundPlayer {
    /** Plays the takbeer clip once, suspending until it finishes or playback is cancelled. */
    suspend fun play()

    /** Immediately stops any in-progress playback. */
    fun stop()
}

expect fun createTakbeerSoundPlayer(): TakbeerSoundPlayer
