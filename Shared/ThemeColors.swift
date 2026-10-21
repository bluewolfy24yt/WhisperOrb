import SwiftUI
import UIKit
import Foundation

public struct ColorPreset: Identifiable, Equatable, Hashable {
    public let id: String
    public let name: String
    public let subtitle: String
    public let hex: String

    public init(id: String, name: String, subtitle: String = "", hex: String) {
        self.id = id
        self.name = name
        self.subtitle = subtitle
        self.hex = hex
    }
}

public struct ThemeColors {

    // MARK: - 1. Dictionnaire & Liste Ordonnée des Fonds (Backgrounds)

    public static let backgrounds: [String: String] = [
        "black": "#08080C",
        "white": "#F8F9FA",
        "obsidian": "#040406",
        "midnight": "#0F172A",
        "ivory": "#FFFFF0"
    ]

    public static let orderedBackgrounds: [ColorPreset] = [
        ColorPreset(id: "black", name: "Noir Profond", subtitle: "Gemini Live", hex: "#08080C"),
        ColorPreset(id: "white", name: "Blanc Pur", subtitle: "Cristal", hex: "#F8F9FA"),
        ColorPreset(id: "obsidian", name: "Obsidienne", subtitle: "Mat Absolu", hex: "#040406"),
        ColorPreset(id: "midnight", name: "Bleu Minuit", subtitle: "Nuit Polaire", hex: "#0F172A"),
        ColorPreset(id: "ivory", name: "Ivoire Doux", subtitle: "Papier Crème", hex: "#FFFFF0")
    ]

    // MARK: - 2. Dictionnaire & Liste Ordonnée des Ondes Lumineuses (Foregrounds - 16 Thèmes)

    public static let foregrounds: [String: String] = [
        "blue": "#0088FF",
        "purple": "#8A2BE2",
        "pink": "#FF2A85",
        "red": "#FF3B30",
        "orange": "#FF9500",
        "yellow": "#FFCC00",
        "green": "#34C759",
        "mint": "#00C7BE",
        "cyan": "#32ADE6",
        "indigo": "#5856D6",
        "gold": "#FFD700",
        "coral": "#FF7F50",
        "lavender": "#B57EDC",
        "gray": "#8E8E93",
        "white": "#FFFFFF",
        "black": "#050507"
    ]

    public static let orderedForegrounds: [ColorPreset] = [
        ColorPreset(id: "blue", name: "Bleu Gemini", subtitle: "Défaut Live", hex: "#0088FF"),
        ColorPreset(id: "purple", name: "Violet Néon", subtitle: "Électrique", hex: "#8A2BE2"),
        ColorPreset(id: "pink", name: "Rose Cyber", subtitle: "Vibrant", hex: "#FF2A85"),
        ColorPreset(id: "red", name: "Rouge Vif", subtitle: "Intense", hex: "#FF3B30"),
        ColorPreset(id: "orange", name: "Orange Solaire", subtitle: "Chaleureux", hex: "#FF9500"),
        ColorPreset(id: "yellow", name: "Jaune Or", subtitle: "Lumineux", hex: "#FFCC00"),
        ColorPreset(id: "green", name: "Vert Émeraude", subtitle: "Nature", hex: "#34C759"),
        ColorPreset(id: "mint", name: "Menthe", subtitle: "Turquoise", hex: "#00C7BE"),
        ColorPreset(id: "cyan", name: "Cyan Polaire", subtitle: "Glacier", hex: "#32ADE6"),
        ColorPreset(id: "indigo", name: "Indigo Profond", subtitle: "Cosmos", hex: "#5856D6"),
        ColorPreset(id: "gold", name: "Or Royal", subtitle: "Précieux", hex: "#FFD700"),
        ColorPreset(id: "coral", name: "Corail", subtitle: "Récif", hex: "#FF7F50"),
        ColorPreset(id: "lavender", name: "Lavande", subtitle: "Pastel", hex: "#B57EDC"),
        ColorPreset(id: "gray", name: "Gris Titane", subtitle: "Métal Pro", hex: "#8E8E93"),
        ColorPreset(id: "white", name: "Blanc Pur", subtitle: "Mode Cristal / Perle", hex: "#FFFFFF"),
        ColorPreset(id: "black", name: "Noir Pur", subtitle: "Mode Éclipse / Stealth", hex: "#050507")
    ]

    // MARK: - 3. Dictionnaire Géant de Recherche Textuelle (140+ Entrées FR/EN Explicites)

    public static let namedColorsDictionary: [String: String] = [
        // Bleus & Nuances Marines
        "bleu": "#0088FF",
        "blue": "#0088FF",
        "gemini": "#0088FF",
        "azur": "#007FFF",
        "azure": "#007FFF",
        "cobalt": "#0047AB",
        "marine": "#001F54",
        "bleu marine": "#001F54",
        "navy": "#000080",
        "minuit": "#0F172A",
        "bleu minuit": "#0F172A",
        "midnight": "#0F172A",
        "midnightblue": "#191970",
        "saphir": "#0F52BA",
        "sapphire": "#0F52BA",
        "cyan": "#32ADE6",
        "aqua": "#00FFFF",
        "turquoise": "#40E0D0",
        "menthe": "#00C7BE",
        "mint": "#00C7BE",
        "teal": "#008080",
        "sarcelle": "#008080",
        "aigue-marine": "#7FFFD4",
        "aigue marine": "#7FFFD4",
        "aquamarine": "#7FFFD4",
        "glacier": "#74B9FF",
        "ciel": "#87CEEB",
        "bleu ciel": "#87CEEB",
        "skyblue": "#87CEEB",
        "deepskyblue": "#00BFFF",
        "dodgerblue": "#1E90FF",
        "royalblue": "#4169E1",
        "bleu roi": "#4169E1",
        "steelblue": "#4682B4",
        "bleu acier": "#4682B4",
        "indigo": "#5856D6",
        "pervenche": "#CCCCFF",
        "periwinkle": "#CCCCFF",
        "paon": "#005F69",
        "peacock": "#005F69",
        "petrole": "#1D4851",
        "pétrole": "#1D4851",

        // Violets, Pourpres & Roses
        "violet": "#8A2BE2",
        "purple": "#8A2BE2",
        "blueviolet": "#8A2BE2",
        "lavande": "#B57EDC",
        "lavender": "#B57EDC",
        "lilas": "#C8A2C8",
        "lilac": "#C8A2C8",
        "prune": "#8E4585",
        "plum": "#DDA0DD",
        "orchidee": "#DA70D6",
        "orchidée": "#DA70D6",
        "orchid": "#DA70D6",
        "magenta": "#FF00FF",
        "fuchsia": "#FF00AA",
        "amethyste": "#9966CC",
        "améthyste": "#9966CC",
        "amethyst": "#9966CC",
        "mauve": "#E0B0FF",
        "pourpre": "#9F00C5",
        "aubergine": "#614051",
        "byzantium": "#702963",
        "rose": "#FF2A85",
        "pink": "#FF2A85",
        "hotpink": "#FF69B4",
        "deeppink": "#FF1493",
        "rose vif": "#FF1493",
        "rose bonbon": "#F9429E",
        "rose pale": "#FFB6C1",
        "rose pâle": "#FFB6C1",
        "lightpink": "#FFB6C1",
        "flamant": "#FC8EAC",
        "flamingo": "#FC8EAC",
        "framboise": "#E30B5C",
        "raspberry": "#E30B5C",

        // Rouges, Corails & Oranges
        "rouge": "#FF3B30",
        "red": "#FF3B30",
        "rubis": "#E0115F",
        "ruby": "#E0115F",
        "carmin": "#DC143C",
        "crimson": "#DC143C",
        "bordeaux": "#800020",
        "burgundy": "#800020",
        "maroon": "#800000",
        "grenat": "#730800",
        "garnet": "#730800",
        "cerise": "#DE3163",
        "cherry": "#DE3163",
        "vermillon": "#E34234",
        "vermilion": "#E34234",
        "ecarlate": "#FF2400",
        "écarlate": "#FF2400",
        "scarlet": "#FF2400",
        "tomate": "#FF6347",
        "tomato": "#FF6347",
        "brique": "#B22222",
        "firebrick": "#B22222",
        "corail": "#FF7F50",
        "coral": "#FF7F50",
        "saumon": "#FA8072",
        "salmon": "#FA8072",
        "peche": "#FFCBA4",
        "pêche": "#FFCBA4",
        "peach": "#FFCBA4",
        "abricot": "#FBCEB1",
        "apricot": "#FBCEB1",
        "orange": "#FF9500",
        "mandarine": "#F28500",
        "tangerine": "#F28500",
        "ambre": "#FFBF00",
        "amber": "#FFBF00",
        "cuivre": "#B87333",
        "copper": "#B87333",
        "terracotta": "#E2725B",

        // Jaunes & Ors
        "jaune": "#FFCC00",
        "yellow": "#FFCC00",
        "or": "#FFD700",
        "dore": "#FFD700",
        "doré": "#FFD700",
        "gold": "#FFD700",
        "golden": "#FFD700",
        "citron": "#FFF44F",
        "lemon": "#FFF44F",
        "moutarde": "#FFDB58",
        "mustard": "#FFDB58",
        "safran": "#F4C430",
        "saffron": "#F4C430",
        "ocre": "#CC7722",
        "ochre": "#CC7722",
        "miel": "#EB9605",
        "honey": "#EB9605",
        "canari": "#FFFF99",
        "canary": "#FFFF99",

        // Verts
        "vert": "#34C759",
        "green": "#34C759",
        "emeraude": "#50C878",
        "émeraude": "#50C878",
        "emerald": "#50C878",
        "jade": "#00A86B",
        "chartreuse": "#7FFF00",
        "lime": "#32CD32",
        "citron vert": "#32CD32",
        "olive": "#808000",
        "kaki": "#C3B091",
        "khaki": "#F0E68C",
        "foret": "#228B22",
        "forêt": "#228B22",
        "vert foret": "#228B22",
        "forestgreen": "#228B22",
        "sapin": "#095228",
        "pine": "#01796F",
        "sauge": "#9DC183",
        "sage": "#9DC183",
        "pistache": "#93C572",
        "pistachio": "#93C572",
        "pomme": "#8DB600",
        "vert pomme": "#8DB600",
        "springgreen": "#00FF7F",
        "seagreen": "#2E8B57",

        // Bruns, Beiges & Tons Naturels
        "marron": "#8B4513",
        "brown": "#8B4513",
        "chocolat": "#7B3F00",
        "chocolate": "#D2691E",
        "cafe": "#6F4E37",
        "café": "#6F4E37",
        "coffee": "#6F4E37",
        "caramel": "#FFD59A",
        "cannelle": "#D2691E",
        "cinnamon": "#D2691E",
        "bronze": "#CD7F32",
        "taupe": "#483C32",
        "sepia": "#704214",
        "sépia": "#704214",
        "sable": "#C2B280",
        "sand": "#C2B280",
        "beige": "#F5F5DC",
        "ivoire": "#FFFFF0",
        "ivory": "#FFFFF0",
        "creme": "#FFFDD0",
        "crème": "#FFFDD0",
        "cream": "#FFFDD0",
        "lin": "#FAF0E6",
        "linen": "#FAF0E6",
        "vanille": "#F3E5AB",
        "vanilla": "#F3E5AB",

        // Blancs, Gris & Noirs (Modes Cristal & Éclipse)
        "blanc": "#FFFFFF",
        "white": "#FFFFFF",
        "neige": "#FFFAFA",
        "snow": "#FFFAFA",
        "perle": "#F0EAD6",
        "pearl": "#F0EAD6",
        "nacre": "#FDEEF4",
        "cristal": "#F8F9FA",
        "crystal": "#F8F9FA",
        "platine": "#E5E4E2",
        "platinum": "#E5E4E2",
        "argent": "#C0C0C0",
        "argente": "#C0C0C0",
        "argenté": "#C0C0C0",
        "silver": "#C0C0C0",
        "gris": "#8E8E93",
        "gray": "#8E8E93",
        "grey": "#8E8E93",
        "titane": "#878681",
        "titanium": "#878681",
        "acier": "#71797E",
        "steel": "#71797E",
        "ardoise": "#708090",
        "slate": "#708090",
        "slategray": "#708090",
        "anthracite": "#293133",
        "charbon": "#36454F",
        "charcoal": "#36454F",
        "obsidienne": "#040406",
        "obsidian": "#040406",
        "noir": "#050507",
        "black": "#050507",
        "eclipse": "#050507",
        "éclipse": "#050507",
        "stealth": "#050507",
        "ebene": "#08080C",
        "ébène": "#08080C",
        "ebony": "#08080C",
        "onyx": "#0F0F14",
        "nuit": "#08080C"
    ]

    // MARK: - 4. Parseur Intelligent de Couleur (Hexa #RRGGBB / #RGB ou Nom FR/EN)

    public static func parseColorInput(_ input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return nil }

        // 1. Test code hexadécimal (#RRGGBB, RRGGBB, #RGB, RGB)
        let rawHex = trimmed.hasPrefix("#") ? String(trimmed.dropFirst()) : trimmed
        let hexCharset = CharacterSet(charactersIn: "0123456789abcdef")

        if rawHex.unicodeScalars.allSatisfy({ hexCharset.contains($0) }) {
            if rawHex.count == 6 {
                return "#" + rawHex.uppercased()
            } else if rawHex.count == 3 {
                let chars = Array(rawHex.uppercased())
                let expanded = "\(chars[0])\(chars[0])\(chars[1])\(chars[1])\(chars[2])\(chars[2])"
                return "#" + expanded
            }
        }

        // 2. Correspondance directe exacte dans foregrounds, backgrounds et namedColorsDictionary
        if let fgMatch = foregrounds[trimmed] {
            return fgMatch
        }
        if let bgMatch = backgrounds[trimmed] {
            return bgMatch
        }
        if let dictMatch = namedColorsDictionary[trimmed] {
            return dictMatch
        }

        // 3. Correspondance normalisée sans accents
        let foldedInput = trimmed.folding(options: .diacriticInsensitive, locale: Locale(identifier: "fr_FR"))
        if let dictMatchFolded = namedColorsDictionary[foldedInput] {
            return dictMatchFolded
        }

        // 4. Recherche partielle (contains) si l'entrée fait au moins 3 caractères
        if foldedInput.count >= 3 {
            // Priorité aux clés qui commencent par la saisie
            let sortedKeys = namedColorsDictionary.keys.sorted { $0.count < $1.count }
            for key in sortedKeys {
                let foldedKey = key.folding(options: .diacriticInsensitive, locale: Locale(identifier: "fr_FR"))
                if foldedKey.hasPrefix(foldedInput) {
                    return namedColorsDictionary[key]
                }
            }
            for key in sortedKeys {
                let foldedKey = key.folding(options: .diacriticInsensitive, locale: Locale(identifier: "fr_FR"))
                if foldedKey.contains(foldedInput) || foldedInput.contains(foldedKey) {
                    return namedColorsDictionary[key]
                }
            }
        }

        return nil
    }

    // MARK: - 5. Fonctions Mathématiques de Luminance et de Contraste

    public static func rgbComponents(from hex: String) -> (r: Double, g: Double, b: Double) {
        var cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cleaned.hasPrefix("#") {
            cleaned.removeFirst()
        }
        if cleaned.count == 3 {
            let chars = Array(cleaned)
            cleaned = "\(chars[0])\(chars[0])\(chars[1])\(chars[1])\(chars[2])\(chars[2])"
        }
        guard cleaned.count == 6, let rgbValue = UInt64(cleaned, radix: 16) else {
            return (r: 0.0, g: 0.533, b: 1.0)
        }
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0
        return (r: r, g: g, b: b)
    }

    /// Formule photométrique standard : L = 0.2126 * R + 0.7152 * G + 0.0722 * B
    public static func relativeLuminance(of hex: String) -> Double {
        let comps = rgbComponents(from: hex)
        return 0.2126 * comps.r + 0.7152 * comps.g + 0.0722 * comps.b
    }

    /// Distance euclidienne RGB : d = sqrt((R1 - R2)^2 + (G1 - G2)^2 + (B1 - B2)^2)
    public static func colorDistance(hex1: String, hex2: String) -> Double {
        let c1 = rgbComponents(from: hex1)
        let c2 = rgbComponents(from: hex2)
        let dr = c1.r - c2.r
        let dg = c1.g - c2.g
        let db = c1.b - c2.b
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /// Mode Spécial "Noir sur Noir" (Éclipse / Dark Stealth)
    public static func isBlackOnBlack(bgHex: String, fgHex: String) -> Bool {
        return relativeLuminance(of: bgHex) < 0.18 && relativeLuminance(of: fgHex) < 0.18
    }

    /// Mode Spécial "Blanc sur Blanc" (Perle / Cristal)
    public static func isWhiteOnWhite(bgHex: String, fgHex: String) -> Bool {
        return relativeLuminance(of: bgHex) > 0.82 && relativeLuminance(of: fgHex) > 0.82
    }

    /// Mode Faible Contraste (deux couleurs proches quelconques)
    public static func isLowContrast(bgHex: String, fgHex: String) -> Bool {
        return colorDistance(hex1: bgHex, hex2: fgHex) < 0.22
    }

    /// Nom lisible d'une couleur Hex si connue
    public static func displayName(for hex: String) -> String {
        let normalized = hex.uppercased()
        if let preset = orderedForegrounds.first(where: { $0.hex.uppercased() == normalized }) {
            return preset.name
        }
        if let preset = orderedBackgrounds.first(where: { $0.hex.uppercased() == normalized }) {
            return preset.name
        }
        if let entry = namedColorsDictionary.first(where: { $0.value.uppercased() == normalized }) {
            return entry.key.capitalized
        }
        return normalized
    }
}

// MARK: - 6. Extensions Bidirectionnelles SwiftUI.Color & UIColor

public extension UIColor {
    convenience init(hex: String) {
        let comps = ThemeColors.rgbComponents(from: hex)
        self.init(red: CGFloat(comps.r), green: CGFloat(comps.g), blue: CGFloat(comps.b), alpha: 1.0)
    }

    func toHex() -> String {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 0

        if self.getRed(&r, green: &g, blue: &b, alpha: &a) {
            let redInt = Int(round(max(0.0, min(1.0, r)) * 255.0))
            let greenInt = Int(round(max(0.0, min(1.0, g)) * 255.0))
            let blueInt = Int(round(max(0.0, min(1.0, b)) * 255.0))
            return String(format: "#%02X%02X%02X", redInt, greenInt, blueInt)
        }

        if let components = self.cgColor.components {
            if components.count >= 3 {
                let redInt = Int(round(max(0.0, min(1.0, components[0])) * 255.0))
                let greenInt = Int(round(max(0.0, min(1.0, components[1])) * 255.0))
                let blueInt = Int(round(max(0.0, min(1.0, components[2])) * 255.0))
                return String(format: "#%02X%02X%02X", redInt, greenInt, blueInt)
            } else if components.count >= 1 {
                let whiteInt = Int(round(max(0.0, min(1.0, components[0])) * 255.0))
                return String(format: "#%02X%02X%02X", whiteInt, whiteInt, whiteInt)
            }
        }

        return "#0088FF"
    }
}

public extension Color {
    init(hex: String) {
        let comps = ThemeColors.rgbComponents(from: hex)
        self.init(.sRGB, red: comps.r, green: comps.g, blue: comps.b, opacity: 1.0)
    }

    func toHex() -> String {
        return UIColor(self).toHex()
    }
}
