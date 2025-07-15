package gos.denver.onnxshowcase.audio.impl

import gos.denver.onnxshowcase.audio.AudioPlayer
import java.io.File

class MockAudioPlayer : AudioPlayer {
    private var playing = false

    override suspend fun play(audioFile: File) {
        playing = true
    }

    override suspend fun pause() {
        playing = false
    }

    override suspend fun stop() {
        playing = false
    }

    override fun isPlaying() = playing
    override fun getDuration() = 10000L
    override fun getCurrentPosition() = 0L
}