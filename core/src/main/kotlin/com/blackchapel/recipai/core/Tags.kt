package com.blackchapel.recipai.core

/** The six tag colors from the design (see docs/DESIGN.md, "Tag colors"). */
enum class TagColor { SAFFRON, PAPRIKA, BASIL, UBE, SUMAC, OCEAN }

object Tags {
    private val pinned = mapOf(
        "breakfast" to TagColor.SAFFRON, "dinner" to TagColor.PAPRIKA, "vegetarian" to TagColor.BASIL,
        "dessert" to TagColor.SUMAC, "spicy" to TagColor.PAPRIKA, "drinks" to TagColor.OCEAN,
        "baking" to TagColor.UBE, "quick" to TagColor.OCEAN, "salad" to TagColor.BASIL,
        "japanese" to TagColor.SUMAC, "thai" to TagColor.BASIL, "italian" to TagColor.UBE,
        "korean" to TagColor.SUMAC, "sweet" to TagColor.SUMAC, "noodles" to TagColor.SAFFRON,
        "gluten-free" to TagColor.OCEAN, "one-pan" to TagColor.SAFFRON, "one-pot" to TagColor.SAFFRON,
    )

    /** A stable color per tag: pinned for common tags, otherwise by name hash. */
    fun colorOf(tag: String): TagColor =
        pinned[tag] ?: TagColor.entries[(fnv1a(tag) % TagColor.entries.size.toUInt()).toInt()]

    /** "  Quick Dinner! " -> "quick-dinner". */
    fun normalize(raw: String): String = raw.trim().lowercase().removePrefix("#")
        .replace(Regex("\\s+"), "-").replace(Regex("[^a-z0-9\\-]"), "").take(24)

    private fun fnv1a(s: String): UInt {
        var h = 2166136261u
        for (c in s) {
            h = h xor c.code.toUInt()
            h *= 16777619u
        }
        return h
    }
}
