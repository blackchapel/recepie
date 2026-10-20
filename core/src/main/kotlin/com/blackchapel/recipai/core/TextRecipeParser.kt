package com.blackchapel.recipai.core

/**
 * Rule-based import of recipe text copied from a web page, a message or a
 * note. Headings (Ingredients / Method / Notes) are used when present; without
 * them each line is classified on its own. Ported from the approved prototype
 * (docs/design/recipai-prototype.html), which is the behavior reference.
 */
object TextRecipeParser : RecipeImporter {
    private val i = RegexOption.IGNORE_CASE

    private val unitAliases: Map<String, String> = linkedMapOf(
        "tsp" to listOf("tsp", "tsps", "teaspoon", "teaspoons"),
        "tbsp" to listOf("tbsp", "tbsps", "tbs", "tbl", "tablespoon", "tablespoons"),
        "cup" to listOf("cup", "cups"),
        "fl oz" to listOf("fl oz", "fluid ounce", "fluid ounces"),
        "oz" to listOf("oz", "ounce", "ounces"),
        "lb" to listOf("lb", "lbs", "pound", "pounds"),
        "g" to listOf("g", "gram", "grams", "gr"),
        "kg" to listOf("kg", "kilogram", "kilograms"),
        "ml" to listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres"),
        "l" to listOf("l", "liter", "liters", "litre", "litres"),
        "clove" to listOf("clove", "cloves"),
        "pinch" to listOf("pinch", "pinches"),
        "can" to listOf("can", "cans", "tin", "tins"),
        "bunch" to listOf("bunch", "bunches"),
        "sprig" to listOf("sprig", "sprigs"),
        "handful" to listOf("handful", "handfuls"),
        "slice" to listOf("slice", "slices"),
        "stick" to listOf("stick", "sticks"),
        "sheet" to listOf("sheet", "sheets"),
        "block" to listOf("block", "blocks"),
        "piece" to listOf("piece", "pieces"),
        "dash" to listOf("dash", "dashes"),
    ).flatMap { (unit, aliases) -> aliases.map { it to unit } }.toMap()

    private val unitRe = Regex(
        "^(" + unitAliases.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", "\\s+") } +
            ")\\.?(?=[\\s,)]|$)",
        i,
    )
    private val qtyRe = Regex(
        "^(\\d+\\s+\\d+\\s*/\\s*\\d+|\\d+\\s*/\\s*\\d+|\\d+(?:[.,]\\d+)?)(?:\\s*(?:-|–|to)\\s*(\\d+\\s*/\\s*\\d+|\\d+(?:[.,]\\d+)?))?\\s*(?:x\\s+)?",
        i,
    )
    private val ingredientsHeading = Regex("^(?:#+\\s*)?(ingredients?|you(?:'|’)?ll need|what you(?:'|’)?ll need|shopping list)\\s*:?\\s*$", i)
    private val stepsHeading = Regex("^(?:#+\\s*)?(instructions?|directions?|method|steps|preparation|how to make(?: it)?)\\s*:?\\s*$", i)
    private val notesHeading = Regex("^(?:#+\\s*)?(notes?|tips?|recipe notes?|cook'?s notes?)\\s*:?\\s*$", i)
    private val skipHeading = Regex("^(?:#+\\s*)?(nutrition(?: facts| information)?|equipment|reviews?|comments?|related recipes?|more recipes|you may also like)\\s*:?\\s*$", i)
    private val junk = Regex(
        "(jump to recipe|print recipe|pin recipe|pin it|skip to content|rate this recipe|leave a (comment|review)|did you make this|tag us|share this|advertisement|sponsored|subscribe|newsletter|cook mode|prevent your screen|save recipe|★|☆|\\b\\d+\\s+(reviews?|ratings?|comments?)\\b|»|›|^(course|cuisine|keyword|author|calories)\\s*:|^recipe by\\b)",
        i,
    )
    private val metaTime = Regex("\\b(prep|cook|total|active|inactive)\\s*time\\b", i)
    private val metaServings = Regex("\\b(?:serves|servings|yield|makes)\\s*:?\\s*(\\d+)", i)
    private val fixedVerbs = Regex("\\b(bake|roast|simmer|boil|rest|chill|refrigerate|marinate|proof|rise|steep|cool|freeze|braise|steam|soak|press|blend|preheat|poach)\\w*", i)
    private val batchVerbs = Regex("\\b(chop|dice|slice|mince|peel|knead|shape|roll|grate|batches|scoop|divide|arrange|assemble|portion|form|stuff|skewer)\\w*", i)
    private val timeRe = Regex("(\\d+(?:[.,]\\d+)?)(?:\\s*(?:-|–|to)\\s*(\\d+(?:[.,]\\d+)?))?\\s*(hours?|hrs?|h|minutes?|mins?|seconds?|secs?)\\b", i)
    private val leadingBullets = Regex("^[\\s\\-–—•*▢□◻☐✓✔·>]+")
    private val leadingBulletsLoose = Regex("^[\\s\\-–—•*▢□◻☐·]+")
    private val stepNumber = Regex("^\\s*(?:step\\s*)?\\d+\\s*[.):\\-–]\\s*", i)
    private val stepBullet = Regex("^[\\s\\-–•*·]+")
    private val spaces = Regex("\\s+")

    private val tagRules: List<Pair<Regex, String>> = listOf(
        Regex("gochujang|kimchi|gochugaru", i) to "korean",
        Regex("gochujang|chil[ei]|sriracha|cayenne|jalape|harissa|curry paste", i) to "spicy",
        Regex("miso|ramen|dashi|mirin", i) to "japanese",
        Regex("orzo|pasta|spaghetti|penne|risotto|parmesan|pecorino", i) to "italian",
        Regex("curry", i) to "curry",
        Regex("\\bsalad\\b", i) to "salad",
        Regex("pancake|waffle|granola|porridge|\\boats?\\b", i) to "breakfast",
        Regex("\\b(smoothie|lassi|juice|cocktail|latte)s?\\b", i) to "drinks",
        Regex("cookie|brownie|\\bcake\\b|tart|pudding|ice cream", i) to "dessert",
        Regex("one[- ]pot", i) to "one-pot",
        Regex("one[- ]pan|sheet[- ]pan", i) to "one-pan",
        Regex("\\bbowls?\\b", i) to "bowls",
    )
    // Drink words ("lemon juice") are everywhere in ingredients and steps, so
    // like the dish-shape tags they only count when the title says so.
    private val titleOnlyTags = setOf("one-pot", "one-pan", "bowls", "drinks")
    private val meat = Regex("\\b(chicken|beef|pork|lamb|bacon|ham|sausage|turkey|fish|shrimp|prawns?|salmon|tuna|anchov|duck|chorizo|pancetta|fish sauce)\\b", i)

    /** The longest time mentioned in a step, in minutes: "10 to 12 minutes" -> 12, "1 hour 20 minutes" -> 80. */
    fun detectMinutes(text: String): Double {
        val s = Quantities.normalizeFractions(text)
        var best = 0.0
        var prevHourEnd = -1
        var prevHourMinutes = 0.0
        for (m in timeRe.findAll(s)) {
            val v = (m.groupValues[2].ifEmpty { m.groupValues[1] }).replace(',', '.').toDouble()
            val u = m.groupValues[3].lowercase()
            var mins = when (u[0]) { 'h' -> v * 60; 's' -> v / 60; else -> v }
            if (prevHourEnd >= 0 && u[0] == 'm' && m.range.first - prevHourEnd < 7) mins += prevHourMinutes
            if (u[0] == 'h') { prevHourEnd = m.range.last + 1; prevHourMinutes = mins } else prevHourEnd = -1
            best = maxOf(best, mins)
        }
        return if (best < 10) Math.round(best * 2) / 2.0 else Math.round(best).toDouble()
    }

    /** A sensible default for how a step's time scales, from its verbs. */
    fun guessTimeScale(text: String): TimeScale = when {
        fixedVerbs.containsMatchIn(text) -> TimeScale.FIXED
        batchVerbs.containsMatchIn(text) -> TimeScale.LINEAR
        else -> TimeScale.PARTIAL
    }

    /**
     * A typed unit in the short form scaling understands: "Tablespoons" ->
     * "tbsp", "Grams" -> "g". Units it doesn't know come back trimmed, as typed.
     */
    fun canonicalUnit(raw: String): String {
        val typed = raw.trim()
        val key = typed.removeSuffix(".").replace(spaces, " ").lowercase()
        return unitAliases[key] ?: typed
    }

    /** "2 cloves garlic, minced" -> 2 clove "garlic" (minced). Null for an empty line. */
    fun parseIngredientLine(line: String): ParsedIngredient? {
        var s = Quantities.normalizeFractions(line).replace(leadingBullets, "").replace(spaces, " ").trim()
        if (s.isEmpty()) return null
        var quantity: Double? = null
        var unit = ""
        val notes = mutableListOf<String>()
        qtyRe.find(s)?.takeIf { it.value.isNotBlank() }?.let { m ->
            quantity = Quantities.parse(m.groupValues[1].replace(',', '.'))
            if (m.groupValues[2].isNotEmpty()) notes += "${m.groupValues[1]}–${m.groupValues[2]}"
            s = s.substring(m.value.length)
        }
        Regex("^\\(([^)]*)\\)\\s*").find(s)?.let { notes += it.groupValues[1]; s = s.substring(it.value.length) }
        if (quantity != null) {
            unitRe.find(s)?.let { m ->
                unit = unitAliases[m.groupValues[1].lowercase().replace(spaces, " ")] ?: ""
                s = s.substring(m.value.length).trim().replace(Regex("^of\\s+", i), "")
            }
        }
        var name = s
        Regex("\\(([^)]*)\\)").find(name)?.let { notes += it.groupValues[1]; name = name.replaceFirst(it.value, "").replace(spaces, " ").trim() }
        val comma = name.indexOf(',')
        if (comma > 0) { notes += name.substring(comma + 1).trim(); name = name.substring(0, comma).trim() }
        if (Regex("\\boptional\\b", i).containsMatchIn(name)) {
            name = name.replaceFirst(Regex("\\s*\\boptional\\b\\s*", i), " ").trim(); notes += "optional"
        }
        name = name.replace(spaces, " ").trim()
        if (name.isEmpty()) return null
        if (quantity != null && name[0].isUpperCase()) name = name[0].lowercase() + name.substring(1)
        return ParsedIngredient(name, quantity, unit, notes.filter { it.isNotEmpty() }.joinToString(", "))
    }

    private fun looksLikeIngredient(line: String): Boolean =
        qtyRe.find(Quantities.normalizeFractions(line.replace(leadingBulletsLoose, "")))?.value?.isNotEmpty() == true ||
            Regex("^(salt|pepper|a pinch|pinch|handful)\\b", i).containsMatchIn(line) ||
            Regex("\\bto taste\\b", i).containsMatchIn(line)

    private fun isSubHeading(line: String): Boolean =
        line.length < 34 && !Regex("[,.]$").containsMatchIn(line) && qtyRe.find(line)?.value.isNullOrEmpty() &&
            (Regex(":\\s*$").containsMatchIn(line) || Regex("^(for|to)\\s+(the\\s+)?\\w+", i).containsMatchIn(line) ||
                (line == line.uppercase() && line.any { it in 'A'..'Z' }))

    fun guessTags(title: String, ingredientText: String, stepText: String, totalMinutes: Double): List<String> {
        val all = "$title $ingredientText $stepText"
        val tags = mutableListOf<String>()
        for ((re, tag) in tagRules) {
            if (re.containsMatchIn(if (tag in titleOnlyTags) title else all) && tag !in tags) tags += tag
        }
        if (!meat.containsMatchIn(ingredientText)) tags += "vegetarian"
        if (totalMinutes > 0 && totalMinutes <= 30) tags += "quick"
        if (tags.none { it in setOf("breakfast", "dessert", "drinks", "salad") }) tags.add(0, "dinner")
        return tags.distinct().take(5)
    }

    private enum class Section { PRE, AUTO, INGREDIENTS, STEPS, NOTES, SKIP }

    override fun parse(text: String): ParsedRecipe {
        val lines = text.replace("\r", "").split("\n").map { it.replace("\t", " ").trim() }
        val hasHeaders = lines.any { ingredientsHeading.containsMatchIn(it) } || lines.any { stepsHeading.containsMatchIn(it) }
        var section = Section.PRE
        var title = ""
        var description = ""
        var servings: Int? = null
        var skipped = 0
        val ingredients = mutableListOf<ParsedIngredient>()
        val steps = mutableListOf<ParsedStep>()
        val notes = mutableListOf<String>()

        for (raw in lines) {
            val line = raw.replace(Regex("\\s{2,}"), " ")
            if (line.isEmpty()) continue
            when {
                ingredientsHeading.containsMatchIn(line) -> { section = Section.INGREDIENTS; continue }
                stepsHeading.containsMatchIn(line) -> { section = Section.STEPS; continue }
                notesHeading.containsMatchIn(line) -> { section = Section.NOTES; continue }
                skipHeading.containsMatchIn(line) -> { section = Section.SKIP; continue }
                junk.containsMatchIn(line) -> { skipped++; continue }
            }
            val serves = metaServings.find(line)
            if (serves != null && line.length < 60) { servings = servings ?: serves.groupValues[1].toInt(); continue }
            if (metaTime.containsMatchIn(line) && line.length < 80) { skipped++; continue }
            if (section == Section.SKIP) { skipped++; continue }
            if (!hasHeaders && section == Section.PRE && title.isNotEmpty() && looksLikeIngredient(line)) section = Section.AUTO
            if (section == Section.PRE) {
                when {
                    title.isEmpty() && line.length < 90 && !Regex("[.!?]$").containsMatchIn(line) -> title = line.replace(Regex("^#+\\s*"), "")
                    title.isNotEmpty() && description.isEmpty() && line.length > 30 -> description = line
                    else -> skipped++
                }
                continue
            }
            if (section == Section.INGREDIENTS || (section == Section.AUTO && looksLikeIngredient(line))) {
                if (section == Section.INGREDIENTS && isSubHeading(line)) {
                    val name = line.replace(Regex(":\\s*$"), "").replace(Regex("^for\\s+the\\s+", i), "For the ").trim()
                    ingredients += ParsedIngredient(name, isHeading = true)
                    continue
                }
                parseIngredientLine(line)?.let { ingredients += it } ?: skipped++
                continue
            }
            if (section == Section.STEPS || section == Section.AUTO) {
                val stepText = line.replace(stepNumber, "").replace(stepBullet, "").trim()
                if (stepText.length < 12) { skipped++; continue }
                steps += ParsedStep(stepText, detectMinutes(stepText), guessTimeScale(stepText))
                continue
            }
            if (section == Section.NOTES) notes += line
        }

        val ingredientText = ingredients.joinToString(" ") { it.name }
        val stepText = steps.joinToString(" ") { it.text }
        val total = steps.sumOf { it.minutes }
        return ParsedRecipe(
            title = title,
            description = description,
            servings = servings ?: 4,
            servingsGuessed = lines.none { metaServings.containsMatchIn(it) },
            ingredients = ingredients,
            steps = steps,
            notes = notes.joinToString("\n"),
            tags = if (title.isNotEmpty() || ingredients.isNotEmpty()) guessTags(title, ingredientText, stepText, total) else emptyList(),
            skippedLines = skipped,
        )
    }
}
