package gos.denver.onnxshowcase.audio

import java.io.File


/**
 * Handles low-level audio capture from device microphone.
 *
 * This interface abstracts MediaRecorder functionality for clean separation
 * of concerns and testability.
 */
interface AudioRecorder {
    /**
     * Begins audio capture from the default microphone.
     *
     * Configures MediaRecorder with appropriate settings and creates output
     * file if it doesn't exist.
     *
     * @param outputFile Destination file for raw audio data
     */
    suspend fun startRecording(outputFile: File)

    /**
     * Stops audio capture and finalizes the recording.
     *
     * Ensures all buffers are flushed and releases MediaRecorder resources.
     *
     * @return File containing the recorded audio
     */
    suspend fun stopRecording(): File

    /**
     * Queries current recording state.
     *
     * @return True if actively recording, false otherwise
     */
    fun isRecording(): Boolean

    /**
     * Gets the duration of the current recording.
     *
     * @return Recording duration in milliseconds, 0 if not recording
     */
    fun getRecordingDuration(): Long
}