package com.blackchapel.recipai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Tab
import com.blackchapel.recipai.ui.Toast
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.delay

val NavBarHeight = 52.dp
val TabBarHeight = 62.dp

/**
 * The top bar over a scrolling page: transparent at the top, a translucent
 * bar with a hairline once [scrolled], and the small centered [title] once
 * the large title has scrolled away ([collapsed]).
 */
@Composable
fun TopBar(
    title: String,
    scrolled: Boolean,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    left: @Composable RowScope.() -> Unit = {},
    right: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalColors.current
    val barAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(250), label = "bar")
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "title")
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = barAlpha }
                .background(c.bar),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(NavBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = left)
            Text(
                title,
                style = RecipaiType.navTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .graphicsLayer { alpha = titleAlpha; translationY = (1 - titleAlpha) * 6.dp.toPx() },
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = right)
        }
    }
}

/** Eyebrow + large title at the top of a tab. */
@Composable
fun LargeTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), style = RecipaiType.eyebrow, color = c.accentInk)
        Text(title, style = RecipaiType.largeTitle, color = c.ink)
    }
}

/** Bottom tab bar: four tabs around the center Import button. */
@Composable
fun TabBar(app: AppController, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth().background(c.bar)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(app, Tab.RECIPES, Ico.Book, "Recipes")
            TabItem(app, Tab.FAVORITES, Ico.Heart, "Favorites")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .semantics { contentDescription = "Import a recipe" }
                        // Light, not medium: the center button felt too strong on the
                        // first test build.
                        .tap(pressedScale = 0.9f, haptic = HapticType.LIGHT, label = "Import a recipe", onClick = onImport)
                        .size(50.dp)
                        .shadow(10.dp, RoundedCornerShape(17.dp), ambientColor = c.paprika, spotColor = c.paprika)
                        .background(FlameBrush, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center,
                ) { RIcon(Ico.Spark, size = 24.dp, tint = OnFlame) }
            }
            TabItem(app, Tab.SEARCH, Ico.Search, "Search")
            TabItem(app, Tab.SETTINGS, Ico.Gear, "Settings")
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun RowScope.TabItem(app: AppController, tab: Tab, icon: Ico, label: String) {
    val c = LocalColors.current
    val on = app.tab == tab
    val pop by animateFloatAsState(if (on) 1f else 0.92f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "tab")
    Column(
        Modifier
            .weight(1f)
            .semantics { selected = on }
            .tap(pressedScale = 0.9f, haptic = HapticType.LIGHT, role = Role.Tab) { app.selectTab(tab) }
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RIcon(
            if (tab == Tab.FAVORITES && on) Ico.HeartFill else icon,
            size = 24.dp,
            tint = if (on) c.saffron else c.faint,
            modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop },
        )
        Text(label, style = RecipaiType.tab, color = if (on) c.ink else c.faint, maxLines = 1)
    }
}

/** The floating + (new recipe), above the tab bar on Recipes and Favorites. */
@Composable
fun NewRecipeFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut(tween(150)) + fadeOut(tween(150)),
    ) {
        Box(
            Modifier
                .semantics { contentDescription = "New recipe" }
                // Light, not medium: the + felt too strong on the first test build.
                .tap(pressedScale = 0.9f, haptic = HapticType.LIGHT, label = "New recipe", onClick = onClick)
                .size(56.dp)
                .shadow(14.dp, RoundedCornerShape(20.dp))
                .background(c.cta, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { RIcon(Ico.Plus, size = 26.dp, tint = c.onCta) }
    }
}

/** The pill toast at the top of the screen, with an optional action (Undo). */
@Composable
fun ToastHost(app: AppController, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val toast = app.toast
    // Keep drawing the last toast while it animates out.
    val shownToast = remember { arrayOfNulls<Toast>(1) }
    if (toast != null) shownToast[0] = toast
    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(if (t.action != null) 6000 else 2800)
        if (app.toast?.id == t.id) app.toast = null
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 6.dp, start = 16.dp, end = 16.dp),
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(250)) { -it * 2 } + fadeOut(tween(250)),
    ) {
        val shown = shownToast[0] ?: return@AnimatedVisibility
        Row(
            Modifier
                .shadow(18.dp, CircleShape)
                .background(c.cta, CircleShape)
                .padding(start = 14.dp, end = if (shown.actionLabel != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp)
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shown.icon != null) RIcon(shown.icon, size = 15.dp, tint = c.onCta)
            Text(shown.message, style = RecipaiType.uiSmall.copy(fontSize = RecipaiType.ui.fontSize), color = c.onCta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (shown.actionLabel != null) {
                Box(
                    Modifier
                        .tap(haptic = HapticType.LIGHT) {
                            shown.action?.invoke()
                            if (app.toast?.id == shown.id) app.toast = null
                        }
                        .height(30.dp)
                        .background(c.onCta.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(shown.actionLabel, style = RecipaiType.uiSmall, color = c.onCta) }
            }
        }
    }
}

/** A thin spacer the height of a hairline. */
@Composable
fun HairSpacer(color: Color = LocalColors.current.line) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(color))
}
