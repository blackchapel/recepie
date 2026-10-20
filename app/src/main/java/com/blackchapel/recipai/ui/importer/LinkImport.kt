package com.blackchapel.recipai.ui.importer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.core.FetchFailure
import com.blackchapel.recipai.core.FetchResult
import com.blackchapel.recipai.core.LinkMessages
import com.blackchapel.recipai.core.Links
import com.blackchapel.recipai.core.PageRecipe
import com.blackchapel.recipai.core.PageRecipeReader
import com.blackchapel.recipai.core.ParsedRecipe
import com.blackchapel.recipai.core.RecipeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the conversion progress list. */
enum class StepState { WAITING, ACTIVE, DONE, FAILED }

/** How the last conversion ended; [seq] makes each one distinct (for its haptic). */
data class LinkOutcome(val ok: Boolean, val seq: Int)

/**
 * The import sheet's "From a link" conversion: fetch the page, read the
 * recipe ([PageRecipeReader]) and walk the progress list the prototype
 * shows. Lives in the [com.blackchapel.recipai.ui.AppController], so a
 * conversion keeps going through rotation or with the sheet closed.
 *
 * [fetch] is `platform.net.PageFetcher` in the app and a fake in tests;
 * [pace] spaces the instant steps so the list reads as progress.
 */
@Stable
class LinkImport(
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> FetchResult,
    private val reader: CoroutineDispatcher = Dispatchers.Default,
    private val pace: Long = 320,
) {
    companion object {
        val STEPS = listOf(
            "Fetching the page",
            "Finding the recipe card",
            "Reading ingredients and amounts",
            "Detecting step times",
            "Suggesting tags",
        )

        /** "Edit the page text" puts at most this much into the paste box. */
        const val MAX_PAGE_TEXT = 20_000
        private val retryable = setOf(FetchFailure.OFFLINE, FetchFailure.TIMEOUT, FetchFailure.SERVER_ERROR, FetchFailure.FAILED)
    }

    /** One state per [STEPS] entry; empty before the first conversion (no list shown). */
    var steps by mutableStateOf<List<StepState>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var result by mutableStateOf<ParsedRecipe?>(null)
        private set
    var via by mutableStateOf<RecipeSource?>(null)
        private set

    /** What went wrong, in words for the user. */
    var error by mutableStateOf<String?>(null)
        private set

    /** A failure worth trying again (network trouble), so the sheet offers Try again. */
    var canRetry by mutableStateOf(false)
        private set

    /** The page's text when no recipe was found on it, for "Edit the page text". */
    var pageText by mutableStateOf<String?>(null)
        private set

    /** The link that arrived through the share sheet, shown as a card until the next conversion. */
    var shared by mutableStateOf<String?>(null)
        private set

    /** Set when a link is shared in; the sheet plays the medium haptic and clears it. */
    var shareHapticPending by mutableStateOf(false)

    var outcome by mutableStateOf<LinkOutcome?>(null)
        private set

    private var job: Job? = null
    private var seq = 0

    /**
     * Starts converting [input]. Returns the cleaned-up link (for the text
     * field), or null when it isn't a link; then [error] says so.
     */
    fun convert(input: String, fromShare: Boolean = false): String? {
        job?.cancel()
        val url = Links.normalize(input)
        result = null
        via = null
        pageText = null
        canRetry = false
        shared = if (fromShare) url else null
        if (url == null) {
            steps = emptyList()
            running = false
            error = LinkMessages.BAD_LINK
            finish(false)
            return null
        }
        error = null
        running = true
        steps = List(STEPS.size) { if (it == 0) StepState.ACTIVE else StepState.WAITING }
        job = scope.launch { run(url) }
        return url
    }

    /** Clears everything (after saving, or "Clear"). */
    fun reset() {
        job?.cancel()
        job = null
        steps = emptyList()
        running = false
        result = null
        via = null
        error = null
        canRetry = false
        pageText = null
        shared = null
        shareHapticPending = false
    }

    private suspend fun run(url: String) {
        val fetched = try {
            fetch(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.Failed(FetchFailure.FAILED, url)
        }
        val page = when (fetched) {
            is FetchResult.Failed -> {
                fail(0, LinkMessages.fetchFailed(fetched.reason, Links.host(fetched.url), fetched.status))
                canRetry = fetched.reason in retryable
                return
            }
            is FetchResult.Page -> fetched
        }
        mark(0, StepState.DONE)
        mark(1, StepState.ACTIVE)
        val read = withContext(reader) {
            try {
                PageRecipeReader.read(page.html, page.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PageRecipe(null, RecipeSource.NONE, pageText = "")
            } catch (e: StackOverflowError) {
                // A pathological page can make a regex recurse too deep; never crash for it.
                PageRecipe(null, RecipeSource.NONE, pageText = "")
            }
        }
        delay(pace)
        val recipe = read.recipe
        if (recipe == null) {
            pageText = read.pageText.take(MAX_PAGE_TEXT).takeIf { it.isNotBlank() }
            fail(1, LinkMessages.noRecipe(Links.host(page.url)))
            return
        }
        mark(1, StepState.DONE)
        for (i in 2 until STEPS.size) {
            mark(i, StepState.ACTIVE)
            delay(pace)
            mark(i, StepState.DONE)
        }
        via = read.via
        result = recipe
        running = false
        finish(true)
    }

    private fun mark(index: Int, state: StepState) {
        steps = steps.toMutableList().also { it[index] = state }
    }

    private fun fail(index: Int, message: String) {
        mark(index, StepState.FAILED)
        error = message
        running = false
        finish(false)
    }

    private fun finish(ok: Boolean) {
        outcome = LinkOutcome(ok, ++seq)
    }
}
