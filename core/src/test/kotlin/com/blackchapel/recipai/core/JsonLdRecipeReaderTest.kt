package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun page(name: String): String =
    requireNotNull(JsonLdRecipeReaderTest::class.java.getResource("/pages/$name")) { "missing fixture $name" }.readText()

class JsonLdRecipeReaderTest {
    private fun card(name: String) = assertNotNull(JsonLdRecipeReader.read(HtmlText.jsonLdBlocks(page(name))))

    @Test fun readsAYoastGraphWithSectionsAndTips() {
        val r = card("wprm-graph.html")
        assertEquals("Crispy Gochujang Tofu Bowls", r.title)
        assertEquals(
            "These sticky, spicy tofu bowls are my weeknight go-to – crispy outside, tender inside, glazed in a sweet & savoury sauce.",
            r.description,
        )
        assertEquals(4, r.servings)
        assertFalse(r.servingsGuessed)
        assertNull(r.servingsLabel, "\"4 servings\" is the default label")

        assertEquals(12, r.ingredients.count { !it.isHeading })
        assertEquals(listOf("For the sauce"), r.ingredients.filter { it.isHeading }.map { it.name })
        val tofu = r.ingredients.first()
        assertEquals(1.0, tofu.quantity)
        assertEquals("block", tofu.unit)
        assertEquals("extra-firm tofu", tofu.name)
        val salt = r.ingredients.first { it.name == "salt" }
        assertEquals(0.25, salt.quantity, "&frac14; is decoded before parsing")
        assertEquals(1.5, r.ingredients.first { it.name == "maple syrup" }.quantity)

        // Two sections flattened (a raw line break inside a string is tolerated); the tip went to notes.
        assertEquals(5, r.steps.size)
        assertTrue(r.steps[0].text.startsWith("Preheat the oven to 425°F"))
        assertEquals(25.0, r.steps[1].minutes)
        assertTrue(r.steps[2].text.endsWith("in a large pan. Simmer for 2 minutes until glossy."), r.steps[2].text)
        assertEquals(2.0, r.steps[2].minutes)
        assertEquals(TimeScale.FIXED, r.steps[1].timeScale)
        assertTrue(r.steps.none { it.text.contains("Leftovers") })
        assertEquals("Leftovers keep for 3 days in the fridge.\nPrep 15 min · Cook 25 min · Total 40 min", r.notes)

        assertEquals("dinner", r.tags[0], "Main Course maps to dinner")
        assertTrue("vegan" in r.tags && "korean" in r.tags && "spicy" in r.tags, r.tags.toString())
        assertFalse("vegetarian" in r.tags, "vegan replaces the rules' vegetarian")
        assertTrue(r.tags.size <= 5)
        assertTrue(r.isUsable)
    }

    @Test fun readsATopLevelArrayWithATypeArrayAndHtmlInstructions() {
        val r = card("array-type.html")
        assertEquals("Brown Butter Chocolate Chip Cookies", r.title, "name wins over headline")
        assertEquals("Nutty brown butter, two kinds of chocolate and flaky salt.", r.description)
        assertEquals(24, r.servings)
        assertEquals("cookies", r.servingsLabel)
        assertEquals(1.5, r.ingredients.first { it.name == "light brown sugar" }.quantity, "&#189; is ½")
        assertEquals(listOf("For the mix-ins"), r.ingredients.filter { it.isHeading }.map { it.name })
        assertEquals("Flaky salt", r.ingredients.last().name)

        assertEquals(
            listOf(
                "Brown the butter in a saucepan over medium heat, about 5 minutes, then let it cool for 10 minutes.",
                "Whisk in the sugar and eggs.",
                "Fold in the flour, baking soda and chocolate.",
                "Chill the dough for 30 minutes.",
                "Scoop and bake at 180°C for 11 to 12 minutes.",
            ),
            r.steps.map { it.text },
        )
        assertEquals(12.0, r.steps[4].minutes)
        assertEquals("Total 1 h 15 min", r.notes)
        assertEquals("dessert", r.tags[0])
        assertTrue("american" in r.tags)
        assertFalse("dinner" in r.tags, "a dessert course drops the default dinner tag")
    }

    @Test fun findsARecipeNestedUnderMainEntity() {
        val r = card("nested-main-entity.html")
        assertEquals("Weeknight Red Lentil Dal", r.title)
        assertEquals(6, r.servings)
        assertNull(r.servingsLabel)
        assertEquals(1, r.ingredients.size, "a single string ingredient")
        assertEquals("red lentils", r.ingredients[0].name)
        assertEquals(
            listOf(
                "Rinse the lentils until the water runs clear.",
                "Simmer the lentils with 3 cups of water and the turmeric for 20 minutes.",
                "Fry the cumin seeds in ghee for 1 minute and pour over the dal.",
            ),
            r.steps.map { it.text },
        )
        assertEquals("Cook 35 min", r.notes)
        assertTrue("dinner" in r.tags && "indian" in r.tags && "vegetarian" in r.tags, r.tags.toString())
    }

    @Test fun noRecipeCardMeansNull() {
        assertNull(JsonLdRecipeReader.read(HtmlText.jsonLdBlocks(page("article.html"))))
        assertNull(JsonLdRecipeReader.read(HtmlText.jsonLdBlocks(page("blog-no-card.html"))))
        assertNull(JsonLdRecipeReader.readJson("{ this is not json"))
        assertNull(JsonLdRecipeReader.readJson(""))
    }

    @Test fun picksTheFullestCardAndSplitsInlineNumberedText() {
        val json = """
            [{"@type":"Recipe","name":"Teaser","recipeIngredient":["1 egg"]},
             {"@type":"Recipe","name":"Shakshuka","recipeYield":"Serves 2-3 (as a main)",
              "recipeIngredient":["1 tbsp olive oil","1 onion","1 can tomatoes","4 eggs"],
              "recipeInstructions":"1. Soften the onion in the oil for 8 minutes. 2. Add the tomatoes and simmer for 10 minutes. 3. Crack in the eggs, cover and cook for 6 minutes."}]
        """.trimIndent()
        val r = assertNotNull(JsonLdRecipeReader.readJson(json))
        assertEquals("Shakshuka", r.title)
        assertEquals(2, r.servings)
        assertNull(r.servingsLabel)
        assertEquals(3, r.steps.size)
        assertEquals("Add the tomatoes and simmer for 10 minutes.", r.steps[1].text)
        assertEquals(listOf(8.0, 10.0, 6.0), r.steps.map { it.minutes })
    }

    @Test fun toleratesDoubleEscapingAndObjectValues() {
        val json = """
            {"@context":"https://schema.org","@type":"Recipe",
             "name":"Mom&amp;#39;s Chili","description":"",
             "recipeYield":{"@type":"QuantitativeValue","value":"8"},
             "recipeIngredient":["2 lb ground beef","1 (28 oz) can crushed tomatoes"],
             "recipeInstructions":[{"@type":"HowToStep","name":"Brown the beef"},{"@type":"HowToStep","text":""}]}
        """.trimIndent()
        val r = assertNotNull(JsonLdRecipeReader.readJson(json))
        assertEquals("Mom's Chili", r.title)
        assertEquals(listOf("Brown the beef"), r.steps.map { it.text })
        assertEquals(8, r.servings)
        assertFalse("vegetarian" in r.tags)
    }

    @Test fun readsDurations() {
        assertEquals(90.0, Durations.minutes("PT1H30M"))
        assertEquals(45.0, Durations.minutes("P0DT0H45M"))
        assertEquals(90.0, Durations.minutes("PT90M"))
        assertEquals(1440.0, Durations.minutes("P1D"))
        assertEquals(0.5, Durations.minutes("PT30S"))
        assertEquals(20.0, Durations.minutes("20 minutes"))
        assertNull(Durations.minutes("PT0S"))
        assertNull(Durations.minutes("PT"))
        assertNull(Durations.minutes(""))
        assertNull(Durations.minutes(null))
    }

    @Test fun readsYields() {
        fun y(vararg v: String) = JsonLdRecipeReader.Yield.parse(v.toList())
        assertEquals(JsonLdRecipeReader.Yield(4, null), y("4"))
        assertEquals(JsonLdRecipeReader.Yield(4, null), y("Serves 4"))
        assertEquals(JsonLdRecipeReader.Yield(6, null), y("6-8 people"))
        assertEquals(JsonLdRecipeReader.Yield(12, "muffins"), y("12", "12 muffins"))
        assertEquals(JsonLdRecipeReader.Yield(1, "loaf"), y("Makes 1 loaf (10 slices)"))
        assertEquals(JsonLdRecipeReader.Yield(2, "cups"), y("about 2 cups"))
        assertNull(y("a crowd"))
        assertNull(y())
    }
}
