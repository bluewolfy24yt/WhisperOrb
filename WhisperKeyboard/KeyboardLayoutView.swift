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

    // État temps réel de la touche pressée (Loupe Apple + Appui long variantes/accents)
    @State private var pressedKeyID: String? = nil
    @State private var isShowingLongPressVariants: Bool = false
    @State private var activeVariants: [String] = []
    @State private var selectedVariantIndex: Int = 0
    @State private var longPressTimer: Timer? = nil

    // Répétition automatique de la touche ⌫ Suppr
    @State private var isHoldingBackspace: Bool = false
    @State private var backspaceRepeatTimer: Timer? = nil

    // MARK: - Disposition Officielle Apple iOS AZERTY Français (4 Rangées Natives)

    private let row1LettersAZERTY: [String] = [
        "a", "z", "e", "r", "t", "y", "u", "i", "o", "p"
    ]

    private let row2LettersQSDF: [String] = [
        "q", "s", "d", "f", "g", "h", "j", "k", "l", "m"
    ]

    // Rangée 3 : W X C V B N + apostrophe directe ' (appui long sur ' donne , . ? ! « »)
    private let row3LettersWXCV: [String] = [
        "w", "x", "c", "v", "b", "n", "'"
    ]

    private let row1Numbers: [String] = [
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    ]

    private let row2Numbers: [String] = [
        "-", "/", ":", ";", "(", ")", "€", "&", "@", "\""
    ]

    private let row3Numbers: [String] = [
        ".", ",", "?", "!", "'"
    ]

    private let row1Symbols: [String] = [
        "[", "]", "{", "}", "#", "%", "^", "*", "+", "="
    ]

    private let row2Symbols: [String] = [
        "_", "\\", "|", "~", "<", ">", "$", "£", "¥", "•"
    ]

    private let row3Symbols: [String] = [
        ".", ",", "?", "!", "'"
    ]

    // MARK: - Table Officielle des Accents & Variantes (Appui Long + Glisser Apple iOS)

    private func variantsForKey(_ rawKey: String) -> [String] {
        let base = rawKey.lowercased()
        let list: [String]
        switch base {
        case "e":
            list = ["é", "è", "ê", "ë", "ę", "ė", "ē"]
        case "a":
            list = ["à", "â", "æ", "á", "ä", "ã", "å", "ā"]
        case "u":
            list = ["ù", "û", "ü", "ú", "ū"]
        case "i":
            list = ["î", "ï", "ì", "í", "į", "ī"]
        case "o":
            list = ["ô", "œ", "ö", "ò", "ó", "õ", "ø", "ō"]
        case "c":
            list = ["ç", "ć", "č"]
        case "y":
            list = ["ÿ"]
        case "n":
            list = ["ñ", "ń"]
        case "s":
            list = ["ß", "ś", "š"]
        case "'":
            return ["'", ",", ".", "?", "!", "\"", "«", "»"]
        case ".":
            return [".", "…", ",", "?", "!", "'", "-"]
        case ",":
            return [",", ".", "?", "!", "'", ";", ":"]
        case "?":
            return ["?", "¿", "!"]
        case "!":
            return ["!", "¡", "?"]
        case "-":
            return ["-", "–", "—", "•"]
        case "€":
            return ["€", "$", "£", "¥"]
        case "0":
            return ["0", "°", "∅"]
        default:
            return []
        }

        if currentLayer == .letters && shiftState != .lowercased {
            return list.map { $0.uppercased() }
        }
        return list
    }

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
            // Fond exact du clavier sombre natif Apple iOS (#1C1C1E)
            Color(hex: "#1C1C1E")
                .ignoresSafeArea()

            if appGroup.isKeyboardCollapsed {
                collapsedVoiceBarView
                    .padding(.horizontal, isIPad ? 12 : 8)
                    .padding(.vertical, isIPad ? 8 : 6)
            } else {
                VStack(spacing: isIPad ? 9 : 8) {
                    // Barre Supérieure Vocale (Réduire, État, Effacer, Réglages & Orbe 3D)
                    topVoiceControlBar

                    if showQuickOrbPopover {
                        quickColorAndModelSelectorPanel
                            .transition(.opacity)
                    } else {
                        // Rangée 1 : A Z E R T Y U I O P (zIndex supérieur pour que les bulles ne soient jamais masquées)
                        keyboardCharacterRow(keys: activeRow1Keys, rowOrder: 1)
                            .zIndex(pressedRowIndex == 1 ? 40 : 1)

                        // Rangée 2 : Q S D F G H J K L M
                        keyboardCharacterRow(keys: activeRow2Keys, rowOrder: 2)
                            .zIndex(pressedRowIndex == 2 ? 40 : 2)

                        // Rangée 3 : ⇧ Maj + W X C V B N ' + ⌫ Suppr
                        row3WithShiftAndBackspace
                            .zIndex(pressedRowIndex == 3 ? 40 : 3)

                        // Rangée 4 : 123 + 🌐 + espace + retour
                        row4BottomBar
                            .zIndex(0)
                    }
                }
                .padding(.horizontal, isIPad ? 8 : 3)
                .padding(.top, isIPad ? 6 : 4)
                .padding(.bottom, isIPad ? 6 : 3)
            }
        }
    }

    private var pressedRowIndex: Int {
        guard let key = pressedKeyID else { return 0 }
        if activeRow1Keys.contains(key) { return 1 }
        if activeRow2Keys.contains(key) { return 2 }
        if activeRow3Keys.contains(key) { return 3 }
        return 0
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

    // MARK: - Mode Clavier Réduit (Barre Vocale Flottante Compacte 58 pt)

    private var collapsedVoiceBarView: some View {
        HStack(spacing: isIPad ? 10 : 6) {
            // Bouton pour ré-afficher le clavier complet AZERTY
            Button(action: {
                triggerLightHaptic()
                withAnimation(.easeInOut(duration: 0.2)) {
                    appGroup.isKeyboardCollapsed = false
                }
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "keyboard.chevron.compact.up")
                        .font(.system(size: isIPad ? 14 : 13, weight: .semibold))
                    Text("Clavier")
                        .font(.system(size: isIPad ? 13 : 12, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, isIPad ? 12 : 9)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                        .fill(Color(hex: "#3A3A3C"))
                        .shadow(color: Color.black.opacity(0.4), radius: 0.5, x: 0, y: 1)
                )
            }
            .buttonStyle(.plain)

            // Touche 🌐 Globe
            Button(action: {
                triggerLightHaptic()
                onAdvanceToNextKeyboard()
            }) {
                Image(systemName: "globe")
                    .font(.system(size: isIPad ? 16 : 15, weight: .regular))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 38 : 34, height: isIPad ? 38 : 34)
                    .background(
                        RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                            .fill(Color(hex: "#2C2C2E"))
                    )
            }
            .buttonStyle(.plain)

            // Pastille centrale d'état vocal
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                appGroup.requestToggleRecordingFromKeyboard()
            }) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(statusIndicatorColor)
                        .frame(width: 7, height: 7)

                    Text(collapsedStatusTitle)
                        .font(.system(size: isIPad ? 13 : 12, weight: .medium))
                        .foregroundColor(.white)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(Color(hex: "#2C2C2E"))
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // Bouton rapide Annuler dernière phrase
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
                Image(systemName: "arrow.uturn.backward")
                    .font(.system(size: isIPad ? 14 : 13, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 38 : 34, height: isIPad ? 38 : 34)
                    .background(
                        RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                            .fill(Color(hex: "#2C2C2E"))
                    )
            }
            .buttonStyle(.plain)

            // Touche ⌫ Suppr avec maintien appuyé
            Image(systemName: isHoldingBackspace ? "delete.left.fill" : "delete.left")
                .font(.system(size: isIPad ? 16 : 15, weight: .regular))
                .foregroundColor(.white)
                .frame(width: isIPad ? 38 : 34, height: isIPad ? 38 : 34)
                .background(
                    RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                        .fill(isHoldingBackspace ? Color(hex: "#48484A") : Color(hex: "#2C2C2E"))
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

            // L'Orbe 3D pur
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
                    size: isIPad ? 44 : 40,
                    showSideTab: false,
                    onTap: nil
                )
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Barre Supérieure Vocale (Style Apple iOS Dictation Bar)

    private var topVoiceControlBar: some View {
        HStack(spacing: isIPad ? 8 : 6) {
            // Bouton Réduire le clavier
            Button(action: {
                triggerLightHaptic()
                showQuickOrbPopover = false
                withAnimation(.easeInOut(duration: 0.2)) {
                    appGroup.isKeyboardCollapsed = true
                }
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "keyboard.chevron.compact.down")
                        .font(.system(size: isIPad ? 13 : 12, weight: .semibold))
                    if isIPad {
                        Text("Réduire")
                            .font(.system(size: 12, weight: .medium))
                    }
                }
                .foregroundColor(Color.white.opacity(0.88))
                .frame(height: 32)
                .padding(.horizontal, isIPad ? 10 : 9)
                .background(
                    Capsule()
                        .fill(Color(hex: "#2C2C2E"))
                )
            }
            .buttonStyle(.plain)

            // Pastille d'état en direct
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                appGroup.requestToggleRecordingFromKeyboard()
            }) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(statusIndicatorColor)
                        .frame(width: 7, height: 7)

                    Text(liveStatusTitle)
                        .font(.system(size: isIPad ? 12 : 11, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.92))
                        .lineLimit(1)
                }
                .frame(height: 32)
                .padding(.horizontal, isIPad ? 12 : 10)
                .background(
                    Capsule()
                        .fill(Color(hex: "#2C2C2E"))
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // Bouton Annuler dernière phrase dictée
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
                HStack(spacing: 4) {
                    Image(systemName: "arrow.uturn.backward")
                        .font(.system(size: 11, weight: .semibold))
                    Text(isIPad ? "Annuler phrase" : "Annuler")
                        .font(.system(size: isIPad ? 12 : 11, weight: .medium))
                }
                .foregroundColor(Color.white.opacity(0.88))
                .frame(height: 32)
                .padding(.horizontal, isIPad ? 11 : 9)
                .background(
                    Capsule()
                        .fill(Color(hex: "#2C2C2E"))
                )
            }
            .buttonStyle(.plain)

            Spacer(minLength: 2)

            // Bouton Réglages rapides (Modèle / Couleur)
            Button(action: {
                triggerLightHaptic()
                withAnimation(.easeInOut(duration: 0.2)) {
                    showQuickOrbPopover.toggle()
                }
            }) {
                Image(systemName: showQuickOrbPopover ? "xmark" : "slider.horizontal.3")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Color.white.opacity(0.88))
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(Color(hex: "#2C2C2E")))
            }
            .buttonStyle(.plain)

            // L'Orbe 3D pur
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
                    size: isIPad ? 42 : 38,
                    showSideTab: false,
                    onTap: nil
                )
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 3)
    }

    private var collapsedStatusTitle: String {
        if !appGroup.isBackgroundEngineActive && appGroup.orbState == .idle {
            return "Ouvrir WhisperOrb (Micro)"
        }
        switch appGroup.orbState {
        case .idle:
            return "Dicter • \(appGroup.selectedModel.shortBadgeName)"
        case .listening:
            return "Écoute en cours..."
        case .transcribing:
            return "Transcription..."
        }
    }

    private var liveStatusTitle: String {
        if !appGroup.isBackgroundEngineActive && appGroup.orbState == .idle {
            return isIPad ? "Activez le micro dans WhisperOrb" : "Micro en veille"
        }
        switch appGroup.orbState {
        case .idle:
            return appGroup.selectedModel.shortBadgeName
        case .listening:
            return "Écoute..."
        case .transcribing:
            return "Transcription..."
        }
    }

    private var statusIndicatorColor: Color {
        if !appGroup.isBackgroundEngineActive && appGroup.orbState == .idle {
            return Color(hex: "#FF9F0A")
        }
        switch appGroup.orbState {
        case .idle:
            return Color(hex: "#30D158")
        case .listening:
            return Color(hex: "#0A84FF")
        case .transcribing:
            return Color(hex: "#FFD60A")
        }
    }

    // MARK: - Panneau Rapide de Modèle & Couleur

    private var quickColorAndModelSelectorPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Modèle IA & Style de l'Orbe")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Color.white.opacity(0.75))

            HStack(spacing: 8) {
                ForEach(WhisperModelTier.allCases) { tier in
                    let isSelected = (appGroup.selectedModel == tier)
                    Button(action: {
                        triggerLightHaptic()
                        appGroup.selectedModel = tier
                    }) {
                        VStack(spacing: 2) {
                            Text(tier.shortBadgeName)
                                .font(.system(size: 12, weight: .semibold))
                            Text(tier.latencyDescription)
                                .font(.system(size: 10, weight: .regular))
                                .opacity(0.75)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                                .fill(isSelected ? Color(hex: "#0A84FF") : Color(hex: "#2C2C2E"))
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button(action: {
                        triggerLightHaptic()
                        appGroup.bgHex = "#050507"
                        appGroup.fgHex = "#050507"
                    }) {
                        Text("Éclipse")
                            .font(.system(size: 11, weight: .medium))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#2C2C2E")))
                            .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        triggerLightHaptic()
                        appGroup.bgHex = "#F8F9FA"
                        appGroup.fgHex = "#FFFFFF"
                    }) {
                        Text("Perle")
                            .font(.system(size: 11, weight: .medium))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#F8F9FA")))
                            .foregroundColor(Color(hex: "#1C1C1E"))
                    }
                    .buttonStyle(.plain)

                    ForEach(ThemeColors.orderedForegrounds) { preset in
                        Button(action: {
                            triggerLightHaptic()
                            if appGroup.bgHex == "#F8F9FA" && preset.id != "white" {
                                appGroup.bgHex = "#08080C"
                            }
                            appGroup.fgHex = preset.hex
                        }) {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 14, height: 14)
                                Text(preset.name.components(separatedBy: " ").first ?? preset.name)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: "#2C2C2E")))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(hex: "#1C1C1E"))
        )
    }

    // MARK: - Rangées 1 & 2 : Grille de Touches Standards Apple iOS (Avec Loupe + Appui Long Accents)

    private func keyboardCharacterRow(keys: [String], rowOrder: Int) -> some View {
        HStack(spacing: isIPad ? 7 : 6) {
            ForEach(Array(keys.enumerated()), id: \.element) { idx, key in
                appleInteractiveCharacterKey(
                    key: key,
                    columnIndex: idx,
                    totalColumns: keys.count
                )
            }
        }
    }

    // MARK: - Rangée 3 : ⇧ Maj + W X C V B N ' + ⌫ Suppr (Proportions Exactes Apple iOS)

    private var row3WithShiftAndBackspace: some View {
        HStack(spacing: isIPad ? 8 : 6) {
            // Touche Gauche : ⇧ Maj (avec vrais SF Symbols Apple shift / shift.fill / capslock.fill)
            Button(action: {
                triggerLightHaptic()
                handleLeftModifierTap()
            }) {
                Group {
                    if currentLayer == .letters {
                        Image(systemName: shiftSymbolName)
                            .font(.system(size: isIPad ? 18 : 16, weight: .semibold))
                    } else if currentLayer == .numbers {
                        Text("#+=")
                            .font(.system(size: isIPad ? 15 : 13, weight: .medium))
                    } else {
                        Text("123")
                            .font(.system(size: isIPad ? 15 : 13, weight: .medium))
                    }
                }
                .foregroundColor(shiftKeyForegroundColor)
                .frame(width: isIPad ? 58 : 42, height: isIPad ? 45 : 42)
                .background(
                    RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                        .fill(shiftKeyBackgroundColor)
                        .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
                )
            }
            .buttonStyle(.plain)

            Spacer(minLength: isIPad ? 8 : 3)

            // Touches Centrales : W, X, C, V, B, N, ' (ou . , ? ! ' en mode 123/#+=)
            HStack(spacing: isIPad ? 7 : 6) {
                ForEach(Array(activeRow3Keys.enumerated()), id: \.element) { idx, key in
                    appleInteractiveCharacterKey(
                        key: key,
                        columnIndex: idx + 1,
                        totalColumns: activeRow3Keys.count + 2
                    )
                }
            }

            Spacer(minLength: isIPad ? 8 : 3)

            // Touche Droite : ⌫ Suppr avec SF Symbol natif delete.left et répétition continue
            Image(systemName: isHoldingBackspace ? "delete.left.fill" : "delete.left")
                .font(.system(size: isIPad ? 18 : 16, weight: .regular))
                .foregroundColor(.white)
                .frame(width: isIPad ? 58 : 42, height: isIPad ? 45 : 42)
                .background(
                    RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                        .fill(isHoldingBackspace ? Color(hex: "#636366") : Color(hex: "#48484A"))
                        .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
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

    // MARK: - Touche Interactive Apple iOS (Loupe d'Aperçu au Toucher + Barre d'Accents à l'Appui Long)

    @ViewBuilder
    private func appleInteractiveCharacterKey(key: String, columnIndex: Int, totalColumns: Int) -> some View {
        let isThisKeyPressed = (pressedKeyID == key)
        let displayedChar = displayStringForKey(key)

        Text(displayedChar)
            .font(.system(size: isIPad ? 22 : 21, weight: .regular))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: isIPad ? 45 : 42)
            .background(
                RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                    .fill(isThisKeyPressed ? Color(hex: "#636366") : Color(hex: "#3A3A3C"))
                    .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
            )
            .overlay(alignment: .top) {
                if isThisKeyPressed {
                    if isShowingLongPressVariants && !activeVariants.isEmpty {
                        appleLongPressVariantsCallout(
                            variants: activeVariants,
                            selectedIndex: selectedVariantIndex,
                            columnIndex: columnIndex,
                            totalColumns: totalColumns
                        )
                        .offset(y: isIPad ? -58 : -54)
                    } else {
                        appleSingleKeyMagnifierCallout(label: displayedChar)
                            .offset(y: isIPad ? -54 : -50)
                    }
                }
            }
            .zIndex(isThisKeyPressed ? 100 : 1)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if pressedKeyID != key {
                            beginKeyPress(key)
                        } else if isShowingLongPressVariants && !activeVariants.isEmpty {
                            let stepWidth: CGFloat = isIPad ? 34.0 : 28.0
                            let deltaSteps = Int(round(value.translation.width / stepWidth))
                            let newIdx = max(0, min(activeVariants.count - 1, deltaSteps))
                            if newIdx != selectedVariantIndex {
                                selectedVariantIndex = newIdx
                                let selectionFeedback = UISelectionFeedbackGenerator()
                                selectionFeedback.selectionChanged()
                            }
                        }
                    }
                    .onEnded { _ in
                        endKeyPress(key)
                    }
            )
    }

    /// Loupe d'aperçu Apple iOS qui s'affiche au-dessus du doigt dès qu'on touche une lettre
    private func appleSingleKeyMagnifierCallout(label: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color(hex: "#545458"))
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.18), lineWidth: 0.7)
                )
                .shadow(color: Color.black.opacity(0.55), radius: 5, x: 0, y: 3)

            Text(label)
                .font(.system(size: isIPad ? 30 : 28, weight: .regular))
                .foregroundColor(.white)
        }
        .frame(width: isIPad ? 54 : 46, height: isIPad ? 54 : 50)
        .allowsHitTesting(false)
    }

    /// Barre flottante d'accents Apple iOS à l'appui long (avec sélection par glissement horizontal)
    private func appleLongPressVariantsCallout(
        variants: [String],
        selectedIndex: Int,
        columnIndex: Int,
        totalColumns: Int
    ) -> some View {
        let itemWidth: CGFloat = isIPad ? 36 : 31
        let itemHeight: CGFloat = isIPad ? 44 : 40
        let totalWidth = CGFloat(variants.count) * itemWidth + 10

        // Décale intelligemment la bulle vers l'intérieur de l'écran si la touche est sur le bord gauche ou droit
        let horizontalShift: CGFloat = {
            if columnIndex <= 1 {
                return max(0, (totalWidth - 40) * 0.45)
            } else if columnIndex >= totalColumns - 2 {
                return -max(0, (totalWidth - 40) * 0.45)
            }
            return 0
        }()

        return HStack(spacing: 2) {
            ForEach(Array(variants.enumerated()), id: \.offset) { idx, variant in
                let isSelected = (idx == selectedIndex)
                Text(variant)
                    .font(.system(size: isIPad ? 22 : 20, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(.white)
                    .frame(width: itemWidth, height: itemHeight)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(isSelected ? Color(hex: "#0A84FF") : Color.clear)
                    )
            }
        }
        .padding(.horizontal, 5)
        .padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(hex: "#545458"))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.20), lineWidth: 0.8)
                )
                .shadow(color: Color.black.opacity(0.60), radius: 7, x: 0, y: 3)
        )
        .offset(x: horizontalShift)
        .fixedSize()
        .allowsHitTesting(false)
    }

    private func beginKeyPress(_ key: String) {
        longPressTimer?.invalidate()
        pressedKeyID = key
        isShowingLongPressVariants = false
        selectedVariantIndex = 0
        triggerLightHaptic()

        let candidates = variantsForKey(key)
        activeVariants = candidates

        guard !candidates.isEmpty else { return }

        let timer = Timer(timeInterval: 0.24, repeats: false) { _ in
            if pressedKeyID == key {
                isShowingLongPressVariants = true
                selectedVariantIndex = 0
                let selectionFeedback = UISelectionFeedbackGenerator()
                selectionFeedback.selectionChanged()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        longPressTimer = timer
    }

    private func endKeyPress(_ key: String) {
        longPressTimer?.invalidate()
        longPressTimer = nil

        let textToInsert: String
        if isShowingLongPressVariants && !activeVariants.isEmpty {
            let safeIdx = max(0, min(activeVariants.count - 1, selectedVariantIndex))
            textToInsert = activeVariants[safeIdx]
        } else {
            textToInsert = displayStringForKey(key)
        }

        pressedKeyID = nil
        isShowingLongPressVariants = false
        activeVariants = []
        selectedVariantIndex = 0

        onInsertText(textToInsert)

        if currentLayer == .letters && shiftState == .uppercased {
            shiftState = .lowercased
        }
    }

    // MARK: - Rangée 4 : Barre Inférieure Native Apple iOS (123 + 🌐 + espace + retour)

    private var row4BottomBar: some View {
        HStack(spacing: isIPad ? 7 : 6) {
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
                    .font(.system(size: isIPad ? 16 : 15, weight: .regular))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 68 : 44, height: isIPad ? 45 : 42)
                    .background(
                        RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                            .fill(Color(hex: "#48484A"))
                            .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
                    )
            }
            .buttonStyle(.plain)

            // Touche 🌐 Globe (SF Symbol natif Apple)
            Button(action: {
                triggerLightHaptic()
                onAdvanceToNextKeyboard()
            }) {
                Image(systemName: "globe")
                    .font(.system(size: isIPad ? 18 : 17, weight: .regular))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 52 : 42, height: isIPad ? 45 : 42)
                    .background(
                        RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                            .fill(Color(hex: "#48484A"))
                            .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
                    )
            }
            .buttonStyle(.plain)

            // Barre d'Espace Apple ("espace", double-tap insère ". ")
            Button(action: {
                handleSpacebarTap()
            }) {
                Text("espace")
                    .font(.system(size: isIPad ? 16 : 15, weight: .regular))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: isIPad ? 45 : 42)
                    .background(
                        RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                            .fill(Color(hex: "#3A3A3C"))
                            .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
                    )
            }
            .buttonStyle(.plain)

            // Touche Retour ("retour", conforme au clavier français Apple iOS)
            Button(action: {
                triggerLightHaptic()
                onInsertText("\n")
                if shiftState == .lowercased {
                    shiftState = .uppercased
                }
            }) {
                Text("retour")
                    .font(.system(size: isIPad ? 16 : 15, weight: .regular))
                    .foregroundColor(.white)
                    .frame(width: isIPad ? 102 : 88, height: isIPad ? 45 : 42)
                    .background(
                        RoundedRectangle(cornerRadius: 5.0, style: .continuous)
                            .fill(Color(hex: "#48484A"))
                            .shadow(color: Color.black.opacity(0.45), radius: 0.4, x: 0, y: 1.0)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Logique Métier des Touches, Casse (Shift / CapsLock) & Répétition Suppr

    private var shiftSymbolName: String {
        switch shiftState {
        case .lowercased:
            return "shift"
        case .uppercased:
            return "shift.fill"
        case .capsLocked:
            return "capslock.fill"
        }
    }

    private func displayStringForKey(_ key: String) -> String {
        guard currentLayer == .letters else { return key }
        if key.count == 1 && key.first?.isLetter == true {
            return shiftState == .lowercased ? key.lowercased() : key.uppercased()
        }
        return key
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
        return Color(hex: "#48484A")
    }

    private var shiftKeyForegroundColor: Color {
        if currentLayer == .letters && (shiftState == .uppercased || shiftState == .capsLocked) {
            return Color.black
        }
        return Color.white
    }

    private func triggerLightHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }
}
