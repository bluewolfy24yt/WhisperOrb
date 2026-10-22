package com.whisperorb.app

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

data class ColorPreset(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val hex: String
)

object ThemeColors {

    val backgrounds: Map<String, String> = mapOf(
        "black" to "#08080C",
        "white" to "#F8F9FA",
        "obsidian" to "#040406",
        "midnight" to "#0F172A",
        "ivory" to "#FFFFF0"
    )

    val orderedBackgrounds: List<ColorPreset> = listOf(
        ColorPreset(id = "black", name = "Noir Profond", subtitle = "Gemini Live", hex = "#08080C"),
        ColorPreset(id = "white", name = "Blanc Pur", subtitle = "Cristal", hex = "#F8F9FA"),
        ColorPreset(id = "obsidian", name = "Obsidienne", subtitle = "Mat Absolu", hex = "#040406"),
        ColorPreset(id = "midnight", name = "Bleu Minuit", subtitle = "Nuit Polaire", hex = "#0F172A"),
        ColorPreset(id = "ivory", name = "Ivoire Doux", subtitle = "Papier Crème", hex = "#FFFFF0")
    )

    val foregrounds: Map<String, String> = mapOf(
        "blue" to "#0088FF",
        "purple" to "#8A2BE2",
        "pink" to "#FF2A85",
        "red" to "#FF3B30",
        "orange" to "#FF9500",
        "yellow" to "#FFCC00",
        "green" to "#34C759",
        "mint" to "#00C7BE",
        "cyan" to "#32ADE6",
        "indigo" to "#5856D6",
        "gold" to "#FFD700",
        "coral" to "#FF7F50",
        "lavender" to "#B57EDC",
        "gray" to "#8E8E93",
        "white" to "#FFFFFF",
        "black" to "#050507"
    )

    val orderedForegrounds: List<ColorPreset> = listOf(
        ColorPreset(id = "blue", name = "Bleu Gemini", subtitle = "Défaut Live", hex = "#0088FF"),
        ColorPreset(id = "purple", name = "Violet Néon", subtitle = "Électrique", hex = "#8A2BE2"),
        ColorPreset(id = "pink", name = "Rose Cyber", subtitle = "Vibrant", hex = "#FF2A85"),
        ColorPreset(id = "red", name = "Rouge Vif", subtitle = "Intense", hex = "#FF3B30"),
        ColorPreset(id = "orange", name = "Orange Solaire", subtitle = "Chaleureux", hex = "#FF9500"),
        ColorPreset(id = "yellow", name = "Jaune Or", subtitle = "Lumineux", hex = "#FFCC00"),
        ColorPreset(id = "green", name = "Vert Émeraude", subtitle = "Nature", hex = "#34C759"),
        ColorPreset(id = "mint", name = "Menthe", subtitle = "Turquoise", hex = "#00C7BE"),
        ColorPreset(id = "cyan", name = "Cyan Polaire", subtitle = "Glacier", hex = "#32ADE6"),
        ColorPreset(id = "indigo", name = "Indigo Profond", subtitle = "Cosmos", hex = "#5856D6"),
        ColorPreset(id = "gold", name = "Or Royal", subtitle = "Précieux", hex = "#FFD700"),
        ColorPreset(id = "coral", name = "Corail", subtitle = "Récif", hex = "#FF7F50"),
        ColorPreset(id = "lavender", name = "Lavande", subtitle = "Pastel", hex = "#B57EDC"),
        ColorPreset(id = "gray", name = "Gris Titane", subtitle = "Métal Pro", hex = "#8E8E93"),
        ColorPreset(id = "white", name = "Blanc Pur", subtitle = "Mode Cristal / Perle", hex = "#FFFFFF"),
        ColorPreset(id = "black", name = "Noir Pur", subtitle = "Mode Éclipse / Stealth", hex = "#050507")
    )

    val namedColorsDictionary: Map<String, String> = mapOf(
        "bleu" to "#0088FF",
        "blue" to "#0088FF",
        "gemini" to "#0088FF",
        "azur" to "#007FFF",
        "azure" to "#007FFF",
        "cobalt" to "#0047AB",
        "marine" to "#001F54",
        "bleu marine" to "#001F54",
        "navy" to "#000080",
        "minuit" to "#0F172A",
        "bleu minuit" to "#0F172A",
        "midnight" to "#0F172A",
        "midnightblue" to "#191970",
        "saphir" to "#0F52BA",
        "sapphire" to "#0F52BA",
        "cyan" to "#32ADE6",
        "aqua" to "#00FFFF",
        "turquoise" to "#40E0D0",
        "menthe" to "#00C7BE",
        "mint" to "#00C7BE",
        "teal" to "#008080",
        "sarcelle" to "#008080",
        "aigue-marine" to "#7FFFD4",
        "aigue marine" to "#7FFFD4",
        "aquamarine" to "#7FFFD4",
        "glacier" to "#74B9FF",
        "ciel" to "#87CEEB",
        "bleu ciel" to "#87CEEB",
        "skyblue" to "#87CEEB",
        "deepskyblue" to "#00BFFF",
        "dodgerblue" to "#1E90FF",
        "royalblue" to "#4169E1",
        "bleu roi" to "#4169E1",
        "steelblue" to "#4682B4",
        "bleu acier" to "#4682B4",
        "indigo" to "#5856D6",
        "pervenche" to "#CCCCFF",
        "periwinkle" to "#CCCCFF",
        "paon" to "#005F69",
        "peacock" to "#005F69",
        "petrole" to "#1D4851",
        "pétrole" to "#1D4851",
        "violet" to "#8A2BE2",
        "purple" to "#8A2BE2",
        "blueviolet" to "#8A2BE2",
        "lavande" to "#B57EDC",
        "lavender" to "#B57EDC",
        "lilas" to "#C8A2C8",
        "lilac" to "#C8A2C8",
        "prune" to "#8E4585",
        "plum" to "#DDA0DD",
        "orchidee" to "#DA70D6",
        "orchidée" to "#DA70D6",
        "orchid" to "#DA70D6",
        "magenta" to "#FF00FF",
        "fuchsia" to "#FF00AA",
        "amethyste" to "#9966CC",
        "améthyste" to "#9966CC",
        "amethyst" to "#9966CC",
        "mauve" to "#E0B0FF",
        "pourpre" to "#9F00C5",
        "aubergine" to "#614051",
        "byzantium" to "#702963",
        "rose" to "#FF2A85",
        "pink" to "#FF2A85",
        "hotpink" to "#FF69B4",
        "deeppink" to "#FF1493",
        "rose vif" to "#FF1493",
        "rose bonbon" to "#F9429E",
        "rose pale" to "#FFB6C1",
        "rose pâle" to "#FFB6C1",
        "lightpink" to "#FFB6C1",
        "flamant" to "#FC8EAC",
        "flamingo" to "#FC8EAC",
        "framboise" to "#E30B5C",
        "raspberry" to "#E30B5C",
        "rouge" to "#FF3B30",
        "red" to "#FF3B30",
        "rubis" to "#E0115F",
        "ruby" to "#E0115F",
        "carmin" to "#DC143C",
        "crimson" to "#DC143C",
        "bordeaux" to "#800020",
        "burgundy" to "#800020",
        "maroon" to "#800000",
        "grenat" to "#730800",
        "garnet" to "#730800",
        "cerise" to "#DE3163",
        "cherry" to "#DE3163",
        "vermillon" to "#E34234",
        "vermilion" to "#E34234",
        "ecarlate" to "#FF2400",
        "écarlate" to "#FF2400",
        "scarlet" to "#FF2400",
        "tomate" to "#FF6347",
        "tomato" to "#FF6347",
        "brique" to "#B22222",
        "firebrick" to "#B22222",
        "corail" to "#FF7F50",
        "coral" to "#FF7F50",
        "saumon" to "#FA8072",
        "salmon" to "#FA8072",
        "peche" to "#FFCBA4",
        "pêche" to "#FFCBA4",
        "peach" to "#FFCBA4",
        "abricot" to "#FBCEB1",
        "apricot" to "#FBCEB1",
        "orange" to "#FF9500",
        "mandarine" to "#F28500",
        "tangerine" to "#F28500",
        "ambre" to "#FFBF00",
        "amber" to "#FFBF00",
        "cuivre" to "#B87333",
        "copper" to "#B87333",
        "terracotta" to "#E2725B",
        "jaune" to "#FFCC00",
        "yellow" to "#FFCC00",
        "or" to "#FFD700",
        "dore" to "#FFD700",
        "doré" to "#FFD700",
        "gold" to "#FFD700",
        "golden" to "#FFD700",
        "citron" to "#FFF44F",
        "lemon" to "#FFF44F",
        "moutarde" to "#FFDB58",
        "mustard" to "#FFDB58",
        "safran" to "#F4C430",
        "saffron" to "#F4C430",
        "ocre" to "#CC7722",
        "ochre" to "#CC7722",
        "miel" to "#EB9605",
        "honey" to "#EB9605",
        "canari" to "#FFFF99",
        "canary" to "#FFFF99",
        "vert" to "#34C759",
        "green" to "#34C759",
        "emeraude" to "#50C878",
        "émeraude" to "#50C878",
        "emerald" to "#50C878",
        "jade" to "#00A86B",
        "chartreuse" to "#7FFF00",
        "lime" to "#32CD32",
        "citron vert" to "#32CD32",
        "olive" to "#808000",
        "kaki" to "#C3B091",
        "khaki" to "#F0E68C",
        "foret" to "#228B22",
        "forêt" to "#228B22",
        "vert foret" to "#228B22",
        "forestgreen" to "#228B22",
        "sapin" to "#095228",
        "pine" to "#01796F",
        "sauge" to "#9DC183",
        "sage" to "#9DC183",
        "pistache" to "#93C572",
        "pistachio" to "#93C572",
        "pomme" to "#8DB600",
        "vert pomme" to "#8DB600",
        "springgreen" to "#00FF7F",
        "seagreen" to "#2E8B57",
        "marron" to "#8B4513",
        "brown" to "#8B4513",
        "chocolat" to "#7B3F00",
        "chocolate" to "#D2691E",
        "cafe" to "#6F4E37",
        "café" to "#6F4E37",
        "coffee" to "#6F4E37",
        "caramel" to "#FFD59A",
        "cannelle" to "#D2691E",
        "cinnamon" to "#D2691E",
        "bronze" to "#CD7F32",
        "taupe" to "#483C32",
        "sepia" to "#704214",
        "sépia" to "#704214",
        "sable" to "#C2B280",
        "sand" to "#C2B280",
        "beige" to "#F5F5DC",
        "ivoire" to "#FFFFF0",
        "ivory" to "#FFFFF0",
        "creme" to "#FFFDD0",
        "crème" to "#FFFDD0",
        "cream" to "#FFFDD0",
        "lin" to "#FAF0E6",
        "linen" to "#FAF0E6",
        "vanille" to "#F3E5AB",
        "vanilla" to "#F3E5AB",
        "blanc" to "#FFFFFF",
        "white" to "#FFFFFF",
        "neige" to "#FFFAFA",
        "snow" to "#FFFAFA",
        "perle" to "#F0EAD6",
        "pearl" to "#F0EAD6",
        "nacre" to "#FDEEF4",
        "cristal" to "#F8F9FA",
        "crystal" to "#F8F9FA",
        "platine" to "#E5E4E2",
        "platinum" to "#E5E4E2",
        "argent" to "#C0C0C0",
        "argente" to "#C0C0C0",
        "argenté" to "#C0C0C0",
        "silver" to "#C0C0C0",
        "gris" to "#8E8E93",
        "gray" to "#8E8E93",
        "grey" to "#8E8E93",
        "titane" to "#878681",
        "titanium" to "#878681",
        "acier" to "#71797E",
        "steel" to "#71797E",
        "ardoise" to "#708090",
        "slate" to "#708090",
        "slategray" to "#708090",
        "anthracite" to "#293133",
        "charbon" to "#36454F",
        "charcoal" to "#36454F",
        "obsidienne" to "#040406",
        "obsidian" to "#040406",
        "noir" to "#050507",
        "black" to "#050507",
        "eclipse" to "#050507",
        "éclipse" to "#050507",
        "stealth" to "#050507",
        "ebene" to "#08080C",
        "ébène" to "#08080C",
        "ebony" to "#08080C",
        "onyx" to "#0F0F14",
        "nuit" to "#08080C"
    )

    private fun stripAccents(input: String): String {
        val norm = Normalizer.normalize(input, Normalizer.Form.NFD)
        return norm.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun parseColorInput(input: String): String? {
        val trimmed = input.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null

        val rawHex = if (trimmed.startsWith("#")) trimmed.drop(1) else trimmed
        if (rawHex.all { it in '0'..'9' || it in 'a'..'f' }) {
            if (rawHex.length == 6) {
                return "#" + rawHex.uppercase(Locale.ROOT)
            } else if (rawHex.length == 3) {
                val c = rawHex.uppercase(Locale.ROOT)
                return "#${c[0]}${c[0]}${c[1]}${c[1]}${c[2]}${c[2]}"
            }
        }

        foregrounds[trimmed]?.let { return it }
        backgrounds[trimmed]?.let { return it }
        namedColorsDictionary[trimmed]?.let { return it }

        val foldedInput = stripAccents(trimmed)
        namedColorsDictionary[foldedInput]?.let { return it }

        if (foldedInput.length >= 3) {
            val sortedKeys = namedColorsDictionary.keys.sortedBy { it.length }
            for (key in sortedKeys) {
                val foldedKey = stripAccents(key)
                if (foldedKey.startsWith(foldedInput)) {
                    return namedColorsDictionary[key]
                }
            }
            for (key in sortedKeys) {
                val foldedKey = stripAccents(key)
                if (foldedKey.contains(foldedInput) || foldedInput.contains(foldedKey)) {
                    return namedColorsDictionary[key]
                }
            }
        }
        return null
    }

    fun rgbComponents(hex: String): Triple<Double, Double, Double> {
        var cleaned = hex.trim().uppercase(Locale.ROOT)
        if (cleaned.startsWith("#")) cleaned = cleaned.drop(1)
        if (cleaned.length == 3) {
            cleaned = "${cleaned[0]}${cleaned[0]}${cleaned[1]}${cleaned[1]}${cleaned[2]}${cleaned[2]}"
        }
        val rgbValue = cleaned.toLongOrNull(16) ?: return Triple(0.0, 0.533, 1.0)
        val r = ((rgbValue and 0xFF0000) shr 16).toDouble() / 255.0
        val g = ((rgbValue and 0x00FF00) shr 8).toDouble() / 255.0
        val b = (rgbValue and 0x0000FF).toDouble() / 255.0
        return Triple(r, g, b)
    }

    fun relativeLuminance(hex: String): Double {
        val (r, g, b) = rgbComponents(hex)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun colorDistance(hex1: String, hex2: String): Double {
        val (r1, g1, b1) = rgbComponents(hex1)
        val (r2, g2, b2) = rgbComponents(hex2)
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return sqrt(dr * dr + dg * dg + db * db)
    }

    fun isBlackOnBlack(bgHex: String, fgHex: String): Boolean {
        return relativeLuminance(bgHex) < 0.18 && relativeLuminance(fgHex) < 0.18
    }

    fun isWhiteOnWhite(bgHex: String, fgHex: String): Boolean {
        return relativeLuminance(bgHex) > 0.82 && relativeLuminance(fgHex) > 0.82
    }

    fun displayName(hex: String): String {
        val normalized = hex.uppercase(Locale.ROOT)
        orderedForegrounds.firstOrNull { it.hex.uppercase(Locale.ROOT) == normalized }?.let { return it.name }
        orderedBackgrounds.firstOrNull { it.hex.uppercase(Locale.ROOT) == normalized }?.let { return it.name }
        namedColorsDictionary.entries.firstOrNull { it.value.uppercase(Locale.ROOT) == normalized }?.let {
            return it.key.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
        return normalized
    }

    fun composeColor(hex: String, fallback: Color = Color(0xFF0088FF)): Color {
        return try {
            val formatted = if (hex.startsWith("#")) hex else "#$hex"
            Color(AndroidColor.parseColor(formatted))
        } catch (_: Exception) {
            fallback
        }
    }
}
