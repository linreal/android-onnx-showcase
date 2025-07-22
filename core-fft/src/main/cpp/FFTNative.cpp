#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <complex>
#include <memory>
#include <vector>

#include "pocketfft_hdronly.h"

#define POCKETFFT_CACHE_SIZE 0

#define TAG "FFTNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct FFTPlan {
    int size;
    std::vector<float> in;
    std::vector<std::complex<float>> out;
    std::mutex mutex;  // Add mutex for thread safety

    FFTPlan(int s) : size(s) {
        try {
            in.resize(size);
            out.resize(size / 2 + 1);
        } catch (const std::exception &e) {
            LOGE("Failed to allocate FFT plan buffers: %s", e.what());
            throw;
        }
    }
};

// Helper function to throw Java exceptions
static void throwJavaException(JNIEnv *env, const char *className, const char *message) {
    jclass exceptionClass = env->FindClass(className);
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

// Helper function to validate FFT plan
static FFTPlan *validatePlan(JNIEnv *env, jlong planPtr) {
    if (planPtr == 0) {
        throwJavaException(env, "java/lang/IllegalStateException", "FFT plan is null");
        return nullptr;
    }
    return reinterpret_cast<FFTPlan *>(planPtr);
}

struct VoiceVariables {
    float volume;
    std::vector<float> spectrum;
};

class VoiceFFTProcessor {
private:
    int sampleRate;
    std::vector<std::pair<float, float>> voiceBandRanges;
    std::vector<float> bandAmplification;

    VoiceVariables processSpectralData(
            const std::vector<std::complex<double>> &spectrum, float binSize,
            int numBands = 8) {
        std::vector<float> bands(numBands);
        std::vector<float> formants(3);
        float totalEnergy = 0.0f;

        for (size_t bandIndex = 0; bandIndex < voiceBandRanges.size();
             bandIndex++) {
            double bandEnergy = 0.0;
            double peakMagnitude = 0.0;
            float peakFrequency = 0.0f;

            // Replace structured binding with direct access
            float rangeStart = voiceBandRanges[bandIndex].first;
            float rangeEnd = voiceBandRanges[bandIndex].second;

            for (size_t i = 0; i < spectrum.size(); i++) {
                float frequency = i * binSize;
                if (frequency >= rangeStart && frequency <= rangeEnd) {
                    double magnitude = std::abs(spectrum[i]);
                    bandEnergy += magnitude;

                    if (magnitude > peakMagnitude) {
                        peakMagnitude = magnitude;
                        peakFrequency = frequency;
                    }
                }
            }

            bands[bandIndex] =
                    static_cast<float>(bandEnergy * bandAmplification[bandIndex]);
            totalEnergy += bands[bandIndex];

            // Store formant frequencies
            switch (bandIndex) {
                case 2:
                    formants[0] = peakFrequency;
                    break;
                case 3:
                case 4:
                    if (peakFrequency > 0) formants[1] = peakFrequency;
                    break;
                case 5:
                case 6:
                    if (peakFrequency > 0) formants[2] = peakFrequency;
                    break;
            }
        }

        // Normalize with dynamics preservation
        if (totalEnergy > 0) {
            float maxBandValue = *std::max_element(bands.begin(), bands.end());
            for (float &band: bands) {
                band = std::pow(band / maxBandValue, 0.7f);
            }
        }

        return VoiceVariables{totalEnergy, bands};
    }

public:
    VoiceFFTProcessor(int sampleRate = 16000) : sampleRate(sampleRate) {
        voiceBandRanges = {
                {85.0f,   255.0f},     // Male fundamental tone
                {256.0f,  500.0f},    // Female fundamental tone + low formants
                {501.0f,  1000.0f},   // First formant (vowels)
                {1001.0f, 1500.0f},  // Second formant (start)
                {1501.0f, 2000.0f},  // Second formant (end)
                {2001.0f, 2500.0f},  // Third formant (start)
                {2501.0f, 3000.0f},  // Third formant (end)
                {3001.0f, 3400.0f}   // High-frequency components
        };

        bandAmplification = {
                1.8f,  // Amplify low frequencies
                1.6f,  // Amplify female voice frequencies
                1.4f,  // Moderate first formant amplification
                1.3f,  // Small amplification of second formant start
                1.2f,  // Maintain second formant end level
                1.1f,  // Soft amplification of third formant start
                1.0f,  // Maintain third formant end level
                0.9f   // Slight attenuation of high frequencies
        };
    }

    VoiceVariables processFFT(const std::vector<jbyte> &fftData,
                              int numBands = 8) {
        // Add input validation
        if (fftData.empty()) {
            LOGE("Empty audio data received");
            return VoiceVariables{0.0f, std::vector<float>(numBands, 0.0f)};
        }

        try {
            size_t complexSize = fftData.size() / 2;
            std::vector<std::complex<double>> complexArray(complexSize);

            // Convert byte data to complex numbers
            for (size_t i = 0; i < complexSize; i++) {
                double real = static_cast<unsigned char>(fftData[i * 2]) & 0xFF;
                double imag = static_cast<unsigned char>(fftData[i * 2 + 1]) & 0xFF;
                complexArray[i] = std::complex<double>(real, imag);
            }

            float binSize = static_cast<float>(sampleRate) / (complexSize * 2);
            return processSpectralData(complexArray, binSize, numBands);
        } catch (const std::exception &e) {
            LOGE("Exception in process: %s", e.what());
            return VoiceVariables{0.0f, std::vector<float>(numBands, 0.0f)};
        }
    }

    VoiceVariables process(const std::vector<jbyte> &audioData,
                           int numBands = 8) {
        // Add input validation
        if (audioData.empty()) {
            LOGE("Empty audio data received");
            return VoiceVariables{0.0f, std::vector<float>(numBands, 0.0f)};
        }

        // Ensure the size is valid for FFT (should be power of 2)
        if (audioData.size() & (audioData.size() - 1)) {
            LOGE("Audio data size %zu is not a power of 2", audioData.size());
            return VoiceVariables{0.0f, std::vector<float>(numBands, 0.0f)};
        }
        try {
            // Convert audio data to doubles
            std::vector<double> realData(audioData.size());
            for (size_t i = 0; i < audioData.size(); i++) {
                realData[i] = static_cast<double>(audioData[i]);
            }

            // Prepare for FFT
            pocketfft::shape_t shape = {realData.size()};
            pocketfft::stride_t stride_in = {sizeof(double)};
            pocketfft::stride_t stride_out = {sizeof(std::complex<double>)};

            // Create output array for FFT (size/2 + 1 for real-to-complex transform)
            std::vector<std::complex<double>> spectrum(realData.size() / 2 + 1);

            // Perform forward FFT
            pocketfft::r2c(shape, stride_in, stride_out, 0, true, realData.data(),
                           spectrum.data(), 1.0);

            float binSize = static_cast<float>(sampleRate) / realData.size();
            return processSpectralData(spectrum, binSize, numBands);
        } catch (const std::exception &e) {
            LOGE("Exception in process: %s", e.what());
            return VoiceVariables{0.0f, std::vector<float>(numBands, 0.0f)};
        }
    }
};

struct VoiceProcessorPlan {
    std::unique_ptr<VoiceFFTProcessor> processor;
    std::mutex mutex;

    explicit VoiceProcessorPlan(int sampleRate) {
        processor = std::make_unique<VoiceFFTProcessor>(sampleRate);
    }
};

static VoiceProcessorPlan* validateVoiceProcessor(JNIEnv* env, jlong voiceProcessorPtr) {
    if (voiceProcessorPtr == 0) {
        throwJavaException(env, "java/lang/IllegalStateException", "Voice processor is null");
        return nullptr;
    }
    return reinterpret_cast<VoiceProcessorPlan*>(voiceProcessorPtr);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_gos_denver_fft_FFTNative_createPlan(JNIEnv *env, jobject thiz, jint size) {
    if (size <= 0) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Size must be positive");
        return 0;
    }

    // Check if size is power of 2
    if ((size & (size - 1)) != 0) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Size must be a power of 2");
        return 0;
    }

    LOGI("Creating FFT plan for size %d", size);

    try {
        FFTPlan *plan = new FFTPlan(size);
        return reinterpret_cast<jlong>(plan);
    } catch (const std::bad_alloc &e) {
        LOGE("Memory allocation failed: %s", e.what());
        throwJavaException(env, "java/lang/OutOfMemoryError", "Failed to allocate FFT plan");
        return 0;
    } catch (const std::exception &e) {
        LOGE("Exception during plan creation: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", "Failed to create FFT plan");
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_gos_denver_fft_FFTNative_destroyPlan(JNIEnv *env, jobject thiz, jlong planPtr) {
    FFTPlan *plan = reinterpret_cast<FFTPlan *>(planPtr);
    if (plan != nullptr) {
        try {
            delete plan;
        } catch (const std::exception &e) {
            LOGE("Exception during plan destruction: %s", e.what());
            // Don't throw exception during cleanup
        }
    }
}

JNIEXPORT void JNICALL
Java_gos_denver_fft_FFTNative_forwardTransform(
        JNIEnv *env, jobject thiz, jlong planPtr,
        jfloatArray inArray, jfloatArray magArray, jfloatArray phaseArray) {

    FFTPlan *plan = validatePlan(env, planPtr);
    if (!plan) return;

    // Validate input arrays
    if (!inArray || !magArray || !phaseArray) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Input arrays cannot be null");
        return;
    }

    // Lock the plan for thread safety
    std::lock_guard<std::mutex> lock(plan->mutex);

    try {
        jsize inSize = env->GetArrayLength(inArray);
        jsize magSize = env->GetArrayLength(magArray);
        jsize phaseSize = env->GetArrayLength(phaseArray);

        // Validate sizes
        if (inSize != plan->size) {
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Input array size doesn't match plan size");
            return;
        }

        if (magSize != plan->size / 2 + 1 || phaseSize != plan->size / 2 + 1) {
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Output arrays size mismatch");
            return;
        }

        // Get input data
        jfloat *input = env->GetFloatArrayElements(inArray, nullptr);
        if (!input) {
            throwJavaException(env, "java/lang/RuntimeException", "Failed to access input array");
            return;
        }

        // Get output arrays
        jfloat *mag = env->GetFloatArrayElements(magArray, nullptr);
        jfloat *phase = env->GetFloatArrayElements(phaseArray, nullptr);

        if (!mag || !phase) {
            if (input) env->ReleaseFloatArrayElements(inArray, input, JNI_ABORT);
            if (mag) env->ReleaseFloatArrayElements(magArray, mag, JNI_ABORT);
            if (phase) env->ReleaseFloatArrayElements(phaseArray, phase, JNI_ABORT);
            throwJavaException(env, "java/lang/RuntimeException", "Failed to access output arrays");
            return;
        }

        // Copy input data
        std::copy(input, input + plan->size, plan->in.data());

        // Prepare for FFT
        pocketfft::shape_t shape = {static_cast<size_t>(plan->size)};
        pocketfft::stride_t stride_in = {sizeof(float)};
        pocketfft::stride_t stride_out = {sizeof(std::complex<float>)};

        // Perform forward FFT
        pocketfft::r2c(shape, stride_in, stride_out, 0, true,
                       plan->in.data(), plan->out.data(), 1.0f);

        // Convert to magnitude and phase
        for (int i = 0; i <= plan->size / 2; i++) {
            mag[i] = std::abs(plan->out[i]);
            phase[i] = std::arg(plan->out[i]);
        }

        // Release arrays
        env->ReleaseFloatArrayElements(inArray, input, JNI_ABORT);
        env->ReleaseFloatArrayElements(magArray, mag, 0);
        env->ReleaseFloatArrayElements(phaseArray, phase, 0);

    } catch (const std::exception &e) {
        LOGE("Exception in forwardTransform: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", "FFT forward transform failed");
    }
}

JNIEXPORT void JNICALL
Java_gos_denver_fft_FFTNative_inverseTransform(
        JNIEnv *env, jobject thiz, jlong planPtr,
        jfloatArray magArray, jfloatArray phaseArray, jfloatArray outArray) {

    FFTPlan *plan = validatePlan(env, planPtr);
    if (!plan) return;

    // Validate input arrays
    if (!magArray || !phaseArray || !outArray) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Input arrays cannot be null");
        return;
    }

    // Lock the plan for thread safety
    std::lock_guard<std::mutex> lock(plan->mutex);

    try {
        jsize magSize = env->GetArrayLength(magArray);
        jsize phaseSize = env->GetArrayLength(phaseArray);
        jsize outSize = env->GetArrayLength(outArray);

        // Validate sizes
        if (magSize != plan->size / 2 + 1 || phaseSize != plan->size / 2 + 1) {
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Input arrays size mismatch");
            return;
        }

        if (outSize != plan->size) {
            throwJavaException(env, "java/lang/IllegalArgumentException",
                               "Output array size doesn't match plan size");
            return;
        }

        // Get input arrays
        jfloat *mag = env->GetFloatArrayElements(magArray, nullptr);
        jfloat *phase = env->GetFloatArrayElements(phaseArray, nullptr);

        if (!mag || !phase) {
            if (mag) env->ReleaseFloatArrayElements(magArray, mag, JNI_ABORT);
            if (phase) env->ReleaseFloatArrayElements(phaseArray, phase, JNI_ABORT);
            throwJavaException(env, "java/lang/RuntimeException", "Failed to access input arrays");
            return;
        }

        // Convert magnitude and phase to complex numbers
        for (int i = 0; i <= plan->size / 2; i++) {
            float re = mag[i] * std::cos(phase[i]);
            float im = mag[i] * std::sin(phase[i]);
            plan->out[i] = std::complex<float>(re, im);
        }

        // Prepare for inverse FFT
        pocketfft::shape_t shape = {static_cast<size_t>(plan->size)};
        pocketfft::stride_t stride_in = {sizeof(std::complex<float>)};
        pocketfft::stride_t stride_out = {sizeof(float)};

        // Perform inverse FFT
        pocketfft::c2r(shape, stride_in, stride_out, 0, false,
                       plan->out.data(), plan->in.data(), 1.0f / plan->size);

        // Copy to output array
        env->SetFloatArrayRegion(outArray, 0, plan->size, plan->in.data());

        // Release arrays
        env->ReleaseFloatArrayElements(magArray, mag, JNI_ABORT);
        env->ReleaseFloatArrayElements(phaseArray, phase, JNI_ABORT);

    } catch (const std::exception &e) {
        LOGE("Exception in inverseTransform: %s", e.what());
        throwJavaException(env, "java/lang/RuntimeException", "FFT inverse transform failed");
    }
}

}
