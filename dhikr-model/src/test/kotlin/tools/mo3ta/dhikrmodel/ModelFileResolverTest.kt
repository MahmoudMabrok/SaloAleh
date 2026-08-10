package tools.mo3ta.dhikrmodel

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileResolverTest {
    @Test
    fun recommendedModelIsSelectedWhenInstalled() {
        val model = modelSpec()

        val selected = requireRecommendedModelFile(
            model,
            setOf("dhikr_007_float32.tflite", "dhikr_007_int8.tflite"),
        )

        assertTrue(selected == "dhikr_007_float32.tflite")
    }

    @Test
    fun missingRecommendedFloatDoesNotSilentlyLoadRejectedInt8() {
        val model = modelSpec()

        val error = runCatching {
            requireRecommendedModelFile(model, setOf("dhikr_007_int8.tflite"))
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("float32"))
        assertTrue(error.message.orEmpty().contains("INT8", ignoreCase = true))
        assertTrue(error.message.orEmpty().contains("drift beyond tolerance"))
    }

    private fun modelSpec() =
        ModelSpec(
            file = "dhikr_007_float32.tflite",
            sha256 = "a".repeat(64),
            outputMode = "softmax",
            targetIndex = 1,
            variants = mapOf(
                "float32" to "dhikr_007_float32.tflite",
                "int8" to "dhikr_007_int8.tflite",
            ),
            rejectionReasons = mapOf(
                "int8" to listOf("probabilities drift beyond tolerance"),
            ),
            inputQuantization = null,
            outputQuantization = null,
        )
}
