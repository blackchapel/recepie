package com.blackchapel.recipai.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Runs the fetcher against a real local HTTP server. */
class WebPageFetcherTest {
    private lateinit var server: HttpServer
    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val seenAgents = mutableListOf<String>()

    private fun route(path: String, handler: (HttpExchange) -> Unit) = server.createContext(path) { ex ->
        ex.requestHeaders.getFirst("User-Agent")?.let { synchronized(seenAgents) { seenAgents += it } }
        try {
            handler(ex)
        } finally {
            ex.close()
        }
    }

    private fun HttpExchange.send(status: Int, body: ByteArray, vararg headers: Pair<String, String>) {
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.write(body)
    }

    private fun HttpExchange.html(body: String) = send(200, body.toByteArray(), "Content-Type" to "text/html; charset=utf-8")

    @BeforeTest fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        route("/recipe") { it.html("<h1>Tofu</h1>") }
        route("/moved") { it.send(301, ByteArray(0), "Location" to "/recipe") }
        route("/hop") { it.send(302, ByteArray(0), "Location" to "$base/moved") }
        route("/loop") { it.send(302, ByteArray(0), "Location" to "/loop") }
        route("/ftp") { it.send(302, ByteArray(0), "Location" to "ftp://example.com/file") }
        route("/missing") { it.send(404, "nope".toByteArray()) }
        route("/blocked") { it.send(403, "denied".toByteArray()) }
        route("/broken") { it.send(503, "later".toByteArray()) }
        route("/photo.png") { it.send(200, byteArrayOf(1, 2, 3), "Content-Type" to "image/png") }
        route("/big") { it.send(200, ByteArray(5000) { 'a'.code.toByte() }, "Content-Type" to "text/html") }
        route("/latin") {
            val body = "<meta charset=\"windows-1252\"><h1>Crème brûlée</h1>".toByteArray(charset("windows-1252"))
            it.send(200, body, "Content-Type" to "text/html")
        }
        route("/zipped") {
            val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { z -> z.write("<p>Zipped soup</p>".toByteArray()) } }
            it.send(200, zipped.toByteArray(), "Content-Type" to "text/html", "Content-Encoding" to "gzip")
        }
        route("/slow") {
            Thread.sleep(1500)
            it.html("too late")
        }
        route("/drip") {
            // Sends a byte every 100 ms for four seconds, so no single read ever waits long.
            it.responseHeaders.add("Content-Type", "text/html")
            it.sendResponseHeaders(200, 0)
            try {
                repeat(40) { _ ->
                    it.responseBody.write('a'.code)
                    it.responseBody.flush()
                    Thread.sleep(100)
                }
            } catch (e: IOException) {
                // The client gave up: that is what the test is about.
            }
        }
        // Handlers run beside each other, so a dripping one doesn't hold up the server.
        server.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        server.start()
    }

    @AfterTest fun stop() = server.stop(0)

    private val fetcher = WebPageFetcher(upgradeHttp = false, maxBytes = 1000, connectTimeoutMs = 2000, readTimeoutMs = 400)

    @Test fun fetchesAPageLikeABrowser() {
        val page = assertIs<FetchResult.Page>(fetcher.fetch("$base/recipe"))
        assertEquals("<h1>Tofu</h1>", page.html)
        assertEquals("$base/recipe", page.url)
        assertTrue(seenAgents.single().startsWith("Mozilla/5.0 (Linux; Android"))
    }

    @Test fun followsRedirectsAndReportsTheFinalUrl() {
        val page = assertIs<FetchResult.Page>(fetcher.fetch("$base/hop"))
        assertEquals("$base/recipe", page.url)
        assertEquals("<h1>Tofu</h1>", page.html)
    }

    @Test fun givesUpOnRedirectLoopsAndOtherSchemes() {
        assertEquals(FetchFailure.TOO_MANY_REDIRECTS, assertIs<FetchResult.Failed>(fetcher.fetch("$base/loop")).reason)
        assertEquals(FetchFailure.NOT_A_PAGE, assertIs<FetchResult.Failed>(fetcher.fetch("$base/ftp")).reason)
    }

    @Test fun classifiesHttpErrors() {
        val missing = assertIs<FetchResult.Failed>(fetcher.fetch("$base/missing"))
        assertEquals(FetchFailure.NOT_FOUND, missing.reason)
        assertEquals(404, missing.status)
        assertEquals(FetchFailure.BLOCKED, assertIs<FetchResult.Failed>(fetcher.fetch("$base/blocked")).reason)
        assertEquals(FetchFailure.SERVER_ERROR, assertIs<FetchResult.Failed>(fetcher.fetch("$base/broken")).reason)
        assertEquals(FetchFailure.NOT_A_PAGE, assertIs<FetchResult.Failed>(fetcher.fetch("$base/photo.png")).reason)
    }

    @Test fun capsTheSize() {
        val page = assertIs<FetchResult.Page>(fetcher.fetch("$base/big"))
        assertEquals(1000, page.html.length)
        assertTrue(page.truncated)
    }

    @Test fun readsTheDeclaredCharsetAndGzip() {
        assertTrue("Crème brûlée" in assertIs<FetchResult.Page>(fetcher.fetch("$base/latin")).html)
        assertEquals("<p>Zipped soup</p>", assertIs<FetchResult.Page>(fetcher.fetch("$base/zipped")).html)
    }

    @Test fun timesOut() {
        assertEquals(FetchFailure.TIMEOUT, assertIs<FetchResult.Failed>(fetcher.fetch("$base/slow")).reason)
    }

    @Test fun aClosedPortIsOffline() {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(FetchFailure.OFFLINE, assertIs<FetchResult.Failed>(fetcher.fetch("http://127.0.0.1:$port/")).reason)
    }

    @Test fun upgradesCleartextLinks() {
        val opened = mutableListOf<URL>()
        val upgrading = WebPageFetcher(open = { url -> opened += url; throw IOException("offline in this test") })
        assertEquals(FetchFailure.FAILED, assertIs<FetchResult.Failed>(upgrading.fetch("http://kitchen.example.com/r")).reason)
        assertEquals("https://kitchen.example.com/r", opened.single().toString())
    }

    private fun failingWith(e: Exception) = WebPageFetcher(open = { throw e })

    @Test fun anHttpLinkWhoseSiteRefusesHttpsIsHttpOnlyNotOffline() {
        val refused = failingWith(ConnectException("failed to connect to /192.0.2.1 (port 443): ECONNREFUSED (Connection refused)"))
        assertEquals(FetchFailure.HTTP_ONLY, assertIs<FetchResult.Failed>(refused.fetch("http://old-blog.example.com/soup")).reason)
        // The same refusal for a link that was https all along is a real connection problem.
        assertEquals(FetchFailure.OFFLINE, assertIs<FetchResult.Failed>(refused.fetch("https://old-blog.example.com/soup")).reason)
        // Really being offline is still offline, whatever the scheme.
        val offline = failingWith(ConnectException("connect failed: ENETUNREACH (Network is unreachable)"))
        assertEquals(FetchFailure.OFFLINE, assertIs<FetchResult.Failed>(offline.fetch("http://old-blog.example.com/soup")).reason)
        assertEquals(FetchFailure.OFFLINE, assertIs<FetchResult.Failed>(failingWith(UnknownHostException("x")).fetch("http://x.example.com/")).reason)
    }

    /** A connection that answers every request with a redirect, without any network. */
    private class RedirectConnection(url: URL, private val location: String) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = 302
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
    }

    @Test fun anHttpsPageThatRedirectsBackToHttpIsHttpOnlyNotARedirectLoop() {
        val downgrading = WebPageFetcher(open = { RedirectConnection(it, "http://old-blog.example.com/soup") })
        val failed = assertIs<FetchResult.Failed>(downgrading.fetch("http://old-blog.example.com/soup"))
        assertEquals(FetchFailure.HTTP_ONLY, failed.reason)
        assertEquals("https://old-blog.example.com/soup", failed.url)
        // A redirect to some other http page is still followed (upgraded), not mistaken for that.
        var n = 0
        val moved = WebPageFetcher(maxRedirects = 2, open = { RedirectConnection(it, "http://old-blog.example.com/${++n}") })
        assertEquals(FetchFailure.TOO_MANY_REDIRECTS, assertIs<FetchResult.Failed>(moved.fetch("https://old-blog.example.com/a")).reason)
    }

    @Test fun aServerDrippingBytesHitsTheOverallDeadline() {
        // Every read gets its byte well inside the read timeout, so only the overall deadline can stop it.
        val patient = WebPageFetcher(upgradeHttp = false, readTimeoutMs = 400, overallTimeoutMs = 600)
        val started = System.nanoTime()
        val failed = assertIs<FetchResult.Failed>(patient.fetch("$base/drip"))
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(FetchFailure.TIMEOUT, failed.reason)
        assertTrue(tookMs < 3000, "stopped by the deadline, not by the end of the stream ($tookMs ms)")
    }

    @Test fun abortingStopsABlockedFetchAtOnce() {
        val abort = FetchAbort()
        val patient = WebPageFetcher(upgradeHttp = false, readTimeoutMs = 5000, overallTimeoutMs = 60_000)
        var result: FetchResult? = null
        val worker = Thread { result = patient.fetch("$base/drip", abort) }
        val started = System.nanoTime()
        worker.start()
        Thread.sleep(300)
        abort.abort()
        worker.join(5000)
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(!worker.isAlive, "the fetch returned")
        assertTrue(tookMs < 3000, "returned long before the stream ended ($tookMs ms)")
        // Not a timeout: the caller stopped it, nobody reads the result.
        assertEquals(FetchFailure.FAILED, assertIs<FetchResult.Failed>(result).reason)
    }

    @Test fun aFetchThatFinishesInTimeIsNotAffectedByTheDeadline() {
        val quick = WebPageFetcher(upgradeHttp = false, overallTimeoutMs = 5000)
        assertIs<FetchResult.Page>(quick.fetch("$base/recipe"))
    }
}
