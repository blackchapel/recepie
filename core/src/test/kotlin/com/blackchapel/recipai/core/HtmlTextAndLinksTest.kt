package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlTextAndLinksTest {
    @Test fun decodesEntities() {
        assertEquals("Mac & cheese – ½ cup, 180°C “hot” ’", HtmlText.decodeEntities("Mac &amp; cheese &#8211; &frac12; cup, 180&deg;C &ldquo;hot&rdquo; &#x2019;"))
        assertEquals("a b", HtmlText.decodeEntities("a&nbsp;b"))
        assertEquals("&unknown; &#0; & fine", HtmlText.decodeEntities("&unknown; &#0; & fine"))
        assertEquals("😀", HtmlText.decodeEntities("&#128512;"))
    }

    @Test fun stripsTagsIntoLines() {
        assertEquals(
            "Brown the butter.\nWhisk in the sugar.\nFold in the flour.\nBake.",
            HtmlText.stripTags("<p>Brown the <b>butter</b>.</p><p>Whisk in the sugar.<br/>Fold in the flour.</p><ul><li>Bake.</li></ul>"),
        )
        assertEquals("One line, tidy", HtmlText.inline("  <span>One\n line,</span>   tidy "))
    }

    @Test fun findsJsonLdBlocksAndMeta() {
        val html = """<head><script type="application/ld+json">{"a":1}</script><script>var x</script>
            <SCRIPT TYPE=application/ld+json>[2]</SCRIPT><meta property="og:title" content="Tofu &amp; Rice">
            <title>Tofu | Site</title></head>"""
        assertEquals(listOf("{\"a\":1}", "[2]"), HtmlText.jsonLdBlocks(html))
        assertEquals("Tofu & Rice", HtmlText.meta(html, "og:title"))
        assertEquals("Tofu & Rice", HtmlText.pageTitle(html))
        assertEquals("Tofu", HtmlText.pageTitle("<title>Tofu | Site name</title>"))
        assertEquals("Big Title", HtmlText.pageTitle("<title>x</title><h1 class='t'>Big <em>Title</em></h1>"))
        assertEquals("windows-1252", HtmlText.declaredCharset("<meta charset=\"windows-1252\">"))
        assertEquals("Shift_JIS", HtmlText.declaredCharset("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=Shift_JIS\">"))
        assertNull(HtmlText.declaredCharset("<meta name=x>"))
    }

    @Test fun findsLinksInSharedText() {
        assertEquals("https://kitchen.example.com/r/1", Links.findUrl("Look at this! https://kitchen.example.com/r/1."))
        assertEquals("https://en.example.org/wiki/Pie_(food)", Links.findUrl("(see https://en.example.org/wiki/Pie_(food))"))
        assertEquals("http://x.example.com/a?b=c&d=e", Links.findUrl("Tofu bowls\nhttp://x.example.com/a?b=c&d=e"))
        assertNull(Links.findUrl("no link here"))
        assertNull(Links.findUrl("https://"))
    }

    @Test fun normalizesTypedLinks() {
        assertEquals("https://kitchen.example.com/recipes/tofu", Links.normalize("  kitchen.example.com/recipes/tofu "))
        assertEquals("http://kitchen.example.com/x", Links.normalize("http://kitchen.example.com/x"))
        assertEquals("https://www.example.com/a%20b/cr%C3%A8me", Links.normalize("https://www.example.com/a b/crème"))
        assertEquals("https://example.com/", Links.normalize("Try this: https://example.com/ !"))
        assertNull(Links.normalize(""))
        assertNull(Links.normalize("tofu bowls"))
        assertNull(Links.normalize("localhost"))
        assertNull(Links.normalize("ftp://example.com/file"))
        assertNull(Links.normalize("javascript:alert(1)"))
    }

    @Test fun namesTheSource() {
        assertEquals("kitchen.example.com/recipes/tofu", Links.source("https://www.kitchen.example.com/recipes/tofu/?utm=x#top"))
        assertEquals("example.com/recettes/crème", Links.source("https://example.com/recettes/cr%C3%A8me"))
        assertEquals("example.com", Links.source("https://example.com/"))
        assertEquals("kitchen.example.com", Links.host("https://www.kitchen.example.com/x"))
    }
}
