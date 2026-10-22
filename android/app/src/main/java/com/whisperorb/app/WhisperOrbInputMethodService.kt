package com.whisperorb.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class WhisperOrbInputMethodService : InputMethodService() {

    companion object {
        fun isImeEnabled(context: Context): Boolean {
            return try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                val list = imm?.enabledInputMethodList ?: return false
                list.any { it.packageName == context.packageName }
            } catch (_: Exception) {
                false
            }
        }

        fun isImeSelectedAsDefault(context: Context): Boolean {
            return try {
                val defaultIme = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                ) ?: ""
                defaultIme.startsWith("${context.packageName}/")
            } catch (_: Exception) {
                false
            }
        }
    }

    private enum class KeyboardLayer {
        LETTERS,
        NUMBERS,
        SYMBOLS,
        EMOJIS
    }

    private enum class ShiftState {
        LOWER,
        UPPER,
        CAPS_LOCK
    }

    private lateinit var engine: WhisperAndroidEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootFrame: FrameLayout? = null
    private var collapsedBarContainer: LinearLayout? = null
    private var expandedKeyboardContainer: LinearLayout? = null
    private var numericKeypadContainer: LinearLayout? = null

    private var expandedOrbView: GeminiOrbCanvasView? = null
    private var collapsedOrbView: GeminiOrbCanvasView? = null
    private var expandedStatusDot: View? = null
    private var collapsedStatusDot: View? = null
    private var expandedStatusLabel: TextView? = null
    private var collapsedStatusLabel: TextView? = null
    private var expandedLangPill: TextView? = null

    private var suggestionLeftBtn: TextView? = null
    private var suggestionCenterBtn: TextView? = null
    private var suggestionRightBtn: TextView? = null

    private var dynamicCenterArea: FrameLayout? = null
    private var accentBarOverlay: LinearLayout? = null

    private var currentLayer: KeyboardLayer = KeyboardLayer.LETTERS
    private var shiftState: ShiftState = ShiftState.UPPER
    private var showQuickSettingsPanel: Boolean = false
    private var isNumericFieldActive: Boolean = false
    private var selectedEmojiCategoryIndex: Int = 1
    private var lastShiftTapMs: Long = 0L
    private var lastSpaceTapMs: Long = 0L

    private var currentSuggestions: AutocorrectSuggestionSet = AutocorrectSuggestionSet.EMPTY
    private var isHoldingBackspace: Boolean = false

    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (isHoldingBackspace) {
                performDeleteBackward()
                mainHandler.postDelayed(this, 70L)
            }
        }
    }

    private val engineListener: () -> Unit = {
        refreshOrbAndStatusVisuals()
    }

    // MARK: - Catégories d'Émojis Complètes (Identiques à Apple)

    private data class EmojiCategory(val icon: String, val title: String, val emojis: List<String>)

    private val emojiCategories = listOf(
        EmojiCategory("🕒", "RÉCENTS", emptyList()),
        EmojiCategory(
            "😀", "SMILEYS & PERSONNES",
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "🫠", "😉", "😊", "😇", "🥰", "😍",
                "🤩", "😘", "😗", "☺️", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🫢",
                "🫣", "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥",
                "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯",
                "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "🥹",
                "😢", "😭", "😱", "😤", "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻", "👽", "🤖", "👋", "👌",
                "🤌", "✌️", "🤞", "🫶", "👍", "👎", "✊", "👊", "👏", "🙌", "🤝", "🙏", "💪", "👀", "🧠", "👑"
            )
        ),
        EmojiCategory(
            "🐾", "ANIMAUX & NATURE",
            listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
                "🙉", "🙊", "🐔", "🐧", "🐦", "🦅", "🦉", "🐺", "🐴", "🦄", "🐝", "🦋", "🐢", "🐍", "🦖", "🐙",
                "🐬", "🐳", "🦈", "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "🍀", "🌷", "🌹", "🌺", "🌸", "🌻",
                "🌞", "🌕", "🌙", "🌍", "🪐", "💫", "⭐", "🌟", "✨", "⚡", "🔥", "🌈", "☀️", "❄️", "💧", "🌊"
            )
        ),
        EmojiCategory(
            "🍔", "NOURRITURE & BOISSONS",
            listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
                "🍅", "🥑", "🥦", "🌶️", "🌽", "🥕", "🥐", "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🥓", "🥩", "🍗",
                "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣", "🍱", "🍤", "🍦", "🍰", "🎂", "🧁",
                "🍫", "🍿", "🍩", "🍪", "☕", "🍵", "🥤", "🧋", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🍾"
            )
        ),
        EmojiCategory(
            "⚽", "ACTIVITÉS & SPORT",
            listOf(
                "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥊", "🥋", "⛳", "⛸️", "🎿", "🏂",
                "🏋️", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎫", "🎭", "🎨", "🎬", "🎤", "🎧", "🎹", "🥁",
                "🎷", "🎺", "🎸", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩", "🎉", "🎊", "🎈", "🎁", "🎆"
            )
        ),
        EmojiCategory(
            "🚀", "VOYAGES & LIEUX",
            listOf(
                "🚗", "🚕", "🚙", "🚌", "🏎️", "🚓", "🚑", "🚒", "🚲", "🛵", "🏍️", "🚨", "🚄", "🚅", "🚆", "🚇",
                "✈️", "🛫", "🛬", "🚀", "🛸", "🚁", "⛵", "🚤", "🚢", "⚓", "⛽", "🚦", "🗺️", "🗽", "🗼", "🏰",
                "🎡", "🎢", "🏖️", "🏝️", "⛰️", "🏔️", "🏕️", "🏠", "🏡", "🏢", "🏥", "🏦", "🏨", "🌅", "🌇", "🌌"
            )
        ),
        EmojiCategory(
            "💡", "OBJETS & TECH",
            listOf(
                "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🕹️", "📷", "📸", "📹", "🎥", "📞", "📺", "🎙️", "⏰",
                "📡", "🔋", "🔌", "💡", "🔦", "💸", "💵", "💶", "💷", "💰", "💳", "💎", "⚖️", "🔧", "🔨", "🛠️",
                "⚙️", "🧲", "💣", "🧨", "🛡️", "🔮", "🔭", "🔬", "💊", "💉", "🧬", "🔑", "🚪", "🎁", "✉️", "📦",
                "📊", "📈", "📉", "📅", "📁", "📚", "📖", "📌", "✂️", "🖊️", "📝", "✏️", "🔍", "🔒", "🔓", "👑"
            )
        ),
        EmojiCategory(
            "❤️", "SYMBOLES & CŒURS",
            listOf(
                "❤️", "🩷", "🧡", "💛", "💚", "💙", "🩵", "💜", "🖤", "🩶", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️",
                "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "☯️", "♾️",
                "💯", "💢", "❌", "⭕", "🛑", "⛔", "🚫", "❗", "❓", "‼️", "⚠️", "✅", "♻️", "🌐", "💤", "🎵",
                "🎶", "➕", "➖", "➗", "✖️", "💲", "✔️", "☑️", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪"
            )
        ),
        EmojiCategory(
            "🇫🇷", "DRAPEAUX",
            listOf(
                "🇫🇷", "🇬🇧", "🇺🇸", "🇨🇦", "🇧🇪", "🇨🇭", "🇪🇸", "🇩🇪", "🇮🇹", "🇵🇹", "🇳🇱", "🇱🇺", "🇲🇨", "🇮🇪", "🇦🇹", "🇬🇷",
                "🇵🇱", "🇸🇪", "🇳🇴", "🇩🇰", "🇫🇮", "🇨🇿", "🇭🇺", "🇷🇴", "🇺🇦", "🇹🇷", "🇯🇵", "🇰🇷", "🇨🇳", "🇹🇼", "🇭🇰", "🇸🇬",
                "🇮🇳", "🇦🇺", "🇳🇿", "🇧🇷", "🇲🇽", "🇦🇷", "🇨🇱", "🇨🇴", "🇲🇦", "🇩🇿", "🇹🇳", "🇸🇳", "🇨🇮", "🇨🇲", "🇿🇦", "🇪🇬",
                "🇸🇦", "🇦🇪", "🇶🇦", "🇱🇧", "🇪🇺", "🇺🇳", "🏳️", "🏴", "🏁", "🚩"
            )
        )
    )

    override fun onCreate() {
        super.onCreate()
        engine = WhisperAndroidEngine.getInstance(applicationContext)
        engine.addListener(engineListener)
        engine.onTranscribedTextForKeyboard = { dictatedText ->
            commitDictatedText(dictatedText)
        }
        engine.onDeleteLastSentenceRequested = { length ->
            deleteLastCharacters(length)
        }
    }

    override fun onDestroy() {
        if (::engine.isInitialized) {
            engine.removeListener(engineListener)
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#16171D"))
        }

        collapsedBarContainer = buildCollapsedVoiceBar()
        expandedKeyboardContainer = buildExpandedKeyboard()
        numericKeypadContainer = buildPureNumericKeypad()

        root.addView(collapsedBarContainer)
        root.addView(expandedKeyboardContainer)
        root.addView(numericKeypadContainer)

        rootFrame = root
        applyActiveModeVisibility()
        refreshOrbAndStatusVisuals()
        refreshAutocorrectBar()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // 1. Détection automatique des champs de CHIFFRES / TÉLÉPHONE / CODE PIN / DATE
        val rawInputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val inputClass = rawInputType and InputType.TYPE_MASK_CLASS
        isNumericFieldActive = (
            inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            )

        // 2. Sur Googlebook 14 / Chromebook / Tablette avec clavier physique attaché, démarre en barre vocale compacte
        if (HardwareDetector.isHardwareKeyboardAttached(this) && !isNumericFieldActive) {
            engine.isKeyboardCollapsed = true
        }

        // Réattache les callbacks de dictée à ce service
        engine.onTranscribedTextForKeyboard = { dictatedText ->
            commitDictatedText(dictatedText)
        }
        engine.onDeleteLastSentenceRequested = { length ->
            deleteLastCharacters(length)
        }

        applyActiveModeVisibility()
        refreshOrbAndStatusVisuals()
        refreshAutocorrectBar()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!isNumericFieldActive && !engine.isKeyboardCollapsed) {
            refreshAutocorrectBar()
        }
    }

    private fun applyActiveModeVisibility() {
        when {
            isNumericFieldActive -> {
                // Champ numérique : Clavier de chiffres pur SANS Orbe !
                numericKeypadContainer?.visibility = View.VISIBLE
                collapsedBarContainer?.visibility = View.GONE
                expandedKeyboardContainer?.visibility = View.GONE
            }
            engine.isKeyboardCollapsed -> {
                // Mode Clavier Réduit (Barre vocale compacte 58dp exactement comme sur Apple)
                numericKeypadContainer?.visibility = View.GONE
                collapsedBarContainer?.visibility = View.VISIBLE
                expandedKeyboardContainer?.visibility = View.GONE
            }
            else -> {
                // Clavier complet avec l'Orbe 3D incrusté
                numericKeypadContainer?.visibility = View.GONE
                collapsedBarContainer?.visibility = View.GONE
                expandedKeyboardContainer?.visibility = View.VISIBLE
            }
        }
    }

    // MARK: - 1. Mode Clavier Réduit (Barre Vocale Compacte 58dp avec Orbe Incrusté)

    private fun buildCollapsedVoiceBar(): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (isLarge) 14 else 8), dp(6), dp(if (isLarge) 14 else 8), dp(6))
            setBackgroundColor(Color.parseColor("#16171D"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Bouton ▴ Clavier (Déplier le clavier)
        val expandBtn = TextView(this).apply {
            text = "▴ Clavier"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLarge) 13f else 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = roundedKeyDrawable("#2E303B", dp(9), "#22FFFFFF")
            setOnClickListener {
                vibrateLight()
                engine.isKeyboardCollapsed = false
                applyActiveModeVisibility()
            }
        }

        // Bouton 🌐 Changement de clavier Android
        val globeBtn = TextView(this).apply {
            text = "🌐"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(6)
            }
            background = roundedKeyDrawable("#262830", dp(9))
            setOnClickListener {
                vibrateLight()
                showKeyboardPicker()
            }
        }

        // Pastille centrale interactive d'état & dictée
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedKeyDrawable("#24262F", dp(20), "#1AFFFFFF")
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            setOnClickListener {
                vibrateLight()
                engine.toggleRecording()
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(7)
            }
            background = circleDrawable("#30D158")
        }
        collapsedStatusDot = dot

        val statusTxt = TextView(this).apply {
            text = "Dicter • ${engine.selectedModel.shortBadgeName}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        collapsedStatusLabel = statusTxt

        statusPill.addView(dot)
        statusPill.addView(statusTxt)

        // Bouton ↶ Annuler dernière phrase dictée
        val undoBtn = TextView(this).apply {
            text = "↶"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(6)
            }
            background = roundedKeyDrawable("#262830", dp(9))
            setOnClickListener {
                vibrateLight()
                handleUndoLastSentence()
            }
        }

        // Bouton ⌫ Suppr avec répétition continue
        val backspaceBtn = createBackspaceButton(widthDp = 38, heightDp = 36, cornerDp = 9)

        // L'Orbe 3D incrusté à droite
        val orb = GeminiOrbCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(6)
            }
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
            setOnClickListener {
                vibrateLight()
                engine.toggleRecording()
            }
        }
        collapsedOrbView = orb

        bar.addView(expandBtn)
        bar.addView(globeBtn)
        bar.addView(statusPill)
        bar.addView(undoBtn)
        bar.addView(backspaceBtn)
        bar.addView(orb)
        return bar
    }

    // MARK: - 2. Clavier Complet WhisperOrb avec Orbe 3D Incrusté + ▾ Réduire + 3 Prédictions

    private fun buildExpandedKeyboard(): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (isLarge) 10 else 4), dp(6), dp(if (isLarge) 10 else 4), dp(if (isLarge) 10 else 6))
            setBackgroundColor(Color.parseColor("#16171D"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Rangée 0 : Barre Vocale Supérieure avec l'Orbe 3D littéralement incrusté dedans
        root.addView(buildTopVoiceControlBar())

        // Rangée 1 : Barre de 3 Prédictions & Correcteur Automatique IA (ou barre d'accents lors d'un appui long)
        val headerStripFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
                bottomMargin = dp(5)
            }
        }
        headerStripFrame.addView(buildPredictiveAutocorrectBar())
        accentBarOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = roundedKeyDrawable("#262834", dp(8), "#0A84FF")
        }
        headerStripFrame.addView(accentBarOverlay)
        root.addView(headerStripFrame)

        // Zone Centrale Dynamique (Grille de touches OU Sélecteur d'Émojis OU Panneau de réglages rapides)
        dynamicCenterArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(dynamicCenterArea)

        rebuildDynamicKeyboardLayer()
        return root
    }

    private fun buildTopVoiceControlBar(): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(0), dp(2), dp(0))
        }

        // Bouton ▾ Réduire (Replie tout le clavier en barre vocale compacte exactement comme chez Apple)
        val collapseBtn = TextView(this).apply {
            text = if (isLarge) "▾ Réduire" else "▾"
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLarge) 12.5f else 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(if (isLarge) 12 else 10), dp(6), dp(if (isLarge) 12 else 10), dp(6))
            background = roundedKeyDrawable("#262832", dp(16))
            setOnClickListener {
                vibrateLight()
                showQuickSettingsPanel = false
                engine.isKeyboardCollapsed = true
                applyActiveModeVisibility()
            }
        }

        // Pastille d'état & modèle Whisper (Tap = Dicter)
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedKeyDrawable("#262832", dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                engine.toggleRecording()
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                marginEnd = dp(6)
            }
            background = circleDrawable("#30D158")
        }
        expandedStatusDot = dot

        val statusTxt = TextView(this).apply {
            text = engine.selectedModel.shortBadgeName
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        expandedStatusLabel = statusTxt
        statusPill.addView(dot)
        statusPill.addView(statusTxt)

        // Pastille de langue rapide (ex: 📱 FR / 🌐 AUTO / 🇬🇧 EN)
        val langPill = TextView(this).apply {
            text = engine.selectedLanguage.shortPillLabel
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = roundedKeyDrawable("#262832", dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                cycleQuickLanguage()
            }
        }
        expandedLangPill = langPill

        // Bouton ↶ Annuler dernière phrase dictée
        val undoBtn = TextView(this).apply {
            text = "↶"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginStart = dp(5)
            }
            background = circleDrawable("#262832")
            setOnClickListener {
                vibrateLight()
                handleUndoLastSentence()
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }

        // Bouton ⚙ Réglages rapides intégrés au clavier (Langue, 7 Modèles, Couleurs, Autocorrect)
        val settingsBtn = TextView(this).apply {
            text = "⚙"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(6)
            }
            background = circleDrawable("#262832")
            setOnClickListener {
                vibrateLight()
                showQuickSettingsPanel = !showQuickSettingsPanel
                rebuildDynamicKeyboardLayer()
            }
        }

        // L'ORBE 3D LITTÉRALEMENT INCRUSTÉ DANS LE CLAVIER ANDROID (Tap = Dicter, Appui long = Réglages rapides)
        val orb = GeminiOrbCanvasView(this).apply {
            val orbSize = dp(if (isLarge) 44 else 40)
            layoutParams = LinearLayout.LayoutParams(orbSize, orbSize)
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
            setOnClickListener {
                vibrateLight()
                engine.toggleRecording()
            }
            setOnLongClickListener {
                vibrateLight()
                showQuickSettingsPanel = !showQuickSettingsPanel
                rebuildDynamicKeyboardLayer()
                true
            }
        }
        expandedOrbView = orb

        bar.addView(collapseBtn)
        bar.addView(statusPill)
        bar.addView(langPill)
        bar.addView(undoBtn)
        bar.addView(spacer)
        bar.addView(settingsBtn)
        bar.addView(orb)
        return bar
    }

    // MARK: - Barre de 3 Suggestions & Correcteur Automatique IA

    private fun buildPredictiveAutocorrectBar(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = createSuggestionSlotView()
        val center = createSuggestionSlotView()
        val right = createSuggestionSlotView()

        left.setOnClickListener {
            vibrateLight()
            val s = currentSuggestions
            if (s.typedWord.isEmpty()) {
                applySuggestionCandidate(s.secondaryPrediction, 0)
            } else {
                // Garde le mot exact tapé sans autocorrection
                currentInputConnection?.commitText(" ", 1)
                refreshAutocorrectBar()
            }
        }

        center.setOnClickListener {
            vibrateLight()
            val s = currentSuggestions
            applySuggestionCandidate(s.primaryCorrection, s.typedWord.length)
        }

        right.setOnClickListener {
            vibrateLight()
            val s = currentSuggestions
            val candidate = if (s.typedWord.isEmpty()) s.tertiaryPrediction else s.secondaryPrediction
            applySuggestionCandidate(candidate, s.typedWord.length)
        }

        suggestionLeftBtn = left
        suggestionCenterBtn = center
        suggestionRightBtn = right

        row.addView(left, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) })
        row.addView(center, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) })
        row.addView(right, LinearLayout.LayoutParams(0, dp(30), 1f))
        return row
    }

    private fun createSuggestionSlotView(): TextView {
        return TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            maxLines = 1
            background = roundedKeyDrawable("#22242D", dp(7))
        }
    }

    private fun refreshAutocorrectBar() {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: ""
        val langCode = engine.selectedLanguage.effectiveStyleLanguageCode
        val suggestions = SmartAutocorrectEngine.computeSuggestions(this, before, langCode)
        currentSuggestions = suggestions

        val leftTitle = if (suggestions.typedWord.isEmpty()) suggestions.secondaryPrediction else "« ${suggestions.typedWord} »"
        val centerTitle = suggestions.primaryCorrection
        val rightTitle = if (suggestions.typedWord.isEmpty()) suggestions.tertiaryPrediction else suggestions.secondaryPrediction
        val highlightCenter = engine.isSmartAutocorrectEnabled && suggestions.shouldAutoReplaceOnSpace

        suggestionLeftBtn?.text = leftTitle
        suggestionCenterBtn?.apply {
            text = centerTitle
            typeface = if (highlightCenter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = if (highlightCenter) {
                roundedKeyDrawable("#3A0A84FF", dp(7), "#0A84FF")
            } else {
                roundedKeyDrawable("#2A2C37", dp(7))
            }
        }
        suggestionRightBtn?.text = rightTitle
    }

    private fun applySuggestionCandidate(candidate: String, replaceCount: Int) {
        if (candidate.isEmpty()) return
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (replaceCount > 0) {
            ic.deleteSurroundingText(replaceCount, 0)
        }
        ic.commitText("$candidate ", 1)
        ic.endBatchEdit()

        if (shiftState == ShiftState.UPPER) {
            shiftState = ShiftState.LOWER
            rebuildDynamicKeyboardLayer()
        }
        refreshAutocorrectBar()
    }

    // MARK: - Construction Dynamique des Calques (AZERTY / QWERTY / 123 / #+= / Émojis / Réglages)

    private fun rebuildDynamicKeyboardLayer() {
        val container = dynamicCenterArea ?: return
        container.removeAllViews()

        if (showQuickSettingsPanel) {
            container.addView(buildQuickSettingsPanelView())
            return
        }

        if (currentLayer == KeyboardLayer.EMOJIS) {
            container.addView(buildEmojiPickerView())
            return
        }

        val isFrenchLayout = (engine.selectedLanguage.effectiveStyleLanguageCode == "fr")
        val row1: List<String>
        val row2: List<String>
        val row3: List<String>

        when (currentLayer) {
            KeyboardLayer.LETTERS -> {
                if (isFrenchLayout) {
                    row1 = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
                    row2 = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
                    row3 = listOf("w", "x", "c", "v", "b", "n", "'")
                } else {
                    row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                    row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                    row3 = listOf("z", "x", "c", "v", "b", "n", "m")
                }
            }
            KeyboardLayer.NUMBERS -> {
                row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                row2 = listOf("-", "/", ":", ";", "(", ")", "€", "&", "@", "\"")
                row3 = listOf(".", ",", "?", "!", "'")
            }
            KeyboardLayer.SYMBOLS -> {
                row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
                row2 = listOf("_", "\\", "|", "~", "<", ">", "$", "£", "¥", "•")
                row3 = listOf(".", ",", "?", "!", "'")
            }
            KeyboardLayer.EMOJIS -> return
        }

        val gridBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        gridBox.addView(buildCharacterRowView(row1))
        gridBox.addView(spaceVertical(dp(6)))
        gridBox.addView(buildCharacterRowView(row2))
        gridBox.addView(spaceVertical(dp(6)))
        gridBox.addView(buildRow3WithShiftAndBackspace(row3))
        gridBox.addView(spaceVertical(dp(6)))
        gridBox.addView(buildBottomRow4View())

        container.addView(gridBox)
    }

    private fun buildCharacterRowView(keys: List<String>): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val keyHeight = dp(if (isLarge) 46 else 42)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        for (i in keys.indices) {
            val rawKey = keys[i]
            val label = displayKeyLabel(rawKey)
            val keyView = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLarge) 20f else 19f)
                gravity = Gravity.CENTER
                background = roundedKeyDrawable("#2B2D38", dp(6))
                layoutParams = LinearLayout.LayoutParams(0, keyHeight, 1f).apply {
                    if (i < keys.size - 1) marginEnd = dp(5)
                }
                setOnClickListener {
                    vibrateLight()
                    hideAccentsOverlay()
                    commitCharacterKey(label)
                }
                setOnLongClickListener {
                    val variants = variantsForKey(rawKey)
                    if (variants.isNotEmpty()) {
                        vibrateLight()
                        showAccentsOverlayFor(variants)
                        true
                    } else {
                        false
                    }
                }
            }
            row.addView(keyView)
        }
        return row
    }

    private fun buildRow3WithShiftAndBackspace(keys: List<String>): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val keyHeight = dp(if (isLarge) 46 else 42)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val isShiftHighlighted = currentLayer == KeyboardLayer.LETTERS && shiftState != ShiftState.LOWER
        val shiftLabel = when (currentLayer) {
            KeyboardLayer.LETTERS -> when (shiftState) {
                ShiftState.LOWER -> "⇧"
                ShiftState.UPPER -> "⬆"
                ShiftState.CAPS_LOCK -> "⇪"
            }
            KeyboardLayer.NUMBERS -> "#+="
            KeyboardLayer.SYMBOLS -> "123"
            KeyboardLayer.EMOJIS -> "ABC"
        }

        val shiftBtn = TextView(this).apply {
            text = shiftLabel
            setTextColor(if (isShiftHighlighted) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (currentLayer == KeyboardLayer.LETTERS) 17f else 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedKeyDrawable(if (isShiftHighlighted) "#FFFFFF" else "#3A3C4A", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(if (isLarge) 58 else 44), keyHeight).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                vibrateLight()
                handleShiftOrSymbolToggle()
            }
        }
        row.addView(shiftBtn)

        val charsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, keyHeight, 1f)
        }

        for (i in keys.indices) {
            val rawKey = keys[i]
            val label = displayKeyLabel(rawKey)
            val keyView = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLarge) 20f else 19f)
                gravity = Gravity.CENTER
                background = roundedKeyDrawable("#2B2D38", dp(6))
                layoutParams = LinearLayout.LayoutParams(0, keyHeight, 1f).apply {
                    if (i < keys.size - 1) marginEnd = dp(5)
                }
                setOnClickListener {
                    vibrateLight()
                    hideAccentsOverlay()
                    commitCharacterKey(label)
                }
                setOnLongClickListener {
                    val variants = variantsForKey(rawKey)
                    if (variants.isNotEmpty()) {
                        vibrateLight()
                        showAccentsOverlayFor(variants)
                        true
                    } else {
                        false
                    }
                }
            }
            charsContainer.addView(keyView)
        }
        row.addView(charsContainer)

        val backspaceBtn = createBackspaceButton(
            widthDp = if (isLarge) 58 else 44,
            heightDp = if (isLarge) 46 else 42,
            cornerDp = 6
        ).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6)
        }
        row.addView(backspaceBtn)

        return row
    }

    private fun buildBottomRow4View(): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val keyHeight = dp(if (isLarge) 46 else 42)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Touche 123 / ABC
        val modeBtn = TextView(this).apply {
            text = if (currentLayer == KeyboardLayer.LETTERS) "123" else "ABC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#3A3C4A", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(if (isLarge) 62 else 44), keyHeight).apply {
                marginEnd = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                currentLayer = if (currentLayer == KeyboardLayer.LETTERS) KeyboardLayer.NUMBERS else KeyboardLayer.LETTERS
                rebuildDynamicKeyboardLayer()
            }
        }

        // Touche 😀 Émojis Complets
        val emojiBtn = TextView(this).apply {
            text = "😀"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#3A3C4A", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(if (isLarge) 48 else 40), keyHeight).apply {
                marginEnd = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                currentLayer = KeyboardLayer.EMOJIS
                rebuildDynamicKeyboardLayer()
            }
        }

        // Touche 🌐 Globe (Changement de clavier Android)
        val globeBtn = TextView(this).apply {
            text = "🌐"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#3A3C4A", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(if (isLarge) 48 else 40), keyHeight).apply {
                marginEnd = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                showKeyboardPicker()
            }
        }

        // Barre d'espace ("espace" • double-tap = ". " • autocorrection IA automatique)
        val spaceLabel = when (engine.selectedLanguage.effectiveStyleLanguageCode) {
            "fr" -> "espace"
            "es" -> "espacio"
            "de" -> "Leerzeichen"
            "it" -> "spazio"
            "pt" -> "espaço"
            else -> "space"
        }
        val spaceBtn = TextView(this).apply {
            text = spaceLabel
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#2B2D38", dp(6))
            layoutParams = LinearLayout.LayoutParams(0, keyHeight, 1f).apply {
                marginEnd = dp(5)
            }
            setOnClickListener {
                vibrateLight()
                hideAccentsOverlay()
                handleSpaceKeyTap()
            }
        }

        // Touche Retour / Entrée ("retour")
        val enterLabel = if (engine.selectedLanguage.effectiveStyleLanguageCode == "fr") "retour" else "return"
        val enterBtn = TextView(this).apply {
            text = enterLabel
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#0A84FF", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(if (isLarge) 94 else 76), keyHeight)
            setOnClickListener {
                vibrateLight()
                hideAccentsOverlay()
                handleEnterKeyTap()
            }
        }

        row.addView(modeBtn)
        row.addView(emojiBtn)
        row.addView(globeBtn)
        row.addView(spaceBtn)
        row.addView(enterBtn)
        return row
    }

    // MARK: - 3. Clavier de Chiffres Pur Automatique (Zéro Orbe sur les Champs Numériques / PIN / Téléphone)

    private fun buildPureNumericKeypad(): LinearLayout {
        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val keyHeight = dp(if (isLarge) 52 else 48)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(Color.parseColor("#16171D"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        val rows = listOf(
            listOf("1", "2", "3", "-"),
            listOf("4", "5", "6", " "),
            listOf("7", "8", "9", "⌫"),
            listOf(",", "0", ".", "OK")
        )

        for (rIdx in rows.indices) {
            val rowKeys = rows[rIdx]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (rIdx < rows.size - 1) {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                }
            }

            for (cIdx in rowKeys.indices) {
                val token = rowKeys[cIdx]
                val isAction = (token == "OK")
                val isMod = (token == "-" || token == " " || token == "⌫" || token == "," || token == ".")
                val bgHex = when {
                    isAction -> "#0A84FF"
                    isMod -> "#343644"
                    else -> "#282A35"
                }

                val btn = TextView(this).apply {
                    text = if (token == " ") "␣" else token
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (token.length == 1 && token[0].isDigit()) 22f else 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = roundedKeyDrawable(bgHex, dp(8))
                    layoutParams = LinearLayout.LayoutParams(0, keyHeight, 1f).apply {
                        if (cIdx < rowKeys.size - 1) marginEnd = dp(6)
                    }
                    setOnClickListener {
                        vibrateLight()
                        when (token) {
                            "⌫" -> performDeleteBackward()
                            "OK" -> handleEnterKeyTap()
                            else -> currentInputConnection?.commitText(token, 1)
                        }
                    }
                }
                row.addView(btn)
            }
            root.addView(row)
        }
        return root
    }

    // MARK: - 4. Sélecteur Complet d'Émojis (9 Catégories + Récents)

    private fun buildEmojiPickerView(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val activeCat = emojiCategories[selectedEmojiCategoryIndex]
        val list = if (selectedEmojiCategoryIndex == 0) engine.recentEmojis else activeCat.emojis

        val catTitle = TextView(this).apply {
            text = activeCat.title
            setTextColor(Color.parseColor("#88FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(4))
        }
        box.addView(catTitle)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(156))
        }

        val isLarge = HardwareDetector.isTabletOrLargeScreen(this)
        val cols = if (isLarge) 12 else 8
        val grid = GridLayout(this).apply {
            columnCount = cols
        }

        for (emoji in list) {
            val cell = TextView(this).apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener {
                    vibrateLight()
                    currentInputConnection?.commitText(emoji, 1)
                    engine.recordUsedEmoji(emoji)
                }
            }
            grid.addView(cell)
        }
        scroll.addView(grid)
        box.addView(scroll)

        // Barre inférieure d'onglets d'émojis (ABC + 9 catégories + ⌫)
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(2))
        }

        val abcBtn = TextView(this).apply {
            text = "ABC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#3A3C4A", dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(34)).apply { marginEnd = dp(4) }
            setOnClickListener {
                vibrateLight()
                currentLayer = KeyboardLayer.LETTERS
                rebuildDynamicKeyboardLayer()
            }
        }
        tabBar.addView(abcBtn)

        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (idx in emojiCategories.indices) {
            val cat = emojiCategories[idx]
            val selected = (idx == selectedEmojiCategoryIndex)
            val tab = TextView(this).apply {
                text = cat.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(32)).apply { marginEnd = dp(3) }
                background = if (selected) roundedKeyDrawable("#3A3C4A", dp(16)) else null
                setOnClickListener {
                    vibrateLight()
                    selectedEmojiCategoryIndex = idx
                    rebuildDynamicKeyboardLayer()
                }
            }
            tabsRow.addView(tab)
        }
        tabsScroll.addView(tabsRow)
        tabBar.addView(tabsScroll)

        val backBtn = createBackspaceButton(widthDp = 42, heightDp = 34, cornerDp = 6).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(4)
        }
        tabBar.addView(backBtn)

        box.addView(tabBar)
        return box
    }

    // MARK: - 5. Panneau Rapide Intégré (Langue, 7 Modèles Whisper, Couleurs de l'Orbe & Correcteur IA)

    private fun buildQuickSettingsPanelView(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedKeyDrawable("#1E2029", dp(12), "#22FFFFFF")
        }

        // 1. Langue de dictée
        val langTitle = TextView(this).apply {
            text = "🌐 Langue de dictée & du correcteur"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(langTitle)
        box.addView(spaceVertical(dp(6)))

        val langScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (lang in WhisperLanguageSelection.entries) {
            val selected = (engine.selectedLanguage == lang)
            val pill = TextView(this).apply {
                text = lang.shortPillLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedKeyDrawable(if (selected) "#0A84FF" else "#2B2D38", dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    vibrateLight()
                    engine.selectedLanguage = lang
                    refreshOrbAndStatusVisuals()
                    refreshAutocorrectBar()
                    rebuildDynamicKeyboardLayer()
                }
            }
            langRow.addView(pill)
        }
        langScroll.addView(langRow)
        box.addView(langScroll)
        box.addView(spaceVertical(dp(8)))

        // 2. Modèle Whisper (7 Modèles avec sécurité RAM)
        val modelTitle = TextView(this).apply {
            text = "🧠 Modèle Whisper (Débloqué selon la RAM)"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(modelTitle)
        box.addView(spaceVertical(dp(6)))

        val modelScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val modelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val downloader = ModelDownloader.getInstance(this)
        for (tier in WhisperModelTier.entries) {
            val unlocked = HardwareDetector.isTierUnlocked(this, tier, engine.simulateModest4GBDevice)
            val selected = (engine.selectedModel == tier)
            val downloaded = downloader.isModelDownloaded(tier)
            val pill = TextView(this).apply {
                text = when {
                    !unlocked -> "🔒 ${tier.shortBadgeName}"
                    downloaded -> "${tier.shortBadgeName} (${tier.latencyDescription})"
                    else -> "⬇ ${tier.shortBadgeName}"
                }
                setTextColor(if (unlocked) Color.WHITE else Color.parseColor("#66FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedKeyDrawable(if (selected) "#0A84FF" else "#2B2D38", dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    if (!unlocked) return@setOnClickListener
                    vibrateLight()
                    engine.selectedModel = tier
                    if (downloaded) {
                        engine.loadModel(tier)
                    } else {
                        downloader.downloadModel(tier) { completed ->
                            engine.selectedModel = completed
                            engine.loadModel(completed)
                        }
                    }
                    refreshOrbAndStatusVisuals()
                    rebuildDynamicKeyboardLayer()
                }
            }
            modelRow.addView(pill)
        }
        modelScroll.addView(modelRow)
        box.addView(modelScroll)
        box.addView(spaceVertical(dp(8)))

        // 3. Thème de l'Orbe & Correcteur IA
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val autocorrectToggle = TextView(this).apply {
            text = if (engine.isSmartAutocorrectEnabled) "✓ Correcteur IA Actif" else "Correcteur IA Désactivé"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedKeyDrawable(if (engine.isSmartAutocorrectEnabled) "#2430D158" else "#2B2D38", dp(8), if (engine.isSmartAutocorrectEnabled) "#30D158" else null)
            setOnClickListener {
                vibrateLight()
                engine.isSmartAutocorrectEnabled = !engine.isSmartAutocorrectEnabled
                refreshAutocorrectBar()
                rebuildDynamicKeyboardLayer()
            }
        }

        val closeBtn = TextView(this).apply {
            text = "Fermer ✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedKeyDrawable("#3A3C4A", dp(8))
            setOnClickListener {
                vibrateLight()
                showQuickSettingsPanel = false
                rebuildDynamicKeyboardLayer()
            }
        }

        bottomRow.addView(autocorrectToggle)
        bottomRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f) })
        bottomRow.addView(closeBtn)
        box.addView(bottomRow)

        return box
    }

    // MARK: - Actions de Saisie, Accents & Dictée

    private fun commitCharacterKey(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (currentLayer == KeyboardLayer.LETTERS && shiftState == ShiftState.UPPER) {
            shiftState = ShiftState.LOWER
            rebuildDynamicKeyboardLayer()
        }
        refreshAutocorrectBar()
    }

    private fun handleSpaceKeyTap() {
        val now = System.currentTimeMillis()
        val ic = currentInputConnection ?: return

        if (now - lastSpaceTapMs < 300L && currentSuggestions.typedWord.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            if (shiftState == ShiftState.LOWER) {
                shiftState = ShiftState.UPPER
                rebuildDynamicKeyboardLayer()
            }
            lastSpaceTapMs = 0L
            refreshAutocorrectBar()
            return
        }

        // Application automatique de la correction IA si activée (ex: "jarrive" -> "j'arrive ", "cest" -> "c'est ")
        val s = currentSuggestions
        if (engine.isSmartAutocorrectEnabled && s.shouldAutoReplaceOnSpace && s.typedWord.isNotEmpty()) {
            applySuggestionCandidate(s.primaryCorrection, s.typedWord.length)
        } else {
            ic.commitText(" ", 1)
            refreshAutocorrectBar()
        }
        lastSpaceTapMs = now
    }

    private fun handleEnterKeyTap() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val imeAction = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val isMultiLine = ((info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (!isMultiLine && imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction)
        } else {
            ic.commitText("\n", 1)
            if (shiftState == ShiftState.LOWER) {
                shiftState = ShiftState.UPPER
                rebuildDynamicKeyboardLayer()
            }
        }
        refreshAutocorrectBar()
    }

    private fun handleShiftOrSymbolToggle() {
        when (currentLayer) {
            KeyboardLayer.LETTERS -> {
                val now = System.currentTimeMillis()
                shiftState = if (now - lastShiftTapMs < 340L) {
                    ShiftState.CAPS_LOCK
                } else {
                    when (shiftState) {
                        ShiftState.LOWER -> ShiftState.UPPER
                        ShiftState.UPPER, ShiftState.CAPS_LOCK -> ShiftState.LOWER
                    }
                }
                lastShiftTapMs = now
            }
            KeyboardLayer.NUMBERS -> currentLayer = KeyboardLayer.SYMBOLS
            KeyboardLayer.SYMBOLS -> currentLayer = KeyboardLayer.NUMBERS
            KeyboardLayer.EMOJIS -> currentLayer = KeyboardLayer.LETTERS
        }
        rebuildDynamicKeyboardLayer()
    }

    private fun performDeleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        refreshAutocorrectBar()
    }

    private fun commitDictatedText(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshAutocorrectBar()
    }

    private fun deleteLastCharacters(count: Int) {
        if (count <= 0) return
        currentInputConnection?.deleteSurroundingText(count, 0)
        refreshAutocorrectBar()
    }

    private fun handleUndoLastSentence() {
        if (engine.lastInsertedSentenceLength > 0) {
            deleteLastCharacters(engine.lastInsertedSentenceLength)
            engine.lastInsertedSentenceLength = 0
        } else {
            performDeleteBackward()
        }
    }

    private fun showAccentsOverlayFor(variants: List<String>) {
        val bar = accentBarOverlay ?: return
        bar.removeAllViews()
        for (v in variants) {
            val btn = TextView(this).apply {
                text = v
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = roundedKeyDrawable("#363949", dp(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    vibrateLight()
                    hideAccentsOverlay()
                    commitCharacterKey(v)
                }
            }
            bar.addView(btn)
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF453A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { hideAccentsOverlay() }
        }
        bar.addView(close)
        bar.visibility = View.VISIBLE
    }

    private fun hideAccentsOverlay() {
        accentBarOverlay?.visibility = View.GONE
    }

    private fun variantsForKey(rawKey: String): List<String> {
        val base = rawKey.lowercase()
        val list = when (base) {
            "e" -> listOf("é", "è", "ê", "ë", "ę")
            "a" -> listOf("à", "â", "æ", "á", "ä", "ã")
            "u" -> listOf("ù", "û", "ü", "ú")
            "i" -> listOf("î", "ï", "í", "ì")
            "o" -> listOf("ô", "œ", "ö", "ó", "ò")
            "c" -> listOf("ç", "ć", "č")
            "n" -> listOf("ñ", "ń")
            "s" -> listOf("ß", "ś", "š")
            "y" -> listOf("ÿ")
            "'" -> listOf("'", ",", ".", "?", "!", "\"", "«", "»")
            else -> emptyList()
        }
        return if (currentLayer == KeyboardLayer.LETTERS && shiftState != ShiftState.LOWER) {
            list.map { it.uppercase() }
        } else {
            list
        }
    }

    private fun displayKeyLabel(rawKey: String): String {
        if (currentLayer != KeyboardLayer.LETTERS) return rawKey
        if (rawKey.length == 1 && rawKey[0].isLetter()) {
            return if (shiftState == ShiftState.LOWER) rawKey.lowercase() else rawKey.uppercase()
        }
        return rawKey
    }

    private fun createBackspaceButton(widthDp: Int, heightDp: Int, cornerDp: Int): TextView {
        return TextView(this).apply {
            text = "⌫"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            background = roundedKeyDrawable("#3A3C4A", dp(cornerDp))
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
            setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        vibrateLight()
                        view.background = roundedKeyDrawable("#525568", dp(cornerDp))
                        isHoldingBackspace = true
                        performDeleteBackward()
                        mainHandler.removeCallbacks(backspaceRepeatRunnable)
                        mainHandler.postDelayed(backspaceRepeatRunnable, 360L)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.background = roundedKeyDrawable("#3A3C4A", dp(cornerDp))
                        isHoldingBackspace = false
                        mainHandler.removeCallbacks(backspaceRepeatRunnable)
                        true
                    }
                    else -> false
                }
            }
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
        engine.selectedLanguage = quickCycle[(idx + 1) % quickCycle.size]
        refreshOrbAndStatusVisuals()
        refreshAutocorrectBar()
        rebuildDynamicKeyboardLayer()
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    private fun refreshOrbAndStatusVisuals() {
        applyActiveModeVisibility()

        expandedOrbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
        }
        collapsedOrbView?.apply {
            bgHex = engine.bgHex
            fgHex = engine.fgHex
            orbState = engine.orbState
            audioLevel = engine.currentAudioLevel
        }

        val dotHex = when (engine.orbState) {
            WhisperOrbState.IDLE -> "#30D158"
            WhisperOrbState.LISTENING -> "#0A84FF"
            WhisperOrbState.TRANSCRIBING -> "#FFD60A"
        }
        expandedStatusDot?.background = circleDrawable(dotHex)
        collapsedStatusDot?.background = circleDrawable(dotHex)

        expandedStatusLabel?.text = when (engine.orbState) {
            WhisperOrbState.IDLE -> engine.selectedModel.shortBadgeName
            WhisperOrbState.LISTENING -> "Écoute..."
            WhisperOrbState.TRANSCRIBING -> "Transcription..."
        }
        collapsedStatusLabel?.text = when (engine.orbState) {
            WhisperOrbState.IDLE -> "Dicter • ${engine.selectedModel.shortBadgeName}"
            WhisperOrbState.LISTENING -> "Écoute (${engine.selectedLanguage.shortPillLabel})..."
            WhisperOrbState.TRANSCRIBING -> "Transcription C++..."
        }

        expandedLangPill?.text = engine.selectedLanguage.shortPillLabel
    }

    // MARK: - Helpers Graphiques

    private fun roundedKeyDrawable(fillHex: String, radiusPx: Int, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun circleDrawable(fillHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fillHex))
        }
    }

    private fun spaceVertical(px: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px)
        }
    }

    private fun vibrateLight() {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
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
