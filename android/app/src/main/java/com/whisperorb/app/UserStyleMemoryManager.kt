package com.whisperorb.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

class UserStyleMemoryManager private constructor(private val context: Context) {

    companion object {
        const val FILE_NAME = "user_style_memory.json"

        @Volatile
        private var instance: UserStyleMemoryManager? = null

        fun getInstance(context: Context): UserStyleMemoryManager {
            return instance ?: synchronized(this) {
                instance ?: UserStyleMemoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var isMemoryEnabled: Boolean = true
    var autoCapitalizeStart: Boolean = true
    var stripTrailingPeriodOnShortMessages: Boolean = true
    var shortMessageMaxWords: Int = 12
    private val replacementsByLanguage: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    private val learnedCorrections: MutableMap<String, String> = mutableMapOf()

    private val memoryFile: File
        get() = File(context.filesDir, FILE_NAME)

    init {
        loadOrInitializeDefaults()
    }

    private fun defaultMap(): MutableMap<String, MutableMap<String, String>> {
        return mutableMapOf(
            "fr" to mutableMapOf(
                "t'inquiète pas" to "tkt",
                "ne t'inquiète pas" to "tkt",
                "t'inquiète" to "tkt",
                "c'est-à-dire" to "càd",
                "rendez-vous" to "rdv",
                "beaucoup" to "bcp",
                "aujourd'hui" to "ajd",
                "s'il te plaît" to "stp",
                "s'il vous plaît" to "svp"
            ),
            "en" to mutableMapOf(
                "by the way" to "btw",
                "to be honest" to "tbh",
                "in my opinion" to "imo",
                "right now" to "rn",
                "as soon as possible" to "asap",
                "i don't know" to "idk",
                "let me know" to "lmk"
            ),
            "es" to mutableMapOf(
                "por favor" to "pfv",
                "también" to "tb",
                "porque" to "pq",
                "fin de semana" to "finde"
            ),
            "de" to mutableMapOf(
                "zum beispiel" to "z.B.",
                "beziehungsweise" to "bzw.",
                "und so weiter" to "usw."
            ),
            "it" to mutableMapOf(
                "per favore" to "pf",
                "comunque" to "cmq"
            ),
            "pt" to mutableMapOf(
                "por favor" to "pfv",
                "também" to "tb",
                "você" to "vc"
            )
        )
    }

    fun loadOrInitializeDefaults() {
        if (!memoryFile.exists()) {
            resetToDefaults()
            return
        }
        try {
            val raw = memoryFile.readText(Charsets.UTF_8)
            val root = JSONObject(raw)
            isMemoryEnabled = root.optBoolean("isMemoryEnabled", true)
            autoCapitalizeStart = root.optBoolean("autoCapitalizeStart", true)
            stripTrailingPeriodOnShortMessages = root.optBoolean("stripTrailingPeriodOnShortMessages", true)
            shortMessageMaxWords = root.optInt("shortMessageMaxWords", 12)

            replacementsByLanguage.clear()
            val byLangObj = root.optJSONObject("replacementsByLanguage")
            if (byLangObj != null) {
                val langKeys = byLangObj.keys()
                while (langKeys.hasNext()) {
                    val lang = langKeys.next()
                    val innerObj = byLangObj.optJSONObject(lang) ?: continue
                    val map = mutableMapOf<String, String>()
                    val phraseKeys = innerObj.keys()
                    while (phraseKeys.hasNext()) {
                        val k = phraseKeys.next()
                        map[k] = innerObj.optString(k, "")
                    }
                    replacementsByLanguage[lang] = map
                }
            } else {
                replacementsByLanguage.putAll(defaultMap())
            }
        } catch (_: Exception) {
            resetToDefaults()
        }
    }

    fun saveToDisk() {
        try {
            val root = JSONObject()
            root.put("version", 2)
            root.put("isMemoryEnabled", isMemoryEnabled)
            root.put("autoCapitalizeStart", autoCapitalizeStart)
            root.put("stripTrailingPeriodOnShortMessages", stripTrailingPeriodOnShortMessages)
            root.put("shortMessageMaxWords", shortMessageMaxWords)

            val byLangObj = JSONObject()
            for ((lang, dict) in replacementsByLanguage) {
                val langObj = JSONObject()
                for ((spoken, written) in dict) {
                    langObj.put(spoken, written)
                }
                byLangObj.put(lang, langObj)
            }
            root.put("replacementsByLanguage", byLangObj)

            val learnedObj = JSONObject()
            for ((k, v) in learnedCorrections) {
                learnedObj.put(k, v)
            }
            root.put("learnedCorrections", learnedObj)

            memoryFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun applyStyleMemory(input: String, languageCode: String): String {
        if (!isMemoryEnabled) return input
        var result = input.trim()
        if (result.isEmpty()) return result

        val lang = languageCode.lowercase(Locale.ROOT).take(2).let {
            if (it.isEmpty() || it == "au") WhisperLanguageSelection.detectedSystemISOCode else it
        }

        val langDict = replacementsByLanguage[lang]
        if (langDict != null) {
            val sorted = langDict.entries.sortedByDescending { it.key.length }
            for ((spoken, written) in sorted) {
                if (spoken.isNotBlank()) {
                    val pattern = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(spoken)}(?![\\p{L}\\p{N}])")
                    result = pattern.replace(result, Regex.escapeReplacement(written))
                }
            }
        }

        if (stripTrailingPeriodOnShortMessages) {
            val wordCount = result.split(Regex("\\s+")).count { it.isNotBlank() }
            if (wordCount <= shortMessageMaxWords && result.endsWith(".") && !result.endsWith("...")) {
                result = result.dropLast(1)
            }
        }

        if (result.isNotEmpty() && result[0].isLetter()) {
            result = if (autoCapitalizeStart) {
                result[0].uppercaseChar() + result.substring(1)
            } else {
                result[0].lowercaseChar() + result.substring(1)
            }
        }

        return result
    }

    fun getReplacementsForLanguage(languageCode: String): List<Pair<String, String>> {
        val lang = languageCode.lowercase(Locale.ROOT).take(2)
        val map = replacementsByLanguage[lang] ?: emptyMap()
        return map.entries.map { it.key to it.value }.sortedBy { it.first.lowercase(Locale.ROOT) }
    }

    fun setReplacement(spoken: String, written: String, languageCode: String) {
        val cleanSpoken = spoken.trim().lowercase(Locale.ROOT)
        val cleanWritten = written.trim()
        if (cleanSpoken.isEmpty() || cleanWritten.isEmpty()) return
        val lang = languageCode.lowercase(Locale.ROOT).take(2)
        val dict = replacementsByLanguage.getOrPut(lang) { mutableMapOf() }
        dict[cleanSpoken] = cleanWritten
        saveToDisk()
    }

    fun removeReplacement(spoken: String, languageCode: String) {
        val lang = languageCode.lowercase(Locale.ROOT).take(2)
        replacementsByLanguage[lang]?.remove(spoken)
        saveToDisk()
    }

    fun resetToDefaults() {
        isMemoryEnabled = true
        autoCapitalizeStart = true
        stripTrailingPeriodOnShortMessages = true
        shortMessageMaxWords = 12
        replacementsByLanguage.clear()
        replacementsByLanguage.putAll(defaultMap())
        learnedCorrections.clear()
        saveToDisk()
    }

    fun prettyJSONString(): String {
        return try {
            if (memoryFile.exists()) memoryFile.readText(Charsets.UTF_8) else "{}"
        } catch (_: Exception) {
            "{}"
        }
    }
}
