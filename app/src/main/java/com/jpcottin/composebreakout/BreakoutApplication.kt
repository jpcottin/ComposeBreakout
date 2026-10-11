package com.jpcottin.composebreakout

import android.app.Application
import androidx.compose.ui.ComposeUiFlags

class BreakoutApplication : Application() {
    override fun onCreate() {
        // Compose reads this flag once per composition root, so it has to be set
        // before any Activity calls setContent. It provides LocalUiMediaScope, which
        // backs the mediaQuery { } calls in DeviceTraits.kt.
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        super.onCreate()
    }
}
