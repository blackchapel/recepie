package com.blackchapel.recipai.platform.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.coroutines.cancellation.CancellationException

/**
 * System back for pushed pages, with the predictive-back swipe: while the
 * finger moves (Android 14+, the edge swipe; needs
 * `android:enableOnBackInvokedCallback` in the manifest) [onProgress] gets
 * the gesture's progress from 0 to 1. Releasing past the point calls
 * [onBack] with `swiped = true`; abandoning the swipe calls [onCancel].
 * The back button, and back on older Android versions, calls [onBack] with
 * `swiped = false` and no progress.
 *
 * [onProgress] runs inside the gesture's coroutine; [onCancel] and [onBack]
 * must not suspend (the gesture's coroutine is already cancelled when a
 * swipe is abandoned), so animations they start belong in the caller's
 * own scope.
 */
@Composable
fun PageBackHandler(
    enabled: Boolean,
    onProgress: suspend (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: (swiped: Boolean) -> Unit,
) {
    val progress by rememberUpdatedState(onProgress)
    val cancel by rememberUpdatedState(onCancel)
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        var swiped = false
        val completed = try {
            events.collect { e ->
                swiped = true
                progress(e.progress.coerceIn(0f, 1f))
            }
            true
        } catch (e: CancellationException) {
            false
        }
        if (completed) back(swiped) else cancel()
    }
}
