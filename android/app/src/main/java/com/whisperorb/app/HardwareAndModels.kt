package com.whisperorb.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale
import kotlin.math.roundToInt

enum class WhisperOrbState {
    IDLE,
    LISTENING,
    TRANSCRIBING;

    val statusTitle: String
        get() = when (this) {
            IDLE -> "Au repos"
            LISTENING -> "Écoute en cours..."
            TRANSCRIBING -> "Transcription..."
        }
}

// MARK: - Sélecteur Intelligent de Langue Multilingue (Zéro "fr" forcé en dur)

enum class WhisperLanguageSelection(val code: String, val flagEmoji: String) {
    SYSTEM("system", "📱"),
    AUTO("auto", "🌐"),
    FR("fr", "🇫🇷"),
    EN("en", "🇬🇧"),
    ES("es", "🇪🇸"),
    DE("de", "🇩🇪"),
    IT("it", "🇮🇹"),
    PT("pt", "🇵🇹"),
    NL("nl", "🇳🇱"),
    PL("pl", "🇵🇱"),
    JA("ja", "🇯🇵"),
    KO("ko", "🇰🇷"),
    ZH("zh", "🇨🇳"),
    AR("ar", "🇸🇦"),
    RU("ru", "🇷🇺");

    companion object {
        private val supportedCodes = setOf("fr", "en", "es", "de", "it", "pt", "nl", "pl", "ja", "ko", "zh", "ar", "ru")

        val detectedSystemISOCode: String
            get() {
                val sys = Locale.getDefault().language.lowercase(Locale.ROOT).take(2)
                return if (supportedCodes.contains(sys)) sys else "en"
            }

        fun fromCode(raw: String?): WhisperLanguageSelection {
            return entries.firstOrNull { it.code == raw } ?: SYSTEM
        }
    }

    val resolvedWhisperCode: String
        get() = when (this) {
            SYSTEM -> detectedSystemISOCode
            AUTO -> "auto"
            else -> code
        }

    val effectiveStyleLanguageCode: String
        get() = when (this) {
            SYSTEM, AUTO -> detectedSystemISOCode
            else -> code
        }

    val displayName: String
        get() = when (this) {
            SYSTEM -> "Langue du système (${detectedSystemISOCode.uppercase()} • Recommandé)"
            AUTO -> "Auto-détection Whisper (Medium / Turbo / TITAN)"
            FR -> "Français (FR)"
            EN -> "English (EN)"
            ES -> "Español (ES)"
            DE -> "Deutsch (DE)"
            IT -> "Italiano (IT)"
            PT -> "Português (PT)"
            NL -> "Nederlands (NL)"
            PL -> "Polski (PL)"
            JA -> "日本語 (JA)"
            KO -> "한국어 (KO)"
            ZH -> "中文 (ZH)"
            AR -> "العربية (AR)"
            RU -> "Русский (RU)"
        }

    val shortPillLabel: String
        get() = when (this) {
            SYSTEM -> "📱 ${detectedSystemISOCode.uppercase()}"
            AUTO -> "🌐 AUTO"
            else -> "$flagEmoji ${code.uppercase()}"
        }

    val initialPunctuationPrompt: String
        get() = when (effectiveStyleLanguageCode) {
            "fr" -> "Bonjour,"
            "en" -> "Hello,"
            "es" -> "Hola,"
            "de" -> "Hallo,"
            "it" -> "Ciao,"
            "pt" -> "Olá,"
            "nl" -> "Hallo,"
            "pl" -> "Cześć,"
            "ja" -> "こんにちは、"
            "ko" -> "안녕하세요,"
            "zh" -> "你好，"
            "ar" -> "مرحباً،"
            "ru" -> "Привет,"
            else -> "Hello,"
        }
}

// MARK: - Catalogue Complet des 7 Modèles Whisper (De Nano 31 Mo jusqu'à TITAN Large-v3 1,08 Go)

enum class WhisperModelTier(
    val id: String,
    val displayName: String,
    val shortBadgeName: String,
    val subtitle: String,
    val fileName: String,
    val sizeMB: Int,
    val ramUsageMB: Int,
    val minimumRequiredRAMGB: Double,
    val requiredRAMBadgeText: String,
    val latencyDescription: String
) {
    NANO(
        id = "nano",
        displayName = "⚡ Whisper Nano",
        shortBadgeName = "Whisper Nano",
        subtitle = "Ultra-léger (Tiny Q5) • Réponse éclair sur tous les appareils",
        fileName = "ggml-tiny-q5_1.bin",
        sizeMB = 31,
        ramUsageMB = 120,
        minimumRequiredRAMGB = 2.0,
        requiredRAMBadgeText = "2 Go+ RAM",
        latencyDescription = "~0,11 s"
    ),
    ECLAIR(
        id = "eclair",
        displayName = "⚡ Whisper Flash",
        shortBadgeName = "Whisper Flash",
        subtitle = "Instantané (Base Q5) • Messages courts & notes rapides",
        fileName = "ggml-base-q5_1.bin",
        sizeMB = 60,
        ramUsageMB = 190,
        minimumRequiredRAMGB = 2.8,
        requiredRAMBadgeText = "3 Go+ RAM",
        latencyDescription = "~0,18 s"
    ),
    NORMAL(
        id = "normal",
        displayName = "⚖️ Whisper PRO",
        shortBadgeName = "Whisper PRO",
        subtitle = "Équilibré (Small Q5) • Dictée quotidienne fluide & précise",
        fileName = "ggml-small-q5_1.bin",
        sizeMB = 190,
        ramUsageMB = 380,
        minimumRequiredRAMGB = 3.6,
        requiredRAMBadgeText = "4 Go+ RAM",
        latencyDescription = "~0,32 s"
    ),
    MEDIUM(
        id = "medium",
        displayName = "🔥 Whisper Medium",
        shortBadgeName = "Whisper Medium",
        subtitle = "Haute fidélité (Medium Q5) • Vocabulaire riche & multilingue",
        fileName = "ggml-medium-q5_0.bin",
        sizeMB = 539,
        ramUsageMB = 900,
        minimumRequiredRAMGB = 5.4,
        requiredRAMBadgeText = "6 Go+ RAM",
        latencyDescription = "~0,68 s"
    ),
    ZERO_FAUTE(
        id = "zeroFaute",
        displayName = "🚀 Whisper Expert (v3 Turbo)",
        shortBadgeName = "Whisper Expert",
        subtitle = "Large-v3 Turbo Q5 • Très haute précision & faible latence",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        sizeMB = 540,
        ramUsageMB = 950,
        minimumRequiredRAMGB = 5.4,
        requiredRAMBadgeText = "6 Go+ RAM",
        latencyDescription = "~0,52 s"
    ),
    EXPERT_PLUS(
        id = "expertPlus",
        displayName = "💎 Whisper Expert+ (Turbo Q8)",
        shortBadgeName = "Expert+ Q8",
        subtitle = "Large-v3 Turbo Q8 (8-bit) • Fidélité studio quasi sans perte",
        fileName = "ggml-large-v3-turbo-q8_0.bin",
        sizeMB = 874,
        ramUsageMB = 1320,
        minimumRequiredRAMGB = 7.4,
        requiredRAMBadgeText = "8 Go+ RAM",
        latencyDescription = "~0,64 s"
    ),
    TITAN(
        id = "titan",
        displayName = "👑 Whisper TITAN (Large-v3 Full)",
        shortBadgeName = "Whisper TITAN",
        subtitle = "Large-v3 Complet (1,55 Md params) • Le modèle maître absolu",
        fileName = "ggml-large-v3-q5_0.bin",
        sizeMB = 1080,
        ramUsageMB = 2150,
        minimumRequiredRAMGB = 11.2,
        requiredRAMBadgeText = "12 Go+ RAM",
        latencyDescription = "~0,98 s"
    );

    val downloadURL: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        fun fromId(raw: String?): WhisperModelTier {
            return entries.firstOrNull { it.id == raw } ?: NORMAL
        }
    }
}

object HardwareDetector {
    fun getPhysicalMemoryGB(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 8.0
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    fun getEffectiveMemoryGB(context: Context, simulate4GB: Boolean): Double {
        return if (simulate4GB) 4.0 else getPhysicalMemoryGB(context)
    }

    fun isTierUnlocked(context: Context, tier: WhisperModelTier, simulate4GB: Boolean): Boolean {
        return getEffectiveMemoryGB(context, simulate4GB) >= tier.minimumRequiredRAMGB
    }

    fun unlockedTiers(context: Context, simulate4GB: Boolean): List<WhisperModelTier> {
        return WhisperModelTier.entries.filter { isTierUnlocked(context, it, simulate4GB) }
    }

    fun recommendedTier(context: Context, simulate4GB: Boolean): WhisperModelTier {
        val ram = getEffectiveMemoryGB(context, simulate4GB)
        return when {
            ram >= 11.2 -> WhisperModelTier.TITAN
            ram >= 7.4 -> WhisperModelTier.EXPERT_PLUS
            ram >= 5.4 -> WhisperModelTier.ZERO_FAUTE
            ram >= 3.6 -> WhisperModelTier.NORMAL
            else -> WhisperModelTier.ECLAIR
        }
    }

    fun getSocMarketingName(): String {
        val rawSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != "unknown" } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }
        val model = Build.MODEL ?: ""
        return when {
            rawSoc.contains("Tensor G4", ignoreCase = true) || model.contains("Pixel 9", ignoreCase = true) ->
                "Google Tensor G4"
            rawSoc.contains("Tensor G3", ignoreCase = true) || model.contains("Pixel 8", ignoreCase = true) ->
                "Google Tensor G3"
            rawSoc.contains("Tensor G2", ignoreCase = true) || model.contains("Pixel 7", ignoreCase = true) ->
                "Google Tensor G2"
            else -> rawSoc
        }
    }

    fun getTelemetrySummary(context: Context): String {
        val modelName = Build.MODEL ?: "Android"
        val socName = getSocMarketingName()
        val ramGB = getPhysicalMemoryGB(context).roundToInt()
        return "$modelName — $socName • $ramGB Go RAM"
    }
}
