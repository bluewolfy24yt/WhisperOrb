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
    @State private var previewShowSideTab: Bool = true

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
            // Fond sombre profond style iPadOS Pro
            LinearGradient(
                colors: [
                    Color(hex: "#07070B"),
                    Color(hex: "#0D0E15"),
                    Color(hex: "#08080C")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 22) {
                    // En-tête + Bandeau Mode Examen + Télémétrie Matérielle
                    examHeaderAndTelemetrySection

                    // Disposition adaptative : Grille large sur iPad Pro 12,9" / Empilement sur iPhone 13
                    if horizontalSizeClass == .regular {
                        HStack(alignment: .top, spacing: 20) {
                            VStack(spacing: 20) {
                                card1LiveStudioAndBackgroundEngine
                                card4GoodnotesOptimizationsAndVoiceCommands
                                card5GitHubQRCodeAndSetupGuide
                            }
                            .frame(maxWidth: .infinity)

                            VStack(spacing: 20) {
                                card2WhisperModelsSelector
                                card3ColorCustomizationStudio
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        VStack(spacing: 20) {
                            card1LiveStudioAndBackgroundEngine
                            card2WhisperModelsSelector
                            card3ColorCustomizationStudio
                            card4GoodnotesOptimizationsAndVoiceCommands
                            card5GitHubQRCodeAndSetupGuide
                        }
                    }
                }
                .padding(.horizontal, horizontalSizeClass == .regular ? 28 : 16)
                .padding(.vertical, 20)
            }
        }
    }

    // MARK: - En-tête : Bandeau Anti-Suspicion "Mode Examen Hors-Ligne" & Télémétrie A12Z / A15

    private var examHeaderAndTelemetrySection: some View {
        VStack(spacing: 14) {
            // Bandeau Dynamique Mode Examen Hors-Ligne
            HStack(spacing: 12) {
                Image(systemName: networkMonitor.isConnected ? "wifi.exclamationmark" : "lock.shield.fill")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(networkMonitor.isConnected ? Color(hex: "#FFCC00") : Color(hex: "#34C759"))

                Text(networkMonitor.bannerText)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.leading)

                Spacer()

                Text(networkMonitor.isConnected ? networkMonitor.connectionType.uppercased() : "100 % LOCAL")
                    .font(.system(size: 11, weight: .heavy, design: .monospaced))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule()
                            .fill(networkMonitor.isConnected ? Color(hex: "#FF9500").opacity(0.28) : Color(hex: "#34C759").opacity(0.28))
                    )
                    .foregroundColor(networkMonitor.isConnected ? Color(hex: "#FFCC00") : Color(hex: "#34C759"))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(
                        networkMonitor.isConnected
                        ? Color(hex: "#1E293B").opacity(0.92)
                        : Color(hex: "#062E1E").opacity(0.92)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(
                                networkMonitor.isConnected
                                ? Color(hex: "#38BDF8").opacity(0.45)
                                : Color(hex: "#34C759").opacity(0.65),
                                lineWidth: 1.4
                            )
                    )
            )

            // Barre de Titre & Télémétrie Matérielle
            HStack(alignment: .center, spacing: 14) {
                GeminiBubbleView(
                    bgHex: appGroup.bgHex,
                    fgHex: appGroup.fgHex,
                    audioLevel: CGFloat(appGroup.currentAudioLevel),
                    state: appGroup.orbState,
                    size: 46,
                    showSideTab: false
                )

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("WhisperOrb")
                            .font(.system(size: 26, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)

                        Text("PRO DST")
                            .font(.system(size: 11, weight: .black, design: .monospaced))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color(hex: "#0088FF").opacity(0.25)))
                            .foregroundColor(Color(hex: "#32ADE6"))
                    }

                    Text(HardwareDetector.telemetrySummary)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.72))
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(whisperEngine.isBackgroundEngineRunning ? Color(hex: "#34C759") : Color(hex: "#FF9500"))
                            .frame(width: 8, height: 8)
                        Text(whisperEngine.isBackgroundEngineRunning ? "Moteur Tâche de Fond Actif" : "Moteur en Veille")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                    }

                    Text("Modèle actif : \(appGroup.selectedModel.shortBadgeName) (\(appGroup.selectedModel.ramUsageMB) Mo RAM)")
                        .font(.system(size: 11, weight: .regular))
                        .foregroundColor(Color.white.opacity(0.60))
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(cardBackgroundSurface)
        }
    }

    // MARK: - CARTE 1 : Studio de Test en Direct & Activation du Moteur Tâche de Fond

    private var card1LiveStudioAndBackgroundEngine: some View {
        VStack(alignment: .leading, spacing: 18) {
            cardSectionHeader(
                icon: "waveform.circle.fill",
                iconColor: Color(hex: "#0088FF"),
                title: "1. Studio Temps Réel & Moteur Goodnotes",
                subtitle: "Testez votre bulle Gemini Live 120 Hz et activez l'écoute en tâche de fond"
            )

            // Scène centrale de la grande bulle GeminiBubbleView (size: 130)
            VStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color(hex: "#0A0B10"))
                        .overlay(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.07), lineWidth: 1)
                        )
                        .frame(height: 205)

                    VStack(spacing: 12) {
                        GeminiBubbleView(
                            bgHex: appGroup.bgHex,
                            fgHex: appGroup.fgHex,
                            audioLevel: CGFloat(appGroup.currentAudioLevel),
                            state: appGroup.orbState,
                            size: 130,
                            showSideTab: previewShowSideTab,
                            onTap: {
                                let generator = UIImpactFeedbackGenerator(style: .medium)
                                generator.impactOccurred()
                                whisperEngine.toggleRecording()
                            }
                        )

                        HStack(spacing: 8) {
                            Circle()
                                .fill(statusColorForOrbState(appGroup.orbState))
                                .frame(width: 8, height: 8)
                            Text("\(appGroup.orbState.statusTitle) — Touchez l'orbe pour parler / arrêter")
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.white.opacity(0.82))
                        }
                    }
                }

                // Option d'affichage de la languette latérale Gemini Live + Badges de mode optique
                HStack {
                    Toggle(isOn: $previewShowSideTab) {
                        Text("Afficher la languette latérale d'accroche")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.75))
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0088FF")))

                    Spacer()

                    opticalModeBadge
                }
            }

            // Bouton Principal : Activer le Moteur Audio en Tâche de Fond (Pour Goodnotes)
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
                    Image(systemName: whisperEngine.isBackgroundEngineRunning ? "checkmark.shield.fill" : "waveform.badge.mic")
                        .font(.system(size: 18, weight: .bold))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(
                            whisperEngine.isBackgroundEngineRunning
                            ? "Moteur Audio en Tâche de Fond ACTIF (Réponse 0,01 s dans Goodnotes)"
                            : "Activer le Moteur Audio en Tâche de Fond (Pour Goodnotes)"
                        )
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        Text(whisperEngine.statusBannerMessage)
                            .font(.system(size: 11, weight: .medium))
                            .opacity(0.88)
                    }
                    Spacer()
                }
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            whisperEngine.isBackgroundEngineRunning
                            ? LinearGradient(colors: [Color(hex: "#16A34A"), Color(hex: "#059669")], startPoint: .leading, endPoint: .trailing)
                            : LinearGradient(colors: [Color(hex: "#0088FF"), Color(hex: "#5856D6")], startPoint: .leading, endPoint: .trailing)
                        )
                )
            }
            .buttonStyle(.plain)

            if let errorMsg = whisperEngine.lastErrorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(Color(hex: "#FF9500"))
                    Text(errorMsg)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: "#FFCC00"))
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#FF3B30").opacity(0.15)))
            }

            // Zone de test de dictée en direct dans l'application
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Zone de test de dictée en direct :")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color.white.opacity(0.88))

                    Spacer()

                    if whisperEngine.lastTranscriptionLatencyMs > 0 {
                        Text("Dernière inférence : \(whisperEngine.lastTranscriptionLatencyMs) ms")
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color(hex: "#34C759"))
                    }
                }

                TextEditor(text: $whisperEngine.liveTestTranscript)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundColor(.white)
                    .scrollContentBackground(.hidden)
                    .padding(10)
                    .frame(minHeight: 105, maxHeight: 150)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.black.opacity(0.45))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                            )
                    )

                HStack(spacing: 10) {
                    Button(action: {
                        UIPasteboard.general.string = whisperEngine.liveTestTranscript
                        showCopiedToast = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            showCopiedToast = false
                        }
                    }) {
                        Label(showCopiedToast ? "Copié !" : "Copier le texte", systemImage: showCopiedToast ? "checkmark" : "doc.on.doc")
                            .font(.system(size: 12, weight: .semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.10)))
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
                        Label("↶ Effacer dernière phrase", systemImage: "arrow.uturn.backward")
                            .font(.system(size: 12, weight: .semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#FF9500").opacity(0.18)))
                            .foregroundColor(Color(hex: "#FFCC00"))
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button(action: {
                        whisperEngine.liveTestTranscript = ""
                    }) {
                        Label("Vider", systemImage: "trash")
                            .font(.system(size: 12, weight: .semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#FF3B30").opacity(0.18)))
                            .foregroundColor(Color(hex: "#FF3B30"))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(20)
        .background(cardBackgroundSurface)
    }

    // MARK: - CARTE 2 : Sélecteur des 3 Modèles ("Whisper Flash", "Whisper PRO", "Whisper Expert")

    private var card2WhisperModelsSelector: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "cpu.fill",
                iconColor: Color(hex: "#8A2BE2"),
                title: "2. Modèles Whisper Hors-Ligne",
                subtitle: "Whisper Flash • Whisper PRO • Whisper Expert (\(whisperEngine.optimalThreadCount) threads CPU + GPU Metal)"
            )

            if let dlError = modelDownloader.errorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "wifi.exclamationmark")
                        .foregroundColor(Color(hex: "#FF3B30"))
                    Text(dlError)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: "#FF3B30"))
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: "#FF3B30").opacity(0.14)))
            }

            VStack(spacing: 12) {
                ForEach(WhisperModelTier.allCases) { tier in
                    modelTierCardView(for: tier)
                }
            }
        }
        .padding(20)
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
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(tier.displayName)
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundColor(.white)

                        if isRecommended {
                            Text("Recommandé pour cet appareil")
                                .font(.system(size: 10, weight: .heavy, design: .rounded))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Capsule().fill(Color(hex: "#0088FF").opacity(0.28)))
                                .foregroundColor(Color(hex: "#32ADE6"))
                        }
                    }

                    Text(tier.subtitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.68))
                }

                Spacer()

                if isDownloaded {
                    Text("Prêt Hors-Ligne ✅")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Color(hex: "#34C759").opacity(0.22)))
                        .foregroundColor(Color(hex: "#34C759"))
                }
            }

            // Métriques techniques : Stockage, RAM, Vitesse, Fichier GGML
            HStack(spacing: 14) {
                Label("\(tier.sizeMB) Mo disque", systemImage: "internaldrive")
                Label("\(tier.ramUsageMB) Mo RAM", systemImage: "memorychip")
                Label(tier.latencyDescription, systemImage: "bolt.fill")
            }
            .font(.system(size: 11, weight: .semibold, design: .monospaced))
            .foregroundColor(Color.white.opacity(0.75))

            if isDownloading {
                VStack(alignment: .leading, spacing: 6) {
                    ProgressView(value: progress, total: 1.0)
                        .tint(Color(hex: "#0088FF"))
                    HStack {
                        Text("Téléchargement de \(tier.displayName) : \(Int(progress * 100)) %")
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color(hex: "#32ADE6"))
                        Spacer()
                        Button("Annuler") {
                            modelDownloader.cancelDownload(tier)
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: "#FF3B30"))
                    }
                }
            }

            // Boutons d'action du modèle
            HStack(spacing: 10) {
                if isDownloaded {
                    Button(action: {
                        appGroup.selectedModel = tier
                        whisperEngine.loadModel(tier)
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            Text(isSelected ? "\(tier.displayName) Actif" : "Activer \(tier.displayName)")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(isSelected ? Color(hex: "#0088FF") : Color.white.opacity(0.10))
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
                            .font(.system(size: 13, weight: .semibold))
                            .padding(8)
                            .background(RoundedRectangle(cornerRadius: 9).fill(Color(hex: "#FF3B30").opacity(0.18)))
                            .foregroundColor(Color(hex: "#FF3B30"))
                    }
                    .buttonStyle(.plain)
                } else if !isDownloading {
                    Button(action: {
                        modelDownloader.downloadModel(tier)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.down.circle.fill")
                            Text("Télécharger \(tier.displayName) (\(tier.sizeMB) Mo)")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color(hex: "#0088FF").opacity(0.26))
                        )
                        .foregroundColor(Color(hex: "#32ADE6"))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(isSelected ? Color(hex: "#192136").opacity(0.95) : Color.black.opacity(0.35))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(
                            isSelected ? Color(hex: "#0088FF") : Color.white.opacity(0.08),
                            lineWidth: isSelected ? 1.8 : 1.0
                        )
                )
        )
    }

    // MARK: - CARTE 3 : Studio de Personnalisation des Couleurs de la Bulle

    private var card3ColorCustomizationStudio: some View {
        VStack(alignment: .leading, spacing: 18) {
            cardSectionHeader(
                icon: "paintpalette.fill",
                iconColor: Color(hex: "#FF2A85"),
                title: "3. Studio des Couleurs de l'Orbe",
                subtitle: "Dictionnaires NSI + Roue chromatique + Recherche de 140+ couleurs FR/EN"
            )

            // Sous-section A — Fond de la bulle (Background)
            VStack(alignment: .leading, spacing: 10) {
                Text("A. Fond de la sphère (Background) — Actuel : \(appGroup.bgHex)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(ThemeColors.orderedBackgrounds) { preset in
                            let isCurrentBg = (appGroup.bgHex.uppercased() == preset.hex.uppercased())
                            Button(action: {
                                appGroup.bgHex = preset.hex
                            }) {
                                HStack(spacing: 8) {
                                    Circle()
                                        .fill(Color(hex: preset.hex))
                                        .frame(width: 20, height: 20)
                                        .overlay(Circle().strokeBorder(Color.white.opacity(0.35), lineWidth: 1))
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(preset.name)
                                            .font(.system(size: 12, weight: .bold))
                                        Text(preset.hex)
                                            .font(.system(size: 10, weight: .regular, design: .monospaced))
                                            .opacity(0.70)
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .fill(isCurrentBg ? Color(hex: "#0088FF").opacity(0.30) : Color.white.opacity(0.07))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                                .strokeBorder(isCurrentBg ? Color(hex: "#0088FF") : Color.white.opacity(0.10), lineWidth: 1.2)
                                        )
                                )
                                .foregroundColor(.white)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                ColorPicker("Spectre complet (Fond sur mesure)", selection: customBgColorBinding, supportsOpacity: false)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.88))
            }

            Divider().background(Color.white.opacity(0.12))

            // Sous-section B — Onde lumineuse (Foreground) + Combos Spéciaux 1-Clic
            VStack(alignment: .leading, spacing: 12) {
                Text("B. Onde lumineuse (Foreground) — Actuelle : \(appGroup.fgHex)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                let columns = [GridItem(.adaptive(minimum: 52), spacing: 10)]
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(ThemeColors.orderedForegrounds) { preset in
                        let isCurrentFg = (appGroup.fgHex.uppercased() == preset.hex.uppercased())
                        Button(action: {
                            appGroup.fgHex = preset.hex
                        }) {
                            VStack(spacing: 4) {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 34, height: 34)
                                    .overlay(
                                        Circle()
                                            .strokeBorder(
                                                isCurrentFg ? Color.white : Color.white.opacity(0.25),
                                                lineWidth: isCurrentFg ? 2.5 : 1.0
                                            )
                                    )
                                    .shadow(color: Color(hex: preset.hex).opacity(0.45), radius: isCurrentFg ? 6 : 0)

                                Text(preset.name.components(separatedBy: " ").first ?? preset.name)
                                    .font(.system(size: 10, weight: isCurrentFg ? .bold : .medium))
                                    .foregroundColor(isCurrentFg ? .white : Color.white.opacity(0.70))
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                // Boutons rapides Combos Spéciaux en 1 clic
                HStack(spacing: 10) {
                    Button(action: {
                        appGroup.bgHex = "#050507"
                        appGroup.fgHex = "#050507"
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "moon.stars.fill")
                            Text("Combo Spécial : Noir sur Noir (Éclipse)")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(Color(hex: "#14141C"))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .strokeBorder(Color.white.opacity(0.35), lineWidth: 1.2)
                                )
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        appGroup.bgHex = "#F8F9FA"
                        appGroup.fgHex = "#FFFFFF"
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles")
                            Text("Combo Spécial : Blanc sur Blanc (Perle Cristal)")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(Color(hex: "#F8F9FA"))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .strokeBorder(Color(hex: "#CBD2D9"), lineWidth: 1.2)
                                )
                        )
                        .foregroundColor(Color(hex: "#0F172A"))
                    }
                    .buttonStyle(.plain)
                }

                ColorPicker("Spectre complet (Onde sur mesure)", selection: customFgColorBinding, supportsOpacity: false)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.88))
            }

            Divider().background(Color.white.opacity(0.12))

            // Sous-section C — Champ de recherche textuelle de couleur (ThemeColors.parseColorInput)
            VStack(alignment: .leading, spacing: 10) {
                Text("C. Recherche Textuelle Intelligente (140+ Couleurs FR/EN ou Code Hexa)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color.white.opacity(0.55))
                    TextField("Tapez une couleur (ex: crimson, turquoise, corail, or, #FF0055)...", text: $colorSearchText)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)
                    if !colorSearchText.isEmpty {
                        Button(action: { colorSearchText = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Color.white.opacity(0.55))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(11)
                .background(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill(Color.black.opacity(0.45))
                        .overlay(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
                        )
                )

                if let parsedHex = ThemeColors.parseColorInput(colorSearchText) {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color(hex: parsedHex))
                            .frame(width: 32, height: 32)
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.45), lineWidth: 1.2))

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Couleur détectée : \(ThemeColors.displayName(for: parsedHex))")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                            Text(parsedHex)
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color(hex: "#32ADE6"))
                        }

                        Spacer()

                        Button("Appliquer à l'Onde") {
                            appGroup.fgHex = parsedHex
                        }
                        .font(.system(size: 11, weight: .bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(RoundedRectangle(cornerRadius: 9).fill(Color(hex: "#0088FF")))
                        .foregroundColor(.white)
                        .buttonStyle(.plain)

                        Button("Appliquer au Fond") {
                            appGroup.bgHex = parsedHex
                        }
                        .font(.system(size: 11, weight: .bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(RoundedRectangle(cornerRadius: 9).fill(Color.white.opacity(0.14)))
                        .foregroundColor(.white)
                        .buttonStyle(.plain)
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.06)))
                }
            }
        }
        .padding(20)
        .background(cardBackgroundSurface)
    }

    // MARK: - CARTE 4 : Optimisations Spéciales Goodnotes & Commandes Vocales

    private var card4GoodnotesOptimizationsAndVoiceCommands: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "pencil.and.scribble",
                iconColor: Color(hex: "#34C759"),
                title: "4. Optimisations Goodnotes & Commandes Vocales",
                subtitle: "Réglages de dictée mains-libres (VAD) et aide-mémoire pour vos copies d'examen"
            )

            Toggle(isOn: $appGroup.autoSpaceForGoodnotes) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Espace automatique après chaque phrase (Optimisé zones de texte Goodnotes)")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                    Text("Évite que deux phrases dictées consécutivement ne se collent dans Goodnotes")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.60))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#34C759")))

            Toggle(isOn: $appGroup.autoPunctuation) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Majuscule automatique en début de phrase")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                    Text("Assure une typographie française propre à chaque segment transcrit")
                        .font(.system(size: 11))
                        .foregroundColor(Color.white.opacity(0.60))
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: Color(hex: "#0088FF")))

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Délai de pause (VAD) avant transcription automatique")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                    Spacer()
                    Text(String(format: "%.1f s", appGroup.vadSilenceThresholdSeconds))
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(Color(hex: "#32ADE6"))
                }

                Slider(value: $appGroup.vadSilenceThresholdSeconds, in: 0.5...2.5, step: 0.1)
                    .tint(Color(hex: "#0088FF"))

                Text("En mode mains-libres, dès que vous faites une pause de \(String(format: "%.1f", appGroup.vadSilenceThresholdSeconds)) s, la phrase est transcrite et insérée dans Goodnotes.")
                    .font(.system(size: 11))
                    .foregroundColor(Color.white.opacity(0.60))
            }

            Divider().background(Color.white.opacity(0.12))

            VStack(alignment: .leading, spacing: 8) {
                Text("Aide-mémoire des commandes vocales françaises reconnues :")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                voiceCommandRow(spoken: "« À la ligne » ou « Nouveau paragraphe »", result: "Insère un retour à la ligne (\\n)")
                voiceCommandRow(spoken: "« Double saut de ligne »", result: "Change de paragraphe (\\n\\n)")
                voiceCommandRow(spoken: "« Effacer la dernière phrase »", result: "Supprime la dernière phrase dictée dans Goodnotes")
                voiceCommandRow(spoken: "« Ouvrir les guillemets » / « Fermer les guillemets »", result: "Insère « et »")
                voiceCommandRow(spoken: "« Point d'interrogation » / « Point d'exclamation »", result: "Insère ? ou !")
            }
        }
        .padding(20)
        .background(cardBackgroundSurface)
    }

    private func voiceCommandRow(spoken: String, result: String) -> some View {
        HStack {
            Text(spoken)
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundColor(Color(hex: "#32ADE6"))
            Spacer()
            Text(result)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(Color.white.opacity(0.75))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.05)))
    }

    // MARK: - CARTE 5 : Partage GitHub & Guide d'Activation Rapide

    private var card5GitHubQRCodeAndSetupGuide: some View {
        VStack(alignment: .leading, spacing: 16) {
            cardSectionHeader(
                icon: "qrcode.viewfinder",
                iconColor: Color(hex: "#FFCC00"),
                title: "5. Partage GitHub (.ipa) & Guide d'Activation",
                subtitle: "Scannez pour télécharger la dernière Release .ipa ou activer le clavier dans iPadOS"
            )

            HStack(alignment: .center, spacing: 18) {
                if let qrUIImage = generateHighContrastQRCode(from: githubReleasesURLString) {
                    Image(uiImage: qrUIImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 118, height: 118)
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Dépôt & Releases Officielles")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)

                    Text(githubRepoURLString)
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(Color(hex: "#32ADE6"))
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        Button(action: {
                            UIPasteboard.general.string = githubRepoURLString
                            showRepoCopiedToast = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                showRepoCopiedToast = false
                            }
                        }) {
                            Label(showRepoCopiedToast ? "Lien copié !" : "Copier le lien", systemImage: showRepoCopiedToast ? "checkmark" : "link")
                                .font(.system(size: 12, weight: .bold))
                                .padding(.horizontal, 11)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 9).fill(Color.white.opacity(0.12)))
                                .foregroundColor(.white)
                        }
                        .buttonStyle(.plain)

                        if let url = URL(string: githubRepoURLString) {
                            Link(destination: url) {
                                Label("Ouvrir GitHub", systemImage: "arrow.up.right.square.fill")
                                    .font(.system(size: 12, weight: .bold))
                                    .padding(.horizontal, 11)
                                    .padding(.vertical, 8)
                                    .background(RoundedRectangle(cornerRadius: 9).fill(Color(hex: "#0088FF")))
                                    .foregroundColor(.white)
                            }
                        }
                    }
                }
            }

            Divider().background(Color.white.opacity(0.12))

            // Encadré récapitulatif en 3 étapes
            VStack(alignment: .leading, spacing: 8) {
                Text("Activation rapide en 3 étapes sur l'iPad Pro :")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                setupStepRow(number: "1", text: "Téléchargez le modèle Whisper Expert (ou Whisper PRO) dans la Carte 2 ci-dessus.")
                setupStepRow(number: "2", text: "Ouvrez Réglages > Général > Clavier > Claviers > Ajouter un clavier > WhisperOrb et activez « Autoriser l'accès complet ».")
                setupStepRow(number: "3", text: "Activez le Moteur Audio en Tâche de Fond (Carte 1), passez en Mode Avion, puis ouvrez Goodnotes !")
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.35)))
        }
        .padding(20)
        .background(cardBackgroundSurface)
    }

    private func setupStepRow(number: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(number)
                .font(.system(size: 11, weight: .black, design: .rounded))
                .frame(width: 20, height: 20)
                .background(Circle().fill(Color(hex: "#0088FF")))
                .foregroundColor(.white)

            Text(text)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color.white.opacity(0.85))
        }
    }

    // MARK: - Générateur Natif CoreImage de QR Code Haute Définition

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

    // MARK: - Composants Visuels Partagés des Cartes

    private var cardBackgroundSurface: some View {
        RoundedRectangle(cornerRadius: 22, style: .continuous)
            .fill(Color(hex: "#14141C").opacity(0.94))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.09), lineWidth: 1)
            )
    }

    private func cardSectionHeader(icon: String, iconColor: Color, title: String, subtitle: String) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(iconColor)
                .frame(width: 40, height: 40)
                .background(RoundedRectangle(cornerRadius: 11).fill(iconColor.opacity(0.16)))

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.62))
            }
        }
    }

    private var opticalModeBadge: some View {
        let labelAndColor: (String, Color) = {
            if ThemeColors.isBlackOnBlack(bgHex: appGroup.bgHex, fgHex: appGroup.fgHex) {
                return ("Mode Éclipse (Noir/Noir)", Color(hex: "#8E8E93"))
            } else if ThemeColors.isWhiteOnWhite(bgHex: appGroup.bgHex, fgHex: appGroup.fgHex) {
                return ("Mode Perle Cristal (Blanc/Blanc)", Color(hex: "#32ADE6"))
            } else if ThemeColors.isLowContrast(bgHex: appGroup.bgHex, fgHex: appGroup.fgHex) {
                return ("Contour Contraste Auto", Color(hex: "#FFCC00"))
            } else {
                return ("Mode Standard 120 Hz", Color(hex: "#34C759"))
            }
        }()

        return Text(labelAndColor.0)
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(labelAndColor.1.opacity(0.20)))
            .foregroundColor(labelAndColor.1)
    }

    private func statusColorForOrbState(_ state: WhisperOrbState) -> Color {
        switch state {
        case .idle:
            return Color(hex: "#34C759")
        case .listening:
            return Color(hex: "#0088FF")
        case .transcribing:
            return Color(hex: "#FF9500")
        }
    }
}
