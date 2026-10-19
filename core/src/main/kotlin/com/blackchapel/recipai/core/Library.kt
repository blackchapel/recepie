package com.blackchapel.recipai.core

enum class SortOrder { RECENT, A_TO_Z, QUICKEST }

/** A library filter: a tag, the "Under 30 min" chip, or nothing. */
sealed interface TagFilter {
    data object All : TagFilter
    data object Quick : TagFilter
    data class Tag(val tag: String) : TagFilter
}

/** One search hit. [why] explains a non-title match, e.g. "Uses garlic". */
data class SearchHit(val recipe: Recipe, val why: String?)

/** List logic shared by Recipes, Favorites and Search. */
object Library {
    const val QUICK_MINUTES = 30.0

    fun isQuick(recipe: Recipe): Boolean = Scaling.totalMinutes(recipe) <= QUICK_MINUTES

    /** Every tag with its recipe count, most used first, then A to Z. */
    fun tagCounts(recipes: List<Recipe>): List<Pair<String, Int>> =
        recipes.flatMap { it.tags.distinct() }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    fun filter(
        recipes: List<Recipe>,
        tag: TagFilter = TagFilter.All,
        query: String = "",
        favoritesOnly: Boolean = false,
        sort: SortOrder = SortOrder.RECENT,
    ): List<Recipe> {
        val q = query.trim().lowercase()
        return recipes
            .asSequence()
            .filter { !favoritesOnly || it.favorite }
            .filter {
                when (tag) {
                    TagFilter.All -> true
                    TagFilter.Quick -> isQuick(it)
                    is TagFilter.Tag -> tag.tag in it.tags
                }
            }
            .filter { q.isEmpty() || haystack(it).contains(q) }
            .toList()
            .let { sorted(it, sort) }
    }

    fun sorted(recipes: List<Recipe>, sort: SortOrder): List<Recipe> = when (sort) {
        SortOrder.RECENT -> recipes.sortedByDescending { it.updatedAt }
        SortOrder.A_TO_Z -> recipes.sortedBy { it.title.lowercase() }
        SortOrder.QUICKEST -> recipes.sortedBy { Scaling.totalMinutes(it) }
    }

    /** Search tab ranking: title matches, then ingredient, then tag matches. */
    fun search(recipes: List<Recipe>, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return recipes.mapNotNull { r ->
            when {
                r.title.lowercase().contains(q) -> Triple(r, 3, null)
                else -> {
                    val ing = r.ingredients.firstOrNull { !it.isHeading && it.name.lowercase().contains(q) }
                    when {
                        ing != null -> Triple(r, 2, "Uses ${ing.name}")
                        r.tags.any { it.contains(q) } -> Triple(r, 1, r.tags.joinToString(" ") { "#$it" })
                        else -> null
                    }
                }
            }
        }.sortedByDescending { it.second }.map { SearchHit(it.first, it.third) }
    }

    private fun haystack(r: Recipe): String =
        (listOf(r.title, r.description) + r.tags + r.ingredients.map { it.name }).joinToString(" ").lowercase()
}
