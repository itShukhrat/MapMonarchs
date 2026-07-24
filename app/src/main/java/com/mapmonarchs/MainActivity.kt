package com.mapmonarchs

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mapbox.common.MapboxOptions
import com.mapmonarchs.data.local.AppLocale
import com.mapmonarchs.feature.shell.AppShell
import com.mapmonarchs.ui.theme.MapMonarchsTheme

class MainActivity : ComponentActivity() {

    // Below API 33 the chosen language is applied by wrapping the base context
    // here; on 33+ this is a no-op and the framework LocaleManager does it.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Public pk.* token, injected from local.properties via BuildConfig.
        MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN

        enableEdgeToEdge()
        setContent {
            MapMonarchsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell()
                }
            }
        }
    }
}