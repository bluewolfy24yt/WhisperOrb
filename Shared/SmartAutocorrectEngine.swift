import Foundation
import UIKit

public struct AutocorrectSuggestionSet: Equatable {
    public let typedWord: String
    public let primaryCorrection: String
    public let secondaryPrediction: String
    public let tertiaryPrediction: String
    public let shouldAutoReplaceOnSpace: Bool

    public init(
        typedWord: String,
        primaryCorrection: String,
        secondaryPrediction: String,
        tertiaryPrediction: String,
        shouldAutoReplaceOnSpace: Bool
    ) {
        self.typedWord = typedWord
        self.primaryCorrection = primaryCorrection
        self.secondaryPrediction = secondaryPrediction
        self.tertiaryPrediction = tertiaryPrediction
        self.shouldAutoReplaceOnSpace = shouldAutoReplaceOnSpace
    }

    public static let empty = AutocorrectSuggestionSet(
        typedWord: "",
        primaryCorrection: "Je",
        secondaryPrediction: "Bonjour",
        tertiaryPrediction: "Merci",
        shouldAutoReplaceOnSpace: false
    )
}

/// Moteur hybride ultra-rapide (< 0,2 ms) de correction orthographique, d'apostrophes/accents automatiques
/// et de prédiction contextuelle à 3 choix, connecté à `user_style_memory.json` et au lexique système `UITextChecker`.
public final class SmartAutocorrectEngine {
    public static let shared = SmartAutocorrectEngine()

    private let textChecker = UITextChecker()

    // MARK: - Dictionnaire Instantané d'Apostrophes, d'Accents et de Fautes de Frappe Fréquentes par Langue

    private let instantCorrectionsByLang: [String: [String: String]] = [
        "fr": [
            "jarrive": "j'arrive",
            "cest": "c'est",
            "cetait": "c'était",
            "jai": "j'ai",
            "jetais": "j'étais",
            "javais": "j'avais",
            "jaime": "j'aime",
            "jattends": "j'attends",
            "jespere": "j'espère",
            "nest": "n'est",
            "netait": "n'était",
            "nai": "n'ai",
            "nas": "n'as",
            "na": "n'a",
            "quon": "qu'on",
            "quil": "qu'il",
            "quils": "qu'ils",
            "quelle": "qu'elle",
            "quelles": "qu'elles",
            "queje": "que je",
            "daccord": "d'accord",
            "dailleurs": "d'ailleurs",
            "dabord": "d'abord",
            "aujourdhui": "aujourd'hui",
            "tinquiete": "t'inquiète",
            "peutetre": "peut-être",
            "meme": "même",
            "memes": "mêmes",
            "deja": "déjà",
            "tres": "très",
            "apres": "après",
            "pres": "près",
            "etre": "être",
            "ete": "été",
            "probleme": "problème",
            "problemes": "problèmes",
            "telephone": "téléphone",
            "desole": "désolé",
            "desolee": "désolée",
            "voila": "voilà",
            "plutot": "plutôt",
            "bientot": "bientôt",
            "evidemment": "évidemment",
            "interessant": "intéressant",
            "prefere": "préfère",
            "numero": "numéro",
            "ecole": "école",
            "ecoute": "écoute",
            "ecrire": "écrire",
            "repondre": "répondre",
            "reponds": "réponds",
            "repondu": "répondu",
            "ca": "ça",
            " francais": "français",
            "francais": "français",
            "francaise": "française",
            "premiere": "première",
            "derniere": "dernière",
            "maniere": "manière",
            "lumiere": "lumière",
            "annee": "année",
            "journee": "journée",
            "soiree": "soirée",
            "idee": "idée",
            " génial": "génial",
            "genial": "génial",
            "securite": "sécurité",
            "qualite": "qualité",
            "verite": "vérité",
            "realite": "réalité",
            "universite": "université",
            "cote": "côté",
            "arrete": "arrête",
            "arreter": "arrêter",
            "tete": "tête",
            "fete": "fête",
            "foret": "forêt",
            "interet": "intérêt",
            "systeme": "système",
            "modele": "modèle",
            "modeles": "modèles",
            "regle": "règle",
            "regles": "règles",
            "completement": "complètement",
            "immediatement": "immédiatement",
            "generalement": "généralement",
            "precisement": "précisément",
            "bcp": "bcp",
            "tkt": "tkt",
            "mdr": "mdr",
            "ptdr": "ptdr",
            "jsp": "jsp",
            "stp": "stp",
            "svp": "svp",
            "rdv": "rdv",
            "msg": "msg",
            "pb": "pb",
            "cad": "c-à-d"
        ],
        "en": [
            "dont": "don't",
            "cant": "can't",
            "wont": "won't",
            "im": "I'm",
            "ive": "I've",
            "id": "I'd",
            "ill": "I'll",
            "youre": "you're",
            "theyre": "they're",
            "weve": "we've",
            "isnt": "isn't",
            "arent": "aren't",
            "wasnt": "wasn't",
            "werent": "weren't",
            "hasnt": "hasn't",
            "havent": "haven't",
            "hadnt": "hadn't",
            "doesnt": "doesn't",
            "didnt": "didn't",
            "couldnt": "couldn't",
            "shouldnt": "shouldn't",
            "wouldnt": "wouldn't",
            "thats": "that's",
            "whats": "what's",
            "lets": "let's",
            "heres": "here's",
            "theres": "there's",
            "wheres": "where's",
            "teh": "the",
            "recieve": "receive",
            "definately": "definitely",
            "seperate": "separate",
            "occured": "occurred",
            "untill": "until",
            "alot": "a lot",
            "becuase": "because",
            "wierd": "weird",
            "acheive": "achieve",
            "beleive": "believe",
            "tommorow": "tomorrow",
            "enviroment": "environment",
            "goverment": "government"
        ],
        "es": [
            "tambien": "también",
            "ademas": "además",
            "despues": "después",
            "habia": "había",
            "estabamos": "estábamos",
            "facil": "fácil",
            "dificil": "difícil",
            "telefono": "teléfono",
            "numero": "número",
            "rapido": "rápido",
            "perdón": "perdón",
            "perdon": "perdón",
            "cancion": "canción",
            "informacion": "información"
        ],
        "de": [
            "fur": "für",
            "konnen": "können",
            "mussen": "müssen",
            "naturlich": "natürlich",
            "schon": "schön",
            "zuruck": "zurück",
            "uber": "über",
            "spater": "später",
            "offnen": "öffnen"
        ],
        "it": [
            "perche": "perché",
            "piu": "più",
            "cosi": "così",
            "gia": "già",
            "puo": "può",
            "citta": "città",
            "universita": "università",
            "verita": "verità"
        ],
        "pt": [
            "nao": "não",
            "voce": "você",
            "tambem": "também",
            "entao": "então",
            "ate": "até",
            "ja": "já",
            "obrigatorio": "obrigatório",
            "informacao": "informação"
        ]
    ]

    // MARK: - Lexique Haute Fréquence par Langue (Complétion & Correction Géométrique)

    private let frequentLexiconByLang: [String: [String]] = [
        "fr": [
            "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
            "bonjour", "bonsoir", "salut", "merci", "beaucoup", "oui", "non", "d'accord", "super", "parfait",
            "comment", "pourquoi", "quand", "combien", "vraiment", "maintenant", "aujourd'hui", "demain", "hier",
            "toujours", "jamais", "encore", "peut-être", "aussi", "avec", "sans", "pour", "dans", "mais",
            "c'est", "j'ai", "j'arrive", "t'inquiète", "problème", "téléphone", "clavier", "application", "modèle",
            "message", "question", "réponse", "travail", "maison", "heure", "minute", "semaine", "journée",
            "excellent", "génial", "exactement", "absolument", "évidemment", "directement", "complètement",
            "possible", "important", "nécessaire", "disponible", "différent", "nouveau", "nouvelle", "premier", "dernière"
        ],
        "en": [
            "the", "and", "that", "have", "for", "not", "with", "you", "this", "but",
            "hello", "thanks", "thank", "please", "great", "perfect", "awesome", "good", "morning", "tonight",
            "because", "really", "actually", "probably", "definitely", "tomorrow", "yesterday", "today", "always",
            "never", "something", "everything", "nothing", "anything", "someone", "everyone", "important", "available",
            "different", "question", "answer", "message", "keyboard", "application", "working", "looking", "thinking"
        ],
        "es": [
            "hola", "gracias", "buenos", "días", "buenas", "noches", "por", "favor", "mucho", "todo",
            "bien", "perfecto", "genial", "ahora", "mañana", "hoy", "siempre", "nunca", "también", "después",
            "porque", "cuando", "donde", "como", "problema", "teléfono", "mensaje", "pregunta", "respuesta"
        ],
        "de": [
            "hallo", "danke", "bitte", "guten", "morgen", "abend", "heute", "morgen", "immer", "vielleicht",
            "natürlich", "wirklich", "wichtig", "perfekt", "super", "warum", "wann", "wie", "problem", "nachricht"
        ],
        "it": [
            "ciao", "grazie", "buongiorno", "buonasera", "perfetto", "bene", "molto", "sempre", "oggi", "domani",
            "perché", "quando", "dove", "come", "problema", "telefono", "messaggio", "domanda", "risposta"
        ],
        "pt": [
            "olá", "obrigado", "obrigada", "bom", "dia", "boa", "noite", "tudo", "bem", "perfeito",
            "sempre", "hoje", "amanhã", "também", "então", "porque", "quando", "problema", "mensagem"
        ]
    ]

    // MARK: - Prédictions du Mot Suivant (Bigrammes Contextuels)

    private let nextWordBigramsByLang: [String: [String: [String]]] = [
        "fr": [
            "": ["Je", "Bonjour", "Merci"],
            "je": ["suis", "vais", "pense"],
            "j'ai": ["fait", "vu", "besoin"],
            "tu": ["es", "as", "peux"],
            "il": ["est", "faut", "y"],
            "on": ["va", "peut", "est"],
            "c'est": ["super", "parfait", "bon"],
            "bonjour": ["à", "comment", "je"],
            "salut": ["comment", "ça", "tu"],
            "comment": ["ça", "vas-tu", "allez-vous"],
            "ça": ["va", "marche", "avance"],
            "merci": ["beaucoup", "pour", "d'avance"],
            "à": ["bientôt", "tout", "demain"],
            "tout": ["à", "le", "est"],
            "pas": ["de", "du", "encore"],
            "de": ["problème", "souci", "rien"],
            "très": ["bien", "bon", "vite"],
            "oui": ["c'est", "je", "merci"],
            "non": ["je", "merci", "pas"]
        ],
        "en": [
            "": ["I", "Hello", "Thanks"],
            "i": ["am", "think", "have"],
            "i'm": ["going", "on", "ready"],
            "you": ["are", "can", "have"],
            "it": ["is", "was", "works"],
            "thank": ["you", "goodness", "so"],
            "thanks": ["for", "a", "so"],
            "how": ["are", "is", "about"],
            "let's": ["go", "do", "see"],
            "see": ["you", "if", "what"]
        ],
        "es": [
            "": ["Hola", "Gracias", "Yo"],
            "hola": ["cómo", "qué", "buenos"],
            "muchas": ["gracias", "veces", "cosas"],
            "por": ["favor", "eso", "supuesto"]
        ],
        "de": [
            "": ["Ich", "Hallo", "Danke"],
            "ich": ["bin", "habe", "kann"],
            "vielen": ["Dank", "Grüßen", "Leuten"]
        ],
        "it": [
            "": ["Ciao", "Grazie", "Io"],
            "ciao": ["come", "tutto", "a"],
            "grazie": ["mille", "di", "per"]
        ],
        "pt": [
            "": ["Olá", "Obrigado", "Eu"],
            "tudo": ["bem", "bom", "certo"]
        ]
    ]

    // MARK: - Coordonnées Géométriques AZERTY / QWERTY pour Pondération des Fautes de Frappe

    private let keyCoordinatesAZERTY: [Character: (Double, Double)] = [
        "a": (0, 0), "z": (1, 0), "e": (2, 0), "r": (3, 0), "t": (4, 0),
        "y": (5, 0), "u": (6, 0), "i": (7, 0), "o": (8, 0), "p": (9, 0),
        "q": (0, 1), "s": (1, 1), "d": (2, 1), "f": (3, 1), "g": (4, 1),
        "h": (5, 1), "j": (6, 1), "k": (7, 1), "l": (8, 1), "m": (9, 1),
        "w": (1.5, 2), "x": (2.5, 2), "c": (3.5, 2), "v": (4.5, 2), "b": (5.5, 2), "n": (6.5, 2)
    ]

    private init() {}

    // MARK: - Calcul Principal des 3 Suggestions & Décision d'Autocorrection

    public func computeSuggestions(
        contextBeforeCursor: String,
        languageCode: String
    ) -> AutocorrectSuggestionSet {
        let lang = normalizeLang(languageCode)
        let (previousWord, currentPrefix) = extractLastTwoTokens(from: contextBeforeCursor)

        // Cas 1 : L'utilisateur vient de taper un espace ou le champ est vide -> Prédiction du mot suivant
        if currentPrefix.isEmpty {
            let nextCandidates = predictNextWords(after: previousWord, languageCode: lang)
            return AutocorrectSuggestionSet(
                typedWord: "",
                primaryCorrection: nextCandidates[0],
                secondaryPrediction: nextCandidates[1],
                tertiaryPrediction: nextCandidates[2],
                shouldAutoReplaceOnSpace: false
            )
        }

        let lowerTyped = currentPrefix.lowercased()
        let isCapitalized = currentPrefix.first?.isUppercase == true
        let isAllCaps = currentPrefix.count > 1 && currentPrefix == currentPrefix.uppercased()

        // 1. Respect absolu de `user_style_memory.json` :
        // Si l'utilisateur a enregistré cette abréviation (ou tape la forme parlée), on la priorise !
        let userMemoryPairs = UserStyleMemoryManager.shared.replacementsForLanguage(lang)
        for pair in userMemoryPairs {
            if pair.written.lowercased() == lowerTyped {
                // C'est une abréviation voulue par l'utilisateur (ex: "tkt", "bcp") -> On ne la corrige JAMAIS de force !
                let expanded = applyCasing(pair.spoken, isCapitalized: isCapitalized, isAllCaps: isAllCaps)
                return AutocorrectSuggestionSet(
                    typedWord: currentPrefix,
                    primaryCorrection: currentPrefix,
                    secondaryPrediction: expanded,
                    tertiaryPrediction: predictNextWords(after: currentPrefix, languageCode: lang).first ?? "👍",
                    shouldAutoReplaceOnSpace: false
                )
            }
            if pair.spoken.lowercased() == lowerTyped {
                let targetWritten = applyCasing(pair.written, isCapitalized: isCapitalized, isAllCaps: isAllCaps)
                return AutocorrectSuggestionSet(
                    typedWord: currentPrefix,
                    primaryCorrection: targetWritten,
                    secondaryPrediction: currentPrefix,
                    tertiaryPrediction: predictNextWords(after: targetWritten, languageCode: lang).first ?? "👍",
                    shouldAutoReplaceOnSpace: true
                )
            }
        }

        // 2. Dictionnaire instantané d'apostrophes & d'accents (ex: "jarrive" -> "j'arrive", "cest" -> "c'est")
        if let instantMap = instantCorrectionsByLang[lang],
           let directFix = instantMap[lowerTyped] {
            let casedFix = applyCasing(directFix, isCapitalized: isCapitalized, isAllCaps: isAllCaps)
            let shouldReplace = (casedFix.lowercased() != lowerTyped)
            let altPredictions = findPrefixCompletions(prefix: lowerTyped, languageCode: lang, excluding: [casedFix, currentPrefix])
            return AutocorrectSuggestionSet(
                typedWord: currentPrefix,
                primaryCorrection: casedFix,
                secondaryPrediction: altPredictions.first ?? currentPrefix,
                tertiaryPrediction: altPredictions.dropFirst().first ?? "👍",
                shouldAutoReplaceOnSpace: shouldReplace
            )
        }

        // 3. Complétion de préfixe + Correcteur géométrique & UITextChecker
        let localeTag = localeIdentifier(for: lang)
        let nsWord = currentPrefix as NSString
        let range = NSRange(location: 0, length: nsWord.length)

        let misspelledRange = textChecker.rangeOfMisspelledWord(
            in: currentPrefix,
            range: range,
            startingAt: 0,
            wrap: false,
            language: localeTag
        )
        let isValidSystemWord = (misspelledRange.location == NSNotFound)

        var completions = findPrefixCompletions(prefix: lowerTyped, languageCode: lang, excluding: [])
        if let sysCompletions = textChecker.completions(forPartialWordRange: range, in: currentPrefix, language: localeTag) {
            for item in sysCompletions where !completions.contains(where: { $0.caseInsensitiveCompare(item) == .orderedSame }) {
                completions.append(item)
                if completions.count >= 6 { break }
            }
        }

        if isValidSystemWord {
            let bestCompletion = completions.first(where: { $0.caseInsensitiveCompare(currentPrefix) != .orderedSame })
                .map { applyCasing($0, isCapitalized: isCapitalized, isAllCaps: isAllCaps) }
                ?? currentPrefix
            let secondCompletion = completions.dropFirst().first(where: {
                $0.caseInsensitiveCompare(currentPrefix) != .orderedSame && $0.caseInsensitiveCompare(bestCompletion) != .orderedSame
            }).map { applyCasing($0, isCapitalized: isCapitalized, isAllCaps: isAllCaps) }
                ?? predictNextWords(after: currentPrefix, languageCode: lang).first ?? "👍"

            return AutocorrectSuggestionSet(
                typedWord: currentPrefix,
                primaryCorrection: currentPrefix,
                secondaryPrediction: bestCompletion,
                tertiaryPrediction: secondCompletion,
                shouldAutoReplaceOnSpace: false
            )
        }

        // Le mot contient une faute de frappe ou est incomplet -> Recherche de la meilleure correction géométrique
        var guesses: [String] = []
        if let sysGuesses = textChecker.guesses(forWordRange: range, in: currentPrefix, language: localeTag) {
            guesses.append(contentsOf: sysGuesses.prefix(8))
        }
        guesses.append(contentsOf: findGeometricClosestWords(to: lowerTyped, languageCode: lang))

        // Tri par score géométrique AZERTY/QWERTY + distance d'édition
        let rankedGuesses = guesses
            .uniquedCaseInsensitive()
            .sorted { candidateA, candidateB in
                geometricDistanceScore(typed: lowerTyped, candidate: candidateA.lowercased()) <
                geometricDistanceScore(typed: lowerTyped, candidate: candidateB.lowercased())
            }

        if let bestGuess = rankedGuesses.first {
            let casedBest = applyCasing(bestGuess, isCapitalized: isCapitalized, isAllCaps: isAllCaps)
            let dist = geometricDistanceScore(typed: lowerTyped, candidate: bestGuess.lowercased())
            // On remplace automatiquement à l'espace uniquement si la faute est très proche (1-2 lettres ou accent)
            let autoReplace = dist <= 2.35 && currentPrefix.count >= 2
            let secondChoice = rankedGuesses.dropFirst().first
                .map { applyCasing($0, isCapitalized: isCapitalized, isAllCaps: isAllCaps) }
                ?? completions.first
                ?? currentPrefix

            return AutocorrectSuggestionSet(
                typedWord: currentPrefix,
                primaryCorrection: casedBest,
                secondaryPrediction: secondChoice,
                tertiaryPrediction: completions.dropFirst().first ?? currentPrefix,
                shouldAutoReplaceOnSpace: autoReplace
            )
        }

        // Sinon, si c'est le début d'un mot connu, on propose les complétions sans forcer le remplacement
        let primaryComp = completions.first
            .map { applyCasing($0, isCapitalized: isCapitalized, isAllCaps: isAllCaps) }
            ?? currentPrefix
        let secondaryComp = completions.dropFirst().first
            .map { applyCasing($0, isCapitalized: isCapitalized, isAllCaps: isAllCaps) }
            ?? currentPrefix

        return AutocorrectSuggestionSet(
            typedWord: currentPrefix,
            primaryCorrection: primaryComp,
            secondaryPrediction: secondaryComp,
            tertiaryPrediction: currentPrefix,
            shouldAutoReplaceOnSpace: false
        )
    }

    // MARK: - Helpers de Recherche & Distance Géométrique

    private func predictNextWords(after previousWord: String, languageCode: String) -> [String] {
        let lowerPrev = previousWord.lowercased()
        let map = nextWordBigramsByLang[languageCode] ?? nextWordBigramsByLang["en"]!
        if let list = map[lowerPrev], list.count >= 3 {
            return list
        }
        return map[""] ?? ["Je", "Bonjour", "Merci"]
    }

    private func findPrefixCompletions(prefix: String, languageCode: String, excluding: [String]) -> [String] {
        let words = frequentLexiconByLang[languageCode] ?? frequentLexiconByLang["en"]!
        let normalizedPrefix = stripDiacritics(prefix)
        var results: [String] = []

        for w in words {
            let normW = stripDiacritics(w)
            if (w.hasPrefix(prefix) || normW.hasPrefix(normalizedPrefix)) &&
               w.caseInsensitiveCompare(prefix) != .orderedSame &&
               !excluding.contains(where: { $0.caseInsensitiveCompare(w) == .orderedSame }) {
                results.append(w)
                if results.count >= 4 { break }
            }
        }
        return results
    }

    private func findGeometricClosestWords(to typed: String, languageCode: String) -> [String] {
        let words = frequentLexiconByLang[languageCode] ?? frequentLexiconByLang["en"]!
        return words.filter { abs($0.count - typed.count) <= 2 }
            .filter { geometricDistanceScore(typed: typed, candidate: $0) <= 2.6 }
    }

    private func geometricDistanceScore(typed: String, candidate: String) -> Double {
        let normTyped = stripDiacritics(typed)
        let normCand = stripDiacritics(candidate)
        if normTyped == normCand {
            return 0.25 // Simple accent ou apostrophe manquante -> priorité maximale !
        }
        let editDist = Double(levenshtein(normTyped, normCand))
        if normTyped.count == normCand.count {
            var keyPenalty = 0.0
            for (c1, c2) in zip(normTyped, normCand) where c1 != c2 {
                if let p1 = keyCoordinatesAZERTY[c1], let p2 = keyCoordinatesAZERTY[c2] {
                    let dx = p1.0 - p2.0
                    let dy = p1.1 - p2.1
                    let dist = sqrt(dx * dx + dy * dy)
                    keyPenalty += (dist <= 1.5) ? 0.65 : 1.35
                } else {
                    keyPenalty += 1.2
                }
            }
            return keyPenalty
        }
        return editDist * 1.15
    }

    private func levenshtein(_ s1: String, _ s2: String) -> Int {
        let a = Array(s1)
        let b = Array(s2)
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }

        var prev = Array(0...b.count)
        var curr = Array(repeating: 0, count: b.count + 1)

        for i in 1...a.count {
            curr[0] = i
            for j in 1...b.count {
                let cost = (a[i - 1] == b[j - 1]) ? 0 : 1
                curr[j] = min(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            prev = curr
        }
        return prev[b.count]
    }

    private func stripDiacritics(_ text: String) -> String {
        return text.folding(options: .diacriticInsensitive, locale: .current)
            .replacingOccurrences(of: "'", with: "")
            .replacingOccurrences(of: "’", with: "")
            .lowercased()
    }

    private func applyCasing(_ word: String, isCapitalized: Bool, isAllCaps: Bool) -> String {
        if isAllCaps {
            return word.uppercased()
        }
        if isCapitalized, let first = word.first {
            return String(first).uppercased() + word.dropFirst()
        }
        return word
    }

    public func extractLastTwoTokens(from text: String) -> (previousWord: String, currentPrefix: String) {
        guard !text.isEmpty else { return ("", "") }
        if let lastChar = text.last, lastChar.isWhitespace || ".,!?;:\n".contains(lastChar) {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            let tokens = trimmed.components(separatedBy: .whitespacesAndNewlines)
            let prev = tokens.last?.trimmingCharacters(in: .punctuationCharacters) ?? ""
            return (prev, "")
        }
        let parts = text.components(separatedBy: .whitespacesAndNewlines)
        let current = parts.last ?? ""
        let prev = parts.dropLast().last?.trimmingCharacters(in: .punctuationCharacters) ?? ""
        return (prev, current)
    }

    private func normalizeLang(_ code: String) -> String {
        let lower = code.lowercased()
        if ["fr", "en", "es", "de", "it", "pt"].contains(lower) {
            return lower
        }
        return WhisperLanguageSelection.detectedSystemISOCode
    }

    private func localeIdentifier(for lang: String) -> String {
        switch lang {
        case "fr": return "fr_FR"
        case "en": return "en_US"
        case "es": return "es_ES"
        case "de": return "de_DE"
        case "it": return "it_IT"
        case "pt": return "pt_PT"
        default:   return "en_US"
        }
    }
}

private extension Array where Element == String {
    func uniquedCaseInsensitive() -> [String] {
        var seen = Set<String>()
        return filter { item in
            let lower = item.lowercased()
            if seen.contains(lower) { return false }
            seen.insert(lower)
            return true
        }
    }
}
