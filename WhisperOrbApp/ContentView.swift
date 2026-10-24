import SwiftUI
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

public struct ContentView: View {
    @EnvironmentObject private var appGroup: AppGroupManager
    @EnvironmentObject private var modelDownloader: ModelDownloader
    @EnvironmentObject private var whisperEngine: WhisperEngine
    @EnvironmentObject private var networkMonitor: NetworkMonitor
    @ObservedObject private var styleMemory = UserStyleMemoryManager.shared

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var colorSearchText: String = ""
    @State private var showCopiedToast: Bool = false
    @State private var showRepoCopiedToast: Bool = false
    @State private var showJSONCopiedToast: Bool = false
    @State private var newSpokenPhrase: String = ""
    @State private var newWrittenReplacement: String = ""
    @State private var showRawJSONPreview: Bool = false
    @State private var easterEggStepIndex: Int? = nil

    private let githubRepoURLString = "https://github.com/bluewolfy24yt/WhisperOrb"
    private let githubReleasesURLString = "https://github.com/bluewolfy24yt/WhisperOrb/releases/latest"

    public init() {}

    private var customBgColorBinding: Binding<Color> {
        Binding<Color>(
            get: { Color(hex: appGroup.bgHex) },
            set: { newColor in
                appGroup.bgHex = newColor.toHex()
            }
        )
    }

    private var customFgColorBinding: Binding<Color> {
        Binding<Color>(
            get: { Color(hex: appGroup.fgHex) },
            set: { newColor in
                appGroup.fgHex = newColor.toHex()
            }
        )
    }

    public var body: some View {
        ZStack {
            Color(hex: "#09090B")
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 18) {
                    soberAppHeaderSection

                    if horizontalSizeClass == .regular {
                        HStack(alignment: .top, spacing: 18) {
                            VStack(spacing: 18) {
                                card1VoiceStudioSection
                                cardStyleMemorySection
                                card4PreferencesAndVoiceCommands
                                card5GitHubAndSetupSection
                            }
                            .frame(maxWidth: .infinity)

                            VStack(spacing: 18) {
                                card2ModelsSection
                                card3AppearanceStudioSection
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        VStack(spacing: 18) {
                            card1VoiceStudioSection
                            card2ModelsSection
                            cardStyleMemorySection
                            card3AppearanceStudioSection
                            card4PreferencesAndVoiceCommands
                            card5GitHubAndSetupSection
                        }
                    }
                }
                .padding(.horizontal, horizontalSizeClass == .regular ? 24 : 16)
                .padding(.vertical, 16)
            }

            if let stepIdx = easterEggStepIndex {
                easterEggWarningModalOverlay(stepIndex: stepIdx)
            }
        }
    }

    // MARK: - Easter Egg des 7 Messages d'Avertissement (Déblocage Forcé des Modèles Lourds)

    private func easterEggWarningModalOverlay(stepIndex: Int) -> some View {
        let isFrench = (appGroup.selectedLanguage.effectiveStyleLanguageCode == "fr")
        let allSteps = EasterEggUnlockStep.steps(isFrench: isFrench)
        let safeIdx = max(0, min(allSteps.count - 1, stepIndex))
        let step = allSteps[safeIdx]

        return ZStack {
            Color.black.opacity(0.75)
                .ignoresSafeArea()
                .onTapGesture {
                    easterEggStepIndex = nil
                }

            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(step.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(safeIdx == 6 ? Color(hex: "#30D158") : Color(hex: "#FF9F0A"))
                    Spacer()
                    Text("\(safeIdx + 1)/7")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.white.opacity(0.10)))
                        .foregroundColor(.white)
                }

                Text(step.message)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.90))
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 10) {
                    Button(action: {
                        easterEggStepIndex = nil
                    }) {
                        Text(step.cancelButtonLabel)
                            .font(.system(size: 13, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.10)))
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        let generator = UIImpactFeedbackGenerator(style: .medium)
                        generator.impactOccurred()
                        if safeIdx + 1 < allSteps.count {
                            easterEggStepIndex = safeIdx + 1
                        } else {
                            appGroup.forceUnlockAllModels = true
                            easterEggStepIndex = nil
                        }
                    }) {
                        Text(step.confirmButtonLabel)
                            .font(.system(size: 13, weight: .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(safeIdx == 6 ? Color(hex: "#30D158") : Color(hex: "#FF453A"))
                            )
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
            .frame(maxWidth: 420)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color(hex: "#16161C"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(Color(hex: "#FF9F0A").opacity(0.45), lineWidth: 1.5)
                    )
                    .shadow(color: Color.black.opacity(0.65), radius: 24, x: 0, y: 12)
            )
            .padding(.horizontal, 24)
        }
    }

    // MARK: - En-tête Sobre & Grand Public (v2.0.0)

    private var soberAppHeaderSection: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("WhisperOrb")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    Text("v2.2.1 • ON-DEVICE AI")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.white.opacity(0.08)))
                        .foregroundColor(Color.white.opacity(0.75))
                }

                Text(HardwareDetector.telemetrySummary)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.55))
            }

            Spacer()

            HStack(spacing: 6) {
                Circle()
                    .fill(whisperEngine.isBackgroundEngineRunning ? Color(hex: "#30D158") : Color(hex: "#FF9F0A"))
                    .frame(width: 7, height: 7)
                Text(whisperEngine.isBackgroundEngineRunning ? "Actif" : "En veille")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.85))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.white.opacity(0.06)))
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
    }

    // MARK: - Section 1 : Studio Vocal, Langue & Écoute en Arrière-Plan

    private var card1VoiceStudioSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "waveform",
                title: "Studio Vocal",
                subtitle: "Touchez l'orbe pour dicter ou activez le moteur en arrière-plan pour le clavier"
            )

            VStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Color(hex: "#0D0D11"))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.06), lineWidth: 1)
                        )
                        .frame(height: 190)

                    VStack(spacing: 12) {
                        GeminiBubbleView(
                            bgHex: appGroup.bgHex,
                            fgHex: appGroup.fgHex,
                            audioLevel: CGFloat(appGroup.currentAudioLevel),
                            state: appGroup.orbState,
                            size: 118,
                            showSideTab: false,
                            onTap: {
                                let generator = UIImpactFeedbackGenerator(style: .medium)
                                generator.impactOccurred()
                                whisperEngine.toggleRecording()
                            }
                        )

                        HStack(spacing: 6) {
                            Circle()
                                .fill(statusColorForOrbState(appGroup.orbState))
                                .frame(width: 7, height: 7)
                            Text("\(appGroup.orbState.statusTitle) • \(appGroup.selectedLanguage.shortPillLabel)")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color.white.opacity(0.75))
                        }
                    }
                }

                // Sélecteur intelligent de langue multilingue
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Label("Langue de dictée Whisper", systemImage: "globe")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color.white.opacity(0.80))
                        Spacer()
                        Text("Code ISO : \(appGroup.selectedLanguage.resolvedWhisperCode.uppercased())")
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundColor(Color(hex: "#0A84FF"))
                    }

                    Picker("Langue de dictée", selection: $appGroup.selectedLanguage) {
                        ForEach(WhisperLanguageSelection.allCases) { lang in
                            Text("\(lang.flagEmoji) \(lang.displayName)").tag(lang)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Color(hex: "#0D0D11"))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                            )
                    )
                }

                HStack {
                    Toggle(isOn: $appGroup.isKeyboardCollapsed) {
                        Text("Mode clavier réduit (Barre vocale seule)")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(Color.white.opacity(0.85))
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0A84FF")))
                }

                HStack {
                    Toggle(isOn: $appGroup.isSmartAutocorrectEnabled) {
                        Text("Correcteur automatique IA & 3 prédictions au clavier")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(Color.white.opacity(0.85))
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#30D158")))
                }
            }

            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                if whisperEngine.isBackgroundEngineRunning {
                    whisperEngine.stopBackgroundAudioEngine()
                } else {
                    whisperEngine.startBackgroundAudioEngine()
                }
            }) {
                HStack(spacing: 12) {
                    Image(systemName: whisperEngine.isBackgroundEngineRunning ? "mic.fill" : "mic.slash")
                        .font(.system(size: 16, weight: .semibold))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(
                            whisperEngine.isBackgroundEngineRunning
                            ? "Moteur vocal actif en arrière-plan"
                            : "Activer le moteur vocal en arrière-plan"
                        )
                        .font(.system(size: 14, weight: .semibold))
                        Text(whisperEngine.statusBannerMessage)
                            .font(.system(size: 11, weight: .regular))
                            .opacity(0.80)
                    }
                    Spacer()
                }
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(
                            whisperEngine.isBackgroundEngineRunning
                            ? Color(hex: "#1C1C1E")
                            : Color(hex: "#0A84FF")
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(
                                    whisperEngine.isBackgroundEngineRunning ? Color(hex: "#30D158").opacity(0.45) : Color.clear,
                                    lineWidth: 1
                                )
                        )
                )
            }
            .buttonStyle(.plain)

            if let errorMsg = whisperEngine.lastErrorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.circle")
                        .foregroundColor(Color(hex: "#FF9F0A"))
                    Text(errorMsg)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(Color(hex: "#FF9F0A"))
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.04)))
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Aperçu de dictée")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.65))

                    Spacer()

                    if whisperEngine.lastTranscriptionLatencyMs > 0 {
                        Text("\(whisperEngine.lastTranscriptionLatencyMs) ms • GPU Metal")
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundColor(Color(hex: "#30D158"))
                    }
                }

                TextEditor(text: $whisperEngine.liveTestTranscript)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundColor(.white)
                    .scrollContentBackground(.hidden)
                    .padding(10)
                    .frame(minHeight: 96, maxHeight: 140)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color(hex: "#0D0D11"))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                            )
                    )

                HStack(spacing: 8) {
                    Button(action: {
                        UIPasteboard.general.string = whisperEngine.liveTestTranscript
                        showCopiedToast = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            showCopiedToast = false
                        }
                    }) {
                        Label(showCopiedToast ? "Copié" : "Copier", systemImage: showCopiedToast ? "checkmark" : "doc.on.doc")
                            .font(.system(size: 12, weight: .medium))
                            .padding(.horizontal, 11)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        if whisperEngine.liveTestTranscript.count >= appGroup.lastInsertedSentenceLength && appGroup.lastInsertedSentenceLength > 0 {
                            whisperEngine.liveTestTranscript.removeLast(appGroup.lastInsertedSentenceLength)
                            appGroup.clearLastInsertedSentenceLength()
                        } else {
                            whisperEngine.liveTestTranscript = ""
                        }
                    }) {
                        Label("Annuler phrase", systemImage: "arrow.uturn.backward")
                            .font(.system(size: 12, weight: .medium))
                            .padding(.horizontal, 11)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button(action: {
                        whisperEngine.liveTestTranscript = ""
                    }) {
                        Text("Effacer")
                            .font(.system(size: 12, weight: .medium))
                            .padding(.horizontal, 11)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                            .foregroundColor(Color.white.opacity(0.75))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    // MARK: - Section 2 : Les 7 Modèles Whisper Hors-Ligne + Sécurité Matérielle RAM

    private var card2ModelsSection: some View {
        let unlockedCount = HardwareDetector.unlockedTiers(
            simulate4GB: appGroup.simulateModest4GBDevice,
            forceUnlock: appGroup.forceUnlockAllModels
        ).count

        return VStack(alignment: .leading, spacing: 14) {
            cardSectionHeader(
                icon: "cpu",
                title: "Catalogue des 7 Modèles Whisper",
                subtitle: "GPU Metal + FlashAttention • \(unlockedCount)/7 modèles débloqués sur votre appareil"
            )

            // Switch de test matériel (Simuler un téléphone 4 Go RAM : débloque jusqu'à Whisper Expert v3 Turbo)
            Toggle(isOn: $appGroup.simulateModest4GBDevice) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("🧪 Simuler un téléphone modeste (4 Go RAM)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                    Text("Débloque jusqu'à Whisper Expert (v3 Turbo) et verrouille Expert+ & TITAN")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.55))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#FF9F0A")))
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#0D0D11")))

            // Bouton / Switch Easter Egg : Débloquer de force TOUS les modèles quoi qu'il arrive
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(appGroup.forceUnlockAllModels ? "🔓 Tous les modèles sont débloqués de force !" : "🔒 Débloquer tous les modèles quoi qu'il arrive")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(appGroup.forceUnlockAllModels ? Color(hex: "#30D158") : .white)
                    Text(appGroup.forceUnlockAllModels ? "Sécurité matérielle RAM désactivée par l'utilisateur" : "Outrepasser la sécurité RAM (à vos risques et périls)")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.55))
                }
                Spacer()
                Button(action: {
                    if appGroup.forceUnlockAllModels {
                        appGroup.forceUnlockAllModels = false
                    } else {
                        easterEggStepIndex = 0
                    }
                }) {
                    Text(appGroup.forceUnlockAllModels ? "Reverrouiller" : "Débloquer")
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(appGroup.forceUnlockAllModels ? Color.white.opacity(0.10) : Color(hex: "#FF9F0A").opacity(0.22))
                        )
                        .foregroundColor(appGroup.forceUnlockAllModels ? .white : Color(hex: "#FF9F0A"))
                }
                .buttonStyle(.plain)
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#0D0D11")))

            if let dlError = modelDownloader.errorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "wifi.exclamationmark")
                        .foregroundColor(Color(hex: "#FF453A"))
                    Text(dlError)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(Color(hex: "#FF453A"))
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.04)))
            }

            VStack(spacing: 10) {
                ForEach(WhisperModelTier.allCases) { tier in
                    modelTierCardView(for: tier)
                }
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    private func modelTierCardView(for tier: WhisperModelTier) -> some View {
        let isUnlocked = HardwareDetector.isTierUnlocked(
            tier,
            simulate4GB: appGroup.simulateModest4GBDevice,
            forceUnlock: appGroup.forceUnlockAllModels
        )
        let isSelected = (appGroup.selectedModel == tier)
        let isDownloaded = modelDownloader.downloadedModels.contains(tier)
        let isDownloading = modelDownloader.activeDownloads.contains(tier)
        let progress = modelDownloader.downloadProgress[tier] ?? 0.0
        let isRecommended = (HardwareDetector.recommendedTier(simulate4GB: appGroup.simulateModest4GBDevice) == tier)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(tier.displayName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(isUnlocked ? .white : Color.white.opacity(0.45))

                        if isRecommended && isUnlocked {
                            Text("Recommandé")
                                .font(.system(size: 10, weight: .semibold))
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color(hex: "#0A84FF").opacity(0.22)))
                                .foregroundColor(Color(hex: "#0A84FF"))
                        }
                    }

                    Text(tier.subtitle)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(Color.white.opacity(isUnlocked ? 0.60 : 0.38))
                }

                Spacer()

                if !isUnlocked {
                    Text("🔒 \(tier.requiredRAMBadgeText)")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color(hex: "#FF9F0A").opacity(0.16)))
                        .foregroundColor(Color(hex: "#FF9F0A"))
                } else if isDownloaded {
                    Text("Téléchargé")
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.white.opacity(0.07)))
                        .foregroundColor(Color(hex: "#30D158"))
                }
            }

            HStack(spacing: 14) {
                Label("\(tier.sizeMB) Mo", systemImage: "internaldrive")
                Label("\(tier.ramUsageMB) Mo RAM", systemImage: "memorychip")
                Label(tier.latencyDescription, systemImage: "bolt")
            }
            .font(.system(size: 11, weight: .regular, design: .monospaced))
            .foregroundColor(Color.white.opacity(isUnlocked ? 0.55 : 0.35))

            if isDownloading {
                VStack(alignment: .leading, spacing: 6) {
                    ProgressView(value: progress, total: 1.0)
                        .tint(Color(hex: "#0A84FF"))
                    HStack {
                        Text("Téléchargement : \(Int(progress * 100)) %")
                            .font(.system(size: 11, weight: .regular, design: .monospaced))
                            .foregroundColor(Color.white.opacity(0.75))
                        Spacer()
                        Button("Annuler") {
                            modelDownloader.cancelDownload(tier)
                        }
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(hex: "#FF453A"))
                    }
                }
            }

            HStack(spacing: 10) {
                if !isUnlocked {
                    Button(action: {
                        easterEggStepIndex = 0
                    }) {
                        HStack(spacing: 6) {
                            Text("🔒 Verrouillé par sécurité matérielle (\(tier.requiredRAMBadgeText)) • Forcer le déblocage ?")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(hex: "#FF9F0A"))
                        }
                    }
                    .buttonStyle(.plain)
                } else if isDownloaded {
                    Button(action: {
                        if appGroup.selectedModel != tier {
                            appGroup.selectedModel = tier
                        } else {
                            whisperEngine.loadModel(tier)
                        }
                        if !whisperEngine.isBackgroundEngineRunning {
                            whisperEngine.startBackgroundAudioEngine()
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            Text(isSelected ? "Modèle actif" : "Utiliser ce modèle")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(isSelected ? Color(hex: "#0A84FF") : Color.white.opacity(0.08))
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button(action: {
                        let wasSelected = (appGroup.selectedModel == tier)
                        if wasSelected {
                            whisperEngine.unloadCurrentModel()
                        }
                        modelDownloader.deleteModel(tier)
                        if wasSelected,
                           let fallback = WhisperModelTier.allCases.first(where: {
                               $0 != tier &&
                               modelDownloader.isModelDownloaded($0) &&
                               HardwareDetector.isTierUnlocked(
                                   $0,
                                   simulate4GB: appGroup.simulateModest4GBDevice,
                                   forceUnlock: appGroup.forceUnlockAllModels
                               )
                           }) {
                            appGroup.selectedModel = fallback
                            whisperEngine.loadModel(fallback)
                        }
                    }) {
                        Image(systemName: "trash")
                            .font(.system(size: 12, weight: .regular))
                            .padding(7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.06)))
                            .foregroundColor(Color.white.opacity(0.65))
                    }
                    .buttonStyle(.plain)
                } else if !isDownloading {
                    Button(action: {
                        modelDownloader.downloadModel(tier)
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.circle")
                            Text("Télécharger (\(tier.sizeMB) Mo)")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(Color.white.opacity(0.09))
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(hex: "#0D0D11"))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(
                            isSelected && isUnlocked ? Color(hex: "#0A84FF").opacity(0.65) : Color.white.opacity(0.06),
                            lineWidth: 1.0
                        )
                )
        )
    }

    // MARK: - Section Mémoire d'Écriture JSON (user_style_memory.json)

    private var cardStyleMemorySection: some View {
        let activeLangCode = appGroup.selectedLanguage.effectiveStyleLanguageCode
        let entries = styleMemory.replacements(for: activeLangCode)

        return VStack(alignment: .leading, spacing: 14) {
            cardSectionHeader(
                icon: "brain.head.profile",
                title: "Mémoire d'Écriture (user_style_memory.json)",
                subtitle: "Adapte les abréviations et la ponctuation à votre façon d'écrire (\(activeLangCode.uppercased()))"
            )

            Toggle(isOn: $styleMemory.isMemoryEnabled) {
                Text("Activer le profil user_style_memory.json")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white)
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#30D158")))

            Toggle(isOn: $styleMemory.memory.stripTrailingPeriodOnShortMessages) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Retirer le point final sur les petits messages")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                    Text("Évite le '.' automatique sur les SMS de ≤ \(styleMemory.memory.shortMessageMaxWords) mots")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.55))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0A84FF")))

            // Ajout d'une nouvelle abréviation pour la langue active
            VStack(alignment: .leading, spacing: 8) {
                Text("Abréviations personnelles (\(activeLangCode.uppercased()))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color.white.opacity(0.75))

                HStack(spacing: 8) {
                    TextField("Mot dicté (ex: t'inquiète)", text: $newSpokenPhrase)
                        .font(.system(size: 12))
                        .foregroundColor(.white)
                        .padding(9)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#0D0D11")))

                    Text("→")
                        .foregroundColor(Color.white.opacity(0.5))

                    TextField("Écrit (ex: tkt)", text: $newWrittenReplacement)
                        .font(.system(size: 12))
                        .foregroundColor(.white)
                        .padding(9)
                        .frame(maxWidth: 110)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#0D0D11")))

                    Button(action: {
                        styleMemory.setReplacement(
                            spoken: newSpokenPhrase,
                            written: newWrittenReplacement,
                            for: activeLangCode
                        )
                        newSpokenPhrase = ""
                        newWrittenReplacement = ""
                    }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color(hex: "#0A84FF"))
                    }
                    .buttonStyle(.plain)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(entries, id: \.spoken) { item in
                            HStack(spacing: 6) {
                                Text("« \(item.spoken) » → \(item.written)")
                                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                                    .foregroundColor(.white)

                                Button(action: {
                                    styleMemory.removeReplacement(spoken: item.spoken, for: activeLangCode)
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.white.opacity(0.45))
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#0D0D11")))
                        }
                    }
                }
            }

            HStack(spacing: 8) {
                Button(action: {
                    withAnimation {
                        showRawJSONPreview.toggle()
                    }
                }) {
                    Label(showRawJSONPreview ? "Masquer JSON" : "Voir JSON", systemImage: "curlybraces")
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                        .foregroundColor(.white)
                }
                .buttonStyle(.plain)

                Button(action: {
                    UIPasteboard.general.string = styleMemory.prettyJSONString()
                    showJSONCopiedToast = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        showJSONCopiedToast = false
                    }
                }) {
                    Label(showJSONCopiedToast ? "JSON copié" : "Exporter JSON", systemImage: "doc.on.doc")
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                        .foregroundColor(.white)
                }
                .buttonStyle(.plain)

                Spacer()

                Button(action: {
                    styleMemory.resetToDefaults()
                }) {
                    Text("Réinitialiser")
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))
                        .foregroundColor(Color.white.opacity(0.65))
                }
                .buttonStyle(.plain)
            }

            if showRawJSONPreview {
                Text(styleMemory.prettyJSONString())
                    .font(.system(size: 10, weight: .regular, design: .monospaced))
                    .foregroundColor(Color(hex: "#30D158"))
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#0D0D11")))
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    // MARK: - Section 3 : Apparence de l'Orbe

    private var card3AppearanceStudioSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "circle.hexagongrid",
                title: "Apparence de l'Orbe",
                subtitle: "Couleur de l'onde, fond de la sphère et thèmes spéciaux"
            )

            VStack(alignment: .leading, spacing: 8) {
                Text("Fond de la sphère")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.70))

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ThemeColors.orderedBackgrounds) { preset in
                            let isCurrentBg = (appGroup.bgHex.uppercased() == preset.hex.uppercased())
                            Button(action: {
                                appGroup.bgHex = preset.hex
                            }) {
                                HStack(spacing: 7) {
                                    Circle()
                                        .fill(Color(hex: preset.hex))
                                        .frame(width: 16, height: 16)
                                        .overlay(Circle().strokeBorder(Color.white.opacity(0.30), lineWidth: 0.8))
                                    Text(preset.name)
                                        .font(.system(size: 12, weight: .medium))
                                }
                                .padding(.horizontal, 11)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                                        .fill(isCurrentBg ? Color.white.opacity(0.16) : Color.white.opacity(0.05))
                                )
                                .foregroundColor(.white)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                ColorPicker("Couleur de fond personnalisée", selection: customBgColorBinding, supportsOpacity: false)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.75))
            }

            Divider().background(Color.white.opacity(0.08))

            VStack(alignment: .leading, spacing: 10) {
                Text("Couleur de l'onde")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.70))

                let columns = [GridItem(.adaptive(minimum: 48), spacing: 10)]
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(ThemeColors.orderedForegrounds) { preset in
                        let isCurrentFg = (appGroup.fgHex.uppercased() == preset.hex.uppercased())
                        Button(action: {
                            appGroup.fgHex = preset.hex
                        }) {
                            VStack(spacing: 4) {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 30, height: 30)
                                    .overlay(
                                        Circle()
                                            .strokeBorder(
                                                isCurrentFg ? Color.white : Color.white.opacity(0.20),
                                                lineWidth: isCurrentFg ? 2.0 : 0.8
                                            )
                                    )

                                Text(preset.name.components(separatedBy: " ").first ?? preset.name)
                                    .font(.system(size: 10, weight: .regular))
                                    .foregroundColor(isCurrentFg ? .white : Color.white.opacity(0.60))
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                HStack(spacing: 8) {
                    Button(action: {
                        appGroup.bgHex = "#050507"
                        appGroup.fgHex = "#050507"
                    }) {
                        Text("Éclipse (Noir sur Noir)")
                            .font(.system(size: 12, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .fill(Color(hex: "#0D0D11"))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                                            .strokeBorder(Color.white.opacity(0.18), lineWidth: 0.8)
                                    )
                            )
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        appGroup.bgHex = "#F8F9FA"
                        appGroup.fgHex = "#FFFFFF"
                    }) {
                        Text("Perle (Blanc sur Blanc)")
                            .font(.system(size: 12, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .fill(Color(hex: "#F8F9FA"))
                            )
                            .foregroundColor(Color(hex: "#1C1C1E"))
                    }
                    .buttonStyle(.plain)
                }

                ColorPicker("Couleur d'onde personnalisée", selection: customFgColorBinding, supportsOpacity: false)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.75))
            }

            Divider().background(Color.white.opacity(0.08))

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.45))
                    TextField("Rechercher une couleur (ex: turquoise, or, #FF0055)...", text: $colorSearchText)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.white)
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)
                    if !colorSearchText.isEmpty {
                        Button(action: { colorSearchText = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Color.white.opacity(0.45))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(hex: "#0D0D11"))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                        )
                )

                if let parsedHex = ThemeColors.parseColorInput(colorSearchText) {
                    HStack(spacing: 10) {
                        Circle()
                            .fill(Color(hex: parsedHex))
                            .frame(width: 26, height: 26)
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.35), lineWidth: 1))

                        Text("\(ThemeColors.displayName(for: parsedHex)) (\(parsedHex))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)

                        Spacer()

                        Button("Onde") {
                            appGroup.fgHex = parsedHex
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 7).fill(Color(hex: "#0A84FF")))
                        .foregroundColor(.white)
                        .buttonStyle(.plain)

                        Button("Fond") {
                            appGroup.bgHex = parsedHex
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 7).fill(Color.white.opacity(0.12)))
                        .foregroundColor(.white)
                        .buttonStyle(.plain)
                    }
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.04)))
                }
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    // MARK: - Section 4 : Préférences & Commandes Vocales

    private var card4PreferencesAndVoiceCommands: some View {
        VStack(alignment: .leading, spacing: 14) {
            cardSectionHeader(
                icon: "slider.horizontal.3",
                title: "Préférences & Commandes Vocales",
                subtitle: "Ponctuation automatique, détection de fin de phrase et commandes"
            )

            Toggle(isOn: $appGroup.autoSpaceForGoodnotes) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Espace automatique après chaque phrase")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                    Text("Sépare proprement les phrases dictées à la suite")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.55))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#30D158")))

            Toggle(isOn: $appGroup.autoPunctuation) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Majuscule et ponctuation automatiques")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                    Text("Ajoute majuscules et points en fin de phrase")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.55))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0A84FF")))

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Délai de pause automatique (VAD)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                    Spacer()
                    Text(String(format: "%.2f s", appGroup.vadSilenceThresholdSeconds))
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.80))
                }

                Slider(value: $appGroup.vadSilenceThresholdSeconds, in: 0.45...2.0, step: 0.05)
                    .tint(Color(hex: "#0A84FF"))
            }

            Divider().background(Color.white.opacity(0.08))

            VStack(alignment: .leading, spacing: 6) {
                Text("Commandes vocales disponibles (FR / EN)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.70))

                voiceCommandRow(spoken: "« À la ligne » / « New line »", result: "Saut de ligne")
                voiceCommandRow(spoken: "« Effacer la dernière phrase » / « Delete last sentence »", result: "Annule la dictée")
                voiceCommandRow(spoken: "« Point d'interrogation » / « Question mark »", result: "Insère ?")
                voiceCommandRow(spoken: "« Point d'exclamation » / « Exclamation mark »", result: "Insère !")
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    private func voiceCommandRow(spoken: String, result: String) -> some View {
        HStack {
            Text(spoken)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color.white.opacity(0.88))
            Spacer()
            Text(result)
                .font(.system(size: 11, weight: .regular))
                .foregroundColor(Color.white.opacity(0.55))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#0D0D11")))
    }

    // MARK: - Section 5 : Partage GitHub & Guide d'Installation Universel

    private var card5GitHubAndSetupSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            cardSectionHeader(
                icon: "qrcode",
                title: "Partage & Installation (v2.2.1)",
                subtitle: "Disponible pour Apple (iPhone, iPad, Mac) et Android (.apk)"
            )

            HStack(alignment: .center, spacing: 16) {
                if let qrUIImage = generateHighContrastQRCode(from: githubReleasesURLString) {
                    Image(uiImage: qrUIImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 96, height: 96)
                        .padding(6)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Releases GitHub v2.2.1 (.ipa & .apk)")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)

                    Text(githubRepoURLString)
                        .font(.system(size: 11, weight: .regular, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.60))
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        Button(action: {
                            UIPasteboard.general.string = githubRepoURLString
                            showRepoCopiedToast = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                showRepoCopiedToast = false
                            }
                        }) {
                            Label(showRepoCopiedToast ? "Copié" : "Copier", systemImage: showRepoCopiedToast ? "checkmark" : "link")
                                .font(.system(size: 12, weight: .medium))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.08)))
                                .foregroundColor(.white)
                        }
                        .buttonStyle(.plain)

                        if let url = URL(string: githubRepoURLString) {
                            Link(destination: url) {
                                Label("GitHub", systemImage: "arrow.up.right")
                                    .font(.system(size: 12, weight: .medium))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 7)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#0A84FF")))
                                    .foregroundColor(.white)
                            }
                        }
                    }
                }
            }
        }
        .padding(18)
        .background(cardBackgroundSurface)
    }

    private func generateHighContrastQRCode(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"

        guard let outputImage = filter.outputImage else { return nil }
        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private var cardBackgroundSurface: some View {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(Color(hex: "#131317"))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.07), lineWidth: 1)
            )
    }

    private func cardSectionHeader(icon: String, title: String, subtitle: String) -> some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Color.white.opacity(0.85))
                .frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.07)))

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.55))
            }
        }
    }

    private func statusColorForOrbState(_ state: WhisperOrbState) -> Color {
        switch state {
        case .idle:
            return Color(hex: "#30D158")
        case .listening:
            return Color(hex: "#0A84FF")
        case .transcribing:
            return Color(hex: "#FFD60A")
        }
    }
}
