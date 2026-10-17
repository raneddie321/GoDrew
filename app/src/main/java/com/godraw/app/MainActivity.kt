package com.godraw.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.ui.navigation.GoDrawNavHost
import com.godraw.app.ui.theme.GoDrawTheme
import com.godraw.core.entitlement.Tier

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val tier by container.entitlements.tier.collectAsStateWithLifecycle()
            // The custom accent color is a Max feature; it is ignored on other plans.
            val accent = settings.accentColor?.takeIf { tier == Tier.MAX }?.let { Color(it) }
            GoDrawTheme(mode = settings.themeMode, accent = accent) {
                GoDrawNavHost(container)
            }
        }
    }
}
