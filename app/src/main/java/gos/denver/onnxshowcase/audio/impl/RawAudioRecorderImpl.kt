package gos.denver.onnxshowcase.audio.impl

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import gos.denver.onnxshowcase.audio.AudioConstants
import gos.denver.onnxshowcase.audio.AudioConversionUtils
import gos.denver.onnxshowcase.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioRecord-based implementation for capturing raw PCM audio data.
 * This enables real-time processing of audio chunks.
 */
class RawAudioRecorderImpl : AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var isCurrentlyRecording = false
    private var recordingStartTime: Long = 0L

    // Buffer size calculation
    private val bufferSize = AudioRecord.getMinBufferSize(
        AudioConstants.SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).let { minSize ->
        // Ensure buffer size is at least 2x chunk size for smooth processing
        maxOf(minSize, AudioConstants.CHUNK_SIZE * 2 * 2) // *2 for 16-bit samples
    }

    /**
     * Initializes AudioRecord for capturing raw PCM data.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (audioRecord != null) return@withContext

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Failed to initialize AudioRecord")
        }
    }

    /**
     * Starts recording and returns a sequence of audio chunks.
     */
    override suspend fun startRecording(): Flow<ShortArray> = withContext(Dispatchers.IO) {
        val audioRecord = this@RawAudioRecorderImpl.audioRecord
            ?: throw IllegalStateException("AudioRecord not initialized")

        kotlinx.coroutines.flow.flow {
            try {
                audioRecord.startRecording()
                isCurrentlyRecording = true
                recordingStartTime = System.currentTimeMillis()

                val buffer = ShortArray(AudioConstants.CHUNK_SIZE)

                while (isCurrentlyRecording) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        // Emit chunk for processing
                        emit(buffer.copyOf(bytesRead))
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        break
                    }
                }
            } finally {
                audioRecord.stop()
            }
        }
    }

    override suspend fun stopRecording() {
        withContext(Dispatchers.IO) {
            isCurrentlyRecording = false
            audioRecord?.stop()
        }
    }

    /**
     * Releases AudioRecord resources.
     */
    override fun release() {
        audioRecord?.release()
        audioRecord = null
        isCurrentlyRecording = false
    }

    override fun isRecording(): Boolean = isCurrentlyRecording

}