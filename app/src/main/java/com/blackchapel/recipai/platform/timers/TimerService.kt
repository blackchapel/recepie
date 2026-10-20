package com.blackchapel.recipai.platform.timers

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blackchapel.recipai.core.TimerBoard
import com.blackchapel.recipai.ui.timers.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps step timers counting with the screen off or the app in the
 * background: a foreground service (type specialUse, subtype "cooking
 * timers") with the ongoing countdown notification. It runs while any
 * timer is running or paused, finishes timers on time (holding a partial
 * wake lock while one runs, so the CPU doesn't sleep through the end) and
 * stops itself when none is left.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var lastStartId = 0
    private var shownKey: Any? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val store: TimerStore get() = TimerStore.shared

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerAlerts.install(applicationContext, store)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // After startForegroundService() the service must call
        // startForeground() promptly, even if it is about to stop.
        if (!goForeground()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { run() }
        // Timers live in memory: after the process dies there is nothing to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        val board = store.board.value
        val now = store.currentTime()
        val notification = TimerNotifications.ongoing(this, board, now)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    TimerNotifications.ONGOING_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(TimerNotifications.ONGOING_ID, notification)
            }
            shownKey = TimerNotifications.key(board, now)
            true
        } catch (e: Exception) {
            // E.g. not allowed from the background: the in-app ticker still
            // runs the timers while the app is open.
            false
        }
    }

    private suspend fun run() {
        while (true) {
            store.tick()
            val board = store.board.value
            if (!board.hasActive) break
            val now = store.currentTime()
            val key = TimerNotifications.key(board, now)
            if (key != shownKey) {
                TimerNotifications.updateOngoing(this, board, now)
                shownKey = key
            }
            holdWakeLock(board)
            // Wake up for the next finish, at least once a second, or as
            // soon as the board changes (pause, cancel, +1 min, new timer).
            val wait = board.nextDeadline()?.let { (it - now).coerceIn(50L, 1_000L) } ?: 1_000L
            withTimeoutOrNull(wait) { store.board.first { it != board } }
        }
        loop = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    private fun holdWakeLock(board: TimerBoard) {
        if (!board.hasRunning) {
            releaseWakeLock()
            return
        }
        val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Recipai:timers")
            .also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
        // Re-armed on every pass; the timeout only matters if this loop stops.
        lock.acquire(WAKE_LOCK_TIMEOUT)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L

        /** Starts (or refreshes) the service. Safe to call repeatedly. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses starts from the background; timers
                // only start from a tap in the app, so this is rare.
            }
        }
    }
}
