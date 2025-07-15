package gos.denver.onnxshowcase.audio.impl

import gos.denver.onnxshowcase.audio.AudioRecorder
import java.io.File

class MockAudioRecorder : AudioRecorder {
    private var recording = false

    override suspend fun startRecording(outputFile: File) {
        recording = true
    }

    override suspend fun stopRecording(): File {
        recording = false
        return File("/mock/audio.raw")
    }

    override fun isRecording() = recording
    override fun getRecordingDuration() = 0L
}