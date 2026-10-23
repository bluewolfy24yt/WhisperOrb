package com.whisperorb.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import helium314.keyboard.latin.whisperorb.WhisperOrbImeHook
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class WhisperOrbEmbeddedBarController private constructor(
    private val appContext: Context
) : WhisperOrbImeHook {

    companion object {
        @Volatile
        private var instance: WhisperOrbEmbeddedBarController? = null

        fun getInstance(context: Context): WhisperOrbEmbeddedBarController {
            return instance ?: synchronized(this) {
                instance ?: WhisperOrbEmbeddedBarController(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private data class OrbThemeOption(
        val label: String,
        val swatchHex: String,
        val bgHex: String?,
        val fgHex: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("whisperorb_prefs", Context.MODE_PRIVATE)
    private val engine = WhisperAndroidEngine.getInstance(appContext)
    private val downloader = ModelDownloader.getInstance(appContext)

    private var currentIme: InputMethodService? = null
    private var currentEditorInfo: EditorInfo? = null
    private var inputViewRef: View? = null
    private var headerSlotRef: FrameLayout? = null
    private var panelSlotRef: FrameLayout? = null
    private var stripContainerRef: View? = null
    private var keyboardWrapperRef: View? = null

    private var isCollapsedMode: Boolean = false
    private var isQuickSelectorOpen: Boolean = false
    private var isNumericFieldFocused: Boolean = false

    private var collapseExpandBtn: TextView? = null
    private var statusDotView: View? = null
    private var statusTitleTv: TextView? = null
    private var langPillTv: TextView? = null
    private var undoBtnTv: TextView? = null
    private var gearBtnTv: TextView? = null
    private var closeCollapsedBtnTv: TextView? = null
    private var orbView: GeminiOrbCanvasView? = null

    private var quickLangRow: LinearLayout? = null
    private var quickOptionsRow: LinearLayout? = null
    private var quickModelRow: LinearLayout? = null
    private var quickColorRow: LinearLayout? = null

    private var fallbackTranscribeCallback: ((String) -> Unit)? = null
    private var fallbackDeleteCallback: ((Int) -> Unit)? = null

    private var autoSendAfterDictation: Boolean
        get() = prefs.getBoolean("autoSendAfterDictation", false)
        set(value) {
            prefs.edit().putBoolean("autoSendAfterDictation", value).apply()
            engine.notifyListeners()
        }

    private val keyboardTranscribeCallback: (String) -> Unit = { text ->
        mainHandler.post {
            handleTranscribedText(text)
        }
    }

    private val keyboardDeleteCallback: (Int) -> Unit = { len ->
        mainHandler.post {
            handleDeleteLastSentence(len)
        }
    }

    private val engineListener: () -> Unit = {
        mainHandler.post {
            ensureEngineCallbacksHooked()
            applyBarAndKeyboardLayoutState()
            refreshBarAndPanelContent()
        }
    }

    init {
        engine.addListener(engineListener)
        downloader.onStateChanged = {
            mainHandler.post {
                refreshBarAndPanelContent()
            }
        }
        ensureEngineCallbacksHooked()
    }

    private fun ensureEngineCallbacksHooked() {
        val currentTranscribe = engine.onTranscribedTextForKeyboard
        if (currentTranscribe != null && currentTranscribe !== keyboardTranscribeCallback) {
            fallbackTranscribeCallback = currentTranscribe
        }
        engine.onTranscribedTextForKeyboard = keyboardTranscribeCallback

        val currentDelete = engine.onDeleteLastSentenceRequested
        if (currentDelete != null && currentDelete !== keyboardDeleteCallback) {
            fallbackDeleteCallback = currentDelete
        }
        engine.onDeleteLastSentenceRequested = keyboardDeleteCallback
    }

    // MARK: - WhisperOrbImeHook Implementation

    override fun onInputViewCreated(
        ime: InputMethodService,
        inputView: View,
        headerSlot: FrameLayout,
        panelSlot: FrameLayout,
        stripContainer: View,
        keyboardWrapper: View
    ) {
        currentIme = ime
        inputViewRef = inputView
        headerSlotRef = headerSlot
        panelSlotRef = panelSlot
        stripContainerRef = stripContainer
        keyboardWrapperRef = keyboardWrapper

        ensureEngineCallbacksHooked()

        headerSlot.removeAllViews()
        panelSlot.removeAllViews()

        val weldedBar = buildWeldedHeaderBar(headerSlot.context)
        headerSlot.addView(
            weldedBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(headerSlot.context, 58)
            )
        )

        val inlinePanel = buildInlineQuickSettingsPanel(panelSlot.context)
        panelSlot.addView(
            inlinePanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(panelSlot.context, 280)
            )
        )

        applyBarAndKeyboardLayoutState()
        refreshBarAndPanelContent()
    }

    override fun onStartInputView(ime: InputMethodService, info: EditorInfo?, restarting: Boolean) {
        currentIme = ime
        currentEditorInfo = info
        ensureEngineCallbacksHooked()

        val inputType = info?.inputType ?: 0
        val maskedClass = inputType and InputType.TYPE_MASK_CLASS
        isNumericFieldFocused = (
            maskedClass == InputType.TYPE_CLASS_NUMBER ||
                maskedClass == InputType.TYPE_CLASS_PHONE ||
                maskedClass == InputType.TYPE_CLASS_DATETIME
            )

        if (isNumericFieldFocused) {
            isQuickSelectorOpen = false
            isCollapsedMode = false
            engine.isKeyboardCollapsed = false
        }

        applyBarAndKeyboardLayoutState()
        refreshBarAndPanelContent()
    }

    override fun onFinishInputView(ime: InputMethodService, finishingInput: Boolean) {
        if (isQuickSelectorOpen) {
            isQuickSelectorOpen = false
        }
        applyBarAndKeyboardLayoutState()
        refreshBarAndPanelContent()
    }

    override fun isKeyboardCollapsed(): Boolean {
        return engine.isGboardBarEnabled && !isNumericFieldFocused && isCollapsedMode && !isQuickSelectorOpen
    }

    override fun isSettingsPanelOpen(): Boolean {
        return engine.isGboardBarEnabled && !isNumericFieldFocused && isQuickSelectorOpen
    }

    // MARK: - Construction de la Barre Soudée (headerSlot = 58dp)

    private fun buildWeldedHeaderBar(ctx: Context): LinearLayout {
        val topCornerPx = dp(ctx, 18).toFloat()
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    topCornerPx, topCornerPx,
                    topCornerPx, topCornerPx,
                    0f, 0f,
                    0f, 0f
                )
                setColor(Color.parseColor("#F6121318"))
                setStroke(max(1, dp(ctx, 1)), Color.parseColor("#2EFFFFFF"))
            }
        }

        // 1. Bouton ▾ Réduire / ▴ Clavier
        val collapseBtn = TextView(ctx).apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            setOnClickListener {
                performLightHaptic()
                handleCollapseOrExpandClick()
            }
        }
        collapseExpandBtn = collapseBtn
        bar.addView(
            collapseBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
        )

        // 2. Pillule Centrale : Point de statut (7dp) + Titre de statut
        val statusPill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(Color.parseColor("#14FFFFFF"))
                setStroke(max(1, dp(ctx, 1)), Color.parseColor("#22FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                toggleQuickSettingsPanel()
            }
        }

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 7), dp(ctx, 7)).apply {
                marginEnd = dp(ctx, 6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#34C759"))
            }
        }
        statusDotView = dot
        statusPill.addView(dot)

        val statusTv = TextView(ctx).apply {
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        statusTitleTv = statusTv
        statusPill.addView(statusTv)

        bar.addView(
            statusPill,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(ctx, 6)
            }
        )

        // 3. Pillule Langue (🇫🇷 FR, 🇬🇧 EN, 🌐 Auto — tap = cycle, long-press = réglages)
        val langTv = TextView(ctx).apply {
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#18FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                cycleQuickLanguage()
            }
            setOnLongClickListener {
                performLightHaptic()
                toggleQuickSettingsPanel()
                true
            }
        }
        langPillTv = langTv
        bar.addView(
            langTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 5)
            }
        )

        // 4. Bouton Annuler dernière phrase (↩)
        val undoTv = TextView(ctx).apply {
            text = "↩"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD60A"))
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 8), dp(ctx, 5), dp(ctx, 8), dp(ctx, 5))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#24FFD60A"))
            }
            setOnClickListener {
                performLightHaptic()
                val len = engine.lastInsertedSentenceLength
                if (len > 0) {
                    handleDeleteLastSentence(len)
                }
            }
        }
        undoBtnTv = undoTv
        bar.addView(
            undoTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 5)
            }
        )

        // 5. Bouton Réglages Rapides (⚙)
        val gearTv = TextView(ctx).apply {
            text = "⚙"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 9), dp(ctx, 5), dp(ctx, 9), dp(ctx, 5))
            setOnClickListener {
                performLightHaptic()
                toggleQuickSettingsPanel()
            }
        }
        gearBtnTv = gearTv
        bar.addView(
            gearTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 5)
            }
        )

        // 6. Bouton ✕ (affiché en mode réduit pour fermer complètement le clavier)
        val closeTv = TextView(ctx).apply {
            text = "✕"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#98989F"))
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 7), dp(ctx, 5), dp(ctx, 7), dp(ctx, 5))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#18FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                isCollapsedMode = false
                isQuickSelectorOpen = false
                engine.isKeyboardCollapsed = false
                applyBarAndKeyboardLayoutState()
                refreshBarAndPanelContent()
                currentIme?.requestHideSelf(0)
            }
        }
        closeCollapsedBtnTv = closeTv
        bar.addView(
            closeTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
        )

        // 7. L'Orbe 3D animé 60fps (42dp x 42dp)
        val orbContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42))
        }
        val orb = GeminiOrbCanvasView(ctx).apply {
            setOnClickListener {
                performLightHaptic()
                engine.toggleRecording()
            }
            setOnLongClickListener {
                performLightHaptic()
                toggleQuickSettingsPanel()
                true
            }
        }
        orbView = orb
        orbContainer.addView(
            orb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        bar.addView(orbContainer)

        return bar
    }

    // MARK: - Construction du Panneau de Réglages Intégré (panelSlot = 280dp, #FA101116)

    private fun buildInlineQuickSettingsPanel(ctx: Context): ScrollView {
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#FA101116"))
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 14))
        }

        // 1. Header row avec titre + "⌨ Clavier Gboard" + "🔧 Options Gboard"
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 10)
            }
        }

        val headerTitle = TextView(ctx).apply {
            text = "⚡ Réglages Rapides WhisperOrb"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(ctx, 6)
            }
        }
        headerRow.addView(headerTitle)

        val returnToKeysBtn = TextView(ctx).apply {
            text = "⌨ Clavier Gboard"
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(ctx, 9), dp(ctx, 5), dp(ctx, 9), dp(ctx, 5))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 10).toFloat()
                setColor(Color.parseColor("#0088FF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = false
                isCollapsedMode = false
                engine.isKeyboardCollapsed = false
                applyBarAndKeyboardLayoutState()
                refreshBarAndPanelContent()
            }
        }
        headerRow.addView(returnToKeysBtn)

        val gboardSettingsBtn = TextView(ctx).apply {
            text = "🔧 Options Gboard"
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#66B8FF"))
            setPadding(dp(ctx, 9), dp(ctx, 5), dp(ctx, 9), dp(ctx, 5))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 10).toFloat()
                setColor(Color.parseColor("#220088FF"))
                setStroke(max(1, dp(ctx, 1)), Color.parseColor("#440088FF"))
            }
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = false
                applyBarAndKeyboardLayoutState()
                refreshBarAndPanelContent()
                try {
                    val intent = Intent().apply {
                        setClassName(appContext, "helium314.keyboard.settings.SettingsActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
        headerRow.addView(gboardSettingsBtn)
        content.addView(headerRow)

        // 2. Langue de dictée
        content.addView(makeSectionLabel(ctx, "🌍 LANGUE DE DICTÉE"))
        val langScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 10)
            }
        }
        val lRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickLangRow = lRow
        langScroll.addView(lRow)
        content.addView(langScroll)

        // 3. Options rapides
        content.addView(makeSectionLabel(ctx, "⚡ OPTIONS RAPIDES"))
        val optionsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 10)
            }
        }
        val optRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickOptionsRow = optRow
        optionsScroll.addView(optRow)
        content.addView(optionsScroll)

        // 4. Modèle IA Whisper (100% Hors-Ligne)
        content.addView(makeSectionLabel(ctx, "🧠 MODÈLE IA WHISPER (100% HORS-LIGNE)"))
        val modelScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 10)
            }
        }
        val mRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickModelRow = mRow
        modelScroll.addView(mRow)
        content.addView(modelScroll)

        // 5. Couleur de l'Orbe (32 thèmes)
        content.addView(makeSectionLabel(ctx, "🎨 COULEUR DE L'ORBE (32 THÈMES)"))
        val colorScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 4)
            }
        }
        val cRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickColorRow = cRow
        colorScroll.addView(cRow)
        content.addView(colorScroll)

        scroll.addView(content)
        return scroll
    }

    private fun makeSectionLabel(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 5)
            }
        }
    }

    // MARK: - Gestion des 3 États (Normal / Réduit 58dp / Panneau 280dp)

    private fun handleCollapseOrExpandClick() {
        if (isQuickSelectorOpen) {
            isQuickSelectorOpen = false
            isCollapsedMode = false
            engine.isKeyboardCollapsed = false
        } else if (isCollapsedMode) {
            isCollapsedMode = false
            engine.isKeyboardCollapsed = false
        } else {
            isCollapsedMode = true
            isQuickSelectorOpen = false
            engine.isKeyboardCollapsed = true
        }
        applyBarAndKeyboardLayoutState()
        refreshBarAndPanelContent()
    }

    private fun toggleQuickSettingsPanel() {
        isQuickSelectorOpen = !isQuickSelectorOpen
        applyBarAndKeyboardLayoutState()
        refreshBarAndPanelContent()
    }

    private fun cycleQuickLanguage() {
        val cycleOrder = listOf(
            WhisperLanguageSelection.AUTO,
            WhisperLanguageSelection.FR,
            WhisperLanguageSelection.EN,
            WhisperLanguageSelection.SYSTEM,
            WhisperLanguageSelection.ES,
            WhisperLanguageSelection.DE
        )
        val currentIdx = cycleOrder.indexOf(engine.selectedLanguage)
        val next = if (currentIdx in cycleOrder.indices) {
            cycleOrder[(currentIdx + 1) % cycleOrder.size]
        } else {
            WhisperLanguageSelection.FR
        }
        engine.selectedLanguage = next
        refreshBarAndPanelContent()
    }

    private fun applyBarAndKeyboardLayoutState() {
        val headerSlot = headerSlotRef ?: return
        val panelSlot = panelSlotRef ?: return
        val stripContainer = stripContainerRef ?: return
        val keyboardWrapper = keyboardWrapperRef ?: return

        if (!engine.isGboardBarEnabled) {
            headerSlot.visibility = View.GONE
            panelSlot.visibility = View.GONE
            stripContainer.visibility = View.VISIBLE
            keyboardWrapper.visibility = View.VISIBLE
            requestImeRelayout()
            return
        }

        if (isNumericFieldFocused) {
            headerSlot.visibility = View.VISIBLE
            panelSlot.visibility = View.GONE
            stripContainer.visibility = View.VISIBLE
            keyboardWrapper.visibility = View.VISIBLE
            requestImeRelayout()
            return
        }

        headerSlot.visibility = View.VISIBLE

        when {
            isQuickSelectorOpen -> {
                // État 3 : Panneau de réglages intégré dans panelSlot (280dp)
                stripContainer.visibility = View.GONE
                keyboardWrapper.visibility = View.GONE
                panelSlot.visibility = View.VISIBLE
                val lp = panelSlot.layoutParams
                val targetH = dp(panelSlot.context, 280)
                if (lp != null && lp.height != targetH) {
                    lp.height = targetH
                    panelSlot.layoutParams = lp
                }
            }
            isCollapsedMode -> {
                // État 2 : Mode Réduit (uniquement headerSlot visible à 58dp en bas de l'écran)
                stripContainer.visibility = View.GONE
                keyboardWrapper.visibility = View.GONE
                panelSlot.visibility = View.GONE
            }
            else -> {
                // État 1 : Clavier Gboard complet sous le Bandeau WhisperOrb
                stripContainer.visibility = View.VISIBLE
                keyboardWrapper.visibility = View.VISIBLE
                panelSlot.visibility = View.GONE
            }
        }

        requestImeRelayout()
    }

    private fun requestImeRelayout() {
        headerSlotRef?.requestLayout()
        panelSlotRef?.requestLayout()
        inputViewRef?.requestLayout()
        currentIme?.window?.window?.decorView?.requestLayout()
    }

    // MARK: - Rafraîchissement Visuel du Bandeau & du Panneau

    private fun refreshBarAndPanelContent() {
        val ctx = headerSlotRef?.context ?: appContext
        val state = engine.orbState

        orbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            audioLevel = engine.currentAudioLevel
            orbState = state
        }

        collapseExpandBtn?.apply {
            when {
                isQuickSelectorOpen -> {
                    text = "▴ Clavier"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(Color.parseColor("#0088FF"))
                    }
                }
                isCollapsedMode -> {
                    text = "▴ Clavier"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(Color.parseColor("#0088FF"))
                    }
                }
                else -> {
                    text = "▾ Réduire"
                    setTextColor(Color.parseColor("#EBEBF5"))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(Color.parseColor("#24FFFFFF"))
                    }
                }
            }
        }

        val activeDownloadEntry = downloader.downloadProgress.entries.firstOrNull {
            downloader.isDownloading(it.key)
        }

        val dotColor = when {
            activeDownloadEntry != null -> Color.parseColor("#0088FF")
            state == WhisperOrbState.LISTENING -> Color.parseColor("#FF3B30")
            state == WhisperOrbState.TRANSCRIBING -> Color.parseColor("#32ADE6")
            else -> Color.parseColor("#34C759")
        }
        statusDotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }

        statusTitleTv?.text = when {
            activeDownloadEntry != null -> {
                val pct = (activeDownloadEntry.value * 100f).roundToInt()
                "Téléchargement $pct%"
            }
            engine.isLoadingModel -> "Chargement ${engine.selectedModel.shortBadgeName}..."
            state == WhisperOrbState.LISTENING -> "Écoute..."
            state == WhisperOrbState.TRANSCRIBING -> "Transcription..."
            else -> "WhisperOrb • ${engine.selectedModel.shortBadgeName}"
        }

        langPillTv?.text = when (engine.selectedLanguage) {
            WhisperLanguageSelection.AUTO -> "🌐 Auto"
            else -> engine.selectedLanguage.shortPillLabel
        }

        undoBtnTv?.visibility = if (engine.lastInsertedSentenceLength > 0) View.VISIBLE else View.GONE

        gearBtnTv?.apply {
            setTextColor(if (isQuickSelectorOpen) Color.WHITE else Color.parseColor("#EBEBF5"))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(if (isQuickSelectorOpen) Color.parseColor("#0088FF") else Color.parseColor("#18FFFFFF"))
            }
        }

        closeCollapsedBtnTv?.visibility = if (isCollapsedMode && !isQuickSelectorOpen) View.VISIBLE else View.GONE

        if (isQuickSelectorOpen) {
            populateQuickSelectorRows(panelSlotRef?.context ?: appContext)
        }
    }

    private fun populateQuickSelectorRows(ctx: Context) {
        // 1. Langue de dictée
        val lRow = quickLangRow
        if (lRow != null) {
            lRow.removeAllViews()
            val languages = listOf(
                WhisperLanguageSelection.AUTO to "🌐 Auto",
                WhisperLanguageSelection.FR to "🇫🇷 Français",
                WhisperLanguageSelection.EN to "🇬🇧 English",
                WhisperLanguageSelection.ES to "🇪🇸 Español",
                WhisperLanguageSelection.DE to "🇩🇪 Deutsch",
                WhisperLanguageSelection.IT to "🇮🇹 Italiano",
                WhisperLanguageSelection.PT to "🇵🇹 Português",
                WhisperLanguageSelection.JA to "🇯🇵 日本語",
                WhisperLanguageSelection.ZH to "🇨🇳 中文",
                WhisperLanguageSelection.AR to "🇸🇦 العربية",
                WhisperLanguageSelection.SYSTEM to "📱 Système (${WhisperLanguageSelection.detectedSystemISOCode.uppercase()})",
                WhisperLanguageSelection.NL to "🇳🇱 Nederlands",
                WhisperLanguageSelection.PL to "🇵🇱 Polski",
                WhisperLanguageSelection.KO to "🇰🇷 한국어",
                WhisperLanguageSelection.RU to "🇷🇺 Русский"
            )
            for ((lang, label) in languages) {
                val isSelected = (engine.selectedLanguage == lang)
                val pill = TextView(ctx).apply {
                    text = label
                    textSize = 11.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(if (isSelected) Color.parseColor("#0088FF") else Color.parseColor("#1E1E24"))
                        setStroke(
                            max(1, dp(ctx, 1)),
                            if (isSelected) Color.WHITE else Color.parseColor("#2AFFFFFF")
                        )
                    }
                    setOnClickListener {
                        performLightHaptic()
                        engine.selectedLanguage = lang
                        refreshBarAndPanelContent()
                    }
                }
                lRow.addView(
                    pill,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(ctx, 6)
                    }
                )
            }
        }

        // 2. Options rapides (Envoi auto, Ponctuation & Style, Silence VAD)
        val optRow = quickOptionsRow
        if (optRow != null) {
            optRow.removeAllViews()

            val autoSendOn = autoSendAfterDictation
            optRow.addView(
                makeQuickTogglePill(
                    ctx = ctx,
                    label = if (autoSendOn) "↵ Envoi auto (Entrée) : OUI" else "↵ Envoi auto (Entrée)",
                    isActive = autoSendOn
                ) {
                    autoSendAfterDictation = !autoSendOn
                    refreshBarAndPanelContent()
                }
            )

            val smartFormatOn = engine.autoPunctuation
            optRow.addView(
                makeQuickTogglePill(
                    ctx = ctx,
                    label = if (smartFormatOn) "✨ Ponctuation & Style : OUI" else "✨ Ponctuation & Style",
                    isActive = smartFormatOn
                ) {
                    val next = !smartFormatOn
                    engine.autoPunctuation = next
                    engine.isSmartAutocorrectEnabled = next
                    refreshBarAndPanelContent()
                }
            )

            val vadPresets = listOf(0.8f, 1.2f, 1.8f, 2.5f)
            val currentVad = engine.vadSilenceThresholdSeconds
            val vadLabel = String.format(Locale.US, "⏱ Pause VAD : %.1fs", currentVad)
            optRow.addView(
                makeQuickTogglePill(
                    ctx = ctx,
                    label = vadLabel,
                    isActive = true
                ) {
                    val idx = vadPresets.indexOfFirst { kotlin.math.abs(it - currentVad) < 0.15f }
                    val nextVad = if (idx in vadPresets.indices) {
                        vadPresets[(idx + 1) % vadPresets.size]
                    } else {
                        1.2f
                    }
                    engine.vadSilenceThresholdSeconds = nextVad
                    refreshBarAndPanelContent()
                }
            )
        }

        // 3. Modèles IA Whisper (100% Hors-Ligne)
        val mRow = quickModelRow
        if (mRow != null) {
            mRow.removeAllViews()
            val unlocked = HardwareDetector.unlockedTiers(
                appContext,
                engine.simulateModest4GBDevice,
                engine.forceUnlockAllModels
            )
            for (tier in unlocked) {
                val isSelected = (engine.selectedModel == tier)
                val isDownloaded = downloader.isModelDownloaded(tier)
                val isDownloading = downloader.isDownloading(tier)
                val pct = ((downloader.downloadProgress[tier] ?: 0f) * 100f).roundToInt()

                val subLabel = when {
                    isDownloading -> "⏳ Téléchargement $pct%"
                    isDownloaded && isSelected -> "✓ Actif • ${tier.latencyDescription}"
                    isDownloaded -> "Choisir (${tier.sizeMB} Mo)"
                    else -> "⬇ Télécharger (${tier.sizeMB} Mo)"
                }

                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(if (isSelected) Color.parseColor("#260088FF") else Color.parseColor("#1E1E24"))
                        setStroke(
                            max(1, dp(ctx, 1)),
                            if (isSelected) Color.parseColor("#0088FF") else Color.parseColor("#2AFFFFFF")
                        )
                    }
                    setOnClickListener {
                        performLightHaptic()
                        if (isDownloaded) {
                            engine.selectedModel = tier
                            engine.loadModel(tier)
                        } else if (!isDownloading) {
                            downloader.downloadModel(tier) { finishedTier ->
                                engine.selectedModel = finishedTier
                                engine.loadModel(finishedTier)
                            }
                        }
                        refreshBarAndPanelContent()
                    }
                }

                val titleTv = TextView(ctx).apply {
                    text = tier.displayName
                    textSize = 11.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
                val subTv = TextView(ctx).apply {
                    text = subLabel
                    textSize = 10f
                    setTextColor(
                        when {
                            isDownloading -> Color.parseColor("#FFD60A")
                            isSelected && isDownloaded -> Color.parseColor("#66B8FF")
                            isDownloaded -> Color.parseColor("#34C759")
                            else -> Color.parseColor("#98989F")
                        }
                    )
                }
                card.addView(titleTv)
                card.addView(subTv)

                mRow.addView(
                    card,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(ctx, 8)
                    }
                )
            }
        }

        // 4. Couleur de l'Orbe (32 thèmes avec cercles lumineux)
        val cRow = quickColorRow
        if (cRow != null) {
            cRow.removeAllViews()
            val isEclipse = ThemeColors.isBlackOnBlack(engine.bgHex, engine.fgHex)
            val isPearl = ThemeColors.isWhiteOnWhite(engine.bgHex, engine.fgHex)

            val themes32 = build32OrbThemes()
            for (theme in themes32) {
                val isSelected = when {
                    theme.bgHex == "#050507" && theme.fgHex == "#050507" -> isEclipse
                    theme.bgHex == "#F8F9FA" && theme.fgHex == "#FFFFFF" -> isPearl
                    else -> !isEclipse && !isPearl && engine.fgHex.equals(theme.fgHex, ignoreCase = true)
                }
                cRow.addView(
                    makeQuickColorChip(
                        ctx = ctx,
                        label = theme.label,
                        swatchHex = theme.swatchHex,
                        isSelected = isSelected
                    ) {
                        if (theme.bgHex != null) {
                            engine.bgHex = theme.bgHex
                            engine.fgHex = theme.fgHex
                        } else {
                            if (ThemeColors.isWhiteOnWhite(engine.bgHex, engine.fgHex) ||
                                ThemeColors.isBlackOnBlack(engine.bgHex, engine.fgHex)
                            ) {
                                engine.bgHex = "#08080C"
                            }
                            engine.fgHex = theme.fgHex
                        }
                        refreshBarAndPanelContent()
                    }
                )
            }
        }
    }

    private fun build32OrbThemes(): List<OrbThemeOption> {
        val list = ArrayList<OrbThemeOption>(32)
        list.add(OrbThemeOption("🌑 Éclipse", "#050507", "#050507", "#050507"))
        list.add(OrbThemeOption("⚪ Perle", "#F8F9FA", "#F8F9FA", "#FFFFFF"))
        for (preset in ThemeColors.orderedForegrounds) {
            list.add(OrbThemeOption(preset.name, preset.hex, null, preset.hex))
        }
        val extraPresets = listOf(
            "Saphir" to "#0F52BA",
            "Azur" to "#007FFF",
            "Turquoise" to "#40E0D0",
            "Aigue-marine" to "#7FFFD4",
            "Glacier" to "#74B9FF",
            "Améthyste" to "#9966CC",
            "Magenta" to "#FF00FF",
            "Fuchsia" to "#FF00AA",
            "Framboise" to "#E30B5C",
            "Rubis" to "#E0115F",
            "Écarlate" to "#FF2400",
            "Ambre" to "#FFBF00",
            "Jade" to "#00A86B",
            "Argent" to "#C0C0C0"
        )
        for ((name, hex) in extraPresets) {
            list.add(OrbThemeOption(name, hex, null, hex))
        }
        return list
    }

    private fun makeQuickTogglePill(
        ctx: Context,
        label: String,
        isActive: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(ctx).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(if (isActive) Color.parseColor("#280088FF") else Color.parseColor("#1E1E24"))
                setStroke(
                    max(1, dp(ctx, 1)),
                    if (isActive) Color.parseColor("#0088FF") else Color.parseColor("#2AFFFFFF")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
            setOnClickListener {
                performLightHaptic()
                onClick()
            }
        }
    }

    private fun makeQuickColorChip(
        ctx: Context,
        label: String,
        swatchHex: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(if (isSelected) Color.parseColor("#280088FF") else Color.parseColor("#1E1E24"))
                setStroke(
                    max(1, dp(ctx, 1)),
                    if (isSelected) Color.parseColor("#0088FF") else Color.parseColor("#2AFFFFFF")
                )
            }
            setOnClickListener {
                performLightHaptic()
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(ctx, 6)
            }
        }

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 14), dp(ctx, 14)).apply {
                marginEnd = dp(ctx, 6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    try {
                        Color.parseColor(swatchHex)
                    } catch (_: Exception) {
                        Color.parseColor("#0088FF")
                    }
                )
                setStroke(max(1, dp(ctx, 1)), Color.parseColor("#66FFFFFF"))
            }
        }
        chip.addView(dot)

        val tv = TextView(ctx).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        chip.addView(tv)
        return chip
    }

    // MARK: - Injection Directe de Texte via InputConnection

    private fun handleTranscribedText(text: String) {
        val ime = currentIme
        val ic = ime?.currentInputConnection
        if (ime != null && ic != null) {
            try {
                val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                val firstChar = text.firstOrNull()
                val isPunctuationStart = firstChar != null && (
                    firstChar == '.' || firstChar == ',' || firstChar == '!' ||
                        firstChar == '?' || firstChar == ';' || firstChar == ':' ||
                        firstChar == '\n'
                    )
                val needsLeadingSpace = before.isNotEmpty() &&
                    !before.last().isWhitespace() &&
                    !text.startsWith(" ") &&
                    !isPunctuationStart

                val finalText = if (needsLeadingSpace) " $text" else text
                ic.commitText(finalText, 1)
                engine.lastInsertedSentenceLength = finalText.length
                refreshBarAndPanelContent()

                if (autoSendAfterDictation) {
                    val info = currentEditorInfo ?: ime.currentInputEditorInfo
                    val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                    if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.performEditorAction(action)
                    } else {
                        ime.sendDefaultEditorAction(true)
                    }
                }
                return
            } catch (_: Exception) {
            }
        }

        fallbackTranscribeCallback?.invoke(text)
    }

    private fun handleDeleteLastSentence(len: Int) {
        if (len <= 0) return
        val ic = currentIme?.currentInputConnection
        if (ic != null) {
            try {
                ic.deleteSurroundingText(len, 0)
                engine.lastInsertedSentenceLength = 0
                refreshBarAndPanelContent()
                return
            } catch (_: Exception) {
            }
        }
        fallbackDeleteCallback?.invoke(len)
        engine.lastInsertedSentenceLength = 0
        refreshBarAndPanelContent()
    }

    // MARK: - Utilitaires

    private fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).roundToInt()
    }

    private fun performLightHaptic() {
        try {
            val vib = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(14L)
            }
        } catch (_: Exception) {
        }
    }
}
