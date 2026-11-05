import SwiftUI

@main
struct WhisperOrbApp: App {
    @StateObject private var appGroup = AppGroupManager.shared
    @StateObject private var modelDownloader = ModelDownloader.shared
    @StateObject private var whisperEngine = WhisperEngine.shared
    @StateObject private var networkMonitor = NetworkMonitor.shared

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appGroup)
                .environmentObject(modelDownloader)
                .environmentObject(whisperEngine)
                .environmentObject(networkMonitor)
                .preferredColorScheme(.dark)
                .onAppear {
                    modelDownloader.refreshDownloadedModels()
                    let currentTier = appGroup.selectedModel
                    if modelDownloader.isModelDownloaded(currentTier) {
                        if whisperEngine.loadedTier == nil {
                            whisperEngine.loadModel(currentTier)
                        }
                        if !whisperEngine.isBackgroundEngineRunning {
                            whisperEngine.startBackgroundAudioEngine()
                        }
                    } else if let anyTier = WhisperModelTier.allCases.first(where: {
                        modelDownloader.isModelDownloaded($0) &&
                        HardwareDetector.isTierUnlocked(
                            $0,
                            simulate4GB: appGroup.simulateModest4GBDevice,
                            forceUnlock: appGroup.forceUnlockAllModels
                        )
                    }) {
                        appGroup.selectedModel = anyTier
                        whisperEngine.loadModel(anyTier)
                        if !whisperEngine.isBackgroundEngineRunning {
                            whisperEngine.startBackgroundAudioEngine()
                        }
                    }
                }
                // Sans ceci, chaque whisperorb:// peut ouvrir une nouvelle fenêtre sur iPad
                .handlesExternalEvents(preferring: Set(["*"]), allowing: Set(["*"]))
                .onOpenURL { _ in
                    appGroup.reloadStateFromChannels()
                    modelDownloader.refreshDownloadedModels()
                    if !whisperEngine.isAudioEngineAlive {
                        whisperEngine.startBackgroundAudioEngine()
                    }
                }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .active:
                appGroup.reloadStateFromChannels()
                modelDownloader.refreshDownloadedModels()
                if !whisperEngine.isAudioEngineAlive && !modelDownloader.downloadedModels.isEmpty {
                    whisperEngine.startBackgroundAudioEngine()
                }
                whisperEngine.prepareInferenceBackend(forBackground: false)
            case .inactive:
                appGroup.persistAndBroadcastState(postDarwinNotification: true)
            case .background:
                appGroup.persistAndBroadcastState(postDarwinNotification: true)
                whisperEngine.prepareInferenceBackend(forBackground: true)
            @unknown default:
                break
            }
        }
    }
}
