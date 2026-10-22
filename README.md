# 🫧 WhisperOrb — Dictée Vocale IA 100 % Hors-Ligne (iPhone, iPad & Mac • GPU Metal)

[![Build & Release WhisperOrb (.ipa)](https://github.com/bluewolfy24yt/WhisperOrb/actions/workflows/build-ipa.yml/badge.svg)](https://github.com/bluewolfy24yt/WhisperOrb/actions)

**WhisperOrb** est une application universelle **iOS, iPadOS & macOS (Apple Silicon)** accompagnée d'un **clavier AZERTY français fidèle à 100 % au clavier natif Apple iOS** (avec loupe d'aperçu au toucher, appui long et glissement pour les accents, et **Mode Clavier Réduit**) intégrant une **sphère vocale 3D interactive à 120 Hz** propulsée localement par **`whisper.cpp` (GPU Metal + FlashAttention)**.

Conçue pour fonctionner **100 % en local sur l'appareil (zéro serveur, confidentialité totale)** dans toutes vos applications (**Messages, Notes, Mail, Safari, Pages, Goodnotes, Word, Discord**) sur **tous les appareils Apple actuels et futurs** (de l'iPhone XS/XR jusqu'à l'**iPhone 18 Pro Max** et générations futures, tous les **iPad**, et tous les **Mac Apple Silicon**).

---

## 📥 Téléchargement Direct du Fichier `.ipa`

Le fichier **`WhisperOrb-iPad.ipa`** (universel iPhone + iPad + Mac Apple Silicon) est compilé automatiquement sur `macos-15` par GitHub Actions à chaque mise à jour et publié dans les Releases officielles :

👉 **[Télécharger la dernière version de `WhisperOrb-iPad.ipa` (GitHub Releases)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)**

---

## 📱 Compatibilité Universelle & Architecture 100 % Future-Proof (iPhone, iPad & Mac)

Grâce à l'inspection directe du silicium via l'API **Apple Metal (`MTLCreateSystemDefaultDevice()`)** et au décodeur dynamique d'identifiants matériels, WhisperOrb s'adapte automatiquement à tous les appareils Apple existants et futurs :

| Famille Apple | Appareils Pris en Charge | Accélération Matérielle | Modèle Recommandé par Défaut |
| :--- | :--- | :---: | :---: |
| **iPhone (A15 à A20 Pro & futurs)** | iPhone 13 à **iPhone 18 Pro Max** (et futurs iPhone 19, 20+) | **GPU Metal + FlashAttention + Cœurs Performance** | **⚖️ Whisper PRO** (4 Go) / **🎯 Whisper Expert** (6 Go–12 Go+) |
| **iPhone (A12 à A14 Bionic)** | iPhone XS, XR, 11 (Pro/Max), SE 2, 12 (mini/Pro/Max) | **GPU Metal + Accelerate** | **⚖️ Whisper PRO** / **⚡ Whisper Flash** |
| **iPad Pro, Air, mini & Standard** | Tous les iPad (A12 à A17 Pro, A12X/A12Z, M1, M2, M3, M4, M5 & futurs) | **GPU Metal + FlashAttention (ProMotion 120 Hz)** | **🎯 Whisper Expert** (6 Go+) / **⚖️ Whisper PRO** |
| **Mac (Apple Silicon M1 à M5+)** | MacBook Air, MacBook Pro, iMac, Mac mini, Mac Studio, Mac Pro | **GPU Metal + FlashAttention + Auto-Copie Presse-Papier** | **🎯 Whisper Expert** |

---

## 🧠 Les 3 Modèles Whisper Intégrés (GPU Metal + FlashAttention)

Un seul modèle est chargé en RAM à la fois (`unloadCurrentModel()` appelle `whisper_free()` pour libérer systématiquement la mémoire GPU Metal avant tout changement). L'encodeur et le décodeur tournent directement sur le **GPU Metal (`use_gpu = true`, `flash_attn = true`)** avec une fenêtre `audio_ctx` dynamique ajustée à la durée réelle de votre voix (plancher 64 frames) :

| Modèle | Poids Disque | Consommation RAM | Latence Moyenne (GPU Metal) | Usage Idéal |
| :--- | :---: | :---: | :---: | :--- |
| **⚡ Whisper Flash** | **60 Mo** | **~190 Mo** | **~0,18 s** | Messages instantanés & notes rapides |
| **⚖️ Whisper PRO** | **190 Mo** | **~380 Mo** | **~0,35 s** | **Dictée quotidienne fluide & précise (iPhone / iPad)** |
| **🎯 Whisper Expert** | **540 Mo** | **~850 Mo** | **~0,65 s** | **Précision maximale & rédaction longue (iPad Pro / iPhone Pro / Mac)** |

---

## ✨ Fonctionnalités Clés

### 1. Clavier Natif Apple iOS AZERTY + Mode Clavier Réduit (`WhisperKeyboard/`)
* **Reproduction exacte du clavier Apple iOS** :
  * **Loupe d'aperçu au toucher** : chaque touche affiche instantanément la lettre agrandie au-dessus de votre doigt lors de la frappe.
  * **Appui long + glissement pour les accents et variantes** : maintenez une touche appuyée (`e`, `a`, `u`, `i`, `o`, `c`, `n`, `'`, `.`, `?`, `!`, `€`, `0`...) et glissez votre doigt vers la gauche ou la droite pour sélectionner l'accent souhaité (`é`, `è`, `ê`, `ë`, `à`, `â`, `æ`, `ç`, `œ`, `ù`, `î`, `«`, `»`, `…`).
  * **4 rangées natives aérées** avec touche apostrophe directe `'`, double-espace pour insérer `". "`, verrouillage majuscule par double-tap (`⇪`) et suppression continue au maintien (`⌫`).
* **Mode Clavier Réduit (Barre Vocale Compacte)** :
  * Touchez le bouton **`▾`** en haut à gauche du clavier pour masquer les touches et ne conserver qu'une fine barre vocale en bas de l'écran.

### 2. Sphère Vocale 3D Pure & Zéro Chauffe (`Shared/GeminiBubbleView.swift`)
* **Rendu 3D fluide et économe en énergie** : mise en pause automatique à 0 % GPU lorsque l'application est en arrière-plan, fréquence adaptative au repos et libération complète du GPU Metal pendant la transcription.
* **Personnalisation complète (`Shared/ThemeColors.swift`)** : modes spéciaux **Éclipse (Noir sur Noir)** et **Perle (Blanc sur Blanc)**, roue chromatique et recherche parmi plus de 140 couleurs FR/EN.

### 3. Commandes Vocales Françaises Intégrées
Pendant votre dictée, prononcez simplement :
* **« À la ligne »** ou **« Nouveau paragraphe »** $\rightarrow$ insère un saut de ligne (`\n`).
* **« Effacer la dernière phrase »** ou **« Annuler la dernière phrase »** $\rightarrow$ supprime la dernière phrase dictée.
* **« Ouvrir les guillemets »** / **« Fermer les guillemets »** $\rightarrow$ insère `« ` et ` »`.
* **« Point d'interrogation »** / **« Point d'exclamation »** $\rightarrow$ insère `?` ou `!`.

---

## 🛠️ Guide d'Installation Rapide

1. Téléchargez **`WhisperOrb-iPad.ipa`** depuis la page **[Releases](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)** et installez-le via *SideStore*, *AltStore*, *Sideloadly* ou en USB.
2. Ouvrez **WhisperOrb** et téléchargez le modèle recommandé (**Whisper PRO** ou **Whisper Expert**).
3. Activez le clavier dans **Réglages > Général > Clavier > Claviers > Ajouter un clavier > WhisperOrb** (avec **« Autoriser l'accès complet »**).
4. Activez le **Moteur vocal en arrière-plan** dans WhisperOrb, puis dictez dans n'importe quelle application !
