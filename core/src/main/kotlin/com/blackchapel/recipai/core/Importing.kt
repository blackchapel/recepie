package com.blackchapel.recipai.core

/**
 * Turns an import preview into a saved recipe. [newId] makes ids for the
 * recipe and each of its rows (the app passes random UUIDs; tests pass a
 * counter).
 */
fun ParsedRecipe.toRecipe(now: Long, source: String? = null, newId: () -> String): Recipe {
    val id = newId()
    return Recipe(
        id = id,
        title = title.ifBlank { "Imported recipe" },
        description = description,
        servings = servings.coerceAtLeast(1),
        tags = tags,
        ingredients = ingredients.map {
            Ingredient(
                id = newId(),
                name = it.name,
                quantity = if (it.isHeading) null else it.quantity,
                unit = if (it.isHeading) "" else it.unit,
                note = if (it.isHeading) "" else it.note,
                isHeading = it.isHeading,
            )
        },
        steps = steps.map { Step(newId(), it.text, it.minutes, it.timeScale) },
        notes = notes,
        source = source,
        createdAt = now,
        updatedAt = now,
    )
}
