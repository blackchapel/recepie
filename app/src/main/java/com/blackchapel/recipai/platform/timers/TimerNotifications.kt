package com.blackchapel.recipai.platform.timers

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blackchapel.recipai.MainActivity
import com.blackchapel.recipai.R
import com.blackchapel.recipai.core.CookTimer
import com.blackchapel.recipai.core.TimerBoard
import java.util.Date

/**
 * The two timer notifications: the silent ongoing one the foreground
 * service shows while timers run (a live countdown via the system
 * chronometer, so it needs no per-second updates), and a heads-up
 * "Time's up" per finished timer, vibrating with its channel's pattern.
 * The chime is played by the app ([TimerChime]) so the Timer sound setting
 * controls it; the "finished" channel itself is silent.
 */
internal object TimerNotifications {
    const val ONGOING_ID = 4101
    private const val CHANNEL_RUNNING = "timers_running"
    private const val CHANNEL_DONE = "timers_done"
    private const val DONE_TAG = "timer_done"
    private const val PAPRIKA = 0xFFFF6B3D.toInt()

    private val posted = HashSet<String>()

    fun ensureChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Running timers")
                .setDescription("The countdown while you cook")
                .setShowBadge(false)
                .build(),
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_DONE, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Finished timers")
                .setDescription("Tells you when a timer is done")
                .setSound(null, null)
                .setVibrationEnabled(true)
                .setVibrationPattern(longArrayOf(0, 400, 200, 400, 200, 600))
                .build(),
        )
    }

    /** What the ongoing notification shows; it is re-posted only when this changes. */
    fun key(board: TimerBoard, now: Long): Any {
        val t = lead(board, now)
        return listOf(t?.id, t?.endsAt, t?.leftMs, board.timers.count { !it.done })
    }

    fun ongoing(context: Context, board: TimerBoard, now: Long): Notification {
        val t = lead(board, now)
        val others = board.timers.count { !it.done } - 1
        val more = if (others > 0) " · $others more ${if (others == 1) "timer" else "timers"}" else ""
        val b = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(PAPRIKA)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val endsAt = t?.endsAt
        when {
            t == null -> b.setContentTitle("Timers").setShowWhen(false)
            endsAt != null -> b
                .setContentTitle(t.label)
                .setContentText("Ends at ${DateFormat.getTimeFormat(context).format(Date(endsAt))}$more")
                .setWhen(endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            else -> b
                .setContentTitle(t.label)
                .setContentText("Paused at ${t.clock(now)}$more")
                .setShowWhen(false)
        }
        return b.build()
    }

    fun updateOngoing(context: Context, board: TimerBoard, now: Long) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(ONGOING_ID, ongoing(context, board, now))
            }
        } catch (e: SecurityException) {
            // Permission revoked meanwhile: the countdown is still in the app.
        }
    }

    fun postFinished(context: Context, timer: CookTimer) {
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(PAPRIKA)
            .setContentTitle("Time’s up")
            .setContentText(timer.label)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(DONE_TAG, idFor(timer.id), n)
                synchronized(posted) { posted.add(timer.id) }
            }
        } catch (e: SecurityException) {
            // No permission: the in-app alert and the chime still fire.
        }
    }

    /** Clears "Time's up" notifications whose timer is no longer done in the app. */
    fun cancelFinishedExcept(context: Context, doneIds: Set<String>) {
        val gone = synchronized(posted) { posted.filter { it !in doneIds }.also { posted.removeAll(it.toSet()) } }
        if (gone.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        gone.forEach { nm.cancel(DONE_TAG, idFor(it)) }
    }

    /** The timer the ongoing notification is about: the running one ending first, else a paused one. */
    private fun lead(board: TimerBoard, now: Long): CookTimer? =
        board.timers.filter { it.running }.minByOrNull { it.remainingMs(now) }
            ?: board.timers.firstOrNull { it.paused }

    private fun idFor(timerId: String): Int = timerId.hashCode()

    /** Brings the app back as it was (like the launcher icon). */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
