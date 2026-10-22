package com.whisperorb.app

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class AutocorrectSuggestionSet(
    val typedWord: String,
    val primaryCorrection: String,
    val secondaryPrediction: String,
    val tertiaryPrediction: String,
    val shouldAutoReplaceOnSpace: Boolean
) {
    companion object {
        val EMPTY = AutocorrectSuggestionSet(
            typedWord = "",
            primaryCorrection = "Je",
            secondaryPrediction = "Bonjour",
            tertiaryPrediction = "Merci",
            shouldAutoReplaceOnSpace = false
        )
    }
}

object SmartAutocorrectEngine {

    private val instantCorrectionsByLang: Map<String, Map<String, String>> = mapOf(
        "fr" to mapOf(
            "jarrive" to "j'arrive",
            "cest" to "c'est",
            "cetait" to "c'était",
            "jai" to "j'ai",
            "jetais" to "j'étais",
            "javais" to "j'avais",
            "jaime" to "j'aime",
            "jattends" to "j'attends",
            "jespere" to "j'espère",
            "nest" to "n'est",
            "netait" to "n'était",
            "nai" to "n'ai",
            "nas" to "n'as",
            "na" to "n'a",
            "quon" to "qu'on",
            "quil" to "qu'il",
            "quils" to "qu'ils",
            "quelle" to "qu'elle",
            "quelles" to "qu'elles",
            "daccord" to "d'accord",
            "dailleurs" to "d'ailleurs",
            "dabord" to "d'abord",
            "aujourdhui" to "aujourd'hui",
            "tinquiete" to "t'inquiète",
            "peutetre" to "peut-être",
            "meme" to "même",
            "memes" to "mêmes",
            "deja" to "déjà",
            "tres" to "très",
            "apres" to "après",
            "pres" to "près",
            "etre" to "être",
            "ete" to "été",
            "probleme" to "problème",
            "problemes" to "problèmes",
            "telephone" to "téléphone",
            "desole" to "désolé",
            "desolee" to "désolée",
            "voila" to "voilà",
            "plutot" to "plutôt",
            "bientot" to "bientôt",
            "evidemment" to "évidemment",
            "interessant" to "intéressant",
            "prefere" to "préfère",
            "numero" to "numéro",
            "ecole" to "école",
            "ecoute" to "écoute",
            "ecrire" to "écrire",
            "repondre" to "répondre",
            "reponds" to "réponds",
            "repondu" to "répondu",
            "ca" to "ça",
            "francais" to "français",
            "francaise" to "française",
            "premiere" to "première",
            "derniere" to "dernière",
            "maniere" to "manière",
            "lumiere" to "lumière",
            "annee" to "année",
            "journee" to "journée",
            "soiree" to "soirée",
            "idee" to "idée",
            "genial" to "génial",
            "securite" to "sécurité",
            "qualite" to "qualité",
            "verite" to "vérité",
            "realite" to "réalité",
            "cote" to "côté",
            "arrete" to "arrête",
            "arreter" to "arrêter",
            "tete" to "tête",
            "fete" to "fête",
            "foret" to "forêt",
            "interet" to "intérêt",
            "systeme" to "système",
            "modele" to "modèle",
            "modeles" to "modèles",
            "regle" to "règle",
            "regles" to "règles",
            "completement" to "complètement",
            "immediatement" to "immédiatement",
            "generalement" to "généralement",
            "precisement" to "précisément"
        ),
        "en" to mapOf(
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "im" to "I'm",
            "ive" to "I've",
            "id" to "I'd",
            "ill" to "I'll",
            "youre" to "you're",
            "theyre" to "they're",
            "weve" to "we've",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "hasnt" to "hasn't",
            "havent" to "haven't",
            "hadnt" to "hadn't",
            "doesnt" to "doesn't",
            "didnt" to "didn't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
            "thats" to "that's",
            "whats" to "what's",
            "lets" to "let's",
            "heres" to "here's",
            "theres" to "there's",
            "wheres" to "where's",
            "teh" to "the",
            "recieve" to "receive",
            "definately" to "definitely",
            "seperate" to "separate",
            "occured" to "occurred",
            "untill" to "until",
            "alot" to "a lot",
            "becuase" to "because",
            "wierd" to "weird",
            "acheive" to "achieve",
            "beleive" to "believe",
            "tommorow" to "tomorrow"
        ),
        "es" to mapOf(
            "tambien" to "también",
            "ademas" to "además",
            "despues" to "después",
            "habia" to "había",
            "facil" to "fácil",
            "dificil" to "difícil",
            "telefono" to "teléfono",
            "numero" to "número",
            "rapido" to "rápido",
            "perdon" to "perdón",
            "cancion" to "canción",
            "informacion" to "información"
        ),
        "de" to mapOf(
            "fur" to "für",
            "konnen" to "können",
            "mussen" to "müssen",
            "naturlich" to "natürlich",
            "schon" to "schön",
            "zuruck" to "zurück",
            "uber" to "über",
            "spater" to "später",
            "offnen" to "öffnen"
        ),
        "it" to mapOf(
            "perche" to "perché",
            "piu" to "più",
            "cosi" to "così",
            "gia" to "già",
            "puo" to "può",
            "citta" to "città",
            "verita" to "verità"
        ),
        "pt" to mapOf(
            "nao" to "não",
            "voce" to "você",
            "tambem" to "também",
            "entao" to "então",
            "ate" to "até",
            "ja" to "já",
            "informacao" to "informação"
        )
    )

    private val frequentLexiconByLang: Map<String, List<String>> = mapOf(
        "fr" to listOf(
            "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
            "bonjour", "bonsoir", "salut", "merci", "beaucoup", "oui", "non", "d'accord", "super", "parfait",
            "comment", "pourquoi", "quand", "combien", "vraiment", "maintenant", "aujourd'hui", "demain", "hier",
            "toujours", "jamais", "encore", "peut-être", "aussi", "avec", "sans", "pour", "dans", "mais",
            "c'est", "j'ai", "j'arrive", "t'inquiète", "problème", "téléphone", "clavier", "application", "modèle",
            "message", "question", "réponse", "travail", "maison", "heure", "minute", "semaine", "journée",
            "excellent", "génial", "exactement", "absolument", "évidemment", "directement", "complètement",
            "possible", "important", "nécessaire", "disponible", "différent", "nouveau", "nouvelle", "premier", "dernière"
        ),
        "en" to listOf(
            "the", "and", "that", "have", "for", "not", "with", "you", "this", "but",
            "hello", "thanks", "thank", "please", "great", "perfect", "awesome", "good", "morning", "tonight",
            "because", "really", "actually", "probably", "definitely", "tomorrow", "yesterday", "today", "always",
            "never", "something", "everything", "nothing", "anything", "someone", "everyone", "important", "available",
            "different", "question", "answer", "message", "keyboard", "application", "working", "looking", "thinking"
        ),
        "es" to listOf(
            "hola", "gracias", "buenos", "días", "buenas", "noches", "por", "favor", "mucho", "todo",
            "bien", "perfecto", "genial", "ahora", "mañana", "hoy", "siempre", "nunca", "también", "después",
            "porque", "cuando", "donde", "como", "problema", "teléfono", "mensaje", "pregunta", "respuesta"
        ),
        "de" to listOf(
            "hallo", "danke", "bitte", "guten", "morgen", "abend", "heute", "immer", "vielleicht",
            "natürlich", "wirklich", "wichtig", "perfekt", "super", "warum", "wann", "wie", "problem", "nachricht"
        ),
        "it" to listOf(
            "ciao", "grazie", "buongiorno", "buonasera", "perfetto", "bene", "molto", "sempre", "oggi", "domani",
            "perché", "quando", "dove", "come", "problema", "telefono", "messaggio", "domanda", "risposta"
        ),
        "pt" to listOf(
            "olá", "obrigado", "obrigada", "bom", "dia", "boa", "noite", "tudo", "bem", "perfeito",
            "sempre", "hoje", "amanhã", "também", "então", "porque", "quando", "problema", "mensagem"
        )
    )

    private val nextWordBigramsByLang: Map<String, Map<String, List<String>>> = mapOf(
        "fr" to mapOf(
            "" to listOf("Je", "Bonjour", "Merci"),
            "je" to listOf("suis", "vais", "pense"),
            "j'ai" to listOf("fait", "vu", "besoin"),
            "tu" to listOf("es", "as", "peux"),
            "il" to listOf("est", "faut", "y"),
            "on" to listOf("va", "peut", "est"),
            "c'est" to listOf("super", "parfait", "bon"),
            "bonjour" to listOf("à", "comment", "je"),
            "salut" to listOf("comment", "ça", "tu"),
            "comment" to listOf("ça", "vas-tu", "allez-vous"),
            "ça" to listOf("va", "marche", "avance"),
            "merci" to listOf("beaucoup", "pour", "d'avance"),
            "à" to listOf("bientôt", "tout", "demain"),
            "pas" to listOf("de", "du", "encore"),
            "très" to listOf("bien", "bon", "vite")
        ),
        "en" to mapOf(
            "" to listOf("I", "Hello", "Thanks"),
            "i" to listOf("am", "think", "have"),
            "i'm" to listOf("going", "on", "ready"),
            "you" to listOf("are", "can", "have"),
            "thank" to listOf("you", "goodness", "so"),
            "thanks" to listOf("for", "a", "so"),
            "how" to listOf("are", "is", "about"),
            "let's" to listOf("go", "do", "see")
        ),
        "es" to mapOf(
            "" to listOf("Hola", "Gracias", "Yo"),
            "hola" to listOf("cómo", "qué", "buenos"),
            "muchas" to listOf("gracias", "veces", "cosas"),
            "por" to listOf("favor", "eso", "supuesto")
        ),
        "de" to mapOf(
            "" to listOf("Ich", "Hallo", "Danke"),
            "ich" to listOf("bin", "habe", "kann")
        ),
        "it" to mapOf(
            "" to listOf("Ciao", "Grazie", "Io"),
            "grazie" to listOf("mille", "di", "per")
        ),
        "pt" to mapOf(
            "" to listOf("Olá", "Obrigado", "Eu"),
            "tudo" to listOf("bem", "bom", "certo")
        )
    )

    private val keyCoordinatesAZERTY: Map<Char, Pair<Double, Double>> = mapOf(
        'a' to (0.0 to 0.0), 'z' to (1.0 to 0.0), 'e' to (2.0 to 0.0), 'r' to (3.0 to 0.0), 't' to (4.0 to 0.0),
        'y' to (5.0 to 0.0), 'u' to (6.0 to 0.0), 'i' to (7.0 to 0.0), 'o' to (8.0 to 0.0), 'p' to (9.0 to 0.0),
        'q' to (0.0 to 1.0), 's' to (1.0 to 1.0), 'd' to (2.0 to 1.0), 'f' to (3.0 to 1.0), 'g' to (4.0 to 1.0),
        'h' to (5.0 to 1.0), 'j' to (6.0 to 1.0), 'k' to (7.0 to 1.0), 'l' to (8.0 to 1.0), 'm' to (9.0 to 1.0),
        'w' to (1.5 to 2.0), 'x' to (2.5 to 2.0), 'c' to (3.5 to 2.0), 'v' to (4.5 to 2.0), 'b' to (5.5 to 2.0), 'n' to (6.5 to 2.0)
    )

    fun computeSuggestions(
        context: Context,
        contextBeforeCursor: String,
        languageCode: String
    ): AutocorrectSuggestionSet {
        val lang = normalizeLang(languageCode)
        val (previousWord, currentPrefix) = extractLastTwoTokens(contextBeforeCursor)

        if (currentPrefix.isEmpty()) {
            val nextCandidates = predictNextWords(previousWord, lang)
            return AutocorrectSuggestionSet(
                typedWord = "",
                primaryCorrection = nextCandidates.getOrElse(0) { "Je" },
                secondaryPrediction = nextCandidates.getOrElse(1) { "Bonjour" },
                tertiaryPrediction = nextCandidates.getOrElse(2) { "Merci" },
                shouldAutoReplaceOnSpace = false
            )
        }

        val lowerTyped = currentPrefix.lowercase(Locale.ROOT)
        val isCapitalized = currentPrefix.firstOrNull()?.isUpperCase() == true
        val isAllCaps = currentPrefix.length > 1 && currentPrefix == currentPrefix.uppercase(Locale.ROOT)

        // 1. Respect absolu de `user_style_memory.json`
        val memoryPairs = UserStyleMemoryManager.getInstance(context).getReplacementsForLanguage(lang)
        for ((spoken, written) in memoryPairs) {
            if (written.lowercase(Locale.ROOT) == lowerTyped) {
                val expanded = applyCasing(spoken, isCapitalized, isAllCaps)
                return AutocorrectSuggestionSet(
                    typedWord = currentPrefix,
                    primaryCorrection = currentPrefix,
                    secondaryPrediction = expanded,
                    tertiaryPrediction = predictNextWords(currentPrefix, lang).firstOrNull() ?: "👍",
                    shouldAutoReplaceOnSpace = false
                )
            }
            if (spoken.lowercase(Locale.ROOT) == lowerTyped) {
                val targetWritten = applyCasing(written, isCapitalized, isAllCaps)
                return AutocorrectSuggestionSet(
                    typedWord = currentPrefix,
                    primaryCorrection = targetWritten,
                    secondaryPrediction = currentPrefix,
                    tertiaryPrediction = predictNextWords(targetWritten, lang).firstOrNull() ?: "👍",
                    shouldAutoReplaceOnSpace = true
                )
            }
        }

        // 2. Dictionnaire instantané d'apostrophes & d'accents
        val instantMap = instantCorrectionsByLang[lang]
        if (instantMap != null && instantMap.containsKey(lowerTyped)) {
            val directFix = instantMap[lowerTyped]!!
            val casedFix = applyCasing(directFix, isCapitalized, isAllCaps)
            val shouldReplace = !casedFix.equals(currentPrefix, ignoreCase = true)
            val alt = findPrefixCompletions(lowerTyped, lang, listOf(casedFix, currentPrefix))
            return AutocorrectSuggestionSet(
                typedWord = currentPrefix,
                primaryCorrection = casedFix,
                secondaryPrediction = alt.firstOrNull() ?: currentPrefix,
                tertiaryPrediction = alt.drop(1).firstOrNull() ?: "👍",
                shouldAutoReplaceOnSpace = shouldReplace
            )
        }

        // 3. Complétion de préfixe et distance géométrique
        val lexicon = frequentLexiconByLang[lang] ?: frequentLexiconByLang["en"]!!
        val isExactKnown = lexicon.any { it.equals(lowerTyped, ignoreCase = true) }
        val completions = findPrefixCompletions(lowerTyped, lang, emptyList())

        if (isExactKnown) {
            val bestComp = completions.firstOrNull { !it.equals(currentPrefix, ignoreCase = true) }
                ?.let { applyCasing(it, isCapitalized, isAllCaps) }
                ?: currentPrefix
            val secondComp = completions.drop(1).firstOrNull { !it.equals(currentPrefix, ignoreCase = true) }
                ?.let { applyCasing(it, isCapitalized, isAllCaps) }
                ?: predictNextWords(currentPrefix, lang).firstOrNull()
                ?: "👍"

            return AutocorrectSuggestionSet(
                typedWord = currentPrefix,
                primaryCorrection = currentPrefix,
                secondaryPrediction = bestComp,
                tertiaryPrediction = secondComp,
                shouldAutoReplaceOnSpace = false
            )
        }

        val closest = lexicon
            .filter { abs(it.length - lowerTyped.length) <= 2 }
            .map { it to geometricDistanceScore(lowerTyped, it) }
            .filter { it.second <= 2.4 }
            .sortedBy { it.second }

        if (closest.isNotEmpty()) {
            val (bestWord, dist) = closest.first()
            val casedBest = applyCasing(bestWord, isCapitalized, isAllCaps)
            val autoReplace = dist <= 1.85 && currentPrefix.length >= 2
            val secondWord = closest.drop(1).firstOrNull()?.first
                ?.let { applyCasing(it, isCapitalized, isAllCaps) }
                ?: completions.firstOrNull()?.let { applyCasing(it, isCapitalized, isAllCaps) }
                ?: currentPrefix

            return AutocorrectSuggestionSet(
                typedWord = currentPrefix,
                primaryCorrection = casedBest,
                secondaryPrediction = secondWord,
                tertiaryPrediction = completions.firstOrNull() ?: currentPrefix,
                shouldAutoReplaceOnSpace = autoReplace
            )
        }

        val primaryComp = completions.firstOrNull()?.let { applyCasing(it, isCapitalized, isAllCaps) } ?: currentPrefix
        val secondaryComp = completions.drop(1).firstOrNull()?.let { applyCasing(it, isCapitalized, isAllCaps) } ?: currentPrefix

        return AutocorrectSuggestionSet(
            typedWord = currentPrefix,
            primaryCorrection = primaryComp,
            secondaryPrediction = secondaryComp,
            tertiaryPrediction = currentPrefix,
            shouldAutoReplaceOnSpace = false
        )
    }

    private fun predictNextWords(previousWord: String, lang: String): List<String> {
        val lowerPrev = previousWord.lowercase(Locale.ROOT)
        val map = nextWordBigramsByLang[lang] ?: nextWordBigramsByLang["en"]!!
        return map[lowerPrev] ?: map[""] ?: listOf("Je", "Bonjour", "Merci")
    }

    private fun findPrefixCompletions(prefix: String, lang: String, excluding: List<String>): List<String> {
        val words = frequentLexiconByLang[lang] ?: frequentLexiconByLang["en"]!!
        val normPrefix = stripDiacritics(prefix)
        val results = mutableListOf<String>()
        for (w in words) {
            val normW = stripDiacritics(w)
            if ((w.startsWith(prefix) || normW.startsWith(normPrefix)) &&
                !w.equals(prefix, ignoreCase = true) &&
                excluding.none { it.equals(w, ignoreCase = true) }
            ) {
                results.add(w)
                if (results.size >= 4) break
            }
        }
        return results
    }

    private fun geometricDistanceScore(typed: String, candidate: String): Double {
        val normTyped = stripDiacritics(typed)
        val normCand = stripDiacritics(candidate)
        if (normTyped == normCand) return 0.25
        val editDist = levenshtein(normTyped, normCand).toDouble()
        if (normTyped.length == normCand.length) {
            var penalty = 0.0
            for (i in normTyped.indices) {
                val c1 = normTyped[i]
                val c2 = normCand[i]
                if (c1 != c2) {
                    val p1 = keyCoordinatesAZERTY[c1]
                    val p2 = keyCoordinatesAZERTY[c2]
                    if (p1 != null && p2 != null) {
                        val dx = p1.first - p2.first
                        val dy = p1.second - p2.second
                        val d = sqrt(dx * dx + dy * dy)
                        penalty += if (d <= 1.5) 0.65 else 1.35
                    } else {
                        penalty += 1.2
                    }
                }
            }
            return penalty
        }
        return editDist * 1.15
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        var prev = IntArray(s2.length + 1) { it }
        var curr = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            curr[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[s2.length]
    }

    private fun stripDiacritics(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("'", "")
            .replace("’", "")
            .lowercase(Locale.ROOT)
    }

    private fun applyCasing(word: String, isCapitalized: Boolean, isAllCaps: Boolean): String {
        if (isAllCaps) return word.uppercase(Locale.ROOT)
        if (isCapitalized && word.isNotEmpty()) {
            return word[0].uppercaseChar() + word.substring(1)
        }
        return word
    }

    private fun extractLastTwoTokens(text: String): Pair<String, String> {
        if (text.isEmpty()) return "" to ""
        val lastChar = text.last()
        if (lastChar.isWhitespace() || ".,!?;:\n".contains(lastChar)) {
            val tokens = text.trim().split(Regex("\\s+"))
            val prev = tokens.lastOrNull()?.trim('.', ',', '!', '?', ';', ':') ?: ""
            return prev to ""
        }
        val parts = text.split(Regex("\\s+"))
        val current = parts.lastOrNull() ?: ""
        val prev = parts.dropLast(1).lastOrNull()?.trim('.', ',', '!', '?', ';', ':') ?: ""
        return prev to current
    }

    private fun normalizeLang(code: String): String {
        val lower = code.lowercase(Locale.ROOT)
        return if (setOf("fr", "en", "es", "de", "it", "pt").contains(lower)) lower
        else WhisperLanguageSelection.detectedSystemISOCode
    }
}
