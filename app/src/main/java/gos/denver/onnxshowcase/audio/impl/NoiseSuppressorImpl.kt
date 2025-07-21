package gos.denver.onnxshowcase.audio.impl

import gos.denver.onnxshowcase.audio.AudioConstants
import gos.denver.onnxshowcase.audio.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Fake implementation of NoiseSuppressor that simulates DTLN model behavior.
 *
 * This implementation applies simple audio transformations to simulate denoising
 * and maintains internal state to mimic temporal coherence requirements.
 */
class NoiseSuppressorImpl : NoiseSuppressor {
    private var isReady = false

    // Simulate DTLN's internal state for temporal coherence
    private var previousChunk: FloatArray? = null
    private var stateBuffer = FloatArray(AudioConstants.CHUNK_SIZE) { 0f }

    // Simple filter parameters for fake processing
    private val noiseThreshold = 0.1f
    private val smoothingFactor = 0.7f

    override suspend fun initialize(modelPath: String) {
        withContext(Dispatchers.IO) {
            // Simulate model loading time
            delay(500)

            // Initialize internal buffers
            previousChunk = FloatArray(AudioConstants.CHUNK_SIZE) { 0f }
            stateBuffer.fill(0f)

            isReady = true
        }
    }

    override fun processChunk(audioChunk: FloatArray): FloatArray {
        if (!isReady) {
            throw IllegalStateException("NoiseSuppressor not initialized")
        }

        if (audioChunk.size != AudioConstants.CHUNK_SIZE) {
            throw IllegalArgumentException("Chunk size must be ${AudioConstants.CHUNK_SIZE}")
        }

        val processedChunk = FloatArray(AudioConstants.CHUNK_SIZE)

        for (i in audioChunk.indices) {
            // Apply fake noise suppression algorithm
            val currentSample = audioChunk[i]
            val previousSample = previousChunk?.get(i) ?: 0f
            val stateSample = stateBuffer[i]

            // Simple noise gate with temporal smoothing
            val magnitude = abs(currentSample)
            val smoothedMagnitude = magnitude * (1 - smoothingFactor) +
                    abs(previousSample) * smoothingFactor

            processedChunk[i] = if (smoothedMagnitude > noiseThreshold) {
                // Keep signal, apply light smoothing with previous state
                currentSample * 0.8f + stateSample * 0.2f
            } else {
                // Suppress noise, gradually fade to silence
                currentSample * 0.3f + stateSample * 0.1f
            }

            // Update state buffer for next chunk
            stateBuffer[i] = processedChunk[i] * 0.5f + stateSample * 0.5f
        }

        // Store current chunk as previous for next iteration
        previousChunk = audioChunk.copyOf()

        return processedChunk
    }

    override fun release() {
        isReady = false
        previousChunk = null
        stateBuffer.fill(0f)
    }

    override fun isInitialized(): Boolean = isReady
}