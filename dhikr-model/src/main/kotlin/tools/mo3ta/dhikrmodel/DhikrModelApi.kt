package tools.mo3ta.dhikrmodel

/** A phrase model discovered in `assets/dhikr_models/<zero-padded id>/`. */
data class DhikrPhrase(
    val id: String,
    val text: String,
    val modelVersion: String,
)

enum class DhikrRuntimeStatus {
    IDLE,
    LOADING,
    READY,
    LISTENING,
    ERROR,
}

enum class DhikrDetectorState {
    IDLE,
    CANDIDATE,
    CONFIRMED,
    COOLDOWN,
}

data class DhikrModelState(
    val status: DhikrRuntimeStatus = DhikrRuntimeStatus.IDLE,
    val requestedPhrase: String? = null,
    val phrase: DhikrPhrase? = null,
    val detectorState: DhikrDetectorState = DhikrDetectorState.IDLE,
    val rawScore: Float = 0f,
    val score: Float = 0f,
    val count: Int = 0,
    val error: String? = null,
)

/** Result of scoring one model window. `detected` is true only on event confirmation. */
data class DhikrDetection(
    val phrase: DhikrPhrase,
    val detected: Boolean,
    val rawScore: Float,
    val score: Float,
    val detectorState: DhikrDetectorState,
    val count: Int,
    val windowIndex: Long,
    val windowStartSeconds: Double,
)

class DhikrModelException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
