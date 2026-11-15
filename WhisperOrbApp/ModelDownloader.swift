import Foundation
import UIKit
import Combine

public final class ModelDownloader: NSObject, ObservableObject, URLSessionDownloadDelegate {
    public static let shared = ModelDownloader()

    @Published public var downloadProgress: [WhisperModelTier: Double] = [:]
    @Published public var downloadedModels: Set<WhisperModelTier> = []
    @Published public var activeDownloads: Set<WhisperModelTier> = []
    @Published public var errorMessage: String? = nil

    private var downloadTasks: [WhisperModelTier: URLSessionDownloadTask] = [:]
    private var taskToTierMap: [Int: WhisperModelTier] = [:]
    private var resumeDataByTier: [WhisperModelTier: Data] = [:]
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid

    /// Session in-process compatible à 100 % avec tous les outils de sideloading (Sideloadly, AltStore, SideStore, Feather, ESign).
    /// Une session `.background` (`nsurlsessiond`) se bloque à 0 % ou échoue (-997) lorsque le Bundle ID est réécrit au re-signing.
    private lazy var urlSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 90
        config.timeoutIntervalForResource = 4 * 3600
        config.httpMaximumConnectionsPerHost = 2
        return URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }()

    public override init() {
        super.init()
        refreshDownloadedModels()
    }

    private func tier(for task: URLSessionTask) -> WhisperModelTier? {
        if let known = taskToTierMap[task.taskIdentifier] { return known }
        let fileName = task.originalRequest?.url?.lastPathComponent
        return WhisperModelTier.allCases.first { $0.fileName == fileName }
    }

    private func beginBackgroundProtectionIfNeeded() {
        guard backgroundTaskID == .invalid else { return }
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "WhisperOrbModelDownload") { [weak self] in
            self?.endBackgroundProtection()
        }
    }

    private func endBackgroundProtectionIfIdle() {
        guard activeDownloads.isEmpty else { return }
        endBackgroundProtection()
    }

    private func endBackgroundProtection() {
        guard backgroundTaskID != .invalid else { return }
        let id = backgroundTaskID
        backgroundTaskID = .invalid
        UIApplication.shared.endBackgroundTask(id)
    }

    /// Au tout premier lancement, télécharge automatiquement Whisper Flash (~57 Mo) pour que le clavier fonctionne immédiatement.
    public func ensureStarterModelAvailableOrDownload() {
        refreshDownloadedModels()
        guard downloadedModels.isEmpty, activeDownloads.isEmpty else { return }
        let starterTier: WhisperModelTier = .eclair
        if AppGroupManager.shared.selectedModel != starterTier {
            AppGroupManager.shared.selectedModel = starterTier
        }
        downloadModel(starterTier)
    }

    // MARK: - Chemins Locaux & Protection Hors-Ligne (Exclusion Backup iCloud / Purge iPadOS)

    public var modelsDirectoryURL: URL {
        AppGroupManager.shared.modelsDirectoryURL
    }

    public func localModelURL(for tier: WhisperModelTier) -> URL {
        return modelsDirectoryURL.appendingPathComponent(tier.fileName, isDirectory: false)
    }

    public func isModelDownloaded(_ tier: WhisperModelTier) -> Bool {
        let fileURL = localModelURL(for: tier)
        guard FileManager.default.fileExists(atPath: fileURL.path),
              let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let fileSize = attrs[.size] as? UInt64 else {
            return false
        }
        // Un modèle GGML valide pèse au moins 20 Mo
        return fileSize > 20 * 1024 * 1024
    }

    public func downloadedFileSizeMB(for tier: WhisperModelTier) -> Int? {
        let fileURL = localModelURL(for: tier)
        guard FileManager.default.fileExists(atPath: fileURL.path),
              let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let fileSize = attrs[.size] as? UInt64,
              fileSize > 20 * 1024 * 1024 else {
            return nil
        }
        return Int(Double(fileSize) / (1024.0 * 1024.0))
    }

    public func refreshDownloadedModels() {
        var found: Set<WhisperModelTier> = []
        for tier in WhisperModelTier.allCases {
            if isModelDownloaded(tier) {
                found.insert(tier)
                excludeFromBackup(url: localModelURL(for: tier))
            }
        }
        let sortedRaws = found.map(\.rawValue).sorted()
        if Thread.isMainThread {
            self.downloadedModels = found
            if AppGroupManager.shared.downloadedModelRaws != sortedRaws {
                AppGroupManager.shared.downloadedModelRaws = sortedRaws
            }
        } else {
            DispatchQueue.main.async {
                self.downloadedModels = found
                if AppGroupManager.shared.downloadedModelRaws != sortedRaws {
                    AppGroupManager.shared.downloadedModelRaws = sortedRaws
                }
            }
        }
    }

    private func excludeFromBackup(url: URL) {
        var mutableURL = url
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        try? mutableURL.setResourceValues(resourceValues)
        try? (url as NSURL).setResourceValue(true, forKey: .isExcludedFromBackupKey)
    }

    // MARK: - Téléchargement, Annulation & Suppression

    public func downloadModel(_ tier: WhisperModelTier) {
        guard !activeDownloads.contains(tier) else { return }

        // Le fichier temporaire et sa copie finale ont besoin d'une marge
        let values = try? modelsDirectoryURL.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        if let available = values?.volumeAvailableCapacityForImportantUsage,
           available < Int64(tier.sizeMB) * 1024 * 1024 * 12 / 10 {
            errorMessage = "Espace insuffisant pour \(tier.displayName) (\(tier.sizeMB) Mo requis)."
            return
        }

        errorMessage = nil
        if downloadProgress[tier] == nil {
            downloadProgress[tier] = 0.0
        }
        activeDownloads.insert(tier)
        beginBackgroundProtectionIfNeeded()

        let task: URLSessionDownloadTask
        if let resumeData = resumeDataByTier.removeValue(forKey: tier) {
            task = urlSession.downloadTask(withResumeData: resumeData)
        } else {
            var request = URLRequest(url: tier.downloadURL)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            task = urlSession.downloadTask(with: request)
        }

        downloadTasks[tier] = task
        taskToTierMap[task.taskIdentifier] = tier
        task.resume()
    }

    public func cancelDownload(_ tier: WhisperModelTier) {
        if let task = downloadTasks[tier] {
            taskToTierMap.removeValue(forKey: task.taskIdentifier)
            task.cancel()
            downloadTasks.removeValue(forKey: tier)
        }
        resumeDataByTier.removeValue(forKey: tier)
        activeDownloads.remove(tier)
        downloadProgress.removeValue(forKey: tier)
        endBackgroundProtectionIfIdle()
    }

    public func deleteModel(_ tier: WhisperModelTier) {
        cancelDownload(tier)
        let destinationURL = localModelURL(for: tier)
        if FileManager.default.fileExists(atPath: destinationURL.path) {
            do {
                try FileManager.default.removeItem(at: destinationURL)
            } catch {
                errorMessage = "Impossible de supprimer \(tier.displayName) : \(error.localizedDescription)"
            }
        }
        refreshDownloadedModels()
    }

    // MARK: - URLSessionDownloadDelegate

    public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        guard let tier = tier(for: downloadTask) else { return }

        let expectedBytes: Double
        if totalBytesExpectedToWrite > 0 {
            expectedBytes = Double(totalBytesExpectedToWrite)
        } else {
            expectedBytes = Double(tier.sizeMB) * 1024.0 * 1024.0
        }

        let fraction = max(0.0, min(0.99, Double(totalBytesWritten) / expectedBytes))
        DispatchQueue.main.async {
            self.downloadProgress[tier] = fraction
        }
    }

    public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        guard let tier = tier(for: downloadTask) else { return }

        if let httpResponse = downloadTask.response as? HTTPURLResponse,
           !(200...299).contains(httpResponse.statusCode) {
            DispatchQueue.main.async {
                self.errorMessage = "Erreur serveur HuggingFace (HTTP \(httpResponse.statusCode)) pour \(tier.displayName)."
                self.activeDownloads.remove(tier)
                self.downloadProgress.removeValue(forKey: tier)
                self.downloadTasks.removeValue(forKey: tier)
                self.taskToTierMap.removeValue(forKey: downloadTask.taskIdentifier)
                self.endBackgroundProtectionIfIdle()
            }
            return
        }

        let destinationURL = localModelURL(for: tier)

        do {
            let expectedSize = downloadTask.countOfBytesExpectedToReceive
            let receivedSize = (try? FileManager.default.attributesOfItem(atPath: location.path))?[.size] as? Int64
            if expectedSize > 0, let receivedSize = receivedSize, receivedSize != expectedSize {
                throw NSError(domain: "WhisperOrb", code: 1, userInfo: [NSLocalizedDescriptionKey: "fichier incomplet"])
            }

            let parentDir = destinationURL.deletingLastPathComponent()
            if !FileManager.default.fileExists(atPath: parentDir.path) {
                try FileManager.default.createDirectory(at: parentDir, withIntermediateDirectories: true)
            }
            if FileManager.default.fileExists(atPath: destinationURL.path) {
                try FileManager.default.removeItem(at: destinationURL)
            }
            try FileManager.default.moveItem(at: location, to: destinationURL)
            excludeFromBackup(url: destinationURL)

            DispatchQueue.main.async {
                self.downloadProgress[tier] = 1.0
                self.resumeDataByTier.removeValue(forKey: tier)
                self.activeDownloads.remove(tier)
                self.downloadTasks.removeValue(forKey: tier)
                self.taskToTierMap.removeValue(forKey: downloadTask.taskIdentifier)
                self.endBackgroundProtectionIfIdle()
                self.refreshDownloadedModels()
                // Sélectionne et charge systématiquement le modèle fraîchement téléchargé
                if AppGroupManager.shared.selectedModel != tier {
                    AppGroupManager.shared.selectedModel = tier
                }
                WhisperEngine.shared.loadModel(tier)
                if !WhisperEngine.shared.isBackgroundEngineRunning {
                    WhisperEngine.shared.startBackgroundAudioEngine()
                }
            }
        } catch {
            DispatchQueue.main.async {
                self.errorMessage = "Erreur d'écriture disque (\(tier.displayName)) : \(error.localizedDescription)"
                self.activeDownloads.remove(tier)
                self.downloadProgress.removeValue(forKey: tier)
                self.downloadTasks.removeValue(forKey: tier)
                self.taskToTierMap.removeValue(forKey: downloadTask.taskIdentifier)
                self.endBackgroundProtectionIfIdle()
            }
        }
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let error = error else { return }
        let nsError = error as NSError
        guard nsError.code != NSURLErrorCancelled else { return }

        guard let tier = tier(for: task) else { return }

        DispatchQueue.main.async {
            if let resumeData = nsError.userInfo[NSURLSessionDownloadTaskResumeData] as? Data {
                self.resumeDataByTier[tier] = resumeData
            }
            self.errorMessage = "Échec du téléchargement (\(tier.displayName)) : \(error.localizedDescription)"
            self.activeDownloads.remove(tier)
            self.downloadTasks.removeValue(forKey: tier)
            self.taskToTierMap.removeValue(forKey: task.taskIdentifier)
            self.endBackgroundProtectionIfIdle()
        }
    }
}
