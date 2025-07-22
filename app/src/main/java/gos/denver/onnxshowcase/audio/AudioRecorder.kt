package gos.denver.onnxshowcase.audio

import kotlinx.coroutines.flow.Flow


/**
 * Handles low-level audio capture from device microphone.
 */
interface AudioRecorder {

    suspend fun initialize()

    suspend fun startRecording(): Flow<ShortArray>

    suspend fun stopRecording()

    fun isRecording(): Boolean

    fun release()
}