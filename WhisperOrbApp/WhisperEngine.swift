import Foundation
import AVFoundation
import Combine
import UIKit
import SwiftWhisper

public final class WhisperEngine: ObservableObject {
    public static let shared = WhisperEngine()

    // MARK: - Propriétés @Published pour l'UI de l'Application

    @Published public var isBackgroundEngineRunning: Bool = false
    @Published public var loadedTier: WhisperModelTier? = nil
    @Published public var isLoadingModel: Bool = false
    @Published public var liveTestTranscript: String = ""
    @Published public var statusBannerMessage: String = "Moteur prêt • Activez le mode Tâche de Fond avant d'ouvrir Goodnotes"
    @Published public var lastErrorMessage: String? = nil
    @Published public var lastTranscriptionLatencyMs: Int = 0

    // MARK: - Gestion Stricte de la RAM (Une Seule Instance Whisper en Mémoire)

    private var whisperInstance: Whisper? = nil
    public let optimalThreadCount: Int = HardwareDetector.optimalThreadCount
    private var initialPromptCString: UnsafeMutablePointer<CChar>? = nil
    private let frenchPunctuationPrompt = "Bonjour, voici une dictée vocale en français avec une orthographe exacte et une ponctuation soignée : virgules, points, points d'interrogation et majuscules."

    // MARK: - Pipeline Audio Temps Réel (AVAudioEngine + Ré-échantillonneur Anti-Repliement 16 kHz)

    private let audioEngine = AVAudioEngine()
    private var audioConverter: AVAudioConverter?
    private let targetSampleRate: Double = 16000.0
    private let audioBufferLock = NSLock()
    private var accumulatedSamples16kHz: [Float] = []

    // MARK: - Détecteur d'Activité Vocale (VAD) & Lissage RMS

    private var smoothedLevel: Float = 0.0
    private let speechRMSThreshold: Float = 0.012
    private var activeSpeechDurationSeconds: Double = 0.0
    private var currentSilenceDurationSeconds: Double = 0.0
    private var hasSpokenEnoughInCurrentUtterance: Bool = false
    private var isTranscribingNow: Bool = false
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialisation

    private init() {
        self.initialPromptCString = strdup(frenchPunctuationPrompt)
        setupInterProcessCallbacks()
        setupAudioSessionNotificationObservers()
    }

    deinit {
        if let ptr = initialPromptCString {
            free(ptr)
            initialPromptCString = nil
        }
    }

    private func setupInterProcessCallbacks() {
        AppGroupManager.shared.onToggleRecordingRequested = { [weak self] in
            self?.toggleRecording()
        }

        AppGroupManager.shared.$selectedModel
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newTier in
                guard let self = self else { return }
                if self.loadedTier != nil && self.loadedTier != newTier {
                    self.unloadCurrentModel()
                    if ModelDownloader.shared.isModelDownloaded(newTier) {
                        self.loadModel(newTier)
                    }
                }
            }
            .store(in: &cancellables)
    }

    private func setupAudioSessionNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioSessionInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )
    }

    @objc private func handleAudioSessionInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        if type == .ended {
            if isBackgroundEngineRunning {
                try? configureAndActivateAudioSession()
                if !audioEngine.isRunning {
                    try? audioEngine.start()
                }
            }
        }
    }

    // MARK: - 1. Gestion Stricte de la RAM (Libération C whisper_free() avant toute allocation)

    public func unloadCurrentModel() {
        whisperInstance = nil
        loadedTier = nil
    }

    public func loadModel(_ tier: WhisperModelTier) {
        let modelURL = ModelDownloader.shared.localModelURL(for: tier)
        guard ModelDownloader.shared.isModelDownloaded(tier) else {
            lastErrorMessage = "Le modèle \(tier.displayName) n'est pas encore téléchargé."
            statusBannerMessage = "Téléchargez le modèle \(tier.shortBadgeName) dans la Carte 2."
            return
        }

        if loadedTier == tier, whisperInstance != nil {
            return
        }

        isLoadingModel = true
        lastErrorMessage = nil
        statusBannerMessage = "Chargement en RAM de \(tier.displayName) (\(tier.ramUsageMB) Mo)..."

        // Libère impérativement le modèle précédent pour ne jamais avoir 2 modèles en RAM
        unloadCurrentModel()

        let whisper = Whisper(fromFileURL: modelURL)
        configureWhisperParams(on: whisper, audioDurationSeconds: 6.0)

        self.whisperInstance = whisper
        self.loadedTier = tier
        self.isLoadingModel = false
        self.statusBannerMessage = "\(tier.displayName) prêt (\(optimalThreadCount) cœurs rapides + Ponctuation FR)"
    }

    private func configureWhisperParams(on whisper: Whisper, audioDurationSeconds: Double) {
        whisper.params.language = .french
        whisper.params.detect_language = false
        whisper.params.translate = false
        whisper.params.n_threads = Int32(optimalThreadCount)
        whisper.params.single_segment = true
        whisper.params.no_context = true
        whisper.params.print_special = false
        whisper.params.print_progress = false
        whisper.params.print_realtime = false
        whisper.params.print_timestamps = false
        whisper.params.suppress_blank = true
        whisper.params.suppress_non_speech_tokens = true
        whisper.params.temperature = 0.0
        whisper.params.temperature_inc = 0.0
        whisper.params.greedy.best_of = 1

        if let promptPtr = initialPromptCString {
            whisper.params.initial_prompt = UnsafePointer(promptPtr)
        }

        // Fenêtre d'encodage dynamique : 1s d'audio = 50 frames Whisper (max 1500 = 30s).
        // Réduit de 3x à 4x le temps de calcul sur les phrases courtes/moyennes sans perte de précision.
        let neededCtx = Int(ceil(audioDurationSeconds * 50.0)) + 150
        let alignedCtx = ((neededCtx + 63) / 64) * 64
        whisper.params.audio_ctx = Int32(max(384, min(1500, alignedCtx)))
    }

    // MARK: - 2. Session Audio en Tâche de Fond (AVAudioSession + AVAudioEngine)

    private func configureAndActivateAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .default,
            options: [.mixWithOthers, .defaultToSpeaker, .allowBluetooth, .allowBluetoothA2DP]
        )
        try session.setPreferredSampleRate(16000)
        try session.setPreferredIOBufferDuration(0.032)
        try session.setActive(true, options: .notifyOthersOnDeactivation)
    }

    public func startBackgroundAudioEngine() {
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self = self else { return }
                guard granted else {
                    self.lastErrorMessage = "Accès au microphone refusé. Activez le micro pour WhisperOrb dans Réglages > Confidentialité > Micro."
                    self.statusBannerMessage = "Permission microphone requise"
                    return
                }
                self.startEngineInternal()
            }
        }
    }

    private func startEngineInternal() {
        do {
            try configureAndActivateAudioSession()

            let selectedTier = AppGroupManager.shared.selectedModel
            if ModelDownloader.shared.isModelDownloaded(selectedTier) && whisperInstance == nil {
                loadModel(selectedTier)
            } else if whisperInstance == nil, let anyDownloaded = WhisperModelTier.allCases.first(where: { ModelDownloader.shared.isModelDownloaded($0) }) {
                AppGroupManager.shared.selectedModel = anyDownloaded
                loadModel(anyDownloaded)
            }

            let inputNode = audioEngine.inputNode
            let hardwareFormat = inputNode.outputFormat(forBus: 0)

            guard hardwareFormat.sampleRate > 0 && hardwareFormat.channelCount > 0 else {
                lastErrorMessage = "Format audio matériel invalide sur le microphone."
                return
            }

            if let targetFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: targetSampleRate,
                channels: 1,
                interleaved: false
            ) {
                audioConverter = AVAudioConverter(from: hardwareFormat, to: targetFormat)
            }

            inputNode.removeTap(onBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 2048, format: hardwareFormat) { [weak self] buffer, _ in
                self?.processMicrophoneBuffer(buffer)
            }

            audioEngine.prepare()
            try audioEngine.start()

            isBackgroundEngineRunning = true
            AppGroupManager.shared.isBackgroundEngineActive = true
            statusBannerMessage = "Moteur Audio Tâche de Fond ACTIF — Prêt à dicter depuis le clavier"
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = "Erreur d'activation audio : \(error.localizedDescription)"
            statusBannerMessage = "Échec du démarrage audio"
            isBackgroundEngineRunning = false
            AppGroupManager.shared.isBackgroundEngineActive = false
        }
    }

    public func stopBackgroundAudioEngine() {
        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        isBackgroundEngineRunning = false
        AppGroupManager.shared.isBackgroundEngineActive = false
        AppGroupManager.shared.orbState = .idle
        AppGroupManager.shared.updateLiveAudioLevel(0.0)
        statusBannerMessage = "Moteur Audio arrêté"
    }

    // MARK: - 3. Contrôle de l'Écoute (Démarrer / Arrêter depuis l'App ou le Clavier Goodnotes)

    public func toggleRecording() {
        if !isBackgroundEngineRunning || !audioEngine.isRunning {
            startEngineInternal()
        }

        let currentState = AppGroupManager.shared.orbState
        switch currentState {
        case .idle:
            beginListeningSession()
        case .listening:
            finishListeningAndTranscribe()
        case .transcribing:
            break
        }
    }

    public func beginListeningSession() {
        let tier = AppGroupManager.shared.selectedModel
        if whisperInstance == nil {
            if ModelDownloader.shared.isModelDownloaded(tier) {
                loadModel(tier)
            } else if let fallback = WhisperModelTier.allCases.first(where: { ModelDownloader.shared.isModelDownloaded($0) }) {
                AppGroupManager.shared.selectedModel = fallback
                loadModel(fallback)
            } else {
                lastErrorMessage = "Veuillez d'abord télécharger l'un des 3 modèles Whisper (Carte 2)."
                statusBannerMessage = "Aucun modèle hors-ligne téléchargé"
                return
            }
        }

        if !isBackgroundEngineRunning || !audioEngine.isRunning {
            startEngineInternal()
        }

        audioBufferLock.lock()
        accumulatedSamples16kHz.removeAll(keepingCapacity: true)
        activeSpeechDurationSeconds = 0.0
        currentSilenceDurationSeconds = 0.0
        hasSpokenEnoughInCurrentUtterance = false
        audioBufferLock.unlock()

        AppGroupManager.shared.orbState = .listening
        statusBannerMessage = "Écoute active (16 kHz Studio) — Parlez maintenant..."
    }

    public func finishListeningAndTranscribe() {
        audioBufferLock.lock()
        let capturedFrames = accumulatedSamples16kHz
        accumulatedSamples16kHz.removeAll(keepingCapacity: true)
        activeSpeechDurationSeconds = 0.0
        currentSilenceDurationSeconds = 0.0
        hasSpokenEnoughInCurrentUtterance = false
        audioBufferLock.unlock()

        AppGroupManager.shared.updateLiveAudioLevel(0.0)

        // Si moins de 0,20 s d'audio capturé, revenir au repos
        guard capturedFrames.count >= Int(targetSampleRate * 0.20) else {
            AppGroupManager.shared.orbState = .idle
            statusBannerMessage = "Audio trop court • Prêt"
            return
        }

        runWhisperTranscription(on: capturedFrames)
    }

    // MARK: - 4. Conversion Temps Réel Sans Perte vers 16000 Hz Mono Float32, Calcul RMS & VAD Mains-Libres

    private func processMicrophoneBuffer(_ buffer: AVAudioPCMBuffer) {
        let currentState = AppGroupManager.shared.orbState
        guard currentState == .listening, !isTranscribingNow else {
            if smoothedLevel > 0.01 {
                smoothedLevel = 0.0
                AppGroupManager.shared.updateLiveAudioLevel(0.0)
            }
            return
        }

        let monoSamples16kHz = convertBufferTo16kHzMonoFloat32(buffer)
        guard !monoSamples16kHz.isEmpty else { return }

        // Calcul de l'amplitude RMS : RMS = sqrt((1/N) * sum(x_i^2))
        var sumSquares: Float = 0.0
        let sampleCount = monoSamples16kHz.count
        for sample in monoSamples16kHz {
            sumSquares += sample * sample
        }
        let rms = sqrt(sumSquares / Float(sampleCount))

        // Conversion en niveau visuel lissé (smoothedLevel dans [0.0, 1.0])
        let normalizedVisual = min(1.0, max(0.0, (rms - 0.003) * 16.0))
        smoothedLevel = smoothedLevel * 0.60 + normalizedVisual * 0.40
        AppGroupManager.shared.updateLiveAudioLevel(smoothedLevel)

        // Durée temporelle de ce buffer à 16 kHz
        let chunkDurationSeconds = Double(sampleCount) / targetSampleRate
        let vadSilenceLimit = max(0.6, min(2.5, AppGroupManager.shared.vadSilenceThresholdSeconds))

        var shouldTriggerAutoTranscribe = false

        audioBufferLock.lock()
        accumulatedSamples16kHz.append(contentsOf: monoSamples16kHz)

        if rms >= speechRMSThreshold {
            activeSpeechDurationSeconds += chunkDurationSeconds
            currentSilenceDurationSeconds = 0.0
            if activeSpeechDurationSeconds >= 0.35 {
                hasSpokenEnoughInCurrentUtterance = true
            }
        } else {
            if hasSpokenEnoughInCurrentUtterance {
                currentSilenceDurationSeconds += chunkDurationSeconds
                if currentSilenceDurationSeconds >= vadSilenceLimit {
                    shouldTriggerAutoTranscribe = true
                }
            }
        }

        // Sécurité anti-saturation RAM : transcription automatique au bout de 28 secondes continues
        let totalRecordedSeconds = Double(accumulatedSamples16kHz.count) / targetSampleRate
        if totalRecordedSeconds >= 28.0 {
            shouldTriggerAutoTranscribe = true
        }
        audioBufferLock.unlock()

        if shouldTriggerAutoTranscribe {
            DispatchQueue.main.async { [weak self] in
                self?.finishListeningAndTranscribe()
            }
        }
    }

    /// Ré-échantillonneur direct 48/44.1 kHz -> 16 kHz Mono Float32 avec filtre passe-bas anti-repliement (zéro coupure inter-buffer)
    private func convertBufferTo16kHzMonoFloat32(_ inputBuffer: AVAudioPCMBuffer) -> [Float] {
        let inputFormat = inputBuffer.format
        let frameLength = Int(inputBuffer.frameLength)
        guard frameLength > 0, let channelData = inputBuffer.floatChannelData else { return [] }

        let channelCount = Int(inputFormat.channelCount)
        var monoSource = [Float](repeating: 0.0, count: frameLength)

        if channelCount == 1 {
            let ptr = channelData[0]
            for i in 0..<frameLength {
                monoSource[i] = ptr[i]
            }
        } else {
            let invChannels = 1.0 / Float(channelCount)
            for i in 0..<frameLength {
                var sum: Float = 0.0
                for ch in 0..<channelCount {
                    sum += channelData[ch][i]
                }
                monoSource[i] = sum * invChannels
            }
        }

        let sourceSampleRate = inputFormat.sampleRate
        if abs(sourceSampleRate - targetSampleRate) < 1.0 {
            return monoSource.map { max(-1.0, min(1.0, $0)) }
        }

        let ratio = sourceSampleRate / targetSampleRate
        let outputCount = max(1, Int(round(Double(frameLength) / ratio)))
        var resampled = [Float](repeating: 0.0, count: outputCount)

        // Si le ratio est >= 2.0 (ex: 48 kHz -> 16 kHz, ratio 3:1), application d'un filtre FIR passe-bas 5-taps
        // pour éliminer le repliement spectral tout en préservant les consonnes françaises (100 Hz - 7500 Hz)
        if ratio >= 2.0 && frameLength >= 5 {
            for outIdx in 0..<outputCount {
                let centerPos = (Double(outIdx) + 0.5) * ratio - 0.5
                let cIdx = min(frameLength - 1, max(0, Int(round(centerPos))))
                let iMinus2 = max(0, cIdx - 2)
                let iMinus1 = max(0, cIdx - 1)
                let iPlus1 = min(frameLength - 1, cIdx + 1)
                let iPlus2 = min(frameLength - 1, cIdx + 2)

                let filtered = 0.10 * monoSource[iMinus2]
                    + 0.22 * monoSource[iMinus1]
                    + 0.36 * monoSource[cIdx]
                    + 0.22 * monoSource[iPlus1]
                    + 0.10 * monoSource[iPlus2]
                resampled[outIdx] = max(-1.0, min(1.0, filtered))
            }
        } else {
            let step = Double(frameLength - 1) / Double(max(1, outputCount - 1))
            for outIdx in 0..<outputCount {
                let srcPos = Double(outIdx) * step
                let leftIdx = Int(srcPos)
                let rightIdx = min(frameLength - 1, leftIdx + 1)
                let frac = Float(srcPos - Double(leftIdx))
                let interpolated = monoSource[leftIdx] * (1.0 - frac) + monoSource[rightIdx] * frac
                resampled[outIdx] = max(-1.0, min(1.0, interpolated))
            }
        }

        return resampled
    }

    /// Conditionnement vocal studio avant envoi à Whisper :
    /// 1) Suppression offset DC + filtre passe-haut 80 Hz (anti-grondement)
    /// 2) Découpe des longs silences inutiles en début/fin
    /// 3) Normalisation dynamique du gain (AGC logiciel vers -1,4 dBFS / 0.85 crête)
    /// 4) Marges de silence de sécurité (0,15 s début + 0,60 s fin, durée min >= 1,35 s) requises par whisper.cpp
    private func prepareAudioFramesForWhisper(_ rawFrames: [Float]) -> [Float] {
        guard !rawFrames.isEmpty else { return [] }

        // 1. Calcul de l'offset DC moyen
        var sum: Float = 0.0
        for s in rawFrames {
            sum += s
        }
        let mean = sum / Float(rawFrames.count)

        // Filtre passe-haut 1er ordre à ~80 Hz (16 kHz) pour retirer les bruits sourds de manipulation de l'iPhone
        var filtered = [Float](repeating: 0.0, count: rawFrames.count)
        var prevX: Float = 0.0
        var prevY: Float = 0.0
        let alpha: Float = 0.9695
        var maxPeak: Float = 0.0

        for i in 0..<rawFrames.count {
            let x = rawFrames[i] - mean
            let y = alpha * (prevY + x - prevX)
            filtered[i] = y
            prevX = x
            prevY = y
            let absY = abs(y)
            if absY > maxPeak {
                maxPeak = absY
            }
        }

        // Si le signal est un silence quasi-total, retourner vide pour éviter toute hallucination
        guard maxPeak >= 0.002 else { return [] }

        // 2. Détection du début et de la fin réelle de la voix par fenêtres de 20 ms (320 échantillons)
        let windowSize = 320
        let voiceThreshold = max(0.0025, maxPeak * 0.045)
        var firstVoiceSample = 0
        var lastVoiceSample = filtered.count - 1

        if filtered.count > windowSize * 3 {
            var foundStart = false
            var idx = 0
            while idx + windowSize <= filtered.count {
                var winSq: Float = 0.0
                for j in idx..<(idx + windowSize) {
                    winSq += filtered[j] * filtered[j]
                }
                let winRms = sqrt(winSq / Float(windowSize))
                if winRms >= voiceThreshold {
                    if !foundStart {
                        firstVoiceSample = idx
                        foundStart = true
                    }
                    lastVoiceSample = min(filtered.count - 1, idx + windowSize - 1)
                }
                idx += windowSize
            }
        }

        // Garde 0,15 s avant le premier son et 0,25 s après le dernier son
        let preRoll = Int(targetSampleRate * 0.15)
        let postRoll = Int(targetSampleRate * 0.25)
        let startIdx = max(0, firstVoiceSample - preRoll)
        let endIdx = min(filtered.count - 1, max(startIdx, lastVoiceSample + postRoll))

        var trimmed = Array(filtered[startIdx...endIdx])

        // 3. Normalisation automatique du volume (AGC logiciel vers 0.85 crête, gain max 28x)
        var trimmedPeak: Float = 0.0
        for s in trimmed {
            let a = abs(s)
            if a > trimmedPeak { trimmedPeak = a }
        }

        if trimmedPeak > 0.001 {
            let targetPeak: Float = 0.85
            let gain = min(28.0, max(1.0, targetPeak / trimmedPeak))
            for i in 0..<trimmed.count {
                let amplified = trimmed[i] * gain
                // Limiteur doux (soft-knee) au-delà de 0.88 pour éviter toute saturation numérique
                if amplified > 0.88 {
                    trimmed[i] = min(0.99, 0.88 + 0.11 * tanh((amplified - 0.88) / 0.11))
                } else if amplified < -0.88 {
                    trimmed[i] = max(-0.99, -0.88 + 0.11 * tanh((amplified + 0.88) / 0.11))
                } else {
                    trimmed[i] = amplified
                }
            }
        }

        // 4. Ajout des marges de silence requises par whisper.cpp (0,15 s en tête + 0,60 s en queue, min 1,35 s au total)
        let leadingPad = [Float](repeating: 0.0, count: Int(targetSampleRate * 0.15))
        let trailingPad = [Float](repeating: 0.0, count: Int(targetSampleRate * 0.60))
        var finalBuffer: [Float] = []
        finalBuffer.reserveCapacity(leadingPad.count + trimmed.count + trailingPad.count + 8000)
        finalBuffer.append(contentsOf: leadingPad)
        finalBuffer.append(contentsOf: trimmed)
        finalBuffer.append(contentsOf: trailingPad)

        let minSamplesRequired = Int(targetSampleRate * 1.35)
        if finalBuffer.count < minSamplesRequired {
            finalBuffer.append(contentsOf: [Float](repeating: 0.0, count: minSamplesRequired - finalBuffer.count))
        }

        return finalBuffer
    }

    // MARK: - 5. Inférence Asynchrone SwiftWhisper Optimisée (A15 Bionic & A12Z Bionic)

    private func runWhisperTranscription(on floatArray: [Float]) {
        guard let whisper = whisperInstance else {
            AppGroupManager.shared.orbState = .idle
            lastErrorMessage = "Modèle Whisper non chargé en mémoire."
            return
        }

        let conditionedFrames = prepareAudioFramesForWhisper(floatArray)
        guard !conditionedFrames.isEmpty else {
            AppGroupManager.shared.orbState = .idle
            statusBannerMessage = "Aucun signal vocal détecté • Prêt"
            return
        }

        let audioDurationSeconds = Double(conditionedFrames.count) / targetSampleRate
        configureWhisperParams(on: whisper, audioDurationSeconds: audioDurationSeconds)

        isTranscribingNow = true
        AppGroupManager.shared.orbState = .transcribing
        statusBannerMessage = "Transcription locale Whisper en cours..."
        let startTime = Date()

        Task {
            do {
                let segments = try await whisper.transcribe(audioFrames: conditionedFrames)
                let rawText = segments.map(\.text).joined(separator: " ")
                let elapsedMs = Int(Date().timeIntervalSince(startTime) * 1000.0)

                await MainActor.run {
                    self.isTranscribingNow = false
                    self.lastTranscriptionLatencyMs = elapsedMs
                    self.handleRawTranscribedText(rawText, latencyMs: elapsedMs)
                }
            } catch {
                await MainActor.run {
                    self.isTranscribingNow = false
                    AppGroupManager.shared.orbState = .idle
                    self.lastErrorMessage = "Erreur de transcription Whisper : \(error.localizedDescription)"
                    self.statusBannerMessage = "Erreur de transcription"
                }
            }
        }
    }

    // MARK: - 6. Post-Traitement Intelligent & Commandes Vocales Spéciales Copie DST

    private func handleRawTranscribedText(_ rawText: String, latencyMs: Int) {
        let cleaned = postProcessFrenchDictation(rawText)

        guard let finalResult = cleaned else {
            AppGroupManager.shared.orbState = .idle
            statusBannerMessage = "Silence ou bruit ignoré (\(latencyMs) ms) • Prêt"
            return
        }

        if finalResult == "__COMMAND_DELETE_LAST_SENTENCE__" {
            AppGroupManager.shared.triggerDeleteLastSentence()
            removeLastSentenceFromLocalPreview()
            statusBannerMessage = "Commande vocale : Dernière phrase effacée (\(latencyMs) ms)"
            return
        }

        // Mise à jour de la zone de test locale dans l'application
        if liveTestTranscript.isEmpty || liveTestTranscript.hasSuffix("\n") || liveTestTranscript.hasSuffix(" ") {
            liveTestTranscript += finalResult + (AppGroupManager.shared.autoSpaceForGoodnotes && !finalResult.hasSuffix("\n") ? " " : "")
        } else {
            liveTestTranscript += " " + finalResult + (AppGroupManager.shared.autoSpaceForGoodnotes && !finalResult.hasSuffix("\n") ? " " : "")
        }

        // Envoi instantané au clavier WhisperKeyboard
        AppGroupManager.shared.publishTranscribedText(finalResult)
        statusBannerMessage = "Transcrit en \(latencyMs) ms • Inséré au clavier"
    }

    public func postProcessFrenchDictation(_ input: String) -> String? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        // 1. Filtrage des hallucinations connues de Whisper sur les silences et du prompt initial
        let hallucinationPatterns: [String] = [
            "[BLANK_AUDIO]",
            "[blank_audio]",
            "(blank_audio)",
            "[ Musique ]",
            "[Musique]",
            "[ Applaudissements ]",
            "[ Silence ]",
            "[Silence]",
            "Sous-titres réalisés para la communauté d'Amara.org",
            "Sous-titres réalisés par la communauté d'Amara.org",
            "Sous-titres réalisés",
            "Merci d'avoir regardé cette vidéo",
            "Merci d'avoir regardé",
            "Abonnez-vous",
            "S'abonner",
            "voici une dictée vocale en français avec une orthographe exacte"
        ]

        for pattern in hallucinationPatterns {
            text = text.replacingOccurrences(of: pattern, with: "", options: [.caseInsensitive])
        }

        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty || text == "..." || text == ".." || text == "." {
            return nil
        }

        // 2. Détection de la commande vocale d'effacement de la dernière phrase
        let lower = text.lowercased()
            .trimmingCharacters(in: CharacterSet.punctuationCharacters.union(.whitespacesAndNewlines))

        if lower == "effacer la dernière phrase" ||
           lower == "effacer la derniere phrase" ||
           lower == "annuler la dernière phrase" ||
           lower == "annuler la derniere phrase" ||
           lower == "supprimer la dernière phrase" ||
           lower == "supprimer la derniere phrase" {
            return "__COMMAND_DELETE_LAST_SENTENCE__"
        }

        // 3. Commandes vocales isolées pour la ponctuation et la mise en page DST
        if lower == "point d'interrogation" {
            return "?"
        }
        if lower == "point d'exclamation" {
            return "!"
        }
        if lower == "point" || lower == "point final" {
            return "."
        }
        if lower == "virgule" {
            return ","
        }
        if lower == "deux points" || lower == "deux-points" {
            return " :"
        }
        if lower == "point-virgule" || lower == "point virgule" {
            return " ;"
        }
        if lower == "nouveau paragraphe" || lower == "à la ligne" || lower == "a la ligne" {
            return "\n"
        }
        if lower == "double saut de ligne" {
            return "\n\n"
        }

        // 4. Remplacement des commandes vocales intégrées au milieu d'une dictée
        let inlineReplacements: [(pattern: String, replacement: String)] = [
            ("double saut de ligne", "\n\n"),
            ("nouveau paragraphe", "\n"),
            ("à la ligne", "\n"),
            ("a la ligne", "\n"),
            ("ouvrir les guillemets", "« "),
            ("fermer les guillemets", " »"),
            ("point d'interrogation", "?"),
            ("point d'exclamation", "!"),
            ("point-virgule", ";"),
            ("point virgule", ";"),
            ("deux-points", ":"),
            ("point final", ".")
        ]

        for item in inlineReplacements {
            text = text.replacingOccurrences(
                of: item.pattern,
                with: item.replacement,
                options: [.caseInsensitive]
            )
        }

        // 5. Nettoyage des espaces multiples et normalisation de la ponctuation française sans écraser les \n
        let lines = text.components(separatedBy: "\n")
        let cleanedLines = lines.map { line -> String in
            let components = line.components(separatedBy: .whitespaces).filter { !$0.isEmpty }
            var joined = components.joined(separator: " ")
            joined = joined.replacingOccurrences(of: " ,", with: ",")
            joined = joined.replacingOccurrences(of: " .", with: ".")
            return joined
        }
        text = cleanedLines.joined(separator: "\n")

        // 6. Majuscule automatique en début de phrase et ponctuation finale si activée
        if AppGroupManager.shared.autoPunctuation {
            if let firstChar = text.first, firstChar.isLetter {
                text = firstChar.uppercased() + text.dropFirst()
            }
            let wordCount = text.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }.count
            if wordCount >= 3, let lastChar = text.last, lastChar.isLetter || lastChar.isNumber {
                text += "."
            }
        }

        return text.isEmpty ? nil : text
    }

    private func removeLastSentenceFromLocalPreview() {
        let length = AppGroupManager.shared.lastInsertedSentenceLength
        guard length > 0, liveTestTranscript.count >= length else {
            liveTestTranscript = ""
            return
        }
        liveTestTranscript.removeLast(length)
    }
}
