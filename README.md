<div align="center">

<img src="docs/assets/icon-blackhole.png" alt="WhisperOrb Black Hole Icon" width="136" height="136" style="border-radius: 28px;" />

# WhisperOrb `v2.0.0`

**100% On-Device Offline AI Voice Dictation & Native Keyboard Ecosystem for Apple (iOS / iPadOS / macOS) & Android (Phones, Tablets, Chromebook Plus & Googlebook 14)**

[![Release v2.0.0](https://img.shields.io/badge/Release-v2.0.0-0A84FF?style=for-the-badge&logo=github)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)
[![Apple Metal GPU](https://img.shields.io/badge/Apple-iOS%20%7C%20iPadOS%20%7C%20macOS%20(Metal%20GPU)-white?style=for-the-badge&logo=apple)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)
[![Android Multi-ABI](https://img.shields.io/badge/Android-ARM64%20%2B%20x86__64%20AVX2%20(.apk)-3DDC84?style=for-the-badge&logo=android)](https://github.com/bluewolfy24yt/WhisperOrb/releases/latest)
[![Googlebook 14 & Tablets](https://img.shields.io/badge/Googlebook%2014%20%7C%20Chromebook%20%7C%20Tablets-Ready-38BDF8?style=for-the-badge)](#)
[![Co-Author](https://img.shields.io/badge/AI%20Co--Author-Gemini--4--Argon-8A2BE2?style=for-the-badge)](#)

<h3>
  <a href="#-english-documentation">🇬🇧 English Documentation</a>
  &nbsp;&nbsp;•&nbsp;&nbsp;
  <a href="#-documentation-française">🇫🇷 Documentation Française</a>
</h3>

</div>

---

## 📸 Visual Showcase — Apple & Android Keyboards with the 3D Orb

| 🍎 **Apple (iOS / iPadOS / macOS)** — Native Keyboard + Full Emojis & AI Autocorrect | 🤖 **Android (Phones, Tablets & Googlebook 14)** — Native Keyboard with Embedded 3D Orb & `▾ Réduire` |
| :---: | :---: |
| <img src="docs/assets/preview-apple-keyboard.png" alt="Apple Keyboard with WhisperOrb" width="100%" /> | <img src="docs/assets/preview-android-keyboard.png" alt="Android Keyboard with WhisperOrb" width="100%" /> |

---

<a name="-english-documentation"></a>
# 🇬🇧 English Documentation

## Overview

**WhisperOrb v2.0.0** is a complete, 100% offline, privacy-first voice dictation and native keyboard ecosystem powered by `whisper.cpp` C++/Metal/ARM64/AVX2 acceleration. Zero audio ever leaves your device: every transcription and autocorrect prediction runs locally on your phone, tablet, or laptop silicon with sub-second latency.

### Key Highlights in `v2.0.0`

1. **🤖 True Native Android Keyboard (`WhisperOrbInputMethodService`) + Cosmic Dark Pro UI**
   - **Literally Embedded 120 Hz 3D Orb**: The 3D Cosmic Black Hole Orb is built directly inside the top voice control bar of the native Android keyboard—no floating overlay clutter.
   - **1-Tap Collapsible Voice Bar (`▾ Réduire`)**: Collapses the entire keyboard grid into a compact **58dp Voice Dictation Bar** at the bottom of the screen, automatically freeing **92% of the app viewport** just like on Apple.
   - **Automatic Pure Numeric Keypad Protection**: Inspects `EditorInfo.inputType` (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`) and automatically switches to a clean, Orb-free numeric keypad on number/PIN/phone fields.

2. **✨ Smart AI Autocorrect & 3-Suggestion Bar (`< 0.2 ms` on Apple & Android)**
   - Integrated 3-slot predictive bar right above the keys on both iOS/iPadOS and Android.
   - Combines **geometric AZERTY/QWERTY key-proximity typo correction**, **instant apostrophe & accent restoration** (`"jarrive" -> "j'arrive"`, `"cest" -> "c'est"`, `"tinquiete" -> "t'inquiète"`, `"dont" -> "don't"`, `"cant" -> "can't"`), and **contextual next-word bigram predictions**.
   - **100% synchronized with `user_style_memory.json`**: Never overwrites your personal slang/abbreviations (`"tkt"`, `"bcp"`, `"btw"`, `"tbh"`).

3. **💻 Googlebook 14, Chromebook Plus & Android Tablets Support (`arm64-v8a` + `x86_64` AVX2)**
   - Compiled natively with Android NDK C++ for both **ARMv8.2/ARMv9 FP16+DotProd (`arm64-v8a`)** (Google Tensor G4, Snapdragon X Elite `X1E-80-100` on HP Googlebook 14, Galaxy Tab S9/S10) and **Intel Core Ultra Series 3 / AMD AVX2+FMA+F16C (`x86_64`)** (ASUS/Acer Googlebook 14 & Chromebook Plus).
   - Automatic **2-Column Masonry Studio Layout** on Android Tablets, Foldables, and Googlebook 14, plus **Physical Keyboard Dock Mode** (auto-collapses into the 58dp voice bar and copies transcriptions to the clipboard for instant `Ctrl+V`).

4. **🍎 1:1 Native Apple iOS/iPadOS Keyboard + Full Emoji Catalog**
   - Built-in dark Apple keyboard (`#1C1C1E`) with instant touch-down key magnifier callouts, long-press + slide horizontal accent selector, **complete 9-category Apple Emoji Picker (`😀`) with persistent Recents**, **3-suggestion AI Autocorrect bar**, and **Collapsed Voice Bar (`▾`)**.
   - Accelerated by **Apple Metal GPU + FlashAttention** (`whisper.xcframework` `b5454`) and 100% future-proof hardware detection (`MTLCreateSystemDefaultDevice()`).

5. **🌐 Smart Multilingual Engine & `user_style_memory.json`**
   - Automatically detects your device's active system language (`Locale.current` / `Locale.getDefault().language`) and passes the exact ISO 639-1 code (`fr`, `en`, `es`, `de`, `it`, `pt`, `nl`, `pl`, `ja`, `ko`, `zh`, `ar`, `ru`) to `whisper.cpp`.
   - Local JSON writing style profile (`user_style_memory.json`) indexed by language code with automatic short-message trailing period removal (`≤ 12 words`).

6. **👑 Full 7-Model Whisper Catalog + Automatic Hardware RAM Gating**
   - From **Whisper Nano (31 MB)** up to the full 1.55B-parameter **Whisper TITAN (`Large-v3` 1.08 GB)**, with a **"🧪 Simulate 4 GB RAM Device"** switch for testing hardware lock badges on flagship devices.

---

## 📊 Complete 7-Model Whisper Catalog (`v2.0.0`)

| Tier | GGML File | Disk Size | RAM Usage | Min. Device RAM | Ideal Use Case |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **1. ⚡ Whisper Nano** | `ggml-tiny-q5_1.bin` | 31 MB | ~120 MB | **2 GB+** | Ultra-fast short replies on any device |
| **2. ⚡ Whisper Flash** | `ggml-base-q5_1.bin` | 60 MB | ~190 MB | **3 GB+** | Instant everyday messaging |
| **3. ⚖️ Whisper PRO** | `ggml-small-q5_1.bin` | 190 MB | ~380 MB | **4 GB+** | Balanced daily dictation (Default on 4 GB phones) |
| **4. 🔥 Whisper Medium** | `ggml-medium-q5_0.bin` | 539 MB | ~900 MB | **6 GB+** | High-accuracy multilingual dictation |
| **5. 🚀 Whisper Expert (v3 Turbo Q5)** | `ggml-large-v3-turbo-q5_0.bin` | 540 MB | ~950 MB | **6 GB+** | Near-Large-v3 accuracy at high speed |
| **6. 💎 Whisper Expert+ (v3 Turbo Q8)** | `ggml-large-v3-turbo-q8_0.bin` | 874 MB | ~1.32 GB | **8 GB+** | 8-bit studio fidelity |
| **7. 👑 Whisper TITAN (Large-v3 Full)** | `ggml-large-v3-q5_0.bin` | **1.08 GB** | ~2.15 GB | **12 GB+** | **Full 1.55B parameter master model** (Pixel 9 Pro XL 16 GB, Googlebook 14, iPad Pro M4/M5, Mac) |

---

<a name="-documentation-française"></a>
# 🇫🇷 Documentation Française

## Présentation Générale

**WhisperOrb v2.0.0** est un écosystème complet de dictée vocale IA et de clavier natif **100 % hors-ligne** pour **Apple (iOS / iPadOS / macOS)** et **Android (Smartphones, Tablettes, Chromebook Plus & Googlebook 14)**, propulsé nativement en C++ par `whisper.cpp` (GPU Metal + FlashAttention sur Apple, NDK ARM64 FP16+DotProd & x86_64 AVX2+FMA avec épinglage des cœurs Prime/Performance sur Android). Aucune donnée vocale ne quitte jamais votre appareil.

### Nouveautés Majeures de la `v2.0.0`

1. **🤖 Vrai Clavier Android Natif (`WhisperOrbInputMethodService`) & Interface Cosmic Dark Pro**
   - **Orbe 3D 120 Hz Littéralement Incrusté dans le Clavier** : L'Orbe 3D Trou Noir est intégré directement dans la barre supérieure du clavier Android natif (plus aucun overlay flottant).
   - **Bouton « `▾ Réduire` » (1:1 comme sur Apple)** : Replie instantanément tout le clavier en une **barre vocale compacte de 58dp** en bas de l'écran, libérant automatiquement **92 % de l'écran** de votre application.
   - **Protection Automatique des Claviers de Chiffres** : Détecte automatiquement les champs numériques, téléphones et codes PIN (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`) et bascule sur un **pavé numérique pur SANS Orbe**.

2. **✨ Correcteur Automatique IA & Barre de 3 Prédictions (`< 0,2 ms` sur Apple & Android)**
   - Barre prédictive à 3 choix intégrée au-dessus des touches sur iOS/iPadOS et Android.
   - Combine la **correction géométrique des fautes de frappe voisines (AZERTY/QWERTY)**, le **rétablissement automatique des apostrophes et accents** (`"jarrive" -> "j'arrive"`, `"cest" -> "c'est"`, `"tinquiete" -> "t'inquiète"`, `"aujourdhui" -> "aujourd'hui"`, `"dont" -> "don't"`) et la **prédiction du mot suivant**.
   - **100 % relié à `user_style_memory.json`** : Ne corrige jamais de force vos abréviations personnelles (`"tkt"`, `"bcp"`, `"btw"`).

3. **💻 Compatibilité Native Googlebook 14, Chromebook Plus & Tablettes Android (`arm64-v8a` + `x86_64` AVX2)**
   - Compilation C++ NDK double architecture : **ARM64 FP16+DotProd** (Google Tensor G4, Snapdragon X Elite `X1E-80-100` des HP Googlebook 14, Samsung Galaxy Tab S9/S10, Pixel Tablet) et **x86_64 AVX2+FMA+F16C** (Intel Core Ultra Series 3 des ASUS/Acer Googlebook 14 & Chromebook Plus).
   - **Interface 2 Colonnes Automatique** sur grand écran et **Mode Clavier Physique** (bascule automatiquement en barre vocale compacte et copie chaque phrase dictée dans le presse-papier pour `Ctrl+V`).

4. **🍎 Clavier Apple Natif + Catalogue Complet d'Émojis (`😀`)**
   - Clavier sombre Apple (`#1C1C1E`) avec loupe d'aperçu au toucher, barre d'accents par glissement à l'appui long, **clavier d'émojis Apple complet (9 catégories + Récents)**, **barre de 3 prédictions IA** et mode **Barre Vocale Réduite (`▾`)**.

5. **👑 Les 7 Modèles Whisper (jusqu'à Whisper TITAN Large-v3 1,08 Go) + Sécurité Matérielle**
   - De **Whisper Nano (31 Mo)** jusqu'à **Whisper TITAN (`Large-v3` complet 1,08 Go)**, avec bouton **« 🧪 Simuler un téléphone modeste (4 Go RAM) »**.

---

## 🛠️ Architecture & Crédits

- **Auteur Principal** : [@bluewolfy24yt](https://github.com/bluewolfy24yt)
- **Co-Auteur IA** : `Gemini-4-Argon`
- **Moteur IA Natif** : Basé sur [`ggml-org/whisper.cpp`](https://github.com/ggml-org/whisper.cpp) (`b5454`) — GPU Metal + FlashAttention (Apple) & NDK ARM64 SIMD + x86_64 AVX2 + Core Pinning (Android).
