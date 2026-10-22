package com.whisperorb.app

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
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
import kotlin.math.abs
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
                activeInstance?.triggerBurstTracking()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private lateinit var engine: WhisperAndroidEngine
    private lateinit var downloader: ModelDownloader

    // Coordonnées écran réelles (getBoundsInScreen) de Gboard dans le référentiel physique [0..realScreenHeight]
    private var isImeWindowVisible: Boolean = false
    private var currentImeTopScreenY: Int = 0
    private var currentImeBottomScreenY: Int = 0
    private var lastKnownImeTopScreenY: Int = 0
    private var lastKnownImeBottomScreenY: Int = 0

    // États des 3 modes d'incrustation Gboard
    private var isNumericFieldFocused: Boolean = false
    private var isCollapsedMode: Boolean = false
    private var isQuickSelectorOpen: Boolean = false
    private var suppressImeReopenUntilMs: Long = 0L

    // Suivi continu pendant les animations d'ouverture/fermeture de Gboard
    private var burstFramesRemaining: Int = 0
    private var isTrackingLoopScheduled: Boolean = false

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
            updateOverlayVisibilityAndPosition()
            if (isOverlayAttached) {
                refreshOverlayContent()
                updateOverlayGeometry()
            }
        }
    }

    private val frameTrackerRunnable = object : Runnable {
        override fun run() {
            isTrackingLoopScheduled = false
            evaluateWindowsAndSyncOverlay()
            if (burstFramesRemaining > 0 || isImeWindowVisible || isCollapsedMode || isQuickSelectorOpen) {
                if (burstFramesRemaining > 0) burstFramesRemaining--
                val delayMs = if (burstFramesRemaining > 0) 28L else 120L
                isTrackingLoopScheduled = true
                mainHandler.postDelayed(this, delayMs)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        engine = WhisperAndroidEngine.getInstance(applicationContext)
        downloader = ModelDownloader.getInstance(applicationContext)

        val realScreenH = getRealScreenHeightPx()
        lastKnownImeBottomScreenY = realScreenH
        lastKnownImeTopScreenY = realScreenH - dp(310)

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
        triggerBurstTracking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                triggerBurstTracking()
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        activeInstance = null
        mainHandler.removeCallbacksAndMessages(null)
        if (::engine.isInitialized) {
            engine.removeListener(engineListener)
        }
        detachOverlayWindow()
        super.onDestroy()
    }

    private fun triggerBurstTracking() {
        // Suit l'animation fluide de Gboard pendant ~600ms (22 frames à 28ms)
        burstFramesRemaining = 22
        evaluateWindowsAndSyncOverlay()
        if (!isTrackingLoopScheduled) {
            isTrackingLoopScheduled = true
            mainHandler.postDelayed(frameTrackerRunnable, 28L)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    @Suppress("DEPRECATION")
    private fun getRealScreenHeightPx(): Int {
        val wm = windowManager ?: return resources.displayMetrics.heightPixels
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            val pt = Point()
            wm.defaultDisplay.getRealSize(pt)
            pt.y
        }
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

    // MARK: - Lecture Directe des Coordonnées Écran de Gboard (getBoundsInScreen)

    private fun evaluateWindowsAndSyncOverlay() {
        val realScreenHeight = getRealScreenHeightPx()
        val minOpenKeyboardHeightPx = dp(175)
        var foundImeVisible = false
        var imeTopY = 0
        var imeBottomY = realScreenHeight

        try {
            val winList = windows
            if (winList != null) {
                val rect = Rect()
                for (w in winList) {
                    if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        w.getBoundsInScreen(rect)
                        // Sur Android 14/15, quand Gboard est fermé, sa région tactile résiduelle fait ~48dp (barre nav).
                        // Quand Gboard est ouvert, rect.height() fait ~260dp à 380dp et rect.top est le bord supérieur exact de Gboard !
                        if (rect.height() >= minOpenKeyboardHeightPx && rect.bottom > rect.top) {
                            foundImeVisible = true
                            imeTopY = rect.top
                            imeBottomY = max(rect.bottom, realScreenHeight)
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

        if (foundImeVisible) {
            currentImeTopScreenY = imeTopY
            currentImeBottomScreenY = imeBottomY
            lastKnownImeTopScreenY = imeTopY
            lastKnownImeBottomScreenY = imeBottomY

            if (!wasImeVisible && System.currentTimeMillis() > suppressImeReopenUntilMs) {
                isCollapsedMode = false
                engine.isKeyboardCollapsed = false
            }
        } else {
            currentImeTopScreenY = 0
            currentImeBottomScreenY = realScreenHeight
        }

        updateOverlayVisibilityAndPosition()
    }

    private fun shouldShowOverlay(): Boolean {
        if (!engine.isGboardBarEnabled) return false
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
        val justAttached = !isOverlayAttached
        attachOverlayWindowIfNeeded()
        if (justAttached) {
            refreshOverlayContent()
        }
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
            isImeWindowVisible = false
            refreshOverlayContent()
            updateOverlayGeometry()
            triggerBurstTracking()
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
        triggerBurstTracking()
    }

    // MARK: - Mise à jour du contenu visuel

    private fun refreshOverlayContent() {
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

        val cRow = quickColorRow ?: return
        cRow.removeAllViews()

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

    // MARK: - Géométrie Pixel-Perfect verrouillée sur getBoundsInScreen(Gboard).top et getLocationOnScreen()

    private fun computeDesiredScreenTopAndHeight(): Pair<Int, Int> {
        val barHeight = dp(58)
        val realScreenH = getRealScreenHeightPx()
        val navBarPadding = dp(24)

        val activeGboardTopY = if (isImeWindowVisible && currentImeTopScreenY > dp(100)) {
            currentImeTopScreenY
        } else if (lastKnownImeTopScreenY > dp(100)) {
            lastKnownImeTopScreenY
        } else {
            realScreenH - dp(310)
        }

        val activeGboardBottomY = if (isImeWindowVisible && currentImeBottomScreenY > activeGboardTopY) {
            currentImeBottomScreenY
        } else {
            realScreenH
        }

        return when {
            isQuickSelectorOpen -> {
                // ÉTAT 2 : Le haut de la barre reste exactement au-dessus de Gboard (activeGboardTopY - barHeight)
                // et le panneau descend jusqu'en bas de Gboard (activeGboardBottomY), couvrant 100 % des touches !
                val topY = max(dp(48), activeGboardTopY - barHeight)
                val totalH = max(dp(260), activeGboardBottomY - topY)
                topY to totalH
            }
            isImeWindowVisible -> {
                // ÉTAT 1 : Le bord inférieur de la barre (topY + barHeight) est verrouillé au pixel près sur activeGboardTopY !
                val topY = max(dp(48), activeGboardTopY - barHeight)
                topY to barHeight
            }
            else -> {
                // ÉTAT 3 : Gboard rétracté via ▾ Réduire -> barre posée tout en bas de l'écran !
                val topY = realScreenH - navBarPadding - barHeight
                topY to barHeight
            }
        }
    }

    private fun attachOverlayWindowIfNeeded() {
        if (isOverlayAttached) return
        val wm = windowManager ?: return
        val root = rootOverlayContainer ?: return

        val (targetTopScreenY, targetHeight) = computeDesiredScreenTopAndHeight()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            targetHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = targetTopScreenY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        try {
            wm.addView(root, lp)
            overlayLayoutParams = lp
            isOverlayAttached = true
            android.util.Log.i(
                "WhisperOrbGboard",
                "Attached overlay: topY=$targetTopScreenY, h=$targetHeight, imeTop=$currentImeTopScreenY, imeBottom=$currentImeBottomScreenY"
            )
        } catch (_: Exception) {
        }
    }

    private fun updateOverlayGeometry() {
        if (!isOverlayAttached) return
        val wm = windowManager ?: return
        val root = rootOverlayContainer ?: return
        val lp = overlayLayoutParams ?: return

        val (targetTopScreenY, targetHeight) = computeDesiredScreenTopAndHeight()

        if (lp.y != targetTopScreenY || lp.height != targetHeight) {
            lp.y = targetTopScreenY
            lp.height = targetHeight
            try {
                wm.updateViewLayout(root, lp)
                android.util.Log.i(
                    "WhisperOrbGboard",
                    "Updated overlay: topY=$targetTopScreenY, h=$targetHeight, imeTop=$currentImeTopScreenY, imeBottom=$currentImeBottomScreenY"
                )
            } catch (_: Exception) {
            }
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
