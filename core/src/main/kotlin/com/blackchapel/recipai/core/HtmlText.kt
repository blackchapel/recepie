package com.blackchapel.recipai.core

/**
 * Just enough HTML handling for link import: entity decoding, tag
 * stripping, the page's JSON-LD blocks and its readable text. Regex based
 * on purpose (no HTML parser dependency); it only ever reads, it never
 * renders anything from the page.
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

    private val comment = Regex("<!--[\\s\\S]*?-->")
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
        val text = html.replace(comment, "")
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

    private val jsonLdScript = Regex(
        "<script\\b[^>]*\\btype\\s*=\\s*[\"']?\\s*application/ld\\+json[^>]*>([\\s\\S]*?)</script\\s*>",
        i,
    )

    /** The contents of every `<script type="application/ld+json">` on the page, in order. */
    fun jsonLdBlocks(html: String): List<String> =
        jsonLdScript.findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

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

    private val h1 = Regex("<h1\\b[^>]*>([\\s\\S]*?)</h1\\s*>", i)
    private val titleTag = Regex("<title\\b[^>]*>([\\s\\S]*?)</title\\s*>", i)
    private val siteSuffix = Regex("\\s+[|•·–—-]\\s+[^|•·–—-]{2,60}$")

    /** The page's own name for itself: `og:title`, else the first `<h1>`, else `<title>` minus " | Site name". */
    fun pageTitle(html: String): String? {
        meta(html, "og:title")?.let { return it }
        h1.find(html)?.let { m -> inline(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { return it } }
        return titleTag.find(html)?.let { m -> inline(m.groupValues[1]).replace(siteSuffix, "").trim().takeIf { it.isNotEmpty() } }
    }

    private val invisible = Regex(
        "<(script|style|noscript|template|svg|iframe|object|canvas|head|nav|footer|aside|form|button|select|dialog)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",
        i,
    )
    private val voidInvisible = Regex("<(?:input|img|link|meta)\\b[^>]*>", i)
    private val regionMain = Regex("<main\\b[^>]*>([\\s\\S]*?)</main\\s*>", i)
    private val regionArticle = Regex("<article\\b[^>]*>([\\s\\S]*)</article\\s*>", i)
    private val regionBody = Regex("<body\\b[^>]*>([\\s\\S]*)", i)
    private val h1Start = Regex("<h1\\b", i)

    /** Caps the work the text rules do on huge pages. */
    private const val MAX_TEXT = 60_000

    /**
     * The readable text of a whole page, for the text rules when there is no
     * recipe card: scripts, styles, navigation, footers, sidebars and forms
     * removed; the main content (`<main>`, else `<article>`, else `<body>`)
     * from its `<h1>` on, so menus above the title don't become the title.
     */
    fun visibleText(html: String): String {
        val cleaned = html.replace(comment, "").replace(invisible, "\n").replace(voidInvisible, "")
        val region = regionMain.find(cleaned)?.groupValues?.get(1)
            ?: regionArticle.find(cleaned)?.groupValues?.get(1)
            ?: regionBody.find(cleaned)?.groupValues?.get(1)
            ?: cleaned
        val fromTitle = h1Start.find(region)?.let { region.substring(it.range.first) } ?: region
        val text = stripTags(fromTitle)
        return if (text.length > MAX_TEXT) text.substring(0, MAX_TEXT) else text
    }

    private val metaCharset = Regex("<meta\\b[^>]*?charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_\\-:.]+)", i)

    /** The charset a page declares in a `<meta>` tag (either form), from the first bytes read as Latin-1. */
    fun declaredCharset(head: String): String? = metaCharset.find(head)?.groupValues?.get(1)
}
