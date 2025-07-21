package gos.denver.onnxshowcase.audio.impl

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import gos.denver.onnxshowcase.audio.AudioConstants
import gos.denver.onnxshowcase.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class AudioRecorderImpl(private val context: Context) : AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var isCurrentlyRecording = false

    override suspend fun startRecording(outputFile: File) {

        withContext(Dispatchers.IO) {
            try {
                // Ensure directory exists
                outputFile.parentFile?.mkdirs()

                // Create MediaRecorder instance
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }

                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setAudioSamplingRate(AudioConstants.SAMPLE_RATE)
                    setAudioChannels(AudioConstants.CHANNEL_COUNT)
                    setOutputFile(outputFile.absolutePath)

                    prepare()
                    start()

                    currentOutputFile = outputFile
                    recordingStartTime = System.currentTimeMillis()
                    isCurrentlyRecording = true
                }
            } catch (e: IOException) {
                mediaRecorder?.release()
                mediaRecorder = null
                isCurrentlyRecording = false
                throw e
            }
        }
    }

    override suspend fun stopRecording(): File = withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            val outputFile =
                currentOutputFile ?: throw IllegalStateException("No recording in progress")

            // Clean up
            mediaRecorder = null
            currentOutputFile = null
            isCurrentlyRecording = false

            outputFile
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            isCurrentlyRecording = false
            throw e
        }
    }

    override fun isRecording(): Boolean = isCurrentlyRecording

    override fun getRecordingDuration(): Long {
        return if (isCurrentlyRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else 0L
    }
}