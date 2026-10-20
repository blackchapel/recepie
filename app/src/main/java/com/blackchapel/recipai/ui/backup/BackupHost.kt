package com.blackchapel.recipai.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.backup.BackupConsentHost
import com.blackchapel.recipai.ui.AppController

/**
 * Always composed at the app root: plays the backup's haptics (a backup
 * finishing while on another screen) and shows Google's sign-in screen
 * whenever a backup asks for it, wherever it was started from.
 */
@Composable
fun BackupHost(app: AppController) {
    val haptics = LocalHaptics.current
    val event = app.backup.haptic
    LaunchedEffect(event) {
        if (event != null) {
            haptics.perform(event.type)
            app.backup.hapticPlayed(event)
        }
    }
    BackupConsentHost(app.backup)
}
