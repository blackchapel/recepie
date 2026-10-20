package com.blackchapel.recipai.core

/**
 * Which ingredients a step mentions, for cook mode ("the scaled
 * ingredients that step uses"). Ported from the prototype's
 * `ingredientUses`: an ingredient matches when one of its name's words
 * (ignoring sizes, colors and preparation words) appears in the step,
 * singular or plural. English only, like the importer.
 */
object IngredientUses {
    private val stop = setOf(
        "large", "small", "medium", "fresh", "ground", "chopped", "sliced", "extra", "firm", "plain", "whole", "cold",
        "ripe", "dried", "mixed", "light", "dark", "unsalted", "all-purpose", "fine", "toasted", "white", "green", "red",
        "dry", "cooked", "baby", "thai", "pink", "black", "neutral", "sea", "flaky", "brown", "granulated", "crushed",
        "smoked", "room", "and",
    )
    private val notLetter = Regex("[^a-z\\s]")
    private val notWordChar = Regex("[^a-z\\s\\-]")
    private val space = Regex("\\s+")
    private val pluralEnd = Regex("(es|s)$")

    /** The key words of an ingredient name ("large red onion" -> onion). */
    fun keywords(name: String): List<String> =
        name.lowercase().replace(notWordChar, " ").split(space).filter { it.length > 2 && it !in stop }

    fun uses(recipe: Recipe, step: Step): List<Ingredient> {
        val text = " " + step.text.lowercase().replace(notLetter, " ").replace(space, " ") + " "
        return recipe.ingredients.filter { ing ->
            !ing.isHeading && keywords(ing.name).any { w -> mentions(text, w) }
        }
    }

    private fun mentions(text: String, word: String): Boolean {
        if (" $word " in text) return true
        val stem = word.replace(pluralEnd, "")
        if (stem.length < 3) return false
        return Regex("\\s" + Regex.escape(stem) + "(s|es)?\\s").containsMatchIn(text)
    }
}
