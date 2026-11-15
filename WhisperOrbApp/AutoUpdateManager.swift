import Foundation
import UIKit
import Combine

// MARK: - Manifeste de Version GitHub & Source AltStore / SideStore

public struct GitHubVersionManifest: Codable, Equatable {
    public let version: String
    public let buildNumber: Int
    public let tagName: String
    public let ipaDownloadURL: String
    public let ipaSizeBytes: Int64?
    public let altstoreSourceURL: String?
    public let releaseNotes: String?
    public let publishedAt: String?
}

private struct GitHubReleaseAPIResponse: Decodable {
    struct Asset: Decodable {
        let name: String
        let browser_download_url: String
        let size: Int64?
    }
    let tag_name: String
    let body: String?
    let published_at: String?
    let assets: [Asset]
}

public enum AutoUpdateState: Equatable {
    case idle
    case checking
    case upToDate
    case updateAvailable(GitHubVersionManifest)
    case downloading(GitHubVersionManifest, Double)
    case readyToInstall(GitHubVersionManifest, URL)
    case error(String)
}

public enum DetectedSideloadInstaller: String {
    case trollStore = "TrollStore"
    case sideStore = "SideStore"
    case altStore = "AltStore"
    case feather = "Feather"
    case scarlet = "Scarlet"

    public func installURL(forRemoteIPAUrl remoteURL: String) -> URL? {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        guard let encoded = remoteURL.addingPercentEncoding(withAllowedCharacters: allowed) else {
            return nil
        }
        switch self {
        case .trollStore:
            return URL(string: "apple-magnifier://install?url=\(encoded)")
        case .sideStore:
            return URL(string: "sidestore://install?url=\(encoded)")
        case .altStore:
            return URL(string: "altstore://install?url=\(encoded)")
        case .feather:
            return URL(string: "feather://install?url=\(encoded)")
        case .scarlet:
            return URL(string: "scarlet://install?url=\(encoded)")
        }
    }

    public func sourceURL(forAltStoreSource sourceURLString: String) -> URL? {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        guard let encoded = sourceURLString.addingPercentEncoding(withAllowedCharacters: allowed) else {
            return nil
        }
        switch self {
        case .sideStore:
            return URL(string: "sidestore://source?url=\(encoded)")
        case .altStore:
            return URL(string: "altstore://source?url=\(encoded)")
        case .feather:
            return URL(string: "feather://source/\(encoded)")
        default:
            return nil
        }
    }
}

// MARK: - Gestionnaire de Mises à Jour Automatiques depuis GitHub

public final class AutoUpdateManager: NSObject, ObservableObject, URLSessionDownloadDelegate {
    public static let shared = AutoUpdateManager()

    public static let latestVersionManifestURL = URL(
        string: "https://github.com/bluewolfy24yt/WhisperOrb/releases/latest/download/version.json"
    )!
    public static let latestAltStoreSourceURLString =
        "https://github.com/bluewolfy24yt/WhisperOrb/releases/latest/download/altstore-source.json"
    public static let githubLatestReleaseAPIURL = URL(
        string: "https://api.github.com/repos/bluewolfy24yt/WhisperOrb/releases/latest"
    )!

    @Published public private(set) var state: AutoUpdateState = .idle
    @Published public private(set) var latestManifest: GitHubVersionManifest? = nil
    @Published public private(set) var downloadedIPAURL: URL? = nil
    @Published public private(set) var detectedInstaller: DetectedSideloadInstaller? = nil
    @Published public var statusMessage: String = "Vérification auto des MAJ GitHub activée"

    @Published public var isAutoUpdateEnabled: Bool {
        didSet {
            UserDefaults.standard.set(isAutoUpdateEnabled, forKey: Keys.autoUpdateEnabled)
            if isAutoUpdateEnabled {
                checkForUpdatesAutomatically(force: true)
            }
        }
    }

    private enum Keys {
        static let autoUpdateEnabled = "whisperorb.autoUpdateEnabled"
        static let lastAutoPromptedVersionKey = "whisperorb.lastAutoPromptedVersion"
    }

    private var checkTimer: Timer?
    private var lastCheckTimestamp: TimeInterval = 0
    private var isCheckingNow: Bool = false
    private var activeDownloadTask: URLSessionDownloadTask?
    private var pendingDownloadManifest: GitHubVersionManifest?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid

    private lazy var downloadSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 1800
        return URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }()

    public var currentVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "2.3.0"
    }

    public var currentBuildNumber: Int {
        if let raw = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
           let num = Int(raw.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return num
        }
        return 0
    }

    public var currentVersionBadge: String {
        "v\(currentVersion)"
    }

    private var updatesDirectoryURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = docs.appendingPathComponent("Updates", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDir = dir
        try? mutableDir.setResourceValues(values)
        return dir
    }

    public override init() {
        if UserDefaults.standard.object(forKey: Keys.autoUpdateEnabled) == nil {
            self.isAutoUpdateEnabled = true
        } else {
            self.isAutoUpdateEnabled = UserDefaults.standard.bool(forKey: Keys.autoUpdateEnabled)
        }
        super.init()
        refreshDetectedInstaller()
        startPeriodicCheckTimer()
    }

    deinit {
        checkTimer?.invalidate()
    }

    private func startPeriodicCheckTimer() {
        checkTimer?.invalidate()
        let timer = Timer(timeInterval: 2 * 3600, repeats: true) { [weak self] _ in
            self?.checkForUpdatesAutomatically(force: false)
        }
        RunLoop.main.add(timer, forMode: .common)
        checkTimer = timer
    }

    public func refreshDetectedInstaller() {
        let candidates: [(String, DetectedSideloadInstaller)] = [
            ("apple-magnifier://", .trollStore),
            ("sidestore://", .sideStore),
            ("altstore://", .altStore),
            ("feather://", .feather),
            ("scarlet://", .scarlet)
        ]
        var found: DetectedSideloadInstaller? = nil
        for (scheme, installer) in candidates {
            if let url = URL(string: scheme), UIApplication.shared.canOpenURL(url) {
                found = installer
                break
            }
        }
        if detectedInstaller != found {
            detectedInstaller = found
        }
    }

    // MARK: - Vérification Automatique GitHub Releases + OTA Rules

    public func checkForUpdatesAutomatically(force: Bool = false) {
        RemoteRulesSyncManager.shared.syncFromGitHubIfNeeded(force: force)
        refreshDetectedInstaller()

        guard isAutoUpdateEnabled || force else { return }

        let now = Date().timeIntervalSince1970
        if isCheckingNow || activeDownloadTask != nil { return }
        if !force && (now - lastCheckTimestamp) < 900.0 { return }

        isCheckingNow = true
        lastCheckTimestamp = now
        if force {
            state = .checking
            statusMessage = "Recherche de mise à jour sur GitHub..."
        }

        fetchLatestManifestFromGitHub { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isCheckingNow = false

                switch result {
                case .failure(let error):
                    if force {
                        self.state = .error(error.localizedDescription)
                        self.statusMessage = "Impossible de joindre GitHub (\(error.localizedDescription))"
                    }
                case .success(let manifest):
                    self.latestManifest = manifest
                    if self.isRemoteNewer(manifest) {
                        let cachedURL = self.localIPAFileURL(for: manifest)
                        if self.isValidIPAFile(at: cachedURL) {
                            self.downloadedIPAURL = cachedURL
                            self.state = .readyToInstall(manifest, cachedURL)
                            self.statusMessage = "Nouvelle MAJ v\(manifest.version) prête à installer"
                            self.autoTriggerInstallerIfPossible(for: manifest, localIPA: cachedURL)
                        } else if self.isAutoUpdateEnabled {
                            self.startAutomaticIPADownload(manifest)
                        } else {
                            self.state = .updateAvailable(manifest)
                            self.statusMessage = "Mise à jour v\(manifest.version) disponible sur GitHub"
                        }
                    } else {
                        self.state = .upToDate
                        self.statusMessage = "À jour (v\(self.currentVersion) • build \(self.currentBuildNumber))"
                    }
                }
            }
        }
    }

    private func fetchLatestManifestFromGitHub(completion: @escaping (Result<GitHubVersionManifest, Error>) -> Void) {
        var request = URLRequest(url: Self.latestVersionManifestURL)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 12.0

        URLSession.shared.dataTask(with: request) { [weak self] data, response, _ in
            if let http = response as? HTTPURLResponse,
               (200...299).contains(http.statusCode),
               let data = data,
               let decoded = try? JSONDecoder().decode(GitHubVersionManifest.self, from: data),
               !decoded.ipaDownloadURL.isEmpty {
                completion(.success(decoded))
                return
            }
            self?.fetchFallbackGitHubReleaseAPI(completion: completion)
        }.resume()
    }

    private func fetchFallbackGitHubReleaseAPI(completion: @escaping (Result<GitHubVersionManifest, Error>) -> Void) {
        var request = URLRequest(url: Self.githubLatestReleaseAPIURL)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 12.0
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("WhisperOrb-iPad-AutoUpdater", forHTTPHeaderField: "User-Agent")

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let http = response as? HTTPURLResponse,
                  (200...299).contains(http.statusCode),
                  let data = data,
                  let release = try? JSONDecoder().decode(GitHubReleaseAPIResponse.self, from: data) else {
                let err = NSError(
                    domain: "AutoUpdateManager",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Aucune release GitHub lisible."]
                )
                completion(.failure(err))
                return
            }

            guard let ipaAsset = release.assets.first(where: { $0.name.lowercased().hasSuffix(".ipa") }) else {
                let err = NSError(
                    domain: "AutoUpdateManager",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Aucun fichier .ipa dans la dernière release."]
                )
                completion(.failure(err))
                return
            }

            let cleanVersion = release.tag_name
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "^[vV]", with: "", options: .regularExpression)
            let parts = cleanVersion.split(separator: ".").compactMap { Int($0) }
            let inferredBuild = parts.count >= 3 ? parts.last! : 0

            let manifest = GitHubVersionManifest(
                version: cleanVersion.isEmpty ? "2.3.0" : cleanVersion,
                buildNumber: inferredBuild,
                tagName: release.tag_name,
                ipaDownloadURL: ipaAsset.browser_download_url,
                ipaSizeBytes: ipaAsset.size,
                altstoreSourceURL: Self.latestAltStoreSourceURLString,
                releaseNotes: release.body,
                publishedAt: release.published_at
            )
            completion(.success(manifest))
        }.resume()
    }

    // MARK: - Comparaison Sémantique de Version & Build

    public func isRemoteNewer(_ remote: GitHubVersionManifest) -> Bool {
        let localTuple = parseVersionTuple(currentVersion)
        let remoteTuple = parseVersionTuple(remote.version)
        let maxLen = max(localTuple.count, remoteTuple.count)

        for idx in 0..<maxLen {
            let l = idx < localTuple.count ? localTuple[idx] : 0
            let r = idx < remoteTuple.count ? remoteTuple[idx] : 0
            if r > l { return true }
            if r < l { return false }
        }
        if remote.buildNumber > 0 && currentBuildNumber > 0 {
            return remote.buildNumber > currentBuildNumber
        }
        return false
    }

    private func parseVersionTuple(_ versionString: String) -> [Int] {
        let cleaned = versionString
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "^[vV]", with: "", options: .regularExpression)
        return cleaned.split(separator: ".").map { Int($0) ?? 0 }
    }

    // MARK: - Téléchargement Automatique de WhisperOrb-iPad.ipa en Arrière-Plan

    private func localIPAFileURL(for manifest: GitHubVersionManifest) -> URL {
        let safeVersion = manifest.version.replacingOccurrences(of: "/", with: "_")
        return updatesDirectoryURL.appendingPathComponent(
            "WhisperOrb-iPad-\(safeVersion)-b\(manifest.buildNumber).ipa",
            isDirectory: false
        )
    }

    private func isValidIPAFile(at url: URL) -> Bool {
        guard FileManager.default.fileExists(atPath: url.path),
              let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let fileSize = attrs[.size] as? UInt64,
              fileSize > 1_000_000,
              let handle = try? FileHandle(forReadingFrom: url) else {
            return false
        }
        defer { try? handle.close() }
        guard let magic = try? handle.read(upToCount: 4), magic.count == 4 else {
            return false
        }
        // Signature ZIP standard : PK\x03\x04
        return magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04
    }

    public func startAutomaticIPADownload(_ manifest: GitHubVersionManifest) {
        guard activeDownloadTask == nil,
              let url = URL(string: manifest.ipaDownloadURL) else {
            return
        }

        let cachedURL = localIPAFileURL(for: manifest)
        if isValidIPAFile(at: cachedURL) {
            downloadedIPAURL = cachedURL
            state = .readyToInstall(manifest, cachedURL)
            statusMessage = "MAJ v\(manifest.version) prête à installer"
            autoTriggerInstallerIfPossible(for: manifest, localIPA: cachedURL)
            return
        }

        pendingDownloadManifest = manifest
        state = .downloading(manifest, 0.0)
        statusMessage = "Téléchargement auto de la MAJ v\(manifest.version)... 0 %"
        beginBackgroundTaskIfNeeded()

        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let task = downloadSession.downloadTask(with: request)
        activeDownloadTask = task
        task.resume()
    }

    private func beginBackgroundTaskIfNeeded() {
        guard backgroundTaskID == .invalid else { return }
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "WhisperOrbAutoUpdateIPA") { [weak self] in
            self?.endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        guard backgroundTaskID != .invalid else { return }
        let id = backgroundTaskID
        backgroundTaskID = .invalid
        UIApplication.shared.endBackgroundTask(id)
    }

    private func cleanupOlderIPAs(keeping keepURL: URL) {
        guard let files = try? FileManager.default.contentsOfDirectory(at: updatesDirectoryURL, includingPropertiesForKeys: nil) else {
            return
        }
        for file in files where file.lastPathComponent != keepURL.lastPathComponent {
            try? FileManager.default.removeItem(at: file)
        }
    }

    // MARK: - Installation 1-Clic / 0-Clic (SideStore, AltStore, Feather, TrollStore ou Partage iPadOS)

    private func autoTriggerInstallerIfPossible(for manifest: GitHubVersionManifest, localIPA: URL) {
        guard isAutoUpdateEnabled,
              UIApplication.shared.applicationState == .active else {
            return
        }
        let versionKey = "\(manifest.version)#\(manifest.buildNumber)"
        let alreadyPrompted = UserDefaults.standard.string(forKey: Keys.lastAutoPromptedVersionKey)
        guard alreadyPrompted != versionKey else { return }

        refreshDetectedInstaller()
        guard let installer = detectedInstaller,
              let schemeURL = installer.installURL(forRemoteIPAUrl: manifest.ipaDownloadURL) else {
            return
        }

        UserDefaults.standard.set(versionKey, forKey: Keys.lastAutoPromptedVersionKey)
        statusMessage = "Ouverture automatique de \(installer.rawValue) pour installer v\(manifest.version)..."
        UIApplication.shared.open(schemeURL, options: [:], completionHandler: nil)
    }

    public func installUpdateNow() {
        refreshDetectedInstaller()

        if let manifest = latestManifest,
           let installer = detectedInstaller,
           let schemeURL = installer.installURL(forRemoteIPAUrl: manifest.ipaDownloadURL) {
            UIApplication.shared.open(schemeURL, options: [:], completionHandler: nil)
            return
        }

        if let localURL = downloadedIPAURL, isValidIPAFile(at: localURL) {
            presentIPAShareSheetOnIPadSafe(fileURL: localURL)
            return
        }

        if let manifest = latestManifest {
            startAutomaticIPADownload(manifest)
        } else {
            checkForUpdatesAutomatically(force: true)
        }
    }

    /// Ajoute la source AltStore/SideStore officielle générée sur GitHub pour activer le rafraîchissement et la MAJ 100 % en arrière-plan.
    @discardableResult
    public func addAutoUpdateSourceToSideStoreOrAltStore() -> Bool {
        refreshDetectedInstaller()
        let sourceURLString = latestManifest?.altstoreSourceURL ?? Self.latestAltStoreSourceURLString

        if let installer = detectedInstaller,
           let deepLink = installer.sourceURL(forAltStoreSource: sourceURLString) {
            UIApplication.shared.open(deepLink, options: [:], completionHandler: nil)
            return true
        }

        for fallbackInstaller in [DetectedSideloadInstaller.sideStore, .altStore, .feather] {
            if let deepLink = fallbackInstaller.sourceURL(forAltStoreSource: sourceURLString),
               UIApplication.shared.canOpenURL(deepLink) {
                UIApplication.shared.open(deepLink, options: [:], completionHandler: nil)
                return true
            }
        }

        UIPasteboard.general.string = sourceURLString
        statusMessage = "Lien Source auto-MAJ copié ! Collez-le dans SideStore / AltStore > Sources."
        return false
    }

    private func presentIPAShareSheetOnIPadSafe(fileURL: URL) {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
              let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController
                ?? windowScene.windows.first?.rootViewController else {
            return
        }

        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }

        let activityVC = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
        // Obligatoire sur iPadOS : sans sourceView/sourceRect, UIActivityViewController lève NSGenericException
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = topVC.view
            popover.sourceRect = CGRect(
                x: topVC.view.bounds.midX,
                y: topVC.view.bounds.midY,
                width: 1,
                height: 1
            )
            popover.permittedArrowDirections = []
        }
        topVC.present(activityVC, animated: true)
    }

    // MARK: - URLSessionDownloadDelegate

    public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        guard let manifest = pendingDownloadManifest else { return }
        let expected = totalBytesExpectedToWrite > 0
            ? totalBytesExpectedToWrite
            : (manifest.ipaSizeBytes ?? 15_000_000)
        let progress = min(0.99, max(0.0, Double(totalBytesWritten) / Double(max(1, expected))))
        state = .downloading(manifest, progress)
        statusMessage = "Téléchargement auto MAJ v\(manifest.version)... \(Int(progress * 100)) %"
    }

    public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        defer {
            activeDownloadTask = nil
            endBackgroundTask()
        }

        guard let manifest = pendingDownloadManifest else { return }

        if let http = downloadTask.response as? HTTPURLResponse,
           !(200...299).contains(http.statusCode) {
            state = .error("Erreur HTTP \(http.statusCode)")
            statusMessage = "Échec du téléchargement de la MAJ (HTTP \(http.statusCode))"
            return
        }

        let destination = localIPAFileURL(for: manifest)
        do {
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.moveItem(at: location, to: destination)

            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var mutableDest = destination
            try? mutableDest.setResourceValues(values)

            guard isValidIPAFile(at: destination) else {
                try? FileManager.default.removeItem(at: destination)
                state = .error("Fichier .ipa téléchargé invalide.")
                statusMessage = "Le fichier téléchargé depuis GitHub est incomplet."
                return
            }

            cleanupOlderIPAs(keeping: destination)
            downloadedIPAURL = destination
            state = .readyToInstall(manifest, destination)
            statusMessage = "MAJ v\(manifest.version) téléchargée et prête !"
            autoTriggerInstallerIfPossible(for: manifest, localIPA: destination)
        } catch {
            state = .error(error.localizedDescription)
            statusMessage = "Erreur sauvegarde IPA : \(error.localizedDescription)"
        }
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let error = error else { return }
        activeDownloadTask = nil
        endBackgroundTask()
        state = .error(error.localizedDescription)
        statusMessage = "Téléchargement MAJ interrompu : \(error.localizedDescription)"
    }
}
