package gos.denver.onnxshowcase.audio.impl

import gos.denver.onnxshowcase.audio.AudioConstants
import gos.denver.onnxshowcase.audio.AudioConversionUtils
import gos.denver.onnxshowcase.audio.AudioFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fixed implementation of AudioFileWriter that creates proper WAV files.
 */
class SimpleAudioFileWriterImpl(private val cacheDir: File) : AudioFileWriter {
    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private var dataSize = 0

    override suspend fun createFile(fileName: String): File = withContext(Dispatchers.IO) {
        // FIXED: Close any existing file first
        finalizeCurrentFile()

        val file = File(cacheDir, fileName)
        file.parentFile?.mkdirs()

        // FIXED: Delete existing file if it exists
        if (file.exists()) {
            file.delete()
        }

        fileOutputStream = FileOutputStream(file)
        currentFile = file
        dataSize = 0

        // FIXED: Write placeholder WAV header with 0 data size initially
        writeWavHeader(fileOutputStream!!, 0)

        file
    }

    override suspend fun writeAudioData(data: FloatArray) = withContext(Dispatchers.IO) {
        val outputStream = fileOutputStream ?: throw IllegalStateException("No file created")

        // Convert float data to 16-bit PCM
        val shortArray = AudioConversionUtils.floatArrayToShortArray(data)
        val byteArray = AudioConversionUtils.shortArrayToByteArray(shortArray)

        // FIXED: Simply append the data, don't rewrite header
        outputStream.write(byteArray)
        outputStream.flush() // ADDED: Ensure data is written immediately
        dataSize += byteArray.size
    }

    override suspend fun finalizeFile(): File = withContext(Dispatchers.IO) {
        val file = currentFile ?: throw IllegalStateException("No file created")

        finalizeCurrentFile()
        file
    }

    override fun getSampleRate(): Int = AudioConstants.SAMPLE_RATE
    override fun getChannelCount(): Int = AudioConstants.CHANNEL_COUNT

    // ADDED: Helper method to properly finalize current file
    private suspend fun finalizeCurrentFile() = withContext(Dispatchers.IO) {
        val file = currentFile ?: return@withContext
        val outputStream = fileOutputStream ?: return@withContext

        try {
            // Close the stream first
            outputStream.close()

            // FIXED: Use RandomAccessFile to update header in place
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                // Update file size in RIFF header (at offset 4)
                randomAccessFile.seek(4)
                randomAccessFile.writeInt(Integer.reverseBytes(36 + dataSize))

                // Update data chunk size (at offset 40)
                randomAccessFile.seek(40)
                randomAccessFile.writeInt(Integer.reverseBytes(dataSize))
            }

        } finally {
            fileOutputStream = null
            currentFile = null
            dataSize = 0
        }
    }

    /**
     * FIXED: Writes WAV file header with proper byte order.
     */
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Int) {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            put("RIFF".toByteArray()) // 0-3
            putInt(36 + dataSize) // 4-7: File size - 8
            put("WAVE".toByteArray()) // 8-11

            // Format chunk
            put("fmt ".toByteArray()) // 12-15
            putInt(16) // 16-19: Chunk size
            putShort(1) // 20-21: Audio format (PCM)
            putShort(AudioConstants.CHANNEL_COUNT.toShort()) // 22-23: Channels
            putInt(AudioConstants.SAMPLE_RATE) // 24-27: Sample rate
            putInt(AudioConstants.SAMPLE_RATE * AudioConstants.CHANNEL_COUNT * 2) // 28-31: Byte rate
            putShort((AudioConstants.CHANNEL_COUNT * 2).toShort()) // 32-33: Block align
            putShort(AudioConstants.BITS_PER_SAMPLE.toShort()) // 34-35: Bits per sample

            // Data chunk
            put("data".toByteArray()) // 36-39
            putInt(dataSize) // 40-43: Data size
        }

        outputStream.write(header)
    }
}