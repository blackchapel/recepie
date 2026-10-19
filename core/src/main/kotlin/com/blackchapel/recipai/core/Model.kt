package com.blackchapel.recipai.core

import kotlinx.serialization.Serializable

/** How a step's time changes when the recipe is scaled. */
@Serializable
enum class TimeScale {
    /** Oven, resting, chilling: the same however much you make. */
    FIXED,

    /** Simmering, reducing: grows with the square root of the factor. */
    PARTIAL,

    /** Chopping, frying in batches: grows with the batch. */
    LINEAR,
}

@Serializable
data class Ingredient(
    val id: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val note: String = "",
    /** A section label such as "For the sauce" rather than an ingredient. */
    val isHeading: Boolean = false,
)

@Serializable
data class Step(
    val id: String,
    val text: String,
    val minutes: Double = 0.0,
    val timeScale: TimeScale = TimeScale.FIXED,
)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val description: String = "",
    val servings: Int,
    /** What a serving is called, e.g. "cookies". Null means "servings". */
    val servingsLabel: String? = null,
    val tags: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    val source: String? = null,
    val isSample: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val itemCount: Int get() = ingredients.count { !it.isHeading }
}
