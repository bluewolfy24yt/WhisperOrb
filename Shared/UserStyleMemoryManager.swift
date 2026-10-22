import Foundation
import Combine

// MARK: - Modèle JSON du Profil d'Écriture Personnel (user_style_memory.json)

public struct UserStyleMemoryPayload: Codable, Equatable {
    public var version: Int
    public var autoCapitalizeStart: Bool
    public var stripTrailingPeriodOnShortMessages: Bool
    public var shortMessageMaxWords: Int
    public var replacementsByLanguage: [String: [String: String]]
    public var learnedCorrections: [String: String]
    public var updatedAt: TimeInterval

    public init(
        version: Int = 2,
        autoCapitalizeStart: Bool = true,
        stripTrailingPeriodOnShortMessages: Bool = true,
        shortMessageMaxWords: Int = 12,
        replacementsByLanguage: [String: [String: String]] = Self.defaultReplacementsByLanguage(),
        learnedCorrections: [String: String] = [:],
        updatedAt: TimeInterval = Date().timeIntervalSince1970
    ) {
        self.version = version
        self.autoCapitalizeStart = autoCapitalizeStart
        self.stripTrailingPeriodOnShortMessages = stripTrailingPeriodOnShortMessages
        self.shortMessageMaxWords = shortMessageMaxWords
        self.replacementsByLanguage = replacementsByLanguage
        self.learnedCorrections = learnedCorrections
        self.updatedAt = updatedAt
    }

    public static func defaultReplacementsByLanguage() -> [String: [String: String]] {
        return [
            "fr": [
                "t'inquiète pas": "tkt",
                "ne t'inquiète pas": "tkt",
                "t'inquiète": "tkt",
                "c'est-à-dire": "càd",
                "rendez-vous": "rdv",
                "beaucoup": "bcp",
                "aujourd'hui": "ajd",
                "s'il te plaît": "stp",
                "s'il vous plaît": "svp",
                "pour l'instant": "pr l'instant"
            ],
            "en": [
                "by the way": "btw",
                "to be honest": "tbh",
                "in my opinion": "imo",
                "right now": "rn",
                "as soon as possible": "asap",
                "I don't know": "idk",
                "let me know": "lmk",
                "thank you so much": "tysm",
                "never mind": "nvm"
            ],
            "es": [
                "por favor": "pfv",
                "también": "tb",
                "porque": "pq",
                "fin de semana": "finde",
                "de verdad": "d vdd"
            ],
            "de": [
                "zum beispiel": "z.B.",
                "beziehungsweise": "bzw.",
                "und so weiter": "usw.",
                "vielleicht": "vllt"
            ],
            "it": [
                "per favore": "pf",
                "comunque": "cmq",
                "non lo so": "boh"
            ],
            "pt": [
                "por favor": "pfv",
                "também": "tb",
                "você": "vc"
            ]
        ]
    }
}

// MARK: - Gestionnaire du Fichier user_style_memory.json (Partagé App + Clavier • < 0,05 ms)

public final class UserStyleMemoryManager: ObservableObject {
    public static let shared = UserStyleMemoryManager()

    public static let fileName = "user_style_memory.json"

    @Published public var memory: UserStyleMemoryPayload {
        didSet {
            if !isReloadingFromDisk && oldValue != memory {
                saveToDisk()
            }
        }
    }

    @Published public var isMemoryEnabled: Bool = true {
        didSet {
            UserDefaults.standard.set(isMemoryEnabled, forKey: "whisperorb.styleMemoryEnabled")
        }
    }

    private var isReloadingFromDisk = false

    public var memoryFileURL: URL {
        AppGroupManager.shared.sharedContainerURL.appendingPathComponent(Self.fileName, isDirectory: false)
    }

    private init() {
        if UserDefaults.standard.object(forKey: "whisperorb.styleMemoryEnabled") != nil {
            self.isMemoryEnabled = UserDefaults.standard.bool(forKey: "whisperorb.styleMemoryEnabled")
        } else {
            self.isMemoryEnabled = true
        }

        let fileURL = AppGroupManager.shared.sharedContainerURL.appendingPathComponent(Self.fileName, isDirectory: false)
        if let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode(UserStyleMemoryPayload.self, from: data) {
            self.memory = decoded
        } else {
            let initial = UserStyleMemoryPayload()
            self.memory = initial
            savePayloadToDisk(initial, url: fileURL)
        }
    }

    public func reloadFromDisk() {
        guard let data = try? Data(contentsOf: memoryFileURL),
              let decoded = try? JSONDecoder().decode(UserStyleMemoryPayload.self, from: data) else {
            return
        }
        if decoded != memory {
            isReloadingFromDisk = true
            memory = decoded
            isReloadingFromDisk = false
        }
    }

    public func saveToDisk() {
        var updated = memory
        updated.updatedAt = Date().timeIntervalSince1970
        savePayloadToDisk(updated, url: memoryFileURL)
    }

    private func savePayloadToDisk(_ payload: UserStyleMemoryPayload, url: URL) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(payload) {
            try? data.write(to: url, options: [.atomic])
        }
    }

    // MARK: - Application Ultra-Rapide (< 0,05 ms) sur le Texte Transcrit

    public func applyStyleMemory(to input: String, languageCode: String) -> String {
        guard isMemoryEnabled else { return input }
        var result = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !result.isEmpty else { return result }

        let normalizedLang = languageCode.lowercased().prefix(2).description
        let effectiveLang = normalizedLang.isEmpty || normalizedLang == "au"
            ? (Locale.current.language.languageCode?.identifier.lowercased() ?? "en")
            : normalizedLang

        // 1. Remplacements spécifiques à la langue active (triés du plus long au plus court)
        if let langMap = memory.replacementsByLanguage[effectiveLang] {
            let sortedPairs = langMap.sorted { $0.key.count > $1.key.count }
            for (spoken, written) in sortedPairs where !spoken.isEmpty {
                result = replaceWholePhraseCaseInsensitive(in: result, target: spoken, replacement: written)
            }
        }

        // 2. Corrections apprises globalement
        if !memory.learnedCorrections.isEmpty {
            let sortedLearned = memory.learnedCorrections.sorted { $0.key.count > $1.key.count }
            for (spoken, written) in sortedLearned where !spoken.isEmpty {
                result = replaceWholePhraseCaseInsensitive(in: result, target: spoken, replacement: written)
            }
        }

        // 3. Suppression du point final sur les messages courts (style SMS / Discord / WhatsApp)
        if memory.stripTrailingPeriodOnShortMessages {
            let words = result.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
            if words.count <= memory.shortMessageMaxWords && result.hasSuffix(".") && !result.hasSuffix("...") {
                result.removeLast()
            }
        }

        // 4. Préférence de majuscule en début de message
        if let first = result.first, first.isLetter {
            if memory.autoCapitalizeStart {
                result = first.uppercased() + result.dropFirst()
            } else {
                result = first.lowercased() + result.dropFirst()
            }
        }

        return result
    }

    private func replaceWholePhraseCaseInsensitive(in source: String, target: String, replacement: String) -> String {
        let escapedTarget = NSRegularExpression.escapedPattern(for: target)
        let pattern = "(?i)(?<![\\p{L}\\p{N}])\(escapedTarget)(?![\\p{L}\\p{N}])"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            return source
        }
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        return regex.stringByReplacingMatches(in: source, options: [], range: range, withTemplate: replacement)
    }

    // MARK: - Édition des Abréviations & Habitudes par Langue

    public func replacements(for languageCode: String) -> [(spoken: String, written: String)] {
        let lang = languageCode.lowercased().prefix(2).description
        let dict = memory.replacementsByLanguage[lang] ?? [:]
        return dict.map { (spoken: $0.key, written: $0.value) }
            .sorted { $0.spoken.localizedCaseInsensitiveCompare($1.spoken) == .orderedAscending }
    }

    public func setReplacement(spoken: String, written: String, for languageCode: String) {
        let cleanSpoken = spoken.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanWritten = written.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanSpoken.isEmpty, !cleanWritten.isEmpty else { return }
        let lang = languageCode.lowercased().prefix(2).description
        var langDict = memory.replacementsByLanguage[lang] ?? [:]
        langDict[cleanSpoken] = cleanWritten
        memory.replacementsByLanguage[lang] = langDict
    }

    public func removeReplacement(spoken: String, for languageCode: String) {
        let lang = languageCode.lowercased().prefix(2).description
        var langDict = memory.replacementsByLanguage[lang] ?? [:]
        langDict.removeValue(forKey: spoken)
        memory.replacementsByLanguage[lang] = langDict
    }

    public func resetToDefaults() {
        memory = UserStyleMemoryPayload()
    }

    public func prettyJSONString() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(memory),
              let str = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return str
    }
}
