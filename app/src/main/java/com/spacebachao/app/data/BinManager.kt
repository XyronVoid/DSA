package com.spacebachao.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Bin = originals ka safe locker (app ke private storage me).
 * Compress ke baad original yahan rehta hai, taaki user restore kar sake.
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

    /** Original ki copy Bin me banata hai. Index me tab jaata hai jab add() call ho. */
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
        )
    }

    @Synchronized
    fun add(entry: BinEntry) {
        val all = list().toMutableList()
        all.add(entry)
        save(all)
    }

    /** Entry hatao aur uski file delete karo (jagah free). */
    @Synchronized
    fun delete(id: String) {
        val all = list().toMutableList()
        val e = all.firstOrNull { it.id == id }
        e?.let { File(it.binPath).delete() }
        all.removeAll { it.id == id }
        save(all)
    }

    /** Sirf temp backup file hatao (jo index me add nahi hui). */
    fun discardBackup(entry: BinEntry) {
        File(entry.binPath).delete()
    }

    @Synchronized
    fun clear(): Long {
        val all = list()
        val freed = all.sumOf { it.originalSize }
        all.forEach { File(it.binPath).delete() }
        save(emptyList())
        // bachi-khuchi files bhi saaf
        dir.listFiles()?.forEach { if (it.name != indexFile.name) it.delete() }
        return freed
    }

    /** Purani entries apne aap delete. days = 0 matlab Bin off. */
    @Synchronized
    fun purgeExpired(days: Int): Long {
        val all = list()
        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(0) * 24L * 60 * 60 * 1000
        val (expired, keep) = all.partition { it.createdAt < cutoff }
        expired.forEach { File(it.binPath).delete() }
        if (expired.isNotEmpty()) save(keep)
        return expired.sumOf { it.originalSize }
    }

    /** Original wapas. Agar gallery se file hi delete ho chuki hai toh nayi file ban jaati hai. */
    fun restore(entry: BinEntry): Uri {
        val src = File(entry.binPath)
        val resolver = context.contentResolver
        val uri = Uri.parse(entry.uri)
        val target = if (FileOps.exists(resolver, uri)) {
            FileOps.overwrite(resolver, uri, src)
            uri
        } else {
            FileOps.insertNew(resolver, entry.kind, entry.name, entry.mime, src, entry.dateTakenMs, "Restored")
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
        )
    } catch (e: Exception) {
        null
    }
}
