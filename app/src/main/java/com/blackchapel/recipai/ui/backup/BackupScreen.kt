package com.blackchapel.recipai.ui.backup

import com.blackchapel.recipai.ui.timers.pushedPageBottomPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.BackupEngine
import com.blackchapel.recipai.core.BackupEntry
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupKind
import com.blackchapel.recipai.core.BackupText
import com.blackchapel.recipai.core.BackupTiming
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.BackupSchedule
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.BackupPage
import com.blackchapel.recipai.ui.components.ActionSheet
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.LargeTitle
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSheet
import com.blackchapel.recipai.ui.components.RSwitch
import com.blackchapel.recipai.ui.components.SheetAction
import com.blackchapel.recipai.ui.components.TopBar
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import java.time.ZoneId
import java.time.ZonedDateTime

/** The Google Drive backup page, pushed from Settings (docs/DESIGN.md, "Backup"). */
@Composable
fun BackupScreen(app: AppController, page: BackupPage) {
    val c = LocalColors.current
    val backup = app.backup
    val settings by app.settings.collectAsState()
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4 } }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() } } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    // Fresh list each time the page opens (the cached one shows meanwhile).
    LaunchedEffect(backup.status.connected) { backup.refresh() }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = top + NavBarHeight, bottom = bottom + pushedPageBottomPadding(app, 28.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("title") { LargeTitle("Google Drive", "Backup") }
            item("body") {
                Column(
                    Modifier
                        .padding(horizontal = Gutter)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when {
                        backup.connecting -> Waiting(onCancel = backup::cancelConnect)
                        !backup.status.connected -> NotConnected(app)
                        else -> Connected(
                            app,
                            settings,
                            onDisconnect = { confirmDisconnect = true },
                            onRestore = { restoreId = it.id },
                            onPickTime = { pickTime = true },
                        )
                    }
                }
            }
        }
        TopBar(title = "Backup", scrolled = scrolled, collapsed = collapsed, left = {
            IconButton(Ico.Left, "Back", { app.back() }, tint = c.accentInk, iconSize = 24.dp, haptic = null)
        })
    }

    if (confirmDisconnect) {
        DisconnectSheet(onDismiss = { confirmDisconnect = false }, onDisconnect = { backup.disconnect() })
    }
    val restoring = restoreId?.let { id -> backup.status.history.firstOrNull { it.id == id } }
    if (restoring != null) {
        RestoreSheet(restoring, onDismiss = { restoreId = null }, onRestore = { backup.restore(restoring) })
    } else if (restoreId != null) {
        LaunchedEffect(restoreId) { restoreId = null }
    }
    if (pickTime) {
        ActionSheet(
            onDismiss = { pickTime = false },
            title = "Time",
            actions = BackupTiming.TIMES.map { (hour, _) ->
                val icon = when {
                    hour == settings.backupHour -> Ico.Check
                    hour < 6 || hour >= 18 -> Ico.Moon
                    else -> Ico.Sun
                }
                SheetAction(BackupText.timeOfDay(hour), icon) { backup.setHour(hour) }
            },
        )
    }
}

/** Waiting for Google's own sign-in window. */
@Composable
private fun Waiting(onCancel: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spinner()
        Text("Waiting for Google sign-in…", style = RecipaiType.sheetTitle, color = c.ink, textAlign = TextAlign.Center)
        Text(
            "Google’s own window opens so you can pick an account and allow access.",
            style = RecipaiType.bodySmall.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
            color = c.mute,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        RButton("Cancel", onCancel)
    }
}

@Composable
private fun NotConnected(app: AppController) {
    val c = LocalColors.current
    val recipes by app.repo.recipes.collectAsState()
    val backup = app.backup
    BackupHero(recipes)
    Text("Keep your recipes safe in Google Drive", style = RecipaiType.section.copy(fontSize = 23.sp, lineHeight = 26.sp), color = c.ink)
    Text(
        "Recipai saves a copy of your recipes, photos and tags to your own Google Drive. If you switch phones, you can bring everything back.",
        style = RecipaiType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
        color = c.body,
        modifier = Modifier.padding(top = 0.dp),
    )
    Card {
        Point(Ico.Folder, "Saved in a “${BackupEngine.FOLDER_NAME}” folder", " in your Drive, using your Drive storage.")
        RowDivider(0.dp)
        Point(Ico.Shield, "Private to you.", " Recipai can open only the files it creates, not the rest of your Drive.")
        RowDivider(0.dp)
        Point(Ico.Calendar, "On your schedule.", " Back up daily, weekly, or only when you tap Back up now.")
    }
    backup.connectError?.let { Note(Ico.Info, it, c.danger) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RButton(
            "Connect Google account",
            onClick = { app.backup.connect() },
            icon = Ico.Cloud,
            style = ButtonStyle.CTA,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Google’s own window opens so you can choose the account. Recipai asks only for access to the files it creates.",
            style = RecipaiType.bodySmall,
            color = c.mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Point(icon: Ico, bold: String, rest: String) {
    val c = LocalColors.current
    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RIcon(icon, tint = c.accentInk, modifier = Modifier.padding(top = 1.dp))
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold))
                append(bold)
                pop()
                append(rest)
            },
            style = RecipaiType.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = c.mute,
        )
    }
}

@Composable
private fun Connected(
    app: AppController,
    settings: AppSettings,
    onDisconnect: () -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onPickTime: () -> Unit,
) {
    val c = LocalColors.current
    val backup = app.backup
    val status = backup.status
    val now = rememberNow()

    // Account.
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = status.accountName?.takeIf { it.isNotBlank() } ?: status.accountEmail ?: "Google account"
        Avatar(name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = RecipaiType.ui, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            status.accountEmail?.let {
                Text(it, style = RecipaiType.mono.copy(fontSize = 12.5.sp), color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status.needsReconnect) StatusPill("Sign in again", Ico.Info, c.saffron) else StatusPill("Connected", Ico.Check, c.basil)
        }
        RButton("Disconnect", onDisconnect, style = ButtonStyle.DANGER, small = true, enabled = !backup.working)
    }

    if (status.needsReconnect) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.saffron.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                .border(1.dp, c.saffron.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Google needs you to sign in again before Recipai can back up. Your backups in Drive are safe.",
                style = RecipaiType.bodySmall.copy(fontSize = 14.sp),
                color = c.body,
            )
            RButton("Sign in again", { backup.connect() }, icon = Ico.Cloud, style = ButtonStyle.CTA, modifier = Modifier.fillMaxWidth())
            backup.connectError?.let { Note(Ico.Info, it, c.danger) }
        }
    }

    BackupNowCard(app, settings, now)

    // Automatic backups.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecLabel("Automatic backups")
        Card {
            val options = listOf(
                Triple(BackupSchedule.DAILY, "Daily", "Every day, while you sleep"),
                Triple(BackupSchedule.WEEKLY, "Weekly", "Once a week on a day you pick"),
                Triple(BackupSchedule.MANUAL, "Only when I tap", "Back up now whenever you like"),
            )
            options.forEachIndexed { i, (schedule, title, sub) ->
                if (i > 0) RowDivider(50.dp)
                RadioRow(title, sub, settings.backupSchedule == schedule) { backup.setSchedule(schedule) }
            }
        }
        AnimatedVisibility(
            settings.backupSchedule == BackupSchedule.WEEKLY,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Day of the week", style = RecipaiType.uiSmall.copy(fontSize = 12.5.sp), color = c.mute)
                    DayPicker(settings.backupWeekday, BackupTiming.WEEK, BackupText::weekdayShort) { backup.setWeekday(it) }
                }
            }
        }
        if (settings.backupSchedule != BackupSchedule.MANUAL) {
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time", style = RecipaiType.uiSmall.copy(fontSize = 12.5.sp), color = c.mute)
                    SelectPill(BackupText.timeOfDay(settings.backupHour), "Change the backup time", onPickTime)
                }
            }
            val zone = ZoneId.systemDefault()
            val nowZoned = BackupText.zoned(now, zone)
            val next = BackupTiming.nextRun(nowZoned, settings.backupSchedule.toFrequency(), settings.backupWeekday, settings.backupHour)
            if (next != null) {
                Note(
                    Ico.Clock,
                    "Next backup ",
                    c.mute,
                    Modifier.padding(horizontal = 14.dp),
                    bold = BackupText.whenText(next, nowZoned),
                    after = if (settings.backupWifiOnly) " on Wi-Fi" else "",
                )
            }
        } else {
            Note(Ico.CloudUp, "Recipai backs up only when you tap ", c.mute, Modifier.padding(horizontal = 14.dp), bold = "Back up now", after = ".")
        }
    }

    // Options.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecLabel("Options")
        Card {
            ToggleRow("Include photos", "Photos make backups larger.", settings.backupIncludePhotos) { backup.toggleIncludePhotos() }
            RowDivider(14.dp)
            ToggleRow("Back up on Wi-Fi only", "Automatic backups wait for Wi-Fi to save mobile data.", settings.backupWifiOnly) { backup.toggleWifiOnly() }
        }
    }

    // Backups in Drive.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val history = status.history
        SecLabel("Backups in your Drive", count = history.size)
        when {
            history.isNotEmpty() -> {
                Card {
                    history.forEachIndexed { i, entry ->
                        if (i > 0) RowDivider(56.dp)
                        HistoryRow(entry, now, enabled = !backup.working) { onRestore(entry) }
                    }
                }
                Text(
                    "Recipai keeps your ${BackupEngine.KEEP} most recent backups and removes older ones from the folder.",
                    style = RecipaiType.bodySmall,
                    color = c.mute,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            backup.listing -> Note(Ico.Cloud, "Looking for backups in your Drive…", c.mute, Modifier.padding(horizontal = 14.dp))
            backup.listError == null -> {
                Text(
                    buildAnnotatedString {
                        append("No backups yet. Tap ")
                        pushStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold))
                        append("Back up now")
                        pop()
                        append(" to make your first one.")
                    },
                    style = RecipaiType.bodySmall.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
                    color = c.mute,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, c.lineStrong, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                )
            }
        }
        backup.listError?.let { err ->
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Note(Ico.Info, "Couldn't load your backups. $err", c.danger, Modifier.weight(1f))
                RButton("Retry", { backup.refresh() }, small = true)
            }
        }
    }
}

/** Last backup, Back up now with its progress bar, and what changed since. */
@Composable
private fun BackupNowCard(app: AppController, settings: AppSettings, now: Long) {
    val c = LocalColors.current
    val backup = app.backup
    val status = backup.status
    val run = backup.run
    val recipes by app.repo.recipes.collectAsState()
    var estimate by remember { mutableStateOf<BackupEstimate?>(null) }
    LaunchedEffect(recipes, settings.backupIncludePhotos, status.lastBackupAt) { estimate = backup.estimate(recipes, settings) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LAST BACKUP", style = RecipaiType.label, color = c.faint)
            val last = status.lastBackupAt
            val title = when {
                run != null -> "${run.label}…"
                last != null -> BackupText.whenText(BackupText.zoned(last, ZoneId.systemDefault()), BackupText.zoned(now, ZoneId.systemDefault()))
                else -> "Not backed up yet"
            }
            Text(title, style = RecipaiType.secTitle.copy(fontSize = 20.sp, lineHeight = 24.sp), color = c.ink)
            val e = estimate
            val photos = if (!settings.backupIncludePhotos) "photos not included" else BackupText.plural(e?.photos ?: 0, "photo")
            val size = e?.takeIf { it.bytes > 0 }?.let { " · about ${BackupText.bytes(it.bytes)}" } ?: ""
            Text("${BackupText.plural(recipes.size, "recipe")} · $photos$size", style = RecipaiType.mono, color = c.mute)
        }
        RButton(
            if (run != null) "${(run.fraction * 100).toInt()}%" else "Back up now",
            onClick = { if (!backup.working) backup.backUpNow() },
            icon = Ico.CloudUp,
            style = ButtonStyle.FLAME,
            height = 52.dp,
            haptic = if (backup.working) null else HapticType.LIGHT,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(run != null, enter = fadeIn(), exit = fadeOut()) {
            ProgressBar(run?.fraction ?: 1f)
        }
        val error = status.lastError
        val failedLast = error != null && (status.lastErrorAt ?: 0L) >= (status.lastBackupAt ?: 0L)
        when {
            run != null -> Unit
            failedLast -> Note(Ico.Info, "The last backup didn't finish. $error", c.danger)
            backup.unsaved -> Note(Ico.Spark, "You’ve changed recipes since the last backup.", c.accentInk)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(pressedScale = 0.99f, haptic = HapticType.SELECTION, role = Role.Switch, isChecked = on, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RecipaiType.ui, color = c.ink)
            Text(subtitle, style = RecipaiType.bodySmall.copy(fontSize = 12.5.sp), color = c.mute)
        }
        RSwitch(on)
    }
}

@Composable
private fun HistoryRow(entry: BackupEntry, now: Long, enabled: Boolean, onRestore: () -> Unit) {
    val c = LocalColors.current
    val zone = ZoneId.systemDefault()
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = when (entry.kind) {
            BackupKind.BEFORE_RESTORE -> Ico.Shield
            BackupKind.AUTOMATIC -> Ico.Clock
            else -> Ico.CloudUp
        }
        Box(Modifier.size(30.dp).background(c.surface2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            RIcon(icon, size = 15.dp, tint = c.mute)
        }
        Column(Modifier.weight(1f)) {
            Text(
                BackupText.whenText(BackupText.zoned(entry.createdAt, zone), BackupText.zoned(now, zone)),
                style = RecipaiType.ui.copy(fontSize = 14.5.sp),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(BackupText.entryDetail(entry), style = RecipaiType.monoSmall, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RButton("Restore", onRestore, icon = Ico.Reset, small = true, enabled = enabled)
    }
}

@Composable
private fun DisconnectSheet(onDismiss: () -> Unit, onDisconnect: () -> Unit) {
    val c = LocalColors.current
    RSheet(onDismiss) { closer ->
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)) {
            Text("Disconnect Google Drive?", style = RecipaiType.sheetTitle.copy(fontSize = 21.sp), color = c.ink)
            VSpace(8.dp)
            Text(
                "Automatic backups stop. Backups already in your Drive stay there, and you can reconnect any time to restore them.",
                style = RecipaiType.body,
                color = c.mute,
            )
            VSpace(20.dp)
            SolidDangerButton("Disconnect") { closer.close(onDisconnect) }
            VSpace(8.dp)
            RButton("Stay connected", { closer.close() }, Modifier.fillMaxWidth(), height = 50.dp)
        }
    }
}

@Composable
private fun RestoreSheet(entry: BackupEntry, onDismiss: () -> Unit, onRestore: () -> Unit) {
    val c = LocalColors.current
    val zone = ZoneId.systemDefault()
    val whenText = BackupText.whenText(BackupText.zoned(entry.createdAt, zone), ZonedDateTime.now(zone))
    RSheet(onDismiss) { closer ->
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)) {
            Box(Modifier.size(48.dp).background(c.ocean.copy(alpha = 0.16f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                RIcon(Ico.Reset, size = 24.dp, tint = c.ocean)
            }
            VSpace(14.dp)
            Text(
                "Restore the backup from ${BackupText.inSentence(whenText)}?",
                style = RecipaiType.sheetTitle.copy(fontSize = 21.sp),
                color = c.ink,
            )
            VSpace(8.dp)
            val count = entry.recipes?.let { "the ${BackupText.plural(it, "recipe")} in this backup" } ?: "the recipes in this backup"
            Text(
                "Your recipe box is replaced with $count${if (entry.includesPhotos) "" else ", without photos"}. " +
                    "Recipai backs up what you have now first, so you can undo this from the list.",
                style = RecipaiType.body,
                color = c.mute,
            )
            VSpace(20.dp)
            RButton(
                "Restore backup",
                { closer.close(onRestore) },
                Modifier.fillMaxWidth(),
                icon = Ico.Reset,
                style = ButtonStyle.CTA,
                height = 50.dp,
                haptic = HapticType.MEDIUM,
            )
            VSpace(8.dp)
            RButton("Cancel", { closer.close() }, Modifier.fillMaxWidth(), height = 50.dp)
        }
    }
}
