package com.blackchapel.recipai.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.FileInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLEncoder

@Serializable
data class DriveUser(val displayName: String? = null, val emailAddress: String? = null)

@Serializable
data class DriveAbout(val user: DriveUser? = null)

/** The fields of a Drive file this app asks for ([DriveClient.FILE_FIELDS]). */
@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    val mimeType: String? = null,
    /** Bytes, as a decimal string (Drive's int64 encoding). */
    val size: String? = null,
    /** RFC 3339, e.g. "2026-10-20T02:00:03.120Z". */
    val createdTime: String? = null,
    val md5Checksum: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
)

@Serializable
data class DriveFileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

@Serializable
private data class DriveTrashed(val trashed: Boolean = false)

@Serializable
private data class DriveErrorItem(val reason: String? = null)

@Serializable
private data class DriveErrorDetail(val code: Int = 0, val message: String? = null, val errors: List<DriveErrorItem> = emptyList())

@Serializable
private data class DriveErrorBody(val error: DriveErrorDetail? = null)

/**
 * A Drive request that failed. [message] is user-facing; [code] is the HTTP
 * status (0 for a network failure) and [reason] Google's error reason.
 */
open class DriveException(message: String, val code: Int = 0, val reason: String? = null, cause: Throwable? = null) :
    IOException(message, cause)

/** The access token was refused (expired or revoked): get a fresh one, or reconnect. */
class DriveAuthException(message: String = "Google Drive needs you to sign in again.") : DriveException(message, 401)

/** The file or folder is gone (deleted, or trashed and emptied). */
class DriveNotFoundException(message: String = "That file is no longer in your Google Drive.") : DriveException(message, 404)

/**
 * Google Drive REST v3, just what backups need: the account, one folder,
 * resumable uploads, listing, downloads and deletes. Blocking; call it off
 * the main thread. Retries rate limits, server errors and dropped
 * connections a few times with backoff, and resumes an interrupted upload
 * where it stopped.
 */
class DriveClient(
    private val http: HttpTransport,
    private val accessToken: String,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val maxAttempts: Int = 4,
) {
    companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val REVOKE = "https://oauth2.googleapis.com/revoke"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FILE_FIELDS = "id,name,mimeType,size,createdTime,md5Checksum,appProperties"
        const val MAX_PAGES = 20

        private val json = Json { ignoreUnknownKeys = true }

        /** A string literal for a Drive search query: quoted, with `\` and `'` escaped. */
        fun quote(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

        /** Percent-encodes a query parameter (spaces as %20, which every Google API accepts). */
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun url(base: String, params: List<Pair<String, String>>): String =
            if (params.isEmpty()) base else base + "?" + params.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

        /**
         * Revokes [token] (and with it the app's access to Drive) at Google.
         * Best effort: the caller ignores failures; a token that is already
         * invalid is fine.
         */
        fun revoke(http: HttpTransport, token: String) {
            http.execute(
                HttpRequest("POST", REVOKE, body = HttpBody.of("token=${encode(token)}", "application/x-www-form-urlencoded")),
            ) { it.text() }
        }

        /** Turns an error response into the right [DriveException]. */
        internal fun errorFor(code: Int, body: String): DriveException {
            val detail = try {
                json.decodeFromString(DriveErrorBody.serializer(), body).error
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            val reason = detail?.errors?.firstOrNull()?.reason
            return when {
                code == 401 -> DriveAuthException()
                code == 404 -> DriveNotFoundException()
                reason == "storageQuotaExceeded" || reason == "quotaExceeded" ->
                    DriveException("Your Google Drive is full. Free up some space, then try again.", code, reason)
                code == 403 && (reason == "insufficientPermissions" || reason == "insufficientFilePermissions" || reason == "appNotAuthorizedToFile") ->
                    DriveException("Recipai doesn't have access to that file in Google Drive.", code, reason)
                code == 403 && (reason == "accessNotConfigured" || reason == "SERVICE_DISABLED") ->
                    DriveException("The Google Drive API isn't turned on for this app's Google Cloud project.", code, reason)
                code == 429 || code == 403 && (reason == "rateLimitExceeded" || reason == "userRateLimitExceeded") ->
                    DriveException("Google Drive is busy. Try again in a minute.", code, reason)
                code >= 500 -> DriveException("Google Drive had a problem. Try again in a minute.", code, reason)
                else -> DriveException("Google Drive refused the request ($code).", code, reason)
            }
        }

        private fun isRetryable(e: DriveException): Boolean =
            e.code == 0 || e.code == 429 || e.code >= 500 || e.reason == "rateLimitExceeded" || e.reason == "userRateLimitExceeded"

        /** "bytes=0-1234" means 1235 bytes arrived; no Range header means none did. */
        internal fun nextOffset(range: String?, total: Long): Long {
            val end = range?.substringAfterLast('-')?.trim()?.toLongOrNull() ?: return 0
            return (end + 1).coerceIn(0, total)
        }
    }

    private fun auth(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf("Authorization" to "Bearer $accessToken") + extra

    /** Runs [request]; a 2xx response goes to [read], anything else becomes a [DriveException]. */
    private fun <T> call(request: HttpRequest, read: (HttpResponse) -> T): T =
        http.execute(request) { r -> if (r.isSuccess) read(r) else throw errorFor(r.code, r.text()) }

    /** [block] with retries for transient failures (network, 429, 5xx), with exponential backoff. */
    private fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: DriveException) {
                if (!isRetryable(e) || attempt >= maxAttempts) throw e
            } catch (e: IOException) {
                if (attempt >= maxAttempts) throw e
            }
            sleeper(1000L shl (attempt - 1))
        }
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw DriveException("Google Drive sent a reply Recipai didn't understand.", 200, cause = e)
    } catch (e: IllegalArgumentException) {
        throw DriveException("Google Drive sent a reply Recipai didn't understand.", 200, cause = e)
    }

    /** The signed-in Google account (the About resource). */
    fun about(): DriveUser = retrying {
        call(HttpRequest("GET", url("$API/about", listOf("fields" to "user(displayName,emailAddress)")), auth())) {
            decode(it.text(), DriveAbout.serializer()).user ?: DriveUser()
        }
    }

    /** Files matching the Drive search [query], newest first, following pages. */
    fun search(query: String, fields: String = FILE_FIELDS, orderBy: String = "createdTime desc"): List<DriveFile> {
        val all = ArrayList<DriveFile>()
        var pageToken: String? = null
        var pages = 0
        do {
            val params = buildList {
                add("q" to query)
                add("spaces" to "drive")
                add("fields" to "nextPageToken,files($fields)")
                add("orderBy" to orderBy)
                add("pageSize" to "100")
                pageToken?.let { add("pageToken" to it) }
            }
            val page = retrying {
                call(HttpRequest("GET", url("$API/files", params), auth())) { decode(it.text(), DriveFileList.serializer()) }
            }
            all += page.files
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        return all
    }

    /** The oldest folder called [name] that this app can see, or null. */
    fun findFolder(name: String): String? =
        search("mimeType = ${quote(FOLDER_MIME)} and name = ${quote(name)} and trashed = false", "id,createdTime", "createdTime").firstOrNull()?.id

    /** True when [folderId] still exists and is not in the trash. */
    fun folderExists(folderId: String): Boolean = try {
        retrying {
            call(HttpRequest("GET", url("$API/files/${encode(folderId)}", listOf("fields" to "id,trashed")), auth())) {
                !decode(it.text(), DriveTrashed.serializer()).trashed
            }
        }
    } catch (e: DriveNotFoundException) {
        false
    }

    /** Creates a folder called [name] at the top of My Drive and returns its id. */
    fun createFolder(name: String): String {
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
        }
        return retrying {
            call(
                HttpRequest("POST", url("$API/files", listOf("fields" to "id")), auth(), HttpBody.of(meta.toString(), "application/json; charset=UTF-8")),
            ) { decode(it.text(), DriveFile.serializer()).id }
        }
    }

    /** The files in [folderId] (not trashed), newest first. */
    fun listFolder(folderId: String): List<DriveFile> =
        search("${quote(folderId)} in parents and trashed = false")

    fun delete(fileId: String) {
        retrying { call(HttpRequest("DELETE", "$API/files/${encode(fileId)}", auth())) { } }
    }

    /**
     * Uploads [file] into [folderId] as [name] with a resumable upload: one
     * request carries the whole file; if the connection drops, the upload
     * asks Drive how much arrived and sends the rest. [onProgress] gets
     * (bytes sent, total).
     */
    fun upload(
        folderId: String,
        name: String,
        file: File,
        mimeType: String,
        appProperties: Map<String, String>,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): DriveFile {
        val total = file.length()
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", mimeType)
            putJsonArray("parents") { add(JsonPrimitive(folderId)) }
            putJsonObject("appProperties") { appProperties.forEach { (k, v) -> put(k, v) } }
        }
        val session = retrying {
            call(
                HttpRequest(
                    "POST",
                    url("$UPLOAD/files", listOf("uploadType" to "resumable", "fields" to FILE_FIELDS)),
                    auth(mapOf("X-Upload-Content-Type" to mimeType, "X-Upload-Content-Length" to total.toString())),
                    HttpBody.of(meta.toString(), "application/json; charset=UTF-8"),
                ),
            ) { it.header("Location") ?: throw DriveException("Google Drive didn't start the upload. Try again.", it.code) }
        }

        var offset = 0L
        var failures = 0
        // False after a failure: ask Drive how much arrived before sending more.
        var known = true
        while (true) {
            try {
                when (val step = if (known) sendRest(session, file, offset, total, mimeType, onProgress) else queryUpload(session, total)) {
                    is UploadStep.Done -> {
                        onProgress(total, total)
                        return step.file
                    }
                    is UploadStep.Resume -> {
                        // A send that ends without moving forward, or Drive holding every byte without
                        // finishing, counts as a failure, so this can't loop forever.
                        val stalled = (known && step.from <= offset) || step.from >= total
                        if (stalled && ++failures >= maxAttempts) {
                            throw DriveException("The upload kept stopping. Try again on a better connection.", 308)
                        }
                        offset = step.from
                        // Nothing left to send: ask Drive again rather than send an empty range.
                        known = step.from < total
                    }
                }
            } catch (e: DriveException) {
                if (!isRetryable(e) || ++failures >= maxAttempts) throw e
                sleeper(1000L shl (failures - 1))
                known = false
            } catch (e: IOException) {
                if (++failures >= maxAttempts) throw e
                sleeper(1000L shl (failures - 1))
                known = false
            }
        }
    }

    private sealed interface UploadStep {
        class Done(val file: DriveFile) : UploadStep
        class Resume(val from: Long) : UploadStep
    }

    /** Sends [file] from [offset] to the end in one PUT. */
    private fun sendRest(
        session: String,
        file: File,
        offset: Long,
        total: Long,
        mimeType: String,
        onProgress: (Long, Long) -> Unit,
    ): UploadStep {
        val body = object : HttpBody {
            override val contentType = mimeType
            override val length = total - offset
            override fun writeTo(out: OutputStream) {
                FileInputStream(file).use { input ->
                    var skip = offset
                    while (skip > 0) {
                        val n = input.skip(skip)
                        if (n <= 0) throw IOException("Couldn't read the backup file")
                        skip -= n
                    }
                    val counting = CountingStream(out) { written -> onProgress(offset + written, total) }
                    input.copyTo(counting, 64 * 1024)
                    counting.flush()
                }
            }
        }
        val headers = if (offset > 0) mapOf("Content-Range" to "bytes $offset-${total - 1}/$total") else emptyMap()
        return http.execute(HttpRequest("PUT", session, auth(headers), body, followRedirects = false)) { r -> uploadStep(r, total) }
    }

    /** Asks Drive how much of an interrupted upload arrived. */
    private fun queryUpload(session: String, total: Long): UploadStep =
        http.execute(
            HttpRequest("PUT", session, auth(mapOf("Content-Range" to "bytes */$total")), HttpBody.EMPTY, followRedirects = false),
        ) { r -> uploadStep(r, total) }

    private fun uploadStep(r: HttpResponse, total: Long): UploadStep = when {
        r.isSuccess -> UploadStep.Done(decode(r.text(), DriveFile.serializer()))
        r.code == 308 -> UploadStep.Resume(nextOffset(r.header("Range"), total))
        // The upload session expired; the caller starts the backup over.
        r.code == 404 || r.code == 410 -> throw DriveException("The upload was interrupted for too long. Try again.", r.code)
        else -> throw errorFor(r.code, r.text())
    }

    /** Downloads [fileId] into [dest], reporting (bytes received, [expected]). */
    fun download(fileId: String, dest: File, expected: Long, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        retrying {
            call(HttpRequest("GET", url("$API/files/${encode(fileId)}", listOf("alt" to "media")), auth())) { r ->
                dest.outputStream().use { out ->
                    val counting = CountingStream(out) { onProgress(it, expected) }
                    r.body.copyTo(counting, 64 * 1024)
                    counting.flush()
                }
            }
        }
    }
}

/** Counts bytes written and reports the running total. */
private class CountingStream(out: OutputStream, private val onCount: (Long) -> Unit) : FilterOutputStream(out) {
    private var count = 0L

    override fun write(b: Int) {
        out.write(b)
        count++
        onCount(count)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
        onCount(count)
    }
}
