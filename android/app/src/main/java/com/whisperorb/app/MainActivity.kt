package com.whisperorb.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
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
    private lateinit var orbStatusLabel: TextView
    private lateinit var engineBannerLabel: TextView
    private lateinit var accessibilityStatusBtn: Button
    private lateinit var testTranscriptInput: EditText
    private lateinit var modelsContainerLayout: LinearLayout
    private lateinit var styleReplacementsContainer: LinearLayout
    private lateinit var rawJsonPreviewText: TextView

    private val uiRefreshListener: () -> Unit = {
        refreshAllDynamicViews()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = WhisperAndroidEngine.getInstance(applicationContext)
        downloader = ModelDownloader.getInstance(applicationContext)
        styleMemory = UserStyleMemoryManager.getInstance(applicationContext)

        requestAudioPermissionIfNeeded()

        setContentView(buildRootLayout())

        engine.addListener(uiRefreshListener)
        downloader.onStateChanged = {
            refreshModelsList()
        }

        // Pré-charge automatiquement un modèle déjà téléchargé s'il y en a un
        if (downloader.isModelDownloaded(engine.selectedModel)) {
            engine.loadModel(engine.selectedModel)
        }
    }

    override fun onResume() {
        super.onResume()
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

    // MARK: - Interface Sobre & Grand Public (Noir Cosmique #09090B)

    private fun buildRootLayout(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#09090B"))
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(32))
        }

        content.addView(buildHeaderSection())
        content.addView(space(dp(16)))
        content.addView(buildCard1VoiceAndKeyboardSection())
        content.addView(space(dp(16)))
        content.addView(buildCard2ModelsCatalogSection())
        content.addView(space(dp(16)))
        content.addView(buildCard3StyleMemorySection())
        content.addView(space(dp(16)))
        content.addView(buildCard4AppearanceSection())
        content.addView(space(dp(16)))
        content.addView(buildCard5GitHubSection())

        scroll.addView(content)
        return scroll
    }

    private fun buildHeaderSection(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "WhisperOrb"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val badge = TextView(this).apply {
            text = "v2.0 • ON-DEVICE AI"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedShape("#18FFFFFF", dp(12))
        }

        titleRow.addView(title)
        titleRow.addView(spaceHorizontal(dp(10)))
        titleRow.addView(badge)

        val hwSummary = TextView(this).apply {
            text = "${HardwareDetector.getTelemetrySummary(this@MainActivity)} • ${WhisperNativeBridge.getCpuTopologySummary()}"
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        box.addView(titleRow)
        box.addView(space(dp(4)))
        box.addView(hwSummary)
        return box
    }

    // MARK: - Carte 1 : Studio Vocal, Clavier Android par Défaut & Sélecteur Multilingue

    private fun buildCard1VoiceAndKeyboardSection(): View {
        val card = createCardContainer()

        card.addView(
            createCardHeader(
                title = "Studio Vocal & Clavier par Défaut",
                subtitle = "L'Orbe se greffe sur votre clavier Android habituel et ignore les claviers de chiffres"
            )
        )
        card.addView(space(dp(14)))

        // Scène de l'Orbe 3D
        val stage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(16))
            background = roundedShape("#0D0D11", dp(18), "#14FFFFFF")
        }

        mainOrbView = GeminiOrbCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(118), dp(118))
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
            setOnClickListener {
                engine.toggleRecording()
            }
        }

        orbStatusLabel = TextView(this).apply {
            text = "${engine.orbState.statusTitle} • Touchez l'Orbe pour parler"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
        }

        engineBannerLabel = TextView(this).apply {
            text = engine.statusBannerMessage
            setTextColor(Color.parseColor("#30D158"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.CENTER
        }

        stage.addView(mainOrbView)
        stage.addView(space(dp(10)))
        stage.addView(orbStatusLabel)
        stage.addView(space(dp(4)))
        stage.addView(engineBannerLabel)
        card.addView(stage)
        card.addView(space(dp(12)))

        // Bouton d'activation de l'Orbe sur le clavier Android par défaut
        accessibilityStatusBtn = Button(this).apply {
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
        card.addView(accessibilityStatusBtn)
        card.addView(space(dp(12)))

        // Sélecteur intelligent de langue multilingue
        val langLabel = TextView(this).apply {
            text = "🌐 Langue de dictée Whisper (Zéro langue forcée en dur)"
            setTextColor(Color.parseColor("#D9FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(langLabel)
        card.addView(space(dp(6)))

        val allLangs = WhisperLanguageSelection.entries
        val langNames = allLangs.map { "${it.flagEmoji} ${it.displayName}" }
        val spinner = Spinner(this).apply {
            background = roundedShape("#0D0D11", dp(10), "#18FFFFFF")
            setPadding(dp(10), dp(8), dp(10), dp(8))
            adapter = object : ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                langNames
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE)
                    v.textSize = 13f
                    return v
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getDropDownView(position, convertView, parent) as TextView
                    v.setBackgroundColor(Color.parseColor("#18181E"))
                    v.setTextColor(Color.WHITE)
                    v.setPadding(dp(14), dp(12), dp(14), dp(12))
                    return v
                }
            }
            setSelection(allLangs.indexOf(engine.selectedLanguage).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val chosen = allLangs[position]
                    if (engine.selectedLanguage != chosen) {
                        engine.selectedLanguage = chosen
                        refreshStyleReplacementsList()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        card.addView(spinner)
        card.addView(space(dp(12)))

        // Zone de test Clavier Normal vs Clavier de Chiffres
        val testHeader = TextView(this).apply {
            text = "Zone de test en direct (Clavier Normal vs Clavier de Chiffres)"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        card.addView(testHeader)
        card.addView(space(dp(6)))

        testTranscriptInput = EditText(this).apply {
            hint = "Touchez ici pour ouvrir votre clavier normal (l'Orbe apparaît au-dessus)..."
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedShape("#0D0D11", dp(12), "#18FFFFFF")
        }
        card.addView(testTranscriptInput)
        card.addView(space(dp(8)))

        val numericTestInput = EditText(this).apply {
            hint = "🔢 Touchez ici pour ouvrir le clavier de CHIFFRES (l'Orbe se masque automatiquement !)"
            setHintTextColor(Color.parseColor("#80FF9F0A"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedShape("#0D0D11", dp(10), "#26FF9F0A")
        }
        card.addView(numericTestInput)

        return card
    }

    // MARK: - Carte 2 : Catalogue Complet des 7 Modèles Whisper + Simulation 4 Go RAM

    private fun buildCard2ModelsCatalogSection(): View {
        val card = createCardContainer()
        val unlockedCount = HardwareDetector.unlockedTiers(this, engine.simulateModest4GBDevice).size

        card.addView(
            createCardHeader(
                title = "Catalogue des 7 Modèles Whisper",
                subtitle = "De Whisper Nano (31 Mo) jusqu'à Whisper TITAN Large-v3 (1,08 Go) • $unlockedCount/7 débloqués"
            )
        )
        card.addView(space(dp(12)))

        // Switch Simuler un téléphone modeste (4 Go RAM)
        val simSwitch = Switch(this).apply {
            text = "🧪 Simuler un téléphone modeste (4 Go RAM)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            isChecked = engine.simulateModest4GBDevice
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedShape("#0D0D11", dp(10), "#22FF9F0A")
            setOnCheckedChangeListener { _, checked ->
                engine.simulateModest4GBDevice = checked
                refreshModelsList()
            }
        }
        card.addView(simSwitch)
        card.addView(space(dp(12)))

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

            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val borderHex = if (isSelected && isUnlocked) "#0A84FF" else "#14FFFFFF"
                background = roundedShape("#0D0D11", dp(14), borderHex)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameView = TextView(this).apply {
                text = if (tier == recommended && isUnlocked) "${tier.displayName}  ★ Recommandé" else tier.displayName
                setTextColor(if (isUnlocked) Color.WHITE else Color.parseColor("#70FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusBadge = TextView(this).apply {
                text = when {
                    !isUnlocked -> "🔒 ${tier.requiredRAMBadgeText}"
                    isDownloaded -> "Téléchargé"
                    else -> "${tier.sizeMB} Mo"
                }
                setTextColor(
                    when {
                        !isUnlocked -> Color.parseColor("#FF9F0A")
                        isDownloaded -> Color.parseColor("#30D158")
                        else -> Color.parseColor("#99FFFFFF")
                    }
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
            }

            topRow.addView(nameView)
            topRow.addView(statusBadge)

            val subView = TextView(this).apply {
                text = "${tier.subtitle}\n${tier.sizeMB} Mo disque • ${tier.ramUsageMB} Mo RAM • Latence ${tier.latencyDescription}"
                setTextColor(Color.parseColor(if (isUnlocked) "#99FFFFFF" else "#55FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            }

            itemBox.addView(topRow)
            itemBox.addView(space(dp(4)))
            itemBox.addView(subView)
            itemBox.addView(space(dp(8)))

            if (isDownloading) {
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    this.progress = (progress * 100).toInt()
                }
                val cancelBtn = Button(this).apply {
                    text = "Annuler (${(progress * 100).toInt()} %)"
                    isAllCaps = false
                    setTextColor(Color.parseColor("#FF453A"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        downloader.cancelDownload(tier)
                    }
                }
                itemBox.addView(bar)
                itemBox.addView(cancelBtn)
            } else if (!isUnlocked) {
                val lockNotice = TextView(this).apply {
                    text = "Verrouillé par sécurité matérielle (nécessite ${tier.requiredRAMBadgeText})"
                    setTextColor(Color.parseColor("#80FF9F0A"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }
                itemBox.addView(lockNotice)
            } else {
                val btnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                if (isDownloaded) {
                    val useBtn = Button(this).apply {
                        text = if (isSelected) "✓ Modèle Actif" else "Utiliser ce modèle"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        background = roundedShape(if (isSelected) "#0A84FF" else "#24FFFFFF", dp(8))
                        setOnClickListener {
                            engine.selectedModel = tier
                            engine.loadModel(tier)
                            refreshModelsList()
                        }
                    }
                    val delBtn = Button(this).apply {
                        text = "Supprimer"
                        isAllCaps = false
                        setTextColor(Color.parseColor("#FF453A"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        background = roundedShape("#18FFFFFF", dp(8))
                        setOnClickListener {
                            if (engine.selectedModel == tier) {
                                engine.unloadCurrentModel()
                            }
                            downloader.deleteModel(tier)
                        }
                    }
                    btnRow.addView(useBtn)
                    btnRow.addView(spaceHorizontal(dp(8)))
                    btnRow.addView(delBtn)
                } else {
                    val dlBtn = Button(this).apply {
                        text = "⬇ Télécharger (${tier.sizeMB} Mo)"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        background = roundedShape("#22FFFFFF", dp(8))
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
        val card = createCardContainer()
        val activeLang = engine.selectedLanguage.effectiveStyleLanguageCode.uppercase()

        card.addView(
            createCardHeader(
                title = "Mémoire d'Écriture (user_style_memory.json)",
                subtitle = "Fichier JSON local d'abréviations par langue ($activeLang) et style de ponctuation"
            )
        )
        card.addView(space(dp(12)))

        val enableSwitch = Switch(this).apply {
            text = "Activer user_style_memory.json (< 0,05 ms)"
            setTextColor(Color.WHITE)
            isChecked = styleMemory.isMemoryEnabled
            setOnCheckedChangeListener { _, checked ->
                styleMemory.isMemoryEnabled = checked
                styleMemory.saveToDisk()
            }
        }
        card.addView(enableSwitch)
        card.addView(space(dp(8)))

        val periodSwitch = Switch(this).apply {
            text = "Retirer le point final '.' sur les messages courts (≤ 12 mots)"
            setTextColor(Color.WHITE)
            isChecked = styleMemory.stripTrailingPeriodOnShortMessages
            setOnCheckedChangeListener { _, checked ->
                styleMemory.stripTrailingPeriodOnShortMessages = checked
                styleMemory.saveToDisk()
            }
        }
        card.addView(periodSwitch)
        card.addView(space(dp(12)))

        // Ajout d'une abréviation
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val spokenInput = EditText(this).apply {
            hint = "Mot dicté (ex: t'inquiète)"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedShape("#0D0D11", dp(8), "#18FFFFFF")
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val writtenInput = EditText(this).apply {
            hint = "Écrit (tkt)"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedShape("#0D0D11", dp(8), "#18FFFFFF")
            layoutParams = LinearLayout.LayoutParams(dp(95), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val addBtn = Button(this).apply {
            text = "+ Ajouter"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = roundedShape("#0A84FF", dp(8))
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
        addRow.addView(spaceHorizontal(dp(6)))
        addRow.addView(writtenInput)
        addRow.addView(spaceHorizontal(dp(6)))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedShape("#0D0D11", dp(10))
            visibility = View.GONE
        }

        val jsonBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toggleJsonBtn = Button(this).apply {
            text = "{ } Voir JSON"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            background = roundedShape("#1CFFFFFF", dp(8))
            setOnClickListener {
                rawJsonPreviewText.text = styleMemory.prettyJSONString()
                rawJsonPreviewText.visibility = if (rawJsonPreviewText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        val copyJsonBtn = Button(this).apply {
            text = "Copier JSON"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            background = roundedShape("#1CFFFFFF", dp(8))
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("user_style_memory.json", styleMemory.prettyJSONString()))
                Toast.makeText(this@MainActivity, "user_style_memory.json copié !", Toast.LENGTH_SHORT).show()
            }
        }

        jsonBtnRow.addView(toggleJsonBtn)
        jsonBtnRow.addView(spaceHorizontal(dp(8)))
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
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedShape("#0D0D11", dp(8))
            }

            val label = TextView(this).apply {
                text = "« $spoken »  →  $written"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val del = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FF453A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(8), dp(2), dp(4), dp(2))
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

    // MARK: - Carte 4 : Apparence de l'Orbe

    private fun buildCard4AppearanceSection(): View {
        val card = createCardContainer()
        card.addView(
            createCardHeader(
                title = "Apparence de l'Orbe 3D",
                subtitle = "Thèmes Éclipse (Trou Noir), Perle et couleurs de l'onde lumineuse"
            )
        )
        card.addView(space(dp(12)))

        val presets = listOf(
            "Bleu Siri" to "#0088FF",
            "Cyan" to "#00E5FF",
            "Violet" to "#8A2BE2",
            "Émeraude" to "#10B981",
            "Or" to "#F59E0B",
            "Rose" to "#FF2A85",
            "Rouge" to "#EF4444"
        )

        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val eclipseBtn = Button(this).apply {
            text = "🕳️ Éclipse (Noir)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = roundedShape("#0D0D11", dp(8), "#33FFFFFF")
            setOnClickListener {
                engine.bgHex = "#050507"
                engine.fgHex = "#050507"
            }
        }
        row.addView(eclipseBtn)
        row.addView(spaceHorizontal(dp(8)))

        val pearlBtn = Button(this).apply {
            text = "⚪ Perle (Blanc)"
            isAllCaps = false
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = roundedShape("#F8F9FA", dp(8))
            setOnClickListener {
                engine.bgHex = "#F8F9FA"
                engine.fgHex = "#FFFFFF"
            }
        }
        row.addView(pearlBtn)
        row.addView(spaceHorizontal(dp(8)))

        for ((name, hex) in presets) {
            val btn = Button(this).apply {
                text = name
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = roundedShape(hex, dp(8))
                setOnClickListener {
                    engine.bgHex = "#08080C"
                    engine.fgHex = hex
                }
            }
            row.addView(btn)
            row.addView(spaceHorizontal(dp(8)))
        }

        hScroll.addView(row)
        card.addView(hScroll)
        return card
    }

    // MARK: - Carte 5 : Dépôt GitHub v2.0.0

    private fun buildCard5GitHubSection(): View {
        val card = createCardContainer()
        card.addView(
            createCardHeader(
                title = "GitHub Open-Source (v2.0.0)",
                subtitle = "Écosystème unifié Apple (.ipa) & Android (.apk)"
            )
        )
        card.addView(space(dp(10)))

        val btn = Button(this).apply {
            text = "Ouvrir github.com/bluewolfy24yt/WhisperOrb"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            background = roundedShape("#0A84FF", dp(10))
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bluewolfy24yt/WhisperOrb")))
            }
        }
        card.addView(btn)
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
            engineBannerLabel.text = engine.lastErrorMessage ?: engine.statusBannerMessage
            engineBannerLabel.setTextColor(
                if (engine.lastErrorMessage != null) Color.parseColor("#FF9F0A") else Color.parseColor("#30D158")
            )
        }
        if (::accessibilityStatusBtn.isInitialized) {
            val active = WhisperOrbAccessibilityService.isServiceRunning
            accessibilityStatusBtn.text = if (active) {
                "✅ Orbe ACTIF sur votre clavier Android par défaut (Protection chiffres active)"
            } else {
                "⚡ Activer l'Orbe sur le clavier Android par défaut (Accessibilité)"
            }
            accessibilityStatusBtn.background = roundedShape(
                if (active) "#1E30D158" else "#0A84FF",
                dp(12),
                if (active) "#30D158" else null
            )
        }
        if (::testTranscriptInput.isInitialized && engine.liveTestTranscript.isNotEmpty()) {
            if (testTranscriptInput.text.toString() != engine.liveTestTranscript) {
                testTranscriptInput.setText(engine.liveTestTranscript)
                testTranscriptInput.setSelection(testTranscriptInput.text.length)
            }
        }
        if (::rawJsonPreviewText.isInitialized && rawJsonPreviewText.visibility == View.VISIBLE) {
            rawJsonPreviewText.text = styleMemory.prettyJSONString()
        }
    }

    // MARK: - Helpers Visuels

    private fun createCardContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedShape("#131317", dp(18), "#14FFFFFF")
        }
    }

    private fun createCardHeader(title: String, subtitle: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val t = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val s = TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        col.addView(t)
        col.addView(space(dp(2)))
        col.addView(s)
        return col
    }

    private fun roundedShape(fillHex: String, radiusPx: Int, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun space(heightPx: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        }
    }

    private fun spaceHorizontal(widthPx: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, 1)
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
