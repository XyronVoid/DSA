package com.spacebachao.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Bin = safe locker for originals (in the app's private storage).
 * After compression the original stays here so the user can restore it.
 */
class BinManager(private val context: Context) {
    private val dir = File(context.filesDir, "bin").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    @Synchronized
    fun list(): List<BinEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
                .filter { File(it.binPath).exists() }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun totalSize(): Long = list().sumOf { it.originalSize }

    /** Copies the original into the Bin. It is listed only after add() is called. */
    fun backup(file: MediaFile, readUri: Uri): BinEntry {
        val id = UUID.randomUUID().toString()
        val dest = File(dir, id)
        FileOps.copyUriToFile(context.contentResolver, readUri, dest)
        return BinEntry(
            id = id,
            key = file.key,
            name = file.name,
            kind = file.kind,
            mime = file.mime,
            uri = file.uri.toString(),
            binPath = dest.absolutePath,
            originalSize = dest.length(),
            compressedSize = 0,
            createdAt = System.currentTimeMillis(),
            dateTakenMs = file.dateTakenMs,
            dateModifiedMs = file.dateModifiedMs,
            path = file.path,
        )
    }

    @Synchronized
    fun add(entry: BinEntry) {
        val all = list().toMutableList()
        all.add(entry)
        save(all)
    }

    /** Removes an entry and deletes its file (frees space). */
    @Synchronized
    fun delete(id: String) {
        val all = list().toMutableList()
        val e = all.firstOrNull { it.id == id }
        e?.let { File(it.binPath).delete() }
        all.removeAll { it.id == id }
        save(all)
    }

    /** Deletes a backup that was never added to the index. */
    fun discardBackup(entry: BinEntry) {
        File(entry.binPath).delete()
    }

    @Synchronized
    fun clear(): Long {
        val all = list()
        val freed = all.sumOf { it.originalSize }
        all.forEach { File(it.binPath).delete() }
        save(emptyList())
        // clean up leftovers too
        dir.listFiles()?.forEach { if (it.name != indexFile.name) it.delete() }
        return freed
    }

    /** Deletes expired entries. */
    @Synchronized
    fun purgeExpired(days: Int): Long {
        val all = list()
        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(0) * 24L * 60 * 60 * 1000
        val (expired, keep) = all.partition { it.createdAt < cutoff }
        expired.forEach { File(it.binPath).delete() }
        if (expired.isNotEmpty()) save(keep)
        return expired.sumOf { it.originalSize }
    }

    /** Puts the original back. If the file was deleted from the gallery, a new file is created. */
    fun restore(entry: BinEntry): Uri {
        val src = File(entry.binPath)
        val resolver = context.contentResolver
        val uri = Uri.parse(entry.uri)
        val target = if (FileOps.exists(resolver, uri)) {
            FileOps.overwrite(resolver, uri, src)
            if (entry.kind != Kind.PDF) {
                FileOps.restoreDates(context, uri, entry.path, entry.dateModifiedMs, entry.dateTakenMs)
            }
            uri
        } else {
            val newUri = FileOps.insertNew(resolver, entry.kind, entry.name, entry.mime, src, entry.dateTakenMs, "Restored")
            if (entry.kind != Kind.PDF) {
                FileOps.restoreDates(context, newUri, null, entry.dateModifiedMs, entry.dateTakenMs)
            }
            newUri
        }
        delete(entry.id)
        return target
    }

    private fun save(entries: List<BinEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(toJson(it)) }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun toJson(e: BinEntry) = JSONObject().apply {
        put("id", e.id)
        put("key", e.key)
        put("name", e.name)
        put("kind", e.kind.name)
        put("mime", e.mime)
        put("uri", e.uri)
        put("binPath", e.binPath)
        put("originalSize", e.originalSize)
        put("compressedSize", e.compressedSize)
        put("createdAt", e.createdAt)
        put("dateTakenMs", e.dateTakenMs)
        put("dateModifiedMs", e.dateModifiedMs)
        e.path?.let { put("path", it) }
    }

    private fun fromJson(o: JSONObject): BinEntry? = try {
        BinEntry(
            id = o.getString("id"),
            key = o.optString("key"),
            name = o.optString("name", "file"),
            kind = Kind.valueOf(o.getString("kind")),
            mime = o.optString("mime"),
            uri = o.getString("uri"),
            binPath = o.getString("binPath"),
            originalSize = o.optLong("originalSize"),
            compressedSize = o.optLong("compressedSize"),
            createdAt = o.optLong("createdAt"),
            dateTakenMs = o.optLong("dateTakenMs"),
            dateModifiedMs = o.optLong("dateModifiedMs"),
            path = if (o.has("path")) o.optString("path") else null,
        )
    } catch (e: Exception) {
        null
    }
}
