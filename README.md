# ONNX Audio Showcase

A comprehensive Android sample project demonstrating real-time audio noise suppression using ONNX Runtime and the DTLN (Dual-signal Transformation LSTM Network) model.

## Project Overview

This showcase application demonstrates how to integrate ONNX Runtime into Android applications for real-time audio processing. The app captures audio from the microphone, processes it through a DTLN noise suppression model, and allows users to compare original and denoised audio tracks side by side.

### Key Features

* **Real-time Audio Processing**: Live noise suppression using DTLN model via ONNX Runtime
* **Dual Audio Output**: Simultaneously saves both raw and processed audio streams
* **Interactive Comparison**: Play and compare original vs. denoised audio tracks
* **Modern Android Architecture**: Built with MVVM pattern, Jetpack Compose, and Kotlin Coroutines
* **Optimized Performance**: Custom NDK integration for FFT operations and efficient audio processing

## Architecture

The project follows clean architecture principles with clear separation of concerns:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   MainActivity  │───▶│  MainViewModel   │───▶│ Audio Components│
│  (Compose UI)   │    │ (State Manager)  │    │   & Processors  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Core Components

* **MainActivity**: Jetpack Compose UI with permission handling and user interactions
* **MainViewModel**: Central state management and coordination between components
* **AudioRecorder**: MediaRecorder-based audio capture
* **RawAudioRecorderImpl**: AudioRecord-based implementation for real-time chunk processing
* **NoiseSuppressor**: ONNX Runtime integration with DTLN model
* **ConcurrentAudioProcessor**: Coordinates real-time recording and processing pipeline
* **AudioPlayer**: MediaPlayer-based playback for comparing results


### DTLN Model Integration
For this showcase, models from a genius https://github.com/breizhn/DTLN/ were used


The DTLN (Dual-signal Transformation LSTM Network) model processes audio in two stages:

1. **Frequency Domain**: Estimates noise suppression mask in frequency domain
2. **Time Domain**: Refines the output using temporal information

### Key Technical Decisions

* **Chunk Size**: 512 samples to match DTLN requirements
* **Sample Rate**: 16kHz for optimal model performance
* **Buffer Management**: Overlap-add processing for seamless audio reconstruction
* **Concurrency**: Separate coroutines for recording, processing, and file I/O
* **Memory Management**: Careful ONNX tensor lifecycle management to prevent leaks

## Acknowledgments

* [DTLN Model](https://github.com/breizhn/DTLN) - Noise suppression model
* [ONNX Runtime](https://onnxruntime.ai/) - Cross-platform ML inference
* [Android Jetpack](https://developer.android.com/jetpack) - Modern Android development

