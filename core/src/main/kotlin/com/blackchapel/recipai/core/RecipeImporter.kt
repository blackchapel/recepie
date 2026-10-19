package com.blackchapel.recipai.core

/** One ingredient line as the importer understood it. */
data class ParsedIngredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val note: String = "",
    val isHeading: Boolean = false,
)

data class ParsedStep(val text: String, val minutes: Double, val timeScale: TimeScale)

data class ParsedRecipe(
    val title: String,
    val description: String,
    val servings: Int,
    /** True when the text never stated servings and [servings] is the default. */
    val servingsGuessed: Boolean,
    val ingredients: List<ParsedIngredient>,
    val steps: List<ParsedStep>,
    val notes: String,
    val tags: List<String>,
    /** Lines dropped as page clutter (ads, ratings, "jump to recipe"...). */
    val skippedLines: Int,
) {
    val timerCount: Int get() = steps.count { it.minutes > 0 }

    /** Enough to save: a title and at least one ingredient or step. */
    val isUsable: Boolean get() = title.isNotBlank() && (ingredients.isNotEmpty() || steps.isNotEmpty())
}

/**
 * Turns copied text into a recipe. The only implementation today is rule
 * based ([TextRecipeParser]); an LLM-backed importer can be added behind this
 * interface later (see docs/SYSTEM_STATE.md, "AI / LLM features").
 */
fun interface RecipeImporter {
    fun parse(text: String): ParsedRecipe
}
