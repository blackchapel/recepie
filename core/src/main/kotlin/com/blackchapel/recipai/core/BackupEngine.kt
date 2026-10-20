package com.blackchapel.recipai.core

import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One backup in the Drive folder, as the Backup page lists it. */
@Serializable
data class BackupEntry(
    val id: String,
    val name: String,
    /** When it was made (epoch milliseconds). */
    val createdAt: Long,
    val bytes: Long,
    /** Null when the file doesn't say (made by something else). */
    val recipes: Int? = null,
    val photos: Int? = null,
    val kind: String = BackupKind.MANUAL,
    val includesPhotos: Boolean = true,
)

/** What a backup step is doing, for the progress bar. [fraction] is 0..1. */
data class BackupProgress(val label: String, val fraction: Float)

/** A backup read back from Drive, with its photos copied into the photo folder. */
data class RestoredBackup(val snapshot: BackupSnapshot, val recipes: List<Recipe>, val missingPhotos: Int)

/**
 * Backups on Google Drive: builds the archive, uploads it, checks it
 * arrived intact, keeps the newest [keep], lists and downloads them.
 * Blocking; the app runs it off the main thread.
 */
class BackupEngine(
    private val drive: DriveClient,
    /** Scratch space for archives (the app's cache directory). */
    private val workDir: File,
    /** Where recipe photos live (`filesDir/photos`). */
    private val photoDir: File,
    private val keep: Int = KEEP,
) {
    companion object {
        /** The Drive folder backups go into. */
        const val FOLDER_NAME = "Recipai backups"

        /** How many backups stay in the folder; older ones are deleted. */
        const val KEEP = 6
        const val MIME = "application/zip"
        const val FILE_PREFIX = "recipai-backup-"

        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        /** `recipai-backup-20261020-020000.zip` (UTC), so names sort by time. */
        fun fileName(createdAt: Long): String = FILE_PREFIX + NAME_TIME.format(Instant.ofEpochMilli(createdAt)) + ".zip"

        /** Drive `appProperties` describing a backup, so listing needs no downloads. */
        fun properties(snapshot: BackupSnapshot): Map<String, String> = mapOf(
            "app" to BackupFormat.APP,
            "format" to snapshot.version.toString(),
            "createdAt" to snapshot.createdAt.toString(),
            "kind" to snapshot.kind,
            "recipes" to snapshot.recipes.size.toString(),
            "photos" to snapshot.photos.size.toString(),
            "includesPhotos" to snapshot.includesPhotos.toString(),
        )

        /** A Drive file as a [BackupEntry], or null when it isn't a Recipai backup. */
        fun entryOf(file: DriveFile): BackupEntry? {
            val p = file.appProperties
            val ours = p["app"] == BackupFormat.APP || file.name.startsWith(FILE_PREFIX)
            if (!ours || file.mimeType == DriveClient.FOLDER_MIME) return null
            val created = p["createdAt"]?.toLongOrNull()
                ?: file.createdTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L
            return BackupEntry(
                id = file.id,
                name = file.name,
                createdAt = created,
                bytes = file.size?.toLongOrNull() ?: 0L,
                recipes = p["recipes"]?.toIntOrNull(),
                photos = p["photos"]?.toIntOrNull(),
                kind = p["kind"] ?: BackupKind.MANUAL,
                includesPhotos = p["includesPhotos"]?.toBooleanStrictOrNull() ?: true,
            )
        }

        /** Builds a snapshot of [recipes]; with [includePhotos], lists every photo file that exists in [photoDir]. */
        fun snapshot(
            recipes: List<Recipe>,
            settings: BackupSettings,
            kind: String,
            includePhotos: Boolean,
            appVersion: String,
            createdAt: Long,
            photoDir: File,
        ): BackupSnapshot {
            val photos = if (!includePhotos) emptyList() else {
                recipes.asSequence()
                    .flatMap { it.photos.asSequence() }
                    .distinct()
                    .filter { BackupArchive.isSafePhotoName(it) }
                    .mapNotNull { name -> File(photoDir, name).takeIf { it.isFile }?.let { BackupPhoto(name, it.length()) } }
                    .toList()
            }
            return BackupSnapshot(
                app = BackupFormat.APP,
                version = BackupFormat.VERSION,
                createdAt = createdAt,
                appVersion = appVersion,
                kind = kind,
                recipes = recipes,
                settings = settings,
                photos = photos,
                includesPhotos = includePhotos,
            )
        }

        /**
         * Recipes as restored: photo names whose files aren't on this phone
         * (a backup made without photos, or a photo that couldn't be read) are
         * dropped, keeping the same cover where it survived.
         */
        fun withAvailablePhotos(recipes: List<Recipe>, available: (String) -> Boolean): Pair<List<Recipe>, Int> {
            var missing = 0
            val out = recipes.map { r ->
                if (r.photos.isEmpty()) return@map r
                val kept = r.photos.filter(available)
                if (kept.size == r.photos.size) return@map r
                missing += r.photos.size - kept.size
                val cover = r.photos.getOrNull(r.coverIndex)
                val newCover = kept.indexOf(cover).takeIf { it >= 0 } ?: 0
                r.copy(photos = kept, coverIndex = newCover)
            }
            return out to missing
        }

        /** Rough size of a backup of [snapshot] (JSON plus photo files), for "about 1.2 MB". */
        fun estimateBytes(snapshot: BackupSnapshot): Long =
            BackupFormat.encode(snapshot).toByteArray(Charsets.UTF_8).size / 3L + snapshot.photos.sumOf { it.bytes } + 512
    }

    /** The backups folder: [knownId] if it still exists, else the one found by name, else a new one. */
    fun folder(knownId: String?): String {
        if (knownId != null && drive.folderExists(knownId)) return knownId
        return drive.findFolder(FOLDER_NAME) ?: drive.createFolder(FOLDER_NAME)
    }

    /** Backups in [folderId], newest first. */
    fun list(folderId: String): List<BackupEntry> =
        drive.listFolder(folderId).mapNotNull(::entryOf).sortedByDescending { it.createdAt }

    /**
     * Packs [snapshot] with its photos, uploads it into [folderId], checks
     * the upload's size and checksum, then deletes the oldest backups beyond
     * [keep]. Returns the new backup.
     */
    fun backUp(folderId: String, snapshot: BackupSnapshot, onProgress: (BackupProgress) -> Unit = {}): BackupEntry {
        onProgress(BackupProgress("Preparing your recipes", 0.02f))
        workDir.mkdirs()
        val archive = File.createTempFile("backup-", ".zip", workDir)
        try {
            val md5 = MessageDigest.getInstance("MD5")
            DigestOutputStream(FileOutputStream(archive), md5).use { out ->
                BackupArchive.write(
                    out,
                    snapshot,
                    photoFile = { name -> File(photoDir, name).takeIf { it.isFile } },
                    onPhoto = { done, total -> onProgress(BackupProgress("Packing photos $done of $total", 0.05f + 0.2f * done / total)) },
                )
            }
            val localMd5 = md5.digest().toHex()
            val size = archive.length()
            onProgress(BackupProgress("Uploading to Google Drive", 0.25f))
            val uploaded = drive.upload(folderId, fileName(snapshot.createdAt), archive, MIME, properties(snapshot)) { sent, total ->
                onProgress(BackupProgress("Uploading to Google Drive", 0.25f + 0.7f * sent / total.coerceAtLeast(1)))
            }
            onProgress(BackupProgress("Checking the backup", 0.96f))
            val sizeOk = uploaded.size == null || uploaded.size.toLongOrNull() == size
            val md5Ok = uploaded.md5Checksum == null || uploaded.md5Checksum.equals(localMd5, ignoreCase = true)
            if (!sizeOk || !md5Ok) {
                runCatching { drive.delete(uploaded.id) }
                throw DriveException("The backup didn't arrive intact. Try again.")
            }
            val entry = entryOf(uploaded) ?: BackupEntry(
                id = uploaded.id,
                name = uploaded.name,
                createdAt = snapshot.createdAt,
                bytes = size,
                recipes = snapshot.recipes.size,
                photos = snapshot.photos.size,
                kind = snapshot.kind,
                includesPhotos = snapshot.includesPhotos,
            )
            prune(folderId, entry.id)
            onProgress(BackupProgress("Checking the backup", 1f))
            return entry.copy(bytes = size)
        } finally {
            archive.delete()
        }
    }

    /** Deletes backups beyond the newest [keep] (never [justMade]). Best effort. */
    private fun prune(folderId: String, justMade: String) {
        val all = try {
            list(folderId)
        } catch (e: java.io.IOException) {
            return
        }
        all.filter { it.id != justMade }.drop((keep - 1).coerceAtLeast(0)).forEach { old ->
            try {
                drive.delete(old.id)
            } catch (e: java.io.IOException) {
                // Left for the next backup to try again.
            }
        }
    }

    /**
     * Downloads [entry] into a temp file and checks it is a backup this app
     * can read. The caller deletes the file (see [restore]).
     */
    fun download(entry: BackupEntry, onProgress: (BackupProgress) -> Unit = {}): Pair<File, BackupSnapshot> {
        workDir.mkdirs()
        val archive = File.createTempFile("restore-", ".zip", workDir)
        try {
            onProgress(BackupProgress("Downloading the backup", 0f))
            drive.download(entry.id, archive, entry.bytes) { got, total ->
                onProgress(BackupProgress("Downloading the backup", if (total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else 0f))
            }
            return archive to BackupArchive.readSnapshot(archive)
        } catch (e: Exception) {
            archive.delete()
            throw e
        }
    }

    /** Copies the photos out of a downloaded [archive] and returns the recipes to put in place. */
    fun restore(archive: File, onProgress: (BackupProgress) -> Unit = {}): RestoredBackup {
        val extracted = BackupArchive.extract(archive, photoDir) { done, total ->
            onProgress(BackupProgress("Restoring photos $done of $total", done.toFloat() / total))
        }
        val (recipes, missing) = withAvailablePhotos(extracted.snapshot.recipes) { name ->
            name in extracted.photos || (BackupArchive.isSafePhotoName(name) && File(photoDir, name).isFile)
        }
        return RestoredBackup(extracted.snapshot, recipes, missing)
    }
}

/** A fingerprint of the recipe list, to tell whether anything changed since the last backup. */
object BackupFingerprint {
    fun of(recipes: List<Recipe>): String =
        MessageDigest.getInstance("SHA-256").digest(RecipeJson.encode(recipes).toByteArray(Charsets.UTF_8)).toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
