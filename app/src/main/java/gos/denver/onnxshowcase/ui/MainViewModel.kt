package gos.denver.onnxshowcase.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gos.denver.onnxshowcase.audio.AudioPlayer
import gos.denver.onnxshowcase.audio.AudioRecorder
import gos.denver.onnxshowcase.audio.ConcurrentAudioProcessor
import gos.denver.onnxshowcase.audio.NoiseSuppressor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Central state management and coordination between UI and business logic components.
 *
 * This ViewModel manages the complete application state and coordinates all audio
 * recording, processing, and playback operations.
 */
class MainViewModel(
    private val audioPlayer: AudioPlayer,
    private val noiseSuppressor: NoiseSuppressor,
    private val concurrentProcessor: ConcurrentAudioProcessor,
    private val audioRecorder: AudioRecorder,
    private val cacheDir: File
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Internal state tracking
    private var recordingJob: Job? = null
    private var recordingStartTime: Long = 0L
    private var durationUpdateJob: Job? = null


    init {
        audioPlayer.setOnPlaybackCompleteListener {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isOriginalPlaying = false,
                    isDenoisedPlaying = false
                )
            }
        }
    }

    /**
     * Updates the permission state in the UI.
     *
     * @param granted Whether RECORD_AUDIO permission was granted
     */
    fun updatePermissionStatus(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasRecordingPermission = granted
        )
    }

    /**
     * Initiates audio recording and processing pipeline.
     *
     * Triggers AudioRecorder to begin capturing audio and starts
     * ConcurrentAudioProcessor for real-time denoising. Updates UI state
     * to reflect recording status.
     */
    fun startRecording() {
        if (!_uiState.value.hasRecordingPermission || _uiState.value.isRecording) {
            return
        }

        // Clear previous recording data
        _uiState.value = _uiState.value.copy(
            originalAudioPath = null,
            denoisedAudioPath = null,
            isOriginalPlaying = false,
            isDenoisedPlaying = false
        )

        recordingJob = viewModelScope.launch {
            try {
                noiseSuppressor.initialize()

                // Update state to recording
                recordingStartTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    isRecording = true,
                    recordingDuration = 0L
                )

                // Start duration tracking
                startDurationTracking()

                val timestamp = System.currentTimeMillis()
                val rawOutputFile = File(cacheDir, "raw_audio_$timestamp.wav")
                val processedOutputFile = File(cacheDir, "processed_audio_$timestamp.wav")

                concurrentProcessor.startProcessing(
                    suppressor = noiseSuppressor,
                    rawOutputFile = rawOutputFile,
                    processedOutputFile = processedOutputFile
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isProcessing = false
                )
                stopDurationTracking()
                noiseSuppressor.release()
            }
        }
    }

    /**
     * Terminates recording and finalizes audio files.
     *
     * Stops AudioRecorder and processing pipeline, saves both raw and
     * processed audio files, and updates UI state with file paths for playback.
     */
    fun stopRecording() {
        if (!_uiState.value.isRecording) {
            return
        }

        recordingJob?.cancel()
        stopDurationTracking()

        viewModelScope.launch {
            try {
                // Update state to processing
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isProcessing = true
                )

                val result = concurrentProcessor.stopProcessing()

                noiseSuppressor.release()

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    originalAudioPath = result.rawAudioFile.absolutePath,
                    denoisedAudioPath = result.processedAudioFile.absolutePath
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isProcessing = false
                )
                noiseSuppressor.release()
            }
        }
    }
    /**
     * Begins playback of the raw recorded audio.
     *
     * Initiates AudioPlayer with original audio file and updates UI state
     * to show playback status.
     */
    fun playOriginalAudio() {

        val audioPath = _uiState.value.originalAudioPath ?: return
        // Stop denoised audio if playing
        if (_uiState.value.isDenoisedPlaying) {
            pauseDenoisedAudio()
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isOriginalPlaying = true)
                audioPlayer.play(File(audioPath))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isOriginalPlaying = false)
                Log.e(TAG, "Error playing original audio", e)
            }
        }
    }

    /**
     * Pauses playback of the raw audio track.
     *
     * Pauses AudioPlayer while maintaining current playback position.
     */
    fun pauseOriginalAudio() {
        if (!_uiState.value.isOriginalPlaying) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isOriginalPlaying = false)
                audioPlayer.pause()

            } catch (e: Exception) {
                Log.e(TAG, "Error pausing original audio", e)
            }
        }
    }

    /**
     * Begins playback of the processed audio.
     *
     * Initiates AudioPlayer with denoised audio file and updates UI state
     * to show playback status.
     */
    fun playDenoisedAudio() {
        val audioPath = _uiState.value.denoisedAudioPath ?: return

        // Stop original audio if playing
        if (_uiState.value.isOriginalPlaying) {
            pauseOriginalAudio()
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDenoisedPlaying = true)
                audioPlayer.play(File(audioPath))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDenoisedPlaying = false)
                Log.e(TAG, "Error playing denoised audio", e)
            }
        }
    }

    /**
     * Pauses playback of the denoised audio track.
     *
     * Pauses AudioPlayer while maintaining current playback position.
     */
    fun pauseDenoisedAudio() {
        if (!_uiState.value.isDenoisedPlaying) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDenoisedPlaying = false)
                audioPlayer.pause()

            } catch (e: Exception) {
                Log.e(TAG, "Error pausing denoised audio", e)
            }
        }
    }


    /**
     * Starts tracking recording duration with real-time updates.
     */
    private fun startDurationTracking() {
        durationUpdateJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                val currentDuration = System.currentTimeMillis() - recordingStartTime
                _uiState.value = _uiState.value.copy(recordingDuration = currentDuration)
                delay(100)
            }
        }
    }

    /**
     * Stops duration tracking and cleans up resources.
     */
    private fun stopDurationTracking() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
    }

    /**
     * Cleans up resources when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        stopDurationTracking()

        viewModelScope.launch {
            try {
                if (audioRecorder.isRecording()) {
                    audioRecorder.stopRecording()
                }
                audioRecorder.release()

                if (noiseSuppressor.isInitialized()) {
                    noiseSuppressor.release()
                }

                audioPlayer.stop()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}