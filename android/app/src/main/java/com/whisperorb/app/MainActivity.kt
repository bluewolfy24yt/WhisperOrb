package com.whisperorb.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var lifecycleRefreshTick by mutableIntStateOf(0)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        lifecycleRefreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Pré-chargement automatique du modèle sélectionné s'il est déjà présent sur l'appareil
        val engine = WhisperAndroidEngine.getInstance(this)
        val downloader = ModelDownloader.getInstance(this)
        if (downloader.isModelDownloaded(engine.selectedModel)) {
            engine.loadModel(engine.selectedModel)
        } else {
            val firstReady = WhisperModelTier.entries.firstOrNull {
                downloader.isModelDownloaded(it) &&
                    HardwareDetector.isTierUnlocked(this, it, engine.simulateModest4GBDevice, engine.forceUnlockAllModels)
            }
            if (firstReady != null) {
                engine.selectedModel = firstReady
                engine.loadModel(firstReady)
            }
        }

        setContent {
            WhisperOrbTheme {
                WhisperOrbContentView(lifecycleRefreshTick = lifecycleRefreshTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleRefreshTick++
        WhisperOrbAccessibilityService.requestRefresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lifecycleRefreshTick++
            WhisperOrbAccessibilityService.requestRefresh()
        }
    }
}

// Palette exacte de WhisperOrbApp/ContentView.swift (Apple iOS / iPadOS)
private val BgPageColor = Color(0xFF09090B)
private val CardBgColor = Color(0xFF131317)
private val InnerBoxColor = Color(0xFF0D0D11)
private val AppleBlue = Color(0xFF0A84FF)
private val AppleGreen = Color(0xFF30D158)
private val AppleOrange = Color(0xFFFF9F0A)
private val AppleRed = Color(0xFFFF453A)
private val AppleYellow = Color(0xFFFFD60A)

@Composable
fun WhisperOrbTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = AppleBlue,
        secondary = AppleGreen,
        background = BgPageColor,
        surface = CardBgColor,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun WhisperOrbContentView(lifecycleRefreshTick: Int = 0) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 680 || HardwareDetector.isTabletOrLargeScreen(context)

    val engine = remember { WhisperAndroidEngine.getInstance(context) }
    val downloader = remember { ModelDownloader.getInstance(context) }
    val styleMemory = remember { UserStyleMemoryManager.getInstance(context) }

    var uiTick by remember { mutableIntStateOf(0) }
    var colorSearchText by remember { mutableStateOf("") }
    var showCopiedToast by remember { mutableStateOf(false) }
    var showRepoCopiedToast by remember { mutableStateOf(false) }
    var showJSONCopiedToast by remember { mutableStateOf(false) }
    var newSpokenPhrase by remember { mutableStateOf("") }
    var newWrittenReplacement by remember { mutableStateOf("") }
    var showRawJSONPreview by remember { mutableStateOf(false) }
    var easterEggStepIndex by remember { mutableStateOf<Int?>(null) }
    var showCustomColorDialogForBg by remember { mutableStateOf<Boolean?>(null) }
    var numericTestValue by remember { mutableStateOf("") }

    DisposableEffect(engine, downloader) {
        val listener: () -> Unit = { uiTick++ }
        engine.addListener(listener)
        downloader.onStateChanged = { uiTick++ }
        onDispose {
            engine.removeListener(listener)
        }
    }

    // Rafraîchissement périodique léger pour détecter l'activation/sélection du clavier ou de la bulle
    LaunchedEffect(Unit) {
        while (true) {
            delay(900L)
            uiTick++
        }
    }

    // Lecture réactive liée à uiTick et lifecycleRefreshTick
    val currentTick = uiTick + lifecycleRefreshTick
    val isKeyboardEnabled = remember(currentTick) {
        WhisperAndroidEngine.isWhisperOrbKeyboardEnabled(context)
    }
    val isKeyboardSelected = remember(currentTick) {
        WhisperAndroidEngine.isWhisperOrbKeyboardSelected(context)
    }
    val isAccessibilityServiceActive = remember(currentTick) {
        WhisperOrbAccessibilityService.isServiceEnabled(context)
    }
    val isHeaderReady = (isKeyboardEnabled && isKeyboardSelected && engine.isGboardBarEnabled) ||
        (isAccessibilityServiceActive && engine.isFloatingBubbleEnabled)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPageColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isLargeScreen) 24.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SoberAppHeaderSection(
                context = context,
                isServiceActive = isHeaderReady
            )

            if (isLargeScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Card1VoiceStudioSection(
                            context = context,
                            engine = engine,
                            isKeyboardEnabled = isKeyboardEnabled,
                            isKeyboardSelected = isKeyboardSelected,
                            isAccessibilityServiceActive = isAccessibilityServiceActive,
                            showCopiedToast = showCopiedToast,
                            onShowCopiedToast = { showCopiedToast = it },
                            numericTestValue = numericTestValue,
                            onNumericTestValueChange = { numericTestValue = it },
                            onForceRefresh = { uiTick++ }
                        )
                        CardStyleMemorySection(
                            context = context,
                            engine = engine,
                            styleMemory = styleMemory,
                            newSpokenPhrase = newSpokenPhrase,
                            onNewSpokenChange = { newSpokenPhrase = it },
                            newWrittenReplacement = newWrittenReplacement,
                            onNewWrittenChange = { newWrittenReplacement = it },
                            showRawJSONPreview = showRawJSONPreview,
                            onToggleRawJSON = { showRawJSONPreview = !showRawJSONPreview },
                            showJSONCopiedToast = showJSONCopiedToast,
                            onShowJSONCopied = { showJSONCopiedToast = it },
                            onForceRefresh = { uiTick++ }
                        )
                        Card4PreferencesAndVoiceCommands(
                            engine = engine,
                            onForceRefresh = { uiTick++ }
                        )
                        Card5GitHubAndSetupSection(
                            context = context,
                            isServiceActive = isKeyboardEnabled && isKeyboardSelected,
                            showRepoCopiedToast = showRepoCopiedToast,
                            onShowRepoCopied = { showRepoCopiedToast = it }
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Card2ModelsSection(
                            context = context,
                            engine = engine,
                            downloader = downloader,
                            onTriggerEasterEgg = { easterEggStepIndex = 0 },
                            onForceRefresh = { uiTick++ }
                        )
                        Card3AppearanceStudioSection(
                            engine = engine,
                            colorSearchText = colorSearchText,
                            onColorSearchChange = { colorSearchText = it },
                            onOpenCustomPicker = { isBg -> showCustomColorDialogForBg = isBg },
                            onForceRefresh = { uiTick++ }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Card1VoiceStudioSection(
                        context = context,
                        engine = engine,
                        isKeyboardEnabled = isKeyboardEnabled,
                        isKeyboardSelected = isKeyboardSelected,
                        isAccessibilityServiceActive = isAccessibilityServiceActive,
                        showCopiedToast = showCopiedToast,
                        onShowCopiedToast = { showCopiedToast = it },
                        numericTestValue = numericTestValue,
                        onNumericTestValueChange = { numericTestValue = it },
                        onForceRefresh = { uiTick++ }
                    )
                    Card2ModelsSection(
                        context = context,
                        engine = engine,
                        downloader = downloader,
                        onTriggerEasterEgg = { easterEggStepIndex = 0 },
                        onForceRefresh = { uiTick++ }
                    )
                    CardStyleMemorySection(
                        context = context,
                        engine = engine,
                        styleMemory = styleMemory,
                        newSpokenPhrase = newSpokenPhrase,
                        onNewSpokenChange = { newSpokenPhrase = it },
                        newWrittenReplacement = newWrittenReplacement,
                        onNewWrittenChange = { newWrittenReplacement = it },
                        showRawJSONPreview = showRawJSONPreview,
                        onToggleRawJSON = { showRawJSONPreview = !showRawJSONPreview },
                        showJSONCopiedToast = showJSONCopiedToast,
                        onShowJSONCopied = { showJSONCopiedToast = it },
                        onForceRefresh = { uiTick++ }
                    )
                    Card3AppearanceStudioSection(
                        engine = engine,
                        colorSearchText = colorSearchText,
                        onColorSearchChange = { colorSearchText = it },
                        onOpenCustomPicker = { isBg -> showCustomColorDialogForBg = isBg },
                        onForceRefresh = { uiTick++ }
                    )
                    Card4PreferencesAndVoiceCommands(
                        engine = engine,
                        onForceRefresh = { uiTick++ }
                    )
                    Card5GitHubAndSetupSection(
                        context = context,
                        isServiceActive = isKeyboardEnabled && isKeyboardSelected,
                        showRepoCopiedToast = showRepoCopiedToast,
                        onShowRepoCopied = { showRepoCopiedToast = it }
                    )
                }
            }
        }

        // Modal Easter Egg des 7 messages d'avertissement progressifs (FR & EN)
        easterEggStepIndex?.let { stepIdx ->
            EasterEggWarningModalOverlay(
                stepIndex = stepIdx,
                isEnglish = (engine.selectedLanguage.effectiveStyleLanguageCode == "en"),
                onCancel = { easterEggStepIndex = null },
                onConfirmStep = {
                    triggerHaptic(context)
                    val allSteps = EasterEggUnlockStep.allSteps
                    if (stepIdx + 1 < allSteps.size) {
                        easterEggStepIndex = stepIdx + 1
                    } else {
                        engine.forceUnlockAllModels = true
                        easterEggStepIndex = null
                        uiTick++
                    }
                }
            )
        }

        // Sélecteur de couleur personnalisée (Cœur de l'orbe / Onde lumineuse)
        showCustomColorDialogForBg?.let { isBgTarget ->
            CustomColorPickerDialog(
                title = if (isBgTarget) "Couleur de fond personnalisée" else "Couleur d'onde personnalisée",
                initialHex = if (isBgTarget) engine.bgHex else engine.fgHex,
                onDismiss = { showCustomColorDialogForBg = null },
                onColorSelected = { hex ->
                    if (isBgTarget) {
                        engine.bgHex = hex
                    } else {
                        engine.fgHex = hex
                    }
                    showCustomColorDialogForBg = null
                    uiTick++
                }
            )
        }
    }
}

// MARK: - En-tête Sobre & Grand Public (v2.0 • ON-DEVICE AI)

@Composable
private fun SoberAppHeaderSection(
    context: Context,
    isServiceActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "WhisperOrb",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "v2.0 • ON-DEVICE AI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
            Text(
                text = HardwareDetector.getTelemetrySummary(context),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isServiceActive) AppleGreen else AppleOrange)
            )
            Text(
                text = if (isServiceActive) "Actif" else "En veille",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// MARK: - Carte 1 : Studio Vocal, Clavier Gboard Intégré & Bandeau Vocal Incrusté

@Composable
private fun Card1VoiceStudioSection(
    context: Context,
    engine: WhisperAndroidEngine,
    isKeyboardEnabled: Boolean,
    isKeyboardSelected: Boolean,
    isAccessibilityServiceActive: Boolean,
    showCopiedToast: Boolean,
    onShowCopiedToast: (Boolean) -> Unit,
    numericTestValue: String,
    onNumericTestValueChange: (String) -> Unit,
    onForceRefresh: () -> Unit
) {
    var langMenuExpanded by remember { mutableStateOf(false) }
    var floatingBubbleSectionExpanded by remember { mutableStateOf(engine.isFloatingBubbleEnabled) }

    AppleCardSurface {
        CardSectionHeader(
            iconText = "〰",
            title = "Studio Vocal & Clavier Gboard Intégré",
            subtitle = "Clavier Gboard complet avec le bandeau WhisperOrb physiquement soudé au sommet"
        )

        // Scène 190dp de l'Orbe 3D 120 Hz (#0D0D11)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(InnerBoxColor)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AndroidView(
                    modifier = Modifier.size(118.dp),
                    factory = { ctx ->
                        GeminiOrbCanvasView(ctx).apply {
                            setOnClickListener {
                                triggerHaptic(ctx)
                                engine.toggleRecording()
                                onForceRefresh()
                            }
                        }
                    },
                    update = { view ->
                        view.bgHex = engine.bgHex
                        view.fgHex = engine.fgHex
                        view.audioLevel = engine.currentAudioLevel
                        view.orbState = engine.orbState
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusDotColor = when (engine.orbState) {
                        WhisperOrbState.IDLE -> AppleGreen
                        WhisperOrbState.LISTENING -> AppleBlue
                        WhisperOrbState.TRANSCRIBING -> AppleYellow
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = "${engine.orbState.statusTitle} • ${engine.selectedLanguage.shortPillLabel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Sélecteur intelligent de langue multilingue
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐 Langue de dictée Whisper",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.80f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Code ISO : ${engine.selectedLanguage.resolvedWhisperCode.uppercase(Locale.ROOT)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = AppleBlue
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InnerBoxColor)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .clickable { langMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${engine.selectedLanguage.flagEmoji} ${engine.selectedLanguage.displayName}",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "▾",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                DropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFF1C1C22))
                ) {
                    WhisperLanguageSelection.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${lang.flagEmoji} ${lang.displayName}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                engine.selectedLanguage = lang
                                langMenuExpanded = false
                                onForceRefresh()
                            }
                        )
                    }
                }
            }
        }

        // Tableau de bord en direct : Badges de statut Clavier WhisperOrb Gboard & Bandeau Vocal Incrusté
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(InnerBoxColor)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge 1 : Clavier WhisperOrb Gboard
            val keyboardBadgeText = when {
                isKeyboardEnabled && isKeyboardSelected -> "Activé & Sélectionné ✓"
                isKeyboardEnabled -> "Activé (non sélectionné)"
                else -> "À activer"
            }
            val keyboardBadgeColor = when {
                isKeyboardEnabled && isKeyboardSelected -> AppleGreen
                isKeyboardEnabled -> AppleOrange
                else -> AppleRed
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⌨️ Clavier WhisperOrb Gboard",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(keyboardBadgeColor.copy(alpha = 0.16f))
                        .border(0.8.dp, keyboardBadgeColor.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = keyboardBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = keyboardBadgeColor
                    )
                }
            }

            // Badge 2 : Bandeau Vocal Incrusté
            val barBadgeText = if (engine.isGboardBarEnabled) "ACTIF en haut du clavier" else "MASQUÉ"
            val barBadgeColor = if (engine.isGboardBarEnabled) AppleGreen else Color.White.copy(alpha = 0.50f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎙️ Bandeau Vocal Incrusté",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (engine.isGboardBarEnabled) AppleGreen.copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            0.8.dp,
                            if (engine.isGboardBarEnabled) AppleGreen.copy(alpha = 0.45f)
                            else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = barBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = barBadgeColor
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            // Bouton 1 : 1. Activer le Clavier WhisperOrb Gboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (!isKeyboardEnabled) AppleBlue
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isKeyboardEnabled) AppleGreen.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        triggerHaptic(context)
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                        }
                        onForceRefresh()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isKeyboardEnabled) "✓" else "1",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isKeyboardEnabled) AppleGreen else Color.White
                )
                Text(
                    text = "1. Activer le Clavier WhisperOrb Gboard",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "↗",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            // Bouton 2 : 2. Choisir WhisperOrb Gboard comme clavier actif
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isKeyboardEnabled && !isKeyboardSelected) AppleBlue
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isKeyboardSelected) AppleGreen.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        triggerHaptic(context)
                        try {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        } catch (_: Exception) {
                        }
                        onForceRefresh()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isKeyboardSelected) "✓" else "2",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isKeyboardSelected) AppleGreen else Color.White
                )
                Text(
                    text = "2. Choisir WhisperOrb Gboard comme clavier actif",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "⌨️",
                    fontSize = 13.sp
                )
            }
        }

        // Switch 0 : Bandeau WhisperOrb incrusté au-dessus du Gboard (sans redémarrer)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bandeau WhisperOrb incrusté au-dessus du Gboard",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = engine.isGboardBarEnabled,
                onCheckedChange = { enabled ->
                    triggerHaptic(context)
                    engine.isGboardBarEnabled = enabled
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleGreen
                )
            )
        }

        // Switch 1 : Mode clavier réduit (Barre vocale seule)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mode clavier réduit (Barre vocale seule)",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = engine.isKeyboardCollapsed,
                onCheckedChange = {
                    engine.isKeyboardCollapsed = it
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleBlue
                )
            )
        }

        // Switch 2 : Correcteur automatique IA & mémoire de style
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Correcteur automatique IA & adaptation du style",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = engine.isSmartAutocorrectEnabled,
                onCheckedChange = {
                    engine.isSmartAutocorrectEnabled = it
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleGreen
                )
            )
        }

        // Bouton principal pour Afficher / Masquer immédiatement le Bandeau WhisperOrb incrusté au-dessus du Gboard
        val isBarCurrentlyActive = engine.isGboardBarEnabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isBarCurrentlyActive) Color(0xFF1C1C1E) else AppleBlue)
                .border(
                    width = 1.dp,
                    color = if (isBarCurrentlyActive) AppleGreen.copy(alpha = 0.55f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    triggerHaptic(context)
                    engine.isGboardBarEnabled = !engine.isGboardBarEnabled
                    onForceRefresh()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isBarCurrentlyActive) "🎙️" else "⌨️",
                fontSize = 18.sp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isBarCurrentlyActive) {
                        "Bandeau WhisperOrb incrusté : ACTIF (Masquer)"
                    } else {
                        "Bandeau WhisperOrb incrusté : MASQUÉ (Afficher)"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = if (isBarCurrentlyActive) {
                        engine.statusBannerMessage
                    } else {
                        "Touchez pour réafficher immédiatement le bandeau en haut du clavier Gboard"
                    },
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }

        // Bouton de personnalisation complète du clavier Gboard / HeliBoard intégré
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InnerBoxColor)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable {
                    triggerHaptic(context)
                    try {
                        context.startActivity(
                            Intent().setClassName(
                                context.packageName,
                                "helium314.keyboard.settings.SettingsActivity"
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {
                    }
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚙ Personnaliser le clavier Gboard (Swipe, Toucher, Disposition)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppleBlue
            )
        }

        // Section secondaire repliable : 🫧 Bulle Flottante Universelle (Optionnel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InnerBoxColor)
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { floatingBubbleSectionExpanded = !floatingBubbleSectionExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "🫧 Bulle Flottante Universelle (Optionnel)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                    Text(
                        text = "Orbe flottant 56dp aimanté aux bords de l'écran en plus du clavier",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }

                Switch(
                    checked = engine.isFloatingBubbleEnabled && isAccessibilityServiceActive,
                    onCheckedChange = { enabled ->
                        triggerHaptic(context)
                        engine.isFloatingBubbleEnabled = enabled
                        if (enabled) {
                            floatingBubbleSectionExpanded = true
                            if (!isAccessibilityServiceActive) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                        WhisperOrbAccessibilityService.requestRefresh()
                        onForceRefresh()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppleBlue
                    )
                )
            }

            AnimatedVisibility(visible = floatingBubbleSectionExpanded || engine.isFloatingBubbleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isAccessibilityServiceActive) Color.White.copy(alpha = 0.05f)
                            else AppleBlue.copy(alpha = 0.20f)
                        )
                        .clickable {
                            triggerHaptic(context)
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isAccessibilityServiceActive) "✓" else "⚙️",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityServiceActive) AppleGreen else AppleBlue
                    )
                    Text(
                        text = if (isAccessibilityServiceActive) {
                            "Service d'Accessibilité actif • La bulle flottante est prête"
                        } else {
                            "Autoriser le Service d'Accessibilité pour afficher la bulle flottante"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Message d'erreur éventuel
        engine.lastErrorMessage?.let { errMsg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚠️", fontSize = 13.sp)
                Text(
                    text = errMsg,
                    fontSize = 12.sp,
                    color = AppleOrange
                )
            }
        }

        // Zone d'Aperçu de dictée en direct + Test Gboard
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aperçu de dictée (Touchez pour ouvrir Gboard + WhisperOrb)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                )
                if (engine.lastTranscriptionLatencyMs > 0) {
                    Text(
                        text = "${engine.lastTranscriptionLatencyMs} ms • C++ NDK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = AppleGreen
                    )
                }
            }

            OutlinedTextField(
                value = engine.liveTestTranscript,
                onValueChange = {
                    engine.liveTestTranscript = it
                    onForceRefresh()
                },
                placeholder = {
                    Text(
                        text = "Touchez ici pour afficher votre clavier WhisperOrb Gboard avec le bandeau vocal incrusté au sommet...",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                },
                textStyle = TextStyle(fontSize = 15.sp, color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 145.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InnerBoxColor,
                    unfocusedContainerColor = InnerBoxColor,
                    focusedBorderColor = AppleBlue.copy(alpha = 0.65f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                )
            )

            // Champ de test Clavier de Chiffres (vérifie le masquage automatique sur les champs numériques)
            OutlinedTextField(
                value = numericTestValue,
                onValueChange = onNumericTestValueChange,
                placeholder = {
                    Text(
                        text = "🔢 Tester un champ numérique (masque automatiquement l'Orbe)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.42f)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InnerBoxColor,
                    unfocusedContainerColor = InnerBoxColor,
                    focusedBorderColor = AppleOrange.copy(alpha = 0.65f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallActionChip(
                    label = if (showCopiedToast) "✓ Copié" else "📋 Copier",
                    textColor = if (showCopiedToast) AppleGreen else Color.White
                ) {
                    copyToClipboard(context, engine.liveTestTranscript)
                    onShowCopiedToast(true)
                }

                SmallActionChip(
                    label = "↶ Annuler phrase",
                    textColor = Color.White
                ) {
                    val lastLen = engine.lastInsertedSentenceLength
                    if (lastLen > 0 && engine.liveTestTranscript.length >= lastLen) {
                        engine.liveTestTranscript = engine.liveTestTranscript.dropLast(lastLen)
                        engine.lastInsertedSentenceLength = 0
                    } else {
                        engine.liveTestTranscript = ""
                    }
                    onForceRefresh()
                }

                Spacer(modifier = Modifier.weight(1f))

                SmallActionChip(
                    label = "Effacer",
                    textColor = Color.White.copy(alpha = 0.75f)
                ) {
                    engine.liveTestTranscript = ""
                    onForceRefresh()
                }
            }
        }
    }

    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            delay(2000L)
            onShowCopiedToast(false)
        }
    }
}

// MARK: - Carte 2 : Catalogue des 7 Modèles Whisper + Easter Egg 7 Messages

@Composable
private fun Card2ModelsSection(
    context: Context,
    engine: WhisperAndroidEngine,
    downloader: ModelDownloader,
    onTriggerEasterEgg: () -> Unit,
    onForceRefresh: () -> Unit
) {
    val unlockedCount = HardwareDetector.unlockedTiers(
        context = context,
        simulate4GB = engine.simulateModest4GBDevice,
        forceUnlock = engine.forceUnlockAllModels
    ).size
    val isOnline = downloader.isOnline()

    AppleCardSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CardSectionHeader(
                    iconText = "🧠",
                    title = "Catalogue des 7 Modèles Whisper",
                    subtitle = "C++ NDK + ARM NEON • $unlockedCount/7 modèles débloqués sur votre appareil"
                )
            }
            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppleOrange.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Hors-Ligne",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleOrange
                    )
                }
            }
        }

        // Switch : Simuler un téléphone modeste (4 Go RAM) -> débloque jusqu'à Whisper Expert (v3 Turbo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InnerBoxColor)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "🧪 Simuler un téléphone modeste (4 Go RAM)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Débloque jusqu'à Whisper Expert (v3 Turbo) et verrouille Expert+ & TITAN",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Switch(
                checked = engine.simulateModest4GBDevice,
                onCheckedChange = {
                    engine.simulateModest4GBDevice = it
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleOrange
                )
            )
        }

        // Ligne Easter Egg : Débloquer tous les modèles quoi qu'il arrive (7 messages)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InnerBoxColor)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (engine.forceUnlockAllModels) {
                        "🔓 Tous les modèles sont débloqués de force !"
                    } else {
                        "🔒 Débloquer tous les modèles quoi qu'il arrive"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (engine.forceUnlockAllModels) AppleGreen else Color.White
                )
                Text(
                    text = if (engine.forceUnlockAllModels) {
                        "Sécurité matérielle RAM désactivée par l'utilisateur"
                    } else {
                        "Outrepasser la sécurité RAM (à vos risques et périls)"
                    },
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (engine.forceUnlockAllModels) {
                            Color.White.copy(alpha = 0.10f)
                        } else {
                            AppleOrange.copy(alpha = 0.22f)
                        }
                    )
                    .clickable {
                        triggerHaptic(context)
                        if (engine.forceUnlockAllModels) {
                            engine.forceUnlockAllModels = false
                            onForceRefresh()
                        } else {
                            onTriggerEasterEgg()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (engine.forceUnlockAllModels) "Reverrouiller" else "Débloquer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (engine.forceUnlockAllModels) Color.White else AppleOrange
                )
            }
        }

        downloader.errorMessage?.let { dlErr ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚠️", fontSize = 13.sp)
                Text(text = dlErr, fontSize = 12.sp, color = AppleRed)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhisperModelTier.entries.forEach { tier ->
                ModelTierCardView(
                    context = context,
                    tier = tier,
                    engine = engine,
                    downloader = downloader,
                    onTriggerEasterEgg = onTriggerEasterEgg,
                    onForceRefresh = onForceRefresh
                )
            }
        }
    }
}

@Composable
private fun ModelTierCardView(
    context: Context,
    tier: WhisperModelTier,
    engine: WhisperAndroidEngine,
    downloader: ModelDownloader,
    onTriggerEasterEgg: () -> Unit,
    onForceRefresh: () -> Unit
) {
    val isUnlocked = HardwareDetector.isTierUnlocked(
        context = context,
        tier = tier,
        simulate4GB = engine.simulateModest4GBDevice,
        forceUnlock = engine.forceUnlockAllModels
    )
    val isSelected = (engine.selectedModel == tier)
    val isDownloaded = downloader.isModelDownloaded(tier)
    val isDownloading = downloader.isDownloading(tier)
    val progress = downloader.downloadProgress[tier] ?: 0f
    val isRecommended = (HardwareDetector.recommendedTier(context, engine.simulateModest4GBDevice) == tier)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InnerBoxColor)
            .border(
                width = 1.dp,
                color = if (isSelected && isUnlocked) AppleBlue.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tier.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                    if (isRecommended && isUnlocked) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppleBlue.copy(alpha = 0.22f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Recommandé",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleBlue
                            )
                        }
                    }
                }
                Text(
                    text = tier.subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = if (isUnlocked) 0.60f else 0.38f)
                )
            }

            if (!isUnlocked) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppleOrange.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🔒 ${tier.requiredRAMBadgeText}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleOrange
                    )
                }
            } else if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Téléchargé",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleGreen
                    )
                }
            }
        }

        // Ligne de télémétrie Disque / RAM / Latence
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val specColor = Color.White.copy(alpha = if (isUnlocked) 0.55f else 0.35f)
            Text(
                text = "💾 ${tier.sizeMB} Mo",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = specColor
            )
            Text(
                text = "🧠 ${tier.ramUsageMB} Mo RAM",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = specColor
            )
            Text(
                text = "⚡ ${tier.latencyDescription}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = specColor
            )
        }

        if (isDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = AppleBlue,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Téléchargement : ${(progress * 100f).roundToInt()} %",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Annuler",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleRed,
                        modifier = Modifier.clickable {
                            downloader.cancelDownload(tier)
                            onForceRefresh()
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isUnlocked) {
                Text(
                    text = "🔒 Verrouillé par sécurité matérielle (${tier.requiredRAMBadgeText}) • Forcer le déblocage ?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleOrange,
                    modifier = Modifier.clickable {
                        triggerHaptic(context)
                        onTriggerEasterEgg()
                    }
                )
            } else if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AppleBlue else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            triggerHaptic(context)
                            engine.selectedModel = tier
                            engine.loadModel(tier)
                            onForceRefresh()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isSelected) "✓ Modèle actif" else "○ Utiliser ce modèle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            triggerHaptic(context)
                            if (engine.selectedModel == tier) {
                                engine.unloadCurrentModel()
                            }
                            downloader.deleteModel(tier)
                            onForceRefresh()
                        }
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "🗑",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            } else if (!isDownloading) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .clickable {
                            triggerHaptic(context)
                            downloader.downloadModel(tier) { finished ->
                                engine.selectedModel = finished
                                engine.loadModel(finished)
                                onForceRefresh()
                            }
                            onForceRefresh()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "⬇ Télécharger (${tier.sizeMB} Mo)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// MARK: - Carte 3 : Mémoire d'Écriture JSON (user_style_memory.json)

@Composable
private fun CardStyleMemorySection(
    context: Context,
    engine: WhisperAndroidEngine,
    styleMemory: UserStyleMemoryManager,
    newSpokenPhrase: String,
    onNewSpokenChange: (String) -> Unit,
    newWrittenReplacement: String,
    onNewWrittenChange: (String) -> Unit,
    showRawJSONPreview: Boolean,
    onToggleRawJSON: () -> Unit,
    showJSONCopiedToast: Boolean,
    onShowJSONCopied: (Boolean) -> Unit,
    onForceRefresh: () -> Unit
) {
    val activeLangCode = engine.selectedLanguage.effectiveStyleLanguageCode
    val entries = styleMemory.getReplacementsForLanguage(activeLangCode)

    AppleCardSurface {
        CardSectionHeader(
            iconText = "✍️",
            title = "Mémoire d'Écriture (user_style_memory.json)",
            subtitle = "Adapte les abréviations et la ponctuation à votre façon d'écrire (${activeLangCode.uppercase(Locale.ROOT)})"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activer le profil user_style_memory.json",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = styleMemory.isMemoryEnabled,
                onCheckedChange = {
                    styleMemory.isMemoryEnabled = it
                    styleMemory.saveToDisk()
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleGreen
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Retirer le point final sur les petits messages",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Évite le '.' automatique sur les SMS de ≤ ${styleMemory.shortMessageMaxWords} mots",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Switch(
                checked = styleMemory.stripTrailingPeriodOnShortMessages,
                onCheckedChange = {
                    styleMemory.stripTrailingPeriodOnShortMessages = it
                    styleMemory.saveToDisk()
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleBlue
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Abréviations personnelles (${activeLangCode.uppercase(Locale.ROOT)})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newSpokenPhrase,
                    onValueChange = onNewSpokenChange,
                    placeholder = {
                        Text(
                            text = "Mot dicté (ex: t'inquiète)",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InnerBoxColor,
                        unfocusedContainerColor = InnerBoxColor,
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                    )
                )

                Text(text = "→", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)

                OutlinedTextField(
                    value = newWrittenReplacement,
                    onValueChange = onNewWrittenChange,
                    placeholder = {
                        Text(
                            text = "Écrit (ex: tkt)",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                    modifier = Modifier.width(115.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InnerBoxColor,
                        unfocusedContainerColor = InnerBoxColor,
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                    )
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppleBlue)
                        .clickable {
                            if (newSpokenPhrase.isNotBlank() && newWrittenReplacement.isNotBlank()) {
                                styleMemory.setReplacement(newSpokenPhrase, newWrittenReplacement, activeLangCode)
                                onNewSpokenChange("")
                                onNewWrittenChange("")
                                onForceRefresh()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entries.forEach { (spoken, written) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(InnerBoxColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "« $spoken » → $written",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Text(
                            text = "✕",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.clickable {
                                styleMemory.removeReplacement(spoken, activeLangCode)
                                onForceRefresh()
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallActionChip(
                label = if (showRawJSONPreview) "{ } Masquer JSON" else "{ } Voir JSON",
                textColor = Color.White
            ) {
                onToggleRawJSON()
            }

            SmallActionChip(
                label = if (showJSONCopiedToast) "✓ JSON copié" else "📋 Exporter JSON",
                textColor = if (showJSONCopiedToast) AppleGreen else Color.White
            ) {
                copyToClipboard(context, styleMemory.prettyJSONString())
                onShowJSONCopied(true)
            }

            Spacer(modifier = Modifier.weight(1f))

            SmallActionChip(
                label = "Réinitialiser",
                textColor = Color.White.copy(alpha = 0.65f)
            ) {
                styleMemory.resetToDefaults()
                onForceRefresh()
            }
        }

        AnimatedVisibility(visible = showRawJSONPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InnerBoxColor)
                    .padding(10.dp)
            ) {
                Text(
                    text = styleMemory.prettyJSONString(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleGreen
                )
            }
        }
    }

    LaunchedEffect(showJSONCopiedToast) {
        if (showJSONCopiedToast) {
            delay(2000L)
            onShowJSONCopied(false)
        }
    }
}

// MARK: - Carte 4 : Apparence de l'Orbe (32 Couleurs, Éclipse, Perle, ColorPickers & Recherche)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Card3AppearanceStudioSection(
    engine: WhisperAndroidEngine,
    colorSearchText: String,
    onColorSearchChange: (String) -> Unit,
    onOpenCustomPicker: (Boolean) -> Unit,
    onForceRefresh: () -> Unit
) {
    AppleCardSurface {
        CardSectionHeader(
            iconText = "🎨",
            title = "Apparence de l'Orbe",
            subtitle = "Couleur de l'onde, fond de la sphère et thèmes spéciaux"
        )

        // 1. Fond de la sphère (5 presets + ColorPicker personnalisé)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Fond de la sphère",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.70f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeColors.orderedBackgrounds.forEach { preset ->
                    val isCurrentBg = engine.bgHex.equals(preset.hex, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = if (isCurrentBg) 0.16f else 0.05f))
                            .clickable {
                                engine.bgHex = preset.hex
                                onForceRefresh()
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(ThemeColors.composeColor(preset.hex))
                                .border(0.8.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                        )
                        Text(
                            text = preset.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCustomPicker(true) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Couleur de fond personnalisée (${engine.bgHex.uppercase(Locale.ROOT)})",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ThemeColors.composeColor(engine.bgHex))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // 2. Couleur de l'onde (16 presets en grille + Éclipse & Perle + ColorPicker personnalisé)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Couleur de l'onde",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.70f)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeColors.orderedForegrounds.forEach { preset ->
                    val isCurrentFg = engine.fgHex.equals(preset.hex, ignoreCase = true)
                    Column(
                        modifier = Modifier
                            .width(54.dp)
                            .clickable {
                                engine.fgHex = preset.hex
                                onForceRefresh()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(ThemeColors.composeColor(preset.hex))
                                .border(
                                    width = if (isCurrentFg) 2.dp else 0.8.dp,
                                    color = if (isCurrentFg) Color.White else Color.White.copy(alpha = 0.20f),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = preset.name.substringBefore(" "),
                            fontSize = 10.sp,
                            color = if (isCurrentFg) Color.White else Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Boutons côte-à-côte Éclipse (Noir sur Noir) & Perle (Blanc sur Blanc)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(InnerBoxColor)
                        .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                        .clickable {
                            engine.bgHex = "#050507"
                            engine.fgHex = "#050507"
                            onForceRefresh()
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Éclipse (Noir sur Noir)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFF8F9FA))
                        .clickable {
                            engine.bgHex = "#F8F9FA"
                            engine.fgHex = "#FFFFFF"
                            onForceRefresh()
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Perle (Blanc sur Blanc)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1C1C1E)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCustomPicker(false) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Couleur d'onde personnalisée (${engine.fgHex.uppercase(Locale.ROOT)})",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ThemeColors.composeColor(engine.fgHex))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // 3. Barre de recherche intelligente de couleur (140+ noms FR/EN ou code Hex)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = colorSearchText,
                onValueChange = onColorSearchChange,
                placeholder = {
                    Text(
                        text = "🔍 Rechercher une couleur (ex: turquoise, or, #FF0055)...",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InnerBoxColor,
                    unfocusedContainerColor = InnerBoxColor,
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                )
            )

            val parsedHex = ThemeColors.parseColorInput(colorSearchText)
            if (parsedHex != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(ThemeColors.composeColor(parsedHex))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )

                    Text(
                        text = "${ThemeColors.displayName(parsedHex)} ($parsedHex)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(AppleBlue)
                            .clickable {
                                engine.fgHex = parsedHex
                                onForceRefresh()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Onde",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                engine.bgHex = parsedHex
                                onForceRefresh()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Fond",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Carte 5 : Préférences & Commandes Vocales

@Composable
private fun Card4PreferencesAndVoiceCommands(
    engine: WhisperAndroidEngine,
    onForceRefresh: () -> Unit
) {
    var sliderVal by remember(engine.vadSilenceThresholdSeconds) {
        mutableFloatStateOf(engine.vadSilenceThresholdSeconds)
    }

    AppleCardSurface {
        CardSectionHeader(
            iconText = "⚙️",
            title = "Préférences & Commandes Vocales",
            subtitle = "Ponctuation automatique, détection de fin de phrase et commandes"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Espace automatique après chaque phrase",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Sépare proprement les phrases dictées à la suite",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Switch(
                checked = engine.autoSpaceAfterSentence,
                onCheckedChange = {
                    engine.autoSpaceAfterSentence = it
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleGreen
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Majuscule et ponctuation automatiques",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Ajoute majuscules et points en fin de phrase",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Switch(
                checked = engine.autoPunctuation,
                onCheckedChange = {
                    engine.autoPunctuation = it
                    onForceRefresh()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleBlue
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Délai de pause automatique (VAD)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.US, "%.2f s", sliderVal),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }

            Slider(
                value = sliderVal,
                onValueChange = {
                    sliderVal = it
                    engine.vadSilenceThresholdSeconds = it
                },
                valueRange = 0.45f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AppleBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Commandes vocales disponibles (FR / EN)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.70f)
            )
            VoiceCommandRow(spoken = "« À la ligne » / « New line »", result = "Saut de ligne")
            VoiceCommandRow(spoken = "« Effacer la dernière phrase » / « Delete last sentence »", result = "Annule la dictée")
            VoiceCommandRow(spoken = "« Point d'interrogation » / « Question mark »", result = "Insère ?")
            VoiceCommandRow(spoken = "« Point d'exclamation » / « Exclamation mark »", result = "Insère !")
        }
    }
}

@Composable
private fun VoiceCommandRow(spoken: String, result: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InnerBoxColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = spoken,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = result,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

// MARK: - Carte 6 : Partage GitHub, QR Code & Guide d'Incrustation Gboard 1-2-3-4

@Composable
private fun Card5GitHubAndSetupSection(
    context: Context,
    isServiceActive: Boolean,
    showRepoCopiedToast: Boolean,
    onShowRepoCopied: (Boolean) -> Unit
) {
    val githubRepoURL = "https://github.com/bluewolfy24yt/WhisperOrb"
    val githubReleasesURL = "https://github.com/bluewolfy24yt/WhisperOrb/releases/latest"
    val qrBitmap = remember { generateQrCodeBitmap(githubReleasesURL, 320) }

    AppleCardSurface {
        CardSectionHeader(
            iconText = "📱",
            title = "Partage & Installation (v2.0.0)",
            subtitle = "Disponible pour Apple (iPhone, iPad, Mac) et Android (.apk avec Gboard intégré)"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code GitHub Releases",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Releases GitHub v2.0.0 (.ipa & .apk)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = githubRepoURL,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 2
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionChip(
                        label = if (showRepoCopiedToast) "✓ Copié" else "🔗 Copier",
                        textColor = if (showRepoCopiedToast) AppleGreen else Color.White
                    ) {
                        copyToClipboard(context, githubRepoURL)
                        onShowRepoCopied(true)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppleBlue)
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(githubRepoURL)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "GitHub ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // Guide 1-2-3-4 du Clavier Gboard intégré & Bandeau Vocal
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Guide d'activation du Clavier Gboard Intégré en 4 étapes",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.80f)
            )
            SetupStepItem(
                num = "1",
                text = "Touchez « 1. Activer le Clavier WhisperOrb Gboard » dans la Carte 1 ci-dessus."
            )
            SetupStepItem(
                num = "2",
                text = if (isServiceActive) {
                    "✓ Clavier WhisperOrb Gboard sélectionné ! Le bandeau vocal est soudé au sommet du clavier."
                } else {
                    "Touchez « 2. Choisir WhisperOrb Gboard comme clavier actif » pour l'utiliser partout."
                }
            )
            SetupStepItem(
                num = "3",
                text = "Touchez « ⚙ » sur le bandeau pour ouvrir les réglages rapides WhisperOrb ou personnalisez Gboard."
            )
            SetupStepItem(
                num = "4",
                text = "Touchez « ▾ Réduire » pour replier les touches et dicter avec le bandeau vocal seul !"
            )
        }
    }

    LaunchedEffect(showRepoCopiedToast) {
        if (showRepoCopiedToast) {
            delay(2000L)
            onShowRepoCopied(false)
        }
    }
}

@Composable
private fun SetupStepItem(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InnerBoxColor)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(AppleBlue.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppleBlue
            )
        }
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}

// MARK: - Modal Easter Egg des 7 Messages d'Avertissement (Parité 1:1 avec ContentView.swift)

@Composable
private fun EasterEggWarningModalOverlay(
    stepIndex: Int,
    isEnglish: Boolean,
    onCancel: () -> Unit,
    onConfirmStep: () -> Unit
) {
    val allSteps = EasterEggUnlockStep.allSteps
    val safeIdx = stepIndex.coerceIn(0, allSteps.lastIndex)
    val step = allSteps[safeIdx]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF16161C))
                .border(1.5.dp, AppleOrange.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.localizedTitle(isEnglish),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (safeIdx == 6) AppleGreen else AppleOrange,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${safeIdx + 1}/7",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }

            Text(
                text = step.localizedMessage(isEnglish),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.90f),
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = step.localizedCancel(isEnglish),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onConfirmStep,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (safeIdx == 6) AppleGreen else AppleRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = step.localizedConfirm(isEnglish),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// MARK: - Sélecteur de Couleur Personnalisée RGB / Hex (Équivalent SwiftUI ColorPicker)

@Composable
private fun CustomColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val (initR, initG, initB) = ThemeColors.rgbComponents(initialHex)
    var r by remember { mutableFloatStateOf(initR.toFloat()) }
    var g by remember { mutableFloatStateOf(initG.toFloat()) }
    var b by remember { mutableFloatStateOf(initB.toFloat()) }

    val currentHex = String.format(
        Locale.US,
        "#%02X%02X%02X",
        (r * 255f).roundToInt().coerceIn(0, 255),
        (g * 255f).roundToInt().coerceIn(0, 255),
        (b * 255f).roundToInt().coerceIn(0, 255)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF16161C),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ThemeColors.composeColor(currentHex))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Text(
                        text = currentHex,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                ColorChannelSlider("Rouge (R)", r, AppleRed) { r = it }
                ColorChannelSlider("Vert (G)", g, AppleGreen) { g = it }
                ColorChannelSlider("Bleu (B)", b, AppleBlue) { b = it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f))
                    ) {
                        Text("Annuler", color = Color.White)
                    }
                    Button(
                        onClick = { onColorSelected(currentHex) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                    ) {
                        Text("Appliquer", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    tint: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text(
                text = "${(value * 255f).roundToInt()}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = tint,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

// MARK: - Composants Visuels Partagés (Surface #131317, En-tête de Carte, Boutons, QR Code)

@Composable
private fun AppleCardSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBgColor)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

@Composable
private fun CardSectionHeader(iconText: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconText, fontSize = 15.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("WhisperOrb", text))
}

private fun triggerHaptic(context: Context) {
    try {
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(14L)
        }
    } catch (_: Exception) {
    }
}

private fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    } catch (_: Exception) {
        null
    }
}
