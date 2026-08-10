package tools.mo3ta.dhikrmodel

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round

internal class LiteRtSession(private val bundle: ModelBundle) : AutoCloseable {
    private val modelBuffer = ByteBuffer.allocateDirect(bundle.modelBytes.size)
        .order(ByteOrder.nativeOrder())
        .put(bundle.modelBytes)
        .also { it.rewind() }
    private val interpreter = Interpreter(
        modelBuffer,
        createInterpreterOptions(),
    )
    private val inputTensor = interpreter.getInputTensor(0)
    private val outputTensor = interpreter.getOutputTensor(0)
    private val inputType = inputTensor.dataType()
    private val outputType = outputTensor.dataType()
    private val inputElements = inputTensor.shape().fold(1, Int::times)
    private val outputElements = outputTensor.shape().fold(1, Int::times)
    private val inputQuantization = inputTensor.quantizationParams()
    private val outputQuantization = outputTensor.quantizationParams()
    private val input = ByteBuffer.allocateDirect(inputElements * inputType.byteSize())
        .order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outputElements * outputType.byteSize())
        .order(ByteOrder.nativeOrder())

    init {
        val expected = intArrayOf(1, *bundle.feature.inputShape)
        require(inputTensor.shape().contentEquals(expected)) {
            "Model input shape ${inputTensor.shape().contentToString()} does not match metadata " +
                expected.contentToString()
        }
        require(inputType == DataType.FLOAT32 || inputType == DataType.INT8) {
            "Only FLOAT32 and INT8 model inputs are supported, got $inputType"
        }
        require(outputType == DataType.FLOAT32 || outputType == DataType.INT8) {
            "Only FLOAT32 and INT8 model outputs are supported, got $outputType"
        }
        val expectedOutputs = if (bundle.model.outputMode == "sigmoid") 1 else 2
        require(outputElements == expectedOutputs) {
            "${bundle.model.outputMode} metadata expects $expectedOutputs outputs, model has $outputElements"
        }
        checkQuantization("input", inputType, inputQuantization.scale, inputQuantization.zeroPoint,
            bundle.model.inputQuantization)
        checkQuantization("output", outputType, outputQuantization.scale, outputQuantization.zeroPoint,
            bundle.model.outputQuantization)
    }

    fun score(features: FloatArray): Float {
        require(features.size == inputElements)
        input.clear()
        when (inputType) {
            DataType.FLOAT32 -> features.forEach(input::putFloat)
            DataType.INT8 -> {
                require(inputQuantization.scale > 0f) { "INT8 input tensor has no quantization scale" }
                for (value in features) {
                    val quantized = round(value / inputQuantization.scale).toInt() +
                        inputQuantization.zeroPoint
                    input.put(quantized.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte())
                }
            }
            else -> error("validated in init")
        }
        input.rewind()
        output.clear()
        interpreter.run(input, output)
        output.rewind()

        val probabilities = FloatArray(outputElements)
        when (outputType) {
            DataType.FLOAT32 -> for (index in probabilities.indices) probabilities[index] = output.float
            DataType.INT8 -> {
                require(outputQuantization.scale > 0f) { "INT8 output tensor has no quantization scale" }
                for (index in probabilities.indices) {
                    probabilities[index] =
                        (output.get().toInt() - outputQuantization.zeroPoint) * outputQuantization.scale
                }
            }
            else -> error("validated in init")
        }
        return probabilities[bundle.model.targetIndex]
    }

    override fun close() {
        interpreter.close()
    }

    private fun checkQuantization(
        label: String,
        type: DataType,
        runtimeScale: Float,
        runtimeZeroPoint: Int,
        exported: QuantizationSpec?,
    ) {
        if (type != DataType.INT8 || exported == null) return
        require(kotlin.math.abs(runtimeScale - exported.scale) <= 1e-7f &&
            runtimeZeroPoint == exported.zeroPoint
        ) { "$label tensor quantization does not match model_metadata.json" }
    }
}

/**
 * XNNPACK is deliberately disabled here. Some Android virtual CPUs advertise an ARM64 ABI while
 * rejecting an instruction selected by XNNPACK, which terminates the process with SIGILL before
 * Kotlin can catch an error. The portable CPU kernels are fast enough for this small phrase model
 * and make model loading reliable across physical devices and emulators.
 */
internal fun createInterpreterOptions(
    availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
): Interpreter.Options = Interpreter.Options()
    .setNumThreads(availableProcessors.coerceIn(1, 4))
    .setUseXNNPACK(false)
