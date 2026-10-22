import SwiftUI
import UIKit

public enum KeyboardLayer: Equatable {
    case letters
    case numbers
    case symbols
}

public enum ShiftState: Equatable {
    case lowercased
    case uppercased
    case capsLocked
}

public struct KeyboardLayoutView: View {
    @ObservedObject private var appGroup = AppGroupManager.shared

    public let onInsertText: (String) -> Void
    public let onDeleteBackward: () -> Void
    public let onDeleteLastDictatedSentence: (Int) -> Void
    public let onAdvanceToNextKeyboard: () -> Void

    @State private var currentLayer: KeyboardLayer = .letters
    @State private var shiftState: ShiftState = .uppercased
    @State private var lastShiftTapTime: Date = .distantPast
    @State private var lastSpaceTapTime: Date = .distantPast
    @State private var showQuickOrbPopover: Bool = false

    // Répétition automatique de la touche ⌫ Suppr
    @State private var isHoldingBackspace: Bool = false
    @State private var backspaceRepeatTimer: Timer? = nil

    // MARK: - Définition Intégrale des Rangées du Clavier AZERTY Français (Zéro Touche Résumée)

    private let row0QuickNumberAndAccents: [String] = [
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "é", "è", "ê", "à", "ù", "ç", "œ", "'", "-"
    ]

    private let row1LettersAZERTY: [String] = [
        "a", "z", "e", "r", "t", "y", "u", "i", "o", "p"
    ]

    private let row2LettersQSDF: [String] = [
        "q", "s", "d", "f", "g", "h", "j", "k", "l", "m"
    ]

    private let row3LettersWXCV: [String] = [
        "w", "x", "c", "v", "b", "n", ",", ".", "?", "!"
    ]

    private let row1Numbers: [String] = [
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    ]

    private let row2Numbers: [String] = [
        "-", "/", ":", ";", "(", ")", "€", "&", "@", "\""
    ]

    private let row3Numbers: [String] = [
        "«", "»", "…", ".", ",", "?", "!", "'", "+", "="
    ]

    private let row1Symbols: [String] = [
        "[", "]", "{", "}", "#", "%", "^", "*", "+", "="
    ]

    private let row2Symbols: [String] = [
        "_", "\\", "|", "~", "<", ">", "$", "£", "¥", "•"
    ]

    private let row3Symbols: [String] = [
        "«", "»", "…", ".", ",", "?", "!", "\"", "°", "§"
    ]

    public init(
        onInsertText: @escaping (String) -> Void,
        onDeleteBackward: @escaping () -> Void,
        onDeleteLastDictatedSentence: @escaping (Int) -> Void,
        onAdvanceToNextKeyboard: @escaping () -> Void
    ) {
        self.onInsertText = onInsertText
        self.onDeleteBackward = onDeleteBackward
        self.onDeleteLastDictatedSentence = onDeleteLastDictatedSentence
        self.onAdvanceToNextKeyboard = onAdvanceToNextKeyboard
    }

    private var isIPad: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    public var body: some View {
        ZStack(alignment: .top) {
            // Fond sobre du clavier iPadOS / iOS Pro
            LinearGradient(
                colors: [
                    Color(hex: "#14151B"),
                    Color(hex: "#0D0E13")
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: isIPad ? 7 : 5) {
                // Barre Supérieure (État en direct, Actions Goodnotes, et GeminiBubbleView 120 Hz)
                topVoiceControlBar

                if showQuickOrbPopover {
                    quickColorAndModelSelectorPanel
                        .transition(.move(edge: .top).combined(with: .opacity))
                } else {
                    // Rangée 0 : Chiffres & Accents Français Indispensables
                    row0FastAccentsBar

                    // Rangée 1 : AZERTY / Chiffres / Symboles
                    keyboardCharacterRow(keys: activeRow1Keys)

                    // Rangée 2 : QSDFGHJKLM / Ponctuation / Symboles
                    keyboardCharacterRow(keys: activeRow2Keys)

                    // Rangée 3 : Maj ⇧ + WXCVBN, . ? ! + Suppr ⌫ (répétition continue)
                    row3WithShiftAndBackspace

                    // Rangée 4 : 123/ABC + Globe 🌐 + Espace ("français • WhisperOrb") + Retour ⏎
                    row4BottomBar
                }
            }
            .padding(.horizontal, isIPad ? 10 : 5)
            .padding(.vertical, isIPad ? 8 : 5)
        }
    }

    // MARK: - Sélection Dynamique des Touches selon le Calque Actif

    private var activeRow1Keys: [String] {
        switch currentLayer {
        case .letters: return row1LettersAZERTY
        case .numbers: return row1Numbers
        case .symbols: return row1Symbols
        }
    }

    private var activeRow2Keys: [String] {
        switch currentLayer {
        case .letters: return row2LettersQSDF
        case .numbers: return row2Numbers
        case .symbols: return row2Symbols
        }
    }

    private var activeRow3Keys: [String] {
        switch currentLayer {
        case .letters: return row3LettersWXCV
        case .numbers: return row3Numbers
        case .symbols: return row3Symbols
        }
    }

    // MARK: - Barre Supérieure (Top Bar : État + Actions Goodnotes + Bulle Gemini 120 FPS)

    private var topVoiceControlBar: some View {
        HStack(spacing: isIPad ? 10 : 6) {
            // Pastille d'état en direct (cliquable elle aussi pour lancer/arrêter l'écoute)
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                appGroup.requestToggleRecordingFromKeyboard()
            }) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(statusIndicatorColor)
                        .frame(width: 8, height: 8)

                    Text(liveStatusTitle)
                        .font(.system(size: isIPad ? 12 : 11, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                }
                .padding(.horizontal, isIPad ? 11 : 8)
                .padding(.vertical, 7)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.08))
                        .overlay(
                            Capsule()
                                .strokeBorder(statusIndicatorColor.opacity(0.45), lineWidth: 1)
                        )
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // Bouton rapide "↶ Effacer phrase"
            Button(action: {
                triggerLightHaptic()
                let length = appGroup.lastInsertedSentenceLength
                if length > 0 {
                    onDeleteLastDictatedSentence(length)
                    appGroup.lastInsertedSentenceLength = 0
                } else {
                    onDeleteBackward()
                }
            }) {
                Text("↶ Effacer phrase")
                    .font(.system(size: isIPad ? 12 : 11, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(hex: "#FFCC00"))
                    .padding(.horizontal, isIPad ? 11 : 8)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Color(hex: "#FF9500").opacity(0.16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(Color(hex: "#FF9500").opacity(0.35), lineWidth: 0.8)
                            )
                    )
            }
            .buttonStyle(.plain)

            // Bouton rapide "↵ À la ligne"
            Button(action: {
                triggerLightHaptic()
                onInsertText("\n")
                if shiftState == .lowercased {
                    shiftState = .uppercased
                }
            }) {
                Text("↵ À la ligne")
                    .font(.system(size: isIPad ? 12 : 11, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, isIPad ? 11 : 8)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Color.white.opacity(0.10))
                    )
            }
            .buttonStyle(.plain)

            Spacer()

            // Bouton d'accès direct au mini-sélecteur de modèle/couleur (sans bloquer le tap de l'orbe)
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .light)
                generator.impactOccurred()
                withAnimation(.spring(response: 0.26, dampingFraction: 0.82)) {
                    showQuickOrbPopover.toggle()
                }
            }) {
                Text(showQuickOrbPopover ? "Fermer ✕" : "⚙️")
                    .font(.system(size: isIPad ? 13 : 12, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, showQuickOrbPopover ? 10 : 8)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(Color.white.opacity(0.14)))
            }
            .buttonStyle(.plain)

            // La Bulle GeminiBubbleView (size: 46) animée à 120 FPS dans un vrai Button natif ultra-réactif
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                appGroup.requestToggleRecordingFromKeyboard()
            }) {
                GeminiBubbleView(
                    bgHex: appGroup.bgHex,
                    fgHex: appGroup.fgHex,
                    audioLevel: CGFloat(appGroup.currentAudioLevel),
                    state: appGroup.orbState,
                    size: 46,
                    showSideTab: true,
                    onTap: nil
                )
                .padding(.leading, 8)
                .padding(.vertical, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 4)
    }

    private var liveStatusTitle: String {
        if !appGroup.isBackgroundEngineActive && appGroup.orbState == .idle {
            return "Ouvrez WhisperOrb pour activer le micro"
        }
        switch appGroup.orbState {
        case .idle:
            return "Prêt • \(appGroup.selectedModel.shortBadgeName)"
        case .listening:
            return "Écoute en cours..."
        case .transcribing:
            return "Transcription..."
        }
    }

    private var statusIndicatorColor: Color {
        if !appGroup.isBackgroundEngineActive && appGroup.orbState == .idle {
            return Color(hex: "#FF9500")
        }
        switch appGroup.orbState {
        case .idle:
            return Color(hex: "#34C759")
        case .listening:
            return Color(hex: "#0088FF")
        case .transcribing:
            return Color(hex: "#FFCC00")
        }
    }

    // MARK: - Mini-Sélecteur Rapide Intégré au Clavier (Appui Long 0,6 s sur la Bulle)

    private var quickColorAndModelSelectorPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Réglages Rapides WhisperOrb (Sans quitter Goodnotes)")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                Spacer()
                Text("Appui long 0,6 s sur la bulle")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.55))
            }

            // Sélecteur de Modèle (Éclair / Normal / Zéro Faute)
            HStack(spacing: 8) {
                ForEach(WhisperModelTier.allCases) { tier in
                    let isSelected = (appGroup.selectedModel == tier)
                    Button(action: {
                        triggerLightHaptic()
                        appGroup.selectedModel = tier
                    }) {
                        VStack(spacing: 2) {
                            Text(tier.shortBadgeName)
                                .font(.system(size: 12, weight: .bold))
                            Text("\(tier.ramUsageMB) Mo RAM • \(tier.latencyDescription)")
                                .font(.system(size: 10, weight: .regular))
                                .opacity(0.75)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(isSelected ? Color(hex: "#0088FF") : Color.white.opacity(0.08))
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Sélecteur rapide de Couleurs de l'Onde + Combos Éclipse & Perle Cristal
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    Button(action: {
                        triggerLightHaptic()
                        appGroup.bgHex = "#050507"
                        appGroup.fgHex = "#050507"
                    }) {
                        Text("🌑 Noir/Noir")
                            .font(.system(size: 11, weight: .bold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 9).fill(Color(hex: "#14141C")))
                            .overlay(RoundedRectangle(cornerRadius: 9).strokeBorder(Color.white.opacity(0.35), lineWidth: 1))
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        triggerLightHaptic()
                        appGroup.bgHex = "#F8F9FA"
                        appGroup.fgHex = "#FFFFFF"
                    }) {
                        Text("💎 Blanc/Blanc")
                            .font(.system(size: 11, weight: .bold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 9).fill(Color(hex: "#F8F9FA")))
                            .foregroundColor(Color(hex: "#0F172A"))
                    }
                    .buttonStyle(.plain)

                    ForEach(ThemeColors.orderedForegrounds) { preset in
                        Button(action: {
                            triggerLightHaptic()
                            if appGroup.bgHex == "#F8F9FA" && preset.id == "white" {
                                appGroup.fgHex = preset.hex
                            } else {
                                if ThemeColors.isWhiteOnWhite(bgHex: appGroup.bgHex, fgHex: preset.hex) == false && appGroup.bgHex == "#F8F9FA" {
                                    appGroup.bgHex = "#08080C"
                                }
                                appGroup.fgHex = preset.hex
                            }
                        }) {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 18, height: 18)
                                    .overlay(Circle().strokeBorder(Color.white.opacity(0.45), lineWidth: 1))
                                Text(preset.name.components(separatedBy: " ").first ?? preset.name)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 9).fill(Color.white.opacity(0.08)))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(hex: "#1A1B24"))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                )
        )
    }

    // MARK: - Rangée 0 : Barre Rapide Chiffres & Accents Français Indispensables

    private var row0FastAccentsBar: some View {
        HStack(spacing: isIPad ? 5 : 3) {
            ForEach(row0QuickNumberAndAccents, id: \.self) { token in
                Button(action: {
                    handleCharacterKeyTap(token)
                }) {
                    Text(displayStringForKey(token))
                        .font(.system(size: isIPad ? 15 : 13, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: isIPad ? 34 : 29)
                        .background(
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(Color(hex: "#232530"))
                                .shadow(color: Color.black.opacity(0.35), radius: 0.5, x: 0, y: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Rangées 1 & 2 : Grille de Touches Standards

    private func keyboardCharacterRow(keys: [String]) -> some View {
        HStack(spacing: isIPad ? 6 : 4) {
            ForEach(keys, id: \.self) { key in
                Button(action: {
                    handleCharacterKeyTap(key)
                }) {
                    Text(displayStringForKey(key))
                        .font(.system(size: isIPad ? 19 : 16, weight: .medium, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: isIPad ? 44 : 38)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(Color(hex: "#2C2E3B"))
                                .shadow(color: Color.black.opacity(0.45), radius: 0.8, x: 0, y: 1.2)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Rangée 3 : Touche ⇧ Maj + W X C V B N , . ? ! + Touche ⌫ Suppr (Répétition Rapide)

    private var row3WithShiftAndBackspace: some View {
        HStack(spacing: isIPad ? 6 : 4) {
            // Touche Gauche : ⇧ Maj (ou #+= / 123 en mode chiffres/symboles)
            Button(action: {
                triggerLightHaptic()
                handleLeftModifierTap()
            }) {
                Group {
                    if currentLayer == .letters {
                        Text(shiftState == .capsLocked ? "⇪" : "⇧")
                            .font(.system(size: isIPad ? 20 : 17, weight: .bold))
                    } else if currentLayer == .numbers {
                        Text("#+=")
                            .font(.system(size: isIPad ? 15 : 13, weight: .bold))
                    } else {
                        Text("123")
                            .font(.system(size: isIPad ? 15 : 13, weight: .bold))
                    }
                }
                .foregroundColor(shiftKeyForegroundColor)
                .frame(width: isIPad ? 64 : 42, height: isIPad ? 44 : 38)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(shiftKeyBackgroundColor)
                        .shadow(color: Color.black.opacity(0.45), radius: 0.8, x: 0, y: 1.2)
                )
            }
            .buttonStyle(.plain)

            // Touches Centrales : W, X, C, V, B, N, ,, ., ?, !
            ForEach(activeRow3Keys, id: \.self) { key in
                Button(action: {
                    handleCharacterKeyTap(key)
                }) {
                    Text(displayStringForKey(key))
                        .font(.system(size: isIPad ? 18 : 15, weight: .medium, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: isIPad ? 44 : 38)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(Color(hex: "#2C2E3B"))
                                .shadow(color: Color.black.opacity(0.45), radius: 0.8, x: 0, y: 1.2)
                        )
                }
                .buttonStyle(.plain)
            }

            // Touche Droite : ⌫ Suppr avec répétition rapide automatique au maintien
            Text("⌫")
                .font(.system(size: isIPad ? 20 : 17, weight: .bold))
                .foregroundColor(.white)
                .frame(width: isIPad ? 64 : 42, height: isIPad ? 44 : 38)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isHoldingBackspace ? Color(hex: "#4B4E63") : Color(hex: "#1E202B"))
                        .shadow(color: Color.black.opacity(0.45), radius: 0.8, x: 0, y: 1.2)
                )
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            if !isHoldingBackspace {
                                startBackspaceRepeat()
                            }
                        }
                        .onEnded { _ in
                            stopBackspaceRepeat()
                        }
                )
        }
    }

    // MARK: - Rangée 4 : Barre Inférieure (123/ABC + Globe 🌐 + Espace + Retour ⏎)

    private var row4BottomBar: some View {
        HStack(spacing: isIPad ? 8 : 5) {
            // Touche 123 / ABC
            Button(action: {
                triggerLightHaptic()
                if currentLayer == .letters {
                    currentLayer = .numbers
                } else {
                    currentLayer = .letters
                }
            }) {
                Text(currentLayer == .letters ? "123" : "ABC")
                    .font(.system(size: isIPad ? 16 : 14, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 76 : 50, height: isIPad ? 44 : 39)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color(hex: "#1E202B"))
                    )
            }
            .buttonStyle(.plain)

            // Touche 🌐 Globe (retour en 1 clic au clavier Apple d'origine)
            Button(action: {
                triggerLightHaptic()
                onAdvanceToNextKeyboard()
            }) {
                Text("🌐")
                    .font(.system(size: isIPad ? 19 : 16))
                    .frame(width: isIPad ? 58 : 42, height: isIPad ? 44 : 39)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color(hex: "#1E202B"))
                    )
            }
            .buttonStyle(.plain)

            // Grande Barre d'Espace ("français • WhisperOrb", double-tap insère ". ")
            Button(action: {
                handleSpacebarTap()
            }) {
                HStack(spacing: 6) {
                    Text("français • WhisperOrb")
                        .font(.system(size: isIPad ? 15 : 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color.white.opacity(0.82))
                }
                .frame(maxWidth: .infinity)
                .frame(height: isIPad ? 44 : 39)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color(hex: "#2C2E3B"))
                        .shadow(color: Color.black.opacity(0.45), radius: 0.8, x: 0, y: 1.2)
                )
            }
            .buttonStyle(.plain)

            // Touche Retour ⏎ (Bleue, insère un saut de ligne \n)
            Button(action: {
                triggerLightHaptic()
                onInsertText("\n")
                if shiftState == .lowercased {
                    shiftState = .uppercased
                }
            }) {
                Text("Retour ⏎")
                    .font(.system(size: isIPad ? 16 : 14, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 110 : 82, height: isIPad ? 44 : 39)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color(hex: "#0088FF"))
                            .shadow(color: Color(hex: "#0088FF").opacity(0.35), radius: 2, x: 0, y: 1.5)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Logique Métier des Touches, Casse (Shift / CapsLock) & Répétition Suppr

    private func displayStringForKey(_ key: String) -> String {
        guard currentLayer == .letters else { return key }
        if key.count == 1 && key.first?.isLetter == true {
            return shiftState == .lowercased ? key.lowercased() : key.uppercased()
        }
        if key == "œ" {
            return shiftState == .lowercased ? "œ" : "Œ"
        }
        return key
    }

    private func handleCharacterKeyTap(_ key: String) {
        triggerLightHaptic()
        let textToInsert = displayStringForKey(key)
        onInsertText(textToInsert)

        if currentLayer == .letters && shiftState == .uppercased {
            shiftState = .lowercased
        }
    }

    private func handleLeftModifierTap() {
        switch currentLayer {
        case .letters:
            let now = Date()
            if now.timeIntervalSince(lastShiftTapTime) < 0.35 {
                shiftState = .capsLocked
            } else {
                switch shiftState {
                case .lowercased:
                    shiftState = .uppercased
                case .uppercased, .capsLocked:
                    shiftState = .lowercased
                }
            }
            lastShiftTapTime = now
        case .numbers:
            currentLayer = .symbols
        case .symbols:
            currentLayer = .numbers
        }
    }

    private func handleSpacebarTap() {
        triggerLightHaptic()
        let now = Date()
        if now.timeIntervalSince(lastSpaceTapTime) < 0.30 {
            onDeleteBackward()
            onInsertText(". ")
            if shiftState == .lowercased {
                shiftState = .uppercased
            }
            lastSpaceTapTime = .distantPast
        } else {
            onInsertText(" ")
            lastSpaceTapTime = now
        }
    }

    private func startBackspaceRepeat() {
        isHoldingBackspace = true
        triggerLightHaptic()
        onDeleteBackward()

        backspaceRepeatTimer?.invalidate()
        let timer = Timer(timeInterval: 0.075, repeats: true) { _ in
            onDeleteBackward()
        }
        RunLoop.main.add(timer, forMode: .common)
        backspaceRepeatTimer = timer
    }

    private func stopBackspaceRepeat() {
        isHoldingBackspace = false
        backspaceRepeatTimer?.invalidate()
        backspaceRepeatTimer = nil
    }

    private var shiftKeyBackgroundColor: Color {
        if currentLayer == .letters && (shiftState == .uppercased || shiftState == .capsLocked) {
            return Color.white
        }
        return Color(hex: "#1E202B")
    }

    private var shiftKeyForegroundColor: Color {
        if currentLayer == .letters && (shiftState == .uppercased || shiftState == .capsLocked) {
            return Color(hex: "#08080C")
        }
        return Color.white
    }

    private func triggerLightHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }
}
