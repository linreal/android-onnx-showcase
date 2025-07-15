package gos.denver.onnxshowcase.audio

import java.io.File

/**
 * Coordinates real-time recording and processing pipeline.
 *
 * This interface manages the concurrent execution of audio recording
 * and ONNX-based noise suppression, handling all necessary audio
 * format conversions and buffering.
 */
interface ConcurrentAudioProcessor {
    /**
     * Initiates concurrent recording and denoising pipeline.
     *
     * Manages concurrent coroutines for recording and processing,
     * handles audio format conversion between components.
     *
     * @param recorder Audio capture component
     * @param suppressor ONNX denoising component
     * @param rawOutputFile Destination for raw audio
     * @param processedOutputFile Destination for denoised audio
     */
    suspend fun startProcessing(
        recorder: AudioRecorder,
        suppressor: NoiseSuppressor,
        rawOutputFile: File,
        processedOutputFile: File
    )

    /**
     * Terminates processing and returns results.
     *
     * Ensures all buffers are processed and finalizes both output files.
     *
     * @return ProcessingResult with file paths and statistics
     */
    suspend fun stopProcessing(): ProcessingResult

    /**
     * Results from audio processing operation.
     *
     * @property rawAudioFile Path to raw audio file
     * @property processedAudioFile Path to denoised audio file
     * @property duration Total recording duration in milliseconds
     * @property chunksProcessed Number of audio chunks processed
     */
    data class ProcessingResult(
        val rawAudioFile: File,
        val processedAudioFile: File,
        val duration: Long,
        val chunksProcessed: Int
    )
}