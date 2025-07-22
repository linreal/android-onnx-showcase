package gos.denver.onnxshowcase.audio.impl

import android.content.Context
import gos.denver.onnxshowcase.audio.AudioConstants
import gos.denver.onnxshowcase.audio.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Build
import android.util.Log
import gos.denver.fft.FFTNative
import java.nio.FloatBuffer

/**
 * Real ONNX Runtime implementation of NoiseSuppressor using DTLN model.
 *
 * Based on DTLN (Dual-signal Transformation LSTM Network) for real-time noise suppression.
 * Uses two-stage processing: frequency domain mask estimation and time domain refinement.
 */
class NoiseSuppressorImpl(private val context: Context) : NoiseSuppressor {

    // ONNX Runtime components
    private var env: OrtEnvironment? = null
    private var model1: OrtSession? = null  // Frequency domain model
    private var model2: OrtSession? = null  // Time domain model

    private var input2Tensor: OnnxTensor? = null
    private var input3Tensor: OnnxTensor? = null
    private var input4Tensor: OnnxTensor? = null
    private var input5Tensor: OnnxTensor? = null
    private var modelOutputs1: OrtSession.Result? = null
    private var modelOutputs2: OrtSession.Result? = null

    private val inBuffer = FloatArray(BLOCK_LEN)
    private val outBuffer = FloatArray(BLOCK_LEN)
    private val inMag = FloatArray(BLOCK_LEN / 2 + 1)
    private val inPhase = FloatArray(BLOCK_LEN / 2 + 1)

     private var fftProcessor: FFTNative? = null

    private var isReady = false

    companion object {
        private const val TAG = "NoiseSuppressorImpl"
        private const val BLOCK_LEN = 512
        private const val BLOCK_SHIFT = 128
        private const val INPUT_2 = "input_2"
        private const val INPUT_3 = "input_3"
        private const val INPUT_4 = "input_4"
        private const val INPUT_5 = "input_5"
        private val INP_SHAPE_1 = longArrayOf(1, 1, 257)
        private val INP_SHAPE_2 = longArrayOf(1, 2, 128, 2)
        private val INP_SHAPE_3 = longArrayOf(1, 1, 512)
    }

    private val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(numThreads)
        setInterOpNumThreads(numThreads)
        setMemoryPatternOptimization(true)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
    }

    override suspend fun initialize(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                env = OrtEnvironment.getEnvironment()

                context.assets.open("dtln1.ort").use { stream ->
                    model1 = env?.createSession(stream.readBytes(), sessionOptions)
                }

                context.assets.open("dtln2.ort").use { stream ->
                    model2 = env?.createSession(stream.readBytes(), sessionOptions)
                }

                input3Tensor = createZeroTensor(INP_SHAPE_2)
                input5Tensor = createZeroTensor(INP_SHAPE_2)

                 fftProcessor = FFTNative(BLOCK_LEN)

                isReady = true
                Log.d(TAG, "DTLN models initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DTLN models", e)
                release()
                throw e
            }
        }
    }

    override fun processChunk(audioChunk: FloatArray): FloatArray {
        if (!isReady) {
            throw IllegalStateException("NoiseSuppressor not initialized")
        }

        val processedChunk = FloatArray(audioChunk.size)
        var inputOffset = 0
        var outputOffset = 0

        try {
            while (inputOffset + BLOCK_SHIFT <= audioChunk.size) {
                val blockOutput = processDTLNBlock(audioChunk, inputOffset)

                // Copy processed block to output
                val copyLength = minOf(blockOutput.size, processedChunk.size - outputOffset)
                System.arraycopy(blockOutput, 0, processedChunk, outputOffset, copyLength)

                inputOffset += BLOCK_SHIFT
                outputOffset += copyLength
            }

            return processedChunk

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio chunk", e)
            throw e
        }
    }

    private fun processDTLNBlock(audioChunk: FloatArray, offset: Int): FloatArray {
        val model1 = requireNotNull(model1) { "Model 1 not initialized" }
        val model2 = requireNotNull(model2) { "Model 2 not initialized" }
        val env = requireNotNull(env) { "ONNX Environment not initialized" }
        val fftProcessor = requireNotNull(fftProcessor) { "first init fftNative before use" }

        try {
            System.arraycopy(inBuffer, BLOCK_SHIFT, inBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            val copyLength = minOf(BLOCK_SHIFT, audioChunk.size - offset)
            System.arraycopy(audioChunk, offset, inBuffer, BLOCK_LEN - BLOCK_SHIFT, copyLength)

             val (magnitude, phase) = fftProcessor.forward(inBuffer)


            System.arraycopy(magnitude, 0, inMag, 0, inMag.size)
            System.arraycopy(phase, 0, inPhase, 0, inPhase.size)

            input2Tensor?.close()
            input2Tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inMag), INP_SHAPE_1)

            modelOutputs1 = model1.run(
                mapOf(
                    INPUT_2 to input2Tensor,
                    INPUT_3 to input3Tensor
                )
            )

            val outMask = (modelOutputs1!![0].value as Array<Array<FloatArray>>)[0][0].copyOf()
            modelOutputs1!![0].close()

            input3Tensor?.close()
            input3Tensor = modelOutputs1!![1] as OnnxTensor

            // Apply mask to magnitude spectrum
            for (i in inMag.indices) {
                inMag[i] *= outMask[i]
            }

             val estimatedBlock = fftProcessor.inverse(inMag, inPhase)

            input4Tensor?.close()
            input4Tensor =
                OnnxTensor.createTensor(env, FloatBuffer.wrap(estimatedBlock), INP_SHAPE_3)

            modelOutputs2 = model2.run(
                mapOf(
                    INPUT_4 to input4Tensor,
                    INPUT_5 to input5Tensor
                )
            )

            val outBlock = (modelOutputs2!![0].value as Array<Array<FloatArray>>)[0][0].copyOf()
            modelOutputs2!![0].close()

            input5Tensor?.close()
            input5Tensor = modelOutputs2!![1] as OnnxTensor

            System.arraycopy(outBuffer, BLOCK_SHIFT, outBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            outBuffer.fill(0f, BLOCK_LEN - BLOCK_SHIFT, BLOCK_LEN)
            for (i in outBuffer.indices) {
                outBuffer[i] += outBlock[i]
            }

            return outBuffer.sliceArray(0 until BLOCK_SHIFT)

        } catch (e: Exception) {
            Log.e(TAG, "Error in DTLN block processing", e)
            throw e
        }
    }

    private fun createZeroTensor(shape: LongArray): OnnxTensor {
        val env = requireNotNull(env) { "ONNX Environment not initialized" }
        val size = shape.reduce { acc, i -> acc * i }.toInt()
        return OnnxTensor.createTensor(env, FloatBuffer.allocate(size), shape)
    }

    override fun release() {
        try {
            modelOutputs1?.forEach { releaseOutput(it) }
            modelOutputs2?.forEach { releaseOutput(it) }

            listOf(
                input2Tensor, input3Tensor, input4Tensor, input5Tensor,
                model1, model2, env
            ).forEach(::releaseResource)

        } catch (e: Exception) {
            Log.e(TAG, "Error during resource cleanup", e)
        } finally {
            // Clear all references
            modelOutputs1 = null
            modelOutputs2 = null
            input2Tensor = null
            input3Tensor = null
            input4Tensor = null
            input5Tensor = null
            model1 = null
            model2 = null
            env = null
            isReady = false
        }
    }

    private fun releaseResource(resource: AutoCloseable?) {
        try {
            when (resource) {
                is OnnxTensor -> if (!resource.isClosed) resource.close()
                is AutoCloseable -> resource.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resource", e)
        }
    }

    private fun releaseOutput(output: MutableMap.MutableEntry<String, ai.onnxruntime.OnnxValue>) {
        try {
            if (!output.value.isClosed) output.value.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing model output", e)
        }
    }

    override fun isInitialized(): Boolean = isReady
}