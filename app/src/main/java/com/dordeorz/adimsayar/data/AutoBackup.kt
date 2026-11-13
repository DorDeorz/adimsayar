package com.dordeorz.adimsayar.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The automatic backup as the settings show it; [folder] is the chosen folder's name, null while it is off. */
data class AutoBackupState(val folder: String?, val lastMs: Long, val failed: Boolean)

/** A file in the backup folder, with the time it was last written. */
data class BackupFile(val name: String, val modified: Long)

/**
 * Writes the full backup into a folder the user chose, once a day, and keeps the newest [KEEP] days.
 * It runs with the 15-minute job, so it has no alarm of its own. The folder stays with this phone:
 * it is not part of the backup.
 */
object AutoBackup {

    const val KEEP = 7
    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_NAME = "name"
    private const val KEY_LAST = "last"
    private const val KEY_TRIED = "tried"
    private const val KEY_FAILED = "failed"

    // Not the name of a backup made by hand, so tidying up never deletes one of those.
    private const val FILE_PREFIX = "fitwren-otomatik-yedek-"
    private const val FILE_SUFFIX = ".json"
    private const val MIME = "application/json"
    private const val RETRY_MS = 60 * 60 * 1000L
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private val lock = Mutex()

    @Volatile
    private var shown: MutableStateFlow<AutoBackupState>? = null

    fun state(context: Context): StateFlow<AutoBackupState> = flow(context).asStateFlow()

    /** Turns the backup on with [folder], a folder the user picked, or off with null. */
    fun setFolder(context: Context, folder: Uri?) {
        val resolver = context.contentResolver
        val prefs = prefs(context)
        val old = prefs.getString(KEY_FOLDER, null)
        val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // The folder must stay writable after a restart of the phone.
        if (folder != null) runCatching { resolver.takePersistableUriPermission(folder, access) }
        if (old != null && old != folder?.toString()) runCatching { resolver.releasePersistableUriPermission(old.toUri(), access) }
        prefs.edit {
            clear()
            if (folder != null) {
                putString(KEY_FOLDER, folder.toString())
                putString(KEY_NAME, folderName(context, folder))
            }
        }
        flow(context).value = read(context)
    }

    /** Writes today's backup when it is on and none was written today. */
    suspend fun runIfDue(context: Context) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_FOLDER, null) == null) return
        if (due(prefs.getLong(KEY_LAST, 0L), prefs.getLong(KEY_TRIED, 0L), System.currentTimeMillis(), ZoneId.systemDefault())) run(context)
    }

    /** Writes a backup now. Returns the rows written, or null when it is off or the folder could not be written. */
    suspend fun run(context: Context): Int? = lock.withLock {
        val prefs = prefs(context)
        val folder = prefs.getString(KEY_FOLDER, null)?.toUri() ?: return@withLock null
        val now = System.currentTimeMillis()
        prefs.edit { putLong(KEY_TRIED, now) }
        val rows = withContext(Dispatchers.IO) {
            try {
                write(context, folder, fileName(LocalDate.now()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The folder was deleted or moved, its storage is gone, or the phone is full.
                null
            }
        }
        prefs.edit {
            putBoolean(KEY_FAILED, rows == null)
            if (rows != null) putLong(KEY_LAST, now)
        }
        flow(context).value = read(context)
        rows
    }

    /** Due once a calendar day; after a try that failed, an hour later at the earliest. */
    fun due(lastMs: Long, triedMs: Long, nowMs: Long, zone: ZoneId): Boolean {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        if (lastMs > 0L && !day(lastMs).isBefore(day(nowMs))) return false
        val failedTry = triedMs > lastMs
        return !failedTry || nowMs - triedMs >= RETRY_MS || nowMs < triedMs
    }

    fun fileName(date: LocalDate): String = "$FILE_PREFIX$date$FILE_SUFFIX"

    /**
     * The automatic backups among [files] that are no longer needed: older copies of a day that has a
     * newer one, and every day beyond the newest [keep]. Other files in the folder are never listed.
     */
    fun outdated(files: List<BackupFile>, keep: Int = KEEP): Set<String> {
        val byDay = files.mapNotNull { file -> dayOf(file.name)?.let { it to file } }.groupBy({ it.first }, { it.second })
        val newest = byDay.mapValues { (_, copies) -> copies.maxWith(compareBy<BackupFile> { it.modified }.thenBy { it.name }) }
        val keptDays = newest.keys.sortedDescending().take(keep).toSet()
        return byDay.flatMap { (day, copies) -> copies.filter { day !in keptDays || it != newest[day] } }.map { it.name }.toSet()
    }

    // "fitwren-otomatik-yedek-2026-11-13.json", or "…-2026-11-13 (1).json" when the folder already had that name.
    private fun dayOf(name: String): String? {
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null
        return name.removePrefix(FILE_PREFIX).take(10).takeIf { DATE.matches(it) }
    }

    private fun write(context: Context, tree: Uri, name: String): Int {
        val resolver = context.contentResolver
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(resolver, folder, MIME, name) ?: throw IOException("The folder gave no file")
        val rows = try {
            (resolver.openOutputStream(file) ?: throw IOException("The file cannot be written")).use { Backup.write(context, it) }
        } catch (e: Exception) {
            // Half a backup is worse than none: it could be taken for a good one.
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }
        // Tidying up comes after the new file is safe; a file that cannot be deleted only stays behind.
        runCatching {
            val children = children(context, tree)
            val gone = outdated(children.map { BackupFile(it.second, it.third) })
            for ((id, childName, _) in children) {
                if (childName in gone) runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
            }
        }
        return rows
    }

    /** The files in the folder: document id, name and when they were last written. */
    private fun children(context: Context, tree: Uri): List<Triple<String, String, Long>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = ArrayList<Triple<String, String, Long>>()
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                result += Triple(id, name, if (cursor.isNull(2)) 0L else cursor.getLong(2))
            }
        }
        return result
    }

    private fun folderName(context: Context, tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
        val named = runCatching {
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            context.contentResolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        // "primary:Documents/FitWren" when the folder does not tell its name.
        return named?.takeIf { it.isNotBlank() } ?: id.substringAfterLast(':').substringAfterLast('/').ifEmpty { id }
    }

    private fun read(context: Context): AutoBackupState {
        val prefs = prefs(context)
        val on = prefs.getString(KEY_FOLDER, null) != null
        return AutoBackupState(
            folder = if (on) prefs.getString(KEY_NAME, null).orEmpty() else null,
            lastMs = prefs.getLong(KEY_LAST, 0L),
            failed = prefs.getBoolean(KEY_FAILED, false),
        )
    }

    private fun flow(context: Context): MutableStateFlow<AutoBackupState> = shown ?: synchronized(this) {
        shown ?: MutableStateFlow(read(context.applicationContext)).also { shown = it }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
