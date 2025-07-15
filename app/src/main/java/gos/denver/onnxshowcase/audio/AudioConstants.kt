package gos.denver.onnxshowcase.audio

/**
 * Central location for audio processing parameters.
 *
 * All constants are configured specifically for DTLN model requirements
 * and optimal Android audio processing.
 */
object AudioConstants {
    /** Audio sample rate in Hz (DTLN requirement) */
    const val SAMPLE_RATE = 16000

    /** Number of channels (mono for DTLN) */
    const val CHANNEL_COUNT = 1

    /** Audio bit depth */
    const val BITS_PER_SAMPLE = 16

    /** Processing chunk size in samples (DTLN frame size) */
    const val CHUNK_SIZE = 512

    /** ONNX model filename */
    const val MODEL_FILE_NAME = "dtln_model.onnx" //todo linreal
}