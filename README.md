# 🫧 WhisperOrb — Dictée Vocale IA 100 % Hors-Ligne (Tous les iPhone & iPad • GPU Metal)

[![Build & Release WhisperOrb (.ipa)](https://github.com/bluewolfy24yt/WhisperOrb/actions/workflows/build-ipa.yml/badge.svg)](https://github.com/bluewolfy24yt/WhisperOrb/actions)

**WhisperOrb** est une application universelle **iOS & iPadOS** accompagnée d'une extension clavier **AZERTY français (style iOS 18 Dark Mode + Mode Clavier Réduit 62 pt)** intégrant une **sphère vocale 3D interactive style Gemini Live à 120 Hz (ProMotion)** propulsée localement par **`whisper.cpp` avec accélération GPU Metal + FlashAttention**.

Conçue pour fonctionner **100 % hors-ligne (Mode Avion)** dans toutes vos applications (**Goodnotes, Notes, Pages, Messages, Mail, Word, Safari**) sur **tous les appareils Apple compatibles iOS/iPadOS 16+** (de l'iPhone XS / XR jusqu'à l'iPhone 16 Pro Max, et de l'iPad mini / Air / standard jusqu'aux iPad Pro A12X, A12Z, M1, M2 et M4).

---

## 📥 Téléchargement Direct du Fichier `.ipa`

Le fichier **`WhisperOrb-iPad.ipa`** (universel iPhone + iPad) est compilé automatiquement sur `macos-15` par GitHub Actions à chaque mise à jour et publié dans les Releases officielles :

👉 **[Télécharger la dernière version de `WhisperOrb-iPad.ipa` (GitHub Releases)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)**

---

## 📱 Compatibilité Universelle Apple (Tous les iPhone & iPad)

WhisperOrb détecte automatiquement le modèle exact de votre appareil Apple, sa quantité de RAM (`ProcessInfo.processInfo.physicalMemory`), son taux de rafraîchissement (`60 Hz` ou `ProMotion 120 Hz`) et sa puce Apple Silicon :

| Gamme Apple | Appareils Pris en Charge | Accélération Matérielle | Modèle Recommandé par Défaut |
| :--- | :--- | :---: | :---: |
| **iPhone (A15 à A18 Pro)** | iPhone 13 (mini/Pro/Max), 14, 15, 16 (Plus/Pro/Max), SE 3 | **GPU Metal + FlashAttention + Accelerate** | **⚖️ Whisper PRO** (ou **🎯 Whisper Expert** sur 15 Pro / 16) |
| **iPhone (A12 à A14 Bionic)** | iPhone XS, XR, 11 (Pro/Max), SE 2, 12 (mini/Pro/Max) | **GPU Metal + Accelerate** | **⚖️ Whisper PRO** / **⚡ Whisper Flash** |
| **iPad Pro (Série A & Série M)** | iPad Pro 11" & 12,9"/13" (A12X, A12Z 2020, M1, M2, M4) | **GPU Metal + FlashAttention (ProMotion 120 Hz)** | **🎯 Whisper Expert** (6 Go à 16 Go RAM) |
| **iPad Air, mini & Standard** | iPad Air (3e gén à M3), iPad mini (5 à 7), iPad (8e à 11e gén) | **GPU Metal + FlashAttention** | **⚖️ Whisper PRO** (3–4 Go) / **🎯 Whisper Expert** (8 Go) |

---

## 🧠 Les 3 Modèles Whisper Intégrés (GPU Metal + FlashAttention)

Un seul modèle est chargé en RAM à la fois (`unloadCurrentModel()` appelle `whisper_free()` pour libérer systématiquement la mémoire GPU Metal avant tout changement). L'encodeur et le décodeur tournent directement sur le **GPU Metal (`use_gpu = true`, `flash_attn = true`)** avec une fenêtre `audio_ctx` dynamique ajustée à la durée réelle de votre voix :

| Modèle | Poids Disque | Consommation RAM | Latence Moyenne (GPU Metal) | Cible Recommandée |
| :--- | :---: | :---: | :---: | :--- |
| **⚡ Whisper Flash** | **60 Mo** | **~190 Mo** | **~0,15 s** | Prise de notes ultra-rapide & appareils 3 Go RAM |
| **⚖️ Whisper PRO** | **190 Mo** | **~380 Mo** | **~0,35 s** | **Recommandé sur iPhone & iPad (4 Go RAM)** |
| **🎯 Whisper Expert** | **540 Mo** | **~850 Mo** | **~0,65 s** | **Recommandé sur iPad Pro & iPhone Pro (6 Go+ RAM)** |

> **Protection Hors-Ligne Garantie :** Les modèles téléchargés sont marqués avec `NSURLIsExcludedFromBackupKey = true` et conservés dans `Documents/Models` afin qu'iOS/iPadOS ne les supprime jamais automatiquement en Mode Avion ni lors des mises à jour.

---

## ✨ Fonctionnalités Clés

### 1. Sphère Vocale 3D Pure 120 Hz (`Shared/GeminiBubbleView.swift`)
* **Rendu procédural 120 FPS** via `TimelineView(.animation)` + `Canvas` + `RadialGradient` reproduisant l'onde lumineuse organique 3D de Gemini Live (sans aucun rectangle gris en arrière-plan).
* **4 Modes Optiques Automatiques :**
  * **Mode Standard** : Onde lumineuse contrastée (ex. Bleu Gemini `#0088FF` sur Noir Profond `#08080C`).
  * **Mode Spécial « Noir sur Noir » (Éclipse / Dark Stealth)** : Sphère noire mat `#050507` avec vague d'ombre liquide `#14141C` couronnée d'un filet spéculaire argenté/métallique.
  * **Mode Spécial « Blanc sur Blanc » (Perle / Cristal)** : Sphère blanc pur `#F8F9FA` avec halo nacré `#CBD2D9`, reflets irisés subtils et crête spéculaire `#FFFFFF`.
  * **Mode Faible Contraste** : Ajout automatique d'un liseré de contraste si les couleurs d'onde et de fond sont proches.
* **Studio de Couleurs (`Shared/ThemeColors.swift`)** : 5 fonds pré-enregistrés, 16 thèmes d'onde, roue chromatique complète `ColorPicker` et moteur de recherche textuelle comprenant **plus de 140 couleurs en français et en anglais**.

### 2. Clavier iOS 18 Dark Mode + Mode Clavier Réduit (`WhisperKeyboard/`)
* **Mode Clavier Réduit (Barre Vocale Flottante de `62 pt`)** :
  * Appuyez sur le bouton **`▾ Réduire`** en haut à gauche du clavier pour replier instantanément toutes les touches AZERTY et ne garder qu'une fine barre vocale flottante de **62 pt** en bas de l'écran (libérant **80 % de votre page Goodnotes ou Notes** !).
  * En mode réduit, vous conservez l'accès direct à l'**Orbe 3D**, au statut d'écoute, ainsi qu'aux boutons rapides **`⌨️ Clavier`** (pour ré-ouvrir les touches), **`🌐`**, **`↶`** (effacer la dernière phrase), **`↵`** (à la ligne) et **`⌫`** (supprimer).
* **Clavier AZERTY Français Complet (Style iOS 18 Dark Mode)** :
  * **Barre d'accents français spacieuse** : `é è ê à ù ç œ ' -` directement accessibles sans sous-menu.
  * **Touches grand confort `#3A3A3C`** avec gestion des majuscules (`⇧`), du verrouillage majuscule par double-clic (`⇪`), de la suppression rapide par maintien appuyé (`⌫`), du double-espace insérant `". "` et des calques `123` / `#+=`.
  * **Bouton `⚙️`** : ouvre un sélecteur rapide pour changer de modèle (`Whisper Flash`, `Whisper PRO`, `Whisper Expert`) ou de couleur d'orbe sans quitter votre application de prise de notes.

### 3. Commandes Vocales Françaises Intégrées
Pendant votre dictée, prononcez simplement :
* **« À la ligne »** ou **« Nouveau paragraphe »** $\rightarrow$ insère un retour à la ligne (`\n`).
* **« Double saut de ligne »** $\rightarrow$ saute un paragraphe (`\n\n`).
* **« Effacer la dernière phrase »** ou **« Annuler la dernière phrase »** $\rightarrow$ supprime automatiquement la dernière phrase insérée.
* **« Ouvrir les guillemets »** / **« Fermer les guillemets »** $\rightarrow$ insère `« ` et ` »`.
* **« Point d'interrogation »** / **« Point d'exclamation »** $\rightarrow$ insère `?` ou `!`.

---

## 🛠️ Guide d'Installation Pas-à-Pas (SideStore / AltStore / Sideloadly / USB)

### Étape 1 : Installer le fichier `.ipa` sur votre iPhone ou iPad
1. Téléchargez **`WhisperOrb-iPad.ipa`** depuis la page **[Releases](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)**.
2. Installez l'IPA avec votre outil habituel (*SideStore*, *AltStore*, *Sideloadly* ou *isideload* en USB).

### Étape 2 : Télécharger le modèle hors-ligne (Une seule fois en Wi-Fi)
1. Ouvrez l'application **WhisperOrb**.
2. Dans la **Carte 2 (Modèles Whisper Hors-Ligne)**, téléchargez **Whisper PRO** (recommandé sur iPhone 4 Go RAM) ou **Whisper Expert** (recommandé sur iPad Pro / iPhone Pro 6 Go+ RAM).
3. Vérifiez que le badge **« Prêt Hors-Ligne ✅ »** s'affiche.

### Étape 3 : Activer le Clavier WhisperOrb dans iOS / iPadOS
1. Ouvrez **Réglages > Général > Clavier > Claviers > Ajouter un clavier...**
2. Sélectionnez **WhisperOrb** dans *Claviers tiers*.
3. Touchez **WhisperOrb Clavier — WhisperOrb**, puis activez **« Autoriser l'accès complet »**.

### Étape 4 : Utilisation Hors-Ligne (Goodnotes, Notes, Mode Avion)
1. Dans la **Carte 1** de WhisperOrb, appuyez sur **« Activer le Moteur Audio en Tâche de Fond »**.
2. Ouvrez **Goodnotes** ou **Notes**, sélectionnez le clavier **WhisperOrb** via la touche Globe 🌐, réduisez le clavier avec **`▾`** si vous souhaitez voir toute votre page, et touchez l'**Orbe 3D** pour dicter instantanément !
