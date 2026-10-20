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
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLException

/**
 * Why a page couldn't be fetched; [LinkMessages] turns it into words.
 * [HTTP_ONLY]: the link was http and the site has no https version (refuses
 * port 443, or sends the https page back to http). Retrying can't help.
 */
enum class FetchFailure { OFFLINE, TIMEOUT, NOT_FOUND, BLOCKED, SERVER_ERROR, NOT_A_PAGE, TOO_MANY_REDIRECTS, INSECURE, HTTP_ONLY, FAILED }

/**
 * Stops a running [WebPageFetcher.fetch] from another thread: [abort] closes
 * its socket, so a read blocked on a slow server returns at once and the
 * thread is free. Make one per fetch.
 */
class FetchAbort {
    @Volatile
    var aborted = false
        private set

    /** Set when the stop came from the fetcher's own overall deadline. */
    @Volatile
    var timedOut = false
        private set

    @Volatile
    private var connection: HttpURLConnection? = null

    fun abort() {
        aborted = true
        connection?.disconnect()
    }

    internal fun expire() {
        timedOut = true
        abort()
    }

    internal fun attach(conn: HttpURLConnection?) {
        connection = conn
        // Aborted between opening and attaching: close it now.
        if (aborted) conn?.disconnect()
    }
}

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
    /** The whole fetch, redirects and reading included (the other timeouts apply per step). */
    private val overallTimeoutMs: Long = 45_000,
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

        /** One daemon thread that fires the overall deadlines; it never does the fetching. */
        private val watchdog: ScheduledThreadPoolExecutor by lazy {
            ScheduledThreadPoolExecutor(1) { r -> Thread(r, "page-fetch-watchdog").apply { isDaemon = true } }
                .apply { removeOnCancelPolicy = true }
        }
    }

    /**
     * Fetches [url]. Gives up with [FetchFailure.TIMEOUT] after
     * [overallTimeoutMs] in all: the read timeout only limits the wait for
     * each chunk, so a server dripping a few bytes at a time would never
     * trip it. [abort] lets another thread stop the fetch early (the user
     * cleared the link): it closes the socket, which ends a blocked read.
     */
    fun fetch(url: String, abort: FetchAbort = FetchAbort()): FetchResult {
        val deadline = watchdog.schedule(Runnable { abort.expire() }, overallTimeoutMs, TimeUnit.MILLISECONDS)
        try {
            return fetchHops(url, abort)
        } finally {
            deadline.cancel(false)
        }
    }

    private fun fetchHops(url: String, abort: FetchAbort): FetchResult {
        var current = upgrade(url)
        // True when [current] is an http link that was rewritten to https.
        var upgraded = current != url
        repeat(maxRedirects + 1) {
            if (abort.aborted) return stopped(abort, current)
            val target = try {
                URL(current)
            } catch (e: Exception) {
                return FetchResult.Failed(FetchFailure.FAILED, current)
            }
            val conn = try {
                open(target)
            } catch (e: Exception) {
                return failure(e, current, upgraded, abort)
            }
            abort.attach(conn)
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
                        val nextUrl = next.toString()
                        val upgradedNext = upgrade(nextUrl)
                        // The page we just got over https sends us back to http: upgrading
                        // that gives the same URL again, so it could only loop. The site
                        // doesn't offer a secure version of this page.
                        if (upgradedNext != nextUrl && upgradedNext == current) {
                            return FetchResult.Failed(FetchFailure.HTTP_ONLY, current, status)
                        }
                        current = upgradedNext
                        upgraded = upgradedNext != nextUrl
                    }
                    status == 404 || status == 410 -> return FetchResult.Failed(FetchFailure.NOT_FOUND, current, status)
                    status == 401 || status == 403 || status == 429 || status == 451 ->
                        return FetchResult.Failed(FetchFailure.BLOCKED, current, status)
                    status >= 500 -> return FetchResult.Failed(FetchFailure.SERVER_ERROR, current, status)
                    status !in 200..299 -> return FetchResult.Failed(FetchFailure.FAILED, current, status)
                    else -> {
                        val type = conn.contentType.orEmpty().lowercase()
                        if (type.isNotEmpty() && !isReadable(type)) return FetchResult.Failed(FetchFailure.NOT_A_PAGE, current, status)
                        val (bytes, truncated) = decoded(conn.inputStream, conn.contentEncoding).use { readCapped(it, abort) }
                        val charset = charsetOf(type, bytes)
                        return FetchResult.Page(current, String(bytes, charset), truncated)
                    }
                }
            } catch (e: Exception) {
                return failure(e, current, upgraded, abort)
            } finally {
                abort.attach(null)
                conn.disconnect()
            }
        }
        return FetchResult.Failed(FetchFailure.TOO_MANY_REDIRECTS, current)
    }

    /** Why a fetch stopped before it finished: the overall deadline, or [FetchAbort.abort]. */
    private fun stopped(abort: FetchAbort, url: String): FetchResult.Failed =
        FetchResult.Failed(if (abort.timedOut) FetchFailure.TIMEOUT else FetchFailure.FAILED, url)

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

    private fun readCapped(input: InputStream, abort: FetchAbort): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            // The watchdog closes the socket; this also covers a stream that kept serving buffered bytes.
            if (abort.aborted) throw SocketTimeoutException("Fetch stopped")
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

    /** [upgraded]: the URL was an http link rewritten to https (see [FetchFailure.HTTP_ONLY]). */
    private fun failure(e: Exception, url: String, upgraded: Boolean, abort: FetchAbort): FetchResult.Failed {
        // Whatever the socket threw when it was closed under the read, the cause is the stop.
        if (abort.aborted) return stopped(abort, url)
        // A refused connection on port 443 for a link that said http://: the site most
        // likely has no https at all. ("Network is unreachable" is really being offline.)
        if (upgraded && e is ConnectException && e.message?.contains("unreachable", ignoreCase = true) != true) {
            return FetchResult.Failed(FetchFailure.HTTP_ONLY, url)
        }
        return classify(e, url)
    }

    private fun classify(e: Exception, url: String): FetchResult.Failed = FetchResult.Failed(
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
