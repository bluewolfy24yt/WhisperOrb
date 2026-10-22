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
                    } else if let anyTier = WhisperModelTier.allCases.first(where: { modelDownloader.isModelDownloaded($0) }) {
                        appGroup.selectedModel = anyTier
                        whisperEngine.loadModel(anyTier)
                        if !whisperEngine.isBackgroundEngineRunning {
                            whisperEngine.startBackgroundAudioEngine()
                        }
                    }
                }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .active:
                appGroup.reloadStateFromChannels()
                modelDownloader.refreshDownloadedModels()
                if !whisperEngine.isBackgroundEngineRunning && !modelDownloader.downloadedModels.isEmpty {
                    whisperEngine.startBackgroundAudioEngine()
                }
            case .inactive, .background:
                appGroup.persistAndBroadcastState(postDarwinNotification: true)
            @unknown default:
                break
            }
        }
    }
}
