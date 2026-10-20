package com.blackchapel.recipai.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.BackupText
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Cell
import com.blackchapel.recipai.ui.components.Group
import com.blackchapel.recipai.ui.components.GroupLabel
import com.blackchapel.recipai.ui.components.Hairline
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/** Search's "Back up now" action subtitle (as in the prototype). */
fun backUpNowSubtitle(app: AppController): String {
    val status = app.backup.status
    val last = status.lastBackupAt
    return when {
        !status.connected -> "Connect Google Drive first"
        last != null -> "Last backup ${BackupText.ago(last, System.currentTimeMillis())}"
        else -> "Save a copy to Google Drive"
    }
}

/**
 * The Backup group at the top of Settings: the Google Drive row (status,
 * opens the Backup page) and, once connected, Back up now.
 */
@Composable
fun BackupSettingsSection(app: AppController) {
    val c = LocalColors.current
    val backup = app.backup
    val status = backup.status
    val run = backup.run
    val now = rememberNow()
    Column(Modifier.padding(top = 14.dp)) {
        GroupLabel("Backup")
        Group {
            Cell(
                "Google Drive",
                icon = Ico.Cloud,
                iconColor = c.ocean,
                subtitle = BackupText.settingsSubtitle(
                    connected = status.connected,
                    needsReconnect = status.needsReconnect,
                    runningLabel = run?.label,
                    runningFraction = run?.fraction ?: 0f,
                    lastBackupAt = status.lastBackupAt,
                    unsaved = backup.unsaved,
                    nowMillis = now,
                ),
                onClick = { app.openBackup() },
            ) { RIcon(Ico.Right, tint = c.faint) }
            if (status.connected) {
                Hairline(start = 56.dp)
                Cell(
                    "Back up now",
                    icon = Ico.CloudUp,
                    iconColor = c.basil,
                    haptic = if (backup.working) null else HapticType.LIGHT,
                    onClick = { backup.backUpNow() },
                ) {
                    if (run != null) Text("${(run.fraction * 100).toInt()}%", style = RecipaiType.mono, color = c.mute)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
