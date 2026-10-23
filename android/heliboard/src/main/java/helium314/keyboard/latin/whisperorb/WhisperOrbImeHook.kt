package helium314.keyboard.latin.whisperorb

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout

interface WhisperOrbImeHook {
    fun onInputViewCreated(
        ime: InputMethodService,
        inputView: View,
        headerSlot: FrameLayout,
        panelSlot: FrameLayout,
        stripContainer: View,
        keyboardWrapper: View
    )
    fun onStartInputView(ime: InputMethodService, info: EditorInfo?, restarting: Boolean)
    fun onFinishInputView(ime: InputMethodService, finishingInput: Boolean)
    fun isKeyboardCollapsed(): Boolean
    fun isSettingsPanelOpen(): Boolean
}

object WhisperOrbImeRegistry {
    @JvmStatic
    @Volatile
    var hook: WhisperOrbImeHook? = null
}
