package tools.mo3ta.dhikrmodel

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal const val MODEL_ASSET_ROOT = "dhikr_models"

internal data class QuantizationSpec(
    val scale: Float,
    val zeroPoint: Int,
)

internal data class FeatureSpec(
    val nMels: Int,
    val nFft: Int,
    val winLength: Int,
    val hopLength: Int,
    val logOffset: Float,
    val normalization: String,
    val inputShape: IntArray,
    val globalMean: Float?,
    val globalStd: Float?,
)

internal data class AudioSpec(
    val sampleRate: Int,
    val clipSamples: Int,
    val hopSeconds: Double,
    val normalize: Boolean,
    val targetDbfs: Double,
    val peakCeiling: Double,
    val fitMode: String,
)

internal data class SmoothingSpec(
    val mode: String,
    val emaAlpha: Float,
    val window: Int,
)

internal data class DetectorSpec(
    val activationThreshold: Float,
    val releaseThreshold: Float,
    val minConsecutiveHits: Int,
    val minEventSeconds: Double,
    val releaseWindows: Int,
    val cooldownMs: Double,
    val smoothing: SmoothingSpec,
)

internal data class ModelSpec(
    val file: String,
    val sha256: String,
    val outputMode: String,
    val targetIndex: Int,
    val variants: Map<String, String>,
    val rejectionReasons: Map<String, List<String>>,
    val inputQuantization: QuantizationSpec?,
    val outputQuantization: QuantizationSpec?,
)

internal data class ModelBundle(
    val assetDirectory: String,
    val phrase: DhikrPhrase,
    val audio: AudioSpec,
    val feature: FeatureSpec,
    val detector: DetectorSpec,
    val model: ModelSpec,
    val melFilterbank: Array<FloatArray>,
    val modelBytes: ByteArray,
)

internal class ModelBundleRepository(private val context: Context) {
    suspend fun listPhrases(): List<DhikrPhrase> = directories().mapNotNull { directory ->
        runCatching { parseMetadata(readText("$MODEL_ASSET_ROOT/$directory/model_metadata.json")).phrase }
            .getOrNull()
    }.sortedBy { it.id }

    suspend fun load(query: String): ModelBundle {
        val requested = query.trim()
        require(requested.isNotEmpty()) { "Phrase ID or text is required" }

        val numericId = requested.toIntOrNull()?.let { it.toString().padStart(3, '0') }
        val candidates = if (numericId != null) {
            listOf(numericId)
        } else {
            directories()
        }

        for (directory in candidates) {
            val metadataPath = "$MODEL_ASSET_ROOT/$directory/model_metadata.json"
            val metadataText = runCatching { readText(metadataPath) }.getOrNull() ?: continue
            val partial = parseMetadata(metadataText)
            require(partial.phrase.id == directory) {
                "Bundle folder '$directory' contains metadata for phrase '${partial.phrase.id}'"
            }
            if (numericId == null &&
                !partial.phrase.text.equals(requested, ignoreCase = true) &&
                !partial.phrase.id.equals(requested, ignoreCase = true)
            ) continue

            val availableFiles = context.assets.list("$MODEL_ASSET_ROOT/$directory")
                ?.toSet().orEmpty()
            val selectedFile = requireRecommendedModelFile(partial.model, availableFiles)
            val modelPath = "$MODEL_ASSET_ROOT/$directory/$selectedFile"
            val modelBytes = readBytes(modelPath)
            verifySha256(modelBytes, partial.model.sha256, modelPath)
            val filterbank = parseFilterbank(
                readText("$MODEL_ASSET_ROOT/$directory/mel_filterbank.json"),
                partial.feature,
            )
            return partial.copy(
                assetDirectory = "$MODEL_ASSET_ROOT/$directory",
                melFilterbank = filterbank,
                modelBytes = modelBytes,
            )
        }

        val known = listPhrases().map { "${it.id} (${it.text})" }
        val suffix = if (known.isEmpty()) {
            "No model bundles are installed under assets/$MODEL_ASSET_ROOT."
        } else {
            "Installed phrases: ${known.joinToString()}."
        }
        throw DhikrModelException("No model bundle matches '$requested'. $suffix")
    }

    private fun directories(): List<String> =
        context.assets.list(MODEL_ASSET_ROOT)?.toList().orEmpty()
            .filter { it.toIntOrNull() != null }

    private fun readText(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun readBytes(path: String): ByteArray = context.assets.open(path).use { input ->
        val output = ByteArrayOutputStream()
        input.copyTo(output)
        output.toByteArray()
    }

    private fun parseMetadata(text: String): ModelBundle {
        val root = JSONObject(text)
        val phraseId = root.getString("target_phrase_id")
        val feature = root.getJSONObject("feature")
        val frontend = root.optJSONObject("frontend")
        val audio = root.getJSONObject("audio_conditioning")
        val detection = root.getJSONObject("detection")
        val smoothing = detection.getJSONObject("smoothing")
        val model = root.getJSONObject("model")
        val tensors = root.optJSONObject("tensors")
        val stats = feature.optJSONObject("stats") ?: frontend?.optJSONObject("stats")
        val shapeJson = feature.getJSONArray("input_shape")
        val inputShape = IntArray(shapeJson.length()) { shapeJson.getInt(it) }

        require(phraseId.matches(Regex("\\d{3}"))) {
            "target_phrase_id must be a zero-padded numeric ID, got '$phraseId'"
        }
        require(feature.optBoolean("center", false).not()) {
            "Centered STFT is not supported by the Android streaming contract"
        }
        require(audio.getInt("channels") == 1) { "Only mono model bundles are supported" }

        return ModelBundle(
            assetDirectory = "",
            phrase = DhikrPhrase(
                id = phraseId,
                text = root.getString("target_phrase_text"),
                modelVersion = root.optString("model_version", "1"),
            ),
            audio = AudioSpec(
                sampleRate = root.getInt("sample_rate"),
                clipSamples = root.getInt("clip_samples"),
                hopSeconds = root.getDouble("hop_seconds"),
                normalize = audio.getBoolean("loudness_normalize"),
                targetDbfs = audio.getDouble("target_dbfs"),
                peakCeiling = audio.getDouble("peak_ceiling"),
                fitMode = audio.optString("fit_mode", "center"),
            ),
            feature = FeatureSpec(
                nMels = feature.getInt("n_mels"),
                nFft = feature.getInt("n_fft"),
                winLength = feature.optInt(
                    "win_length",
                    frontend?.optInt("win_length") ?: 0,
                ),
                hopLength = feature.optInt(
                    "hop_length",
                    frontend?.optInt("hop_length") ?: 0,
                ),
                logOffset = feature.getDouble("log_offset").toFloat(),
                normalization = feature.getString("normalization"),
                inputShape = inputShape,
                globalMean = stats?.optDouble("mean")?.toFloat(),
                globalStd = stats?.optDouble("std")?.toFloat(),
            ),
            detector = DetectorSpec(
                activationThreshold = detection.getDouble("activation_threshold").toFloat(),
                releaseThreshold = detection.getDouble("release_threshold").toFloat(),
                minConsecutiveHits = detection.getInt("min_consecutive_hits"),
                minEventSeconds = detection.getDouble("min_event_seconds"),
                releaseWindows = detection.getInt("release_windows"),
                cooldownMs = detection.getDouble("cooldown_ms"),
                smoothing = SmoothingSpec(
                    mode = smoothing.getString("mode"),
                    emaAlpha = smoothing.getDouble("ema_alpha").toFloat(),
                    window = smoothing.getInt("window"),
                ),
            ),
            model = ModelSpec(
                file = model.getString("file"),
                sha256 = model.getString("sha256"),
                outputMode = root.getString("output_mode"),
                targetIndex = root.getInt("target_index"),
                variants = parseVariants(model.optJSONObject("variants")),
                rejectionReasons = parseRejectionReasons(root.optJSONObject("quantization")),
                inputQuantization = parseQuantization(tensors?.optJSONObject("input")),
                outputQuantization = parseQuantization(tensors?.optJSONObject("output")),
            ),
            melFilterbank = emptyArray(),
            modelBytes = byteArrayOf(),
        ).also(::validateMetadata)
    }

    private fun validateMetadata(bundle: ModelBundle) {
        val feature = bundle.feature
        require(bundle.audio.sampleRate > 0 && bundle.audio.clipSamples > 0)
        require(bundle.audio.hopSeconds > 0.0)
        require((bundle.audio.hopSeconds * bundle.audio.sampleRate).toInt() in 1..bundle.audio.clipSamples) {
            "Streaming hop must not be longer than the model window"
        }
        require(feature.nFft > 0 && feature.nFft and (feature.nFft - 1) == 0) {
            "n_fft must be a power of two"
        }
        require(feature.winLength in 1..feature.nFft) { "Invalid win_length" }
        require(feature.hopLength > 0) { "Invalid feature hop_length" }
        require(feature.inputShape.contentEquals(intArrayOf(
            1 + (bundle.audio.clipSamples - feature.nFft) / feature.hopLength,
            feature.nMels,
            1,
        ))) { "Metadata input_shape does not match the exported audio/frontend geometry" }
        require(feature.normalization in setOf("none", "per_example", "global"))
        if (feature.normalization == "global") {
            require(feature.globalMean?.isFinite() == true &&
                feature.globalStd?.isFinite() == true && feature.globalStd > 0f
            ) {
                "Global feature normalization requires mean/std in metadata"
            }
        }
        require(bundle.model.outputMode in setOf("softmax", "sigmoid"))
        require(bundle.model.file.endsWith(".tflite") && '/' !in bundle.model.file && '\\' !in bundle.model.file) {
            "Model metadata must name one .tflite file in the phrase bundle"
        }
        require(bundle.detector.releaseThreshold <= bundle.detector.activationThreshold)
        require(bundle.detector.minConsecutiveHits > 0 && bundle.detector.releaseWindows > 0)
        require(bundle.detector.smoothing.mode in setOf("none", "ema", "moving_average"))
    }

    private fun parseQuantization(tensor: JSONObject?): QuantizationSpec? {
        val quantization = tensor?.optJSONObject("quantization") ?: return null
        return QuantizationSpec(
            scale = quantization.optDouble("scale", 0.0).toFloat(),
            zeroPoint = quantization.optInt("zero_point", 0),
        )
    }

    private fun parseVariants(variants: JSONObject?): Map<String, String> {
        if (variants == null) return emptyMap()
        return variants.keys().asSequence().associateWith(variants::getString)
    }

    private fun parseRejectionReasons(quantization: JSONObject?): Map<String, List<String>> {
        val variants = quantization?.optJSONArray("variants") ?: return emptyMap()
        return buildMap {
            for (index in 0 until variants.length()) {
                val item = variants.getJSONObject(index)
                if (item.optBoolean("accepted", true)) continue
                val reasonsJson = item.optJSONArray("reasons")
                val reasons = if (reasonsJson == null) {
                    emptyList()
                } else {
                    List(reasonsJson.length()) { reasonsJson.getString(it) }
                }
                put(item.getString("variant"), reasons)
            }
        }
    }

    private fun parseFilterbank(text: String, feature: FeatureSpec): Array<FloatArray> {
        val root = JSONObject(text)
        val shape = root.getJSONArray("shape")
        require(shape.getInt(0) == feature.nMels && shape.getInt(1) == feature.nFft / 2 + 1) {
            "mel_filterbank.json shape does not match model metadata"
        }
        val filters = root.getJSONArray("filters")
        return Array(feature.nMels) { mel ->
            val row = filters.getJSONArray(mel)
            FloatArray(feature.nFft / 2 + 1) { bin -> row.getDouble(bin).toFloat() }
        }
    }

    private fun verifySha256(bytes: ByteArray, expected: String, path: String) {
        require(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 in model metadata" }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw DhikrModelException("Model fingerprint mismatch for $path")
        }
    }
}
