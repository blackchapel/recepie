package com.blackchapel.recipai.ui.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.SystemBackHandler
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** One page of the first-run tour. */
private class WelcomePage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val points: List<String>,
    val art: @Composable () -> Unit,
)

private val Pages = listOf(
    WelcomePage(
        eyebrow = "Welcome to Recipai",
        title = "Your recipes, scaled to fit",
        body = "Keep your recipes with timed steps, photos and tags. Change any amount and everything else follows, step times included.",
        points = listOf("Live scaling", "Step timers", "Cook mode", "Photos and tags"),
        art = { ScaleArt() },
    ),
    WelcomePage(
        eyebrow = "Import",
        title = "Paste a recipe, or share a link",
        body = "Copy a recipe from a message or a website, or share a link to Recipai from your browser. It picks out the ingredients, steps and times for you.",
        points = listOf("Paste text", "Share a link", "Review before saving"),
        art = { ImportArt() },
    ),
    WelcomePage(
        eyebrow = "Backup",
        title = "Safe in your own Google Drive",
        body = "Connect your account to back up recipes, photos and settings daily, weekly or when you tap. Recipai only sees the backup files it made.",
        points = listOf("Daily or weekly", "Restore any time", "Optional"),
        art = { BackupArt() },
    ),
)

/**
 * The first-run tour: three pages (the app, import, backup), each with an
 * illustration, a short note and a few feature chips. Shown once on a new
 * install ([com.blackchapel.recipai.ui.AppController.decideWelcome]), and
 * again from Settings > About. Follows the system light/dark theme like
 * the rest of the app; fills the window including behind the system bars,
 * with padding so nothing interactive goes under them.
 */
@Composable
fun WelcomeScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, Pages.lastIndex), pageCount = { Pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Pages.lastIndex

    // Next and Back move the pager themselves. Next already plays its own
    // button tap and Back is silent, so the page-change tick below is only for
    // a swipe; without this a Next tap vibrated twice.
    var scrollingByButton by remember { mutableStateOf(false) }

    fun goTo(page: Int) {
        scope.launch {
            scrollingByButton = true
            try {
                pager.animateScrollToPage(page)
            } finally {
                scrollingByButton = false
            }
        }
    }

    // Back steps to the previous page; on the first page it leaves the tour.
    SystemBackHandler(enabled = true) {
        if (pager.currentPage > 0) goTo(pager.currentPage - 1) else onFinish()
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { if (!scrollingByButton) haptics.perform(HapticType.SELECTION) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.canvas)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Skip: always there except on the last page, where the main button finishes.
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!last) RButton("Skip", onFinish, style = ButtonStyle.GHOST, small = true, haptic = HapticType.LIGHT)
        }

        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i -> PageContent(Pages[i]) }

        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Dots(count = Pages.size, current = pager.currentPage)
            Spacer(Modifier.height(18.dp))
            if (last) {
                RButton("Get started", { onFinish() }, Modifier.fillMaxWidth(), icon = Ico.Check, style = ButtonStyle.FLAME, height = 54.dp, haptic = HapticType.LIGHT)
            } else {
                RButton("Next", { goTo(pager.currentPage + 1) }, Modifier.fillMaxWidth(), icon = Ico.Right, style = ButtonStyle.CTA, height = 54.dp, haptic = HapticType.LIGHT)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageContent(page: WelcomePage) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The illustration is drawn at a fixed design size and scaled down to fit
        // the space left, so small phones keep the picture instead of losing it.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val k = minOf(1f, maxWidth / ArtSize, maxHeight / ArtSize)
            Box(Modifier.requiredSize(ArtSize).graphicsLayer { scaleX = k; scaleY = k }.clearAndSetSemantics { }) { page.art() }
        }
        Spacer(Modifier.height(8.dp))
        Text(page.eyebrow.uppercase(), style = RecipaiType.eyebrow, color = c.accentInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            page.title,
            style = RecipaiType.detailTitle,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(page.body, style = RecipaiType.body, color = c.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            page.points.forEach { PointChip(it) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PointChip(text: String) {
    val c = LocalColors.current
    Box(
        Modifier
            .height(32.dp)
            .background(c.surface, CircleShape)
            .border(1.dp, c.line, CircleShape)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RecipaiType.uiSmall, color = c.body, maxLines = 1) }
}

/** The page dots: the current one stretches into a pill. */
@Composable
private fun Dots(count: Int, current: Int) {
    val c = LocalColors.current
    Row(
        Modifier.semantics { stateDescription = "Page ${current + 1} of $count"; contentDescription = "Welcome tour" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            val w by animateDpAsState(if (on) 24.dp else 8.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
            val color by animateColorAsState(if (on) c.saffron else c.lineStrong, label = "dotColor")
            Box(Modifier.width(w).height(8.dp).background(color, CircleShape))
        }
    }
}
