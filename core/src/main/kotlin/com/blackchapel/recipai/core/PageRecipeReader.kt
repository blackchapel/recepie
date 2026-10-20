package com.blackchapel.recipai.core

/** How a recipe was found on a page. */
enum class RecipeSource {
    /** The page's schema.org recipe card (JSON-LD). */
    RECIPE_CARD,

    /** No usable card: the page's visible text through the text rules. */
    PAGE_TEXT,

    /** Nothing that looks like a recipe. */
    NONE,
}

/**
 * [recipe] is null exactly when [via] is [RecipeSource.NONE]. [pageText] is
 * the page's readable text (empty for a recipe card), for "Edit the page text".
 */
data class PageRecipe(val recipe: ParsedRecipe?, val via: RecipeSource, val pageText: String)

/**
 * A fetched page to a recipe: the JSON-LD recipe card first
 * ([JsonLdRecipeReader]), else the page's visible text through
 * [TextRecipeParser]. A text parse only counts when it looks like a real
 * recipe, so an article or a shop page is reported as "no recipe" rather
 * than imported as nonsense.
 */
object PageRecipeReader {
    fun read(html: String, url: String? = null): PageRecipe {
        val source = url?.let(Links::source)
        val card = JsonLdRecipeReader.read(HtmlText.jsonLdBlocks(html))
        if (card != null && card.isUsable) {
            return PageRecipe(card.copy(source = source), RecipeSource.RECIPE_CARD, pageText = "")
        }
        val pageText = HtmlText.visibleText(html)
        val text = TextRecipeParser.parse(pageText)
        if (looksLikeRecipe(text)) {
            // The card's name (or the page's) beats the text rules' first-line guess.
            val title = card?.title?.takeIf { it.isNotBlank() } ?: text.title.ifBlank { HtmlText.pageTitle(html).orEmpty() }
            return PageRecipe(text.copy(title = title, source = source), RecipeSource.PAGE_TEXT, pageText)
        }
        return PageRecipe(null, RecipeSource.NONE, pageText)
    }

    /** At least two ingredients with an amount and one real step, so menus and articles don't pass. */
    internal fun looksLikeRecipe(p: ParsedRecipe): Boolean =
        p.ingredients.count { !it.isHeading && it.quantity != null } >= 2 && p.steps.isNotEmpty()
}

/** The words for link-import failures (shown under the progress list). */
object LinkMessages {
    const val BAD_LINK = "Enter a full link, like https://www.example.com/recipes/…"

    fun fetchFailed(reason: FetchFailure, host: String, status: Int = 0): String = when (reason) {
        FetchFailure.OFFLINE -> "Couldn’t reach $host. Check the link and your internet connection, then try again."
        FetchFailure.TIMEOUT -> "$host took too long to answer. Try again, or copy the recipe and paste the text instead."
        FetchFailure.NOT_FOUND -> "$host says this page doesn’t exist (error $status). Check the link."
        FetchFailure.BLOCKED -> "$host didn’t let Recipai read this page (error $status). Open it in your browser, copy the recipe and paste the text instead."
        FetchFailure.SERVER_ERROR -> "$host had a problem (error $status). Try again in a little while, or paste the recipe text instead."
        FetchFailure.NOT_A_PAGE -> "That link isn’t a web page Recipai can read. Copy the recipe and paste the text instead."
        FetchFailure.TOO_MANY_REDIRECTS -> "$host kept redirecting, so Recipai gave up. Open it in your browser and paste the text instead."
        FetchFailure.INSECURE -> "Couldn’t make a secure connection to $host. Copy the recipe and paste the text instead."
        FetchFailure.HTTP_ONLY -> "$host doesn’t seem to offer this page over a secure (https) connection, and Recipai only reads secure pages. Open it in your browser, copy the recipe and paste the text instead."
        FetchFailure.FAILED -> "Couldn’t read this page from $host. Copy the recipe and paste the text instead."
    }

    fun noRecipe(host: String): String =
        "Couldn’t find a recipe on this page from $host. Some sites only show it in the browser: copy the recipe and paste the text instead."
}
