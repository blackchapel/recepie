package com.blackchapel.recipai.ui.components

import com.blackchapel.recipai.ui.photos.RecipeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.haptics.NoComposeHaptics
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lets sheet content close the sheet with its slide-down animation. */
@OptIn(ExperimentalMaterial3Api::class)
class SheetCloser(
    private val state: SheetState,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit,
) {
    /** Animates the sheet away, then runs [then] (after [onDismiss]). */
    fun close(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
}

/**
 * The app's bottom sheet: 28dp top corners, a grabber, an optional title
 * row with a close button. Drag down or tap the scrim to dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(SheetCloser) -> Unit,
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss = {
        haptics.perform(HapticType.LIGHT)
        onDismiss()
    }
    val closer = SheetCloser(state, scope, onDismiss)
    ModalBottomSheet(
        onDismissRequest = dismiss,
        // The app draws behind the system bars. A tall sheet (the import sheet
        // fills the window) must stop below the status bar, so its grabber,
        // title and close button stay reachable. Padding on the sheet's own
        // modifier shrinks the height the sheet can use.
        modifier = modifier.statusBarsPadding(),
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.surface,
        contentColor = c.body,
        scrimColor = c.scrim,
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(5.dp).background(c.lineStrong, CircleShape))
            }
        },
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = RecipaiType.sheetTitle, color = c.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(Ico.X, "Close", { closer.close() }, size = 32.dp, iconSize = 16.dp, background = c.surface2, tint = c.mute)
            }
        }
        CompositionLocalProvider(LocalHapticFeedback provides NoComposeHaptics) { content(closer) }
    }
}

/**
 * [haptic] is the feedback for tapping the row. Pass null when [run] plays
 * its own (e.g. a favorite toggle), so one tap never vibrates twice.
 */
data class SheetAction(
    val label: String,
    val icon: Ico,
    val danger: Boolean = false,
    val haptic: HapticType? = HapticType.LIGHT,
    val run: () -> Unit,
)

/** A list of actions with Cancel, optionally headed by a recipe preview. */
@Composable
fun ActionSheet(
    onDismiss: () -> Unit,
    actions: List<SheetAction>,
    preview: Recipe? = null,
    title: String? = null,
    message: String? = null,
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    RSheet(onDismiss) { closer ->
        if (preview != null) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RecipeImage(preview, Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f)) {
                    Text(preview.title, style = RecipaiType.secTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${timeText(preview)} · ${servingsText(preview)}", style = RecipaiType.mono, color = c.mute)
                }
            }
        } else if (title != null) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp)) {
                Text(title, style = RecipaiType.sheetTitle, color = c.ink)
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = RecipaiType.body, color = c.mute)
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface2)) {
                actions.forEachIndexed { i, a ->
                    if (i > 0) Hairline(start = 52.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap(pressedScale = 0.99f, haptic = a.haptic) { closer.close(a.run) }
                            .height(54.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val color = if (a.danger) c.danger else c.ink
                        RIcon(a.icon, tint = color)
                        Text(a.label, style = RecipaiType.cell.copy(fontSize = RecipaiType.ui.fontSize.times(16f / 15f)), color = color)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .tap(pressedScale = 0.99f, haptic = null) {
                        haptics.perform(HapticType.LIGHT)
                        closer.close()
                    }
                    .height(54.dp)
                    .background(c.surface2, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", style = RecipaiType.ui, color = c.ink) }
        }
    }
}

/** "Delete “Title”?" with a destructive confirm. */
@Composable
fun ConfirmDeleteSheet(recipe: Recipe, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val c = LocalColors.current
    RSheet(onDismiss) { closer ->
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)) {
            Box(
                Modifier.size(48.dp).background(c.danger.copy(alpha = 0.16f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { RIcon(Ico.Trash, tint = c.danger) }
            Spacer(Modifier.height(14.dp))
            Text("Delete “${recipe.title}”?", style = RecipaiType.sheetTitle.copy(fontSize = RecipaiType.secTitle.fontSize.times(21f / 19f)), color = c.ink)
            Spacer(Modifier.height(6.dp))
            val photos = recipe.photos.size
            val also = if (photos > 0) " and its $photos ${if (photos == 1) "photo" else "photos"}" else ""
            Text("This removes the recipe$also. You can undo for a few seconds.", style = RecipaiType.body, color = c.mute)
            Spacer(Modifier.height(20.dp))
            DangerButton("Delete recipe") { closer.close(onDelete) }
            Spacer(Modifier.height(8.dp))
            RButton("Cancel", { closer.close() }, Modifier.fillMaxWidth(), height = 50.dp)
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(haptic = HapticType.WARNING, onClick = onClick)
            .height(50.dp)
            .background(c.danger, RoundedCornerShape(15.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RIcon(Ico.Trash, tint = Color.White)
        Text(text, style = RecipaiType.ui, color = Color.White)
    }
}
