import Foundation
import SwiftUI
import UIKit
import Combine
import CoreFoundation
import Metal

// MARK: - Sélecteur Intelligent de Langue Multilingue (Zéro "fr" codé en dur)

public enum WhisperLanguageSelection: String, CaseIterable, Codable, Identifiable {
    case system = "system"
    case auto = "auto"
    case fr = "fr"
    case en = "en"
    case es = "es"
    case de = "de"
    case it = "it"
    case pt = "pt"
    case nl = "nl"
    case pl = "pl"
    case ja = "ja"
    case ko = "ko"
    case zh = "zh"
    case ar = "ar"
    case ru = "ru"

    public var id: String { rawValue }

    /// Code ISO 639-1 à 2 lettres détecté sur le téléphone (ex: "fr", "en", "es"...)
    public static var detectedSystemISOCode: String {
        let supportedCodes: Set<String> = ["fr", "en", "es", "de", "it", "pt", "nl", "pl", "ja", "ko", "zh", "ar", "ru"]
        if let preferred = Locale.preferredLanguages.first {
            let prefix2 = String(preferred.prefix(2)).lowercased()
            if supportedCodes.contains(prefix2) {
                return prefix2
            }
        }
        let currentCode = (Locale.current.language.languageCode?.identifier ?? "en").lowercased()
        let prefix2 = String(currentCode.prefix(2))
        return supportedCodes.contains(prefix2) ? prefix2 : "en"
    }

    /// Code envoyé à whisper.cpp ("auto" uniquement si l'utilisateur a explicitement choisi l'auto-détection)
    public var resolvedWhisperCode: String {
        switch self {
        case .system:
            return Self.detectedSystemISOCode
        case .auto:
            return "auto"
        default:
            return rawValue
        }
    }

    /// Code de langue effectif pour user_style_memory.json
    public var effectiveStyleLanguageCode: String {
        switch self {
        case .system, .auto:
            return Self.detectedSystemISOCode
        default:
            return rawValue
        }
    }

    public var flagEmoji: String {
        switch self {
        case .system: return "📱"
        case .auto:   return "🌐"
        case .fr:     return "🇫🇷"
        case .en:     return "🇬🇧"
        case .es:     return "🇪🇸"
        case .de:     return "🇩🇪"
        case .it:     return "🇮🇹"
        case .pt:     return "🇵🇹"
        case .nl:     return "🇳🇱"
        case .pl:     return "🇵🇱"
        case .ja:     return "🇯🇵"
        case .ko:     return "🇰🇷"
        case .zh:     return "🇨🇳"
        case .ar:     return "🇸🇦"
        case .ru:     return "🇷🇺"
        }
    }

    public var displayName: String {
        switch self {
        case .system:
            return "Langue du système (\(Self.detectedSystemISOCode.uppercased()) • Recommandé)"
        case .auto:
            return "Auto-détection Whisper (Medium / Turbo / TITAN)"
        case .fr: return "Français (FR)"
        case .en: return "English (EN)"
        case .es: return "Español (ES)"
        case .de: return "Deutsch (DE)"
        case .it: return "Italiano (IT)"
        case .pt: return "Português (PT)"
        case .nl: return "Nederlands (NL)"
        case .pl: return "Polski (PL)"
        case .ja: return "日本語 (JA)"
        case .ko: return "한국어 (KO)"
        case .zh: return "中文 (ZH)"
        case .ar: return "العربية (AR)"
        case .ru: return "Русский (RU)"
        }
    }

    public var shortPillLabel: String {
        switch self {
        case .system:
            return "📱 \(Self.detectedSystemISOCode.uppercased())"
        case .auto:
            return "🌐 AUTO"
        default:
            return "\(flagEmoji) \(rawValue.uppercased())"
        }
    }

    /// Seed de ponctuation ultra-court adapté à la langue pour guider Whisper sans biaiser le texte
    public var initialPunctuationPrompt: String {
        let code = effectiveStyleLanguageCode
        switch code {
        case "fr": return "Bonjour,"
        case "en": return "Hello,"
        case "es": return "Hola,"
        case "de": return "Hallo,"
        case "it": return "Ciao,"
        case "pt": return "Olá,"
        case "nl": return "Hallo,"
        case "pl": return "Cześć,"
        case "ja": return "こんにちは、"
        case "ko": return "안녕하세요,"
        case "zh": return "你好，"
        case "ar": return "مرحباً،"
        case "ru": return "Привет,"
        default:   return "Hello,"
        }
    }
}

// MARK: - Catalogue Complet des 7 Modèles Whisper (De Nano 31 Mo jusqu'à TITAN Large-v3 1,08 Go)

public enum WhisperModelTier: String, CaseIterable, Codable, Identifiable {
    case nano = "nano"
    case eclair = "eclair"
    case normal = "normal"
    case medium = "medium"
    case zeroFaute = "zeroFaute"
    case expertPlus = "expertPlus"
    case titan = "titan"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .nano:
            return "⚡ Whisper Nano"
        case .eclair:
            return "⚡ Whisper Flash"
        case .normal:
            return "⚖️ Whisper PRO"
        case .medium:
            return "🔥 Whisper Medium"
        case .zeroFaute:
            return "🚀 Whisper Expert (v3 Turbo)"
        case .expertPlus:
            return "💎 Whisper Expert+ (Turbo Q8)"
        case .titan:
            return "👑 Whisper TITAN (Large-v3 Full)"
        }
    }

    public var shortBadgeName: String {
        switch self {
        case .nano:
            return "Whisper Nano"
        case .eclair:
            return "Whisper Flash"
        case .normal:
            return "Whisper PRO"
        case .medium:
            return "Whisper Medium"
        case .zeroFaute:
            return "Whisper Expert"
        case .expertPlus:
            return "Expert+ Q8"
        case .titan:
            return "Whisper TITAN"
        }
    }

    public var subtitle: String {
        switch self {
        case .nano:
            return "Ultra-léger (Tiny Q5) • Réponse éclair sur tous les appareils"
        case .eclair:
            return "Instantané (Base Q5) • Messages courts & notes rapides"
        case .normal:
            return "Équilibré (Small Q5) • Dictée quotidienne fluide & précise"
        case .medium:
            return "Haute fidélité (Medium Q5) • Vocabulaire riche & multilingue"
        case .zeroFaute:
            return "Large-v3 Turbo Q5 • Très haute précision & faible latence"
        case .expertPlus:
            return "Large-v3 Turbo Q8 (8-bit) • Fidélité studio quasi sans perte"
        case .titan:
            return "Large-v3 Complet (1,55 Md params) • Le modèle maître absolu"
        }
    }

    public var fileName: String {
        switch self {
        case .nano:
            return "ggml-tiny-q5_1.bin"
        case .eclair:
            return "ggml-base-q5_1.bin"
        case .normal:
            return "ggml-small-q5_1.bin"
        case .medium:
            return "ggml-medium-q5_0.bin"
        case .zeroFaute:
            return "ggml-large-v3-turbo-q5_0.bin"
        case .expertPlus:
            return "ggml-large-v3-turbo-q8_0.bin"
        case .titan:
            return "ggml-large-v3-q5_0.bin"
        }
    }

    public var downloadURL: URL {
        return URL(string: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/\(fileName)")!
    }

    public var sizeMB: Int {
        switch self {
        case .nano:
            return 31
        case .eclair:
            return 60
        case .normal:
            return 190
        case .medium:
            return 539
        case .zeroFaute:
            return 540
        case .expertPlus:
            return 874
        case .titan:
            return 1080
        }
    }

    public var ramUsageMB: Int {
        switch self {
        case .nano:
            return 120
        case .eclair:
            return 190
        case .normal:
            return 380
        case .medium:
            return 900
        case .zeroFaute:
            return 950
        case .expertPlus:
            return 1320
        case .titan:
            return 2150
        }
    }

    /// RAM physique minimale requise sur le téléphone/tablette pour débloquer ce modèle sans crash OOM
    public var minimumRequiredRAMGB: Double {
        switch self {
        case .nano:
            return 2.0
        case .eclair:
            return 2.8
        case .normal:
            return 3.6
        case .medium:
            return 3.6
        case .zeroFaute:
            return 3.6
        case .expertPlus:
            return 7.4
        case .titan:
            return 11.2
        }
    }

    public var requiredRAMBadgeText: String {
        switch self {
        case .nano:
            return "2 Go+ RAM"
        case .eclair:
            return "3 Go+ RAM"
        case .normal, .medium, .zeroFaute:
            return "4 Go+ RAM"
        case .expertPlus:
            return "8 Go+ RAM"
        case .titan:
            return "12 Go+ RAM"
        }
    }

    public var latencyDescription: String {
        switch self {
        case .nano:
            return "~0,11 s"
        case .eclair:
            return "~0,18 s"
        case .normal:
            return "~0,35 s"
        case .medium:
            return "~0,75 s"
        case .zeroFaute:
            return "~0,60 s"
        case .expertPlus:
            return "~0,72 s"
        case .titan:
            return "~1,15 s"
        }
    }
}

// MARK: - Détecteur Matériel Universel & 100 % Future-Proof (Tous les iPhone, iPad & Mac)

public struct HardwareDetector {
    public static var physicalMemoryBytes: UInt64 {
        ProcessInfo.processInfo.physicalMemory
    }

    public static var physicalMemoryGB: Double {
        Double(physicalMemoryBytes) / (1024.0 * 1024.0 * 1024.0)
    }

    public static func effectiveMemoryGB(simulate4GB: Bool) -> Double {
        return simulate4GB ? 4.0 : physicalMemoryGB
    }

    public static func isTierUnlocked(_ tier: WhisperModelTier, simulate4GB: Bool = false, forceUnlock: Bool = false) -> Bool {
        if forceUnlock { return true }
        return effectiveMemoryGB(simulate4GB: simulate4GB) >= tier.minimumRequiredRAMGB
    }

    public static func unlockedTiers(simulate4GB: Bool = false, forceUnlock: Bool = false) -> [WhisperModelTier] {
        return WhisperModelTier.allCases.filter { isTierUnlocked($0, simulate4GB: simulate4GB, forceUnlock: forceUnlock) }
    }

    public static var formattedMemoryGB: String {
        String(format: "%.0f Go RAM", round(physicalMemoryGB))
    }

    public static var isRunningOnMac: Bool {
        if ProcessInfo.processInfo.isiOSAppOnMac {
            return true
        }
        let code = machineIdentifier
        return code.hasPrefix("Mac") || code.hasPrefix("iMac") || code.hasPrefix("MacBook")
    }

    /// Lit directement le nom de la puce gravée dans le silicium via le pilote Metal d'Apple (100 % Future-Proof)
    public static let metalGPUChipName: String = {
        guard let device = MTLCreateSystemDefaultDevice() else {
            return "Apple Metal GPU"
        }
        let raw = device.name
            .replacingOccurrences(of: " GPU", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return raw.isEmpty ? "Apple Metal GPU" : raw
    }()

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

        if ProcessInfo.processInfo.isiOSAppOnMac {
            return "Mac (\(metalGPUChipName))"
        }

        switch code {
        // --- Gamme iPad Pro (A12X / A12Z jusqu'à M4, M5 & futurs) ---
        case "iPad8,1", "iPad8,2", "iPad8,3", "iPad8,4", "iPad8,5", "iPad8,6", "iPad8,7", "iPad8,8":
            return "iPad Pro — A12X Bionic"
        case "iPad8,9", "iPad8,10":
            return "iPad Pro 11\" — A12Z Bionic"
        case "iPad8,11", "iPad8,12":
            return "iPad Pro 12,9\" — A12Z Bionic"
        case "iPad13,4", "iPad13,5", "iPad13,6", "iPad13,7", "iPad13,8", "iPad13,9", "iPad13,10", "iPad13,11":
            return "iPad Pro — Apple M1"
        case "iPad14,3", "iPad14,4", "iPad14,5", "iPad14,6":
            return "iPad Pro — Apple M2"
        case "iPad16,3", "iPad16,4", "iPad16,5", "iPad16,6":
            return "iPad Pro — Apple M4"
        case "iPad17,1", "iPad17,2", "iPad17,3", "iPad17,4":
            return "iPad Pro — Apple M5"

        // --- Gamme iPad Air ---
        case "iPad11,3", "iPad11,4":
            return "iPad Air (3e gén) — A12 Bionic"
        case "iPad13,1", "iPad13,2":
            return "iPad Air (4e gén) — A14 Bionic"
        case "iPad13,16", "iPad13,17":
            return "iPad Air — Apple M1"
        case "iPad14,8", "iPad14,9", "iPad14,10", "iPad14,11":
            return "iPad Air — Apple M2"
        case "iPad15,3", "iPad15,4", "iPad15,5", "iPad15,6":
            return "iPad Air — Apple M3"
        case "iPad16,8", "iPad16,9", "iPad16,10", "iPad16,11":
            return "iPad Air — Apple M4"

        // --- Gamme iPad mini ---
        case "iPad11,1", "iPad11,2":
            return "iPad mini (5e gén) — A12 Bionic"
        case "iPad14,1", "iPad14,2":
            return "iPad mini (6e gén) — A15 Bionic"
        case "iPad16,1", "iPad16,2":
            return "iPad mini (7e gén) — A17 Pro"

        // --- Gamme iPad Standard ---
        case "iPad11,6", "iPad11,7":
            return "iPad (8e gén) — A12 Bionic"
        case "iPad12,1", "iPad12,2":
            return "iPad (9e gén) — A13 Bionic"
        case "iPad13,18", "iPad13,19":
            return "iPad (10e gén) — A14 Bionic"
        case "iPad15,7", "iPad15,8":
            return "iPad (11e gén) — A16 Bionic"

        // --- Gamme iPhone (XS / XR jusqu'à iPhone 18 Pro Max & Future-Proof) ---
        case "iPhone11,2", "iPhone11,4", "iPhone11,6":
            return "iPhone XS — A12 Bionic"
        case "iPhone11,8":
            return "iPhone XR — A12 Bionic"
        case "iPhone12,1":
            return "iPhone 11 — A13 Bionic"
        case "iPhone12,3", "iPhone12,5":
            return "iPhone 11 Pro — A13 Bionic"
        case "iPhone12,8":
            return "iPhone SE (2e gén) — A13 Bionic"
        case "iPhone13,1":
            return "iPhone 12 mini — A14 Bionic"
        case "iPhone13,2":
            return "iPhone 12 — A14 Bionic"
        case "iPhone13,3", "iPhone13,4":
            return "iPhone 12 Pro — A14 Bionic"
        case "iPhone14,4":
            return "iPhone 13 mini — A15 Bionic"
        case "iPhone14,5":
            return "iPhone 13 — A15 Bionic"
        case "iPhone14,2":
            return "iPhone 13 Pro — A15 Bionic"
        case "iPhone14,3":
            return "iPhone 13 Pro Max — A15 Bionic"
        case "iPhone14,6":
            return "iPhone SE (3e gén) — A15 Bionic"
        case "iPhone14,7":
            return "iPhone 14 — A15 Bionic"
        case "iPhone14,8":
            return "iPhone 14 Plus — A15 Bionic"
        case "iPhone15,2":
            return "iPhone 14 Pro — A16 Bionic"
        case "iPhone15,3":
            return "iPhone 14 Pro Max — A16 Bionic"
        case "iPhone15,4":
            return "iPhone 15 — A16 Bionic"
        case "iPhone15,5":
            return "iPhone 15 Plus — A16 Bionic"
        case "iPhone16,1":
            return "iPhone 15 Pro — A17 Pro"
        case "iPhone16,2":
            return "iPhone 15 Pro Max — A17 Pro"
        case "iPhone17,3":
            return "iPhone 16 — A18"
        case "iPhone17,4":
            return "iPhone 16 Plus — A18"
        case "iPhone17,5":
            return "iPhone 16e — A18"
        case "iPhone17,1":
            return "iPhone 16 Pro — A18 Pro"
        case "iPhone17,2":
            return "iPhone 16 Pro Max — A18 Pro"
        case "iPhone18,3":
            return "iPhone 17 — A19"
        case "iPhone18,4":
            return "iPhone 17 Air — A19"
        case "iPhone18,1":
            return "iPhone 17 Pro — A19 Pro"
        case "iPhone18,2":
            return "iPhone 17 Pro Max — A19 Pro"
        case "iPhone18,5":
            return "iPhone 17e — A19"
        case "iPhone19,3":
            return "iPhone 18 — A19 / A20"
        case "iPhone19,4":
            return "iPhone 18 Air — A20"
        case "iPhone19,1":
            return "iPhone 18 Pro — A20 Pro"
        case "iPhone19,2":
            return "iPhone 18 Pro Max — A20 Pro"

        default:
            return futureProofDeviceDescription(for: code)
        }
    }

    private static func futureProofDeviceDescription(for code: String) -> String {
        let chip = metalGPUChipName
        if code.hasPrefix("iPhone") {
            let numbers = code.replacingOccurrences(of: "iPhone", with: "").split(separator: ",")
            if let major = Int(numbers.first ?? ""), major >= 19 {
                let gen = major - 1
                let minor = Int(numbers.dropFirst().first ?? "") ?? 0
                let suffix: String
                switch minor {
                case 1: suffix = " Pro"
                case 2: suffix = " Pro Max"
                case 4: suffix = " Air"
                default: suffix = ""
                }
                return "iPhone \(gen)\(suffix) — \(chip)"
            }
            return "iPhone — \(chip)"
        } else if code.hasPrefix("iPad") {
            return "iPad — \(chip)"
        } else if code.hasPrefix("Mac") || code.hasPrefix("iMac") {
            return "Mac — \(chip)"
        } else if UIDevice.current.userInterfaceIdiom == .pad {
            return "iPad — \(chip)"
        } else {
            return "iPhone — \(chip)"
        }
    }

    public static var isHighRefreshRateProMotion: Bool {
        UIScreen.main.maximumFramesPerSecond >= 120
    }

    public static func recommendedTier(simulate4GB: Bool = false) -> WhisperModelTier {
        let ram = effectiveMemoryGB(simulate4GB: simulate4GB)
        if ram >= 11.2 {
            return .expertPlus
        } else if ram >= 5.4 {
            return .zeroFaute
        } else if ram >= 3.6 {
            return .normal
        } else {
            return .eclair
        }
    }

    public static var recommendedTier: WhisperModelTier {
        return recommendedTier(simulate4GB: false)
    }

    public static var optimalThreadCount: Int {
        let totalCores = ProcessInfo.processInfo.activeProcessorCount
        if isRunningOnMac {
            return max(4, min(8, totalCores - 2))
        }
        if UIDevice.current.userInterfaceIdiom == .phone || totalCores <= 6 {
            return 2
        }
        return min(6, max(4, totalCores / 2))
    }

    public static var telemetrySummary: String {
        return "\(deviceMarketingName) • \(formattedMemoryGB)"
    }
}

// MARK: - Easter Egg des 7 Messages d'Avertissement pour Forcer le Déblocage de Tous les Modèles (FR & EN)

public struct EasterEggUnlockStep: Identifiable, Equatable {
    public let id: Int
    public let title: String
    public let message: String
    public let confirmButtonLabel: String
    public let cancelButtonLabel: String

    public static func steps(isFrench: Bool) -> [EasterEggUnlockStep] {
        if isFrench {
            return [
                EasterEggUnlockStep(
                    id: 1,
                    title: "⚠️ WARNING : ÊTES-VOUS SÛR ? (1/7)",
                    message: "Ce modèle dépasse la capacité RAM recommandée de votre appareil. Risque critique de crash système (Out-Of-Memory). Voulez-vous vraiment continuer ?",
                    confirmButtonLabel: "Continuer quand même (1/7)",
                    cancelButtonLabel: "Annuler"
                ),
                EasterEggUnlockStep(
                    id: 2,
                    title: "🛑 Deuxième confirmation (2/7)",
                    message: "Vraiment sûr ? Le processeur va tourner à 100 % et la mémoire vive risque de saturer violemment si vous lancez TITAN sur une config modeste.",
                    confirmButtonLabel: "Oui, je suis sûr (2/7)",
                    cancelButtonLabel: "Annuler"
                ),
                EasterEggUnlockStep(
                    id: 3,
                    title: "🤨 Sérieusement ? (3/7)",
                    message: "On a codé une sécurité exprès anti-idiots comme toi pour protéger ton téléphone, et toi tu forces le passage ?",
                    confirmButtonLabel: "Forcer le passage (3/7)",
                    cancelButtonLabel: "J'avoue, j'arrête"
                ),
                EasterEggUnlockStep(
                    id: 4,
                    title: "🔥 Mode Grille-Pain (4/7)",
                    message: "Bon écoute... Si ton téléphone se transforme en grille-pain ou plante au milieu d'une phrase, viens pas pleurer dans les Issues GitHub hein.",
                    confirmButtonLabel: "Je prends le risque (4/7)",
                    cancelButtonLabel: "Demi-tour"
                ),
                EasterEggUnlockStep(
                    id: 5,
                    title: "😰 Panique en salle des machines (5/7)",
                    message: "Ta barrette de RAM est littéralement en train de trembler de peur dans un coin. Il est encore temps de faire demi-tour.",
                    confirmButtonLabel: "Même pas peur (5/7)",
                    cancelButtonLabel: "Sauver ma RAM"
                ),
                EasterEggUnlockStep(
                    id: 6,
                    title: "🙄 Têtu jusqu'au bout (6/7)",
                    message: "Ok t'es vraiment têtu en fait... Tu comptes cliquer sur « Continuer » jusqu'au bout quoi qu'on te dise, c'est ça ?",
                    confirmButtonLabel: "Exactement, continue (6/7)",
                    cancelButtonLabel: "Abandonner"
                ),
                EasterEggUnlockStep(
                    id: 7,
                    title: "🤷‍♂️ Bon bah tant pis pour toi... (7/7)",
                    message: "Bon bah tant pis pour toi... Tous les modèles (jusqu'à Whisper TITAN 1,08 Go) sont maintenant débloqués ! Amuse-toi bien.",
                    confirmButtonLabel: "🔓 Débloquer TOUS les modèles !",
                    cancelButtonLabel: "Annuler"
                )
            ]
        } else {
            return [
                EasterEggUnlockStep(
                    id: 1,
                    title: "⚠️ WARNING: ARE YOU SURE? (1/7)",
                    message: "This model exceeds your device's recommended RAM capacity. Critical risk of system crash (Out-Of-Memory). Do you really want to continue?",
                    confirmButtonLabel: "Continue anyway (1/7)",
                    cancelButtonLabel: "Cancel"
                ),
                EasterEggUnlockStep(
                    id: 2,
                    title: "🛑 Second confirmation (2/7)",
                    message: "Are you really sure? Your CPU will hit 100% and RAM might violently saturate if you run TITAN on a modest device.",
                    confirmButtonLabel: "Yes, I'm sure (2/7)",
                    cancelButtonLabel: "Cancel"
                ),
                EasterEggUnlockStep(
                    id: 3,
                    title: "🤨 Seriously? (3/7)",
                    message: "We coded an anti-idiot safety lock specifically for people like you to protect your phone, and you're still forcing your way through?",
                    confirmButtonLabel: "Force my way through (3/7)",
                    cancelButtonLabel: "Fair enough, I'll stop"
                ),
                EasterEggUnlockStep(
                    id: 4,
                    title: "🔥 Toaster Mode (4/7)",
                    message: "Look... If your phone turns into a toaster or crashes mid-sentence, don't come crying in the GitHub Issues, okay?",
                    confirmButtonLabel: "I'll take the risk (4/7)",
                    cancelButtonLabel: "Turn back"
                ),
                EasterEggUnlockStep(
                    id: 5,
                    title: "😰 Engine Room Panic (5/7)",
                    message: "Your RAM chip is literally shaking in fear in a corner right now. There is still time to turn back.",
                    confirmButtonLabel: "No fear (5/7)",
                    cancelButtonLabel: "Save my RAM"
                ),
                EasterEggUnlockStep(
                    id: 6,
                    title: "🙄 Stubborn to the end (6/7)",
                    message: "Okay you're really stubborn... You're just going to keep clicking \"Continue\" no matter what we say, right?",
                    confirmButtonLabel: "Yep, keep going (6/7)",
                    cancelButtonLabel: "Give up"
                ),
                EasterEggUnlockStep(
                    id: 7,
                    title: "🤷‍♂️ Well, too bad for you then... (7/7)",
                    message: "Well, too bad for you then... All models (up to Whisper TITAN 1.08 GB) are now unlocked! Have fun.",
                    confirmButtonLabel: "🔓 Unlock ALL models!",
                    cancelButtonLabel: "Cancel"
                )
            ]
        }
    }
}

// MARK: - Payload JSON de Synchronisation Inter-Processus (IPC)

public struct WhisperSharedStatePayload: Codable, Equatable {
    public var bgHex: String
    public var fgHex: String
    public var selectedModelRaw: String
    public var selectedLanguageRaw: String?
    public var simulateModest4GBDevice: Bool?
    public var forceUnlockAllModels: Bool?
    public var orbStateRaw: String
    public var currentAudioLevel: Float
    public var pendingTranscribedText: String
    public var pendingTextUpdatedAt: TimeInterval?
    public var lastInsertedSentenceLength: Int
    public var shouldDeleteLastSentence: Bool
    public var autoSpaceForGoodnotes: Bool
    public var autoPunctuation: Bool
    public var vadSilenceThresholdSeconds: Double
    public var isBackgroundEngineActive: Bool
    public var isKeyboardCollapsed: Bool?
    public var isSmartAutocorrectEnabled: Bool?
    public var downloadedModelRaws: [String]?
    public var toggleRecordingRequestCount: Int?
    public var updatedAt: TimeInterval
}

// MARK: - Gestionnaire Central AppGroup + Notifications Darwin (Zéro Chauffe / 0 % CPU au Repos)

public final class AppGroupManager: ObservableObject {
    public static let shared = AppGroupManager()

    public static let appGroupIdentifier: String = {
        let fallbackGroup = "group.com.bluewolfy24yt.whisperorb"
        if let altGroups = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String],
           let firstGroup = altGroups.first, !firstGroup.isEmpty {
            if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: firstGroup) != nil {
                return firstGroup
            }
        }
        if let bundleId = Bundle.main.bundleIdentifier {
            let baseBundleId: String
            if bundleId.hasSuffix(".keyboard") {
                baseBundleId = String(bundleId.dropLast(".keyboard".count))
            } else if bundleId.hasSuffix(".WhisperKeyboard") {
                baseBundleId = String(bundleId.dropLast(".WhisperKeyboard".count))
            } else if bundleId.contains(".keyboard.") {
                baseBundleId = bundleId.replacingOccurrences(of: ".keyboard.", with: ".")
            } else if bundleId.contains(".WhisperKeyboard.") {
                baseBundleId = bundleId.replacingOccurrences(of: ".WhisperKeyboard.", with: ".")
            } else {
                baseBundleId = bundleId
            }
            let dynamicCandidate = "group.\(baseBundleId)"
            if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: dynamicCandidate) != nil {
                return dynamicCandidate
            }
            if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: fallbackGroup) != nil {
                return fallbackGroup
            }
            return dynamicCandidate
        }
        return fallbackGroup
    }()

    public static let darwinToggleRecordingNotification = "com.bluewolfy24yt.whisperorb.toggleRecording"
    public static let darwinStateUpdatedNotification = "com.bluewolfy24yt.whisperorb.stateUpdated"
    public static let darwinAudioLevelUpdatedNotification = "com.bluewolfy24yt.whisperorb.audioLevel"
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

    @Published public var selectedLanguage: WhisperLanguageSelection = .system {
        didSet { if !isApplyingRemoteUpdate && oldValue != selectedLanguage { persistAndBroadcastState() } }
    }

    @Published public var simulateModest4GBDevice: Bool = false {
        didSet {
            if !isApplyingRemoteUpdate && oldValue != simulateModest4GBDevice {
                if simulateModest4GBDevice && !HardwareDetector.isTierUnlocked(selectedModel, simulate4GB: true, forceUnlock: forceUnlockAllModels) {
                    selectedModel = .zeroFaute
                }
                persistAndBroadcastState()
            }
        }
    }

    @Published public var forceUnlockAllModels: Bool = false {
        didSet {
            if !isApplyingRemoteUpdate && oldValue != forceUnlockAllModels {
                if !forceUnlockAllModels && !HardwareDetector.isTierUnlocked(selectedModel, simulate4GB: simulateModest4GBDevice, forceUnlock: false) {
                    selectedModel = .zeroFaute
                }
                persistAndBroadcastState()
            }
        }
    }

    @Published public var recentEmojis: [String] = ["😂", "❤️", "🔥", "👍", "😭", "🙏", "✨", "😍", "🤣", "😎", "🥳", "🚀", "👀", "💯", "🫶", "🤔"] {
        didSet {
            userDefaults.set(recentEmojis, forKey: Keys.recentEmojisList)
        }
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

    @Published public var vadSilenceThresholdSeconds: Double = 0.65 {
        didSet { if !isApplyingRemoteUpdate && oldValue != vadSilenceThresholdSeconds { persistAndBroadcastState() } }
    }

    @Published public var isBackgroundEngineActive: Bool = false {
        didSet { if !isApplyingRemoteUpdate && oldValue != isBackgroundEngineActive { persistAndBroadcastState() } }
    }

    @Published public var isKeyboardCollapsed: Bool = false {
        didSet { if !isApplyingRemoteUpdate && oldValue != isKeyboardCollapsed { persistAndBroadcastState() } }
    }

    @Published public var isSmartAutocorrectEnabled: Bool = true {
        didSet { if !isApplyingRemoteUpdate && oldValue != isSmartAutocorrectEnabled { persistAndBroadcastState() } }
    }

    @Published public var downloadedModelRaws: [String] = [] {
        didSet { if !isApplyingRemoteUpdate && oldValue != downloadedModelRaws { persistAndBroadcastState() } }
    }

    public var onToggleRecordingRequested: (() -> Void)?

    // MARK: - Propriétés Internes

    private let userDefaults: UserDefaults
    private var isApplyingRemoteUpdate: Bool = false
    private var pollingTimer: Timer?
    private var lastAppliedTimestamp: TimeInterval = 0
    private var pendingTextUpdatedAt: TimeInterval? = nil
    private var toggleRecordingRequestCount: Int = 0
    private var lastHandledToggleRequestCount: Int = 0
    private var lastToggleTriggerTime: TimeInterval = 0
    private var lastAudioBroadcastTime: TimeInterval = 0

    private var isKeyboardExtensionProcess: Bool {
        Bundle.main.bundlePath.hasSuffix(".appex")
    }

    private enum Keys {
        static let hasLaunchedBefore = "whisperorb.hasLaunchedBefore"
        static let sharedPayloadData = "whisperorb.sharedPayloadData"
        static let liveAudioLevel = "whisperorb.liveAudioLevel"
        static let engineHeartbeatTimestamp = "whisperorb.engineHeartbeatTimestamp"
        static let toggleRecordingRequestCount = "whisperorb.toggleRecordingRequestCount"
        static let sharedFileName = "whisperorb_shared_state.json"
        static let recentEmojisList = "whisperorb.recentEmojisList"
    }

    private init() {
        if let groupDefaults = UserDefaults(suiteName: Self.appGroupIdentifier) {
            self.userDefaults = groupDefaults
        } else {
            self.userDefaults = UserDefaults.standard
        }

        if let savedEmojis = userDefaults.stringArray(forKey: Keys.recentEmojisList), !savedEmojis.isEmpty {
            self.recentEmojis = savedEmojis
        }

        // Initialise le compteur d'enregistrement existant pour ne jamais déclencher un faux toggle à l'ouverture
        let existingToggleCount = max(
            userDefaults.integer(forKey: Keys.toggleRecordingRequestCount),
            readFreshestPayload()?.toggleRecordingRequestCount ?? 0
        )
        self.toggleRecordingRequestCount = existingToggleCount
        self.lastHandledToggleRequestCount = existingToggleCount

        if !userDefaults.bool(forKey: Keys.hasLaunchedBefore) {
            self.selectedModel = HardwareDetector.recommendedTier
            self.selectedLanguage = .system
            userDefaults.set(true, forKey: Keys.hasLaunchedBefore)
            persistAndBroadcastState()
        } else {
            reloadStateFromChannels()
        }

        registerDarwinNotificationObservers()
    }

    deinit {
        pollingTimer?.invalidate()
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterRemoveEveryObserver(center, Unmanaged.passUnretained(self).toOpaque())
    }

    public func recordUsedEmoji(_ emoji: String) {
        var current = recentEmojis.filter { $0 != emoji }
        current.insert(emoji, at: 0)
        if current.count > 32 {
            current = Array(current.prefix(32))
        }
        recentEmojis = current
    }

    // MARK: - Heartbeat du Moteur Audio en Arrière-Plan

    public func recordEngineHeartbeat() {
        userDefaults.set(Date().timeIntervalSince1970, forKey: Keys.engineHeartbeatTimestamp)
    }

    public func clearEngineHeartbeat() {
        userDefaults.set(0.0, forKey: Keys.engineHeartbeatTimestamp)
    }

    public var isEngineHeartbeatFresh: Bool {
        let lastBeat = userDefaults.double(forKey: Keys.engineHeartbeatTimestamp)
        guard lastBeat > 0 else { return false }
        return (Date().timeIntervalSince1970 - lastBeat) < 6.0
    }

    // MARK: - Répertoires Partagés

    public var sharedContainerURL: URL {
        if let groupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier) {
            return groupURL
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    public var modelsDirectoryURL: URL {
        let docsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let docsModelsDir = docsURL.appendingPathComponent("Models", isDirectory: true)
        if FileManager.default.fileExists(atPath: docsModelsDir.path) {
            return docsModelsDir
        }
        let dir = docsModelsDir
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        var mutableDir = dir
        try? mutableDir.setResourceValues(resourceValues)
        return dir
    }

    private var sharedStateFileURL: URL {
        sharedContainerURL.appendingPathComponent(Keys.sharedFileName, isDirectory: false)
    }

    // MARK: - Sauvegarde et Diffusion Événementielle

    private func readFreshestPayload() -> WhisperSharedStatePayload? {
        let decoder = JSONDecoder()
        var freshest: WhisperSharedStatePayload? = nil

        if let defaultsData = userDefaults.data(forKey: Keys.sharedPayloadData),
           let payload = try? decoder.decode(WhisperSharedStatePayload.self, from: defaultsData) {
            freshest = payload
        }

        if let fileData = try? Data(contentsOf: sharedStateFileURL),
           let filePayload = try? decoder.decode(WhisperSharedStatePayload.self, from: fileData) {
            if let current = freshest {
                if filePayload.updatedAt >= current.updatedAt {
                    freshest = filePayload
                }
            } else {
                freshest = filePayload
            }
        }

        return freshest
    }

    private func writePayload(_ payload: WhisperSharedStatePayload) {
        guard let encoded = try? JSONEncoder().encode(payload) else { return }
        userDefaults.set(encoded, forKey: Keys.sharedPayloadData)
        try? encoded.write(to: sharedStateFileURL, options: [.atomic])
    }

    public func persistAndBroadcastState(postDarwinNotification: Bool = true, overwritePendingTextState: Bool = false) {
        let now = Date().timeIntervalSince1970
        lastAppliedTimestamp = now

        let existing = readFreshestPayload()
        let latestToggleCount = max(
            toggleRecordingRequestCount,
            userDefaults.integer(forKey: Keys.toggleRecordingRequestCount),
            existing?.toggleRecordingRequestCount ?? 0
        )
        toggleRecordingRequestCount = latestToggleCount

        // Le clavier ne doit jamais écraser l'état temps réel du moteur audio de l'app principale
        let authoritativeOrbStateRaw = isKeyboardExtensionProcess
            ? (existing?.orbStateRaw ?? orbState.rawValue)
            : orbState.rawValue
        let authoritativeAudioLevel = isKeyboardExtensionProcess
            ? (existing?.currentAudioLevel ?? currentAudioLevel)
            : currentAudioLevel
        let authoritativeBgEngineActive = isKeyboardExtensionProcess
            ? (existing?.isBackgroundEngineActive ?? isBackgroundEngineActive)
            : isBackgroundEngineActive
        let authoritativeDownloadedModels = isKeyboardExtensionProcess
            ? (existing?.downloadedModelRaws ?? downloadedModelRaws)
            : downloadedModelRaws

        // Empêche l'application principale de ressusciter un pendingTranscribedText déjà consommé par le clavier
        let authoritativePendingText: String
        let authoritativePendingTextTime: TimeInterval?
        let authoritativeLastSentenceLen: Int
        let authoritativeShouldDelete: Bool

        if overwritePendingTextState {
            authoritativePendingText = pendingTranscribedText
            authoritativePendingTextTime = pendingTextUpdatedAt
            authoritativeLastSentenceLen = lastInsertedSentenceLength
            authoritativeShouldDelete = shouldDeleteLastSentence
        } else {
            authoritativePendingText = existing?.pendingTranscribedText ?? pendingTranscribedText
            authoritativePendingTextTime = existing?.pendingTextUpdatedAt ?? pendingTextUpdatedAt
            authoritativeLastSentenceLen = existing?.lastInsertedSentenceLength ?? lastInsertedSentenceLength
            authoritativeShouldDelete = existing?.shouldDeleteLastSentence ?? shouldDeleteLastSentence

            if Thread.isMainThread {
                let prevApplying = isApplyingRemoteUpdate
                isApplyingRemoteUpdate = true
                if pendingTranscribedText != authoritativePendingText {
                    pendingTranscribedText = authoritativePendingText
                }
                pendingTextUpdatedAt = authoritativePendingTextTime
                if lastInsertedSentenceLength != authoritativeLastSentenceLen {
                    lastInsertedSentenceLength = authoritativeLastSentenceLen
                }
                if shouldDeleteLastSentence != authoritativeShouldDelete {
                    shouldDeleteLastSentence = authoritativeShouldDelete
                }
                isApplyingRemoteUpdate = prevApplying
            }
        }

        let payload = WhisperSharedStatePayload(
            bgHex: bgHex,
            fgHex: fgHex,
            selectedModelRaw: selectedModel.rawValue,
            selectedLanguageRaw: selectedLanguage.rawValue,
            simulateModest4GBDevice: simulateModest4GBDevice,
            forceUnlockAllModels: forceUnlockAllModels,
            orbStateRaw: authoritativeOrbStateRaw,
            currentAudioLevel: authoritativeAudioLevel,
            pendingTranscribedText: authoritativePendingText,
            pendingTextUpdatedAt: authoritativePendingTextTime,
            lastInsertedSentenceLength: authoritativeLastSentenceLen,
            shouldDeleteLastSentence: authoritativeShouldDelete,
            autoSpaceForGoodnotes: autoSpaceForGoodnotes,
            autoPunctuation: autoPunctuation,
            vadSilenceThresholdSeconds: vadSilenceThresholdSeconds,
            isBackgroundEngineActive: authoritativeBgEngineActive,
            isKeyboardCollapsed: isKeyboardCollapsed,
            isSmartAutocorrectEnabled: isSmartAutocorrectEnabled,
            downloadedModelRaws: authoritativeDownloadedModels,
            toggleRecordingRequestCount: latestToggleCount,
            updatedAt: now
        )

        writePayload(payload)

        if postDarwinNotification {
            postDarwinNotificationNamed(Self.darwinStateUpdatedNotification)
        }
    }

    public func updateLiveAudioLevel(_ level: Float) {
        let clamped = max(0.0, min(1.0, level))
        let now = Date().timeIntervalSince1970

        if clamped > 0.005 && (now - lastAudioBroadcastTime < 0.12) && abs(clamped - currentAudioLevel) < 0.04 {
            return
        }
        lastAudioBroadcastTime = now

        if DispatchQueue.isMain {
            self.currentAudioLevel = clamped
        } else {
            DispatchQueue.main.async {
                self.currentAudioLevel = clamped
            }
        }

        userDefaults.set(clamped, forKey: Keys.liveAudioLevel)
        postDarwinNotificationNamed(Self.darwinAudioLevelUpdatedNotification)
    }

    private func reloadLiveAudioLevelOnly() {
        guard isKeyboardExtensionProcess else { return }
        let level = userDefaults.float(forKey: Keys.liveAudioLevel)
        if abs(self.currentAudioLevel - level) > 0.01 {
            if Thread.isMainThread {
                self.currentAudioLevel = level
            } else {
                DispatchQueue.main.async {
                    self.currentAudioLevel = level
                }
            }
        }
    }

    public func reloadStateFromChannels() {
        UserStyleMemoryManager.shared.reloadFromDisk()

        guard let validPayload = readFreshestPayload() else {
            return
        }

        let remoteToggleCount = max(
            validPayload.toggleRecordingRequestCount ?? 0,
            userDefaults.integer(forKey: Keys.toggleRecordingRequestCount)
        )
        var shouldFireToggleCallback = false
        if remoteToggleCount > self.toggleRecordingRequestCount {
            self.toggleRecordingRequestCount = remoteToggleCount
        }
        if remoteToggleCount > self.lastHandledToggleRequestCount {
            self.lastHandledToggleRequestCount = remoteToggleCount
            if !isKeyboardExtensionProcess {
                shouldFireToggleCallback = true
            }
        }

        lastAppliedTimestamp = max(lastAppliedTimestamp, validPayload.updatedAt)

        let applyBlock = { [weak self] in
            guard let self = self else { return }
            self.isApplyingRemoteUpdate = true
            defer {
                self.isApplyingRemoteUpdate = false
                if shouldFireToggleCallback {
                    self.dispatchDebouncedToggleRecording()
                }
            }

            if self.bgHex != validPayload.bgHex { self.bgHex = validPayload.bgHex }
            if self.fgHex != validPayload.fgHex { self.fgHex = validPayload.fgHex }
            if let model = WhisperModelTier(rawValue: validPayload.selectedModelRaw), self.selectedModel != model {
                self.selectedModel = model
            }
            if let langRaw = validPayload.selectedLanguageRaw,
               let lang = WhisperLanguageSelection(rawValue: langRaw),
               self.selectedLanguage != lang {
                self.selectedLanguage = lang
            }
            if let sim4GB = validPayload.simulateModest4GBDevice, self.simulateModest4GBDevice != sim4GB {
                self.simulateModest4GBDevice = sim4GB
            }
            if let forceUnlock = validPayload.forceUnlockAllModels, self.forceUnlockAllModels != forceUnlock {
                self.forceUnlockAllModels = forceUnlock
            }
            if let remoteDownloaded = validPayload.downloadedModelRaws, self.downloadedModelRaws != remoteDownloaded {
                self.downloadedModelRaws = remoteDownloaded
            }
            if self.isKeyboardExtensionProcess {
                let engineAlive = validPayload.isBackgroundEngineActive && self.isEngineHeartbeatFresh
                if self.isBackgroundEngineActive != engineAlive {
                    self.isBackgroundEngineActive = engineAlive
                }
                if engineAlive {
                    if let state = WhisperOrbState(rawValue: validPayload.orbStateRaw), self.orbState != state {
                        self.orbState = state
                    }
                    if abs(self.currentAudioLevel - validPayload.currentAudioLevel) > 0.005 {
                        self.currentAudioLevel = validPayload.currentAudioLevel
                    }
                } else {
                    if self.orbState != .idle {
                        self.orbState = .idle
                    }
                    if self.currentAudioLevel != 0.0 {
                        self.currentAudioLevel = 0.0
                    }
                }
            }
            self.pendingTextUpdatedAt = validPayload.pendingTextUpdatedAt
            if self.pendingTranscribedText != validPayload.pendingTranscribedText {
                self.pendingTranscribedText = validPayload.pendingTranscribedText
            }
            if self.lastInsertedSentenceLength != validPayload.lastInsertedSentenceLength {
                self.lastInsertedSentenceLength = validPayload.lastInsertedSentenceLength
            }
            if self.shouldDeleteLastSentence != validPayload.shouldDeleteLastSentence {
                self.shouldDeleteLastSentence = validPayload.shouldDeleteLastSentence
            }
            if self.autoSpaceForGoodnotes != validPayload.autoSpaceForGoodnotes {
                self.autoSpaceForGoodnotes = validPayload.autoSpaceForGoodnotes
            }
            if self.autoPunctuation != validPayload.autoPunctuation {
                self.autoPunctuation = validPayload.autoPunctuation
            }
            if abs(self.vadSilenceThresholdSeconds - validPayload.vadSilenceThresholdSeconds) > 0.01 {
                self.vadSilenceThresholdSeconds = validPayload.vadSilenceThresholdSeconds
            }
            if let remoteCollapsed = validPayload.isKeyboardCollapsed, self.isKeyboardCollapsed != remoteCollapsed {
                self.isKeyboardCollapsed = remoteCollapsed
            }
            if let remoteAutocorrect = validPayload.isSmartAutocorrectEnabled, self.isSmartAutocorrectEnabled != remoteAutocorrect {
                self.isSmartAutocorrectEnabled = remoteAutocorrect
            }
        }

        if Thread.isMainThread {
            applyBlock()
        } else {
            DispatchQueue.main.async(execute: applyBlock)
        }
    }

    private func dispatchDebouncedToggleRecording() {
        guard !isKeyboardExtensionProcess else { return }
        let now = Date().timeIntervalSince1970
        guard now - lastToggleTriggerTime > 0.45 else { return }
        lastToggleTriggerTime = now
        onToggleRecordingRequested?()
    }

    public func requestToggleRecordingFromKeyboard() {
        let baseCount = max(
            toggleRecordingRequestCount,
            userDefaults.integer(forKey: Keys.toggleRecordingRequestCount),
            readFreshestPayload()?.toggleRecordingRequestCount ?? 0
        )
        let nextCount = baseCount + 1
        toggleRecordingRequestCount = nextCount
        lastHandledToggleRequestCount = nextCount
        userDefaults.set(nextCount, forKey: Keys.toggleRecordingRequestCount)

        if var payload = readFreshestPayload() {
            payload.toggleRecordingRequestCount = nextCount
            payload.updatedAt = Date().timeIntervalSince1970
            writePayload(payload)
        } else {
            persistAndBroadcastState(postDarwinNotification: false)
        }

        postDarwinNotificationNamed(Self.darwinToggleRecordingNotification)
    }

    public func publishTranscribedText(_ rawText: String) {
        let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let formattedText: String
        if autoSpaceForGoodnotes && !trimmed.hasSuffix("\n") {
            formattedText = trimmed + " "
        } else {
            formattedText = trimmed
        }

        if HardwareDetector.isRunningOnMac {
            UIPasteboard.general.string = formattedText
        }

        let updateBlock = { [weak self] in
            guard let self = self else { return }
            self.lastInsertedSentenceLength = formattedText.count
            self.pendingTranscribedText = formattedText
            self.pendingTextUpdatedAt = Date().timeIntervalSince1970
            self.orbState = .idle
            self.currentAudioLevel = 0.0
            self.userDefaults.set(Float(0.0), forKey: Keys.liveAudioLevel)
            self.persistAndBroadcastState(postDarwinNotification: true, overwritePendingTextState: true)
        }

        if Thread.isMainThread {
            updateBlock()
        } else {
            DispatchQueue.main.async(execute: updateBlock)
        }
    }

    public func consumePendingTranscribedText() -> String? {
        guard !pendingTranscribedText.isEmpty else { return nil }
        let now = Date().timeIntervalSince1970
        let textTimestamp = pendingTextUpdatedAt ?? readFreshestPayload()?.pendingTextUpdatedAt
        let isFresh = textTimestamp == nil || (now - (textTimestamp ?? now)) <= 15.0

        let textToInsert = isFresh ? pendingTranscribedText : nil
        isApplyingRemoteUpdate = true
        pendingTranscribedText = ""
        pendingTextUpdatedAt = nil
        isApplyingRemoteUpdate = false

        if var payload = readFreshestPayload() {
            payload.pendingTranscribedText = ""
            payload.pendingTextUpdatedAt = nil
            payload.updatedAt = now
            writePayload(payload)
            postDarwinNotificationNamed(Self.darwinStateUpdatedNotification)
        } else {
            persistAndBroadcastState(postDarwinNotification: true, overwritePendingTextState: true)
        }
        return textToInsert
    }

    public func triggerDeleteLastSentence() {
        let updateBlock = { [weak self] in
            guard let self = self else { return }
            guard self.lastInsertedSentenceLength > 0 else { return }
            self.shouldDeleteLastSentence = true
            self.orbState = .idle
            self.persistAndBroadcastState(postDarwinNotification: true, overwritePendingTextState: true)
        }

        if Thread.isMainThread {
            updateBlock()
        } else {
            DispatchQueue.main.async(execute: updateBlock)
        }
    }

    public func consumeDeleteLastSentenceLength() -> Int? {
        guard shouldDeleteLastSentence, lastInsertedSentenceLength > 0 else { return nil }
        let length = lastInsertedSentenceLength
        isApplyingRemoteUpdate = true
        shouldDeleteLastSentence = false
        lastInsertedSentenceLength = 0
        isApplyingRemoteUpdate = false

        if var payload = readFreshestPayload() {
            payload.shouldDeleteLastSentence = false
            payload.lastInsertedSentenceLength = 0
            payload.updatedAt = Date().timeIntervalSince1970
            writePayload(payload)
            postDarwinNotificationNamed(Self.darwinStateUpdatedNotification)
        } else {
            persistAndBroadcastState(postDarwinNotification: true, overwritePendingTextState: true)
        }
        return length
    }

    public func clearLastInsertedSentenceLength() {
        guard lastInsertedSentenceLength != 0 || shouldDeleteLastSentence else { return }
        isApplyingRemoteUpdate = true
        lastInsertedSentenceLength = 0
        shouldDeleteLastSentence = false
        isApplyingRemoteUpdate = false

        if var payload = readFreshestPayload() {
            payload.lastInsertedSentenceLength = 0
            payload.shouldDeleteLastSentence = false
            payload.updatedAt = Date().timeIntervalSince1970
            writePayload(payload)
            postDarwinNotificationNamed(Self.darwinStateUpdatedNotification)
        }
    }

    // MARK: - Notifications Système Darwin

    private func registerDarwinNotificationObservers() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observerPtr = Unmanaged.passUnretained(self).toOpaque()

        CFNotificationCenterAddObserver(
            center,
            observerPtr,
            { _, observer, _, _, _ in
                guard let observer = observer else { return }
                let manager = Unmanaged<AppGroupManager>.fromOpaque(observer).takeUnretainedValue()
                DispatchQueue.main.async {
                    guard !manager.isKeyboardExtensionProcess else { return }
                    let remoteCount = max(
                        manager.userDefaults.integer(forKey: Keys.toggleRecordingRequestCount),
                        manager.readFreshestPayload()?.toggleRecordingRequestCount ?? 0
                    )
                    let isNewRequest = remoteCount > manager.lastHandledToggleRequestCount || remoteCount == 0
                    if remoteCount > manager.toggleRecordingRequestCount {
                        manager.toggleRecordingRequestCount = remoteCount
                    }
                    manager.lastHandledToggleRequestCount = manager.toggleRecordingRequestCount
                    manager.reloadStateFromChannels()
                    if isNewRequest {
                        manager.dispatchDebouncedToggleRecording()
                    }
                }
            },
            Self.darwinToggleRecordingNotification as CFString,
            nil,
            .deliverImmediately
        )

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

        CFNotificationCenterAddObserver(
            center,
            observerPtr,
            { _, observer, _, _, _ in
                guard let observer = observer else { return }
                let manager = Unmanaged<AppGroupManager>.fromOpaque(observer).takeUnretainedValue()
                manager.reloadLiveAudioLevelOnly()
            },
            Self.darwinAudioLevelUpdatedNotification as CFString,
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

    public func startFastPolling() {
        pollingTimer?.invalidate()
        let timer = Timer(timeInterval: 0.45, repeats: true) { [weak self] _ in
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
