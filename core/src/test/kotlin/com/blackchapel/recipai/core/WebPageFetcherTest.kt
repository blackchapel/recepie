package com.blackchapel.recipai.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
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
}
