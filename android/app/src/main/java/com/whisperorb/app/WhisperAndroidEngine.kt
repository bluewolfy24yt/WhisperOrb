package com.whisperorb.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

class WhisperAndroidEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: WhisperAndroidEngine? = null

        fun getInstance(context: Context): WhisperAndroidEngine {
            return instance ?: synchronized(this) {
                instance ?: WhisperAndroidEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("whisperorb_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var selectedModel: WhisperModelTier
        get() = WhisperModelTier.fromId(
            prefs.getString("selectedModel", HardwareDetector.recommendedTier(context, simulateModest4GBDevice).id)
        )
        set(value) {
            prefs.edit().putString("selectedModel", value.id).apply()
            notifyListeners()
        }

    var selectedLanguage: WhisperLanguageSelection
        get() = WhisperLanguageSelection.fromCode(prefs.getString("selectedLanguage", "system"))
        set(value) {
            prefs.edit().putString("selectedLanguage", value.code).apply()
            notifyListeners()
        }

    var simulateModest4GBDevice: Boolean
        get() = prefs.getBoolean("simulateModest4GBDevice", false)
        set(value) {
            prefs.edit().putBoolean("simulateModest4GBDevice", value).apply()
            if (value && !HardwareDetector.isTierUnlocked(context, selectedModel, true, forceUnlockAllModels)) {
                selectedModel = WhisperModelTier.ZERO_FAUTE
            }
            notifyListeners()
        }

    var forceUnlockAllModels: Boolean
        get() = prefs.getBoolean("forceUnlockAllModels", false)
        set(value) {
            prefs.edit().putBoolean("forceUnlockAllModels", value).apply()
            notifyListeners()
        }

    var bgHex: String
        get() = prefs.getString("bgHex", "#08080C") ?: "#08080C"
        set(value) {
            prefs.edit().putString("bgHex", value).apply()
            notifyListeners()
        }

    var fgHex: String
        get() = prefs.getString("fgHex", "#0088FF") ?: "#0088FF"
        set(value) {
            prefs.edit().putString("fgHex", value).apply()
            notifyListeners()
        }

    var autoSpaceAfterSentence: Boolean
        get() = prefs.getBoolean("autoSpaceAfterSentence", true)
        set(value) {
            prefs.edit().putBoolean("autoSpaceAfterSentence", value).apply()
        }

    var autoPunctuation: Boolean
        get() = prefs.getBoolean("autoPunctuation", true)
        set(value) {
            prefs.edit().putBoolean("autoPunctuation", value).apply()
        }

    var vadSilenceThresholdSeconds: Float
        get() = prefs.getFloat("vadSilenceThresholdSeconds", 0.70f)
        set(value) {
            prefs.edit().putFloat("vadSilenceThresholdSeconds", value).apply()
        }

    var isKeyboardCollapsed: Boolean
        get() = prefs.getBoolean("isKeyboardCollapsed", false)
        set(value) {
            prefs.edit().putBoolean("isKeyboardCollapsed", value).apply()
            notifyListeners()
        }

    var isGboardBarEnabled: Boolean
        get() = prefs.getBoolean("isGboardBarEnabled", true)
        set(value) {
            prefs.edit().putBoolean("isGboardBarEnabled", value).apply()
            notifyListeners()
        }

    var isSmartAutocorrectEnabled: Boolean
        get() = prefs.getBoolean("isSmartAutocorrectEnabled", true)
        set(value) {
            prefs.edit().putBoolean("isSmartAutocorrectEnabled", value).apply()
            notifyListeners()
        }

    var recentEmojis: List<String>
        get() {
            val raw = prefs.getString("recentEmojis", null)
            if (raw.isNullOrBlank()) {
                return listOf("😂", "❤️", "🔥", "👍", "😭", "🙏", "✨", "😍", "🤣", "😎", "🥳", "🚀", "👀", "💯", "🫶", "🤔")
            }
            return raw.split("|").filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString("recentEmojis", value.take(32).joinToString("|")).apply()
        }

    fun recordUsedEmoji(emoji: String) {
        val updated = mutableListOf(emoji)
        for (e in recentEmojis) {
            if (e != emoji) updated.add(e)
        }
        recentEmojis = updated.take(32)
    }

    var orbState: WhisperOrbState = WhisperOrbState.IDLE
        private set

    var currentAudioLevel: Float = 0f
        private set

    var loadedTier: WhisperModelTier? = null
        private set

    var isLoadingModel: Boolean = false
        private set

    var statusBannerMessage: String = "Moteur prêt • Touchez l'Orbe pour dicter"
        private set

    var lastErrorMessage: String? = null
        private set

    var lastTranscriptionLatencyMs: Int = 0
        private set

    var liveTestTranscript: String = ""
    var lastInsertedSentenceLength: Int = 0

    private var nativeContextPtr: Long = 0L
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecordingAudio: Boolean = false
    private val capturedSamples = ArrayList<Float>(16000 * 15)

    private val stateListeners = mutableListOf<() -> Unit>()
    var onTranscribedTextForKeyboard: ((String) -> Unit)? = null
    var onDeleteLastSentenceRequested: ((Int) -> Unit)? = null

    fun addListener(listener: () -> Unit) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    fun notifyListeners() {
        scope.launch {
            for (l in stateListeners.toList()) {
                l.invoke()
            }
        }
    }

    fun loadModel(tier: WhisperModelTier, onReady: (() -> Unit)? = null) {
        if (!HardwareDetector.isTierUnlocked(context, tier, simulateModest4GBDevice, forceUnlockAllModels)) {
            lastErrorMessage = "Le modèle ${tier.displayName} nécessite ${tier.requiredRAMBadgeText}."
            notifyListeners()
            return
        }
        val downloader = ModelDownloader.getInstance(context)
        if (!downloader.isModelDownloaded(tier)) {
            lastErrorMessage = "Veuillez d'abord télécharger ${tier.shortBadgeName} (${tier.sizeMB} Mo)."
            statusBannerMessage = "Téléchargez ${tier.shortBadgeName} dans la section Modèles"
            notifyListeners()
            return
        }
        if (loadedTier == tier && nativeContextPtr != 0L) {
            onReady?.invoke()
            return
        }

        isLoadingModel = true
        lastErrorMessage = null
        statusBannerMessage = "Chargement C++ NDK de ${tier.shortBadgeName} (${tier.ramUsageMB} Mo RAM)..."
        notifyListeners()

        val modelPath = downloader.localModelFile(tier).absolutePath
        scope.launch(Dispatchers.Default) {
            if (nativeContextPtr != 0L) {
                WhisperNativeBridge.freeContext(nativeContextPtr)
                nativeContextPtr = 0L
            }
            val newPtr = WhisperNativeBridge.initContext(modelPath)
            withContext(Dispatchers.Main) {
                nativeContextPtr = newPtr
                loadedTier = if (newPtr != 0L) tier else null
                isLoadingModel = false
                if (newPtr != 0L) {
                    statusBannerMessage = "${tier.shortBadgeName} actif • ${WhisperNativeBridge.getCpuTopologySummary()}"
                    onReady?.invoke()
                } else {
                    lastErrorMessage = "Échec de l'initialisation C++ de ${tier.displayName}."
                    statusBannerMessage = "Erreur de chargement du modèle"
                }
                notifyListeners()
            }
        }
    }

    fun unloadCurrentModel() {
        if (nativeContextPtr != 0L) {
            val ptr = nativeContextPtr
            nativeContextPtr = 0L
            loadedTier = null
            scope.launch(Dispatchers.Default) {
                WhisperNativeBridge.freeContext(ptr)
            }
            notifyListeners()
        }
    }

    fun toggleRecording() {
        when (orbState) {
            WhisperOrbState.IDLE -> startListening()
            WhisperOrbState.LISTENING -> stopListeningAndTranscribe()
            WhisperOrbState.TRANSCRIBING -> {}
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        val downloader = ModelDownloader.getInstance(context)
        var targetTier = selectedModel
        if (!downloader.isModelDownloaded(targetTier) || !HardwareDetector.isTierUnlocked(context, targetTier, simulateModest4GBDevice, forceUnlockAllModels)) {
            val fallback = WhisperModelTier.entries.firstOrNull {
                downloader.isModelDownloaded(it) && HardwareDetector.isTierUnlocked(context, it, simulateModest4GBDevice, forceUnlockAllModels)
            }
            if (fallback != null) {
                selectedModel = fallback
                targetTier = fallback
            } else {
                lastErrorMessage = "Aucun modèle Whisper téléchargé. Téléchargez un modèle ci-dessous."
                statusBannerMessage = "Téléchargez un modèle pour dicter"
                notifyListeners()
                return
            }
        }

        if (nativeContextPtr == 0L || loadedTier != targetTier) {
            loadModel(targetTier) {
                beginMicrophoneCapture()
            }
        } else {
            beginMicrophoneCapture()
        }
    }

    @SuppressLint("MissingPermission")
    private fun beginMicrophoneCapture() {
        if (isRecordingAudio) return
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            lastErrorMessage = "Microphone indisponible ou permission refusée."
            notifyListeners()
            return
        }

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, 16000 * 2)
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                lastErrorMessage = "Autorisez l'accès au Microphone pour WhisperOrb."
                notifyListeners()
                return
            }

            synchronized(capturedSamples) {
                capturedSamples.clear()
            }
            audioRecord = recorder
            isRecordingAudio = true
            orbState = WhisperOrbState.LISTENING
            lastErrorMessage = null
            statusBannerMessage = "Écoute (${selectedLanguage.shortPillLabel}) — Parlez maintenant..."
            notifyListeners()

            recorder.startRecording()

            scope.launch(Dispatchers.IO) {
                val readChunk = ShortArray(1024)
                var activeSpeechSec = 0.0
                var silenceSec = 0.0
                var hasSpoken = false
                var smoothedVisual = 0f

                while (isRecordingAudio) {
                    val readCount = recorder.read(readChunk, 0, readChunk.size)
                    if (readCount > 0) {
                        var sumSq = 0.0
                        synchronized(capturedSamples) {
                            for (i in 0 until readCount) {
                                val normalized = (readChunk[i].toFloat() / 32768.0f).coerceIn(-1f, 1f)
                                capturedSamples.add(normalized)
                                sumSq += (normalized * normalized).toDouble()
                            }
                        }
                        val rms = sqrt(sumSq / readCount.toDouble()).toFloat()
                        val visual = ((rms - 0.003f) * 16f).coerceIn(0f, 1f)
                        smoothedVisual = smoothedVisual * 0.6f + visual * 0.4f

                        withContext(Dispatchers.Main) {
                            currentAudioLevel = smoothedVisual
                            notifyListeners()
                        }

                        val chunkSec = readCount.toDouble() / 16000.0
                        if (rms >= 0.013f) {
                            activeSpeechSec += chunkSec
                            silenceSec = 0.0
                            if (activeSpeechSec >= 0.35) {
                                hasSpoken = true
                            }
                        } else if (hasSpoken) {
                            silenceSec += chunkSec
                            if (silenceSec >= vadSilenceThresholdSeconds.toDouble()) {
                                withContext(Dispatchers.Main) {
                                    stopListeningAndTranscribe()
                                }
                                break
                            }
                        }

                        val totalSec = synchronized(capturedSamples) { capturedSamples.size } / 16000.0
                        if (totalSec >= 28.0) {
                            withContext(Dispatchers.Main) {
                                stopListeningAndTranscribe()
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isRecordingAudio = false
            orbState = WhisperOrbState.IDLE
            lastErrorMessage = "Erreur micro : ${e.message}"
            notifyListeners()
        }
    }

    fun stopListeningAndTranscribe() {
        if (!isRecordingAudio) return
        isRecordingAudio = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        currentAudioLevel = 0f

        val rawSamples: FloatArray = synchronized(capturedSamples) {
            capturedSamples.toFloatArray().also { capturedSamples.clear() }
        }

        if (rawSamples.size < 3200) {
            orbState = WhisperOrbState.IDLE
            statusBannerMessage = "Audio trop court • Prêt"
            notifyListeners()
            return
        }

        val conditioned = conditionStudioAudio(rawSamples)
        if (conditioned.isEmpty()) {
            orbState = WhisperOrbState.IDLE
            statusBannerMessage = "Silence ignoré • Prêt"
            notifyListeners()
            return
        }

        orbState = WhisperOrbState.TRANSCRIBING
        statusBannerMessage = "Transcription C++ NDK..."
        notifyListeners()

        val langSelection = selectedLanguage
        val whisperLangCode = langSelection.resolvedWhisperCode
        val isSmallModel = (loadedTier == WhisperModelTier.NANO || loadedTier == WhisperModelTier.ECLAIR)
        val promptSeed = if (isSmallModel || langSelection == WhisperLanguageSelection.AUTO) "" else langSelection.initialPunctuationPrompt
        val threads = WhisperNativeBridge.getOptimalPerformanceThreadCount()
        val startMs = System.currentTimeMillis()

        scope.launch(Dispatchers.Default) {
            val rawText = WhisperNativeBridge.transcribeAudio(
                contextPtr = nativeContextPtr,
                audioSamples = conditioned,
                languageCode = whisperLangCode,
                initialPrompt = promptSeed,
                numThreads = threads
            )
            val elapsedMs = (System.currentTimeMillis() - startMs).toInt()
            withContext(Dispatchers.Main) {
                orbState = WhisperOrbState.IDLE
                lastTranscriptionLatencyMs = elapsedMs
                handleRawTranscribedResult(rawText, elapsedMs)
            }
        }
    }

    private fun conditionStudioAudio(raw: FloatArray): FloatArray {
        var sum = 0f
        for (s in raw) sum += s
        val mean = sum / raw.size.toFloat()

        val filtered = FloatArray(raw.size)
        var prevX = 0f
        var prevY = 0f
        val alpha = 0.9695f
        var maxPeak = 0f

        for (i in raw.indices) {
            val x = raw[i] - mean
            val y = alpha * (prevY + x - prevX)
            filtered[i] = y
            prevX = x
            prevY = y
            val a = abs(y)
            if (a > maxPeak) maxPeak = a
        }

        if (maxPeak < 0.002f) return FloatArray(0)

        val targetPeak = 0.85f
        val gain = (targetPeak / maxPeak).coerceIn(1.0f, 26.0f)
        for (i in filtered.indices) {
            val amp = filtered[i] * gain
            filtered[i] = when {
                amp > 0.88f -> min(0.99f, 0.88f + 0.11f * tanh((amp - 0.88f) / 0.11f))
                amp < -0.88f -> max(-0.99f, -0.88f + 0.11f * tanh((amp + 0.88f) / 0.11f))
                else -> amp
            }
        }

        val minLen = 10400 // 0.65s @ 16kHz
        if (filtered.size >= minLen) return filtered
        val padded = FloatArray(minLen)
        System.arraycopy(filtered, 0, padded, 0, filtered.size)
        return padded
    }

    private fun handleRawTranscribedResult(rawText: String, latencyMs: Int) {
        val processed = postProcessDictation(rawText)
        if (processed.isNullOrBlank()) {
            statusBannerMessage = "Silence ignoré ($latencyMs ms) • Prêt"
            notifyListeners()
            return
        }

        if (processed == "__COMMAND_DELETE_LAST_SENTENCE__") {
            if (lastInsertedSentenceLength > 0) {
                onDeleteLastSentenceRequested?.invoke(lastInsertedSentenceLength)
                if (liveTestTranscript.length >= lastInsertedSentenceLength) {
                    liveTestTranscript = liveTestTranscript.dropLast(lastInsertedSentenceLength)
                }
                lastInsertedSentenceLength = 0
            }
            statusBannerMessage = "Dernière phrase annulée ($latencyMs ms)"
            notifyListeners()
            return
        }

        val withSpace = if (autoSpaceAfterSentence && !processed.endsWith("\n")) "$processed " else processed
        lastInsertedSentenceLength = withSpace.length
        liveTestTranscript += withSpace

        // Sur Googlebook 14 / Chromebook / Clavier physique attaché, copie aussi dans le presse-papier (Ctrl+V prêt)
        if (HardwareDetector.isChromebookOrGooglebook(context) || HardwareDetector.isHardwareKeyboardAttached(context)) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("WhisperOrb", withSpace))
        }

        onTranscribedTextForKeyboard?.invoke(withSpace)
        statusBannerMessage = "Transcrit en $latencyMs ms • Inséré au clavier"
        notifyListeners()
    }

    private fun postProcessDictation(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null

        val hallucinations = listOf(
            "[BLANK_AUDIO]", "[blank_audio]", "(blank_audio)",
            "[ Musique ]", "[Musique]", "[ Music ]", "[Music]",
            "[ Silence ]", "[Silence]",
            "Sous-titres réalisés par la communauté d'Amara.org",
            "Sous-titres réalisés",
            "Thank you for watching", "Thanks for watching",
            "Merci d'avoir regardé"
        )
        for (h in hallucinations) {
            text = text.replace(h, "", ignoreCase = true)
        }
        text = text.trim()
        if (text.isEmpty() || text == "." || text == ".." || text == "...") return null

        val lower = text.lowercase().trim(' ', '.', ',', '!', '?', ';', ':')
        if (lower == "effacer la dernière phrase" ||
            lower == "effacer la derniere phrase" ||
            lower == "annuler la dernière phrase" ||
            lower == "delete last sentence" ||
            lower == "undo last sentence"
        ) {
            return "__COMMAND_DELETE_LAST_SENTENCE__"
        }

        if (lower == "à la ligne" || lower == "a la ligne" || lower == "nouveau paragraphe" || lower == "new line" || lower == "new paragraph") {
            return "\n"
        }

        if (autoPunctuation) {
            if (text.isNotEmpty() && text[0].isLetter()) {
                text = text[0].uppercaseChar() + text.substring(1)
            }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            if (words >= 3 && text.last().isLetterOrDigit()) {
                text += "."
            }
        }

        text = UserStyleMemoryManager.getInstance(context)
            .applyStyleMemory(text, selectedLanguage.effectiveStyleLanguageCode)

        return text.ifBlank { null }
    }
}
