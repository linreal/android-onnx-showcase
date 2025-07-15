package gos.denver.onnxshowcase.audio

/**
 * Manages ONNX Runtime and DTLN model for real-time noise suppression.
 *
 * This interface encapsulates all ONNX Runtime operations and maintains
 * the internal state required by the DTLN model for temporal coherence.
 *
 * Thread safety: Should not be accessed concurrently as it maintains
 * internal state between processing calls.
 */
interface NoiseSuppressor {
    /**
     * Loads and initializes the DTLN ONNX model.
     *
     * Creates ONNX Runtime session, allocates necessary buffers for processing,
     * and initializes internal state for DTLN model.
     *
     * @param modelPath Path to the .onnx model file
     * @throws OrtException if model loading fails
     */
    suspend fun initialize(modelPath: String)

    /**
     * Processes a single audio chunk through the DTLN model.
     *
     * Maintains internal state between calls for temporal coherence.
     * Chunk size must match DTLN requirements (512 samples).
     *
     * @param audioChunk Input audio samples normalized to [-1, 1]
     * @return Denoised audio samples
     */
    fun processChunk(audioChunk: FloatArray): FloatArray

    /**
     * Releases all ONNX Runtime resources.
     *
     * Frees model memory, closes ONNX session, and clears internal buffers.
     */
    fun release()

    /**
     * Checks if the model is ready for processing.
     *
     * @return True if model is loaded and ready
     */
    fun isInitialized(): Boolean
}
