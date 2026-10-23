package com.whisperorb.app

import helium314.keyboard.latin.App
import helium314.keyboard.latin.whisperorb.WhisperOrbImeRegistry

class WhisperOrbApplication : App() {
    override fun onCreate() {
        super.onCreate()
        WhisperOrbImeRegistry.hook = WhisperOrbEmbeddedBarController.getInstance(this)
    }
}
