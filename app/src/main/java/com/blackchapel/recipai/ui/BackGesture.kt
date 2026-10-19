package com.blackchapel.recipai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The predictive back swipe on a pushed page ("swipe from the left edge:
 * back, the page follows the finger"). While the finger moves, [page]
 * slides right by [progress] of the screen width and the tab underneath
 * slides back into place. Released past the point, the page finishes
 * sliding out and is popped without the usual pop animation
 * ([swipedAway]); abandoned, it springs back.
 */
@Stable
class PageBackSwipe {
    /** The page following the finger in the latest swipe. Stale once popped; pages are never reused. */
    var page by mutableStateOf<Page?>(null)
        private set

    /** The swipe was released past the point: [page] is on its way out. */
    var committed by mutableStateOf(false)
        private set

    /** 0 = at rest, 1 = fully off screen to the right. */
    val progress = Animatable(0f)

    /** How far [p] is pushed to the right, as a fraction of its width. */
    fun offset(p: Page?): Float = if (p != null && p === page) progress.value else 0f

    /** [p] was popped by a finished swipe (it is already off screen). */
    fun swipedAway(p: Page?): Boolean = p != null && p === page && committed

    /** [p] is following the finger right now. */
    fun isFollowing(p: Page?): Boolean = p != null && p === page && !committed

    suspend fun follow(p: Page, fraction: Float) {
        if (page !== p || committed) {
            page = p
            committed = false
        }
        progress.snapTo(fraction)
    }

    fun commit() {
        committed = true
    }

    companion object {
        /**
         * Pages that follow the finger: sideways pages (not the modal
         * editor or cook mode, which slide down) directly over a tab, so
         * what the swipe reveals is what is really underneath.
         */
        fun canFollow(top: Page?, stackSize: Int): Boolean =
            top != null && top !is EditorPage && top !is CookPage && stackSize == 1
    }
}
