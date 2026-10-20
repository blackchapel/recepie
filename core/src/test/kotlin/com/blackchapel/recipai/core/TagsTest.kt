package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TagsTest {
    @Test fun pinnedTagsHaveFixedColors() {
        assertEquals(TagColor.BASIL, Tags.colorOf("vegetarian"))
        assertEquals(TagColor.SUMAC, Tags.colorOf("dessert"))
    }

    @Test fun otherTagsGetAStableColor() {
        assertEquals(Tags.colorOf("brunch"), Tags.colorOf("brunch"))
    }

    @Test fun normalizesTags() {
        assertEquals("quick-dinner", Tags.normalize("  #Quick Dinner! "))
        assertEquals("make-ahead", Tags.normalize("Make ahead"))
    }
}
