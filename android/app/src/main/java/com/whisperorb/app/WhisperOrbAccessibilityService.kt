package com.whisperorb.app

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class WhisperOrbAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var statusPillText: TextView? = null
    private var orbView: GeminiOrbCanvasView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAdded: Boolean = false

    private lateinit var engine: WhisperAndroidEngine
    private val engineListener: () -> Unit = {
        updateOverlayVisuals()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        engine = WhisperAndroidEngine.getInstance(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createOverlayView()

        engine.addListener(engineListener)
        engine.onTranscribedTextForKeyboard = { dictatedText ->
            insertTextIntoFocusedField(dictatedText)
        }
        engine.onDeleteLastSentenceRequested = { length ->
            deleteLastCharactersFromFocusedField(length)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        if (::engine.isInitialized) {
            engine.removeListener(engineListener)
        }
        removeOverlayView()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        evaluateKeyboardAndInputType()
    }

    // MARK: - Détection du Clavier Android par Défaut & Protection Stricte des Claviers de Chiffres

    private fun evaluateKeyboardAndInputType() {
        val wm = windowManager ?: return
        val interactiveWindows = try {
            windows
        } catch (_: Exception) {
            null
        }

        // 1. Vérifie si le clavier Android par défaut (Gboard / Samsung / SwiftKey) est affiché à l'écran
        var imeBounds: Rect? = null
        if (interactiveWindows != null) {
            for (win in interactiveWindows) {
                if (win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val r = Rect()
                    win.getBoundsInScreen(r)
                    if (r.height() > dp(110)) {
                        imeBounds = r
                        break
                    }
                }
            }
        }

        val focusedNode = findFocusedEditableNode()

        // 2. PROTECTION DU CLAVIER DE CHIFFRES :
        // Si l'utilisateur est sur un clavier numérique, téléphone, code PIN ou date/heure, ON N'Y TOUCHE PAS !
        if (focusedNode != null) {
            val rawInputType = focusedNode.inputType
            val inputClass = rawInputType and InputType.TYPE_MASK_CLASS
            val isNumericOrPhoneKeyboard = (
                inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            )

            if (isNumericOrPhoneKeyboard) {
                hideOverlay()
                return
            }
        }

        // 3. Affiche l'Orbe uniquement quand le clavier normal est ouvert sur un champ de texte
        if (imeBounds != null && focusedNode != null) {
            showAndDockOverlayAboveKeyboard(imeBounds)
        } else if (imeBounds == null && engine.orbState == WhisperOrbState.IDLE) {
            hideOverlay()
        }
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val directFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (directFocus != null && directFocus.isEditable) {
            return directFocus
        }
        return searchEditableFocusedRecursive(root, 0)
    }

    private fun searchEditableFocusedRecursive(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 12) return null
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchEditableFocusedRecursive(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    // MARK: - Création & Positionnement de l'Orbe 3D sur la Barre Supérieure du Clavier Android

    private fun createOverlayView() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#E6121318"))
                setStroke(dp(1), Color.parseColor("#40FFFFFF"))
            }
        }

        val pill = TextView(this).apply {
            text = "${engine.selectedModel.shortBadgeName} • ${engine.selectedLanguage.shortPillLabel}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                vibrateLight()
                cycleQuickLanguage()
            }
        }

        val undoBtn = TextView(this).apply {
            text = "↶"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(6), dp(2), dp(8), dp(2))
            setOnClickListener {
                vibrateLight()
                if (engine.lastInsertedSentenceLength > 0) {
                    deleteLastCharactersFromFocusedField(engine.lastInsertedSentenceLength)
                    engine.lastInsertedSentenceLength = 0
                } else {
                    deleteLastCharactersFromFocusedField(1)
                }
            }
        }

        val orb = GeminiOrbCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
        }

        // Glisser-déposer + Tap (Dicter) + Appui long (Changer de langue) sur l'Orbe
        var downRawX = 0f
        var downRawY = 0f
        var startParamX = 0
        var startParamY = 0
        var moved = false

        orb.setOnTouchListener { _, ev ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startParamX = lp.x
                    startParamY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                        moved = true
                        lp.x = (startParamX - dx.toInt()).coerceAtLeast(dp(6))
                        lp.y = (startParamY + dy.toInt()).coerceAtLeast(dp(40))
                        try {
                            windowManager?.updateViewLayout(overlayContainer, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        vibrateLight()
                        engine.toggleRecording()
                    }
                    true
                }
                else -> false
            }
        }

        container.addView(pill)
        container.addView(undoBtn)
        container.addView(orb)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(420)
        }

        overlayContainer = container
        statusPillText = pill
        orbView = orb
        layoutParams = params
    }

    private fun showAndDockOverlayAboveKeyboard(imeBounds: Rect) {
        val wm = windowManager ?: return
        val container = overlayContainer ?: return
        val lp = layoutParams ?: return

        // Positionne l'Orbe exactement sur la barre supérieure du clavier Android par défaut
        val targetY = (imeBounds.top - dp(26)).coerceAtLeast(dp(60))
        if (!isOverlayAdded) {
            lp.y = targetY
            try {
                wm.addView(container, lp)
                isOverlayAdded = true
            } catch (_: Exception) {
            }
        } else if (abs(lp.y - targetY) > dp(16)) {
            lp.y = targetY
            try {
                wm.updateViewLayout(container, lp)
            } catch (_: Exception) {
            }
        }
        container.visibility = View.VISIBLE
        updateOverlayVisuals()
    }

    private fun hideOverlay() {
        overlayContainer?.visibility = View.GONE
    }

    private fun removeOverlayView() {
        val wm = windowManager ?: return
        val container = overlayContainer ?: return
        if (isOverlayAdded) {
            try {
                wm.removeView(container)
            } catch (_: Exception) {
            }
            isOverlayAdded = false
        }
    }

    private fun updateOverlayVisuals() {
        orbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
        }
        statusPillText?.text = when (engine.orbState) {
            WhisperOrbState.IDLE -> "${engine.selectedModel.shortBadgeName} • ${engine.selectedLanguage.shortPillLabel}"
            WhisperOrbState.LISTENING -> "Écoute (${engine.selectedLanguage.shortPillLabel})..."
            WhisperOrbState.TRANSCRIBING -> "Transcription..."
        }
    }

    private fun cycleQuickLanguage() {
        val quickCycle = listOf(
            WhisperLanguageSelection.SYSTEM,
            WhisperLanguageSelection.AUTO,
            WhisperLanguageSelection.FR,
            WhisperLanguageSelection.EN,
            WhisperLanguageSelection.ES,
            WhisperLanguageSelection.DE
        )
        val idx = quickCycle.indexOf(engine.selectedLanguage)
        val next = quickCycle[(idx + 1) % quickCycle.size]
        engine.selectedLanguage = next
        updateOverlayVisuals()
    }

    // MARK: - Injection Directe du Texte Transcrit au Curseur dans N'importe Quelle Application

    private fun insertTextIntoFocusedField(textToInsert: String) {
        val node = findFocusedEditableNode() ?: return

        val existingText = node.text?.toString() ?: ""
        val isShowingHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.isShowingHintText
        } else {
            false
        }

        val currentContent = if (isShowingHint) "" else existingText
        val selStart = node.textSelectionStart.takeIf { it >= 0 && it <= currentContent.length } ?: currentContent.length
        val selEnd = node.textSelectionEnd.takeIf { it >= selStart && it <= currentContent.length } ?: selStart

        val newContent = StringBuilder(currentContent)
            .replace(selStart, selEnd, textToInsert)
            .toString()

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newContent)
        }
        val setOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (setOk) {
            val newCursor = (selStart + textToInsert.length).coerceAtMost(newContent.length)
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        } else {
            // Fallback presse-papier + ACTION_PASTE pour les WebViews / éditeurs custom
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("WhisperOrb", textToInsert))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    private fun deleteLastCharactersFromFocusedField(count: Int) {
        if (count <= 0) return
        val node = findFocusedEditableNode() ?: return
        val existing = node.text?.toString() ?: return
        if (existing.isEmpty()) return

        val newLen = (existing.length - count).coerceAtLeast(0)
        val newText = existing.substring(0, newLen)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun vibrateLight() {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
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
