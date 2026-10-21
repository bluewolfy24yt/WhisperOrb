import Foundation
import SwiftUI
import UIKit
import Combine
import CoreFoundation

// MARK: - Enumération des 3 Modèles Whisper (Partagée App + Clavier)

public enum WhisperModelTier: String, CaseIterable, Codable, Identifiable {
    case eclair = "eclair"
    case normal = "normal"
    case zeroFaute = "zeroFaute"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .eclair:
            return "Whisper Flash"
        case .normal:
            return "Whisper PRO"
        case .zeroFaute:
            return "Whisper Expert"
        }
    }

    public var shortBadgeName: String {
        switch self {
        case .eclair:
            return "Whisper Flash"
        case .normal:
            return "Whisper PRO"
        case .zeroFaute:
            return "Whisper Expert"
        }
    }

    public var subtitle: String {
        switch self {
        case .eclair:
            return "Ultra-rapide • Brouillon & prise de notes"
        case .normal:
            return "Équilibré • Fluide & économe en batterie"
        case .zeroFaute:
            return "Précision maximale • Contexte & ponctuation parfaite"
        }
    }

    public var fileName: String {
        switch self {
        case .eclair:
            return "ggml-base-q5_1.bin"
        case .normal:
            return "ggml-small-q5_1.bin"
        case .zeroFaute:
            return "ggml-large-v3-turbo-q5_0.bin"
        }
    }

    public var downloadURL: URL {
        switch self {
        case .eclair:
            return URL(string: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin")!
        case .normal:
            return URL(string: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin")!
        case .zeroFaute:
            return URL(string: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin")!
        }
    }

    public var sizeMB: Int {
        switch self {
        case .eclair:
            return 60
        case .normal:
            return 190
        case .zeroFaute:
            return 540
        }
    }

    public var ramUsageMB: Int {
        switch self {
        case .eclair:
            return 190
        case .normal:
            return 380
        case .zeroFaute:
            return 850
        }
    }

    public var latencyDescription: String {
        switch self {
        case .eclair:
            return "~0,15 s"
        case .normal:
            return "~0,35 s"
        case .zeroFaute:
            return "~0,70 s"
        }
    }
}

// MARK: - Détecteur Matériel Haute Précision (A12Z Bionic 6 Go / A15 Bionic 4 Go)

public struct HardwareDetector {
    public static var physicalMemoryBytes: UInt64 {
        ProcessInfo.processInfo.physicalMemory
    }

    public static var physicalMemoryGB: Double {
        Double(physicalMemoryBytes) / (1024.0 * 1024.0 * 1024.0)
    }

    public static var formattedMemoryGB: String {
        String(format: "%.1f Go RAM", physicalMemoryGB)
    }

    public static var machineIdentifier: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        return identifier
    }

    public static var deviceMarketingName: String {
        let code = machineIdentifier
        switch code {
        // iPad Pro 12,9" (4e génération - 2020 - MXAT2NF/A - A12Z Bionic 6 Go)
        case "iPad8,11", "iPad8,12":
            return "iPad Pro 12,9\" (4e gén) — A12Z Bionic"
        // iPad Pro 11" (2e génération - 2020 - A12Z Bionic 6 Go)
        case "iPad8,9", "iPad8,10":
            return "iPad Pro 11\" (2e gén) — A12Z Bionic"
        // iPad Pro (3e gén 2018 - A12X Bionic)
        case "iPad8,1", "iPad8,2", "iPad8,3", "iPad8,4", "iPad8,5", "iPad8,6", "iPad8,7", "iPad8,8":
            return "iPad Pro (A12X Bionic)"
        // iPad Pro M1 / M2 / M4
        case "iPad13,4", "iPad13,5", "iPad13,6", "iPad13,7", "iPad13,8", "iPad13,9", "iPad13,10", "iPad13,11":
            return "iPad Pro (Puce Apple M1)"
        case "iPad14,3", "iPad14,4", "iPad14,5", "iPad14,6":
            return "iPad Pro (Puce Apple M2)"
        case "iPad16,3", "iPad16,4", "iPad16,5", "iPad16,6":
            return "iPad Pro (Puce Apple M4)"
        // iPhone 13 (Banc de test secondaire - A15 Bionic 4 Go)
        case "iPhone14,5":
            return "iPhone 13 — A15 Bionic"
        case "iPhone14,4":
            return "iPhone 13 mini — A15 Bionic"
        case "iPhone14,2":
            return "iPhone 13 Pro — A15 Bionic"
        case "iPhone14,3":
            return "iPhone 13 Pro Max — A15 Bionic"
        case "iPhone14,7", "iPhone14,8":
            return "iPhone 14 — A15 Bionic"
        case "iPhone15,2", "iPhone15,3":
            return "iPhone 14 Pro — A16 Bionic"
        case "iPhone15,4", "iPhone15,5":
            return "iPhone 15 — A16 Bionic"
        case "iPhone16,1", "iPhone16,2":
            return "iPhone 15 Pro — A17 Pro"
        case "x86_64", "arm64":
            if UIDevice.current.userInterfaceIdiom == .pad {
                return "iPad Pro 12,9\" (Simulateur / A12Z)"
            } else {
                return "iPhone 13 (Simulateur / A15)"
            }
        default:
            if code.hasPrefix("iPad") {
                return "iPad (\(code))"
            } else if code.hasPrefix("iPhone") {
                return "iPhone (\(code))"
            }
            return "Appareil Apple (\(code))"
        }
    }

    public static var isHighRefreshRateProMotion: Bool {
        UIScreen.main.maximumFramesPerSecond >= 120 || machineIdentifier.hasPrefix("iPad8,") || machineIdentifier.hasPrefix("iPad13,") || machineIdentifier.hasPrefix("iPad14,")
    }

    public static var recommendedTier: WhisperModelTier {
        if physicalMemoryGB >= 5.2 {
            return .zeroFaute
        } else {
            return .normal
        }
    }

    public static var optimalThreadCount: Int {
        max(2, min(6, ProcessInfo.processInfo.activeProcessorCount - 2))
    }

    public static var telemetrySummary: String {
        let proMotionBadge = isHighRefreshRateProMotion ? " • ProMotion 120 Hz" : " • 60 Hz"
        return "\(deviceMarketingName) • \(formattedMemoryGB)\(proMotionBadge)"
    }
}

// MARK: - Payload JSON de Synchronisation Inter-Processus (IPC)

public struct WhisperSharedStatePayload: Codable, Equatable {
    public var bgHex: String
    public var fgHex: String
    public var selectedModelRaw: String
    public var orbStateRaw: String
    public var currentAudioLevel: Float
    public var pendingTranscribedText: String
    public var lastInsertedSentenceLength: Int
    public var shouldDeleteLastSentence: Bool
    public var autoSpaceForGoodnotes: Bool
    public var autoPunctuation: Bool
    public var vadSilenceThresholdSeconds: Double
    public var isBackgroundEngineActive: Bool
    public var updatedAt: TimeInterval
}

// MARK: - Gestionnaire Central AppGroup + Notifications Darwin + Fallback Pasteboard

public final class AppGroupManager: ObservableObject {
    public static let shared = AppGroupManager()

    public static let appGroupIdentifier = "group.com.bluewolfy24yt.whisperorb"
    public static let darwinToggleRecordingNotification = "com.bluewolfy24yt.whisperorb.toggleRecording"
    public static let darwinStateUpdatedNotification = "com.bluewolfy24yt.whisperorb.stateUpdated"
    public static let pasteboardBusName = UIPasteboard.Name("com.bluewolfy24yt.whisperorb.bus")

    // MARK: - Propriétés @Published Synchronisées

    @Published public var bgHex: String = "#08080C" {
        didSet { if !isApplyingRemoteUpdate && oldValue != bgHex { persistAndBroadcastState() } }
    }

    @Published public var fgHex: String = "#0088FF" {
        didSet { if !isApplyingRemoteUpdate && oldValue != fgHex { persistAndBroadcastState() } }
    }

    @Published public var selectedModel: WhisperModelTier = HardwareDetector.recommendedTier {
        didSet { if !isApplyingRemoteUpdate && oldValue != selectedModel { persistAndBroadcastState() } }
    }

    @Published public var orbState: WhisperOrbState = .idle {
        didSet { if !isApplyingRemoteUpdate && oldValue != orbState { persistAndBroadcastState() } }
    }

    @Published public var currentAudioLevel: Float = 0.0

    @Published public var pendingTranscribedText: String = ""

    @Published public var lastInsertedSentenceLength: Int = 0

    @Published public var shouldDeleteLastSentence: Bool = false

    @Published public var autoSpaceForGoodnotes: Bool = true {
        didSet { if !isApplyingRemoteUpdate && oldValue != autoSpaceForGoodnotes { persistAndBroadcastState() } }
    }

    @Published public var autoPunctuation: Bool = true {
        didSet { if !isApplyingRemoteUpdate && oldValue != autoPunctuation { persistAndBroadcastState() } }
    }

    @Published public var vadSilenceThresholdSeconds: Double = 1.0 {
        didSet { if !isApplyingRemoteUpdate && oldValue != vadSilenceThresholdSeconds { persistAndBroadcastState() } }
    }

    @Published public var isBackgroundEngineActive: Bool = false {
        didSet { if !isApplyingRemoteUpdate && oldValue != isBackgroundEngineActive { persistAndBroadcastState() } }
    }

    // Callback déclenché dans l'application principale quand le clavier touche la bulle
    public var onToggleRecordingRequested: (() -> Void)?

    // MARK: - Propriétés Internes

    private let userDefaults: UserDefaults
    private var isApplyingRemoteUpdate: Bool = false
    private var pollingTimer: Timer?
    private var lastAppliedTimestamp: TimeInterval = 0

    private enum Keys {
        static let hasLaunchedBefore = "whisperorb.hasLaunchedBefore"
        static let sharedPayloadData = "whisperorb.sharedPayloadData"
        static let sharedFileName = "whisperorb_shared_state.json"
        static let pasteboardStateType = "com.bluewolfy24yt.whisperorb.state.json"
    }

    // MARK: - Initialisation

    private init() {
        if let groupDefaults = UserDefaults(suiteName: Self.appGroupIdentifier) {
            self.userDefaults = groupDefaults
        } else {
            self.userDefaults = UserDefaults.standard
        }

        // Premier lancement : sélection intelligente du modèle selon la RAM (6 Go A12Z -> Zéro Faute, 4 Go A15 -> Normal)
        if !userDefaults.bool(forKey: Keys.hasLaunchedBefore) {
            self.selectedModel = HardwareDetector.recommendedTier
            userDefaults.set(true, forKey: Keys.hasLaunchedBefore)
            persistAndBroadcastState()
        } else {
            reloadStateFromChannels()
        }

        registerDarwinNotificationObservers()
        startFastPolling()
    }

    deinit {
        pollingTimer?.invalidate()
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterRemoveEveryObserver(center, Unmanaged.passUnretained(self).toOpaque())
    }

    // MARK: - Répertoires Partagés (App Group avec Fallback Documents)

    public var sharedContainerURL: URL {
        if let groupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier) {
            return groupURL
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    public var modelsDirectoryURL: URL {
        let dir = sharedContainerURL.appendingPathComponent("Models", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            var mutableDir = dir
            try? mutableDir.setResourceValues(resourceValues)
        }
        return dir
    }

    private var sharedStateFileURL: URL {
        sharedContainerURL.appendingPathComponent(Keys.sharedFileName, isDirectory: false)
    }

    // MARK: - Canal 1 & Canal 2 : Sauvegarde et Diffusion Temps Réel

    public func persistAndBroadcastState(postDarwinNotification: Bool = true) {
        let now = Date().timeIntervalSince1970
        lastAppliedTimestamp = now

        let payload = WhisperSharedStatePayload(
            bgHex: bgHex,
            fgHex: fgHex,
            selectedModelRaw: selectedModel.rawValue,
            orbStateRaw: orbState.rawValue,
            currentAudioLevel: currentAudioLevel,
            pendingTranscribedText: pendingTranscribedText,
            lastInsertedSentenceLength: lastInsertedSentenceLength,
            shouldDeleteLastSentence: shouldDeleteLastSentence,
            autoSpaceForGoodnotes: autoSpaceForGoodnotes,
            autoPunctuation: autoPunctuation,
            vadSilenceThresholdSeconds: vadSilenceThresholdSeconds,
            isBackgroundEngineActive: isBackgroundEngineActive,
            updatedAt: now
        )

        guard let encoded = try? JSONEncoder().encode(payload) else { return }

        // Canal 1A : UserDefaults(suiteName: group...)
        userDefaults.set(encoded, forKey: Keys.sharedPayloadData)

        // Canal 1B : Fichier JSON atomique dans le conteneur App Group
        try? encoded.write(to: sharedStateFileURL, options: [.atomic])

        // Canal 2 : Fallback Sideloading gratuit via UIPasteboard dédié nommé
        if let busPasteboard = UIPasteboard(name: Self.pasteboardBusName, create: true) {
            busPasteboard.setData(encoded, forPasteboardType: Keys.pasteboardStateType)
        }

        if postDarwinNotification {
            postDarwinNotificationNamed(Self.darwinStateUpdatedNotification)
        }
    }

    /// Mise à jour haute fréquence (20 Hz) du niveau audio sans surcharger les notifications Darwin
    public func updateLiveAudioLevel(_ level: Float) {
        let clamped = max(0.0, min(1.0, level))
        if DispatchQueue.isMain {
            self.currentAudioLevel = clamped
        } else {
            DispatchQueue.main.async {
                self.currentAudioLevel = clamped
            }
        }
        persistAndBroadcastState(postDarwinNotification: false)
    }

    // MARK: - Lecture Multi-Canal (App Group JSON -> UserDefaults -> Pasteboard Sideloading)

    public func reloadStateFromChannels() {
        var candidates: [WhisperSharedStatePayload] = []
        let decoder = JSONDecoder()

        // 1. Lecture fichier JSON partagé
        if let fileData = try? Data(contentsOf: sharedStateFileURL),
           let payload = try? decoder.decode(WhisperSharedStatePayload.self, from: fileData) {
            candidates.append(payload)
        }

        // 2. Lecture UserDefaults App Group
        if let defaultsData = userDefaults.data(forKey: Keys.sharedPayloadData),
           let payload = try? decoder.decode(WhisperSharedStatePayload.self, from: defaultsData) {
            candidates.append(payload)
        }

        // 3. Lecture Pasteboard nommé (Fallback universel Sideloading SideStore / AltStore / Sideloadly)
        if let busPasteboard = UIPasteboard(name: Self.pasteboardBusName, create: false),
           let pasteData = busPasteboard.data(forPasteboardType: Keys.pasteboardStateType),
           let payload = try? decoder.decode(WhisperSharedStatePayload.self, from: pasteData) {
            candidates.append(payload)
        }

        // Sélection de l'état le plus récent parmi les canaux disponibles
        guard let freshest = candidates.max(by: { $0.updatedAt < $1.updatedAt }) else {
            return
        }

        guard freshest.updatedAt >= lastAppliedTimestamp else {
            return
        }

        lastAppliedTimestamp = freshest.updatedAt

        let applyBlock = { [weak self] in
            guard let self = self else { return }
            self.isApplyingRemoteUpdate = true
            defer { self.isApplyingRemoteUpdate = false }

            if self.bgHex != freshest.bgHex { self.bgHex = freshest.bgHex }
            if self.fgHex != freshest.fgHex { self.fgHex = freshest.fgHex }
            if let model = WhisperModelTier(rawValue: freshest.selectedModelRaw), self.selectedModel != model {
                self.selectedModel = model
            }
            if let state = WhisperOrbState(rawValue: freshest.orbStateRaw), self.orbState != state {
                self.orbState = state
            }
            if abs(self.currentAudioLevel - freshest.currentAudioLevel) > 0.005 {
                self.currentAudioLevel = freshest.currentAudioLevel
            }
            if self.pendingTranscribedText != freshest.pendingTranscribedText {
                self.pendingTranscribedText = freshest.pendingTranscribedText
            }
            if self.lastInsertedSentenceLength != freshest.lastInsertedSentenceLength {
                self.lastInsertedSentenceLength = freshest.lastInsertedSentenceLength
            }
            if self.shouldDeleteLastSentence != freshest.shouldDeleteLastSentence {
                self.shouldDeleteLastSentence = freshest.shouldDeleteLastSentence
            }
            if self.autoSpaceForGoodnotes != freshest.autoSpaceForGoodnotes {
                self.autoSpaceForGoodnotes = freshest.autoSpaceForGoodnotes
            }
            if self.autoPunctuation != freshest.autoPunctuation {
                self.autoPunctuation = freshest.autoPunctuation
            }
            if abs(self.vadSilenceThresholdSeconds - freshest.vadSilenceThresholdSeconds) > 0.01 {
                self.vadSilenceThresholdSeconds = freshest.vadSilenceThresholdSeconds
            }
            if self.isBackgroundEngineActive != freshest.isBackgroundEngineActive {
                self.isBackgroundEngineActive = freshest.isBackgroundEngineActive
            }
        }

        if Thread.isMainThread {
            applyBlock()
        } else {
            DispatchQueue.main.async(execute: applyBlock)
        }
    }

    // MARK: - Actions Métier Clavier <-> Application

    /// Appelé par le clavier quand l'élève touche la bulle GeminiBubbleView
    public func requestToggleRecordingFromKeyboard() {
        postDarwinNotificationNamed(Self.darwinToggleRecordingNotification)
    }

    /// Appelé par WhisperEngine quand une phrase est transcrite et prête pour Goodnotes
    public func publishTranscribedText(_ rawText: String) {
        let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let formattedText: String
        if autoSpaceForGoodnotes && !trimmed.hasSuffix("\n") {
            formattedText = trimmed + " "
        } else {
            formattedText = trimmed
        }

        let updateBlock = { [weak self] in
            guard let self = self else { return }
            self.lastInsertedSentenceLength = formattedText.count
            self.pendingTranscribedText = formattedText
            self.orbState = .idle
            self.currentAudioLevel = 0.0
            self.persistAndBroadcastState(postDarwinNotification: true)
        }

        if Thread.isMainThread {
            updateBlock()
        } else {
            DispatchQueue.main.async(execute: updateBlock)
        }
    }

    /// Appelé par le clavier dès qu'il a inséré pendingTranscribedText dans Goodnotes
    public func consumePendingTranscribedText() -> String? {
        guard !pendingTranscribedText.isEmpty else { return nil }
        let textToInsert = pendingTranscribedText
        isApplyingRemoteUpdate = true
        pendingTranscribedText = ""
        isApplyingRemoteUpdate = false
        persistAndBroadcastState(postDarwinNotification: false)
        return textToInsert
    }

    /// Déclenche la suppression de la dernière phrase dictée (par commande vocale ou bouton)
    public func triggerDeleteLastSentence() {
        let updateBlock = { [weak self] in
            guard let self = self else { return }
            guard self.lastInsertedSentenceLength > 0 else { return }
            self.shouldDeleteLastSentence = true
            self.orbState = .idle
            self.persistAndBroadcastState(postDarwinNotification: true)
        }

        if Thread.isMainThread {
            updateBlock()
        } else {
            DispatchQueue.main.async(execute: updateBlock)
        }
    }

    /// Appelé par le clavier pour exécuter et acquitter la suppression de la dernière phrase
    public func consumeDeleteLastSentenceLength() -> Int? {
        guard shouldDeleteLastSentence, lastInsertedSentenceLength > 0 else { return nil }
        let length = lastInsertedSentenceLength
        isApplyingRemoteUpdate = true
        shouldDeleteLastSentence = false
        lastInsertedSentenceLength = 0
        isApplyingRemoteUpdate = false
        persistAndBroadcastState(postDarwinNotification: false)
        return length
    }

    // MARK: - Notifications Système Darwin (C-API Traversant Toutes les Sandboxes iOS)

    private func registerDarwinNotificationObservers() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observerPtr = Unmanaged.passUnretained(self).toOpaque()

        // 1. Observation de la demande de démarrage/arrêt d'écoute venant du clavier
        CFNotificationCenterAddObserver(
            center,
            observerPtr,
            { _, observer, _, _, _ in
                guard let observer = observer else { return }
                let manager = Unmanaged<AppGroupManager>.fromOpaque(observer).takeUnretainedValue()
                DispatchQueue.main.async {
                    manager.reloadStateFromChannels()
                    manager.onToggleRecordingRequested?()
                }
            },
            Self.darwinToggleRecordingNotification as CFString,
            nil,
            .deliverImmediately
        )

        // 2. Observation des mises à jour d'état (couleurs, texte transcrit, état de la bulle)
        CFNotificationCenterAddObserver(
            center,
            observerPtr,
            { _, observer, _, _, _ in
                guard let observer = observer else { return }
                let manager = Unmanaged<AppGroupManager>.fromOpaque(observer).takeUnretainedValue()
                DispatchQueue.main.async {
                    manager.reloadStateFromChannels()
                }
            },
            Self.darwinStateUpdatedNotification as CFString,
            nil,
            .deliverImmediately
        )
    }

    private func postDarwinNotificationNamed(_ name: String) {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterPostNotification(
            center,
            CFNotificationName(name as CFString),
            nil,
            nil,
            true
        )
    }

    // MARK: - Polling Rapide Temps Réel (0.05s / 20 Hz)

    public func startFastPolling() {
        pollingTimer?.invalidate()
        let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in
            self?.reloadStateFromChannels()
        }
        RunLoop.main.add(timer, forMode: .common)
        pollingTimer = timer
    }

    public func stopFastPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }
}

private extension DispatchQueue {
    static var isMain: Bool {
        Thread.isMainThread
    }
}
