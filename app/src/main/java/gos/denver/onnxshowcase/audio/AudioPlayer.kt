package gos.denver.onnxshowcase.audio

import java.io.File

/**
 * Provides audio playback functionality for recorded files.
 *
 * This interface abstracts MediaPlayer for playing both raw and
 * processed audio tracks.
 */
interface AudioPlayer {
    /**
     * Starts or resumes audio playback.
     *
     * Initializes MediaPlayer if needed and resumes from last position if paused.
     *
     * @param audioFile Audio file to play
     */
    suspend fun play(audioFile: File)

    /**
     * Pauses current playback.
     *
     * Maintains playback position and keeps MediaPlayer resources allocated.
     */
    suspend fun pause()

    /**
     * Stops playback and releases resources.
     *
     * Resets playback position to beginning and releases MediaPlayer resources.
     */
    suspend fun stop()

    /**
     * Queries current playback state.
     *
     * @return True if audio is currently playing
     */
    fun isPlaying(): Boolean

    /**
     * Gets total duration of loaded audio.
     *
     * @return Total duration in milliseconds, 0 if no audio loaded
     */
    fun getDuration(): Long

    /**
     * Gets current playback position.
     *
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long

    /**
     * Sets a callback to be invoked when audio playback completes naturally.
     *
     * @param callback Function to call when playback finishes
     */
    fun setOnPlaybackCompleteListener(callback: () -> Unit)
}
