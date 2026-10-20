package com.blackchapel.recipai.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the app remembers about Google Drive backup, in a small JSON file
 * in app-private storage. No tokens: Google Play services keeps those.
 */
@Serializable
data class BackupStatus(
    val connected: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    /** The "Recipai backups" folder, so each backup doesn't search for it. */
    val folderId: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupBytes: Long? = null,
    /** [BackupFingerprint] of the recipes in the last backup. */
    val lastFingerprint: String? = null,
    /** The last failure (user-facing), cleared by the next success. */
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
    /** Google refused a silent sign-in: the user has to connect again. */
    val needsReconnect: Boolean = false,
    /** The folder's backups as last listed, newest first (shown while refreshing). */
    val history: List<BackupEntry> = emptyList(),
    /** What automatic backups were last scheduled with ([BackupTiming.signature]). */
    val scheduled: String? = null,
)

/**
 * Reads and writes [BackupStatus], one instance per file per process (the
 * app and the background worker share it). Writes go through a temp file,
 * so a crash never leaves a half-written file; listeners hear every change.
 */
class BackupStatusStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val lock = Any()
    private var cached: BackupStatus? = null
    private val listeners = CopyOnWriteArrayList<(BackupStatus) -> Unit>()

    fun read(): BackupStatus = synchronized(lock) { cached ?: load().also { cached = it } }

    /** Applies [transform], saves, notifies listeners and returns the new status. */
    fun update(transform: (BackupStatus) -> BackupStatus): BackupStatus {
        val next = synchronized(lock) {
            val current = cached ?: load()
            val next = transform(current)
            if (next != current) save(next)
            cached = next
            next
        }
        listeners.forEach { it(next) }
        return next
    }

    /** Calls [listener] on every change; returns a function that stops it. */
    fun listen(listener: (BackupStatus) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun load(): BackupStatus {
        if (!file.isFile) return BackupStatus()
        return try {
            json.decodeFromString(BackupStatus.serializer(), file.readText())
        } catch (e: SerializationException) {
            BackupStatus()
        } catch (e: IllegalArgumentException) {
            BackupStatus()
        } catch (e: IOException) {
            BackupStatus()
        }
    }

    private fun save(status: BackupStatus) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            Durable.write(tmp, json.encodeToString(BackupStatus.serializer(), status).toByteArray(Charsets.UTF_8))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            // Kept in memory; the next change tries to save again.
        }
    }
}
