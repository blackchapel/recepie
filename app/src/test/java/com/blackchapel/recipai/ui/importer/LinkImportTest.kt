package com.blackchapel.recipai.ui.importer

import com.blackchapel.recipai.core.FetchFailure
import com.blackchapel.recipai.core.FetchResult
import com.blackchapel.recipai.core.LinkMessages
import com.blackchapel.recipai.core.RecipeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkImportTest {
    private val card = """
        <html><head><script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"Tomato Soup","recipeYield":"4 servings",
         "recipeIngredient":["2 tbsp olive oil","1 onion, chopped","800 g tomatoes"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Soften the onion in the oil for 8 minutes."},
                               {"@type":"HowToStep","text":"Add the tomatoes and simmer for 20 minutes, then blend."}]}
        </script></head><body><h1>Tomato Soup</h1></body></html>
    """.trimIndent()

    private val article = "<html><body><main><h1>News</h1><p>Prices went up again this spring, analysts said.</p></main></body></html>"

    private fun TestScope.importer(fetch: suspend (String) -> FetchResult): LinkImport {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LinkImport(this, fetch, reader = dispatcher, pace = 100)
    }

    @Test
    fun convertsARecipeCardStepByStep() = runTest {
        val gate = CompletableDeferred<Unit>()
        val asked = mutableListOf<String>()
        val link = importer { url ->
            asked += url
            gate.await()
            FetchResult.Page("https://www.soup.example.com/tomato/", card)
        }
        assertEquals("https://soup.example.com/tomato", link.convert(" soup.example.com/tomato "))
        runCurrent()
        assertEquals(listOf("https://soup.example.com/tomato"), asked)
        assertTrue(link.running)
        assertEquals(listOf(StepState.ACTIVE) + List(4) { StepState.WAITING }, link.steps)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(link.running)
        assertEquals(List(5) { StepState.DONE }, link.steps)
        assertEquals(RecipeSource.RECIPE_CARD, link.via)
        val r = link.result!!
        assertEquals("Tomato Soup", r.title)
        assertEquals("the final URL after redirects", "soup.example.com/tomato", r.source)
        assertNull(link.error)
        assertEquals(true, link.outcome?.ok)
    }

    @Test
    fun aBadLinkSaysSoWithoutFetching() = runTest {
        var fetched = false
        val link = importer { fetched = true; FetchResult.Page(it, card) }
        assertNull(link.convert("tomato soup"))
        advanceUntilIdle()
        assertFalse(fetched)
        assertEquals(LinkMessages.BAD_LINK, link.error)
        assertTrue(link.steps.isEmpty())
        assertEquals(false, link.outcome?.ok)
    }

    @Test
    fun aFetchFailureFailsTheFirstStepAndOffersARetry() = runTest {
        val link = importer { FetchResult.Failed(FetchFailure.OFFLINE, it) }
        link.convert("https://www.soup.example.com/x")
        advanceUntilIdle()
        assertEquals(StepState.FAILED, link.steps[0])
        assertTrue(link.steps.drop(1).all { it == StepState.WAITING })
        assertEquals(LinkMessages.fetchFailed(FetchFailure.OFFLINE, "soup.example.com"), link.error)
        assertTrue(link.canRetry)
        assertNull(link.result)
        assertFalse(link.running)

        val blocked = importer { FetchResult.Failed(FetchFailure.BLOCKED, it, 403) }
        blocked.convert("https://soup.example.com/x")
        advanceUntilIdle()
        assertFalse(blocked.canRetry)
        assertTrue(blocked.error!!.contains("error 403"))
    }

    @Test
    fun aPageWithoutARecipeKeepsItsTextForEditing() = runTest {
        val link = importer { FetchResult.Page(it, article) }
        link.convert("https://news.example.com/prices")
        advanceUntilIdle()
        assertEquals(listOf(StepState.DONE, StepState.FAILED), link.steps.take(2))
        assertEquals(LinkMessages.noRecipe("news.example.com"), link.error)
        assertTrue(link.pageText!!.startsWith("News"))
        assertNull(link.result)
    }

    @Test
    fun anUnexpectedFetchErrorIsAFailureNotACrash() = runTest {
        val link = importer { throw IllegalStateException("boom") }
        link.convert("https://soup.example.com/x")
        advanceUntilIdle()
        assertEquals(StepState.FAILED, link.steps[0])
        assertNotNull(link.error)
    }

    @Test
    fun aNewConversionReplacesTheOldOneAndSharesAreMarked() = runTest {
        val slow = CompletableDeferred<Unit>()
        val link = importer { url ->
            if ("first" in url) slow.await()
            FetchResult.Page(url, card)
        }
        link.convert("https://soup.example.com/first")
        runCurrent()
        link.convert("https://soup.example.com/second", fromShare = true)
        assertEquals("https://soup.example.com/second", link.shared)
        slow.complete(Unit)
        advanceUntilIdle()
        assertEquals("soup.example.com/second", link.result?.source)

        link.reset()
        assertTrue(link.steps.isEmpty())
        assertNull(link.result)
        assertNull(link.shared)
    }
}
