package com.blackchapel.recipai.ui.backup

import com.blackchapel.recipai.core.BackupFingerprint
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupKind
import com.blackchapel.recipai.core.BackupSettings
import com.blackchapel.recipai.core.BackupStatusStore
import com.blackchapel.recipai.core.HttpRequest
import com.blackchapel.recipai.core.HttpResponse
import com.blackchapel.recipai.core.HttpTransport
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.BackupSchedule
import com.blackchapel.recipai.data.LibraryLayout
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BackupJobsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** A tiny in-memory Drive: about, one folder, single-request uploads, list, download, delete. */
    private class MiniDrive(val validTokens: Set<String> = setOf("token")) : HttpTransport {
        class F(val id: String, val name: String, val parent: String?, val props: Map<String, String>, val bytes: ByteArray, val folder: Boolean)

        val files = LinkedHashMap<String, F>()

        /** Called when a backup is downloaded, to let a test change things mid-restore. */
        var onDownload: () -> Unit = {}
        private val sessions = HashMap<String, Pair<String, String>>()
        private var n = 0

        private fun resp(code: Int, body: String = "", headers: Map<String, List<String>> = emptyMap()) =
            HttpResponse(code, headers, ByteArrayInputStream(body.toByteArray()))

        private fun json(f: F): String {
            val md5 = MessageDigest.getInstance("MD5").digest(f.bytes).joinToString("") { "%02x".format(it) }
            val props = f.props.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
            return """{"id":"${f.id}","name":"${f.name}","mimeType":"${if (f.folder) "application/vnd.google-apps.folder" else "application/zip"}",""" +
                """"size":"${f.bytes.size}","md5Checksum":"$md5","appProperties":{$props}}"""
        }

        override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token !in validTokens) return handle(resp(401, """{"error":{"code":401}}"""))
            val raw = ByteArrayOutputStream().also { request.body?.writeTo(it) }.toByteArray()
            val body = String(raw, Charsets.UTF_8)
            val path = request.url.substringBefore('?')
            val q = request.url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
                .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            return handle(
                when {
                    path.startsWith("https://upload.fake/") -> {
                        val (name, meta) = sessions.getValue(path.substringAfterLast('/'))
                        val m = Json.parseToJsonElement(meta).jsonObject
                        val props = m["appProperties"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
                        val parent = m["parents"]!!.jsonArray.first().jsonPrimitive.content
                        val f = F("f${++n}", name, parent, props, raw, false)
                        files[f.id] = f
                        resp(200, json(f))
                    }
                    path.endsWith("/about") -> resp(200, """{"user":{"displayName":"Ada Cook","emailAddress":"ada@example.com"}}""")
                    request.method == "POST" && path.endsWith("/upload/drive/v3/files") -> {
                        val id = "s${++n}"
                        sessions[id] = Json.parseToJsonElement(body).jsonObject.getValue("name").jsonPrimitive.content to body
                        resp(200, headers = mapOf("Location" to listOf("https://upload.fake/$id")))
                    }
                    request.method == "POST" && path.endsWith("/drive/v3/files") -> {
                        val f = F("d${++n}", "Recipai backups", null, emptyMap(), ByteArray(0), true)
                        files[f.id] = f
                        resp(200, """{"id":"${f.id}"}""")
                    }
                    request.method == "GET" && path.endsWith("/drive/v3/files") -> {
                        val query = q.getValue("q")
                        val hits = if ("in parents" in query) {
                            val parent = query.substringAfter("'").substringBefore("'")
                            files.values.filter { it.parent == parent }.reversed()
                        } else {
                            files.values.filter { it.folder }
                        }
                        resp(200, """{"files":[${hits.joinToString(",") { json(it) }}]}""")
                    }
                    request.method == "GET" -> {
                        val f = files[path.substringAfterLast('/')] ?: return handle(resp(404, "{}"))
                        if (q["alt"] == "media") {
                            onDownload()
                            HttpResponse(200, emptyMap(), ByteArrayInputStream(f.bytes))
                        } else resp(200, """{"id":"${f.id}","trashed":false}""")
                    }
                    request.method == "DELETE" -> if (files.remove(path.substringAfterLast('/')) != null) resp(204) else resp(404, "{}")
                    else -> resp(400, "{}")
                },
            )
        }
    }

    private inner class FakePlatform(override val http: HttpTransport) : BackupPlatform {
        override val status = BackupStatusStore(File(tmp.root, "status.json"))
        override val photoDir = File(tmp.root, "photos")
        override val workDir = File(tmp.root, "cache")
        override val appVersion = "0.test"
        val tokens = ArrayDeque<AuthOutcome>()
        val cleared = ArrayList<String>()
        val scheduled = ArrayList<String>()

        override suspend fun authorize(): AuthOutcome = tokens.removeFirstOrNull() ?: AuthOutcome.Granted("token")
        override suspend fun clearToken(token: String) {
            cleared += token
        }
        override fun schedule(frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime) {
            scheduled += "${frequency.name} $isoWeekday $hour $wifiOnly"
        }
        override fun cancelSchedule() {
            scheduled += "cancel"
        }
        override fun runSoon(wifiOnly: Boolean) {
            scheduled += "soon $wifiOnly"
        }
        override suspend fun savedRecipes(): List<Recipe>? = null
        override suspend fun savedSettings(): AppSettings = AppSettings()
    }

    private var clock = 1_790_000_000_000L
    private val drive = MiniDrive()
    private val platform by lazy { FakePlatform(drive) }
    private val jobs by lazy { BackupJobs(platform, clock = { clock += 1000; clock }, now = { ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, ZoneOffset.UTC) }) }

    private fun recipes(vararg titles: String) = titles.map { Recipe(id = it, title = it, servings = 2) }
    private fun input(r: List<Recipe>) = BackupInput(r, BackupSettings(theme = "DARK"), includePhotos = true)
    private val tokens = TokenSource { "token" }

    @Test
    fun connectReadsTheAccountAndMakesTheFolder() = runTest {
        val status = jobs.connect("token")
        assertTrue(status.connected)
        assertEquals("Ada Cook", status.accountName)
        assertEquals("ada@example.com", status.accountEmail)
        assertEquals(1, drive.files.values.count { it.folder })
        // Reconnecting finds the same folder instead of making another.
        jobs.connect("token")
        assertEquals(1, drive.files.values.count { it.folder })
    }

    @Test
    fun aBackupIsRecordedAndUnchangedAutomaticBackupsAreSkipped() = runTest {
        jobs.connect("token")
        val entry = jobs.backUp(input(recipes("Soup", "Toast")), BackupKind.MANUAL, tokens)!!
        assertEquals(2, entry.recipes)
        val status = jobs.status.value
        assertEquals(entry.createdAt, status.lastBackupAt)
        assertEquals(BackupFingerprint.of(recipes("Soup", "Toast")), status.lastFingerprint)
        assertEquals(listOf(entry.id), status.history.map { it.id })
        assertNull(jobs.run.value)

        assertNull(jobs.backUp(input(recipes("Soup", "Toast")), BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true))
        assertTrue(jobs.backUp(input(recipes("Soup")), BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true) != null)
        assertEquals(2, jobs.list(tokens).size)
    }

    @Test
    fun aRefusedTokenIsClearedAndReplacedOnce() = runTest {
        jobs.connect("token")
        var handed = 0
        val flaky = TokenSource { if (handed++ == 0) "stale" else "token" }
        jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, flaky)
        assertEquals(listOf("stale"), platform.cleared)

        try {
            jobs.backUp(input(recipes("Tea")), BackupKind.MANUAL, TokenSource { "stale" })
            fail("expected a sign-in error")
        } catch (e: BackupAuthRequired) {
            assertTrue(jobs.status.value.needsReconnect)
            assertTrue(jobs.status.value.lastError!!.contains("sign in"))
        }
    }

    @Test
    fun silentSignInThatNeedsTheUserAsksToReconnect() = runTest {
        jobs.connect("token")
        platform.tokens += AuthOutcome.NeedsConsent(Any())
        try {
            jobs.backUp(input(recipes("Soup")), BackupKind.AUTOMATIC, jobs.silentTokens)
            fail("expected a sign-in error")
        } catch (e: BackupAuthRequired) {
            assertTrue(jobs.status.value.needsReconnect)
        }
        // The next good backup clears it.
        jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)
        assertFalse(jobs.status.value.needsReconnect)
    }

    @Test
    fun restoreSavesWhatIsHereFirst() = runTest {
        jobs.connect("token")
        val old = jobs.backUp(input(recipes("Soup", "Toast")), BackupKind.MANUAL, tokens)!!
        val restored = jobs.restore(old, { input(recipes("Pie")) }, tokens)
        assertEquals(recipes("Soup", "Toast"), restored.recipes)
        assertEquals("DARK", restored.snapshot.settings.theme)

        val history = jobs.list(tokens)
        assertEquals(listOf(BackupKind.BEFORE_RESTORE, BackupKind.MANUAL), history.map { it.kind })
        assertEquals(1, history.first().recipes)
        assertEquals(BackupFingerprint.of(recipes("Soup", "Toast")), jobs.status.value.lastFingerprint)
        assertNull(jobs.run.value)
    }

    @Test
    fun afterARestoreTheFingerprintFollowsThisPhonesPhotosOption() = runTest {
        jobs.connect("token")
        // The backup includes photos, but this phone has Include photos off.
        val old = jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)!!
        val phone = BackupInput(recipes("Pie"), BackupSettings(theme = "DARK", backupIncludePhotos = false), includePhotos = false)
        jobs.restore(old, { phone }, tokens)
        // Later backups use the phone's option, so nothing looks unsaved or gets re-uploaded.
        assertEquals(BackupFingerprint.of(recipes("Soup"), includePhotos = false), jobs.status.value.lastFingerprint)
        assertNull(jobs.backUp(BackupInput(recipes("Soup"), phone.settings, includePhotos = false), BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true))
    }

    @Test
    fun restoreSavesWhatIsHereAfterTheDownloadNotBefore() = runTest {
        jobs.connect("token")
        val old = jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)!!
        // The user edits while the download runs: the recipes are read once it is done.
        var here = recipes("Pie")
        drive.onDownload = { here = recipes("Pie", "Edited meanwhile") }
        jobs.restore(old, { input(here) }, tokens)
        val before = jobs.list(tokens).first { it.kind == BackupKind.BEFORE_RESTORE }
        assertEquals(2, before.recipes)
    }

    @Test
    fun restoreNeverDeletesTheBackupItRestores() = runTest {
        jobs.connect("token")
        val entries = (1..6).map { i -> jobs.backUp(input(recipes("R$i")), BackupKind.MANUAL, tokens)!! }
        val oldest = entries.first()
        assertEquals(6, jobs.list(tokens).size)
        val restored = jobs.restore(oldest, { input(recipes("Here")) }, tokens)
        assertEquals(recipes("R1"), restored.recipes)
        val left = jobs.list(tokens)
        assertEquals(6, left.size)
        assertTrue(left.any { it.id == oldest.id })
        assertTrue(left.any { it.kind == BackupKind.BEFORE_RESTORE })
    }

    @Test
    fun aFailedRestoreLeavesNoArchiveBehind() = runTest {
        jobs.connect("token")
        val old = jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)!!
        try {
            jobs.restore(old, { throw IllegalStateException("gone") }, tokens)
            fail("expected the failure")
        } catch (e: IllegalStateException) {
            assertTrue(platform.workDir.listFiles().orEmpty().isEmpty())
            assertNull(jobs.run.value)
        }
    }

    @Test
    fun turningIncludePhotosOnIsABackupEvenWhenTheRecipesAreTheSame() = runTest {
        jobs.connect("token")
        val r = recipes("Soup")
        val noPhotos = BackupInput(r, BackupSettings(theme = "DARK"), includePhotos = false)
        assertTrue(jobs.backUp(noPhotos, BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true) != null)
        assertNull(jobs.backUp(noPhotos, BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true))
        // Photos switched on, nothing else changed: not skipped.
        assertTrue(jobs.backUp(input(r), BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true) != null)
        assertNull(jobs.backUp(input(r), BackupKind.AUTOMATIC, tokens, skipIfUnchanged = true))
    }

    @Test
    fun disconnectForgetsTheAccountAndStopsTheSchedule() = runTest {
        jobs.connect("token")
        jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)
        jobs.disconnect()
        val s = jobs.status.value
        assertFalse(s.connected)
        assertNull(s.accountEmail)
        assertNull(s.lastBackupAt)
        assertTrue("cancel" in platform.scheduled)
        assertEquals(listOf("token"), platform.cleared)
        // Backups stay in Drive.
        assertEquals(1, drive.files.values.count { !it.folder })
    }

    @Test
    fun reschedulesOnlyWhenTheScheduleChanges() = runTest {
        val weekly = AppSettings(backupSchedule = BackupSchedule.WEEKLY, backupWeekday = 3, backupHour = 20)
        jobs.reschedule(weekly)
        assertEquals(listOf("cancel"), platform.scheduled) // not connected yet
        jobs.connect("token")
        jobs.reschedule(weekly)
        jobs.reschedule(weekly)
        assertEquals(listOf("cancel", "WEEKLY 3 20 true"), platform.scheduled)
        jobs.reschedule(weekly.copy(backupWifiOnly = false))
        jobs.reschedule(weekly.copy(backupSchedule = BackupSchedule.MANUAL))
        assertEquals(listOf("cancel", "WEEKLY 3 20 true", "WEEKLY 3 20 false", "cancel"), platform.scheduled)
    }

    @Test
    fun anOverdueScheduleIsCaughtUpOnce() = runTest {
        val daily = AppSettings(backupSchedule = BackupSchedule.DAILY, backupWifiOnly = false)
        assertFalse(jobs.catchUpIfOverdue(daily)) // not connected
        jobs.connect("token")
        assertFalse(jobs.catchUpIfOverdue(daily)) // no backup yet to be overdue from
        jobs.backUp(input(recipes("Soup")), BackupKind.MANUAL, tokens)
        assertFalse(jobs.catchUpIfOverdue(daily)) // just backed up
        clock += 25 * 3_600_000L
        assertFalse(jobs.catchUpIfOverdue(daily)) // due, but within the slack: left to the schedule
        clock += 2 * 3_600_000L
        assertFalse(jobs.catchUpIfOverdue(daily.copy(backupSchedule = BackupSchedule.MANUAL)))
        assertFalse(jobs.catchUpIfOverdue(daily.copy(backupSchedule = BackupSchedule.WEEKLY)))
        assertTrue(jobs.catchUpIfOverdue(daily))
        assertEquals(listOf("soon false"), platform.scheduled)
    }

    @Test
    fun restoredSettingsKeepThisPhonesBackupOptions() {
        val here = AppSettings(theme = ThemeMode.LIGHT, backupSchedule = BackupSchedule.DAILY, backupWifiOnly = false)
        val backup = AppSettings(theme = ThemeMode.DARK, layout = LibraryLayout.LIST, haptics = false, backupSchedule = BackupSchedule.MANUAL).toBackup()
        val out = here.restoredFrom(backup)
        assertEquals(ThemeMode.DARK, out.theme)
        assertEquals(LibraryLayout.LIST, out.layout)
        assertFalse(out.haptics)
        assertEquals(BackupSchedule.DAILY, out.backupSchedule)
        assertFalse(out.backupWifiOnly)
        // Unknown values from a newer app are skipped.
        assertEquals(ThemeMode.LIGHT, here.restoredFrom(BackupSettings(theme = "SEPIA")).theme)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun replaceAllSwapsTheWholeLibraryInOneSave() = runTest {
        val file = File(tmp.root, "recipes.json")
        val repo = RecipeRepository(file, this, StandardTestDispatcher(testScheduler), clock = { 1L })
        advanceUntilIdle()
        repo.replaceAll(recipes("A", "B"))
        advanceUntilIdle()
        assertEquals(listOf("A", "B"), repo.recipes.value.map { it.id })
        assertEquals(listOf("A", "B"), RecipeRepository(file, this, StandardTestDispatcher(testScheduler)).also { advanceUntilIdle() }.recipes.value.map { it.id })
    }
}
