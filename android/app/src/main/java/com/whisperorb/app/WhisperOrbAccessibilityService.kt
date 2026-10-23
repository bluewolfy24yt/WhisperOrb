package com.whisperorb.app

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Service d'Accessibilité optionnel dédié uniquement à la **Bulle Flottante Universelle** (56dp x 56dp)
 * et à l'insertion de secours hors-clavier.
 *
 * IMPORTANT : Le bandeau vocal WhisperOrb étant désormais soudé nativement à l'intérieur du clavier
 * intégré Gboard/HeliBoard (`LatinIME`), ce service ne dessine JAMAIS de barre pleine largeur par-dessus
 * le clavier. Il affiche uniquement la Bulle Flottante Universelle lorsque
 * `engine.isFloatingBubbleEnabled == true && isServiceEnabled(this)`.
 */
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
                activeInstance?.syncFloatingBubbleState()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private lateinit var engine: WhisperAndroidEngine

    // Dernier champ de texte éditable focalisé (utilisé par la Bulle Flottante ou en secours hors-IME)
    private var lastEditableNode: AccessibilityNodeInfo? = null

    // Vues et géométrie de la Bulle Flottante Universelle (56dp x 56dp)
    private var bubbleContainer: FrameLayout? = null
    private var bubbleOrbView: GeminiOrbCanvasView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var isBubbleAttached: Boolean = false
    private var snapAnimator: ValueAnimator? = null

    private var bubblePosX: Int = -1
    private var bubblePosY: Int = -1

    private val engineListener: () -> Unit = {
        mainHandler.post {
            syncFloatingBubbleState()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        engine = WhisperAndroidEngine.getInstance(applicationContext)

        engine.addListener(engineListener)

        buildFloatingBubbleView()
        syncFloatingBubbleState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                captureFocusedEditableNode()
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        activeInstance = null
        snapAnimator?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (::engine.isInitialized) {
            engine.removeListener(engineListener)
        }
        detachFloatingBubble()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    @Suppress("DEPRECATION")
    private fun getScreenBoundsPx(): Pair<Int, Int> {
        val wm = windowManager ?: return resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val pt = Point()
            wm.defaultDisplay.getRealSize(pt)
            pt.x to pt.y
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

    private fun captureFocusedEditableNode() {
        try {
            val root = rootInActiveWindow ?: return
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                lastEditableNode = focused
            }
        } catch (_: Exception) {
        }
    }

    // MARK: - Bulle Flottante Universelle (56dp x 56dp, déplaçable + aimantation magnétique aux bords)

    private fun shouldShowFloatingBubble(): Boolean {
        if (!::engine.isInitialized) return false
        return engine.isFloatingBubbleEnabled && isServiceEnabled(this)
    }

    private fun syncFloatingBubbleState() {
        if (!shouldShowFloatingBubble()) {
            detachFloatingBubble()
            return
        }
        attachFloatingBubbleIfNeeded()
        refreshFloatingBubbleVisuals()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildFloatingBubbleView() {
        val sizePx = dp(56)
        val innerOrbSizePx = dp(48)

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = createBubbleBackgroundDrawable(WhisperOrbState.IDLE)
            elevation = dp(10).toFloat()
        }

        val orb = GeminiOrbCanvasView(this).apply {
            isClickable = false
            isFocusable = false
        }
        bubbleOrbView = orb

        container.addView(
            orb,
            FrameLayout.LayoutParams(innerOrbSizePx, innerOrbSizePx, Gravity.CENTER)
        )

        var downRawX = 0f
        var downRawY = 0f
        var startWindowX = 0
        var startWindowY = 0
        var isDragging = false
        var longPressTriggered = false
        val touchSlopPx = dp(8)

        val longPressRunnable = Runnable {
            if (!isDragging && isBubbleAttached) {
                longPressTriggered = true
                performLightHaptic()
                openWhisperOrbStudioApp()
            }
        }

        container.setOnTouchListener { _, event ->
            val lp = bubbleLayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startWindowX = lp.x
                    startWindowY = lp.y
                    isDragging = false
                    longPressTriggered = false
                    mainHandler.postDelayed(longPressRunnable, 520L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        val (screenW, screenH) = getScreenBoundsPx()
                        val bubbleSize = dp(56)
                        val newX = (startWindowX + dx.roundToInt()).coerceIn(0, max(0, screenW - bubbleSize))
                        val newY = (startWindowY + dy.roundToInt()).coerceIn(dp(48), max(dp(48), screenH - bubbleSize - dp(48)))
                        updateBubblePosition(newX, newY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!isDragging && !longPressTriggered) {
                        performLightHaptic()
                        captureFocusedEditableNode()
                        engine.toggleRecording()
                        refreshFloatingBubbleVisuals()
                    } else if (isDragging) {
                        snapBubbleToNearestHorizontalEdge()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        snapBubbleToNearestHorizontalEdge()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleContainer = container
    }

    private fun createBubbleBackgroundDrawable(state: WhisperOrbState): GradientDrawable {
        val strokeColor = when (state) {
            WhisperOrbState.IDLE -> Color.parseColor("#38FFFFFF")
            WhisperOrbState.LISTENING -> Color.parseColor("#FF3B30")
            WhisperOrbState.TRANSCRIBING -> Color.parseColor("#32ADE6")
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E814141A"))
            setStroke(max(1, dp(2)), strokeColor)
        }
    }

    private fun refreshFloatingBubbleVisuals() {
        if (!::engine.isInitialized) return
        val state = engine.orbState
        bubbleOrbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            audioLevel = engine.currentAudioLevel
            orbState = state
        }
        bubbleContainer?.background = createBubbleBackgroundDrawable(state)
    }

    private fun attachFloatingBubbleIfNeeded() {
        if (isBubbleAttached) return
        val wm = windowManager ?: return
        val view = bubbleContainer ?: return
        val (screenW, screenH) = getScreenBoundsPx()
        val bubbleSize = dp(56)
        val edgeMargin = dp(12)

        if (bubblePosX < 0 || bubblePosY < 0) {
            bubblePosX = max(edgeMargin, screenW - bubbleSize - edgeMargin)
            bubblePosY = (screenH * 0.42f).roundToInt().coerceIn(dp(72), max(dp(72), screenH - bubbleSize - dp(96)))
        } else {
            bubblePosX = bubblePosX.coerceIn(edgeMargin, max(edgeMargin, screenW - bubbleSize - edgeMargin))
            bubblePosY = bubblePosY.coerceIn(dp(48), max(dp(48), screenH - bubbleSize - dp(48)))
        }

        val lp = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubblePosX
            y = bubblePosY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            wm.addView(view, lp)
            bubbleLayoutParams = lp
            isBubbleAttached = true
        } catch (_: Exception) {
        }
    }

    private fun updateBubblePosition(newX: Int, newY: Int) {
        val wm = windowManager ?: return
        val view = bubbleContainer ?: return
        val lp = bubbleLayoutParams ?: return
        bubblePosX = newX
        bubblePosY = newY
        if (lp.x != newX || lp.y != newY) {
            lp.x = newX
            lp.y = newY
            if (isBubbleAttached) {
                try {
                    wm.updateViewLayout(view, lp)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun snapBubbleToNearestHorizontalEdge() {
        val lp = bubbleLayoutParams ?: return
        val (screenW, screenH) = getScreenBoundsPx()
        val bubbleSize = dp(56)
        val edgeMargin = dp(12)
        val currentCenterX = lp.x + bubbleSize / 2
        val targetX = if (currentCenterX < screenW / 2) {
            edgeMargin
        } else {
            max(edgeMargin, screenW - bubbleSize - edgeMargin)
        }
        val clampedY = lp.y.coerceIn(dp(56), max(dp(56), screenH - bubbleSize - dp(64)))
        val startX = lp.x

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 210L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val animatedX = anim.animatedValue as? Int ?: targetX
                updateBubblePosition(animatedX, clampedY)
            }
            start()
        }
    }

    private fun detachFloatingBubble() {
        snapAnimator?.cancel()
        if (!isBubbleAttached) return
        val wm = windowManager ?: return
        val view = bubbleContainer ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        isBubbleAttached = false
    }

    private fun openWhisperOrbStudioApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // MARK: - Insertion Directe de Secours dans le Champ Actif (utilisable par la Bulle Flottante ou hors-IME)

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

    fun insertTranscribedTextIntoActiveField(text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { insertTranscribedTextIntoActiveField(text) }
            return
        }
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
                .replace(selStart, selEnd, text)
                .toString()

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                val newCursor = (selStart + text.length).coerceAtMost(newText.length)
                val selArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                return
            }

            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("WhisperOrb", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) {
        }
    }

    fun deleteLastCharactersFromActiveField(charCount: Int) {
        if (charCount <= 0) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { deleteLastCharactersFromActiveField(charCount) }
            return
        }
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
