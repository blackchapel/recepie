package com.blackchapel.recipai.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A request body streamed to the connection ([length] -1 means unknown). */
interface HttpBody {
    val contentType: String
    val length: Long
    fun writeTo(out: OutputStream)

    companion object {
        fun of(text: String, contentType: String): HttpBody {
            val bytes = text.toByteArray(Charsets.UTF_8)
            return object : HttpBody {
                override val contentType = contentType
                override val length = bytes.size.toLong()
                override fun writeTo(out: OutputStream) = out.write(bytes)
            }
        }

        val EMPTY: HttpBody = of("", "application/octet-stream")
    }
}

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: HttpBody? = null,
    /** Off for resumable uploads: a 308 there means "resume incomplete", not a redirect. */
    val followRedirects: Boolean = true,
)

class HttpResponse(val code: Int, headers: Map<String, List<String>>, val body: InputStream) {
    private val headers = headers.mapKeys { it.key.lowercase() }

    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()

    fun text(): String = body.readBytes().toString(Charsets.UTF_8)

    val isSuccess: Boolean get() = code in 200..299
}

/**
 * Runs one HTTP exchange. [execute] hands the response to [handle] while
 * the connection is open (so a download can stream) and closes it after.
 * Network failures throw [java.io.IOException].
 */
interface HttpTransport {
    fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T
}

/** [HttpTransport] on java.net.HttpURLConnection (the JVM's and Android's built-in client). */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 60_000,
) : HttpTransport {
    override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
        val conn = URL(request.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = request.method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = request.followRedirects
            conn.useCaches = false
            request.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val body = request.body ?: if (request.method == "POST" || request.method == "PUT") HttpBody.EMPTY else null
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", body.contentType)
                if (body.length >= 0) conn.setFixedLengthStreamingMode(body.length) else conn.setChunkedStreamingMode(0)
                conn.outputStream.use { body.writeTo(it) }
            }
            val code = conn.responseCode
            val stream = (if (code >= 400) conn.errorStream else conn.inputStream) ?: ByteArrayInputStream(ByteArray(0))
            val headers = conn.headerFields.orEmpty().filterKeys { it != null }.mapKeys { it.key!! }
            return stream.use { handle(HttpResponse(code, headers, it)) }
        } finally {
            conn.disconnect()
        }
    }
}
