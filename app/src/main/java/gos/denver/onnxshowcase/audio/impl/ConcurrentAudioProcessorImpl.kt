package gos.denver.onnxshowcase.audio.impl

import androidx.annotation.RequiresPermission
import gos.denver.onnxshowcase.audio.AudioConversionUtils
import gos.denver.onnxshowcase.audio.AudioRecorder
import gos.denver.onnxshowcase.audio.ConcurrentAudioProcessor
import gos.denver.onnxshowcase.audio.NoiseSuppressor
import kotlinx.coroutines.*
import java.io.File

/**
 * Implements concurrent audio recording and processing pipeline.
 * Coordinates real-time recording, denoising, and dual file output.
 */
class ConcurrentAudioProcessorImpl(
    private val rawAudioRecorder: AudioRecorder,
    private val cacheDir: File
) : ConcurrentAudioProcessor {

    private var processingJob: Job? = null
    private var isProcessing = false
    private var processedChunksCount = 0
    private var processingStartTime = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override suspend fun startProcessing(
        suppressor: NoiseSuppressor,
        rawOutputFile: File,
        processedOutputFile: File
    ) {
        if (isProcessing) return

        isProcessing = true
        processedChunksCount = 0
        processingStartTime = System.currentTimeMillis()

        // Initialize components
        rawAudioRecorder.initialize()

        processingJob = scope.launch {
            // Create file writers for both outputs
            val rawFileWriter = SimpleAudioFileWriterImpl(cacheDir)
            val processedFileWriter = SimpleAudioFileWriterImpl(cacheDir)

            try {
                rawFileWriter.createFile(rawOutputFile.name)
                processedFileWriter.createFile(processedOutputFile.name)

                // Start recording and process chunks
                rawAudioRecorder.startRecording().collect { audioChunk ->
                    if (!isProcessing) return@collect

                    // Convert to float array for processing
                    val floatChunk = AudioConversionUtils.shortArrayToFloatArray(audioChunk)

                    // Process chunk through noise suppressor
                    val processedChunk = suppressor.processChunk(floatChunk)

                    // Write both raw and processed data concurrently
                    launch { rawFileWriter.writeAudioData(floatChunk) }
                    launch { processedFileWriter.writeAudioData(processedChunk) }

                    processedChunksCount++
                }

                // Finalize files
                rawFileWriter.finalizeFile()
                processedFileWriter.finalizeFile()

            } catch (e: Exception) {
                throw e
            }
        }
    }

    override suspend fun stopProcessing(): ConcurrentAudioProcessor.ProcessingResult {
        if (!isProcessing) {
            throw IllegalStateException("Processing not started")
        }

        isProcessing = false
        rawAudioRecorder.stopRecording()

        // Wait for processing to complete
        processingJob?.join()

        val duration = System.currentTimeMillis() - processingStartTime

        return ConcurrentAudioProcessor.ProcessingResult(
            rawAudioFile = File(cacheDir, "raw_audio_${processingStartTime}.wav"),
            processedAudioFile = File(cacheDir, "processed_audio_${processingStartTime}.wav"),
            duration = duration,
            chunksProcessed = processedChunksCount
        )
    }
}