package com.blackchapel.recipai.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * An in-memory Google Drive speaking just enough of the REST v3 protocol
 * for [DriveClient]: about, search, folders, resumable uploads (including
 * interrupted ones), downloads and deletes.
 */
class FakeDrive : HttpTransport {
    class StoredFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val parents: List<String>,
        val appProperties: Map<String, String>,
        val createdTime: String,
        var bytes: ByteArray = ByteArray(0),
        var trashed: Boolean = false,
    )

    private class Session(val meta: JsonObject, val total: Long) {
        val received = ByteArrayOutputStream()
    }

    val files = LinkedHashMap<String, StoredFile>()
    private val sessions = HashMap<String, Session>()
    private var nextId = 1
    private var clock = 0L

    /** Every request as "METHOD path", for asserting what the client did. */
    val log = ArrayList<String>()

    /** Make the next upload PUT drop the connection after this many bytes. */
    var dropUploadAfter: Long? = null

    /** Answer the next N requests with this status (e.g. 503) before behaving. */
    var failNext: Int = 0
    var failCode: Int = 503

    /** Report a wrong checksum for uploaded files. */
    var corruptChecksums = false

    var tokenAccepted = true

    fun backups(): List<StoredFile> = files.values.filter { it.mimeType == BackupEngine.MIME && !it.trashed }

    private fun newId() = "id${nextId++}"

    private fun now(): String {
        clock += 1000
        return java.time.Instant.ofEpochMilli(1_790_000_000_000 + clock).toString()
    }

    private fun json(code: Int, body: String, headers: Map<String, List<String>> = emptyMap()) =
        HttpResponse(code, headers + ("Content-Type" to listOf("application/json")), ByteArrayInputStream(body.toByteArray()))

    private fun fileJson(f: StoredFile): String {
        val md5 = MessageDigest.getInstance("MD5").digest(f.bytes).joinToString("") { "%02x".format(it) }
        val props = f.appProperties.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        return """{"id":"${f.id}","name":"${f.name}","mimeType":"${f.mimeType}","size":"${f.bytes.size}",""" +
            """"createdTime":"${f.createdTime}","md5Checksum":"${if (corruptChecksums) "0000" else md5}","appProperties":{$props}}"""
    }

    private fun params(url: String): Map<String, String> =
        url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
        val path = request.url.substringBefore('?').removePrefix("https://www.googleapis.com")
        log += "${request.method} $path"
        if (failNext > 0) {
            failNext--
            return handle(json(failCode, """{"error":{"code":$failCode,"errors":[{"reason":"backendError"}]}}"""))
        }
        val isSession = request.url.startsWith("https://upload.fake/")
        if (!isSession && request.headers["Authorization"] != "Bearer token" || !tokenAccepted) {
            return handle(json(401, """{"error":{"code":401,"message":"Invalid Credentials"}}"""))
        }
        val q = params(request.url)
        if (isSession) return handle(uploadChunk(request))
        val body = ByteArrayOutputStream().also { out -> request.body?.writeTo(out) }
        return when {
            request.method == "GET" && path == "/drive/v3/about" ->
                handle(json(200, """{"user":{"displayName":"Ada Cook","emailAddress":"ada@example.com"}}"""))
            request.method == "GET" && path == "/drive/v3/files" -> handle(search(q.getValue("q")))
            request.method == "GET" && path.startsWith("/drive/v3/files/") -> {
                val f = files[path.removePrefix("/drive/v3/files/")]
                when {
                    f == null -> handle(json(404, """{"error":{"code":404,"errors":[{"reason":"notFound"}]}}"""))
                    q["alt"] == "media" -> handle(HttpResponse(200, emptyMap(), ByteArrayInputStream(f.bytes)))
                    else -> handle(json(200, """{"id":"${f.id}","trashed":${f.trashed}}"""))
                }
            }
            request.method == "POST" && path == "/drive/v3/files" -> {
                val meta = Json.parseToJsonElement(body.toString()).jsonObject
                val f = StoredFile(newId(), meta.getValue("name").jsonPrimitive.content, meta.getValue("mimeType").jsonPrimitive.content, emptyList(), emptyMap(), now())
                files[f.id] = f
                handle(json(200, """{"id":"${f.id}"}"""))
            }
            request.method == "POST" && path == "/upload/drive/v3/files" -> {
                val id = "s${sessions.size + 1}"
                sessions[id] = Session(Json.parseToJsonElement(body.toString()).jsonObject, request.headers.getValue("X-Upload-Content-Length").toLong())
                handle(json(200, "", mapOf("Location" to listOf("https://upload.fake/$id"))))
            }
            request.method == "DELETE" && path.startsWith("/drive/v3/files/") -> {
                val gone = files.remove(path.removePrefix("/drive/v3/files/"))
                handle(if (gone == null) json(404, """{"error":{"code":404}}""") else HttpResponse(204, emptyMap(), ByteArrayInputStream(ByteArray(0))))
            }
            else -> handle(json(400, """{"error":{"code":400,"message":"unexpected ${request.method} $path"}}"""))
        }
    }

    private fun search(q: String): HttpResponse {
        val quoted = Regex("""'((?:[^'\\]|\\.)*)'""").findAll(q).map { it.groupValues[1].replace("\\'", "'").replace("\\\\", "\\") }.toList()
        val hits = when {
            "in parents" in q -> files.values.filter { quoted.first() in it.parents && !it.trashed }.sortedByDescending { it.createdTime }
            DriveClient.FOLDER_MIME in q -> files.values.filter { it.mimeType == DriveClient.FOLDER_MIME && it.name == quoted[1] && !it.trashed }.sortedBy { it.createdTime }
            else -> emptyList()
        }
        return json(200, """{"files":[${hits.joinToString(",") { fileJson(it) }}]}""")
    }

    private fun uploadChunk(request: HttpRequest): HttpResponse {
        val id = request.url.substringAfterLast('/')
        val s = sessions[id] ?: return json(404, """{"error":{"code":404}}""")
        val range = request.headers["Content-Range"]
        if (range != null && range.startsWith("bytes */")) {
            return if (s.received.size().toLong() == s.total) complete(id, s) else rangeResponse(s)
        }
        if (range != null) {
            val start = range.removePrefix("bytes ").substringBefore('-').toLong()
            check(start == s.received.size().toLong()) { "resumed at $start but ${s.received.size()} arrived" }
        }
        val drop = dropUploadAfter
        if (drop != null) {
            dropUploadAfter = null
            val limited = object : OutputStream() {
                var n = 0L
                override fun write(b: Int) {
                    if (n >= drop) throw IOException("connection reset")
                    s.received.write(b)
                    n++
                }
            }
            request.body!!.writeTo(limited)
        } else {
            request.body!!.writeTo(s.received)
        }
        return if (s.received.size().toLong() == s.total) complete(id, s) else rangeResponse(s)
    }

    private fun rangeResponse(s: Session): HttpResponse {
        val n = s.received.size()
        return HttpResponse(308, if (n > 0) mapOf("Range" to listOf("bytes=0-${n - 1}")) else emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun complete(id: String, s: Session): HttpResponse {
        sessions.remove(id)
        val props = s.meta["appProperties"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        val parents = s.meta["parents"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val f = StoredFile(newId(), s.meta.getValue("name").jsonPrimitive.content, s.meta.getValue("mimeType").jsonPrimitive.content, parents, props, now(), s.received.toByteArray())
        files[f.id] = f
        return json(200, fileJson(f))
    }
}
