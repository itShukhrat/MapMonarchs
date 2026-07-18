package com.mapmonarchs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mapbox.common.MapboxOptions
import com.mapmonarchs.feature.map.MapScreen
import com.mapmonarchs.ui.theme.MapMonarchsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Public pk.* token, injected from local.properties via BuildConfig.
        MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN

        enableEdgeToEdge()
        setContent {
            MapMonarchsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen()
                }
            }
        }
    }
}