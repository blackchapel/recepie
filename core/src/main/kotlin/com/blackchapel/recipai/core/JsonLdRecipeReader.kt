package com.blackchapel.recipai.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Reads the schema.org `Recipe` "recipe card" that most recipe sites embed
 * for search engines (`<script type="application/ld+json">`). Handles the
 * shapes seen in the wild: a bare object, a top-level array, an `@graph`,
 * a Recipe nested under another node, `@type` as a string or an array,
 * instructions as one string, an array of strings, `HowToStep`s or
 * `HowToSection`s, and `recipeYield` as a number, a string or an array.
 * The recipe image is ignored for now (photos are added by hand).
 */
object JsonLdRecipeReader {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        allowComments = true
    }
    private val i = RegexOption.IGNORE_CASE
    private val controlChars = Regex("[\\u0000-\\u001F]")
    private val wrappers = Regex("^\\s*(?://\\s*)?(?:<!\\[CDATA\\[|<!--)|(?://\\s*)?(?:]]>|-->)\\s*$")
    private val stepNumber = Regex("^\\s*(?:step\\s*)?\\d{1,2}\\s*[.):\\-–]\\s+", i)
    private val inlineNumbered = Regex("(?<=[.!?])\\s+(?=(?:[Ss]tep\\s*)?\\d{1,2}[.)]\\s+\\p{Lu})")
    private val digit = Regex("\\d")

    /** The best recipe card in [blocks] (the JSON-LD script contents), or null if there is none. */
    fun read(blocks: List<String>): ParsedRecipe? {
        val found = blocks.mapNotNull(::parseJson).flatMap { findRecipes(it, 0) }
        // Pages with several cards (a round-up, a related recipe) list the main one first; ties keep it.
        val best = found.maxByOrNull { count(it["recipeIngredient"] ?: it["ingredients"]) + count(it["recipeInstructions"]) }
            ?: return null
        return toParsed(best)
    }

    /** Convenience for a single JSON document. */
    fun readJson(text: String): ParsedRecipe? = read(listOf(text))

    private fun count(e: JsonElement?): Int = when (e) {
        is JsonArray -> e.size
        is JsonPrimitive -> if (e.isString && e.content.isNotBlank()) 1 else 0
        is JsonObject -> 1
        else -> 0
    }

    private fun parseJson(raw: String): JsonElement? {
        val text = raw.replace(wrappers, "").replace(controlChars, " ").trim()
        if (text.isEmpty()) return null
        return try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            null // Broken markup on the page: fall back to the page text.
        }
    }

    private fun findRecipes(e: JsonElement, depth: Int): List<JsonObject> {
        if (depth > 12) return emptyList()
        return when (e) {
            is JsonArray -> e.flatMap { findRecipes(it, depth + 1) }
            is JsonObject -> {
                val self = if (isRecipeType(e["@type"])) listOf(e) else emptyList()
                // A recipe's own fields never hold another recipe worth reading.
                self + if (self.isEmpty()) e.values.flatMap { findRecipes(it, depth + 1) } else emptyList()
            }
            else -> emptyList()
        }
    }

    private fun isRecipeType(t: JsonElement?): Boolean = when (t) {
        is JsonPrimitive -> t.isString && t.content.substringAfterLast('/').substringAfterLast(':').trim().equals("Recipe", ignoreCase = true)
        is JsonArray -> t.any(::isRecipeType)
        else -> false
    }

    /** A string value: a string, a number, the first usable entry of an array, or an object's `name`/`text`/`value`. */
    private fun str(e: JsonElement?): String? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
        is JsonArray -> e.firstNotNullOfOrNull(::str)
        is JsonObject -> str(e["name"]) ?: str(e["text"]) ?: str(e["@value"]) ?: str(e["value"])
    }

    /** Every string in a value that may be a string, an array of strings, or comma separated. */
    private fun strings(e: JsonElement?): List<String> = when (e) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOf(e.content)
        is JsonArray -> e.flatMap(::strings)
        is JsonObject -> listOfNotNull(str(e))
    }

    /** Page strings are often HTML and sometimes double-escaped ("&amp;#39;"). */
    private fun clean(s: String): String = HtmlText.stripTags(HtmlText.decodeEntities(s))
    private fun cleanInline(s: String): String = HtmlText.inline(HtmlText.decodeEntities(s))

    private fun toParsed(o: JsonObject): ParsedRecipe {
        val title = (str(o["name"]) ?: str(o["headline"]))?.let(::cleanInline).orEmpty()
        val description = str(o["description"])?.let(::cleanInline).orEmpty()

        val ingredients = strings(o["recipeIngredient"] ?: o["ingredients"]).flatMap { clean(it).split('\n') }.mapNotNull { line ->
            val t = line.trim()
            when {
                t.isEmpty() -> null
                t.endsWith(":") && !digit.containsMatchIn(t) && t.length < 40 ->
                    ParsedIngredient(t.removeSuffix(":").trim(), isHeading = true)
                else -> TextRecipeParser.parseIngredientLine(t)
            }
        }

        val tips = mutableListOf<String>()
        val stepTexts = mutableListOf<String>()
        collectInstructions(o["recipeInstructions"], stepTexts, tips, 0)
        val steps = stepTexts
            .map { it.replace(stepNumber, "").trim() }
            .filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { acc, s -> if (acc.lastOrNull() != s) acc += s; acc }
            .map { ParsedStep(it, TextRecipeParser.detectMinutes(it), TextRecipeParser.guessTimeScale(it)) }

        val yield = Yield.parse(strings(o["recipeYield"] ?: o["yield"]))
        val prep = Durations.minutes(str(o["prepTime"]))
        val cook = Durations.minutes(str(o["cookTime"]))
        val statedTotal = Durations.minutes(str(o["totalTime"]))
        val total = statedTotal ?: if (prep != null || cook != null) (prep ?: 0.0) + (cook ?: 0.0) else null
        // A total that only repeats the one time given says nothing new.
        val shownTotal = total?.takeIf { (prep != null && cook != null) || (statedTotal != null && statedTotal != prep && statedTotal != cook) }

        // The model has no prep/cook times (it sums the steps), so keep the page's in the notes.
        val times = listOfNotNull(
            prep?.let { "Prep ${Scaling.formatMinutes(it)}" },
            cook?.let { "Cook ${Scaling.formatMinutes(it)}" },
            shownTotal?.let { "Total ${Scaling.formatMinutes(it)}" },
        ).joinToString(" · ")
        val notes = (tips + listOfNotNull(times.takeIf { it.isNotEmpty() })).joinToString("\n")

        val ingredientText = ingredients.joinToString(" ") { it.name }
        val stepText = steps.joinToString(" ") { it.text }
        val tags = RecipeCardTags.of(
            title = title,
            ingredientText = ingredientText,
            stepText = stepText,
            totalMinutes = total ?: steps.sumOf { it.minutes },
            categories = strings(o["recipeCategory"]),
            cuisines = strings(o["recipeCuisine"]),
            keywords = strings(o["keywords"]),
            diets = strings(o["suitableForDiet"]),
        )
        return ParsedRecipe(
            title = title,
            description = description,
            servings = yield?.count ?: 4,
            servingsGuessed = yield == null,
            ingredients = ingredients,
            steps = steps,
            notes = notes,
            tags = tags,
            skippedLines = 0,
            servingsLabel = yield?.label,
        )
    }

    private fun collectInstructions(e: JsonElement?, steps: MutableList<String>, tips: MutableList<String>, depth: Int) {
        if (e == null || depth > 8) return
        when (e) {
            is JsonPrimitive -> if (e.isString) steps += splitInstructionText(e.content)
            is JsonArray -> e.forEach { collectInstructions(it, steps, tips, depth + 1) }
            is JsonObject -> {
                val type = str(e["@type"]).orEmpty().substringAfterLast('/').substringAfterLast(':')
                val text = str(e["text"])
                val children = (e["itemListElement"] ?: e["steps"])?.takeIf { it is JsonArray || it is JsonObject }
                when {
                    type.equals("HowToTip", ignoreCase = true) ->
                        (text ?: str(e["name"]))?.let { tips += HtmlText.inline(HtmlText.decodeEntities(it)) }
                    // A HowToSection or ItemList: its name is a heading, its steps follow.
                    text == null && children != null -> collectInstructions(children, steps, tips, depth + 1)
                    else -> (text ?: str(e["name"]) ?: str(e["description"]))?.let { steps += splitInstructionText(it) }
                }
            }
        }
    }

    /** One instruction string: HTML paragraphs or lines become steps; "1. Mix. 2. Bake." is split too. */
    private fun splitInstructionText(raw: String): List<String> =
        clean(raw).split('\n').flatMap { line -> line.split(inlineNumbered) }.map { it.trim() }.filter { it.isNotEmpty() }

    /** "4", "4 servings", "Serves 4-6", "Makes 24 cookies", ["4", "4 servings"]. */
    internal data class Yield(val count: Int, val label: String?) {
        companion object {
            private val number = Regex("(\\d+(?:[.,]\\d+)?)(?:\\s*(?:-|–|to)\\s*\\d+(?:[.,]\\d+)?)?")
            private val generic = Regex(
                "^(?:servings?|serves|people|persons?|portions?|plates?|helpings?|adults?|pax|x|yield|makes|about|approx\\.?|approximately|around|for)$",
                RegexOption.IGNORE_CASE,
            )
            private val lead = Regex("^(?:serves|servings?|yield|yields|makes|about|approx\\.?|approximately|around|for)\\b\\s*:?\\s*", RegexOption.IGNORE_CASE)

            fun parse(values: List<String>): Yield? {
                val parsed = values.mapNotNull { one(HtmlText.inline(HtmlText.decodeEntities(it))) }
                if (parsed.isEmpty()) return null
                return parsed.firstOrNull { it.label != null && it.count == parsed.first().count } ?: parsed.first()
            }

            private fun one(s: String): Yield? {
                val m = number.find(s) ?: return null
                val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
                val count = n.roundToInt().coerceIn(1, 999)
                val after = s.substring(m.range.last + 1).replace(Regex("^[\\s:)]+"), "").replace(lead, "")
                    .replace(Regex("[(.].*$"), "").trim()
                val words = after.split(Regex("\\s+")).filter { it.isNotEmpty() && !generic.matches(it) }
                val label = words.take(3).joinToString(" ").lowercase().trim()
                    .takeIf { it.isNotEmpty() && it.length <= 24 && it.any(Char::isLetter) && words.size <= 3 }
                return Yield(count, label)
            }
        }
    }
}

/** ISO 8601 durations as schema.org uses them: "PT1H30M", "P0DT0H45M", "PT90M". */
object Durations {
    private val iso = Regex(
        "^P(?:(\\d+(?:\\.\\d+)?)Y)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)W)?(?:(\\d+(?:\\.\\d+)?)D)?" +
            "(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        RegexOption.IGNORE_CASE,
    )

    /** Minutes, or null for a missing, zero or unreadable value. A plain "45 minutes" is read too. */
    fun minutes(value: String?): Double? {
        val s = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val m = iso.find(s)
        val total = if (m != null) {
            val g = m.groupValues.map { it.toDoubleOrNull() ?: 0.0 }
            g[1] * 525_600 + g[2] * 43_200 + g[3] * 10_080 + g[4] * 1_440 + g[5] * 60 + g[6] + g[7] / 60
        } else {
            TextRecipeParser.detectMinutes(s)
        }
        if (total <= 0 || total > 60 * 24 * 60) return null
        return if (total < 10) Math.round(total * 2) / 2.0 else Math.round(total).toDouble()
    }
}

/**
 * Tags for an imported recipe card: the page's own course, cuisine, diet and
 * keyword hints, mapped to the app's tag vocabulary, then the text rules'
 * guesses ([TextRecipeParser.guessTags]).
 */
object RecipeCardTags {
    private val courses = mapOf(
        "main" to "dinner", "main course" to "dinner", "main courses" to "dinner", "main dish" to "dinner",
        "main dishes" to "dinner", "mains" to "dinner", "dinner" to "dinner", "dinners" to "dinner",
        "entree" to "dinner", "entrée" to "dinner", "entrees" to "dinner", "supper" to "dinner",
        "lunch" to "lunch", "breakfast" to "breakfast", "brunch" to "breakfast",
        "dessert" to "dessert", "desserts" to "dessert", "sweets" to "dessert",
        "drink" to "drinks", "drinks" to "drinks", "beverage" to "drinks", "beverages" to "drinks",
        "cocktail" to "drinks", "cocktails" to "drinks", "smoothie" to "drinks", "smoothies" to "drinks",
        "salad" to "salad", "salads" to "salad", "side" to "side", "sides" to "side", "side dish" to "side",
        "side dishes" to "side", "appetizer" to "snack", "appetizers" to "snack", "starter" to "snack",
        "starters" to "snack", "snack" to "snack", "snacks" to "snack", "soup" to "soup", "soups" to "soup",
        "bread" to "baking", "breads" to "baking", "baking" to "baking", "baked goods" to "baking",
        "sauce" to "sauce", "sauces" to "sauce",
    )
    private val mealCourses = setOf("dinner", "lunch", "breakfast", "dessert", "drinks", "salad", "side", "snack", "soup", "baking", "sauce")
    private val diets = mapOf(
        "vegetarian" to "vegetarian", "vegetariandiet" to "vegetarian", "vegan" to "vegan", "vegandiet" to "vegan",
        "glutenfree" to "gluten-free", "glutenfreediet" to "gluten-free", "gluten-free" to "gluten-free",
        "dairy-free" to "dairy-free", "dairyfree" to "dairy-free",
    )
    private val keywordTags = mapOf(
        "vegetarian" to "vegetarian", "vegan" to "vegan", "gluten-free" to "gluten-free", "gluten free" to "gluten-free",
        "dairy-free" to "dairy-free", "dairy free" to "dairy-free", "spicy" to "spicy", "one-pot" to "one-pot",
        "one pot" to "one-pot", "one-pan" to "one-pan", "one pan" to "one-pan", "sheet pan" to "one-pan",
        "sheet-pan" to "one-pan", "quick" to "quick", "noodles" to "noodles",
    )

    fun of(
        title: String,
        ingredientText: String,
        stepText: String,
        totalMinutes: Double,
        categories: List<String>,
        cuisines: List<String>,
        keywords: List<String>,
        diets: List<String> = emptyList(),
    ): List<String> {
        fun split(values: List<String>) = values.flatMap { HtmlText.decodeEntities(it).split(',', ';', '|') }
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val page = mutableListOf<String>()
        split(categories).forEach { c -> courses[c]?.let(page::add) }
        split(diets).forEach { d -> this.diets[d.substringAfterLast('/').replace(" ", "")]?.let(page::add) }
        split(cuisines).forEach { c ->
            val t = Tags.normalize(c)
            if (t.isNotEmpty() && t.length <= 14 && '-' !in t && t !in setOf("world", "global", "fusion", "international", "other")) page += t
        }
        split(keywords).forEach { k -> keywordTags[k]?.let(page::add) }

        var guessed = TextRecipeParser.guessTags(title, ingredientText, stepText, totalMinutes)
        // The page's course beats the rules' default "dinner".
        if (page.any { it in mealCourses } && "dinner" !in page) guessed = guessed - "dinner"
        if ("vegan" in page) guessed = guessed - "vegetarian"
        return (page + guessed).distinct().take(5)
    }
}
