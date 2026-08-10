package tools.mo3ta.dhikrmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Complete Android runtime for one independent dhikr phrase model.
 *
 * Challenge screens normally call [startListening] with an ID or exact phrase text and collect
 * [events]. Tests and imported recordings can call [load] then [detect] with mono PCM16.
 */
class DhikrModelRunner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val repository = ModelBundleRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(DhikrModelState())
    private val mutableEvents = MutableSharedFlow<DhikrDetection>(extraBufferCapacity = 16)

    val state: StateFlow<DhikrModelState> = mutableState.asStateFlow()
    val events: SharedFlow<DhikrDetection> = mutableEvents.asSharedFlow()

    private var loaded: LoadedModel? = null
    private var recordingJob: Job? = null
    private var recorder: AudioRecord? = null

    suspend fun availablePhrases(): List<DhikrPhrase> = withContext(Dispatchers.IO) {
        repository.listPhrases()
    }

    suspend fun load(phraseIdOrText: String): DhikrPhrase = mutex.withLock {
        ensureOpen()
        stopRecordingLocked()
        if (loaded?.matches(phraseIdOrText) == true) {
            mutableState.value = mutableState.value.copy(status = DhikrRuntimeStatus.READY)
            return@withLock requireNotNull(loaded).bundle.phrase
        }

        closeLoaded()
        mutableState.value = DhikrModelState(
            status = DhikrRuntimeStatus.LOADING,
            requestedPhrase = phraseIdOrText,
        )
        try {
            val bundle = withContext(Dispatchers.IO) { repository.load(phraseIdOrText) }
            val model = withContext(Dispatchers.Default) { LoadedModel(bundle) }
            loaded = model
            mutableState.value = DhikrModelState(
                status = DhikrRuntimeStatus.READY,
                requestedPhrase = phraseIdOrText,
                phrase = bundle.phrase,
            )
            bundle.phrase
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            val wrapped = exception as? DhikrModelException
                ?: DhikrModelException("Could not load phrase model: ${exception.message}", exception)
            mutableState.value = DhikrModelState(
                status = DhikrRuntimeStatus.ERROR,
                requestedPhrase = phraseIdOrText,
                error = wrapped.message,
            )
            throw wrapped
        }
    }

    /** Loads [phraseIdOrText] when needed, then starts continuous microphone detection. */
    suspend fun startListening(phraseIdOrText: String) {
        if (loaded?.matches(phraseIdOrText) != true) load(phraseIdOrText)
        startListening()
    }

    /** Starts listening with the currently loaded model. RECORD_AUDIO must already be granted. */
    @SuppressLint("MissingPermission")
    suspend fun startListening() = mutex.withLock {
        ensureOpen()
        if (recordingJob?.isActive == true) return@withLock
        val model = loaded ?: throw DhikrModelException("Load a phrase model before listening")
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw DhikrModelException("Microphone permission is required")
        }

        model.reset()
        val minimum = AudioRecord.getMinBufferSize(
            model.bundle.audio.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minimum > 0) { "This device cannot record ${model.bundle.audio.sampleRate} Hz mono audio" }
        val bufferBytes = maxOf(minimum, model.frontend.hopSamples * 2 * 4)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            model.bundle.audio.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw DhikrModelException("Could not initialize the microphone")
        }
        if (audioRecord.sampleRate != model.bundle.audio.sampleRate) {
            audioRecord.release()
            throw DhikrModelException(
                "Microphone opened at ${audioRecord.sampleRate} Hz; model requires ${model.bundle.audio.sampleRate} Hz",
            )
        }
        try {
            audioRecord.startRecording()
        } catch (exception: Exception) {
            audioRecord.release()
            mutableState.value = mutableState.value.copy(
                status = DhikrRuntimeStatus.ERROR,
                error = "Could not start the microphone: ${exception.message}",
            )
            throw DhikrModelException("Could not start the microphone", exception)
        }
        recorder = audioRecord
        recordingJob = scope.launch(Dispatchers.IO) { captureLoop(audioRecord, model) }
        mutableState.value = mutableState.value.copy(
            status = DhikrRuntimeStatus.LISTENING,
            detectorState = DhikrDetectorState.IDLE,
            rawScore = 0f,
            score = 0f,
            count = 0,
            error = null,
        )
    }

    suspend fun stopListening() = mutex.withLock {
        stopRecordingLocked()
        mutableState.value = loaded?.let {
            mutableState.value.copy(status = DhikrRuntimeStatus.READY)
        } ?: DhikrModelState()
    }

    /** Scores one window of mono PCM16 using the loaded phrase's sample rate. */
    suspend fun detect(samples: ShortArray): DhikrDetection = mutex.withLock {
        ensureOpen()
        val model = loaded ?: throw DhikrModelException("Load a phrase model before calling detect")
        check(recordingJob?.isActive != true) {
            "detect(samples) cannot run concurrently with microphone listening"
        }
        withContext(Dispatchers.Default) { detectLocked(model, samples) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runBlocking {
            mutex.withLock {
                stopRecordingLocked()
                closeLoaded()
            }
        }
        scope.coroutineContext[Job]?.cancel()
        mutableState.value = DhikrModelState()
    }

    private suspend fun captureLoop(audioRecord: AudioRecord, model: LoadedModel) {
        val window = ShortArray(model.frontend.windowSamples)
        val hop = ShortArray(model.frontend.hopSamples)
        try {
            readFully(audioRecord, window)
            detectLocked(model, window)
            while (currentCoroutineContext().isActive) {
                readFully(audioRecord, hop)
                window.copyInto(window, startIndex = hop.size, endIndex = window.size)
                hop.copyInto(window, destinationOffset = window.size - hop.size)
                detectLocked(model, window)
            }
        } catch (_: CancellationException) {
            // Normal stop/close path.
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                status = DhikrRuntimeStatus.ERROR,
                error = "Listening stopped: ${exception.message}",
            )
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            if (recorder === audioRecord) recorder = null
        }
    }

    private fun readFully(audioRecord: AudioRecord, destination: ShortArray) {
        var offset = 0
        while (offset < destination.size) {
            val read = audioRecord.read(
                destination,
                offset,
                destination.size - offset,
                AudioRecord.READ_BLOCKING,
            )
            if (read <= 0) throw DhikrModelException("Microphone read failed ($read)")
            offset += read
        }
    }

    private fun detectLocked(model: LoadedModel, samples: ShortArray): DhikrDetection {
        val features = model.frontend.extract(samples)
        val raw = model.session.score(features)
        val smoothed = model.smoother.push(raw)
        val index = model.windowIndex++
        val time = index * model.bundle.audio.hopSeconds
        val pushed = model.detector.push(time, smoothed)
        if (pushed.detected) model.count += 1
        val result = DhikrDetection(
            phrase = model.bundle.phrase,
            detected = pushed.detected,
            rawScore = raw,
            score = smoothed,
            detectorState = pushed.state,
            count = model.count,
            windowIndex = index,
            windowStartSeconds = time,
        )
        mutableState.value = mutableState.value.copy(
            status = if (recordingJob?.isActive == true) {
                DhikrRuntimeStatus.LISTENING
            } else {
                DhikrRuntimeStatus.READY
            },
            detectorState = pushed.state,
            rawScore = raw,
            score = smoothed,
            count = model.count,
            error = null,
        )
        if (pushed.detected) mutableEvents.tryEmit(result)
        return result
    }

    private suspend fun stopRecordingLocked() {
        val job = recordingJob
        recordingJob = null
        job?.cancel()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        job?.cancelAndJoin()
    }

    private fun closeLoaded() {
        loaded?.close()
        loaded = null
    }

    private fun ensureOpen() {
        check(!closed.get()) { "DhikrModelRunner is closed" }
    }

    private class LoadedModel(val bundle: ModelBundle) : AutoCloseable {
        val frontend = LogMelFrontend(bundle)
        val session = LiteRtSession(bundle)
        val smoother = ScoreSmoother(bundle.detector.smoothing)
        val detector = EventDetector(bundle.detector, bundle.audio.clipSamples.toDouble() / bundle.audio.sampleRate)
        var count = 0
        var windowIndex = 0L

        fun matches(query: String): Boolean {
            val normalized = query.trim()
            return bundle.phrase.id == normalized.toIntOrNull()?.toString()?.padStart(3, '0') ||
                bundle.phrase.id.equals(normalized, ignoreCase = true) ||
                bundle.phrase.text.equals(normalized, ignoreCase = true)
        }

        fun reset() {
            smoother.reset()
            detector.reset()
            count = 0
            windowIndex = 0L
        }

        override fun close() = session.close()
    }
}
