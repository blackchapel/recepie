package com.blackchapel.recipai.core

/**
 * Just enough HTML handling for link import: entity decoding, tag
 * stripping, the page's JSON-LD blocks and its readable text. No HTML
 * parser dependency on purpose: a linear scan for structure, small regexes
 * for tags. It only ever reads; nothing from the page is rendered or run.
 */
object HtmlText {
    private val i = RegexOption.IGNORE_CASE

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "minus" to "−", "hellip" to "…", "bull" to "•", "middot" to "·",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
        "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›", "prime" to "′", "Prime" to "″",
        "frac12" to "½", "frac13" to "⅓", "frac23" to "⅔", "frac14" to "¼", "frac34" to "¾",
        "frac15" to "⅕", "frac16" to "⅙", "frac18" to "⅛", "frac38" to "⅜", "frac58" to "⅝", "frac78" to "⅞",
        "frasl" to "⁄", "deg" to "°", "times" to "×", "divide" to "÷", "plusmn" to "±", "micro" to "µ",
        "copy" to "©", "reg" to "®", "trade" to "™", "sect" to "§", "para" to "¶", "euro" to "€",
        "pound" to "£", "yen" to "¥", "cent" to "¢", "iexcl" to "¡", "iquest" to "¿", "shy" to "",
        "zwj" to "", "zwnj" to "", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã", "auml" to "ä", "aring" to "å", "aelig" to "æ",
        "ccedil" to "ç", "egrave" to "è", "eacute" to "é", "ecirc" to "ê", "euml" to "ë",
        "igrave" to "ì", "iacute" to "í", "icirc" to "î", "iuml" to "ï", "ntilde" to "ñ",
        "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô", "otilde" to "õ", "ouml" to "ö", "oslash" to "ø",
        "ugrave" to "ù", "uacute" to "ú", "ucirc" to "û", "uuml" to "ü", "yacute" to "ý", "yuml" to "ÿ", "szlig" to "ß",
        "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â", "Auml" to "Ä", "Aring" to "Å", "AElig" to "Æ", "Ccedil" to "Ç",
        "Egrave" to "È", "Eacute" to "É", "Ecirc" to "Ê", "Iacute" to "Í", "Ntilde" to "Ñ", "Oacute" to "Ó",
        "Ouml" to "Ö", "Oslash" to "Ø", "Uacute" to "Ú", "Uuml" to "Ü", "oelig" to "œ", "OElig" to "Œ",
    )
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#\\d{1,7}|[a-zA-Z][a-zA-Z0-9]{1,8});")

    /** "Mac &amp; cheese &#8211; &frac12; cup" -> "Mac & cheese – ½ cup". Unknown entities stay as they are. */
    fun decodeEntities(s: String): String {
        if ('&' !in s) return s.replace('\u00A0', ' ')
        return entity.replace(s) { m ->
            val body = m.groupValues[1]
            val decoded = when {
                body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2).toIntOrNull(16))
                body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull())
                else -> named[body]
            }
            decoded ?: m.value
        }.replace('\u00A0', ' ')
    }

    private fun codePoint(cp: Int?): String? = when {
        cp == null || cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF -> null
        cp == 0xA0 -> " "
        else -> String(Character.toChars(cp))
    }

    // Structure (comments, script blocks, regions) is found by scanning with
    // indexOf, not regex: Android's ICU regex engine keeps a backtracking
    // state per character for lazy `[\s\S]*?`, which overflows its stack on
    // pages with a few hundred KB of inline script.

    private val breakTag = Regex("<br\\s*/?>", i)
    private val blockTag = Regex(
        "</?(?:p|div|li|ul|ol|h[1-6]|tr|table|tbody|thead|section|article|main|header|blockquote|dt|dd|dl|figure|figcaption|pre|hr)\\b[^>]*>",
        i,
    )
    private val anyTag = Regex("<[^>]*>")
    private val spaces = Regex("[ \\t\\f\\x0B\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+")
    private val zeroWidth = Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]")

    /**
     * An HTML fragment as plain text: block elements and `<br>` become line
     * breaks, other tags vanish, entities are decoded, each line is trimmed
     * and blank lines are dropped.
     */
    fun stripTags(html: String): String {
        if ('<' !in html && '&' !in html) return cleanLines(html)
        val text = removeComments(html)
            .replace(breakTag, "\n")
            .replace(blockTag, "\n")
            .replace(anyTag, "")
        return cleanLines(decodeEntities(text))
    }

    private fun cleanLines(text: String): String = text.replace("\r", "").split('\n')
        .map { it.replace(zeroWidth, "").replace(spaces, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    /** One line of plain text: tags stripped, all whitespace collapsed. */
    fun inline(html: String): String = stripTags(html).replace('\n', ' ').replace(spaces, " ").trim()

    /** ASCII-only lowercase: same length as [s], so indexes line up. */
    private fun lower(s: String): String {
        val chars = CharArray(s.length)
        for (k in s.indices) {
            val ch = s[k]
            chars[k] = if (ch in 'A'..'Z') ch + 32 else ch
        }
        return String(chars)
    }

    private fun removeComments(html: String): String {
        var start = html.indexOf("<!--")
        if (start < 0) return html
        val out = StringBuilder(html.length)
        var from = 0
        while (start >= 0) {
            out.append(html, from, start)
            val end = html.indexOf("-->", start + 4)
            if (end < 0) return out.toString()
            from = end + 3
            start = html.indexOf("<!--", from)
        }
        return out.append(html, from, html.length).toString()
    }

    /** The tag name opening at [lt] in [low] ("<script ..."), or null for closing tags, comments and stray "<". */
    private fun tagNameAt(low: String, lt: Int): String? {
        var k = lt + 1
        if (k >= low.length || low[k] !in 'a'..'z') return null
        while (k < low.length && (low[k] in 'a'..'z' || low[k] in '0'..'9')) k++
        if (k < low.length && !low[k].isWhitespace() && low[k] != '>' && low[k] != '/') return null
        return low.substring(lt + 1, k)
    }

    /** An element's position: its opening tag is [open] (from "<" to ">"), its content [content]. */
    private class Element(val open: IntRange, val content: IntRange, val end: Int)

    /** The element opening at [lt] (whose name is [name]); its content runs to the first `</name`. */
    private fun elementAt(low: String, lt: Int, name: String): Element {
        val gt = low.indexOf('>', lt).let { if (it < 0) low.length - 1 else it }
        if (gt > lt && low[gt - 1] == '/') return Element(lt..gt, gt + 1 until gt + 1, gt + 1) // <svg ... />
        val close = low.indexOf("</$name", gt + 1)
        if (close < 0) return Element(lt..gt, gt + 1 until low.length, low.length)
        val closeEnd = low.indexOf('>', close).let { if (it < 0) low.length else it + 1 }
        return Element(lt..gt, gt + 1 until close, closeEnd)
    }

    /** Every `<name ...>` element, in order (not nested in one another). */
    private fun elements(low: String, name: String): Sequence<Element> = sequence {
        var from = 0
        while (true) {
            val lt = low.indexOf("<$name", from)
            if (lt < 0) break
            if (tagNameAt(low, lt) != name) {
                from = lt + 1
                continue
            }
            val e = elementAt(low, lt, name)
            yield(e)
            from = maxOf(e.end, lt + 1)
        }
    }

    private fun first(html: String, low: String, name: String): String? =
        elements(low, name).firstOrNull()?.let { html.substring(it.content.first, maxOf(it.content.first, it.content.last + 1)) }

    /** The contents of every `<script type="application/ld+json">` on the page, in order. */
    fun jsonLdBlocks(html: String): List<String> {
        val low = lower(html)
        return elements(low, "script")
            .filter { e -> attr(html.substring(e.open.first, e.open.last + 1), "type")?.contains("ld+json", ignoreCase = true) == true }
            .map { e -> html.substring(e.content.first, maxOf(e.content.first, e.content.last + 1)).trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private val metaTag = Regex("<meta\\b[^>]*>", i)
    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", i).find(tag)?.let { m ->
            m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }
        }

    /** The `content` of `<meta property|name="[key]">`, decoded. */
    fun meta(html: String, key: String): String? = metaTag.findAll(html)
        .firstOrNull { tag ->
            val m = tag.value
            attr(m, "property").equals(key, ignoreCase = true) || attr(m, "name").equals(key, ignoreCase = true)
        }
        ?.let { attr(it.value, "content") }
        ?.let { decodeEntities(it).trim() }
        ?.takeIf { it.isNotEmpty() }

    private val siteSuffix = Regex("\\s+[|•·–—-]\\s+[^|•·–—-]{2,60}$")

    /** The page's own name for itself: `og:title`, else the first `<h1>`, else `<title>` minus " | Site name". */
    fun pageTitle(html: String): String? {
        meta(html, "og:title")?.let { return it }
        val page = removeComments(html)
        val low = lower(page)
        first(page, low, "h1")?.take(2_000)?.let(::inline)?.takeIf { it.isNotEmpty() }?.let { return it }
        return first(page, low, "title")?.take(2_000)?.let(::inline)?.replace(siteSuffix, "")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Elements whose content is never recipe text. */
    private val invisible = setOf(
        "script", "style", "noscript", "template", "svg", "iframe", "object", "canvas", "head",
        "nav", "footer", "aside", "form", "button", "select", "dialog",
    )
    private val voidInvisible = Regex("<(?:input|img|link|meta)\\b[^>]*>", i)

    /** [html] without comments and without the [invisible] elements (each replaced by a line break). */
    private fun removeInvisible(html: String): String {
        val low = lower(html)
        val out = StringBuilder(html.length)
        var from = 0
        var k = 0
        while (true) {
            val lt = low.indexOf('<', k)
            if (lt < 0) break
            if (low.startsWith("<!--", lt)) {
                out.append(html, from, lt)
                val end = low.indexOf("-->", lt + 4)
                from = if (end < 0) html.length else end + 3
                k = from
                continue
            }
            val name = tagNameAt(low, lt)
            if (name != null && name in invisible) {
                out.append(html, from, lt).append('\n')
                from = elementAt(low, lt, name).end
                k = from
                continue
            }
            k = lt + 1
        }
        if (from < html.length) out.append(html, from, html.length)
        return out.toString()
    }

    /** Caps the work the text rules do on huge pages. */
    private const val MAX_TEXT = 60_000

    /**
     * The readable text of a whole page, for the text rules when there is no
     * recipe card: scripts, styles, navigation, footers, sidebars and forms
     * removed; the main content (`<main>`, else `<article>`, else `<body>`)
     * from its `<h1>` on, so menus above the title don't become the title.
     */
    fun visibleText(html: String): String {
        val cleaned = removeInvisible(html).replace(voidInvisible, "")
        val low = lower(cleaned)
        val region = first(cleaned, low, "main")
            ?: elements(low, "article").firstOrNull()?.let { a ->
                // Up to the last </article>, so nested articles (comments) don't cut it short.
                val close = low.lastIndexOf("</article")
                cleaned.substring(a.content.first, if (close > a.content.first) close else cleaned.length)
            }
            ?: elements(low, "body").firstOrNull()?.let { cleaned.substring(it.content.first) }
            ?: cleaned
        val regionLow = lower(region)
        val h1 = elements(regionLow, "h1").firstOrNull()?.open?.first
        val text = stripTags(if (h1 != null) region.substring(h1) else region)
        return if (text.length > MAX_TEXT) text.substring(0, MAX_TEXT) else text
    }

    private val metaCharset = Regex("<meta\\b[^>]*?charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_\\-:.]+)", i)

    /** The charset a page declares in a `<meta>` tag (either form), from the first bytes read as Latin-1. */
    fun declaredCharset(head: String): String? = metaCharset.find(head.take(4096))?.groupValues?.get(1)
}
