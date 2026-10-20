package com.blackchapel.recipai.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.PhotoSet
import com.blackchapel.recipai.core.Photos
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.photos.rememberPhotoPicker
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.CoroutineScope

private val TileSize = 112.dp
private val TileShape = RoundedCornerShape(18.dp)

/**
 * Photos being copied into an editor, and the last problem reading them.
 * Held by the editor's state, not the strip's composition, so a rotation
 * mid-copy keeps the placeholder tiles and still shows the error.
 */
@Stable
class EditorPhotoAdding {
    /** How many picked photos are still being copied (placeholder tiles). */
    var pending by mutableIntStateOf(0)

    /** Why the last pick failed, or null. */
    var problem by mutableStateOf<String?>(null)
}

/**
 * The editor's photo strip: an Add photos tile (the system photo picker,
 * several at a time), then each photo with a Cover badge on the cover and
 * buttons to make it the cover or remove it. Until a photo exists it shows
 * the generated illustration. [draft] is the recipe being edited (its
 * photos, cover and, for the illustration, the rest); [onChange] reports
 * the new photo list and cover index.
 *
 * Picked photos are copied in [scope], which must outlive the screen (the
 * app's photo scope), so a rotation mid-copy doesn't drop them. The copy
 * can finish after this composition is gone, so [onAdded] gets the stored
 * names to add to the live draft (read when it runs, not captured from
 * [draft]), and [adding] holds the progress.
 *
 * Spans the full width and scrolls sideways; [contentPadding] is the
 * gutter (place it without horizontal padding of its own). Removing a
 * photo here only drops it from the draft: the file stays until a later
 * launch finds nothing using it, so Cancel loses nothing.
 */
@Composable
fun EditorPhotoStrip(
    draft: Recipe,
    onChange: (photos: List<String>, coverIndex: Int) -> Unit,
    adding: EditorPhotoAdding,
    onAdded: (names: List<String>) -> Unit,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp),
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val set = PhotoSet(draft.photos, Photos.clampCover(draft.photos.size, draft.coverIndex))
    val pending = adding.pending
    val problem = adding.problem
    val picker = rememberPhotoPicker(
        scope = scope,
        onStart = { n ->
            adding.pending = n
            adding.problem = null
        },
        onDone = { result ->
            adding.pending = 0
            if (result.added > 0) onAdded(result.names)
            haptics.perform(if (result.added > 0) HapticType.SUCCESS else HapticType.ERROR)
            adding.problem = when (result.failed) {
                0 -> null
                1 -> "Couldn't read one photo. Try a JPG or PNG."
                else -> "Couldn't read ${result.failed} photos. Try JPGs or PNGs."
            }
        },
    )
    // Photos already in the draft don't pop in; ones added here do.
    val initial = remember { draft.photos.toSet() }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AddPhotosTile {
                adding.problem = null
                if (!picker.launch()) adding.problem = "Couldn't open the photo picker on this phone."
            }
            set.photos.forEachIndexed { i, name ->
                key(name) {
                    EditorPhoto(
                        name = name,
                        index = i,
                        isCover = i == set.cover,
                        draft = draft,
                        popIn = name !in initial,
                        onCover = {
                            haptics.perform(HapticType.SELECTION)
                            val now = Photos.setCover(set, i)
                            onChange(now.photos, now.cover)
                        },
                        onRemove = {
                            haptics.perform(HapticType.LIGHT)
                            val now = Photos.remove(set, i)
                            onChange(now.photos, now.cover)
                        },
                    )
                }
            }
            repeat(pending) { PendingPhotoTile(TileSize, TileShape) }
            if (set.photos.isEmpty() && pending == 0) IllustrationTile(draft)
        }
        AnimatedVisibility(problem != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                problem.orEmpty(),
                style = RecipaiType.bodySmall,
                color = c.danger,
                modifier = Modifier.padding(contentPadding).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AddPhotosTile(onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier
            .tap(pressedScale = 0.96f, haptic = HapticType.LIGHT, label = "Add photos", onClick = onClick)
            .size(TileSize)
            .background(c.surface, TileShape)
            .dashedBorder(1.5.dp, c.lineStrong, 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(40.dp).background(FlameBrush, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            RIcon(Ico.Camera, size = 20.dp, tint = OnFlame)
        }
        Text("Add photos", style = RecipaiType.uiSmall.copy(fontSize = 12.sp), color = c.mute, maxLines = 1)
    }
}

@Composable
private fun EditorPhoto(
    name: String,
    index: Int,
    isCover: Boolean,
    draft: Recipe,
    popIn: Boolean,
    onCover: () -> Unit,
    onRemove: () -> Unit,
) {
    val pop = rememberPop(play = popIn)
    Box(Modifier.pop(pop).size(TileSize)) {
        PhotoFill(name, draft, TileShape, Modifier.matchParentSize(), description = "Photo ${index + 1}")
        if (isCover) {
            GlassLabel("COVER", Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!isCover) TileButton(Ico.Image, "Make photo ${index + 1} the cover", onCover)
            TileButton(Ico.Trash, "Remove photo ${index + 1}", onRemove)
        }
    }
}

/** The illustration shown until the first photo is added. */
@Composable
private fun IllustrationTile(draft: Recipe) {
    Box(Modifier.size(150.dp).clip(TileShape).background(LocalColors.current.surface2)) {
        RecipeArt(draft, Modifier.matchParentSize())
        GlassLabel(
            "Illustration until you add one",
            Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth(),
            uppercase = false,
        )
    }
}

@Composable
private fun GlassLabel(text: String, modifier: Modifier = Modifier, uppercase: Boolean = true) {
    val c = LocalColors.current
    Box(
        modifier
            .heightIn(min = 20.dp)
            .background(c.glass, RoundedCornerShape(10.dp))
            .border(1.dp, c.glassLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = RecipaiType.tag.copy(
                fontSize = if (uppercase) 10.sp else 10.5.sp,
                lineHeight = 13.sp,
                letterSpacing = if (uppercase) 0.05.em else RecipaiType.tag.letterSpacing,
                textAlign = TextAlign.Center,
            ),
            color = Color.White,
            maxLines = 2,
        )
    }
}

@Composable
private fun TileButton(icon: Ico, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .tap(pressedScale = 0.88f, haptic = null, description = label, onClick = onClick)
            .size(30.dp)
            .background(c.glass, shape)
            .border(1.dp, c.glassLine, shape),
        contentAlignment = Alignment.Center,
    ) { RIcon(icon, size = 15.dp, tint = Color.White) }
}
