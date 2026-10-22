package com.whisperorb.app

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

class WhisperOrbAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var activeInstance: WhisperOrbAccessibilityService? = null
            private set

        @Volatile
        var forcePreviewOverlay: Boolean = false

        fun isServiceEnabled(context: Context): Boolean {
            if (activeInstance != null) return true
            val expectedComponent = "${context.packageName}/${WhisperOrbAccessibilityService::class.java.canonicalName}"
            val expectedShort = "${context.packageName}/.WhisperOrbAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any {
                it.equals(expectedComponent, ignoreCase = true) || it.equals(expectedShort, ignoreCase = true)
            }
        }

        fun requestRefresh() {
            activeInstance?.mainHandler?.post {
                activeInstance?.evaluateWindowsAndSyncOverlay()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private lateinit var engine: WhisperAndroidEngine
    private lateinit var downloader: ModelDownloader

    // États des 3 modes d'incrustation Gboard
    private var isImeWindowVisible: Boolean = false
    private var currentImeHeightPx: Int = 0
    private var lastKnownGboardHeightPx: Int = 0
    private var isNumericFieldFocused: Boolean = false
    private var isCollapsedMode: Boolean = false
    private var isQuickSelectorOpen: Boolean = false
    private var suppressImeReopenUntilMs: Long = 0L

    // Référence au dernier champ de texte éditable pour ré-ouvrir Gboard via ▴ Clavier et insérer le texte
    private var lastEditableNode: AccessibilityNodeInfo? = null

    // Vues de la fenêtre d'incrustation
    private var rootOverlayContainer: LinearLayout? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached: Boolean = false

    private var collapseExpandBtn: TextView? = null
    private var statusDotView: View? = null
    private var statusTitleTv: TextView? = null
    private var langPillTv: TextView? = null
    private var undoBtnTv: TextView? = null
    private var gearBtnTv: TextView? = null
    private var closeCollapsedBtnTv: TextView? = null
    private var orbView: GeminiOrbCanvasView? = null
    private var quickSelectorContainer: ScrollView? = null
    private var quickLangRow: LinearLayout? = null
    private var quickModelRow: LinearLayout? = null
    private var quickColorRow: LinearLayout? = null

    private val engineListener: () -> Unit = {
        mainHandler.post {
            refreshOverlayContent()
            updateOverlayGeometry()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        engine = WhisperAndroidEngine.getInstance(applicationContext)
        downloader = ModelDownloader.getInstance(applicationContext)
        lastKnownGboardHeightPx = dp(286)

        engine.addListener(engineListener)
        downloader.onStateChanged = {
            mainHandler.post {
                refreshOverlayContent()
            }
        }

        engine.onTranscribedTextForKeyboard = { text ->
            mainHandler.post {
                insertTranscribedTextIntoActiveField(text)
            }
        }

        engine.onDeleteLastSentenceRequested = { length ->
            mainHandler.post {
                deleteLastCharactersFromActiveField(length)
            }
        }

        buildOverlayViewHierarchy()
        evaluateWindowsAndSyncOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                inspectFocusedNodeAndWindows()
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        activeInstance = null
        if (::engine.isInitialized) {
            engine.removeListener(engineListener)
        }
        detachOverlayWindow()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    private fun performLightHaptic() {
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(14L)
            }
        } catch (_: Exception) {
        }
    }

    // MARK: - Inspection des Fenêtres Système (Hauteur exacte de Gboard via AccessibilityWindowInfo.TYPE_INPUT_METHOD)

    private fun inspectFocusedNodeAndWindows() {
        evaluateWindowsAndSyncOverlay()
    }

    private fun evaluateWindowsAndSyncOverlay() {
        val screenHeight = resources.displayMetrics.heightPixels
        var foundImeVisible = false
        var measuredImeHeight = 0

        try {
            val winList = windows
            if (winList != null) {
                val rect = Rect()
                for (w in winList) {
                    if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        w.getBoundsInScreen(rect)
                        val h = screenHeight - rect.top
                        if (rect.height() > dp(64) && h > dp(64)) {
                            foundImeVisible = true
                            measuredImeHeight = h
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Vérification du champ focalisé (et protection automatique des claviers de chiffres / téléphone / PIN)
        try {
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                lastEditableNode = focused
                val inputType = focused.inputType
                val maskedClass = inputType and InputType.TYPE_MASK_CLASS
                isNumericFieldFocused = (
                    maskedClass == InputType.TYPE_CLASS_NUMBER ||
                        maskedClass == InputType.TYPE_CLASS_PHONE ||
                        maskedClass == InputType.TYPE_CLASS_DATETIME
                    )
            } else if (!foundImeVisible) {
                isNumericFieldFocused = false
            }
        } catch (_: Exception) {
        }

        val wasImeVisible = isImeWindowVisible
        isImeWindowVisible = foundImeVisible

        if (foundImeVisible && measuredImeHeight > dp(120)) {
            currentImeHeightPx = measuredImeHeight
            lastKnownGboardHeightPx = measuredImeHeight
            // Si Gboard vient de s'ouvrir après la période de rétractation, on sort du mode réduit
            if (!wasImeVisible && System.currentTimeMillis() > suppressImeReopenUntilMs) {
                isCollapsedMode = false
                engine.isKeyboardCollapsed = false
            }
        } else {
            currentImeHeightPx = 0
        }

        updateOverlayVisibilityAndPosition()
    }

    private fun shouldShowOverlay(): Boolean {
        if (isNumericFieldFocused) return false
        if (forcePreviewOverlay) return true
        if (isImeWindowVisible) return true
        if (isCollapsedMode) return true
        if (isQuickSelectorOpen) return true
        return false
    }

    private fun updateOverlayVisibilityAndPosition() {
        if (!shouldShowOverlay()) {
            detachOverlayWindow()
            return
        }
        attachOverlayWindowIfNeeded()
        refreshOverlayContent()
        updateOverlayGeometry()
    }

    // MARK: - Construction de la Barre Vocale Apple + Panneau de Réglages Intégré à la Zone Gboard

    private fun buildOverlayViewHierarchy() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
            }
            elevation = dp(12).toFloat()
        }

        // Filet lumineux supérieur subtil (#26FFFFFF)
        val topHairline = View(this).apply {
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, max(1, dp(1)))
        }
        root.addView(topHairline)

        // BARRE SUPÉRIEURE (58dp) — Identique au clavier Apple WhisperOrb
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(57)
            )
        }

        // 1. Bouton ▾ Réduire / ▴ Clavier / ⌨️ Gboard
        val collapseBtn = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                performLightHaptic()
                handleLeftActionButtonClick()
            }
        }
        collapseExpandBtn = collapseBtn
        topBar.addView(
            collapseBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        )

        // 2. Pillule Centrale de Statut & Modèle (cliquable -> ouvre les réglages dans Gboard)
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#14FFFFFF"))
                setStroke(max(1, dp(1)), Color.parseColor("#22FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = !isQuickSelectorOpen
                refreshOverlayContent()
                updateOverlayGeometry()
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#34C759"))
            }
        }
        statusDotView = dot
        statusPill.addView(dot)

        val statusTv = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        statusTitleTv = statusTv
        statusPill.addView(statusTv)

        topBar.addView(
            statusPill,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )

        // 3. Pillule Langue (📱 FR / 🌐 AUTO)
        val langTv = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#18FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = !isQuickSelectorOpen
                refreshOverlayContent()
                updateOverlayGeometry()
            }
        }
        langPillTv = langTv
        topBar.addView(
            langTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
        )

        // 4. Bouton Annuler dernière phrase (↶)
        val undoTv = TextView(this).apply {
            text = "↶"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD60A"))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#24FFD60A"))
            }
            setOnClickListener {
                performLightHaptic()
                if (engine.lastInsertedSentenceLength > 0) {
                    deleteLastCharactersFromActiveField(engine.lastInsertedSentenceLength)
                    engine.lastInsertedSentenceLength = 0
                    refreshOverlayContent()
                }
            }
        }
        undoBtnTv = undoTv
        topBar.addView(
            undoTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
        )

        // 5. Bouton Réglages Rapides DANS Gboard (⚙)
        val gearTv = TextView(this).apply {
            text = "⚙"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = !isQuickSelectorOpen
                refreshOverlayContent()
                updateOverlayGeometry()
            }
        }
        gearBtnTv = gearTv
        topBar.addView(
            gearTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
        )

        // 6. Bouton ✕ (affiché uniquement en mode réduit pour fermer la barre si souhaité)
        val closeTv = TextView(this).apply {
            text = "✕"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#98989F"))
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(5), dp(7), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#18FFFFFF"))
            }
            setOnClickListener {
                performLightHaptic()
                isCollapsedMode = false
                isQuickSelectorOpen = false
                forcePreviewOverlay = false
                engine.isKeyboardCollapsed = false
                updateOverlayVisibilityAndPosition()
            }
        }
        closeCollapsedBtnTv = closeTv
        topBar.addView(
            closeTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        )

        // 7. L'Orbe 3D 120 Hz incrusté à droite (appui court = dicter, appui long = réglages dans Gboard)
        val orbContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        val orb = GeminiOrbCanvasView(this).apply {
            setOnClickListener {
                performLightHaptic()
                engine.toggleRecording()
            }
            setOnLongClickListener {
                performLightHaptic()
                isQuickSelectorOpen = !isQuickSelectorOpen
                refreshOverlayContent()
                updateOverlayGeometry()
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
        topBar.addView(orbContainer)

        root.addView(topBar)

        // PANNEAU DE RÉGLAGES INTÉGRÉ DANS LA ZONE GBOARD (État 2 : remplace visuellement les touches de Gboard)
        val selectorScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#121216"))
        }

        val selectorContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
        }

        // En-tête du panneau intégré Gboard
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val headerTitle = TextView(this).apply {
            text = "⚡ RÉGLAGES WHISPERORB INTÉGRÉS À GBOARD"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#98989F"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)

        val openAppBtn = TextView(this).apply {
            text = "📱 Ouvrir Studio"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#0088FF"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#220088FF"))
            }
            setOnClickListener {
                performLightHaptic()
                isQuickSelectorOpen = false
                refreshOverlayContent()
                updateOverlayGeometry()
                val intent = Intent(this@WhisperOrbAccessibilityService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }
        headerRow.addView(openAppBtn)
        selectorContent.addView(headerRow)

        // Section 1 : Langue de dictée
        selectorContent.addView(makeSectionLabel("🌍 LANGUE DE DICTÉE"))
        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
        val lRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickLangRow = lRow
        langScroll.addView(lRow)
        selectorContent.addView(langScroll)

        // Section 2 : Modèles Whisper IA
        selectorContent.addView(makeSectionLabel("🧠 MODÈLE WHISPER IA (C++ NDK HORS-LIGNE)"))
        val modelScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
        val mRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickModelRow = mRow
        modelScroll.addView(mRow)
        selectorContent.addView(modelScroll)

        // Section 3 : Couleur de l'Orbe (Éclipse, Perle & 32 Thèmes)
        selectorContent.addView(makeSectionLabel("🎨 COULEUR DE L'ORBE (ÉCLIPSE, PERLE & 32 THÈMES)"))
        val colorScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        val cRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickColorRow = cRow
        colorScroll.addView(cRow)
        selectorContent.addView(colorScroll)

        selectorScroll.addView(selectorContent)
        quickSelectorContainer = selectorScroll

        root.addView(
            selectorScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        rootOverlayContainer = root
    }

    private fun makeSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
        }
    }

    // MARK: - Actions des 3 États (Réduire Gboard / Ré-ouvrir Gboard / Panneau Réglages dans Gboard)

    private fun handleLeftActionButtonClick() {
        if (isQuickSelectorOpen) {
            // Si le panneau de réglages couvre Gboard, on revient aux touches Gboard
            isQuickSelectorOpen = false
            if (isCollapsedMode && !isImeWindowVisible) {
                reopenGboardOnActiveField()
            } else {
                refreshOverlayContent()
                updateOverlayGeometry()
            }
            return
        }

        if (isImeWindowVisible && !isCollapsedMode) {
            // État 1 -> État 3 : Rétracte physiquement Gboard via GLOBAL_ACTION_BACK et garde la barre vocale en bas !
            isCollapsedMode = true
            isQuickSelectorOpen = false
            engine.isKeyboardCollapsed = true
            suppressImeReopenUntilMs = System.currentTimeMillis() + 650L
            performGlobalAction(GLOBAL_ACTION_BACK)
            currentImeHeightPx = 0
            isImeWindowVisible = false
            refreshOverlayContent()
            updateOverlayGeometry()
        } else {
            // État 3 -> État 1 : Ré-ouvre Gboard sous la barre vocale WhisperOrb !
            reopenGboardOnActiveField()
        }
    }

    private fun reopenGboardOnActiveField() {
        isCollapsedMode = false
        isQuickSelectorOpen = false
        engine.isKeyboardCollapsed = false
        try {
            val target = lastEditableNode ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (_: Exception) {
        }
        refreshOverlayContent()
        updateOverlayGeometry()
        mainHandler.postDelayed({ evaluateWindowsAndSyncOverlay() }, 180L)
        mainHandler.postDelayed({ evaluateWindowsAndSyncOverlay() }, 420L)
    }

    // MARK: - Mise à jour du contenu visuel et de la géométrie de la fenêtre

    private fun refreshOverlayContent() {
        val state = engine.orbState
        orbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            audioLevel = engine.currentAudioLevel
            orbState = state
        }

        // Bouton gauche selon l'état (État 1: ▾ Réduire | État 2: ⌨️ Gboard | État 3: ▴ Clavier)
        collapseExpandBtn?.apply {
            when {
                isQuickSelectorOpen -> {
                    text = "⌨️ Gboard"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#0088FF"))
                    }
                }
                isCollapsedMode || !isImeWindowVisible -> {
                    text = "▴ Clavier"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#0088FF"))
                    }
                }
                else -> {
                    text = "▾ Réduire"
                    setTextColor(Color.parseColor("#EBEBF5"))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#24FFFFFF"))
                    }
                }
            }
        }

        // Point de statut + Texte central
        val dotColor = when (state) {
            WhisperOrbState.IDLE -> Color.parseColor("#34C759")
            WhisperOrbState.LISTENING -> Color.parseColor("#FF3B30")
            WhisperOrbState.TRANSCRIBING -> Color.parseColor("#32ADE6")
        }
        statusDotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }

        statusTitleTv?.text = when (state) {
            WhisperOrbState.IDLE -> engine.selectedModel.shortBadgeName
            WhisperOrbState.LISTENING -> "Écoute (${engine.selectedLanguage.shortPillLabel})..."
            WhisperOrbState.TRANSCRIBING -> "Transcription IA..."
        }

        langPillTv?.text = engine.selectedLanguage.shortPillLabel
        undoBtnTv?.visibility = if (engine.lastInsertedSentenceLength > 0) View.VISIBLE else View.GONE

        gearBtnTv?.apply {
            setTextColor(if (isQuickSelectorOpen) Color.WHITE else Color.parseColor("#EBEBF5"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (isQuickSelectorOpen) Color.parseColor("#0088FF") else Color.parseColor("#18FFFFFF"))
            }
        }

        closeCollapsedBtnTv?.visibility = if (!isImeWindowVisible && !isQuickSelectorOpen) View.VISIBLE else View.GONE

        if (isQuickSelectorOpen) {
            quickSelectorContainer?.visibility = View.VISIBLE
            populateQuickSelectorRows()
        } else {
            quickSelectorContainer?.visibility = View.GONE
        }
    }

    private fun populateQuickSelectorRows() {
        // 1. Langues
        val lRow = quickLangRow ?: return
        lRow.removeAllViews()
        for (lang in WhisperLanguageSelection.entries) {
            val isSelected = (engine.selectedLanguage == lang)
            val pill = TextView(this).apply {
                text = lang.shortPillLabel
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isSelected) Color.parseColor("#0088FF") else Color.parseColor("#1E1E24"))
                    setStroke(max(1, dp(1)), if (isSelected) Color.WHITE else Color.parseColor("#2AFFFFFF"))
                }
                setOnClickListener {
                    performLightHaptic()
                    engine.selectedLanguage = lang
                    refreshOverlayContent()
                }
            }
            lRow.addView(
                pill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(6)
                }
            )
        }

        // 2. Modèles Whisper IA
        val mRow = quickModelRow ?: return
        mRow.removeAllViews()
        val unlocked = HardwareDetector.unlockedTiers(
            this,
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
                isDownloaded -> "✓ Prêt (${tier.sizeMB} Mo)"
                else -> "⬇ Télécharger (${tier.sizeMB} Mo)"
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isSelected) Color.parseColor("#260088FF") else Color.parseColor("#1E1E24"))
                    setStroke(
                        max(1, dp(1)),
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
                    refreshOverlayContent()
                }
            }

            val titleTv = TextView(this).apply {
                text = tier.displayName
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val subTv = TextView(this).apply {
                text = subLabel
                textSize = 10.5f
                setTextColor(
                    when {
                        isSelected -> Color.parseColor("#66B8FF")
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
                    marginEnd = dp(8)
                }
            )
        }

        // 3. Couleurs de l'Orbe (Éclipse, Perle + 16 Foregrounds + 5 Backgrounds)
        val cRow = quickColorRow ?: return
        cRow.removeAllViews()

        // Bouton spécial Éclipse (Noir sur Noir)
        val isEclipse = ThemeColors.isBlackOnBlack(engine.bgHex, engine.fgHex)
        cRow.addView(
            makeQuickColorChip(
                label = "🌑 Éclipse",
                swatchHex = "#050507",
                isSelected = isEclipse
            ) {
                engine.bgHex = "#050507"
                engine.fgHex = "#050507"
                refreshOverlayContent()
            }
        )

        // Bouton spécial Perle (Blanc sur Blanc)
        val isPearl = ThemeColors.isWhiteOnWhite(engine.bgHex, engine.fgHex)
        cRow.addView(
            makeQuickColorChip(
                label = "⚪ Perle",
                swatchHex = "#F8F9FA",
                isSelected = isPearl
            ) {
                engine.bgHex = "#F8F9FA"
                engine.fgHex = "#FFFFFF"
                refreshOverlayContent()
            }
        )

        for (preset in ThemeColors.orderedForegrounds) {
            val selected = engine.fgHex.equals(preset.hex, ignoreCase = true) && !isEclipse && !isPearl
            cRow.addView(
                makeQuickColorChip(
                    label = preset.name,
                    swatchHex = preset.hex,
                    isSelected = selected
                ) {
                    if (ThemeColors.isWhiteOnWhite(engine.bgHex, engine.fgHex) ||
                        ThemeColors.isBlackOnBlack(engine.bgHex, engine.fgHex)
                    ) {
                        engine.bgHex = "#08080C"
                    }
                    engine.fgHex = preset.hex
                    refreshOverlayContent()
                }
            )
        }
    }

    private fun makeQuickColorChip(
        label: String,
        swatchHex: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (isSelected) Color.parseColor("#280088FF") else Color.parseColor("#1E1E24"))
                setStroke(
                    max(1, dp(1)),
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
                marginEnd = dp(6)
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                marginEnd = dp(6)
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
                setStroke(max(1, dp(1)), Color.parseColor("#55FFFFFF"))
            }
        }
        chip.addView(dot)

        val tv = TextView(this).apply {
            text = label
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        chip.addView(tv)
        return chip
    }

    private fun attachOverlayWindowIfNeeded() {
        if (isOverlayAttached) return
        val wm = windowManager ?: return
        val root = rootOverlayContainer ?: return

        val barHeight = dp(58)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = if (isImeWindowVisible) currentImeHeightPx else 0
        }

        try {
            wm.addView(root, lp)
            overlayLayoutParams = lp
            isOverlayAttached = true
        } catch (_: Exception) {
        }
    }

    private fun updateOverlayGeometry() {
        if (!isOverlayAttached) return
        val wm = windowManager ?: return
        val root = rootOverlayContainer ?: return
        val lp = overlayLayoutParams ?: return

        val barHeight = dp(58)
        val effectiveGboardHeight = if (currentImeHeightPx > dp(120)) {
            currentImeHeightPx
        } else {
            max(dp(260), lastKnownGboardHeightPx)
        }

        if (isQuickSelectorOpen) {
            // ÉTAT 2 : Le panneau WhisperOrb se déploie DANS la zone des touches de Gboard (y = 0, hauteur = Gboard + barre)
            lp.y = 0
            lp.height = effectiveGboardHeight + barHeight
        } else if (isImeWindowVisible) {
            // ÉTAT 1 : Bandeau supérieur collé juste au-dessus de Gboard (y = hauteur exacte de la fenêtre IME)
            lp.y = currentImeHeightPx
            lp.height = barHeight
        } else {
            // ÉTAT 3 : Clavier Gboard rétracté via ▾ Réduire -> barre compacte posée tout en bas de l'écran (y = 0)
            lp.y = 0
            lp.height = barHeight
        }

        try {
            wm.updateViewLayout(root, lp)
        } catch (_: Exception) {
        }
    }

    private fun detachOverlayWindow() {
        if (!isOverlayAttached) return
        val wm = windowManager ?: return
        val root = rootOverlayContainer ?: return
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        isOverlayAttached = false
    }

    // MARK: - Insertion Directe du Texte Transcrit dans le Champ Actif de Gboard

    private fun findTargetEditableNode(): AccessibilityNodeInfo? {
        try {
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                lastEditableNode = focused
                return focused
            }
        } catch (_: Exception) {
        }
        return lastEditableNode
    }

    private fun insertTranscribedTextIntoActiveField(textToInsert: String) {
        val node = findTargetEditableNode() ?: return
        try {
            node.refresh()
            val existingCharSeq = node.text
            val isShowingHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.isShowingHintText
            } else {
                false
            }
            val currentText = if (isShowingHint || existingCharSeq == null) "" else existingCharSeq.toString()

            val selStart = node.textSelectionStart.takeIf { it >= 0 && it <= currentText.length } ?: currentText.length
            val selEnd = node.textSelectionEnd.takeIf { it >= selStart && it <= currentText.length } ?: selStart

            val newText = StringBuilder(currentText)
                .replace(selStart, selEnd, textToInsert)
                .toString()

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                val newCursor = (selStart + textToInsert.length).coerceAtMost(newText.length)
                val selArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                return
            }

            // Fallback universel par presse-papier + ACTION_PASTE (ex: WebViews / Chrome / Docs)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("WhisperOrb", textToInsert))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) {
        }
    }

    private fun deleteLastCharactersFromActiveField(charCount: Int) {
        if (charCount <= 0) return
        val node = findTargetEditableNode() ?: return
        try {
            node.refresh()
            val currentText = node.text?.toString() ?: return
            if (currentText.isEmpty()) return
            val updated = if (currentText.length >= charCount) {
                currentText.dropLast(charCount)
            } else {
                ""
            }
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    updated
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Exception) {
        }
    }
}
