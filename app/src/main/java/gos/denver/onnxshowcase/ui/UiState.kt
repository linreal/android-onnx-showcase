package gos.denver.onnxshowcase.ui

/**
 * Immutable data class representing the complete UI state for the application.
 *
 * @property isRecording Indicates if audio recording is currently active
 * @property isProcessing Indicates if audio processing is in progress
 * @property isOriginalPlaying True when original audio track is playing
 * @property isDenoisedPlaying True when denoised audio track is playing
 * @property originalAudioPath File path to the raw recorded audio
 * @property denoisedAudioPath File path to the processed audio
 * @property recordingDuration Duration of the current/last recording in milliseconds
 * @property hasRecordingPermission Indicates if RECORD_AUDIO permission is granted
 */
data class UiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isOriginalPlaying: Boolean = false,
    val isDenoisedPlaying: Boolean = false,
    val originalAudioPath: String? = null,
    val denoisedAudioPath: String? = null,
    val recordingDuration: Long = 0L,
    val hasRecordingPermission: Boolean = false
)