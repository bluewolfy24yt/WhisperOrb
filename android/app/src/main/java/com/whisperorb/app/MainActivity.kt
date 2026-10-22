package com.whisperorb.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var engine: WhisperAndroidEngine
    private lateinit var downloader: ModelDownloader
    private lateinit var styleMemory: UserStyleMemoryManager

    private lateinit var mainOrbView: GeminiOrbCanvasView
    private lateinit var headerStatusDot: View
    private lateinit var headerStatusText: TextView
    private lateinit var orbStatusLabel: TextView
    private lateinit var engineBannerLabel: TextView
    private lateinit var step1EnableImeBtn: TextView
    private lateinit var step2SelectImeBtn: TextView
    private lateinit var languagePillsRow: LinearLayout
    private lateinit var testTranscriptInput: EditText
    private lateinit var modelsContainerLayout: LinearLayout
    private lateinit var styleReplacementsContainer: LinearLayout
    private lateinit var rawJsonPreviewText: TextView

    private val uiRefreshListener: () -> Unit = {
        refreshAllDynamicViews()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Barre de statut & barre de navigation Noir Cosmique pur (#07080C)
        window.statusBarColor = Color.parseColor("#07080C")
        window.navigationBarColor = Color.parseColor("#07080C")

        engine = WhisperAndroidEngine.getInstance(applicationContext)
        downloader = ModelDownloader.getInstance(applicationContext)
        styleMemory = UserStyleMemoryManager.getInstance(applicationContext)

        requestAudioPermissionIfNeeded()

        setContentView(buildResponsiveRootLayout())

        engine.addListener(uiRefreshListener)
        downloader.onStateChanged = {
            refreshModelsList()
        }

        if (downloader.isModelDownloaded(engine.selectedModel)) {
            engine.loadModel(engine.selectedModel)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllDynamicViews()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshKeyboardActivationSteps()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(buildResponsiveRootLayout())
        refreshAllDynamicViews()
    }

    override fun onDestroy() {
        engine.removeListener(uiRefreshListener)
        downloader.onStateChanged = null
        super.onDestroy()
    }

    private fun requestAudioPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    // MARK: - Architecture Responsive 1 Colonne (Smartphone) / 2 Colonnes (Tablettes Android, Chromebook Plus & Googlebook 14)

    private fun buildResponsiveRootLayout(): View {
        val isLargeScreen = HardwareDetector.isTabletOrLargeScreen(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#07080C"))
            isFillViewport = true
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = dp(if (isLargeScreen) 26 else 16)
            setPadding(hPad, dp(16), hPad, dp(36))
        }

        outer.addView(buildCosmicHeaderSection())
        outer.addView(space(dp(18)))

        val card1 = buildCard1StudioAndNativeKeyboardSection()
        val card2 = buildCard2ModelsCatalogSection()
        val card3 = buildCard3StyleMemorySection()
        val card4 = buildCard4AppearanceStudioSection()
        val card5 = buildCard5EcosystemAndGitHubSection()

        if (isLargeScreen) {
            // Mode 2 Colonnes automatique pour Tablettes Android (Galaxy Tab, Pixel Tablet), Chromebook Plus & Googlebook 14
            val columnsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10)
                }
            }
            val rightCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            }

            leftCol.addView(card1)
            leftCol.addView(space(dp(18)))
            leftCol.addView(card3)
            leftCol.addView(space(dp(18)))
            leftCol.addView(card5)

            rightCol.addView(card2)
            rightCol.addView(space(dp(18)))
            rightCol.addView(card4)

            columnsRow.addView(leftCol)
            columnsRow.addView(rightCol)
            outer.addView(columnsRow)
        } else {
            outer.addView(card1)
            outer.addView(space(dp(18)))
            outer.addView(card2)
            outer.addView(space(dp(18)))
            outer.addView(card3)
            outer.addView(space(dp(18)))
            outer.addView(card4)
            outer.addView(space(dp(18)))
            outer.addView(card5)
        }

        scroll.addView(outer)
        return scroll
    }

    // MARK: - En-Tête Cosmic Dark Pro (Identique à la Version Apple)

    private fun buildCosmicHeaderSection(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "WhisperOrb"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val versionPill = TextView(this).apply {
            text = "v2.0 • ON-DEVICE AI"
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#1A0A84FF", 20, "#400A84FF")
        }

        titleRow.addView(title)
        titleRow.addView(spaceHorizontal(dp(10)))
        titleRow.addView(versionPill)

        val telemetryText = TextView(this).apply {
            text = "${HardwareDetector.getTelemetrySummary(this@MainActivity)} • ${WhisperNativeBridge.getCpuTopologySummary()}"
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(4), 0, dp(4))
        }

        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        badgeRow.addView(
            CosmicUiFactory.createSpecBadge(
                this,
                HardwareDetector.getFormFactorBadge(this),
                "#D938BDF8",
                "#1438BDF8"
            )
        )
        if (HardwareDetector.isHardwareKeyboardAttached(this)) {
            badgeRow.addView(spaceHorizontal(dp(6)))
            badgeRow.addView(
                CosmicUiFactory.createSpecBadge(
                    this,
                    "⌨️ Clavier Physique Détecté (Ctrl+V auto)",
                    "#30D158",
                    "#1630D158"
                )
            )
        }

        leftCol.addView(titleRow)
        leftCol.addView(telemetryText)
        leftCol.addView(badgeRow)

        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#14FFFFFF", 20, "#20FFFFFF")
        }

        headerStatusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
            background = circleDrawable("#30D158")
        }
        headerStatusText = TextView(this).apply {
            text = "Prêt"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusPill.addView(headerStatusDot)
        statusPill.addView(headerStatusText)

        header.addView(leftCol)
        header.addView(spaceHorizontal(dp(8)))
        header.addView(statusPill)
        return header
    }

    // MARK: - Carte 1 : Studio Vocal, Vrai Clavier Android Natif & Correcteur IA

    private fun buildCard1StudioAndNativeKeyboardSection(): View {
        val card = createCosmicCard()

        card.addView(
            createCardHeader(
                icon = "🎙️",
                title = "Studio Vocal & Clavier Natif WhisperOrb",
                subtitle = "Orbe 3D incrusté dans le clavier, bouton « ▾ Réduire », 3 prédictions IA & clavier de chiffres auto"
            )
        )
        card.addView(space(dp(14)))

        // Scène Cosmique de l'Orbe 3D
        val stage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(18))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A0C12", 18, "#2238BDF8")
        }

        mainOrbView = GeminiOrbCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(128), dp(128))
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
            setOnClickListener {
                engine.toggleRecording()
            }
        }

        orbStatusLabel = TextView(this).apply {
            text = "${engine.orbState.statusTitle} • ${engine.selectedLanguage.shortPillLabel}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        engineBannerLabel = TextView(this).apply {
            text = engine.statusBannerMessage
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.CENTER
        }

        stage.addView(mainOrbView)
        stage.addView(space(dp(12)))
        stage.addView(orbStatusLabel)
        stage.addView(space(dp(4)))
        stage.addView(engineBannerLabel)
        card.addView(stage)
        card.addView(space(dp(14)))

        // Activation en 2 étapes du Vrai Clavier Android Natif (InputMethodService)
        val setupBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0C0E14", 16, "#1EFFFFFF")
        }

        val setupHeader = TextView(this).apply {
            text = "⌨️ Activation du Clavier Android WhisperOrb"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        setupBox.addView(setupHeader)
        setupBox.addView(space(dp(8)))

        val stepsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        step1EnableImeBtn = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        step2SelectImeBtn = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }

        stepsRow.addView(step1EnableImeBtn)
        stepsRow.addView(step2SelectImeBtn)
        setupBox.addView(stepsRow)
        card.addView(setupBox)
        card.addView(space(dp(14)))

        // Sélecteur de Langue par Pastilles Fluides (Zéro Spinner moche !)
        val langHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val langLabel = TextView(this).apply {
            text = "🌐 Langue de dictée & du correcteur IA"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val isoBadge = TextView(this).apply {
            text = "ISO : ${engine.selectedLanguage.resolvedWhisperCode.uppercase()}"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        langHeaderRow.addView(langLabel)
        langHeaderRow.addView(isoBadge)
        card.addView(langHeaderRow)
        card.addView(space(dp(8)))

        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        languagePillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        langScroll.addView(languagePillsRow)
        card.addView(langScroll)
        rebuildLanguagePills()
        card.addView(space(dp(14)))

        // Toggles fluides CosmicPillSwitch (Mode Clavier Réduit & Correcteur Automatique IA)
        card.addView(
            CosmicUiFactory.createSettingSwitchRow(
                context = this,
                title = "Mode clavier réduit (« ▾ Réduire »)",
                subtitle = "Replie tout le clavier en une barre vocale compacte de 58dp comme sur Apple",
                initialChecked = engine.isKeyboardCollapsed,
                activeColorHex = "#0A84FF"
            ) { checked ->
                engine.isKeyboardCollapsed = checked
            }
        )
        card.addView(space(dp(8)))

        card.addView(
            CosmicUiFactory.createSettingSwitchRow(
                context = this,
                title = "Correcteur automatique IA & 3 prédictions",
                subtitle = "Corrige les fautes de frappe et apostrophes (j'arrive, c'est, t'inquiète) en < 0,2 ms",
                initialChecked = engine.isSmartAutocorrectEnabled,
                activeColorHex = "#30D158"
            ) { checked ->
                engine.isSmartAutocorrectEnabled = checked
            }
        )
        card.addView(space(dp(14)))

        // Arène de Test en Direct (Clavier Complet avec Orbe vs Clavier de Chiffres Pur)
        val arenaLabel = TextView(this).apply {
            text = "✨ Arène de test en direct (Clavier Normal vs Clavier de Chiffres)"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(arenaLabel)
        card.addView(space(dp(6)))

        testTranscriptInput = EditText(this).apply {
            hint = "✍️ Touchez ici : ouvre le Clavier WhisperOrb avec l'Orbe incrusté, « ▾ Réduire » et les 3 prédictions IA..."
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A0C12", 14, "#24FFFFFF")
        }
        card.addView(testTranscriptInput)
        card.addView(space(dp(8)))

        val numericTestInput = EditText(this).apply {
            hint = "🔢 Touchez ici (Champ Chiffres / PIN) : bascule en Clavier de Chiffres pur SANS Orbe !"
            setHintTextColor(Color.parseColor("#99FF9F0A"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A0C12", 12, "#33FF9F0A")
        }
        card.addView(numericTestInput)

        refreshKeyboardActivationSteps()
        return card
    }

    private fun rebuildLanguagePills() {
        if (!::languagePillsRow.isInitialized) return
        languagePillsRow.removeAllViews()

        for (lang in WhisperLanguageSelection.entries) {
            val isSelected = (engine.selectedLanguage == lang)
            val pill = TextView(this).apply {
                text = if (lang == WhisperLanguageSelection.SYSTEM) {
                    "📱 Système (${WhisperLanguageSelection.detectedSystemISOCode.uppercase()})"
                } else {
                    lang.shortPillLabel
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = if (isSelected) {
                    CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A84FF", 12, "#66FFFFFF")
                } else {
                    CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0C0E14", 12, "#1AFFFFFF")
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    engine.selectedLanguage = lang
                    rebuildLanguagePills()
                    refreshStyleReplacementsList()
                }
            }
            languagePillsRow.addView(pill)
        }
    }

    private fun refreshKeyboardActivationSteps() {
        if (!::step1EnableImeBtn.isInitialized || !::step2SelectImeBtn.isInitialized) return
        val enabled = WhisperOrbInputMethodService.isImeEnabled(this)
        val selected = WhisperOrbInputMethodService.isImeSelectedAsDefault(this)

        step1EnableImeBtn.apply {
            text = if (enabled) "✓ 1. Clavier Activé" else "1. Activer le Clavier"
            setTextColor(if (enabled) Color.parseColor("#30D158") else Color.WHITE)
            background = if (enabled) {
                CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#1630D158", 12, "#4030D158")
            } else {
                CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A84FF", 12, "#55FFFFFF")
            }
        }

        step2SelectImeBtn.apply {
            text = if (selected) "✓ 2. Clavier Actif" else "2. Choisir WhisperOrb"
            setTextColor(if (selected) Color.parseColor("#30D158") else Color.WHITE)
            background = if (selected) {
                CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#1630D158", 12, "#4030D158")
            } else {
                CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A84FF", 12, "#55FFFFFF")
            }
        }
    }

    // MARK: - Carte 2 : Catalogue Complet des 7 Modèles Whisper (De Nano 31 Mo jusqu'à TITAN 1,08 Go)

    private fun buildCard2ModelsCatalogSection(): View {
        val card = createCosmicCard()
        val unlockedCount = HardwareDetector.unlockedTiers(this, engine.simulateModest4GBDevice).size

        card.addView(
            createCardHeader(
                icon = "🧠",
                title = "Catalogue des 7 Modèles Whisper",
                subtitle = "De Whisper Nano (31 Mo) jusqu'à Whisper TITAN Large-v3 (1,08 Go) • $unlockedCount/7 débloqués"
            )
        )
        card.addView(space(dp(12)))

        card.addView(
            CosmicUiFactory.createSettingSwitchRow(
                context = this,
                title = "🧪 Simuler un téléphone modeste (4 Go RAM)",
                subtitle = "Permet de tester le verrouillage automatique des modèles lourds sur ton appareil",
                initialChecked = engine.simulateModest4GBDevice,
                activeColorHex = "#FF9F0A"
            ) { checked ->
                engine.simulateModest4GBDevice = checked
                refreshModelsList()
            }
        )
        card.addView(space(dp(14)))

        modelsContainerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(modelsContainerLayout)

        refreshModelsList()
        return card
    }

    private fun refreshModelsList() {
        if (!::modelsContainerLayout.isInitialized) return
        modelsContainerLayout.removeAllViews()

        val simulate4GB = engine.simulateModest4GBDevice
        val recommended = HardwareDetector.recommendedTier(this, simulate4GB)

        for (tier in WhisperModelTier.entries) {
            val isUnlocked = HardwareDetector.isTierUnlocked(this, tier, simulate4GB)
            val isSelected = (engine.selectedModel == tier)
            val isDownloaded = downloader.isModelDownloaded(tier)
            val isDownloading = downloader.isDownloading(tier)
            val progress = downloader.downloadProgress[tier] ?: 0f

            val strokeHex = when {
                isSelected && isUnlocked -> "#0A84FF"
                !isUnlocked -> "#22FF9F0A"
                else -> "#18FFFFFF"
            }
            val fillHex = if (isSelected && isUnlocked) "#121624" else "#0B0D14"

            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = CosmicUiFactory.roundedCardDrawable(
                    this@MainActivity,
                    fillHex,
                    16,
                    strokeHex,
                    if (isSelected && isUnlocked) 2 else 1
                )
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameView = TextView(this).apply {
                text = tier.displayName
                setTextColor(if (isUnlocked) Color.WHITE else Color.parseColor("#75FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(nameView)

            if (tier == recommended && isUnlocked) {
                topRow.addView(
                    CosmicUiFactory.createSpecBadge(this, "★ Recommandé", "#38BDF8", "#1A38BDF8")
                )
                topRow.addView(spaceHorizontal(dp(6)))
            }

            val statusBadge = CosmicUiFactory.createSpecBadge(
                context = this,
                label = when {
                    !isUnlocked -> "🔒 ${tier.requiredRAMBadgeText}"
                    isDownloaded -> "✓ Prêt"
                    else -> "${tier.sizeMB} Mo"
                },
                textHex = when {
                    !isUnlocked -> "#FF9F0A"
                    isDownloaded -> "#30D158"
                    else -> "#B3FFFFFF"
                },
                bgHex = when {
                    !isUnlocked -> "#1AFF9F0A"
                    isDownloaded -> "#1A30D158"
                    else -> "#14FFFFFF"
                }
            )
            topRow.addView(statusBadge)

            val subView = TextView(this).apply {
                text = tier.subtitle
                setTextColor(Color.parseColor(if (isUnlocked) "#AAFFFFFF" else "#55FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(8))
            }

            // Barre de 3 pastilles techniques (Disque • RAM • Latence)
            val specsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            specsRow.addView(CosmicUiFactory.createSpecBadge(this, "💾 ${tier.sizeMB} Mo", "#B3FFFFFF", "#12FFFFFF"))
            specsRow.addView(spaceHorizontal(dp(6)))
            specsRow.addView(CosmicUiFactory.createSpecBadge(this, "🧠 ${tier.ramUsageMB} Mo RAM", "#B3FFFFFF", "#12FFFFFF"))
            specsRow.addView(spaceHorizontal(dp(6)))
            specsRow.addView(CosmicUiFactory.createSpecBadge(this, "⚡ ${tier.latencyDescription}", "#38BDF8", "#1238BDF8"))

            itemBox.addView(topRow)
            itemBox.addView(subView)
            itemBox.addView(specsRow)
            itemBox.addView(space(dp(10)))

            if (isDownloading) {
                val bar = CosmicProgressBar(this).apply {
                    progressFraction = progress
                }
                val cancelBtn = TextView(this).apply {
                    text = "Téléchargement en cours : ${(progress * 100).toInt()} %  •  Annuler ✕"
                    setTextColor(Color.parseColor("#FF453A"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(8), dp(10), dp(4))
                    setOnClickListener {
                        downloader.cancelDownload(tier)
                    }
                }
                itemBox.addView(bar)
                itemBox.addView(cancelBtn)
            } else if (!isUnlocked) {
                val lockNotice = TextView(this).apply {
                    text = "🔒 Verrouillé par sécurité matérielle (nécessite ${tier.requiredRAMBadgeText})"
                    setTextColor(Color.parseColor("#CCFF9F0A"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                }
                itemBox.addView(lockNotice)
            } else {
                val btnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                if (isDownloaded) {
                    val useBtn = TextView(this).apply {
                        text = if (isSelected) "✓ Modèle Actif en C++ NDK" else "Activer ce modèle"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(dp(14), dp(9), dp(14), dp(9))
                        background = CosmicUiFactory.roundedCardDrawable(
                            this@MainActivity,
                            if (isSelected) "#0A84FF" else "#1E2230",
                            10,
                            if (isSelected) "#66FFFFFF" else "#24FFFFFF"
                        )
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                        setOnClickListener {
                            engine.selectedModel = tier
                            engine.loadModel(tier)
                            refreshModelsList()
                        }
                    }

                    val delBtn = TextView(this).apply {
                        text = "Supprimer"
                        setTextColor(Color.parseColor("#FF453A"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(dp(12), dp(9), dp(12), dp(9))
                        background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#1AFF453A", 10, "#33FF453A")
                        setOnClickListener {
                            if (engine.selectedModel == tier) {
                                engine.unloadCurrentModel()
                            }
                            downloader.deleteModel(tier)
                        }
                    }

                    btnRow.addView(useBtn)
                    btnRow.addView(delBtn)
                } else {
                    val dlBtn = TextView(this).apply {
                        text = "⬇ Télécharger ${tier.shortBadgeName} (${tier.sizeMB} Mo)"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#1C2233", 10, "#38BDF8")
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setOnClickListener {
                            downloader.downloadModel(tier) { completedTier ->
                                engine.selectedModel = completedTier
                                engine.loadModel(completedTier)
                            }
                        }
                    }
                    btnRow.addView(dlBtn)
                }
                itemBox.addView(btnRow)
            }

            modelsContainerLayout.addView(itemBox)
            modelsContainerLayout.addView(space(dp(10)))
        }
    }

    // MARK: - Carte 3 : Mémoire d'Écriture JSON (user_style_memory.json)

    private fun buildCard3StyleMemorySection(): View {
        val card = createCosmicCard()
        val activeLang = engine.selectedLanguage.effectiveStyleLanguageCode.uppercase()

        card.addView(
            createCardHeader(
                icon = "📝",
                title = "Mémoire d'Écriture (user_style_memory.json)",
                subtitle = "Dictionnaire JSON local d'abréviations par langue ($activeLang) partagé avec la dictée et le correcteur IA"
            )
        )
        card.addView(space(dp(12)))

        card.addView(
            CosmicUiFactory.createSettingSwitchRow(
                context = this,
                title = "Activer user_style_memory.json",
                subtitle = "Remplace automatiquement vos expressions en < 0,05 ms avec 0 % de risque d'hallucination",
                initialChecked = styleMemory.isMemoryEnabled,
                activeColorHex = "#30D158"
            ) { checked ->
                styleMemory.isMemoryEnabled = checked
                styleMemory.saveToDisk()
            }
        )
        card.addView(space(dp(8)))

        card.addView(
            CosmicUiFactory.createSettingSwitchRow(
                context = this,
                title = "Messages courts sans point final '.'",
                subtitle = "Retire le point final sur les messages de 12 mots ou moins (style SMS / WhatsApp)",
                initialChecked = styleMemory.stripTrailingPeriodOnShortMessages,
                activeColorHex = "#0A84FF"
            ) { checked ->
                styleMemory.stripTrailingPeriodOnShortMessages = checked
                styleMemory.saveToDisk()
            }
        )
        card.addView(space(dp(12)))

        // Ajout d'une règle personnalisée
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val spokenInput = EditText(this).apply {
            hint = "Mot dicté (ex: t'inquiète)"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A0C12", 10, "#20FFFFFF")
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val writtenInput = EditText(this).apply {
            hint = "Écrit (tkt)"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A0C12", 10, "#20FFFFFF")
            layoutParams = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }

        val addBtn = TextView(this).apply {
            text = "+ Ajouter"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A84FF", 10, "#55FFFFFF")
            setOnClickListener {
                val lang = engine.selectedLanguage.effectiveStyleLanguageCode
                styleMemory.setReplacement(
                    spoken = spokenInput.text.toString(),
                    written = writtenInput.text.toString(),
                    languageCode = lang
                )
                spokenInput.setText("")
                writtenInput.setText("")
                refreshStyleReplacementsList()
            }
        }

        addRow.addView(spokenInput)
        addRow.addView(writtenInput)
        addRow.addView(addBtn)
        card.addView(addRow)
        card.addView(space(dp(10)))

        styleReplacementsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(styleReplacementsContainer)
        card.addView(space(dp(8)))

        rawJsonPreviewText = TextView(this).apply {
            setTextColor(Color.parseColor("#30D158"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#090B10", 12, "#2230D158")
            visibility = View.GONE
        }

        val jsonBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toggleJsonBtn = TextView(this).apply {
            text = "{ } Voir user_style_memory.json"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#14FFFFFF", 10, "#24FFFFFF")
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                rawJsonPreviewText.text = styleMemory.prettyJSONString()
                rawJsonPreviewText.visibility = if (rawJsonPreviewText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        val copyJsonBtn = TextView(this).apply {
            text = "Copier JSON"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#14FFFFFF", 10, "#24FFFFFF")
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("user_style_memory.json", styleMemory.prettyJSONString()))
                Toast.makeText(this@MainActivity, "user_style_memory.json copié !", Toast.LENGTH_SHORT).show()
            }
        }

        jsonBtnRow.addView(toggleJsonBtn)
        jsonBtnRow.addView(copyJsonBtn)
        card.addView(jsonBtnRow)
        card.addView(space(dp(8)))
        card.addView(rawJsonPreviewText)

        refreshStyleReplacementsList()
        return card
    }

    private fun refreshStyleReplacementsList() {
        if (!::styleReplacementsContainer.isInitialized) return
        styleReplacementsContainer.removeAllViews()

        val lang = engine.selectedLanguage.effectiveStyleLanguageCode
        val pairs = styleMemory.getReplacementsForLanguage(lang)

        for ((spoken, written) in pairs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0B0D14", 10, "#16FFFFFF")
            }

            val label = TextView(this).apply {
                text = "« $spoken »   →   $written"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val del = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FF453A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(2), dp(4), dp(2))
                setOnClickListener {
                    styleMemory.removeReplacement(spoken, lang)
                    refreshStyleReplacementsList()
                }
            }

            row.addView(label)
            row.addView(del)
            styleReplacementsContainer.addView(row)
            styleReplacementsContainer.addView(space(dp(6)))
        }
    }

    // MARK: - Carte 4 : Studio d'Apparence de l'Orbe 3D

    private fun buildCard4AppearanceStudioSection(): View {
        val card = createCosmicCard()
        card.addView(
            createCardHeader(
                icon = "🎨",
                title = "Studio d'Apparence de l'Orbe 3D",
                subtitle = "Thèmes Éclipse (Trou Noir), Perle et couleurs de l'onde lumineuse synchronisées avec le clavier"
            )
        )
        card.addView(space(dp(12)))

        val presets = listOf(
            Triple("🕳️ Éclipse (Noir)", "#050507", "#050507"),
            Triple("⚪ Perle (Blanc)", "#F8F9FA", "#FFFFFF"),
            Triple("🔵 Bleu Siri", "#08080C", "#0088FF"),
            Triple("💎 Cyan", "#08080C", "#00E5FF"),
            Triple("🟣 Violet", "#08080C", "#8A2BE2"),
            Triple("🟢 Émeraude", "#08080C", "#10B981"),
            Triple("🟡 Or", "#08080C", "#F59E0B"),
            Triple("🌸 Rose", "#08080C", "#FF2A85"),
            Triple("🔴 Rouge", "#08080C", "#EF4444")
        )

        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for ((label, bg, fg) in presets) {
            val isLight = (bg == "#F8F9FA")
            val swatch = TextView(this).apply {
                text = label
                setTextColor(if (isLight) Color.BLACK else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = CosmicUiFactory.roundedCardDrawable(
                    this@MainActivity,
                    if (isLight) "#F8F9FA" else "#0C0E15",
                    12,
                    if (fg == "#050507") "#44FFFFFF" else fg,
                    2
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    engine.bgHex = bg
                    engine.fgHex = fg
                }
            }
            row.addView(swatch)
        }

        hScroll.addView(row)
        card.addView(hScroll)
        return card
    }

    // MARK: - Carte 5 : Écosystème Multi-Plateforme & Dépôt GitHub v2.0.0

    private fun buildCard5EcosystemAndGitHubSection(): View {
        val card = createCosmicCard()
        card.addView(
            createCardHeader(
                icon = "🚀",
                title = "Écosystème Unifié v2.0.0",
                subtitle = "Apple (iPhone, iPad, Mac) • Android • Tablettes • Chromebook Plus • Googlebook 14"
            )
        )
        card.addView(space(dp(10)))

        val specsInfo = TextView(this).apply {
            text = "✓ Compilation C++ NDK multi-architecture (ARM64 FP16+DotProd & x86_64 AVX2+FMA)\n" +
                "✓ Compatible Snapdragon X Elite & Intel Core Ultra (Googlebook 14 & Chromebook Plus)\n" +
                "✓ 100 % Hors-Ligne • Zéro Serveur • Zéro Télémétrie"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        card.addView(specsInfo)
        card.addView(space(dp(12)))

        val ghBtn = TextView(this).apply {
            text = "🌐 Ouvrir github.com/bluewolfy24yt/WhisperOrb (Releases v2.0.0)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#0A84FF", 12, "#55FFFFFF")
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bluewolfy24yt/WhisperOrb/releases/latest")))
            }
        }
        card.addView(ghBtn)
        return card
    }

    // MARK: - Rafraîchissement Temps Réel

    private fun refreshAllDynamicViews() {
        if (::mainOrbView.isInitialized) {
            mainOrbView.bgHex = engine.bgHex
            mainOrbView.fgHex = engine.fgHex
            mainOrbView.orbState = engine.orbState
            mainOrbView.audioLevel = engine.currentAudioLevel
        }
        if (::orbStatusLabel.isInitialized) {
            orbStatusLabel.text = "${engine.orbState.statusTitle} • ${engine.selectedLanguage.shortPillLabel}"
        }
        if (::engineBannerLabel.isInitialized) {
            val err = engine.lastErrorMessage
            if (!err.isNullOrBlank()) {
                engineBannerLabel.text = err
                engineBannerLabel.setTextColor(Color.parseColor("#FF453A"))
            } else {
                engineBannerLabel.text = engine.statusBannerMessage
                engineBannerLabel.setTextColor(Color.parseColor("#38BDF8"))
            }
        }
        if (::headerStatusDot.isInitialized && ::headerStatusText.isInitialized) {
            val (dotHex, label) = when (engine.orbState) {
                WhisperOrbState.IDLE -> "#30D158" to "Prêt"
                WhisperOrbState.LISTENING -> "#0A84FF" to "Écoute..."
                WhisperOrbState.TRANSCRIBING -> "#FFD60A" to "C++..."
            }
            headerStatusDot.background = circleDrawable(dotHex)
            headerStatusText.text = label
        }
        refreshKeyboardActivationSteps()
    }

    // MARK: - Helpers Visuels

    private fun createCosmicCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#11131B", 20, "#1CFFFFFF")
        }
    }

    private fun createCardHeader(icon: String, title: String, subtitle: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBadge = TextView(this).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
            background = CosmicUiFactory.roundedCardDrawable(this@MainActivity, "#180A84FF", 10, "#330A84FF")
        }

        val t = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.DEFAULT_BOLD
        }

        titleRow.addView(iconBadge)
        titleRow.addView(t)

        val s = TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(5), 0, 0)
        }

        box.addView(titleRow)
        box.addView(s)
        return box
    }

    private fun circleDrawable(hex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
    }

    private fun space(px: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px)
        }
    }

    private fun spaceHorizontal(px: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(px, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
