package gos.denver.onnxshowcase.audio

import java.io.File

/**
 * Handles audio data serialization to files.
 *
 * This interface manages the conversion of float audio samples to
 * appropriate file formats (e.g., WAV) with proper headers.
 */
interface AudioFileWriter {
    /**
     * Creates a new audio file for writing.
     *
     * Creates necessary directories if missing.
     *
     * @param fileName Name for the output file
     * @return File handle for writing
     * @throws IOException if file creation fails
     */
    suspend fun createFile(fileName: String): File

    /**
     * Appends audio samples to the file.
     *
     * Converts float samples to appropriate format and handles buffering
     * for efficiency.
     *
     * @param data Audio samples to write (normalized floats)
     */
    suspend fun writeAudioData(data: FloatArray)

    /**
     * Completes file writing and adds headers.
     *
     * Writes WAV headers if applicable and flushes all buffers.
     *
     * @return Finalized audio file
     */
    suspend fun finalizeFile(): File

    /**
     * Gets the sample rate used for writing.
     *
     * @return Sample rate in Hz (16000 for DTLN)
     */
    fun getSampleRate(): Int

    /**
     * Gets the number of audio channels.
     *
     * @return Channel count (1 for mono)
     */
    fun getChannelCount(): Int
}