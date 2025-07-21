package gos.denver.onnxshowcase.ui


import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import gos.denver.onnxshowcase.audio.impl.AudioPlayerImpl
import gos.denver.onnxshowcase.audio.impl.AudioRecorderImpl


class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                audioRecorder = AudioRecorderImpl(context.applicationContext),
                audioPlayer = AudioPlayerImpl()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}