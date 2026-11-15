import Foundation

// MARK: - Synchronisation OTA Silencieuse depuis GitHub (0 Clic • Dictionnaires & Règles Vocales)

public struct RemoteOTARulesPayload: Codable, Equatable {
    public var schemaVersion: Int
    public var rulesRevision: Int
    public var updatedAt: String?
    public var extraHallucinations: [String]?
    public var extraStandaloneHallucinations: [String]?
    public var extraInlineVoiceCommands: [String: String]?
    public var extraInstantCorrectionsByLang: [String: [String: String]]?
}

public final class RemoteRulesSyncManager {
    public static let shared = RemoteRulesSyncManager()

    public static let remoteRulesURL = URL(string: "https://raw.githubusercontent.com/bluewolfy24yt/WhisperOrb/main/remote_rules.json")!
    private static let cacheFileName = "whisperorb_ota_rules_cache.json"

    private let lock = NSLock()
    private var currentRules = RemoteOTARulesPayload(
        schemaVersion: 1,
        rulesRevision: 0,
        updatedAt: nil,
        extraHallucinations: [],
        extraStandaloneHallucinations: [],
        extraInlineVoiceCommands: [:],
        extraInstantCorrectionsByLang: [:]
    )
    private var lastSyncTimestamp: TimeInterval = 0
    private var isSyncingNow: Bool = false

    private var cacheFileURL: URL {
        if let groupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: AppGroupManager.appGroupIdentifier) {
            return groupURL.appendingPathComponent(Self.cacheFileName, isDirectory: false)
        }
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        try? FileManager.default.createDirectory(at: docs, withIntermediateDirectories: true)
        return docs.appendingPathComponent(Self.cacheFileName, isDirectory: false)
    }

    private init() {
        loadCachedRulesFromDisk()
    }

    public var activeRevision: Int {
        lock.lock()
        defer { lock.unlock() }
        return currentRules.rulesRevision
    }

    public func extraHallucinations() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return currentRules.extraHallucinations ?? []
    }

    public func extraStandaloneHallucinations() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return currentRules.extraStandaloneHallucinations ?? []
    }

    public func extraInlineVoiceCommands() -> [String: String] {
        lock.lock()
        defer { lock.unlock() }
        return currentRules.extraInlineVoiceCommands ?? [:]
    }

    public func extraInstantCorrection(for word: String, languageCode: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        return currentRules.extraInstantCorrectionsByLang?[languageCode]?[word]
    }

    private func loadCachedRulesFromDisk() {
        guard let data = try? Data(contentsOf: cacheFileURL),
              let decoded = try? JSONDecoder().decode(RemoteOTARulesPayload.self, from: data) else {
            return
        }
        lock.lock()
        currentRules = decoded
        lock.unlock()
    }

    public func syncFromGitHubIfNeeded(force: Bool = false) {
        let now = Date().timeIntervalSince1970
        lock.lock()
        if isSyncingNow || (!force && (now - lastSyncTimestamp) < 900.0) {
            lock.unlock()
            return
        }
        isSyncingNow = true
        lastSyncTimestamp = now
        lock.unlock()

        var request = URLRequest(url: Self.remoteRulesURL)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 10.0

        URLSession.shared.dataTask(with: request) { [weak self] data, response, _ in
            guard let self = self else { return }
            defer {
                self.lock.lock()
                self.isSyncingNow = false
                self.lock.unlock()
            }

            guard let http = response as? HTTPURLResponse,
                  (200...299).contains(http.statusCode),
                  let data = data,
                  let decoded = try? JSONDecoder().decode(RemoteOTARulesPayload.self, from: data) else {
                return
            }

            self.lock.lock()
            let isNewer = decoded.rulesRevision >= self.currentRules.rulesRevision
            if isNewer {
                self.currentRules = decoded
            }
            self.lock.unlock()

            if isNewer {
                try? data.write(to: self.cacheFileURL, options: [.atomic])
            }
        }.resume()
    }
}
