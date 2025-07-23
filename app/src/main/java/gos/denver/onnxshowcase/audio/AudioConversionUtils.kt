package gos.denver.onnxshowcase.audio

import kotlin.math.*

/**
 * Utility functions for audio format conversions and processing.
 */
object AudioConversionUtils {

    /**
     * Converts 16-bit PCM samples (Short array) to normalized float array [-1.0, 1.0].
     */
    fun shortArrayToFloatArray(shortArray: ShortArray): FloatArray {
        return FloatArray(shortArray.size) { i ->
            shortArray[i].toFloat() / Short.MAX_VALUE.toFloat()
        }
    }

    /**
     * Converts normalized float array [-1.0, 1.0] to 16-bit PCM samples (Short array).
     */
    fun floatArrayToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            val clampedValue = floatArray[i].coerceIn(-1.0f, 1.0f)
            (clampedValue * Short.MAX_VALUE.toFloat()).roundToInt().toShort()
        }
    }

    /**
     * Converts short array to byte array for file writing.
     */
    fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            val value = shortArray[i].toInt()
            byteArray[i * 2] = (value and 0xFF).toByte()
            byteArray[i * 2 + 1] = (value shr 8 and 0xFF).toByte()
        }
        return byteArray
    }
}