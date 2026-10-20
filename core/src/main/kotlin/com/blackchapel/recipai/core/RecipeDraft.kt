package com.blackchapel.recipai.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** One ingredient row in the editor, exactly as typed. */
data class DraftIngredient(
    val id: String,
    val quantity: String = "",
    val unit: String = "",
    val name: String = "",
    val note: String = "",
    /** A section heading ("For the sauce"): only [name] is used. */
    val isHeading: Boolean = false,
) {
    val isBlank: Boolean
        get() = name.isBlank() && (isHeading || (quantity.isBlank() && unit.isBlank() && note.isBlank()))

    /** The quantity, or null when there is none or it isn't a number. */
    val quantityValue: Double? get() = if (isHeading) null else Quantities.parse(quantity)

    /** Something is typed in the quantity that Recipai can't read as an amount. */
    val quantityInvalid: Boolean get() = !isHeading && quantity.isNotBlank() && quantityValue == null

    /** An amount, unit or note without a name: saving would drop it. */
    val nameMissing: Boolean get() = !isHeading && name.isBlank() && !isBlank
}

/** One method step in the editor, as typed. */
data class DraftStep(
    val id: String,
    val text: String = "",
    /** Minutes as typed ("12", "1.5"); blank means no timer. */
    val minutes: String = "",
    val timeScale: TimeScale = TimeScale.FIXED,
) {
    val minutesValue: Double get() = Quantities.parse(minutes) ?: 0.0
    val minutesInvalid: Boolean get() = minutes.isNotBlank() && Quantities.parse(minutes) == null

    /**
     * A time found in the step's text while it has no minutes of its own
     * (the "Found 12 min · use it" chip); null when there is nothing to offer.
     */
    val detectedMinutes: Double?
        get() = if (minutesValue > 0 || minutesInvalid || text.isBlank()) null
        else TextRecipeParser.detectMinutes(text).takeIf { it > 0 }
}

/** What stops a draft from saving. Ids are ingredient or step row ids. */
data class DraftErrors(
    val titleMissing: Boolean = false,
    val badQuantities: Set<String> = emptySet(),
    val missingNames: Set<String> = emptySet(),
    val badMinutes: Set<String> = emptySet(),
) {
    val any: Boolean get() = titleMissing || badQuantities.isNotEmpty() || missingNames.isNotEmpty() || badMinutes.isNotEmpty()
}

/**
 * The recipe editor's working copy: every field as text, the way the user
 * typed it, so nothing is lost or reformatted while editing. [toRecipe]
 * turns it into a [Recipe] when it is saved. Immutable: each edit returns a
 * new draft, so "unsaved changes" is a plain comparison with the draft the
 * editor opened with.
 */
data class RecipeDraft(
    /** The recipe being edited; null for a new one (including an import being reviewed). */
    val recipeId: String?,
    val title: String = "",
    val description: String = "",
    val servings: String = "4",
    val servingsLabel: String = "",
    val tags: List<String> = emptyList(),
    val ingredients: List<DraftIngredient> = emptyList(),
    val steps: List<DraftStep> = emptyList(),
    val notes: String = "",
    val photos: List<String> = emptyList(),
    val coverIndex: Int = 0,
    /** Where an imported recipe came from (kept as [Recipe.source]). */
    val source: String? = null,
) {
    val isNew: Boolean get() = recipeId == null

    /** Ingredient rows that aren't section headings. */
    val ingredientCount: Int get() = ingredients.count { !it.isHeading }

    fun errors(): DraftErrors = DraftErrors(
        titleMissing = title.isBlank(),
        badQuantities = ingredients.filter { it.quantityInvalid }.mapTo(LinkedHashSet()) { it.id },
        missingNames = ingredients.filter { it.nameMissing }.mapTo(LinkedHashSet()) { it.id },
        badMinutes = steps.filter { it.minutesInvalid }.mapTo(LinkedHashSet()) { it.id },
    )

    /** The servings to save: the typed whole number (1 to 999), else [fallback]. */
    fun servingsValue(fallback: Int = 4): Int =
        servings.trim().toIntOrNull()?.takeIf { it >= 1 }?.coerceAtMost(MAX_SERVINGS) ?: fallback.coerceAtLeast(1)

    /**
     * The recipe to save. [original] is the saved version being replaced (its
     * favorite and creation time carry over); [newId] names a new recipe.
     * Blank rows and steps are dropped; units are tidied ("Tablespoons" to
     * "tbsp") so scaling understands them. Check [errors] first.
     */
    fun toRecipe(now: Long, original: Recipe?, newId: () -> String): Recipe {
        val keptPhotos = photos
        // An amount left as it was shown keeps its exact stored value (⅓ is
        // shown rounded, and reading "⅓" back gives 0.333).
        val before = original?.ingredients?.associateBy { it.id }.orEmpty()
        fun quantityOf(row: DraftIngredient): Double? {
            val old = before[row.id]
            return if (old != null && !old.isHeading && quantityText(old.quantity) == row.quantity.trim()) old.quantity else row.quantityValue
        }
        return Recipe(
            id = recipeId ?: newId(),
            title = title.trim(),
            description = description.trim(),
            servings = servingsValue(original?.servings ?: 4),
            servingsLabel = servingsLabel.trim().takeIf { it.isNotEmpty() && !it.equals("servings", ignoreCase = true) },
            tags = tags.distinct(),
            ingredients = ingredients.filter { it.name.isNotBlank() }.map {
                if (it.isHeading) {
                    Ingredient(id = it.id, name = it.name.trim(), isHeading = true)
                } else {
                    Ingredient(
                        id = it.id,
                        name = it.name.trim(),
                        quantity = quantityOf(it),
                        unit = TextRecipeParser.canonicalUnit(it.unit),
                        note = it.note.trim(),
                    )
                }
            },
            steps = steps.filter { it.text.isNotBlank() }.map {
                Step(id = it.id, text = it.text.trim(), minutes = it.minutesValue.coerceIn(0.0, MAX_MINUTES), timeScale = it.timeScale)
            },
            notes = notes.trim(),
            favorite = original?.favorite ?: false,
            source = source ?: original?.source,
            isSample = false,
            photos = keptPhotos,
            coverIndex = if (keptPhotos.isEmpty()) 0 else coverIndex.coerceIn(0, keptPhotos.lastIndex),
            createdAt = original?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
    }

    /**
     * A stand-in recipe for what the draft would look like (the photo strip's
     * illustration). Never saved.
     */
    fun preview(): Recipe = Recipe(
        id = recipeId ?: "draft",
        title = title.trim().ifEmpty { "Untitled" },
        servings = servingsValue(),
        tags = tags,
        ingredients = ingredients.filter { it.name.isNotBlank() }.map {
            Ingredient(it.id, it.name.trim(), it.quantityValue, it.unit.trim(), it.note.trim(), it.isHeading)
        },
        steps = steps.filter { it.text.isNotBlank() }.map { Step(it.id, it.text.trim(), it.minutesValue, it.timeScale) },
        photos = photos,
        coverIndex = coverIndex,
    )

    // ---- Ingredients ----

    fun updateIngredient(id: String, transform: (DraftIngredient) -> DraftIngredient): RecipeDraft =
        copy(ingredients = ingredients.map { if (it.id == id) transform(it) else it })

    /** Adds an empty ingredient (or a section heading) after [afterId], or at the end. */
    fun addIngredient(id: String, heading: Boolean = false, afterId: String? = null): RecipeDraft {
        val row = DraftIngredient(id = id, isHeading = heading)
        val at = afterId?.let { a -> ingredients.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }?.plus(1) ?: ingredients.size
        return copy(ingredients = ingredients.toMutableList().apply { add(at, row) })
    }

    fun removeIngredient(id: String): RecipeDraft = copy(ingredients = ingredients.filterNot { it.id == id })

    /** Moves an ingredient one place up ([delta] -1) or down (+1). Unchanged at either end. */
    fun moveIngredient(id: String, delta: Int): RecipeDraft = copy(ingredients = ingredients.moved(id, delta) { it.id })

    /**
     * Several lines pasted into an ingredient's name: each line becomes an
     * ingredient (amount, unit, name and note read the way import reads
     * them; a short line ending in a colon becomes a section heading). They
     * replace the row if it was empty, else go right after it. Returns the
     * new draft and how many rows were added, or null when [pasted] isn't
     * several lines (then it is just typing).
     */
    fun pasteIngredients(rowId: String, pasted: String, newId: () -> String): Pair<RecipeDraft, Int>? {
        val index = ingredients.indexOfFirst { it.id == rowId }
        if (index < 0) return null
        val rows = parseIngredientLines(pasted, newId) ?: return null
        val row = ingredients[index]
        val list = ingredients.toMutableList()
        if (row.isBlank) {
            list.removeAt(index)
            list.addAll(index, rows)
        } else {
            list.addAll(index + 1, rows)
        }
        return copy(ingredients = list) to rows.size
    }

    // ---- Steps ----

    fun updateStep(id: String, transform: (DraftStep) -> DraftStep): RecipeDraft =
        copy(steps = steps.map { if (it.id == id) transform(it) else it })

    fun addStep(id: String, afterId: String? = null): RecipeDraft {
        val at = afterId?.let { a -> steps.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }?.plus(1) ?: steps.size
        return copy(steps = steps.toMutableList().apply { add(at, DraftStep(id = id)) })
    }

    fun removeStep(id: String): RecipeDraft = copy(steps = steps.filterNot { it.id == id })

    fun moveStep(id: String, delta: Int): RecipeDraft = copy(steps = steps.moved(id, delta) { it.id })

    /** Takes the time found in a step's text, and guesses how it scales from its verbs. */
    fun useDetectedTime(stepId: String): RecipeDraft = updateStep(stepId) { s ->
        val found = s.detectedMinutes ?: return@updateStep s
        s.copy(minutes = minutesText(found), timeScale = TextRecipeParser.guessTimeScale(s.text))
    }

    // ---- Tags ----

    /** Adds a tag (normalized: "Quick Dinner" -> "quick-dinner"). Unchanged if empty or already there. */
    fun addTag(raw: String): RecipeDraft {
        val t = Tags.normalize(raw)
        return if (t.isEmpty() || t in tags) this else copy(tags = tags + t)
    }

    fun removeTag(tag: String): RecipeDraft = copy(tags = tags - tag)

    /**
     * Tags to offer under the tag field: first what the recipe itself
     * suggests (the importer's [TextRecipeParser.guessTags]), then the
     * library's tags (most used first), then a few common ones. Skips tags
     * already added; with a [query], only tags containing it.
     */
    fun tagSuggestions(libraryTags: List<String>, query: String = "", limit: Int = 8): List<String> {
        val q = Tags.normalize(query)
        val named = ingredients.filter { !it.isHeading && it.name.isNotBlank() }
        val guessed = if (title.isBlank() && named.isEmpty()) emptyList() else TextRecipeParser.guessTags(
            title,
            named.joinToString(" ") { it.name },
            steps.joinToString(" ") { it.text },
            steps.sumOf { it.minutesValue },
        )
        return (guessed + libraryTags + COMMON_TAGS)
            .asSequence()
            .distinct()
            .filter { it !in tags && (q.isEmpty() || it.contains(q)) }
            .take(limit)
            .toList()
    }

    companion object {
        const val MAX_SERVINGS = 999
        /** A week: anything longer is a typo, not a step. */
        const val MAX_MINUTES = 7 * 24 * 60.0

        val COMMON_TAGS = listOf("quick", "vegetarian", "dinner", "breakfast", "dessert", "make-ahead")

        /** A new recipe: one empty ingredient row and one empty step, ready to type into. */
        fun blank(newId: () -> String): RecipeDraft = RecipeDraft(
            recipeId = null,
            ingredients = listOf(DraftIngredient(newId())),
            steps = listOf(DraftStep(newId())),
        )

        /**
         * A draft of a saved recipe. [asNew] starts a new recipe from it
         * instead (an import being reviewed). Empty ingredient and method
         * lists get one empty row each, ready to type into.
         */
        fun from(recipe: Recipe, newId: () -> String, asNew: Boolean = false): RecipeDraft = RecipeDraft(
            recipeId = if (asNew) null else recipe.id,
            title = recipe.title,
            description = recipe.description,
            servings = recipe.servings.toString(),
            servingsLabel = recipe.servingsLabel.orEmpty(),
            tags = recipe.tags,
            ingredients = recipe.ingredients.map {
                DraftIngredient(
                    id = it.id,
                    quantity = if (it.isHeading) "" else quantityText(it.quantity),
                    unit = if (it.isHeading) "" else it.unit,
                    name = it.name,
                    note = if (it.isHeading) "" else it.note,
                    isHeading = it.isHeading,
                )
            }.ifEmpty { listOf(DraftIngredient(newId())) },
            steps = recipe.steps.map { DraftStep(it.id, it.text, minutesText(it.minutes), it.timeScale) }
                .ifEmpty { listOf(DraftStep(newId())) },
            notes = recipe.notes,
            photos = recipe.photos,
            coverIndex = recipe.coverIndex,
            source = recipe.source,
        )

        /** An import preview opened for review ("Review" in the import sheet). */
        fun fromImport(parsed: ParsedRecipe, newId: () -> String): RecipeDraft =
            from(parsed.toRecipe(now = 0L, newId = newId).copy(title = parsed.title), newId, asNew = true)

        /** An amount for editing: "1½" for kitchen fractions, else up to 3 decimals; "" for none. */
        fun quantityText(q: Double?): String {
            if (q == null || q.isNaN() || q.isInfinite()) return ""
            val rest = q - floor(q)
            val niceFraction = rest < 1e-6 || KITCHEN_FRACTIONS.any { abs(rest - it) < 1e-6 }
            return if (niceFraction) Quantities.toFraction(q) else Quantities.trimmed(q, 3)
        }

        /** Minutes for editing: "12", "1.5"; "" for no time. */
        fun minutesText(m: Double): String =
            if (m <= 0 || m.isNaN()) "" else Quantities.trimmed((m * 100).roundToInt() / 100.0, 2)

        /**
         * Pasted ingredient lines as rows, or null for fewer than two
         * non-empty lines.
         */
        fun parseIngredientLines(pasted: String, newId: () -> String): List<DraftIngredient>? {
            val lines = pasted.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) return null
            val rows = lines.mapNotNull { line ->
                val heading = line.removeSuffix(":").trim()
                when {
                    INGREDIENTS_LINE.matches(line) -> null
                    line.endsWith(":") && heading.isNotEmpty() && heading.length <= 40 &&
                        TextRecipeParser.parseIngredientLine(heading)?.quantity == null ->
                        DraftIngredient(id = newId(), name = heading, isHeading = true)
                    else -> {
                        val p = TextRecipeParser.parseIngredientLine(line)
                        if (p == null) {
                            null
                        } else {
                            DraftIngredient(id = newId(), quantity = quantityText(p.quantity), unit = p.unit, name = p.name, note = p.note)
                        }
                    }
                }
            }
            return rows.takeIf { it.isNotEmpty() }
        }

        private val KITCHEN_FRACTIONS = listOf(1.0 / 8, 1.0 / 4, 1.0 / 3, 3.0 / 8, 1.0 / 2, 5.0 / 8, 2.0 / 3, 3.0 / 4, 7.0 / 8)
        private val INGREDIENTS_LINE = Regex("^(?:#+\\s*)?ingredients?\\s*:?$", RegexOption.IGNORE_CASE)
    }
}

/** This list with the element whose key is [id] swapped with its neighbour [delta] away. */
private fun <T> List<T>.moved(id: String, delta: Int, key: (T) -> String): List<T> {
    val i = indexOfFirst { key(it) == id }
    val j = i + delta
    if (i < 0 || j < 0 || j > lastIndex || i == j) return this
    return toMutableList().also {
        val tmp = it[i]
        it[i] = it[j]
        it[j] = tmp
    }
}
