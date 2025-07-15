package gos.denver.onnxshowcase.audio

/**
 * Manages Android audio recording permissions.
 *
 * This interface handles permission requests and status checks for
 * RECORD_AUDIO permission.
 */
interface AudioPermissionHandler {
    /**
     * Requests RECORD_AUDIO permission from user.
     *
     * Handles permission dialogs and works with Android's permission system.
     *
     * @return True if permission granted, false otherwise
     */
    suspend fun requestAudioPermission(): Boolean

    /**
     * Checks current permission status.
     *
     * @return True if app has RECORD_AUDIO permission
     */
    fun hasAudioPermission(): Boolean
}