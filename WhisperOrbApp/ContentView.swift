import SwiftUI
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

public struct ContentView: View {
    @EnvironmentObject private var appGroup: AppGroupManager
    @EnvironmentObject private var modelDownloader: ModelDownloader
    @EnvironmentObject private var whisperEngine: WhisperEngine
    @EnvironmentObject private var networkMonitor: NetworkMonitor

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var colorSearchText: String = ""
    @State private var showCopiedToast: Bool = false
    @State private var showRepoCopiedToast: Bool = false

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
            // Fond noir sobre style Apple Pro
            Color(hex: "#09090B")
                .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 18) {
                    // En-tête sobre et épuré (sans bandeau réseau/DST)
                    soberAppHeaderSection

                    // Disposition adaptative : 2 colonnes sur iPad & Mac / 1 colonne sur iPhone
                    if horizontalSizeClass == .regular {
                        HStack(alignment: .top, spacing: 18) {
                            VStack(spacing: 18) {
                                card1VoiceStudioSection
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
                            card3AppearanceStudioSection
                            card4PreferencesAndVoiceCommands
                            card5GitHubAndSetupSection
                        }
                    }
                }
                .padding(.horizontal, horizontalSizeClass == .regular ? 24 : 16)
                .padding(.vertical, 16)
            }
        }
    }

    // MARK: - En-tête Sobre & Grand Public (iPhone, iPad & Mac)

    private var soberAppHeaderSection: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("WhisperOrb")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    Text("ON-DEVICE AI")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.white.opacity(0.08)))
                        .foregroundColor(Color.white.opacity(0.70))
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

    // MARK: - Section 1 : Studio Vocal & Écoute en Arrière-Plan

    private var card1VoiceStudioSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "waveform",
                title: "Studio Vocal",
                subtitle: "Touchez l'orbe pour dicter ou activez le moteur en arrière-plan pour le clavier"
            )

            // Scène centrale de l'Orbe 3D
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
                            Text("\(appGroup.orbState.statusTitle) — Touchez l'orbe pour parler")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color.white.opacity(0.72))
                        }
                    }
                }

                // Mode Clavier Réduit + Badge Optique
                HStack {
                    Toggle(isOn: $appGroup.isKeyboardCollapsed) {
                        Text("Mode clavier réduit (Barre vocale seule)")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(Color.white.opacity(0.85))
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0A84FF")))
                }
            }

            // Bouton d'activation du moteur en arrière-plan
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

            // Zone d'essai de dictée
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
                        appGroup.triggerDeleteLastSentence()
                        if whisperEngine.liveTestTranscript.count >= appGroup.lastInsertedSentenceLength && appGroup.lastInsertedSentenceLength > 0 {
                            whisperEngine.liveTestTranscript.removeLast(appGroup.lastInsertedSentenceLength)
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

    // MARK: - Section 2 : Modèles Whisper Hors-Ligne (GPU Metal + FlashAttention)

    private var card2ModelsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            cardSectionHeader(
                icon: "cpu",
                title: "Modèles IA Hors-Ligne",
                subtitle: "Accélération GPU Metal + FlashAttention (\(whisperEngine.optimalThreadCount) cœurs Performance)"
            )

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
        let isSelected = (appGroup.selectedModel == tier)
        let isDownloaded = modelDownloader.downloadedModels.contains(tier)
        let isDownloading = modelDownloader.activeDownloads.contains(tier)
        let progress = modelDownloader.downloadProgress[tier] ?? 0.0
        let isRecommended = (HardwareDetector.recommendedTier == tier)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(tier.displayName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)

                        if isRecommended {
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
                        .foregroundColor(Color.white.opacity(0.60))
                }

                Spacer()

                if isDownloaded {
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
            .foregroundColor(Color.white.opacity(0.55))

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
                if isDownloaded {
                    Button(action: {
                        appGroup.selectedModel = tier
                        whisperEngine.loadModel(tier)
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
                        if appGroup.selectedModel == tier {
                            whisperEngine.unloadCurrentModel()
                        }
                        modelDownloader.deleteModel(tier)
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
                            isSelected ? Color(hex: "#0A84FF").opacity(0.65) : Color.white.opacity(0.06),
                            lineWidth: 1.0
                        )
                )
        )
    }

    // MARK: - Section 3 : Apparence de l'Orbe

    private var card3AppearanceStudioSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "circle.hexagongrid",
                title: "Apparence de l'Orbe",
                subtitle: "Couleur de l'onde, fond de la sphère et thèmes spéciaux"
            )

            // Fond de la sphère
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

            // Onde lumineuse
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

            // Recherche par nom ou code hexadécimal
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
                Text("Commandes vocales disponibles")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.70))

                voiceCommandRow(spoken: "« À la ligne » / « Nouveau paragraphe »", result: "Saut de ligne")
                voiceCommandRow(spoken: "« Effacer la dernière phrase »", result: "Annule la dernière dictée")
                voiceCommandRow(spoken: "« Point d'interrogation » / « Point d'exclamation »", result: "Insère ? ou !")
                voiceCommandRow(spoken: "« Ouvrir les guillemets » / « Fermer les guillemets »", result: "Insère « et »")
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
                title: "Partage & Installation",
                subtitle: "Compatible avec tous les iPhone, iPad et Mac (Apple Silicon)"
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
                    Text("Releases GitHub (.ipa)")
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

    // MARK: - Générateur Natif CoreImage de QR Code

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

    // MARK: - Composants Visuels Sobres

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
