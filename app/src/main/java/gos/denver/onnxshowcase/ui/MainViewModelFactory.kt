package gos.denver.onnxshowcase.ui


import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import gos.denver.onnxshowcase.audio.impl.AudioPlayerImpl
import gos.denver.onnxshowcase.audio.impl.AudioRecorderImpl
import gos.denver.onnxshowcase.audio.impl.ConcurrentAudioProcessorImpl
import gos.denver.onnxshowcase.audio.impl.NoiseSuppressorImpl
import gos.denver.onnxshowcase.audio.impl.RawAudioRecorderImpl


class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val rawAudioRecorder = RawAudioRecorderImpl()
            val noiseSuppressor = NoiseSuppressorImpl()
            val concurrentProcessor = ConcurrentAudioProcessorImpl(
                rawAudioRecorder = rawAudioRecorder,
                cacheDir = context.applicationContext.cacheDir
            )
            return MainViewModel(
                audioRecorder = AudioRecorderImpl(context.applicationContext),
                audioPlayer = AudioPlayerImpl(),
                concurrentProcessor = concurrentProcessor,
                noiseSuppressor = noiseSuppressor,
                rawAudioRecorder = rawAudioRecorder,
                cacheDir = context.cacheDir
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}