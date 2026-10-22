#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <cmath>
#include <sched.h>
#include <unistd.h>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct CoreFreqInfo {
    int coreId;
    long maxFreqKHz;
};

static std::vector<CoreFreqInfo> detectCpuTopology() {
    std::vector<CoreFreqInfo> cores;
    int numCores = sysconf(_SC_NPROCESSORS_CONF);
    if (numCores <= 0 || numCores > 32) {
        numCores = 8;
    }

    for (int i = 0; i < numCores; ++i) {
        std::ostringstream path;
        path << "/sys/devices/system/cpu/cpu" << i << "/cpufreq/cpuinfo_max_freq";
        std::ifstream file(path.str());
        long freq = 0;
        if (file.is_open()) {
            file >> freq;
        }
        if (freq <= 0) {
            std::ostringstream fallbackPath;
            fallbackPath << "/sys/devices/system/cpu/cpu" << i << "/cpufreq/scaling_max_freq";
            std::ifstream fbFile(fallbackPath.str());
            if (fbFile.is_open()) {
                fbFile >> freq;
            }
        }
        cores.push_back({i, freq});
    }
    return cores;
}

/// Épingle les threads d'inférence uniquement sur les cœurs Prime & Performance (ex: Cortex-X4 + Cortex-A720 sur Tensor G4)
static int pinToPerformanceCoresAndGetThreadCount() {
    auto cores = detectCpuTopology();
    if (cores.empty()) {
        return 4;
    }

    long minFreq = cores[0].maxFreqKHz;
    long maxFreq = cores[0].maxFreqKHz;
    for (const auto& c : cores) {
        if (c.maxFreqKHz > 0 && (minFreq <= 0 || c.maxFreqKHz < minFreq)) {
            minFreq = c.maxFreqKHz;
        }
        if (c.maxFreqKHz > maxFreq) {
            maxFreq = c.maxFreqKHz;
        }
    }

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    int perfCount = 0;

    if (maxFreq > minFreq && minFreq > 0) {
        // Exclut le cluster de petits cœurs Efficacité (Cortex-A520 / A510)
        long threshold = minFreq + (maxFreq - minFreq) / 5;
        for (const auto& c : cores) {
            if (c.maxFreqKHz >= threshold) {
                CPU_SET(c.coreId, &cpuset);
                perfCount++;
            }
        }
    }

    if (perfCount >= 2 && perfCount <= 6) {
        sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
        return perfCount;
    }

    // Fallback universel : 4 plus gros cœurs
    std::sort(cores.begin(), cores.end(), [](const CoreFreqInfo& a, const CoreFreqInfo& b) {
        return a.maxFreqKHz > b.maxFreqKHz;
    });

    CPU_ZERO(&cpuset);
    int target = std::min(4, (int)cores.size());
    for (int i = 0; i < target; ++i) {
        CPU_SET(cores[i].coreId, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    return std::max(2, target);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_whisperorb_app_WhisperNativeBridge_getCpuTopologySummary(JNIEnv* env, jobject /* thiz */) {
    auto cores = detectCpuTopology();
    int perfThreads = pinToPerformanceCoresAndGetThreadCount();
    long maxFreqKHz = 0;
    for (const auto& c : cores) {
        if (c.maxFreqKHz > maxFreqKHz) maxFreqKHz = c.maxFreqKHz;
    }

    std::ostringstream oss;
    if (maxFreqKHz > 0) {
        double ghz = (double)maxFreqKHz / 1000000.0;
        char buf[64];
        snprintf(buf, sizeof(buf), "%d cœurs Perf (max %.2f GHz) • ARMv9 FP16+DotProd", perfThreads, ghz);
        oss << buf;
    } else {
        oss << perfThreads << " cœurs Performance • ARM64 SIMD + FlashAttention";
    }
    return env->NewStringUTF(oss.str().c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_whisperorb_app_WhisperNativeBridge_getOptimalPerformanceThreadCount(JNIEnv* /* env */, jobject /* thiz */) {
    return pinToPerformanceCoresAndGetThreadCount();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_whisperorb_app_WhisperNativeBridge_initContext(JNIEnv* env, jobject /* thiz */, jstring modelPathJStr) {
    const char* modelPath = env->GetStringUTFChars(modelPathJStr, nullptr);
    if (!modelPath) {
        return 0;
    }

    pinToPerformanceCoresAndGetThreadCount();

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = true;
    cparams.flash_attn = true;

    struct whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx) {
        cparams.flash_attn = false;
        ctx = whisper_init_from_file_with_params(modelPath, cparams);
    }

    env->ReleaseStringUTFChars(modelPathJStr, modelPath);

    if (!ctx) {
        LOGE("Failed to initialize whisper_context");
        return 0;
    }

    // Pré-chauffage rapide (warmup) sur 0,5 s de silence
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language = "en";
    wparams.detect_language = false;
    wparams.n_threads = pinToPerformanceCoresAndGetThreadCount();
    wparams.single_segment = true;
    wparams.no_timestamps = true;
    wparams.no_context = true;
    wparams.max_tokens = 1;
    wparams.audio_ctx = 64;
    std::vector<float> silence(8000, 0.0f);
    whisper_full(ctx, wparams, silence.data(), (int)silence.size());

    LOGI("Whisper context initialized successfully (%p)", ctx);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_whisperorb_app_WhisperNativeBridge_freeContext(JNIEnv* /* env */, jobject /* thiz */, jlong contextPtr) {
    if (contextPtr != 0) {
        auto* ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
        whisper_free(ctx);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_whisperorb_app_WhisperNativeBridge_transcribeAudio(
    JNIEnv* env,
    jobject /* thiz */,
    jlong contextPtr,
    jfloatArray audioSamplesJArr,
    jstring languageCodeJStr,
    jstring initialPromptJStr,
    jint numThreads
) {
    if (contextPtr == 0 || audioSamplesJArr == nullptr) {
        return env->NewStringUTF("");
    }

    auto* ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
    int optimalThreads = pinToPerformanceCoresAndGetThreadCount();
    if (numThreads > 0) {
        optimalThreads = numThreads;
    }

    jsize sampleCount = env->GetArrayLength(audioSamplesJArr);
    if (sampleCount <= 0) {
        return env->NewStringUTF("");
    }

    jfloat* rawSamples = env->GetFloatArrayElements(audioSamplesJArr, nullptr);
    const char* langCStr = languageCodeJStr ? env->GetStringUTFChars(languageCodeJStr, nullptr) : "en";
    const char* promptCStr = initialPromptJStr ? env->GetStringUTFChars(initialPromptJStr, nullptr) : "";

    std::string langStr(langCStr ? langCStr : "en");
    std::string promptStr(promptCStr ? promptCStr : "");

    if (languageCodeJStr && langCStr) env->ReleaseStringUTFChars(languageCodeJStr, langCStr);
    if (initialPromptJStr && promptCStr) env->ReleaseStringUTFChars(initialPromptJStr, promptCStr);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    bool isAuto = (langStr == "auto" || langStr.empty());
    wparams.language = isAuto ? "auto" : langStr.c_str();
    wparams.detect_language = isAuto;
    wparams.translate = false;
    wparams.n_threads = optimalThreads;
    wparams.single_segment = true;
    wparams.no_timestamps = true;
    wparams.no_context = true;
    wparams.print_special = false;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.suppress_blank = true;
    wparams.suppress_nst = true;
    wparams.temperature = 0.0f;
    wparams.temperature_inc = 0.0f;
    wparams.greedy.best_of = 1;
    wparams.max_tokens = 96;
    wparams.n_max_text_ctx = 32;

    // Fenêtre d'encodage dynamique (plancher 64 frames = 1,28 s au lieu de 1500 frames = 30 s)
    double durationSeconds = (double)sampleCount / 16000.0;
    int neededCtx = (int)std::ceil(durationSeconds * 50.0) + 12;
    int alignedCtx = ((neededCtx + 31) / 32) * 32;
    wparams.audio_ctx = std::max(64, std::min(1500, alignedCtx));

    std::vector<whisper_token> promptTokens;
    if (!promptStr.empty() && !isAuto) {
        promptTokens.resize(16);
        int nTokens = whisper_tokenize(ctx, promptStr.c_str(), promptTokens.data(), (int)promptTokens.size());
        if (nTokens > 0) {
            promptTokens.resize(nTokens);
            wparams.prompt_tokens = promptTokens.data();
            wparams.prompt_n_tokens = (int)promptTokens.size();
        }
    }

    int rc = whisper_full(ctx, wparams, rawSamples, (int)sampleCount);
    env->ReleaseFloatArrayElements(audioSamplesJArr, rawSamples, JNI_ABORT);

    std::ostringstream resultStream;
    if (rc == 0) {
        int nSegments = whisper_full_n_segments(ctx);
        for (int i = 0; i < nSegments; ++i) {
            const char* segText = whisper_full_get_segment_text(ctx, i);
            if (segText) {
                if (i > 0) resultStream << " ";
                resultStream << segText;
            }
        }
    }

    return env->NewStringUTF(resultStream.str().c_str());
}
