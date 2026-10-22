import SwiftUI

public enum WhisperOrbState: String, Codable, CaseIterable {
    case idle
    case listening
    case transcribing

    public var statusTitle: String {
        switch self {
        case .idle:
            return "Au repos"
        case .listening:
            return "Écoute en cours..."
        case .transcribing:
            return "Transcription..."
        }
    }
}

public struct GeminiBubbleView: View {
    @Environment(\.scenePhase) private var scenePhase

    public let bgHex: String
    public let fgHex: String
    public let audioLevel: CGFloat
    public let state: WhisperOrbState
    public var size: CGFloat = 48
    public var showSideTab: Bool = false
    public var onTap: (() -> Void)? = nil

    public init(
        bgHex: String,
        fgHex: String,
        audioLevel: CGFloat,
        state: WhisperOrbState,
        size: CGFloat = 48,
        showSideTab: Bool = false,
        onTap: (() -> Void)? = nil
    ) {
        self.bgHex = bgHex
        self.fgHex = fgHex
        self.audioLevel = audioLevel
        self.state = state
        self.size = size
        self.showSideTab = showSideTab
        self.onTap = onTap
    }

    private var isBlackStealth: Bool {
        ThemeColors.isBlackOnBlack(bgHex: bgHex, fgHex: fgHex)
    }

    private var isWhiteCrystal: Bool {
        ThemeColors.isWhiteOnWhite(bgHex: bgHex, fgHex: fgHex)
    }

    private var isLowContrastPair: Bool {
        !isBlackStealth && !isWhiteCrystal && ThemeColors.isLowContrast(bgHex: bgHex, fgHex: fgHex)
    }

    private var targetFrameInterval: Double {
        switch state {
        case .idle:
            return 1.0 / 24.0
        case .listening:
            return 1.0 / 60.0
        case .transcribing:
            return 1.0 / 24.0
        }
    }

    public var body: some View {
        let isPaused = (scenePhase == .background || scenePhase == .inactive)
        let animatedOrb = TimelineView(.animation(minimumInterval: targetFrameInterval, paused: isPaused)) { timeline in
            let phase = timeline.date.timeIntervalSinceReferenceDate
            let effectiveLevel = computeEffectiveLevel(phase: phase)

            ZStack(alignment: .center) {
                // Sphère principale 120 Hz pure (sans rectangle gris arrière)
                orbSphere(phase: phase, effectiveLevel: effectiveLevel)
                    .frame(width: size, height: size)
                    .shadow(
                        color: state == .listening
                            ? Color(hex: fgHex).opacity(0.42)
                            : Color.black.opacity(isWhiteCrystal ? 0.22 : 0.55),
                        radius: state == .listening ? size * 0.18 : size * 0.13,
                        x: 0,
                        y: state == .listening ? 0 : size * 0.05
                    )
            }
        }
        .contentShape(Rectangle())

        if let onTap = onTap {
            animatedOrb
                .onTapGesture {
                    onTap()
                }
        } else {
            animatedOrb
        }
    }

    // MARK: - Calcul Dynamique 120 Hz de l'Amplitude de l'Onde

    private func computeEffectiveLevel(phase: TimeInterval) -> Double {
        switch state {
        case .idle:
            return 0.08 + 0.05 * sin(phase * 2.2)
        case .listening:
            let clampedAudio = min(1.0, max(0.12, Double(audioLevel)))
            return min(1.0, clampedAudio + 0.08 * sin(phase * 8.0))
        case .transcribing:
            return 0.45 + 0.25 * sin(phase * 12.0)
        }
    }

    // MARK: - Languette d'Accroche Latérale (Style Gemini Live)

    private var sideDockingTab: some View {
        RoundedRectangle(cornerRadius: size * 0.08, style: .continuous)
            .fill(
                LinearGradient(
                    colors: [
                        Color(hex: "#2B2C30").opacity(0.92),
                        Color(hex: "#1E1F23").opacity(0.96)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .overlay(
                RoundedRectangle(cornerRadius: size * 0.08, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.8)
            )
            .frame(width: size * 0.58, height: size * 0.66)
    }

    // MARK: - Rendu Complet de la Sphère

    @ViewBuilder
    private func orbSphere(phase: TimeInterval, effectiveLevel: Double) -> some View {
        let baseBgColor: Color = {
            if isBlackStealth {
                return Color(hex: "#050507")
            } else if isWhiteCrystal {
                return Color(hex: "#F8F9FA")
            } else {
                return Color(hex: bgHex)
            }
        }()

        let primaryFgColor = Color(hex: fgHex)

        ZStack {
            // 1. Fond mat profond de la sphère
            Circle()
                .fill(baseBgColor)

            // 2. Rendu de l'onde selon le mode optique actif
            if isBlackStealth {
                blackOnBlackEclipseLayer(phase: phase, effectiveLevel: effectiveLevel)
            } else if isWhiteCrystal {
                whiteOnWhiteCrystalLayer(phase: phase, effectiveLevel: effectiveLevel)
            } else {
                standardAndLowContrastLayer(
                    phase: phase,
                    effectiveLevel: effectiveLevel,
                    fgColor: primaryFgColor,
                    bgColor: baseBgColor
                )
            }

            // 3. Ombrage hémisphérique supérieur pour fondre le haut de la sphère (fidèle à la référence Gemini Live)
            if !isWhiteCrystal {
                Circle()
                    .fill(
                        LinearGradient(
                            stops: [
                                .init(color: baseBgColor.opacity(0.96), location: 0.0),
                                .init(color: baseBgColor.opacity(0.72), location: max(0.18, 0.44 - effectiveLevel * 0.22)),
                                .init(color: Color.clear, location: min(0.90, 0.78 - effectiveLevel * 0.15))
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .blendMode(.normal)
            }

            // 4. Bordure 3D / Rotation Orbitale pendant .transcribing
            orbPerimeterBorder(phase: phase, fgColor: primaryFgColor)
        }
        .clipShape(Circle())
    }

    // MARK: - Mode Standard & Mode Faible Contraste (Reproduction Exacte de l'Orbe Gemini Live)

    @ViewBuilder
    private func standardAndLowContrastLayer(
        phase: TimeInterval,
        effectiveLevel: Double,
        fgColor: Color,
        bgColor: Color
    ) -> some View {
        let centerShiftX = 0.5 + 0.08 * sin(phase * 3.5)
        let centerShiftY = 0.94 - effectiveLevel * 0.32
        let secondaryShiftX = 0.44 + 0.07 * cos(phase * 4.2)
        let secondaryShiftY = 0.88 - effectiveLevel * 0.28

        ZStack {
            // Halo atmosphérique principal ancré en bas
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(stops: [
                            .init(color: fgColor.opacity(0.95), location: 0.0),
                            .init(color: fgColor.opacity(0.75), location: 0.28),
                            .init(color: fgColor.opacity(0.55), location: 0.52),
                            .init(color: fgColor.opacity(0.18), location: 0.76),
                            .init(color: Color.clear, location: 1.0)
                        ]),
                        center: UnitPoint(x: centerShiftX, y: centerShiftY),
                        startRadius: size * 0.02,
                        endRadius: size * (0.64 + CGFloat(effectiveLevel) * 0.26)
                    )
                )

            // Second halo déphasé pour la profondeur fluide
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(stops: [
                            .init(color: fgColor.opacity(0.88), location: 0.0),
                            .init(color: fgColor.opacity(0.45), location: 0.48),
                            .init(color: Color.clear, location: 0.95)
                        ]),
                        center: UnitPoint(x: secondaryShiftX, y: secondaryShiftY),
                        startRadius: size * 0.01,
                        endRadius: size * (0.52 + CGFloat(effectiveLevel) * 0.22)
                    )
                )
                .blur(radius: size * 0.08)

            // Vague d'horizon sinusoïdale interne + faisceau lumineux horizontal (Canvas 120 Hz)
            Canvas { context, canvasSize in
                let width = canvasSize.width
                let height = canvasSize.height
                let baseHorizonY = height * CGFloat(0.76 - effectiveLevel * 0.34)
                let waveAmplitude = height * CGFloat(0.035 + effectiveLevel * 0.055)

                var wavePath = Path()
                wavePath.move(to: CGPoint(x: 0, y: height))
                wavePath.addLine(to: CGPoint(x: 0, y: baseHorizonY))

                let steps = 48
                for step in 0...steps {
                    let progress = Double(step) / Double(steps)
                    let x = CGFloat(progress) * width
                    let primarySine = sin(progress * .pi * 2.0 + phase * 3.8)
                    let secondaryCosine = cos(progress * .pi * 1.5 - phase * 4.2) * 0.45
                    let y = baseHorizonY + CGFloat(primarySine + secondaryCosine) * waveAmplitude
                    wavePath.addLine(to: CGPoint(x: x, y: y))
                }

                wavePath.addLine(to: CGPoint(x: width, y: height))
                wavePath.closeSubpath()

                context.fill(
                    wavePath,
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: fgColor.opacity(0.68), location: 0.0),
                            .init(color: fgColor.opacity(0.96), location: 0.55),
                            .init(color: Color.white.opacity(0.28), location: 1.0)
                        ]),
                        startPoint: CGPoint(x: width * 0.5, y: baseHorizonY - waveAmplitude),
                        endPoint: CGPoint(x: width * 0.5, y: height)
                    )
                )

                // Ligne d'horizon lumineuse caractéristique (comme sur la capture de référence)
                var crestPath = Path()
                for step in 0...steps {
                    let progress = Double(step) / Double(steps)
                    let x = CGFloat(progress) * width
                    let primarySine = sin(progress * .pi * 2.0 + phase * 3.8)
                    let secondaryCosine = cos(progress * .pi * 1.5 - phase * 4.2) * 0.45
                    let y = baseHorizonY + CGFloat(primarySine + secondaryCosine) * waveAmplitude
                    if step == 0 {
                        crestPath.move(to: CGPoint(x: x, y: y))
                    } else {
                        crestPath.addLine(to: CGPoint(x: x, y: y))
                    }
                }

                if isLowContrastPair {
                    let bgLum = ThemeColors.relativeLuminance(of: bgHex)
                    let contrastCrestColor = bgLum < 0.5 ? Color.white.opacity(0.75) : Color.black.opacity(0.65)
                    context.stroke(
                        crestPath,
                        with: .color(contrastCrestColor),
                        lineWidth: max(1.6, size * 0.032)
                    )
                } else {
                    context.stroke(
                        crestPath,
                        with: .linearGradient(
                            Gradient(colors: [
                                fgColor.opacity(0.35),
                                Color.white.opacity(0.42),
                                fgColor.opacity(0.55)
                            ]),
                            startPoint: CGPoint(x: 0, y: baseHorizonY),
                            endPoint: CGPoint(x: width, y: baseHorizonY)
                        ),
                        lineWidth: max(1.2, size * 0.025)
                    )
                }
            }
            .blur(radius: state == .transcribing ? 0 : (isLowContrastPair ? size * 0.02 : size * 0.05))

            // Coeur incandescent en bas de la sphère pour l'effet volumétrique Gemini Live
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(colors: [
                            Color.white.opacity(0.24 + effectiveLevel * 0.14),
                            fgColor.opacity(0.55),
                            Color.clear
                        ]),
                        center: UnitPoint(x: 0.46 + 0.05 * sin(phase * 2.9), y: 0.92),
                        startRadius: 0,
                        endRadius: size * 0.38
                    )
                )
                .blendMode(.plusLighter)
        }
    }

    // MARK: - Mode Spécial "Noir sur Noir" (Éclipse / Dark Stealth)

    @ViewBuilder
    private func blackOnBlackEclipseLayer(phase: TimeInterval, effectiveLevel: Double) -> some View {
        let centerShiftX = 0.5 + 0.07 * sin(phase * 3.2)
        let centerShiftY = 0.92 - effectiveLevel * 0.30

        ZStack {
            // Halo d'ombre liquide #14141C
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(stops: [
                            .init(color: Color(hex: "#1C1C26").opacity(0.95), location: 0.0),
                            .init(color: Color(hex: "#14141C").opacity(0.85), location: 0.45),
                            .init(color: Color(hex: "#0A0A0F").opacity(0.40), location: 0.78),
                            .init(color: Color.clear, location: 1.0)
                        ]),
                        center: UnitPoint(x: centerShiftX, y: centerShiftY),
                        startRadius: size * 0.02,
                        endRadius: size * (0.60 + CGFloat(effectiveLevel) * 0.24)
                    )
                )

            // Vague d'ombre liquide noire couronnée d'un filet spéculaire argenté/métallique
            Canvas { context, canvasSize in
                let width = canvasSize.width
                let height = canvasSize.height
                let baseHorizonY = height * CGFloat(0.75 - effectiveLevel * 0.33)
                let waveAmplitude = height * CGFloat(0.035 + effectiveLevel * 0.055)

                var wavePath = Path()
                var crestPath = Path()
                wavePath.move(to: CGPoint(x: 0, y: height))
                wavePath.addLine(to: CGPoint(x: 0, y: baseHorizonY))

                let steps = 48
                for step in 0...steps {
                    let progress = Double(step) / Double(steps)
                    let x = CGFloat(progress) * width
                    let waveOffset = sin(progress * .pi * 2.0 + phase * 3.6) + 0.4 * cos(progress * .pi * 1.5 - phase * 4.0)
                    let y = baseHorizonY + CGFloat(waveOffset) * waveAmplitude
                    wavePath.addLine(to: CGPoint(x: x, y: y))
                    if step == 0 {
                        crestPath.move(to: CGPoint(x: x, y: y))
                    } else {
                        crestPath.addLine(to: CGPoint(x: x, y: y))
                    }
                }

                wavePath.addLine(to: CGPoint(x: width, y: height))
                wavePath.closeSubpath()

                context.fill(
                    wavePath,
                    with: .linearGradient(
                        Gradient(colors: [
                            Color(hex: "#181822").opacity(0.95),
                            Color(hex: "#08080C").opacity(0.98)
                        ]),
                        startPoint: CGPoint(x: width * 0.5, y: baseHorizonY),
                        endPoint: CGPoint(x: width * 0.5, y: height)
                    )
                )

                // Filet spéculaire argenté / métallique sur la crête
                context.stroke(
                    crestPath,
                    with: .linearGradient(
                        Gradient(colors: [
                            Color(hex: "#8E8E93").opacity(0.35),
                            Color.white.opacity(0.32),
                            Color(hex: "#8E8E93").opacity(0.38)
                        ]),
                        startPoint: CGPoint(x: 0, y: baseHorizonY),
                        endPoint: CGPoint(x: width, y: baseHorizonY)
                    ),
                    lineWidth: max(1.4, size * 0.03)
                )
            }
        }
    }

    // MARK: - Mode Spécial "Blanc sur Blanc" (Perle / Cristal)

    @ViewBuilder
    private func whiteOnWhiteCrystalLayer(phase: TimeInterval, effectiveLevel: Double) -> some View {
        let centerShiftX = 0.5 + 0.08 * sin(phase * 3.4)
        let centerShiftY = 0.92 - effectiveLevel * 0.30

        ZStack {
            // Halo nacré gris-perle doux (#CBD2D9 à 55%) + léger irisé bleuté/rosé à 12%
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(stops: [
                            .init(color: Color(hex: "#CBD2D9").opacity(0.58), location: 0.0),
                            .init(color: Color(hex: "#32ADE6").opacity(0.12), location: 0.38),
                            .init(color: Color(hex: "#FF2A85").opacity(0.10), location: 0.62),
                            .init(color: Color.clear, location: 1.0)
                        ]),
                        center: UnitPoint(x: centerShiftX, y: centerShiftY),
                        startRadius: size * 0.02,
                        endRadius: size * (0.64 + CGFloat(effectiveLevel) * 0.22)
                    )
                )

            // Onde cristalline 3D avec reflet spéculaire blanc pur #FFFFFF
            Canvas { context, canvasSize in
                let width = canvasSize.width
                let height = canvasSize.height
                let baseHorizonY = height * CGFloat(0.75 - effectiveLevel * 0.32)
                let waveAmplitude = height * CGFloat(0.035 + effectiveLevel * 0.05)

                var wavePath = Path()
                var crestPath = Path()
                wavePath.move(to: CGPoint(x: 0, y: height))
                wavePath.addLine(to: CGPoint(x: 0, y: baseHorizonY))

                let steps = 48
                for step in 0...steps {
                    let progress = Double(step) / Double(steps)
                    let x = CGFloat(progress) * width
                    let waveOffset = sin(progress * .pi * 2.0 + phase * 3.5) + 0.4 * cos(progress * .pi * 1.5 - phase * 4.1)
                    let y = baseHorizonY + CGFloat(waveOffset) * waveAmplitude
                    wavePath.addLine(to: CGPoint(x: x, y: y))
                    if step == 0 {
                        crestPath.move(to: CGPoint(x: x, y: y))
                    } else {
                        crestPath.addLine(to: CGPoint(x: x, y: y))
                    }
                }

                wavePath.addLine(to: CGPoint(x: width, y: height))
                wavePath.closeSubpath()

                context.fill(
                    wavePath,
                    with: .linearGradient(
                        Gradient(colors: [
                            Color(hex: "#CBD2D9").opacity(0.55),
                            Color(hex: "#E2E8F0").opacity(0.75),
                            Color(hex: "#FFFFFF").opacity(0.95)
                        ]),
                        startPoint: CGPoint(x: width * 0.5, y: baseHorizonY),
                        endPoint: CGPoint(x: width * 0.5, y: height)
                    )
                )

                // Liseré de réfraction perle + spéculaire blanc pur #FFFFFF
                context.stroke(
                    crestPath,
                    with: .linearGradient(
                        Gradient(colors: [
                            Color(hex: "#94A3B8").opacity(0.42),
                            Color(hex: "#FFFFFF").opacity(0.98),
                            Color(hex: "#94A3B8").opacity(0.42)
                        ]),
                        startPoint: CGPoint(x: 0, y: baseHorizonY),
                        endPoint: CGPoint(x: width, y: baseHorizonY)
                    ),
                    lineWidth: max(1.5, size * 0.032)
                )
            }
        }
    }

    // MARK: - Bordure 3D et Animation Orbitale (.transcribing)

    @ViewBuilder
    private func orbPerimeterBorder(phase: TimeInterval, fgColor: Color) -> some View {
        if state == .transcribing {
            let angle = Angle.radians(phase * 5.5)
            let highlightColor: Color = isBlackStealth
                ? Color.white.opacity(0.75)
                : (isWhiteCrystal ? Color(hex: "#64748B") : fgColor)

            Circle()
                .strokeBorder(
                    AngularGradient(
                        gradient: Gradient(colors: [
                            highlightColor.opacity(0.10),
                            highlightColor.opacity(0.45),
                            Color.white.opacity(0.92),
                            highlightColor.opacity(0.85),
                            highlightColor.opacity(0.10)
                        ]),
                        center: .center,
                        startAngle: angle,
                        endAngle: angle + .degrees(360)
                    ),
                    lineWidth: max(1.6, size * 0.038)
                )
        } else {
            let rimBottomColor: Color = {
                if isBlackStealth {
                    return Color.white.opacity(0.20)
                } else if isWhiteCrystal {
                    return Color(hex: "#94A3B8").opacity(0.38)
                } else {
                    return fgColor.opacity(0.55)
                }
            }()

            Circle()
                .strokeBorder(
                    LinearGradient(
                        stops: [
                            .init(color: Color.white.opacity(isWhiteCrystal ? 0.65 : 0.14), location: 0.0),
                            .init(color: Color.white.opacity(0.03), location: 0.45),
                            .init(color: rimBottomColor, location: 1.0)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    lineWidth: max(1.0, size * 0.022)
                )
        }
    }
}
