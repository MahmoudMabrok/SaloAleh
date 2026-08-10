package tools.mo3ta.dhikrmodel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Kotlin port of `DhikrSpeech/src/features.py`; all geometry comes from export metadata. */
internal class LogMelFrontend(private val bundle: ModelBundle) {
    private val audio = bundle.audio
    private val feature = bundle.feature
    private val filterbank = bundle.melFilterbank
    private val fft = RealFft(feature.nFft)
    private val paddedWindow = FloatArray(feature.nFft).also { values ->
        val offset = (feature.nFft - feature.winLength) / 2
        for (index in 0 until feature.winLength) {
            // librosa's `hann` with fftbins=true is a periodic Hann window.
            values[offset + index] =
                (0.5 - 0.5 * cos(2.0 * PI * index / feature.winLength)).toFloat()
        }
    }

    val windowSamples: Int get() = audio.clipSamples
    val hopSamples: Int = max((audio.hopSeconds * audio.sampleRate).toInt(), 1)

    fun extract(pcm16: ShortArray): FloatArray {
        val samples = condition(pcm16)
        val frameCount = feature.inputShape[0]
        val result = FloatArray(frameCount * feature.nMels)
        val frame = FloatArray(feature.nFft)
        val power = FloatArray(feature.nFft / 2 + 1)

        for (frameIndex in 0 until frameCount) {
            val sampleOffset = frameIndex * feature.hopLength
            for (index in frame.indices) {
                frame[index] = samples[sampleOffset + index] * paddedWindow[index]
            }
            fft.powerSpectrum(frame, power)
            for (mel in 0 until feature.nMels) {
                var energy = 0.0
                val filter = filterbank[mel]
                for (bin in power.indices) energy += filter[bin] * power[bin]
                result[frameIndex * feature.nMels + mel] =
                    ln(energy + feature.logOffset).toFloat()
            }
        }
        normalizeFeatures(result)
        return result
    }

    private fun condition(pcm16: ShortArray): FloatArray {
        val fitted = fitLength(pcm16)
        val result = FloatArray(fitted.size) { fitted[it] / 32768f }
        if (!audio.normalize) return result

        var squared = 0.0
        var peak = 0.0
        for (sample in result) {
            squared += sample * sample
            peak = max(peak, kotlin.math.abs(sample.toDouble()))
        }
        val rms = sqrt(squared / result.size)
        if (rms <= 1e-12) return result
        val currentDbfs = 20.0 * kotlin.math.log10(rms)
        var gain = 10.0.pow((audio.targetDbfs - currentDbfs) / 20.0)
        if (peak > 0.0) gain = kotlin.math.min(gain, audio.peakCeiling / peak)
        for (index in result.indices) result[index] = (result[index] * gain).toFloat()
        return result
    }

    private fun fitLength(source: ShortArray): ShortArray {
        if (source.size == audio.clipSamples) return source
        val result = ShortArray(audio.clipSamples)
        if (source.size > audio.clipSamples) {
            val start = if (audio.fitMode == "center") {
                (source.size - audio.clipSamples) / 2
            } else {
                0
            }
            source.copyInto(result, startIndex = start, endIndex = start + audio.clipSamples)
        } else {
            val start = if (audio.fitMode == "center") {
                (audio.clipSamples - source.size) / 2
            } else {
                0
            }
            source.copyInto(result, destinationOffset = start)
        }
        return result
    }

    private fun normalizeFeatures(values: FloatArray) {
        when (feature.normalization) {
            "none" -> Unit
            "per_example" -> {
                val mean = values.fold(0.0) { sum, value -> sum + value } / values.size
                val variance = values.fold(0.0) { sum, value ->
                    val delta = value - mean
                    sum + delta * delta
                } / values.size
                val std = sqrt(variance)
                for (index in values.indices) {
                    values[index] = ((values[index] - mean) / (std + 1e-8)).toFloat()
                }
            }
            "global" -> {
                val mean = requireNotNull(feature.globalMean)
                val std = requireNotNull(feature.globalStd)
                for (index in values.indices) {
                    values[index] = (values[index] - mean) / (std + 1e-8f)
                }
            }
        }
    }
}

/** Allocation-free radix-2 FFT; output is the squared magnitude of bins `[0, n/2]`. */
internal class RealFft(private val size: Int) {
    private val real = DoubleArray(size)
    private val imaginary = DoubleArray(size)

    init {
        require(size > 0 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    fun powerSpectrum(input: FloatArray, output: FloatArray) {
        require(input.size == size)
        require(output.size == size / 2 + 1)
        for (index in 0 until size) {
            real[index] = input[index].toDouble()
            imaginary[index] = 0.0
        }

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realSwap = real[i]
                real[i] = real[j]
                real[j] = realSwap
                val imaginarySwap = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginarySwap
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val rootReal = cos(angle)
            val rootImaginary = kotlin.math.sin(angle)
            var start = 0
            while (start < size) {
                var weightReal = 1.0
                var weightImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * weightReal - imaginary[odd] * weightImaginary
                    val oddImaginary = real[odd] * weightImaginary + imaginary[odd] * weightReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextWeightReal = weightReal * rootReal - weightImaginary * rootImaginary
                    weightImaginary = weightReal * rootImaginary + weightImaginary * rootReal
                    weightReal = nextWeightReal
                }
                start += length
            }
            length = length shl 1
        }

        for (bin in output.indices) {
            output[bin] = (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]).toFloat()
        }
    }
}
