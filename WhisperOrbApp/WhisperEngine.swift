import Foundation
import AVFoundation
import Combine
import UIKit
import whisper

public final class WhisperEngine: ObservableObject {
    public static let shared = WhisperEngine()

    // MARK: - Propriétés @Published pour l'UI de l'Application

    @Published public var isBackgroundEngineRunning: Bool = false
    @Published public var loadedTier: WhisperModelTier? = nil
    @Published public var isLoadingModel: Bool = false
    @Published public var liveTestTranscript: String = ""
    @Published public var statusBannerMessage: String = "Moteur prêt • Activez l'écoute en arrière-plan pour dicter partout"
    @Published public var lastErrorMessage: String? = nil
    @Published public var lastTranscriptionLatencyMs: Int = 0
    @Published public var activeBackendLabel: String = "CPU"

    // MARK: - Gestion Stricte de la RAM & Accélération GPU Metal + FlashAttention (whisper.xcframework)

    private var whisperContext: OpaquePointer? = nil
    private var contextUsesGPU: Bool = false      // lu/écrit sur inferenceQueue
    private var isGPUBackendLoaded: Bool = false  // miroir pour le thread principal
    private var shouldResumeAfterInterruption: Bool = false
    private var resumeListeningAfterTranscription: Bool = false
    private var loadingTier: WhisperModelTier? = nil
    private let inferenceQueue = DispatchQueue(label: "com.bluewolfy24yt.whisperorb.metal.inference", qos: .userInteractive)
    public let optimalThreadCount: Int = HardwareDetector.optimalThreadCount
    private var activeLangCString: UnsafeMutablePointer<CChar>? = nil
    private var cachedPromptTokens: [whisper_token] = []

    // MARK: - Pipeline Audio Temps Réel (AVAudioEngine + Ré-échantillonneur Anti-Repliement 16 kHz)

    private var audioEngine = AVAudioEngine()
    private var audioConverter: AVAudioConverter?
    private let targetSampleRate: Double = 16000.0
    private let audioBufferLock = NSLock()
    private var accumulatedSamples16kHz: [Float] = []
    // Copies protégées par audioBufferLock : le thread audio ne lit jamais AppGroupManager
    private var isCaptureEnabled: Bool = false
    private var vadSilenceSeconds: Double = 1.45

    // MARK: - Détecteur d'Activité Vocale (VAD) & Lissage RMS

    private var smoothedLevel: Float = 0.0
    private let speechRMSThreshold: Float = 0.012
    private var activeSpeechDurationSeconds: Double = 0.0
    private var currentSilenceDurationSeconds: Double = 0.0
    private var hasSpokenEnoughInCurrentUtterance: Bool = false
    private var recentRMSWindow: [Float] = []
    private var isTranscribingNow: Bool = false
    private var lastHeartbeatTime: TimeInterval = 0
    private var heartbeatTimer: Timer? = nil
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialisation

    private init() {
        let initialLang = AppGroupManager.shared.selectedLanguage.resolvedWhisperCode
        self.activeLangCString = strdup(initialLang)
        setupInterProcessCallbacks()
        setupAudioSessionNotificationObservers()
    }

    deinit {
        heartbeatTimer?.invalidate()
        if let ctx = whisperContext {
            whisper_free(ctx)
            whisperContext = nil
        }
        if let langPtr = activeLangCString {
            free(langPtr)
            activeLangCString = nil
        }
    }

    private func setupInterProcessCallbacks() {
        AppGroupManager.shared.onToggleRecordingRequested = { [weak self] in
            self?.toggleRecording()
        }

        AppGroupManager.shared.$orbState
            .sink { [weak self] state in
                guard let self = self else { return }
                self.audioBufferLock.lock()
                self.isCaptureEnabled = (state == .listening)
                self.audioBufferLock.unlock()
            }
            .store(in: &cancellables)

        AppGroupManager.shared.$vadSilenceThresholdSeconds
            .sink { [weak self] seconds in
                guard let self = self else { return }
                self.audioBufferLock.lock()
                self.vadSilenceSeconds = seconds
                self.audioBufferLock.unlock()
            }
            .store(in: &cancellables)

        AppGroupManager.shared.$selectedModel
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newTier in
                guard let self = self else { return }
                if ModelDownloader.shared.isModelDownloaded(newTier) {
                    if self.loadedTier != newTier && self.loadingTier != newTier {
                        self.loadModel(newTier)
                    }
                } else {
                    self.statusBannerMessage = "Téléchargez \(newTier.shortBadgeName) dans la Carte 2 pour l'activer."
                }
            }
            .store(in: &cancellables)

        AppGroupManager.shared.$selectedLanguage
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newLang in
                guard let self = self else { return }
                self.updateActiveLanguagePointer(to: newLang)
            }
            .store(in: &cancellables)
    }

    private func updateActiveLanguagePointer(to selection: WhisperLanguageSelection) {
        let code = selection.resolvedWhisperCode
        let promptSeed = selection.initialPunctuationPrompt

        inferenceQueue.async { [weak self] in
            guard let self = self else { return }
            if let old = self.activeLangCString {
                free(old)
            }
            self.activeLangCString = strdup(code)

            if let validCtx = self.whisperContext {
                self.cachedPromptTokens = Self.tokenizePrompt(promptSeed, ctx: validCtx)
            }
        }
    }

    private func setupAudioSessionNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMemoryWarning(_:)),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioSessionInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioEngineConfigurationChange(_:)),
            name: .AVAudioEngineConfigurationChange,
            object: audioEngine
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMediaServicesWereReset(_:)),
            name: AVAudioSession.mediaServicesWereResetNotification,
            object: AVAudioSession.sharedInstance()
        )
    }

    @objc private func handleAudioSessionInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        if type == .began {
            // Transcrit ce qui a déjà été capté au lieu de le perdre
            finishListeningAndTranscribe()
            shouldResumeAfterInterruption = isBackgroundEngineRunning
            isBackgroundEngineRunning = false
            AppGroupManager.shared.clearEngineHeartbeat()
            AppGroupManager.shared.isBackgroundEngineActive = false
        } else if type == .ended {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
                guard let self = self, self.shouldResumeAfterInterruption else { return }
                self.shouldResumeAfterInterruption = false
                self.startEngineInternal()
            }
        }
    }

    @objc private func handleMemoryWarning(_ notification: Notification) {
        guard AppGroupManager.shared.orbState == .idle,
              UIApplication.shared.applicationState == .background else { return }
        unloadCurrentModel()
        statusBannerMessage = "Modèle libéré (mémoire faible) • Rechargé à la prochaine dictée"
    }

    @objc private func handleAudioEngineConfigurationChange(_ notification: Notification) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
            guard let self = self, self.isBackgroundEngineRunning else { return }
            if !self.audioEngine.isRunning {
                self.startEngineInternal()
            }
        }
    }

    @objc private func handleMediaServicesWereReset(_ notification: Notification) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
            guard let self = self, self.isBackgroundEngineRunning else { return }
            // Après ce reset, les objets audio existants sont invalides : il faut les recréer.
            NotificationCenter.default.removeObserver(self, name: .AVAudioEngineConfigurationChange, object: self.audioEngine)
            self.audioEngine = AVAudioEngine()
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(self.handleAudioEngineConfigurationChange(_:)),
                name: .AVAudioEngineConfigurationChange,
                object: self.audioEngine
            )
            self.startEngineInternal()
        }
    }

    // MARK: - 1. Gestion Stricte de la RAM & Initialisation GPU Metal + FlashAttention

    /// Maintient le backend d'inférence prêt sans provoquer de rechargement bloquant lors du passage au clavier.
    public func prepareInferenceBackend(forBackground: Bool) {
        guard let tier = loadedTier, !isLoadingModel, !isTranscribingNow else { return }
        if forBackground {
            guard isGPUBackendLoaded, !HardwareDetector.isRunningOnMac else { return }
            loadModel(tier, reloadBackend: true, forceCPU: true)
            return
        }
        // Si le modèle a été libéré par une alerte mémoire en arrière-plan, on le recharge au retour
        if whisperContext == nil {
            loadModel(tier, reloadBackend: true)
        }
    }

    public var isAudioEngineAlive: Bool {
        isBackgroundEngineRunning && audioEngine.isRunning
    }

    public func unloadCurrentModel() {
        loadedTier = nil
        loadingTier = nil
        isGPUBackendLoaded = false
        inferenceQueue.async { [weak self] in
            guard let self = self else { return }
            autoreleasepool {
                if let ctx = self.whisperContext {
                    whisper_free(ctx)
                    self.whisperContext = nil
                }
                self.contextUsesGPU = false
                self.cachedPromptTokens = []
            }
        }
    }

    public func loadModel(_ tier: WhisperModelTier, reloadBackend: Bool = false, forceCPU: Bool = false) {
        let appGroup = AppGroupManager.shared
        guard HardwareDetector.isTierUnlocked(
            tier,
            simulate4GB: appGroup.simulateModest4GBDevice,
            forceUnlock: appGroup.forceUnlockAllModels
        ) else {
            lastErrorMessage = "Le modèle \(tier.displayName) nécessite \(tier.requiredRAMBadgeText) sur cet appareil."
            return
        }

        let modelURL = ModelDownloader.shared.localModelURL(for: tier)
        guard ModelDownloader.shared.isModelDownloaded(tier) else {
            lastErrorMessage = "Le modèle \(tier.displayName) n'est pas encore téléchargé."
            statusBannerMessage = "Téléchargez le modèle \(tier.shortBadgeName) dans la Carte 2."
            return
        }

        if !reloadBackend && ((loadedTier == tier && whisperContext != nil && !isLoadingModel) || (loadingTier == tier && isLoadingModel)) {
            return
        }

        isLoadingModel = true
        loadingTier = tier
        lastErrorMessage = nil
        statusBannerMessage = "Chargement de \(tier.displayName) (\(tier.ramUsageMB) Mo)..."

        let modelPath = modelURL.path
        let currentLangSelection = appGroup.selectedLanguage
        let resolvedLang = currentLangSelection.resolvedWhisperCode
        let promptSeed = currentLangSelection.initialPunctuationPrompt
        let isConstrainedRAM = HardwareDetector.physicalMemoryGB < 5.5 || appGroup.simulateModest4GBDevice
        // Sur iOS/iPadOS, la dictée clavier s'exécute quand l'app est en arrière-plan (où iOS interdit Metal GPU).
        // Utiliser directement Apple Accelerate AMX évite tout rechargement bloquant lors du passage au clavier.
        let cpuOnly = forceCPU || !HardwareDetector.isRunningOnMac || UIApplication.shared.applicationState == .background

        inferenceQueue.async { [weak self] in
            guard let self = self else { return }

            autoreleasepool {
                if let existingCtx = self.whisperContext {
                    whisper_free(existingCtx)
                    self.whisperContext = nil
                }

                if let old = self.activeLangCString {
                    free(old)
                }
                self.activeLangCString = strdup(resolvedLang)

                var cparams = whisper_context_default_params()
                // Sur iPhone 4 Go, les modèles >= 800 Mo (Expert+ 874 Mo, TITAN 1080 Mo) dépassent la limite
                // de buffers Metal câblés par processus (Jetsam SIGKILL). Le mode CPU/Accelerate mmap évite ce crash.
                let useMetalGPU = !cpuOnly && !(isConstrainedRAM && tier.sizeMB >= 800)
                cparams.use_gpu = useMetalGPU
                // Sur iPhone 4 Go (ou modèles >= 500 Mo sur appareils < 5.5 Go), désactiver flash_attn
                // évite un pic d'allocation des buffers KV Metal qui provoque un Jetsam kill iOS.
                cparams.flash_attn = useMetalGPU
                    && HardwareDetector.supportsMetalFlashAttention
                    && !(isConstrainedRAM && tier.sizeMB >= 500)

                var ctx = whisper_init_from_file_with_params(modelPath, cparams)
                var metalActive = useMetalGPU

                if ctx == nil && cparams.flash_attn {
                    cparams.flash_attn = false
                    ctx = whisper_init_from_file_with_params(modelPath, cparams)
                }
                if ctx == nil && cparams.use_gpu {
                    cparams.use_gpu = false
                    metalActive = false
                    ctx = whisper_init_from_file_with_params(modelPath, cparams)
                }

                var extractedTokens: [whisper_token] = []
                if let validCtx = ctx {
                    extractedTokens = Self.tokenizePrompt(promptSeed, ctx: validCtx)

                    // Éviter le passe de warmup sur les modèles lourds (>= 500 Mo) sur appareils <= 4 Go
                    // pour ne pas doubler le pic mémoire immédiatement après l'initialisation.
                    if !cpuOnly && !(isConstrainedRAM && tier.sizeMB >= 500) {
                        var warmupParams = self.makeWhisperFullParams(audioDurationSeconds: 0.5)
                        warmupParams.initial_prompt = nil
                        warmupParams.max_tokens = 1
                        let warmupSilence = [Float](repeating: 0.0, count: 8000)
                        _ = warmupSilence.withUnsafeBufferPointer { ptr in
                            whisper_full(validCtx, warmupParams, ptr.baseAddress, Int32(warmupSilence.count))
                        }
                    }
                }

                self.whisperContext = ctx
                self.contextUsesGPU = (ctx != nil) && metalActive
                self.cachedPromptTokens = extractedTokens

                let loadedSuccessfully = (ctx != nil)
                let flashEnabled = cparams.flash_attn
                let gpuLoaded = loadedSuccessfully && metalActive
                DispatchQueue.main.async {
                    self.isGPUBackendLoaded = gpuLoaded
                    self.activeBackendLabel = gpuLoaded ? (flashEnabled ? "GPU Metal + FlashAttention" : "GPU Metal") : "CPU"
                    self.loadedTier = loadedSuccessfully ? tier : nil
                    self.loadingTier = nil
                    self.isLoadingModel = false
                    if loadedSuccessfully {
                        let accelLabel = metalActive
                            ? (flashEnabled ? "GPU Metal + FlashAttention" : "GPU Metal (Mémoire Optimisée)")
                            : "\(self.optimalThreadCount) cœurs Accelerate (Anti-OOM)"
                        self.statusBannerMessage = "\(tier.shortBadgeName) actif • \(accelLabel)"
                    } else {
                        self.lastErrorMessage = "Échec de l'initialisation du modèle \(tier.displayName) (mémoire insuffisante)."
                        self.statusBannerMessage = "Erreur de chargement du modèle"
                    }
                }
            }
        }
    }

    /// Tokenise le prompt initial. Si le tampon de 16 tokens est trop petit, whisper_tokenize renvoie
    /// -(nombre requis) : on agrandit le tampon et on relance au lieu d'ignorer le prompt silencieusement.
    private static func tokenizePrompt(_ prompt: String, ctx: OpaquePointer) -> [whisper_token] {
        var tempTokens = [whisper_token](repeating: 0, count: 16)
        var count = prompt.withCString { cStr in
            tempTokens.withUnsafeMutableBufferPointer { buf in
                whisper_tokenize(ctx, cStr, buf.baseAddress, Int32(buf.count))
            }
        }
        if count < 0 {
            tempTokens = [whisper_token](repeating: 0, count: Int(-count))
            count = prompt.withCString { cStr in
                tempTokens.withUnsafeMutableBufferPointer { buf in
                    whisper_tokenize(ctx, cStr, buf.baseAddress, Int32(buf.count))
                }
            }
        }
        return count > 0 ? Array(tempTokens.prefix(Int(count))) : []
    }

    /// Doit être appelé sur `inferenceQueue` pour garantir la validité de `activeLangCString`.
    private func makeWhisperFullParams(audioDurationSeconds: Double) -> whisper_full_params {
        var wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY)
        if let langPtr = activeLangCString {
            wparams.language = UnsafePointer(langPtr)
        }
        // IMPORTANT: Dans whisper.cpp, `wparams.language = "auto"` suffit pour auto-détecter la langue ET transcrire.
        // Mettre `wparams.detect_language = true` arrête `whisper_full_with_state` immédiatement après la détection
        // sans décoder le moindre segment de texte !
        wparams.detect_language = false
        wparams.translate = false
        wparams.n_threads = Int32(optimalThreadCount)
        wparams.single_segment = true
        wparams.no_timestamps = true
        wparams.no_context = true
        wparams.print_special = false
        wparams.print_progress = false
        wparams.print_realtime = false
        wparams.print_timestamps = false
        wparams.suppress_blank = true
        wparams.suppress_nst = true
        wparams.temperature = 0.0
        wparams.temperature_inc = 0.2
        wparams.greedy.best_of = 1
        wparams.max_tokens = 0 // pas de plafond : 28 s de parole peuvent dépasser 96 tokens
        wparams.n_max_text_ctx = 32
        wparams.initial_prompt = nil

        let neededCtx = Int(ceil(audioDurationSeconds * 50.0)) + 64
        let alignedCtx = ((neededCtx + 31) / 32) * 32
        wparams.audio_ctx = Int32(max(64, min(1500, alignedCtx)))

        return wparams
    }

    // MARK: - 2. Session Audio en Tâche de Fond (AVAudioSession + AVAudioEngine)

    private func configureAndActivateAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .default,
            // Sans .allowBluetooth : le moteur reste ouvert et forcerait les AirPods en qualité d'appel
            options: [.mixWithOthers, .defaultToSpeaker, .allowBluetoothA2DP]
        )
        try session.setPreferredSampleRate(16000)
        try session.setPreferredIOBufferDuration(0.032)
        try session.setActive(true, options: .notifyOthersOnDeactivation)
    }

    public func startBackgroundAudioEngine() {
        if isBackgroundEngineRunning && audioEngine.isRunning {
            AppGroupManager.shared.recordEngineHeartbeat()
            AppGroupManager.shared.isBackgroundEngineActive = true
            let appGroup = AppGroupManager.shared
            let selectedTier = appGroup.selectedModel
            if ModelDownloader.shared.isModelDownloaded(selectedTier) && whisperContext == nil && !isLoadingModel {
                loadModel(selectedTier)
            }
            return
        }

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

            let appGroup = AppGroupManager.shared
            let selectedTier = appGroup.selectedModel
            if ModelDownloader.shared.isModelDownloaded(selectedTier) && whisperContext == nil && !isLoadingModel {
                loadModel(selectedTier)
            } else if whisperContext == nil && !isLoadingModel,
                      let anyDownloaded = WhisperModelTier.allCases.first(where: {
                          ModelDownloader.shared.isModelDownloaded($0) &&
                          HardwareDetector.isTierUnlocked(
                              $0,
                              simulate4GB: appGroup.simulateModest4GBDevice,
                              forceUnlock: appGroup.forceUnlockAllModels
                          )
                      }) {
                appGroup.selectedModel = anyDownloaded
                loadModel(anyDownloaded)
            }

            if audioEngine.isRunning {
                audioEngine.stop()
            }
            let inputNode = audioEngine.inputNode
            inputNode.removeTap(onBus: 0)

            let hardwareFormat = inputNode.outputFormat(forBus: 0)

            guard hardwareFormat.sampleRate > 0 && hardwareFormat.channelCount > 0 else {
                lastErrorMessage = "Format audio matériel invalide sur le microphone."
                return
            }

            audioConverter = nil

            // format nil : un format périmé après un changement de micro lèverait une exception Objective-C
            inputNode.installTap(onBus: 0, bufferSize: 2048, format: nil) { [weak self] buffer, _ in
                self?.processMicrophoneBuffer(buffer)
            }

            audioEngine.prepare()
            try audioEngine.start()

            lastHeartbeatTime = Date().timeIntervalSince1970
            AppGroupManager.shared.recordEngineHeartbeat()
            startHeartbeatTimer()
            isBackgroundEngineRunning = true
            AppGroupManager.shared.isBackgroundEngineActive = true
            statusBannerMessage = "Moteur Audio Tâche de Fond ACTIF — Prêt à dicter depuis le clavier"
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = "Erreur d'activation audio : \(error.localizedDescription)"
            statusBannerMessage = "Échec du démarrage audio"
            stopHeartbeatTimer()
            isBackgroundEngineRunning = false
            AppGroupManager.shared.clearEngineHeartbeat()
            AppGroupManager.shared.isBackgroundEngineActive = false
        }
    }

    private func startHeartbeatTimer() {
        heartbeatTimer?.invalidate()
        let timer = Timer(timeInterval: 2.5, repeats: true) { [weak self] _ in
            guard let self = self, self.isBackgroundEngineRunning else { return }
            AppGroupManager.shared.recordEngineHeartbeat()
        }
        RunLoop.main.add(timer, forMode: .common)
        heartbeatTimer = timer
    }

    private func stopHeartbeatTimer() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    public func stopBackgroundAudioEngine() {
        stopHeartbeatTimer()
        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        isBackgroundEngineRunning = false
        AppGroupManager.shared.clearEngineHeartbeat()
        AppGroupManager.shared.isBackgroundEngineActive = false
        AppGroupManager.shared.orbState = .idle
        AppGroupManager.shared.updateLiveAudioLevel(0.0)
        statusBannerMessage = "Moteur Audio arrêté"
    }

    // MARK: - 3. Contrôle de l'Écoute

    public func toggleRecording() {
        guard AVAudioSession.sharedInstance().recordPermission == .granted else {
            startBackgroundAudioEngine()
            return
        }

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
        resumeListeningAfterTranscription = false
        let appGroup = AppGroupManager.shared
        let tier = appGroup.selectedModel
        if whisperContext == nil && !isLoadingModel {
            if ModelDownloader.shared.isModelDownloaded(tier) {
                loadModel(tier)
            } else if let fallback = WhisperModelTier.allCases.first(where: {
                ModelDownloader.shared.isModelDownloaded($0) &&
                HardwareDetector.isTierUnlocked(
                    $0,
                    simulate4GB: appGroup.simulateModest4GBDevice,
                    forceUnlock: appGroup.forceUnlockAllModels
                )
            }) {
                appGroup.selectedModel = fallback
                loadModel(fallback)
            } else {
                lastErrorMessage = "Veuillez d'abord télécharger l'un des modèles Whisper hors-ligne (Carte 2)."
                statusBannerMessage = "Aucun modèle hors-ligne téléchargé"
                appGroup.orbState = .idle
                return
            }
        }

        if !isBackgroundEngineRunning || !audioEngine.isRunning {
            startEngineInternal()
        }

        guard isBackgroundEngineRunning && audioEngine.isRunning else {
            appGroup.orbState = .idle
            return
        }

        audioBufferLock.lock()
        accumulatedSamples16kHz.removeAll(keepingCapacity: true)
        activeSpeechDurationSeconds = 0.0
        currentSilenceDurationSeconds = 0.0
        hasSpokenEnoughInCurrentUtterance = false
        recentRMSWindow.removeAll(keepingCapacity: true)
        audioBufferLock.unlock()

        appGroup.recordEngineHeartbeat()
        appGroup.orbState = .listening
        statusBannerMessage = "Écoute active (\(appGroup.selectedLanguage.shortPillLabel)) — Parlez maintenant..."
    }

    public func finishListeningAndTranscribe() {
        guard AppGroupManager.shared.orbState == .listening, !isTranscribingNow else {
            return
        }

        audioBufferLock.lock()
        let capturedFrames = accumulatedSamples16kHz
        accumulatedSamples16kHz.removeAll(keepingCapacity: true)
        activeSpeechDurationSeconds = 0.0
        currentSilenceDurationSeconds = 0.0
        hasSpokenEnoughInCurrentUtterance = false
        audioBufferLock.unlock()

        AppGroupManager.shared.updateLiveAudioLevel(0.0)

        guard capturedFrames.count >= Int(targetSampleRate * 0.20) else {
            AppGroupManager.shared.orbState = .idle
            statusBannerMessage = "Audio trop court • Prêt"
            return
        }

        runWhisperTranscription(on: capturedFrames)
    }

    private func cancelSilentListeningSession() {
        guard AppGroupManager.shared.orbState == .listening, !isTranscribingNow else { return }

        audioBufferLock.lock()
        accumulatedSamples16kHz.removeAll(keepingCapacity: true)
        activeSpeechDurationSeconds = 0.0
        currentSilenceDurationSeconds = 0.0
        hasSpokenEnoughInCurrentUtterance = false
        audioBufferLock.unlock()

        resumeListeningAfterTranscription = false
        AppGroupManager.shared.updateLiveAudioLevel(0.0)
        AppGroupManager.shared.orbState = .idle
        statusBannerMessage = "Aucune parole détectée • Prêt"
    }

    // MARK: - 4. Conversion Temps Réel vers 16000 Hz Mono Float32, Calcul RMS & VAD

    private func processMicrophoneBuffer(_ buffer: AVAudioPCMBuffer) {
        let now = Date().timeIntervalSince1970
        if now - lastHeartbeatTime >= 1.5 {
            lastHeartbeatTime = now
            AppGroupManager.shared.recordEngineHeartbeat()
        }

        audioBufferLock.lock()
        let capturing = isCaptureEnabled
        let configuredSilenceSeconds = vadSilenceSeconds
        audioBufferLock.unlock()

        guard capturing else {
            if smoothedLevel > 0.01 {
                smoothedLevel = 0.0
                AppGroupManager.shared.updateLiveAudioLevel(0.0)
            }
            return
        }

        let monoSamples16kHz = convertBufferTo16kHzMonoFloat32(buffer)
        guard !monoSamples16kHz.isEmpty else { return }

        var sumSquares: Float = 0.0
        let sampleCount = monoSamples16kHz.count
        for sample in monoSamples16kHz {
            sumSquares += sample * sample
        }
        let rms = sqrt(sumSquares / Float(sampleCount))

        let normalizedVisual = min(1.0, max(0.0, (rms - 0.003) * 16.0))
        smoothedLevel = smoothedLevel * 0.60 + normalizedVisual * 0.40
        AppGroupManager.shared.updateLiveAudioLevel(smoothedLevel)

        let chunkDurationSeconds = Double(sampleCount) / targetSampleRate
        let vadSilenceLimit = max(0.55, min(3.0, configuredSilenceSeconds))

        var shouldTriggerAutoTranscribe = false
        var reachedMaxDuration = false
        var shouldCancelSilentSession = false

        audioBufferLock.lock()
        accumulatedSamples16kHz.append(contentsOf: monoSamples16kHz)

        recentRMSWindow.append(rms)
        let windowCapacity = max(8, Int(2.0 / max(0.005, chunkDurationSeconds)))
        if recentRMSWindow.count > windowCapacity {
            recentRMSWindow.removeFirst(recentRMSWindow.count - windowCapacity)
        }
        let noiseFloor = recentRMSWindow.min() ?? rms
        let adaptiveSpeechThreshold = min(speechRMSThreshold, max(0.004, noiseFloor * 3.0))

        if rms >= adaptiveSpeechThreshold {
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

        let totalRecordedSeconds = Double(accumulatedSamples16kHz.count) / targetSampleRate
        if !hasSpokenEnoughInCurrentUtterance && activeSpeechDurationSeconds < 0.35 && totalRecordedSeconds >= 12.0 {
            shouldCancelSilentSession = true
        } else if totalRecordedSeconds >= 28.0 {
            shouldTriggerAutoTranscribe = true
            reachedMaxDuration = true
        }
        if shouldTriggerAutoTranscribe {
            hasSpokenEnoughInCurrentUtterance = false
            currentSilenceDurationSeconds = 0.0
        }
        audioBufferLock.unlock()

        if shouldCancelSilentSession {
            DispatchQueue.main.async { [weak self] in
                self?.cancelSilentListeningSession()
            }
        } else if shouldTriggerAutoTranscribe {
            DispatchQueue.main.async { [weak self] in
                self?.resumeListeningAfterTranscription = reachedMaxDuration
                self?.finishListeningAndTranscribe()
            }
        }
    }

    /// AVAudioConverter garde son état entre les tampons : filtrage correct, sans clic aux jointures.
    private func convertBufferTo16kHzMonoFloat32(_ inputBuffer: AVAudioPCMBuffer) -> [Float] {
        guard inputBuffer.frameLength > 0,
              let targetFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: targetSampleRate,
                channels: 1,
                interleaved: false
              ) else { return [] }

        if audioConverter == nil || audioConverter?.inputFormat != inputBuffer.format {
            audioConverter = AVAudioConverter(from: inputBuffer.format, to: targetFormat)
            audioConverter?.downmix = true
        }
        guard let converter = audioConverter else { return [] }

        let ratio = targetSampleRate / inputBuffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(inputBuffer.frameLength) * ratio) + 64
        guard let output = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: capacity) else { return [] }

        var didSupplyInput = false
        var conversionError: NSError?
        let status = converter.convert(to: output, error: &conversionError) { _, inputStatus in
            if didSupplyInput {
                inputStatus.pointee = .noDataNow
                return nil
            }
            didSupplyInput = true
            inputStatus.pointee = .haveData
            return inputBuffer
        }

        guard status != .error, let channel = output.floatChannelData else { return [] }
        return Array(UnsafeBufferPointer(start: channel[0], count: Int(output.frameLength)))
    }

    private func prepareAudioFramesForWhisper(_ rawFrames: [Float]) -> [Float] {
        guard !rawFrames.isEmpty else { return [] }

        var sum: Float = 0.0
        for s in rawFrames {
            sum += s
        }
        let mean = sum / Float(rawFrames.count)

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

        guard maxPeak >= 0.002 else { return [] }

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

        let preRoll = Int(targetSampleRate * 0.08)
        let postRoll = Int(targetSampleRate * 0.14)
        let startIdx = max(0, firstVoiceSample - preRoll)
        let endIdx = min(filtered.count - 1, max(startIdx, lastVoiceSample + postRoll))

        var trimmed = Array(filtered[startIdx...endIdx])

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
                if amplified > 0.88 {
                    trimmed[i] = min(0.99, 0.88 + 0.11 * tanh((amplified - 0.88) / 0.11))
                } else if amplified < -0.88 {
                    trimmed[i] = max(-0.99, -0.88 + 0.11 * tanh((amplified + 0.88) / 0.11))
                } else {
                    trimmed[i] = amplified
                }
            }
        }

        let leadingPad = [Float](repeating: 0.0, count: Int(targetSampleRate * 0.05))
        let trailingPad = [Float](repeating: 0.0, count: Int(targetSampleRate * 0.15))
        var finalBuffer: [Float] = []
        finalBuffer.reserveCapacity(leadingPad.count + trimmed.count + trailingPad.count + 2048)
        finalBuffer.append(contentsOf: leadingPad)
        finalBuffer.append(contentsOf: trimmed)
        finalBuffer.append(contentsOf: trailingPad)

        let minSamplesRequired = Int(targetSampleRate * 0.65)
        if finalBuffer.count < minSamplesRequired {
            finalBuffer.append(contentsOf: [Float](repeating: 0.0, count: minSamplesRequired - finalBuffer.count))
        }

        return finalBuffer
    }

    // MARK: - 5. Inférence Asynchrone C/Metal Ultra-Rapide (whisper.xcframework GPU Metal + FlashAttention)

    private func runWhisperTranscription(on floatArray: [Float]) {
        let conditionedFrames = prepareAudioFramesForWhisper(floatArray)
        guard !conditionedFrames.isEmpty else {
            AppGroupManager.shared.orbState = .idle
            statusBannerMessage = "Aucun signal vocal détecté • Prêt"
            return
        }

        let audioDurationSeconds = Double(conditionedFrames.count) / targetSampleRate
        let isAutoDetect = (AppGroupManager.shared.selectedLanguage == .auto)
        let isSmallModel = (loadedTier == .nano || loadedTier == .eclair)
        let modelPath = loadedTier.map { ModelDownloader.shared.localModelURL(for: $0).path }

        isTranscribingNow = true
        AppGroupManager.shared.orbState = .transcribing
        statusBannerMessage = "Transcription en cours..."
        let startTime = Date()

        inferenceQueue.async { [weak self] in
            guard let self = self else { return }
            guard let ctx = self.whisperContext else {
                DispatchQueue.main.async {
                    self.isTranscribingNow = false
                    AppGroupManager.shared.orbState = .idle
                    self.lastErrorMessage = "Modèle Whisper non chargé en mémoire."
                }
                return
            }

            let baseParams = self.makeWhisperFullParams(audioDurationSeconds: audioDurationSeconds)
            let promptTokensSnapshot = (isSmallModel || isAutoDetect) ? [] : self.cachedPromptTokens

            let runInference: (OpaquePointer) -> Int32 = { inferenceCtx in
                conditionedFrames.withUnsafeBufferPointer { audioPtr -> Int32 in
                    var wparams = baseParams
                    if !promptTokensSnapshot.isEmpty {
                        return promptTokensSnapshot.withUnsafeBufferPointer { tokenPtr -> Int32 in
                            wparams.prompt_tokens = tokenPtr.baseAddress
                            wparams.prompt_n_tokens = Int32(tokenPtr.count)
                            return whisper_full(inferenceCtx, wparams, audioPtr.baseAddress, Int32(audioPtr.count))
                        }
                    }
                    return whisper_full(inferenceCtx, wparams, audioPtr.baseAddress, Int32(audioPtr.count))
                }
            }

            var activeCtx = ctx
            var rc = runInference(activeCtx)
            var fellBackToCPU = false

            // Une erreur Metal reste collée au backend : on rouvre le modèle sur CPU et on réessaie.
            if rc != 0, self.contextUsesGPU, let path = modelPath {
                whisper_free(activeCtx)
                var cpuParams = whisper_context_default_params()
                cpuParams.use_gpu = false
                cpuParams.flash_attn = false
                let cpuCtx = whisper_init_from_file_with_params(path, cpuParams)
                self.whisperContext = cpuCtx
                self.contextUsesGPU = false
                fellBackToCPU = true
                if let validCPUCtx = cpuCtx {
                    activeCtx = validCPUCtx
                    rc = runInference(validCPUCtx)
                }
            }

            var rawSegments: [String] = []
            if rc == 0 {
                let nSegments = whisper_full_n_segments(activeCtx)
                for i in 0..<nSegments {
                    if let cText = whisper_full_get_segment_text(activeCtx, i) {
                        rawSegments.append(String(cString: cText))
                    }
                }
            }

            let contextLost = (self.whisperContext == nil)
            let rawText = rawSegments.joined(separator: " ")
            let elapsedMs = Int(Date().timeIntervalSince(startTime) * 1000.0)

            DispatchQueue.main.async {
                self.isTranscribingNow = false
                if fellBackToCPU {
                    self.isGPUBackendLoaded = false
                    self.activeBackendLabel = "CPU"
                }
                if contextLost {
                    self.loadedTier = nil
                }
                if rc == 0 {
                    self.lastTranscriptionLatencyMs = elapsedMs
                    self.handleRawTranscribedText(rawText, latencyMs: elapsedMs)
                    if self.resumeListeningAfterTranscription {
                        self.resumeListeningAfterTranscription = false
                        if AppGroupManager.shared.orbState == .idle {
                            self.beginListeningSession()
                        }
                    }
                } else {
                    AppGroupManager.shared.orbState = .idle
                    self.lastErrorMessage = "Erreur d'inférence Whisper (code \(rc))."
                    self.statusBannerMessage = "Erreur de transcription"
                }
            }
        }
    }

    // MARK: - 6. Post-Traitement Multilingue + Mémoire JSON (user_style_memory.json)

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

        if liveTestTranscript.isEmpty || liveTestTranscript.hasSuffix("\n") || liveTestTranscript.hasSuffix(" ") {
            liveTestTranscript += finalResult + (AppGroupManager.shared.autoSpaceForGoodnotes && !finalResult.hasSuffix("\n") ? " " : "")
        } else {
            liveTestTranscript += " " + finalResult + (AppGroupManager.shared.autoSpaceForGoodnotes && !finalResult.hasSuffix("\n") ? " " : "")
        }

        AppGroupManager.shared.publishTranscribedText(finalResult)
        statusBannerMessage = "Transcrit en \(latencyMs) ms • Envoyé au clavier"
    }

    public func postProcessFrenchDictation(_ input: String) -> String? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        // 1. Filtrage des hallucinations connues de Whisper sur les silences (FR / EN / International)
        let hallucinationPatterns: [String] = [
            "[BLANK_AUDIO]",
            "[blank_audio]",
            "(blank_audio)",
            "[ Musique ]",
            "[Musique]",
            "[ Music ]",
            "[Music]",
            "[ Applaudissements ]",
            "[ Silence ]",
            "[Silence]",
            "Sous-titres réalisés para la communauté d'Amara.org",
            "Sous-titres réalisés par la communauté d'Amara.org",
            "Sous-titres réalisés",
            "Subtitles by the Amara.org community"
        ]

        for pattern in hallucinationPatterns {
            text = text.replacingOccurrences(of: pattern, with: "", options: [.caseInsensitive])
        }

        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty || text == "..." || text == ".." || text == "." {
            return nil
        }

        // 2. Détection de la commande vocale d'effacement de la dernière phrase (FR + EN)
        let lower = text.lowercased()
            .trimmingCharacters(in: CharacterSet.punctuationCharacters.union(.whitespacesAndNewlines))

        // Ces phrases sont de vrais mots : on ne les filtre que si elles sont seules.
        let standaloneHallucinations: Set<String> = [
            "thank you for watching", "thanks for watching",
            "merci d'avoir regardé cette vidéo", "merci d'avoir regardé",
            "abonnez-vous", "s'abonner"
        ]
        if standaloneHallucinations.contains(lower) {
            return nil
        }

        if lower == "effacer la dernière phrase" ||
           lower == "effacer la derniere phrase" ||
           lower == "annuler la dernière phrase" ||
           lower == "annuler la derniere phrase" ||
           lower == "supprimer la dernière phrase" ||
           lower == "supprimer la derniere phrase" ||
           lower == "delete last sentence" ||
           lower == "undo last sentence" {
            return "__COMMAND_DELETE_LAST_SENTENCE__"
        }

        // 3. Commandes vocales isolées pour la ponctuation (FR + EN)
        if lower == "point d'interrogation" || lower == "question mark" {
            return "?"
        }
        if lower == "point d'exclamation" || lower == "exclamation mark" || lower == "exclamation point" {
            return "!"
        }
        if lower == "point" || lower == "point final" || lower == "period" || lower == "full stop" {
            return "."
        }
        if lower == "virgule" || lower == "comma" {
            return ","
        }
        if lower == "deux points" || lower == "deux-points" || lower == "colon" {
            return " :"
        }
        if lower == "point-virgule" || lower == "point virgule" || lower == "semicolon" {
            return " ;"
        }
        if lower == "nouveau paragraphe" || lower == "à la ligne" || lower == "a la ligne" || lower == "new line" || lower == "new paragraph" {
            return "\n"
        }
        if lower == "double saut de ligne" {
            return "\n\n"
        }

        // 4. Remplacement des commandes vocales intégrées au milieu d'une dictée
        let inlineReplacements: [(pattern: String, replacement: String)] = [
            ("double saut de ligne", "\n\n"),
            ("nouveau paragraphe", "\n"),
            ("new paragraph", "\n"),
            ("à la ligne", "\n"),
            ("new line", "\n"),
            ("ouvrir les guillemets", "« "),
            ("fermer les guillemets", " »"),
            ("point d'interrogation", "?"),
            ("question mark", "?"),
            ("point d'exclamation", "!"),
            ("exclamation mark", "!"),
            ("point-virgule", ";"),
            ("point virgule", ";"),
            ("deux-points", ":"),
            ("point final", ".")
        ]

        for item in inlineReplacements {
            let wholeWordPattern = "(?i)(?<![\\p{L}\\p{N}])"
                + NSRegularExpression.escapedPattern(for: item.pattern)
                + "(?![\\p{L}\\p{N}])"
            guard let regex = try? NSRegularExpression(pattern: wholeWordPattern, options: []) else { continue }
            let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
            text = regex.stringByReplacingMatches(
                in: text,
                options: [],
                range: fullRange,
                withTemplate: NSRegularExpression.escapedTemplate(for: item.replacement)
            )
        }

        // 5. Nettoyage des espaces multiples
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

        // 7. Application de la mémoire personnelle user_style_memory.json (par langue + habitudes de ponctuation)
        let activeLang = AppGroupManager.shared.selectedLanguage.effectiveStyleLanguageCode
        text = UserStyleMemoryManager.shared.applyStyleMemory(to: text, languageCode: activeLang)

        return text.isEmpty ? nil : text
    }

    private func removeLastSentenceFromLocalPreview() {
        let length = AppGroupManager.shared.lastInsertedSentenceLength
        guard length > 0 else { return }
        guard liveTestTranscript.count >= length else {
            liveTestTranscript = ""
            return
        }
        liveTestTranscript.removeLast(length)
    }
}
