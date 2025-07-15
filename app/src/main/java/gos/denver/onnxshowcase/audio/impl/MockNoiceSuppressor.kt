package gos.denver.onnxshowcase.audio.impl

import gos.denver.onnxshowcase.audio.NoiseSuppressor

class MockNoiseSuppressor : NoiseSuppressor {
    private var initialized = false

    override suspend fun initialize(modelPath: String) {
        initialized = true
    }

    override fun processChunk(audioChunk: FloatArray): FloatArray {
        return audioChunk // Pass through
    }

    override fun release() {
        initialized = false
    }

    override fun isInitialized() = initialized
}