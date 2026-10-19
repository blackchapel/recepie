package com.blackchapel.recipai.core

import java.net.URI

/** Recipe links: what the user typed or shared, made fetchable, and how a saved recipe names its source. */
object Links {
    private val urlInText = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""[.,;:!?)\]}>»”’'"]+$""")
    private val scheme = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)

    /**
     * The first web link in shared or pasted text ("Look at this! https://x.com/r/1."),
     * without trailing punctuation. A closing bracket is kept when the link
     * itself opened one (Wikipedia-style "/Pie_(food)").
     */
    fun findUrl(text: String): String? {
        val raw = urlInText.find(text)?.value ?: return null
        var url = raw.replace(trailing, "")
        // Give back closing brackets the link opened itself.
        val rest = raw.substring(url.length)
        for (ch in rest) {
            val open = when (ch) { ')' -> '('; ']' -> '['; else -> null } ?: break
            if (url.count { it == open } > url.count { it == ch }) url += ch else break
        }
        return url.takeIf { it.length > "https://".length }
    }

    /**
     * What the user typed in the link field, as a fetchable `https://` or
     * `http://` URL, or null when it can't be a web page link. A bare
     * "example.com/recipe" gets https; spaces and non-ASCII are percent-encoded.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        // A link with words around it ("Try this: https://…"): just the link. A typed
        // link that starts with its scheme is taken whole, spaces and all.
        if (!scheme.containsMatchIn(s)) findUrl(s)?.let { s = it }
        if (!scheme.containsMatchIn(s)) {
            if (s.contains("://") || s.startsWith("//")) s = s.removePrefix("//").substringAfter("://")
            s = "https://$s"
        }
        val uri = parse(s) ?: return null
        val sch = uri.scheme?.lowercase() ?: return null
        if (sch != "http" && sch != "https") return null
        val host = uri.host ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return uri.toASCIIString()
    }

    /** "kitchen.example.com/recipes/tofu" for https://www.kitchen.example.com/recipes/tofu/?utm=x#top. */
    fun source(url: String): String {
        val uri = parse(url) ?: return url.substringAfter("://").substringBefore('?').substringBefore('#').trimEnd('/')
        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val path = uri.path ?: ""
        return (host + path).trimEnd('/')
    }

    /** "kitchen.example.com" (no "www."), for messages. */
    fun host(url: String): String = parse(url)?.host?.lowercase()?.removePrefix("www.") ?: url.substringAfter("://").substringBefore('/')

    /** A java.net.URI for [s], percent-encoding characters URI refuses (spaces, non-ASCII, `|`, `{`...). */
    fun parse(s: String): URI? = try {
        URI(s)
    } catch (e: Exception) {
        try {
            URI(encodeIllegal(s))
        } catch (e: Exception) {
            null
        }
    }

    private const val ILLEGAL = " \"<>\\^`{|}"

    private fun encodeIllegal(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp in 0x21..0x7E && cp.toChar() !in ILLEGAL) {
                append(cp.toChar())
            } else {
                for (b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)) append("%%%02X".format(b.toInt() and 0xFF))
            }
            i += Character.charCount(cp)
        }
    }
}
