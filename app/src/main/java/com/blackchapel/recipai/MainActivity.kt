package com.blackchapel.recipai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.blackchapel.recipai.haptics.Haptics
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.haptics.NoComposeHaptics
import com.blackchapel.recipai.ui.RecipaiApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = viewModel.app
            val view = LocalView.current
            val haptics = remember(view) { Haptics(view) { app.settings.value.haptics } }
            CompositionLocalProvider(
                LocalHaptics provides haptics,
                // Compose's built-in haptics (long press, text handles) would
                // bypass the in-app switch; every haptic goes through Haptics.
                LocalHapticFeedback provides NoComposeHaptics,
            ) {
                RecipaiApp(app, BuildConfig.VERSION_NAME)
            }
        }
    }
}
