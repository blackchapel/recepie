package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeDraftTest {
    private fun ids(): () -> String {
        var n = 0
        return { "id${++n}" }
    }

    private val saved = Recipe(
        id = "r1",
        title = "Weeknight dal",
        description = "Cozy.",
        servings = 4,
        servingsLabel = "bowls",
        tags = listOf("dinner", "vegetarian"),
        ingredients = listOf(
            Ingredient("h1", "For the tadka", isHeading = true),
            Ingredient("i1", "red lentils", 1.5, "cup", "rinsed"),
            Ingredient("i2", "turmeric", 0.333333, "tsp"),
            Ingredient("i3", "ghee", 0.15, "kg"),
            Ingredient("i4", "salt"),
        ),
        steps = listOf(
            Step("s1", "Simmer the lentils for 25 minutes.", 25.0, TimeScale.PARTIAL),
            Step("s2", "Season and serve.", 0.0, TimeScale.FIXED),
        ),
        notes = "Freezes well.",
        favorite = true,
        source = "example.com/dal",
        isSample = true,
        photos = listOf("a.jpg", "b.jpg"),
        coverIndex = 1,
        createdAt = 100,
        updatedAt = 200,
    )

    @Test fun blankDraftHasOneEmptyRowOfEach() {
        val d = RecipeDraft.blank(ids())
        assertTrue(d.isNew)
        assertEquals(1, d.ingredients.size)
        assertEquals(1, d.steps.size)
        assertTrue(d.ingredients.single().isBlank)
        assertTrue(d.errors().titleMissing)
    }

    @Test fun editingASavedRecipeShowsItsAmountsAsTyped() {
        val d = RecipeDraft.from(saved, ids())
        assertEquals("r1", d.recipeId)
        assertEquals("4", d.servings)
        assertEquals("bowls", d.servingsLabel)
        assertEquals(listOf("", "1½", "⅓", "0.15", ""), d.ingredients.map { it.quantity })
        assertEquals("For the tadka", d.ingredients[0].name)
        assertTrue(d.ingredients[0].isHeading)
        assertEquals(listOf("25", ""), d.steps.map { it.minutes })
        assertFalse(d.errors().any)
    }

    @Test fun anUnchangedDraftSavesTheSameRecipe() {
        val d = RecipeDraft.from(saved, ids())
        val r = d.toRecipe(now = 300, original = saved) { error("no new ids") }
        assertEquals(saved.copy(isSample = false, updatedAt = 300), r)
        // A changed amount is read from the text.
        val more = d.updateIngredient("i2") { it.copy(quantity = "1/2") }.toRecipe(now = 300, original = saved) { "n" }
        assertEquals(0.5, more.ingredients.first { it.id == "i2" }.quantity)
    }

    @Test fun savingANewRecipeTrimsDropsBlankRowsAndTidiesUnits() {
        val d = RecipeDraft.blank(ids()).copy(
            title = "  Pancakes ",
            servings = "",
            servingsLabel = "Servings",
            ingredients = listOf(
                DraftIngredient("a", "1 1/2", "Cups", " flour ", " sifted "),
                DraftIngredient("b"),
                DraftIngredient("h", name = "  ", isHeading = true),
                DraftIngredient("c", "2", "tablespoons.", "sugar"),
                DraftIngredient("d", "", "heads", "garlic"),
            ),
            steps = listOf(DraftStep("s", " Whisk. ", "1,5", TimeScale.LINEAR), DraftStep("t", "   ")),
        )
        assertFalse(d.errors().any)
        val r = d.toRecipe(now = 50, original = null) { "new" }
        assertEquals("new", r.id)
        assertEquals("Pancakes", r.title)
        assertEquals(4, r.servings)
        assertNull(r.servingsLabel)
        assertEquals(listOf("flour", "sugar", "garlic"), r.ingredients.map { it.name })
        assertEquals(listOf(1.5, 2.0, null), r.ingredients.map { it.quantity })
        assertEquals(listOf("cup", "tbsp", "heads"), r.ingredients.map { it.unit })
        assertEquals("sifted", r.ingredients[0].note)
        assertEquals(listOf(Step("s", "Whisk.", 1.5, TimeScale.LINEAR)), r.steps)
        assertEquals(50, r.createdAt)
        assertEquals(50, r.updatedAt)
        assertFalse(r.favorite)
        assertFalse(r.isSample)
    }

    @Test fun servingsAreClampedOrFallBack() {
        val d = RecipeDraft.blank(ids()).copy(title = "x")
        assertEquals(12, d.copy(servings = "12").servingsValue())
        assertEquals(999, d.copy(servings = "5000").servingsValue())
        assertEquals(6, d.copy(servings = "0").servingsValue(fallback = 6))
        assertEquals(3, d.copy(servings = "").toRecipe(1, saved.copy(servings = 3)) { "n" }.servings)
    }

    @Test fun editingKeepsFavoriteCreationAndSourceButNotTheSampleFlag() {
        val d = RecipeDraft.from(saved, ids()).copy(title = "Better dal")
        val r = d.toRecipe(now = 999, original = saved) { "n" }
        assertEquals("r1", r.id)
        assertTrue(r.favorite)
        assertEquals(100, r.createdAt)
        assertEquals(999, r.updatedAt)
        assertEquals("example.com/dal", r.source)
        assertFalse(r.isSample)
        assertEquals(listOf("a.jpg", "b.jpg"), r.photos)
        assertEquals(1, r.coverIndex)
    }

    @Test fun coverIndexStaysInRange() {
        val d = RecipeDraft.from(saved, ids()).copy(photos = listOf("a.jpg"), coverIndex = 1)
        assertEquals(0, d.toRecipe(1, saved) { "n" }.coverIndex)
        assertEquals(0, d.copy(photos = emptyList()).toRecipe(1, saved) { "n" }.coverIndex)
    }

    @Test fun reportsWhatStopsASave() {
        val d = RecipeDraft.blank(ids()).copy(
            ingredients = listOf(
                DraftIngredient("ok", "2", "", "eggs"),
                DraftIngredient("q", "a few", "", "basil"),
                DraftIngredient("n", "200", "g"),
                DraftIngredient("blank"),
                DraftIngredient("head", isHeading = true),
            ),
            steps = listOf(DraftStep("m", "Bake", "1.2.3"), DraftStep("fine", "Rest", "10")),
        )
        val e = d.errors()
        assertTrue(e.titleMissing)
        assertEquals(setOf("q"), e.badQuantities)
        assertEquals(setOf("n"), e.missingNames)
        assertEquals(setOf("m"), e.badMinutes)
        assertTrue(e.any)
        assertFalse(d.copy(title = "x", ingredients = d.ingredients.take(1), steps = d.steps.drop(1)).errors().any)
    }

    @Test fun addsRemovesAndMovesRows() {
        var d = RecipeDraft.blank(ids())
        val first = d.ingredients[0].id
        d = d.addIngredient("x").addIngredient("sec", heading = true, afterId = first)
        assertEquals(listOf(first, "sec", "x"), d.ingredients.map { it.id })
        assertTrue(d.ingredients[1].isHeading)
        assertEquals(2, d.ingredientCount)
        d = d.moveIngredient("x", -1)
        assertEquals(listOf(first, "x", "sec"), d.ingredients.map { it.id })
        assertEquals(d, d.moveIngredient(first, -1))
        assertEquals(d, d.moveIngredient("sec", 1))
        d = d.removeIngredient("x")
        assertEquals(listOf(first, "sec"), d.ingredients.map { it.id })

        val s0 = d.steps[0].id
        d = d.addStep("s2").addStep("s1", afterId = s0).moveStep("s2", -1)
        assertEquals(listOf(s0, "s2", "s1"), d.steps.map { it.id })
        assertEquals(listOf(s0, "s1"), d.removeStep("s2").steps.map { it.id })
        assertEquals("Chop", d.updateStep("s1") { it.copy(text = "Chop") }.steps.last().text)
    }

    @Test fun pastedLinesBecomeIngredients() {
        val d = RecipeDraft.blank(ids())
        val row = d.ingredients[0].id
        val newIds = ids()
        val (pasted, n) = assertNotNull(
            d.pasteIngredients(row, "Ingredients\n- 2 cloves garlic, minced\n\nFor the sauce:\n1½ cups milk\nSalt to taste\n") { "p" + newIds() },
        )
        assertEquals(4, n)
        // The empty row is replaced.
        assertEquals(4, pasted.ingredients.size)
        val (garlic, heading, milk, salt) = pasted.ingredients
        assertEquals(DraftIngredient("pid1", "2", "clove", "garlic", "minced"), garlic)
        assertTrue(heading.isHeading)
        assertEquals("For the sauce", heading.name)
        assertEquals("1½", milk.quantity)
        assertEquals("cup", milk.unit)
        assertEquals("Salt to taste", salt.name)
        assertEquals("", salt.quantity)
    }

    @Test fun pastingAfterARowWithTextKeepsIt() {
        val d = RecipeDraft.blank(ids()).updateIngredient("id1") { it.copy(name = "eggs", quantity = "2") }
        val newIds = ids()
        val (pasted, n) = assertNotNull(d.pasteIngredients("id1", "100 g butter\n50 g sugar") { "new" + newIds() })
        assertEquals(2, n)
        assertEquals(listOf("eggs", "butter", "sugar"), pasted.ingredients.map { it.name })
    }

    @Test fun oneLineIsJustTyping() {
        val d = RecipeDraft.blank(ids())
        assertNull(d.pasteIngredients("id1", "2 eggs") { "n" })
        assertNull(d.pasteIngredients("id1", "2 eggs\n\n  ") { "n" })
        assertNull(d.pasteIngredients("missing", "a\nb") { "n" })
    }

    @Test fun offersTheTimeFoundInAStep() {
        val s = DraftStep("s", "Simmer for 10 to 12 minutes, then chop the herbs.")
        assertEquals(12.0, s.detectedMinutes)
        assertNull(s.copy(minutes = "5").detectedMinutes)
        assertNull(s.copy(minutes = "x").detectedMinutes)
        assertNull(DraftStep("t", "Serve.").detectedMinutes)

        val d = RecipeDraft.blank(ids()).copy(steps = listOf(s))
        val used = d.useDetectedTime("s").steps.single()
        assertEquals("12", used.minutes)
        assertEquals(TimeScale.FIXED, used.timeScale) // "simmer" is a fixed-time verb
        assertNull(used.detectedMinutes)
        // Nothing to use: unchanged.
        assertEquals(d.useDetectedTime("s"), d.useDetectedTime("s").useDetectedTime("s"))
    }

    @Test fun tagsAreNormalizedAndUnique() {
        var d = RecipeDraft.blank(ids()).addTag("  Quick Dinner ")
        assertEquals(listOf("quick-dinner"), d.tags)
        assertEquals(d, d.addTag("quick dinner"))
        assertEquals(d, d.addTag("  !! "))
        d = d.addTag("#Spicy").removeTag("quick-dinner")
        assertEquals(listOf("spicy"), d.tags)
    }

    @Test fun suggestsTagsFromTheRecipeThenTheLibrary() {
        val d = RecipeDraft.blank(ids()).copy(
            title = "Gochujang noodles",
            tags = listOf("korean"),
            ingredients = listOf(DraftIngredient("a", "2", "tbsp", "gochujang")),
            steps = listOf(DraftStep("s", "Boil the noodles.", "8")),
        )
        val s = d.tagSuggestions(libraryTags = listOf("weeknight", "spicy", "baking"))
        assertEquals(listOf("dinner", "spicy", "vegetarian", "quick", "weeknight", "baking", "breakfast", "dessert"), s)
        assertEquals(listOf("baking", "breakfast"), d.tagSuggestions(listOf("weeknight", "baking"), query = "b"))
        // An empty draft offers the library, then common tags.
        assertEquals(listOf("a", "quick", "vegetarian"), RecipeDraft.blank(ids()).tagSuggestions(listOf("a"), limit = 3))
    }

    @Test fun importReviewStartsANewRecipe() {
        val parsed = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
        val d = RecipeDraft.fromImport(parsed, ids())
        assertTrue(d.isNew)
        assertEquals(parsed.title, d.title)
        assertEquals(parsed.ingredients.size, d.ingredients.size)
        assertEquals(parsed.steps.map { it.text }, d.steps.map { it.text })
        assertEquals(parsed.tags, d.tags)
        val r = d.toRecipe(now = 5, original = null) { "saved" }
        assertEquals("saved", r.id)
        assertEquals(parsed.steps.map { it.minutes }, r.steps.map { it.minutes })
    }

    @Test fun formatsAmountsAndMinutesForEditing() {
        assertEquals("", RecipeDraft.quantityText(null))
        assertEquals("2", RecipeDraft.quantityText(2.0))
        assertEquals("¾", RecipeDraft.quantityText(0.75))
        assertEquals("2⅔", RecipeDraft.quantityText(2 + 2.0 / 3))
        assertEquals("0.15", RecipeDraft.quantityText(0.15))
        assertEquals("1.234", RecipeDraft.quantityText(1.23449))
        assertEquals("", RecipeDraft.minutesText(0.0))
        assertEquals("7.5", RecipeDraft.minutesText(7.5))
        assertEquals("90", RecipeDraft.minutesText(90.0))
    }

    @Test fun canonicalUnits() {
        assertEquals("tbsp", TextRecipeParser.canonicalUnit(" Tablespoons "))
        assertEquals("g", TextRecipeParser.canonicalUnit("Grams"))
        assertEquals("fl oz", TextRecipeParser.canonicalUnit("fl  oz"))
        assertEquals("tsp", TextRecipeParser.canonicalUnit("tsp."))
        assertEquals("Heads", TextRecipeParser.canonicalUnit(" Heads"))
        assertEquals("", TextRecipeParser.canonicalUnit("  "))
    }
}
