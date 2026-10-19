package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ScalingTest {
    private val shakshuka = SampleRecipes.all(now = 0).first { it.id == "shakshuka" }
    private fun ing(name: String) = shakshuka.ingredients.first { it.name == name }

    @Test fun unscaledAmountsKeepTheirUnit() {
        val oil = Scaling.amount(ing("olive oil"), 1.0)
        assertEquals("2", oil.text)
        assertEquals("tbsp", oil.unit)
        val tomatoes = Scaling.amount(ing("crushed tomatoes"), 1.0)
        assertEquals("800", tomatoes.text)
        assertEquals("g", tomatoes.unit)
    }

    @Test fun metricStepsUpPastOneThousand() {
        val a = Scaling.amount(ing("crushed tomatoes"), 2.0)
        assertEquals("1.6", a.text)
        assertEquals("kg", a.unit)
        assertEquals(1000.0, a.toStoredUnit)
    }

    @Test fun spoonsStepUpAndDown() {
        assertEquals("3" to "tbsp", Scaling.amount(ing("olive oil"), 1.5).let { it.text to it.unit })
        assertEquals("⅛" to "tsp", Scaling.amount(ing("chili flakes"), 0.5).let { it.text to it.unit })
        assertEquals("1⅓" to "tbsp", Scaling.amount(ing("ground cumin"), 4.0).let { it.text to it.unit })
        val cup = Scaling.amount(Ingredient("x", "flour", 4.0, "tbsp"), 2.0)
        assertEquals("½" to "cup", cup.text to cup.unit)
    }

    @Test fun countsUseFractionsAndPlurals() {
        assertEquals("4½", Scaling.amount(ing("eggs"), 0.75).text)
        val garlic = Scaling.amount(ing("garlic"), 1.5)
        assertEquals("4½", garlic.text)
        assertEquals("cloves", garlic.unit)
        assertEquals("clove", Scaling.singular(garlic.unit))
    }

    @Test fun ingredientsWithoutAmountsStayEmpty() {
        val salt = Scaling.amount(ing("Salt and black pepper"), 2.0)
        assertEquals("", salt.text)
    }

    @Test fun typingOneAmountScalesTheRecipe() {
        val tomatoes = ing("crushed tomatoes")
        val shown = Scaling.amount(tomatoes, 2.0) // 1.6 kg
        assertEquals(1.5, Scaling.factorFor(tomatoes, 1.2, shown)!!, 1e-9)
        assertEquals(3.0, Scaling.factorFor(ing("olive oil"), 6.0, Scaling.amount(ing("olive oil"), 1.0))!!, 1e-9)
    }

    @Test fun stepTimesScaleByMode() {
        assertEquals(10.0, Scaling.minutes(10.0, TimeScale.FIXED, 2.0))
        assertEquals(14.0, Scaling.minutes(10.0, TimeScale.PARTIAL, 2.0))
        assertEquals(24.0, Scaling.minutes(12.0, TimeScale.LINEAR, 2.0))
        assertEquals(10.0, Scaling.minutes(8.0, TimeScale.PARTIAL, 1.5))
        assertEquals(0.0, Scaling.minutes(0.0, TimeScale.LINEAR, 3.0))
        assertEquals(26.0, Scaling.totalMinutes(shakshuka))
    }

    @Test fun formatsTimes() {
        assertEquals("6½ min", Scaling.formatMinutes(6.5))
        assertEquals("30 s", Scaling.formatMinutes(0.5))
        assertEquals("1 h", Scaling.formatMinutes(60.0))
        assertEquals("1 h 16 min", Scaling.formatMinutes(76.0))
        assertEquals("", Scaling.formatMinutes(0.0))
        assertEquals("9:05", Scaling.formatClock(545.0))
        assertEquals("1:02:30", Scaling.formatClock(3750.0))
        assertEquals("0:00", Scaling.formatClock(-3.0))
    }

    @Test fun servingsStepByWholeNumbers() {
        assertEquals("4½", Scaling.formatServings(4, 1.125))
        assertEquals(1.25, Scaling.stepServings(4, 1.0, +1))
        assertEquals(1.0, Scaling.stepServings(4, 1.125, -1))
        assertEquals(0.25, Scaling.stepServings(4, 0.25, -1))
    }
}
