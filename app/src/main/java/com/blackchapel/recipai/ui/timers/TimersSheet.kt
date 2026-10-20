package com.blackchapel.recipai.ui.timers

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.CookTimer
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSheet
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * Every timer (Sheet.Timers): ring, countdown and what it is for, with
 * pause / resume, +1 min and cancel (dismiss when done). Closes itself
 * when the last timer goes.
 */
@Composable
fun TimersSheet(app: AppController) {
    val store = app.timers
    val board by store.board.collectAsState()
    val now by store.now.collectAsState()
    val openedEmpty = remember { board.isEmpty }
    if (openedEmpty) {
        // Opened with nothing to show (the mini timer and the bottom bar
        // only offer it while timers exist).
        LaunchedEffect(Unit) { app.sheet = null }
        return
    }
    RSheet(onDismiss = { app.sheet = null }, title = "Timers") { closer ->
        // The last timer was cancelled or dismissed: slide the sheet away.
        val empty = board.isEmpty
        LaunchedEffect(empty) { if (empty) closer.close() }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            board.timers.forEach { t ->
                key(t.id) {
                    TimerRow(
                        t,
                        now,
                        onToggle = { store.toggle(t.id) },
                        onAddMinute = { store.addMinute(t.id) },
                        onRemove = { store.remove(t.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerRow(t: CookTimer, now: Long, onToggle: () -> Unit, onAddMinute: () -> Unit, onRemove: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimerRing(t.fractionLeft(now), t.done, 46.dp, track = c.surface3, iconTint = c.ink)
        Column(Modifier.weight(1f)) {
            Text(t.clock(now), style = clockStyle(22f), color = if (t.done) c.basil else c.ink, maxLines = 1)
            Text(
                t.label,
                style = RecipaiType.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!t.done) {
                ActionButton(if (t.running) "Pause timer" else "Resume timer", HapticType.SELECTION, onToggle) {
                    RIcon(if (t.running) Ico.Pause else Ico.Play, size = 15.dp, tint = c.ink)
                }
            }
            ActionButton("Add one minute", HapticType.SELECTION, onAddMinute) {
                Text("+1m", style = clockStyle(11f), color = c.ink, maxLines = 1)
            }
            ActionButton(if (t.done) "Dismiss timer" else "Cancel timer", HapticType.LIGHT, onRemove) {
                RIcon(Ico.X, size = 15.dp, tint = c.ink)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, haptic: HapticType, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .tap(pressedScale = 0.88f, haptic = haptic, description = label, onClick = onClick)
            .size(38.dp)
            .background(LocalColors.current.surface, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
