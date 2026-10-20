package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuantitiesTest {
    @Test fun parsesPlainFractionalAndMixedAmounts() {
        assertEquals(2.0, Quantities.parse("2"))
        assertEquals(1.5, Quantities.parse("1.5"))
        assertEquals(1.5, Quantities.parse("1,5"))
        assertEquals(0.75, Quantities.parse("3/4"))
        assertEquals(1.5, Quantities.parse("1 1/2"))
        assertEquals(1.5, Quantities.parse("1½"))
        assertEquals(1.5, Quantities.parse("1 ½"))
        assertEquals(0.25, Quantities.parse(" ¼ "))
    }

    @Test fun rejectsNonAmounts() {
        assertNull(Quantities.parse(null))
        assertNull(Quantities.parse(""))
        assertNull(Quantities.parse("abc"))
        assertNull(Quantities.parse("2 cups"))
        assertNull(Quantities.parse("1/0"))
    }

    @Test fun formatsAsKitchenFractions() {
        assertEquals("2", Quantities.toFraction(2.0))
        assertEquals("1½", Quantities.toFraction(1.5))
        assertEquals("⅓", Quantities.toFraction(0.333))
        assertEquals("⅛", Quantities.toFraction(0.01))
        assertEquals("1", Quantities.toFraction(0.95))
        assertEquals("0", Quantities.toFraction(0.0))
        assertEquals("4½", Quantities.toFraction(4.5))
    }

    @Test fun normalizesVulgarFractionsInText() {
        assertEquals("1.5 cups and 0.25 tsp", Quantities.normalizeFractions("1½ cups and ¼ tsp"))
        assertEquals("6.5 minutes", Quantities.normalizeFractions("6½ minutes"))
    }
}
