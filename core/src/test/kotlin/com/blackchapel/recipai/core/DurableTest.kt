package com.blackchapel.recipai.core

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DurableTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeReplacesTheFileWithExactlyTheBytes() {
        val f = File(tmp.root, "a.bin")
        f.writeBytes(ByteArray(100) { 1 })
        val bytes = ByteArray(10) { it.toByte() }
        Durable.write(f, bytes)
        assertContentEquals(bytes, f.readBytes())
    }

    @Test
    fun syncKeepsWhatWasWritten() {
        val f = File(tmp.root, "b.txt")
        FileOutputStream(f).use { out ->
            out.write("recipes".toByteArray())
            Durable.sync(out)
        }
        assertEquals("recipes", f.readText())
    }

    @Test
    fun theBackupStatusSurvivesAReload() {
        val file = File(tmp.root, "backup-status.json")
        val store = BackupStatusStore(file)
        store.update { it.copy(connected = true, accountEmail = "ada@example.com") }
        val again = BackupStatusStore(file).read()
        assertEquals(true, again.connected)
        assertEquals("ada@example.com", again.accountEmail)
        assertEquals(false, File(tmp.root, "backup-status.json.tmp").exists())
    }
}
