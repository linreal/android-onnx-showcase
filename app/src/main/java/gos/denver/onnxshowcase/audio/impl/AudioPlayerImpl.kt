package gos.denver.onnxshowcase.audio.impl

import android.media.MediaPlayer
import gos.denver.onnxshowcase.audio.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioPlayerImpl : AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var isPaused = false

    override suspend fun play(audioFile: File) = withContext(Dispatchers.IO) {
        try {
            if (isPaused && mediaPlayer != null) {
                mediaPlayer?.start()
                isPaused = false
                return@withContext
            }

            stop()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }
            isPaused = false
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            throw e
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            if (isPlaying()) {
                mediaPlayer?.pause()
                isPaused = true
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPaused = false
    }

    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    override fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }

    override fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }
}