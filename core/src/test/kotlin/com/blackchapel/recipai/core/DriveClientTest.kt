package com.blackchapel.recipai.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveClientTest {
    private val tmp: File = Files.createTempDirectory("drive").toFile()
    private val drive = FakeDrive()
    private val sleeps = ArrayList<Long>()
    private val client = DriveClient(drive, "token", sleeper = { sleeps += it })

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun file(bytes: ByteArray) = File(tmp, "upload.bin").apply { writeBytes(bytes) }

    @Test
    fun quotesAndEncodesQueries() {
        assertEquals("'Bob\\'s \\\\ recipes'", DriveClient.quote("Bob's \\ recipes"))
        assertEquals("a%20b%26c%3D%27", DriveClient.encode("a b&c='"))
        assertEquals("https://x/y?q=a%20b&n=1", DriveClient.url("https://x/y", listOf("q" to "a b", "n" to "1")))
    }

    @Test
    fun readsTheAccount() {
        val user = client.about()
        assertEquals("Ada Cook", user.displayName)
        assertEquals("ada@example.com", user.emailAddress)
    }

    @Test
    fun anExpiredTokenIsAnAuthError() {
        drive.tokenAccepted = false
        assertFailsWith<DriveAuthException> { client.about() }
        assertTrue(sleeps.isEmpty(), "auth errors are not retried")
    }

    @Test
    fun serverErrorsAreRetriedWithBackoff() {
        drive.failNext = 2
        assertEquals("Ada Cook", client.about().displayName)
        assertEquals(listOf(1000L, 2000L), sleeps)
    }

    @Test
    fun givesUpAfterTheLastAttempt() {
        drive.failNext = 10
        val e = assertFailsWith<DriveException> { client.about() }
        assertEquals(503, e.code)
        assertEquals(3, sleeps.size)
    }

    @Test
    fun mapsErrorReasonsToMessages() {
        val full = DriveClient.errorFor(403, """{"error":{"code":403,"errors":[{"reason":"storageQuotaExceeded"}]}}""")
        assertTrue(full.message!!.contains("Drive is full"))
        assertTrue(DriveClient.errorFor(404, "") is DriveNotFoundException)
        assertTrue(DriveClient.errorFor(401, "not json") is DriveAuthException)
        assertEquals("rateLimitExceeded", DriveClient.errorFor(403, """{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""").reason)
        assertTrue(DriveClient.errorFor(400, "<html>").message!!.contains("400"))
    }

    @Test
    fun findsOrCreatesTheFolder() {
        assertNull(client.findFolder("Recipai backups"))
        val id = client.createFolder("Recipai backups")
        assertEquals(id, client.findFolder("Recipai backups"))
        assertTrue(client.folderExists(id))
        drive.files.getValue(id).trashed = true
        assertFalse(client.folderExists(id))
        assertFalse(client.folderExists("missing"))
    }

    @Test
    fun uploadsListsDownloadsAndDeletes() {
        val folder = client.createFolder("Recipai backups")
        val bytes = Random(1).nextBytes(300_000)
        val progress = ArrayList<Long>()
        val up = client.upload(folder, "b.zip", file(bytes), "application/zip", mapOf("app" to "recipai")) { sent, total ->
            assertEquals(bytes.size.toLong(), total)
            progress += sent
        }
        assertEquals("b.zip", up.name)
        assertEquals(bytes.size.toString(), up.size)
        assertEquals("recipai", up.appProperties["app"])
        assertEquals(bytes.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)

        assertEquals(listOf(up.id), client.listFolder(folder).map { it.id })

        val dest = File(tmp, "down.bin")
        client.download(up.id, dest, bytes.size.toLong())
        assertContentEquals(bytes, dest.readBytes())

        client.delete(up.id)
        assertTrue(client.listFolder(folder).isEmpty())
        assertFailsWith<DriveNotFoundException> { client.delete(up.id) }
    }

    @Test
    fun aDroppedUploadResumesWhereItStopped() {
        val folder = client.createFolder("f")
        val bytes = Random(2).nextBytes(200_000)
        drive.dropUploadAfter = 70_000
        val up = client.upload(folder, "b.zip", file(bytes), "application/zip", emptyMap())
        assertContentEquals(bytes, drive.files.getValue(up.id).bytes)
        assertEquals(listOf(1000L), sleeps)
        // The first PUT broke, then one asked how much arrived, then one sent the rest.
        assertEquals(3, drive.log.count { it.startsWith("PUT") })
    }

    @Test
    fun uploadFailsAfterRepeatedDrops() {
        val folder = client.createFolder("f")
        val flaky = object : HttpTransport {
            override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
                if (request.method == "PUT") throw IOException("offline")
                return drive.execute(request, handle)
            }
        }
        val c = DriveClient(flaky, "token", sleeper = { sleeps += it })
        assertFailsWith<IOException> { c.upload(folder, "b.zip", file(ByteArray(10)), "application/zip", emptyMap()) }
        assertEquals(3, sleeps.size)
    }

    @Test
    fun anUploadDriveNeverFinishesStopsInsteadOfLooping() {
        val folder = client.createFolder("f")
        val puts = ArrayList<String?>()
        // Every PUT reads the body, then answers "all 10 bytes arrived" without ever completing.
        val stuck = object : HttpTransport {
            override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
                if (request.method != "PUT") return drive.execute(request, handle)
                puts += request.headers["Content-Range"]
                request.body?.writeTo(java.io.ByteArrayOutputStream())
                return handle(HttpResponse(308, mapOf("Range" to listOf("bytes=0-9")), java.io.ByteArrayInputStream(ByteArray(0))))
            }
        }
        val c = DriveClient(stuck, "token", sleeper = { sleeps += it })
        val e = assertFailsWith<DriveException> { c.upload(folder, "b.zip", file(ByteArray(10)), "application/zip", emptyMap()) }
        assertEquals(308, e.code)
        // After the first send, only status queries: never an empty "bytes 10-9/10" range.
        assertEquals(null, puts.first())
        assertTrue(puts.drop(1).all { it == "bytes */10" }, "$puts")
    }

    @Test
    fun readsTheResumeOffset() {
        assertEquals(0, DriveClient.nextOffset(null, 10))
        assertEquals(5, DriveClient.nextOffset("bytes=0-4", 10))
        assertEquals(10, DriveClient.nextOffset("bytes=0-99", 10))
    }
}
