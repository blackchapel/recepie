package com.blackchapel.recipai.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLException

/** Why a page couldn't be fetched; [LinkMessages] turns it into words. */
enum class FetchFailure { OFFLINE, TIMEOUT, NOT_FOUND, BLOCKED, SERVER_ERROR, NOT_A_PAGE, TOO_MANY_REDIRECTS, INSECURE, FAILED }

sealed interface FetchResult {
    /** [url] is where the page ended up after redirects. [truncated]: it was larger than the cap and was cut. */
    data class Page(val url: String, val html: String, val truncated: Boolean = false) : FetchResult

    data class Failed(val reason: FetchFailure, val url: String, val status: Int = 0) : FetchResult
}

/**
 * Fetches one web page the way a phone browser would, with the limits a
 * recipe import needs: timeouts, a size cap, a handful of redirects (followed
 * by hand so http -> https hops work and nothing but http(s) is followed),
 * gzip, and the charset from the header or the page's own `<meta>`.
 *
 * Plain java.net, so it is unit-tested on the JVM; the app calls it off the
 * main thread through `platform.net.PageFetcher`. Blocking.
 *
 * [upgradeHttp] rewrites `http://` to `https://` before connecting: Android
 * refuses cleartext traffic, and nearly every recipe site serves https.
 */
class WebPageFetcher(
    private val upgradeHttp: Boolean = true,
    private val maxBytes: Int = MAX_BYTES,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxRedirects: Int = 6,
    private val userAgent: String = USER_AGENT,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    companion object {
        /** Recipe pages are rarely over 1 MB; the JSON-LD card is near the top. */
        const val MAX_BYTES = 4 * 1024 * 1024

        /** A current Chrome on Android: some sites refuse or strip pages for unknown clients. */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        private val charsetParam = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-:.]+)", RegexOption.IGNORE_CASE)
    }

    fun fetch(url: String): FetchResult {
        var current = upgrade(url)
        repeat(maxRedirects + 1) {
            val target = try {
                URL(current)
            } catch (e: Exception) {
                return FetchResult.Failed(FetchFailure.FAILED, current)
            }
            val conn = try {
                open(target)
            } catch (e: Exception) {
                return failure(e, current)
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.useCaches = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                val status = conn.responseCode
                when {
                    status in 300..399 && status != 304 -> {
                        val location = conn.getHeaderField("Location")
                            ?: return FetchResult.Failed(FetchFailure.FAILED, current, status)
                        val next = try {
                            URL(target, location)
                        } catch (e: Exception) {
                            return FetchResult.Failed(FetchFailure.FAILED, current, status)
                        }
                        val protocol = next.protocol.lowercase()
                        if (protocol != "http" && protocol != "https") return FetchResult.Failed(FetchFailure.NOT_A_PAGE, current, status)
                        current = upgrade(next.toString())
                    }
                    status == 404 || status == 410 -> return FetchResult.Failed(FetchFailure.NOT_FOUND, current, status)
                    status == 401 || status == 403 || status == 429 || status == 451 ->
                        return FetchResult.Failed(FetchFailure.BLOCKED, current, status)
                    status >= 500 -> return FetchResult.Failed(FetchFailure.SERVER_ERROR, current, status)
                    status !in 200..299 -> return FetchResult.Failed(FetchFailure.FAILED, current, status)
                    else -> {
                        val type = conn.contentType.orEmpty().lowercase()
                        if (type.isNotEmpty() && !isReadable(type)) return FetchResult.Failed(FetchFailure.NOT_A_PAGE, current, status)
                        val (bytes, truncated) = decoded(conn.inputStream, conn.contentEncoding).use { readCapped(it) }
                        val charset = charsetOf(type, bytes)
                        return FetchResult.Page(current, String(bytes, charset), truncated)
                    }
                }
            } catch (e: Exception) {
                return failure(e, current)
            } finally {
                conn.disconnect()
            }
        }
        return FetchResult.Failed(FetchFailure.TOO_MANY_REDIRECTS, current)
    }

    private fun upgrade(url: String): String =
        if (upgradeHttp && url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    private fun isReadable(type: String): Boolean =
        "html" in type || "xml" in type || type.startsWith("text/") || "json" in type

    private fun decoded(stream: InputStream, encoding: String?): InputStream = when (encoding?.trim()?.lowercase()) {
        // Android's HttpURLConnection unzips transparently and drops the header; the JVM's doesn't.
        "gzip", "x-gzip" -> GZIPInputStream(stream)
        "deflate" -> InflaterInputStream(stream)
        else -> stream
    }

    private fun readCapped(input: InputStream): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray() to false
            val room = maxBytes - out.size()
            if (n >= room) {
                out.write(buffer, 0, room)
                return out.toByteArray() to true
            }
            out.write(buffer, 0, n)
        }
    }

    private fun charsetOf(contentType: String, bytes: ByteArray): Charset {
        val name = charsetParam.find(contentType)?.groupValues?.get(1)
            ?: HtmlText.declaredCharset(String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1))
        return try {
            if (name != null) Charset.forName(name) else Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    private fun failure(e: Exception, url: String): FetchResult.Failed = FetchResult.Failed(
        when (e) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> FetchFailure.OFFLINE
            is SocketTimeoutException -> FetchFailure.TIMEOUT
            is SSLException -> FetchFailure.INSECURE
            // Android: "Cleartext HTTP traffic to x not permitted".
            is IOException -> if (e.message?.contains("cleartext", ignoreCase = true) == true) FetchFailure.INSECURE else FetchFailure.FAILED
            else -> FetchFailure.FAILED
        },
        url,
    )
}
