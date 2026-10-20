package com.blackchapel.recipai

import android.content.Intent
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
        // A fresh launch from the share sheet; rotation re-creates with the
        // same intent, so only handle it the first time.
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        // Each time the app is opened: look for a newer GitHub release in the
        // background (silent; Settings shows a card only if there is one).
        viewModel.app.checkForUpdate(BuildConfig.VERSION_NAME)
    }

    /**
     * launchMode is singleTask, so a share from another app and a tap on a
     * timer notification both arrive here, at the one MainActivity, instead
     * of starting a second one next to it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Text or a link shared to Recipai from another app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        // Opening Recipai's task from Recents re-delivers the intent that
        // first started it, which can be a share that was handled long ago:
        // it would open the import sheet again.
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        // Not getStringExtra: styled text arrives as a Spanned CharSequence,
        // which getStringExtra silently turns into null.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
        viewModel.app.handleSharedText(text)
    }
}
