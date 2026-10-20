package com.blackchapel.recipai.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The settings a backup carries. Every field is optional: a missing one
 * (an older backup, or a setting added later) leaves that setting alone on
 * restore. Enum settings are stored by name so an unknown value from a
 * newer app is skipped instead of failing the whole backup.
 */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val haptics: Boolean? = null,
    val layout: String? = null,
    val keepScreenOn: Boolean? = null,
    val timerSound: Boolean? = null,
    val backupSchedule: String? = null,
    val backupWeekday: Int? = null,
    val backupHour: Int? = null,
    val backupIncludePhotos: Boolean? = null,
    val backupWifiOnly: Boolean? = null,
)

/** A photo file inside the archive (`photos/<name>`). */
@Serializable
data class BackupPhoto(val name: String, val bytes: Long)

/** Why a backup was made; shown in the backup list. */
object BackupKind {
    const val MANUAL = "manual"
    const val AUTOMATIC = "automatic"
    const val BEFORE_RESTORE = "before-restore"

    /** The list's label: "Manual", "Automatic", "Before restore". */
    fun label(kind: String): String = when (kind) {
        AUTOMATIC -> "Automatic"
        BEFORE_RESTORE -> "Before restore"
        else -> "Manual"
    }
}

/**
 * `backup.json`, the heart of a backup archive: the recipes, the settings
 * and the list of photos stored next to it under `photos/`.
 *
 * Versioned from day one ([BackupFormat.VERSION]). Readers ignore unknown
 * keys, so adding a field is backward compatible; a change an older app
 * can't read must bump the version (and is a MAJOR app version bump, see
 * CLAUDE.md).
 */
@Serializable
data class BackupSnapshot(
    /** Always "recipai": tells a Recipai backup from any other zip. */
    val app: String,
    val version: Int,
    /** When the backup was made (epoch milliseconds). */
    val createdAt: Long,
    /** The app version that wrote it, e.g. "0.2.1". */
    val appVersion: String,
    /** One of [BackupKind]'s values. */
    val kind: String,
    val recipes: List<Recipe>,
    val settings: BackupSettings,
    /** Photos in the archive. Empty when the backup was made without photos. */
    val photos: List<BackupPhoto>,
    /** False when "Include photos" was off: recipes keep their photo names but the files are not in the archive. */
    val includesPhotos: Boolean,
) {
    val photoCount: Int get() = photos.size
}

/** Reads and writes [BackupSnapshot] JSON. */
object BackupFormat {
    /** The newest backup format this app writes and reads. */
    const val VERSION = 1
    const val APP = "recipai"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(BackupSnapshot.serializer(), snapshot)

    /**
     * Reads `backup.json`. Throws [BackupFormatException] for text that is
     * not a Recipai backup, or one written by a newer format version.
     */
    fun decode(text: String): BackupSnapshot {
        val snapshot = try {
            json.decodeFromString(BackupSnapshot.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException("This isn't a Recipai backup.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("This isn't a Recipai backup.", e)
        }
        if (snapshot.app != APP) throw BackupFormatException("This isn't a Recipai backup.")
        if (snapshot.version > VERSION) {
            throw BackupFormatException("This backup was made by a newer version of Recipai. Update the app to restore it.")
        }
        return snapshot
    }
}

/** A backup that can't be read. [message] is user-facing. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
