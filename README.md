# 🫧 WhisperOrb — Dictée Vocale IA 100 % Hors-Ligne (iPad Pro 12,9" & iPhone)

[![Build & Release WhisperOrb (.ipa)](https://github.com/bluewolfy24yt/WhisperOrb/actions/workflows/build-ipa.yml/badge.svg)](https://github.com/bluewolfy24yt/WhisperOrb/actions)

**WhisperOrb** est une application iPadOS/iOS et une extension clavier **AZERTY français complet** intégrant une **bulle vocale interactive style Gemini Live à 120 Hz (ProMotion)** propulsée localement par **Whisper (`whisper.cpp` / Metal GPU)**.

Conçue spécifiquement pour la rédaction de copies en **salle d'examen (Mode Avion 100 % hors-ligne)** directement dans **Goodnotes** sur **iPad Pro 12,9 pouces (4e génération — A12Z Bionic, 6 Go de RAM)** ainsi que sur **iPhone 13 (A15 Bionic, 4 Go de RAM)**.

---

## 📥 Téléchargement Direct du Fichier `.ipa`

Le fichier **`WhisperOrb-iPad.ipa`** est compilé automatiquement sur `macos-15` par GitHub Actions à chaque mise à jour et publié dans les Releases officielles :

👉 **[Télécharger la dernière version de `WhisperOrb-iPad.ipa` (GitHub Releases)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)**

---

## 🧠 Les 3 Modèles Whisper Intégrés (Sélection Intelligente selon la RAM)

WhisperOrb détecte automatiquement la quantité de mémoire vive (`ProcessInfo.processInfo.physicalMemory`) et la puce de votre appareil (`A12Z Bionic` vs `A15 Bionic`). Un seul modèle est chargé en RAM à la fois (`unloadCurrentModel()` libère systématiquement la mémoire Metal avant tout changement).

| Modèle | Poids Disque | Consommation RAM | Latence Moyenne | Cible Recommandée |
| :--- | :---: | :---: | :---: | :--- |
| **⚡ Whisper Flash** | **60 Mo** | **~190 Mo** | **~0,15 s** | Prise de notes rapide & brouillons |
| **⚖️ Whisper PRO** | **190 Mo** | **~380 Mo** | **~0,35 s** | **Recommandé sur iPhone 13 (4 Go RAM)** |
| **🎯 Whisper Expert** | **540 Mo** | **~850 Mo** | **~0,70 s** | **Recommandé sur iPad Pro 12,9" (6 Go RAM)** |

> **Protection Hors-Ligne Garantie :** Les modèles téléchargés sont marqués avec `NSURLIsExcludedFromBackupKey = true` afin qu'iPadOS ne les supprime jamais automatiquement lorsque vous êtes hors-ligne en salle d'examen.

---

## ✨ Fonctionnalités Clés

### 1. Bulle Vocale Gemini Live 120 Hz (`Shared/GeminiBubbleView.swift`)
* **Rendu procédural 120 FPS** via `TimelineView(.animation)` + `Canvas` + `RadialGradient` reproduisant l'onde lumineuse organique de Gemini Live avec languette latérale d'accroche.
* **4 Modes Optiques Automatiques :**
  * **Mode Standard** : Onde lumineuse contrastée (ex. Bleu Gemini `#0088FF` sur Noir Profond `#08080C`).
  * **Mode Spécial « Noir sur Noir » (Éclipse / Dark Stealth)** : Sphère noire mat `#050507` avec vague d'ombre liquide `#14141C` couronnée d'un filet spéculaire argenté/métallique.
  * **Mode Spécial « Blanc sur Blanc » (Perle / Cristal)** : Sphère blanc pur `#F8F9FA` avec halo nacré `#CBD2D9`, reflets irisés subtils et crête spéculaire `#FFFFFF`.
  * **Mode Faible Contraste** : Ajout automatique d'un liseré de contraste si les couleurs d'onde et de fond ont une distance euclidienne RGB $< 0.22$.
* **Studio de Couleurs NSI (`Shared/ThemeColors.swift`)** : 5 fonds pré-enregistrés, 16 thèmes d'onde, roue chromatique complète `ColorPicker` et moteur de recherche textuelle comprenant **plus de 140 couleurs en français et en anglais** (avec ou sans accents) ainsi que les codes hexadécimaux `#RRGGBB` / `#RGB`.

### 2. Clavier AZERTY Français Complet pour Goodnotes (`WhisperKeyboard/`)
* **Rangée 0 dédiée à la rédaction française** : `1 2 3 4 5 6 7 8 9 0 é è ê à ù ç œ ' -` directement accessibles sans sous-menu.
* **Clavier AZERTY intégral** avec gestion des majuscules (`⇧`), du verrouillage majuscule par double-clic (`⇪`), de la suppression rapide par maintien appuyé (`⌫`), du double-espace insérant `". "` et des calques `123` / `#+=`.
* **Barre supérieure intelligente** :
  * Pastille d'état en direct (`Prêt • Whisper Expert`, `Écoute en cours...`, `Transcription...`).
  * Bouton **`↶ Effacer phrase`** : supprime instantanément la dernière phrase dictée dans Goodnotes d'un seul tap.
  * Bouton **`↵ À la ligne`** : insère un saut de ligne propre dans la zone de texte Goodnotes.
  * **Appui long (0,6 s) sur la bulle Gemini** : ouvre un mini-sélecteur intégré au clavier pour changer de modèle (`Whisper Flash`, `Whisper PRO`, `Whisper Expert`) ou de couleur de bulle sans quitter Goodnotes.

### 3. Commandes Vocales Françaises Spéciales Copie DST
Pendant votre dictée, prononcez simplement :
* **« À la ligne »** ou **« Nouveau paragraphe »** $\rightarrow$ insère un retour à la ligne (`\n`).
* **« Double saut de ligne »** $\rightarrow$ saute un paragraphe (`\n\n`).
* **« Effacer la dernière phrase »** ou **« Annuler la dernière phrase »** $\rightarrow$ supprime automatiquement la dernière phrase insérée dans Goodnotes.
* **« Ouvrir les guillemets »** / **« Fermer les guillemets »** $\rightarrow$ insère `« ` et ` »`.
* **« Point d'interrogation »** / **« Point d'exclamation »** $\rightarrow$ insère `?` ou `!`.

### 4. Double Canal de Synchronisation (Compatible Sideloading Gratuit)
Pour fonctionner à 100 % même avec un compte Apple gratuit (sans abonnement développeur à 99 €/an), `AppGroupManager` combine :
1. **Canal 1** : `UserDefaults(suiteName: "group.com.bluewolfy24yt.whisperorb")` + fichier JSON atomique partagé.
2. **Canal 2 (Fallback Sideloading universel)** : Notifications système Darwin C-API (`CFNotificationCenterGetDarwinNotifyCenter()`) qui traversent toutes les sandboxes iOS + bus de données dédié `UIPasteboard(name: "com.bluewolfy24yt.whisperorb.bus")`.

---

## 🛠️ Guide d'Installation Pas-à-Pas (SideStore / AltStore / Sideloadly)

### Étape 1 : Installer le fichier `.ipa` sur l'iPad Pro ou l'iPhone
1. Téléchargez **`WhisperOrb-iPad.ipa`** depuis la page **[Releases](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)**.
2. Installez l'IPA avec votre outil habituel :
   * **SideStore / AltStore** : Ouvrez l'onglet *My Apps*, appuyez sur le bouton **`+`** en haut à gauche et sélectionnez `WhisperOrb-iPad.ipa` (conservez les App Extensions lors de la signature).
   * **Sideloadly** : Connectez l'iPad à votre ordinateur, glissez `WhisperOrb-iPad.ipa` dans Sideloadly, entrez votre Apple ID et cliquez sur *Start*.

### Étape 2 : Télécharger le modèle hors-ligne (Une seule fois en Wi-Fi)
1. Ouvrez l'application **WhisperOrb** sur votre iPad Pro.
2. Dans la **Carte 2 (Modèles Whisper Hors-Ligne)**, téléchargez le modèle **Whisper Expert** (recommandé pour les 6 Go de RAM de l'iPad Pro A12Z) ou **Whisper PRO** (sur iPhone 13).
3. Vérifiez que le badge **« Prêt Hors-Ligne ✅ »** s'affiche.

### Étape 3 : Activer le Clavier WhisperOrb dans iPadOS
1. Ouvrez l'application **Réglages** de l'iPad.
2. Allez dans **Général > Clavier > Claviers > Ajouter un clavier...**
3. Sélectionnez **WhisperOrb** dans la section *Claviers tiers*.
4. Touchez **WhisperOrb Clavier — WhisperOrb** dans la liste, puis activez **« Autoriser l'accès complet »** (indispensable pour permettre au clavier de communiquer avec le moteur Whisper en arrière-plan).

### Étape 4 : Utilisation en Salle d'Examen (Goodnotes + Mode Avion)
1. Activez le **Mode Avion** sur votre iPad Pro.
2. Ouvrez **WhisperOrb** : le bandeau supérieur devient **vert émeraude** (*« 🔒 MODE EXAMEN HORS-LIGNE ACTIF — Aucune connexion Internet détectée (100 % Local) »*).
3. Dans la **Carte 1**, appuyez sur le bouton **« Activer le Moteur Audio en Tâche de Fond (Pour Goodnotes) »** et accordez l'accès au microphone.
4. Ouvrez **Goodnotes**, créez ou touchez une zone de texte, sélectionnez le clavier **WhisperOrb** via la touche Globe 🌐, puis touchez la **bulle Gemini Live** en haut à droite du clavier pour dicter votre copie !
