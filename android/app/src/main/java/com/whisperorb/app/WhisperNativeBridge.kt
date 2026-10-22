package com.whisperorb.app

object WhisperNativeBridge {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun getCpuTopologySummary(): String
    external fun getOptimalPerformanceThreadCount(): Int
    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribeAudio(
        contextPtr: Long,
        audioSamples: FloatArray,
        languageCode: String,
        initialPrompt: String,
        numThreads: Int
    ): String
}
