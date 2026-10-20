package com.blackchapel.recipai.platform.timers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.blackchapel.recipai.ui.timers.TimerStore

// The Android side of the step timers, used by the shared UI. A stub with
// the same API is in tools/desktop-check/stubs (TimerSystemStub.kt).

/**
 * Hooks [store] up to Android while the activity is around: installs the
 * finish alert (chime, notification), starts the foreground service when a
 * timer is running or paused, asks for the notification permission the
 * first time (Android 13+), and clears "Time's up" notifications whose
 * timer was dismissed or restarted in the app.
 */
@Composable
fun TimerSystem(store: TimerStore) {
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(store) { TimerAlerts.install(appContext, store) }
    val board by store.board.collectAsState()
    val active = board.hasActive
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // The service's notification stayed hidden until now: post it again.
        if (granted && store.board.value.hasActive) TimerService.start(appContext)
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        TimerService.start(appContext)
        if (NotificationPermission.shouldAsk(appContext)) {
            NotificationPermission.markAsked(appContext)
            runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    val doneIds = board.timers.filter { it.done }.mapTo(HashSet()) { it.id }
    LaunchedEffect(doneIds) { TimerNotifications.cancelFinishedExcept(appContext, doneIds) }
}

/** Keeps the screen on while composed with [enabled] (cook mode). */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) view.keepScreenOn = true
        onDispose { if (enabled) view.keepScreenOn = false }
    }
}

/** POST_NOTIFICATIONS (Android 13+), asked once: the first time a timer starts. */
internal object NotificationPermission {
    private const val PREFS = "timers"
    private const val ASKED = "asked_notification_permission"

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun shouldAsk(context: Context): Boolean =
        !granted(context) && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
    }
}

/** What happens when a timer finishes, whoever noticed it (screen or service). */
internal object TimerAlerts {
    fun install(context: Context, store: TimerStore) {
        val app = context.applicationContext
        TimerNotifications.ensureChannels(app)
        store.onFinish = { timer ->
            TimerNotifications.postFinished(app, timer)
            if (store.sound) TimerChime.play()
        }
    }
}
