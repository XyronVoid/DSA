package com.spacebachao.app

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spacebachao.app.data.BinEntry
import com.spacebachao.app.data.BinManager
import com.spacebachao.app.data.FileOps
import com.spacebachao.app.data.ItemStatus
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.MediaScanner
import com.spacebachao.app.data.Mode
import com.spacebachao.app.data.Prefs
import com.spacebachao.app.data.Quality
import com.spacebachao.app.data.ResultItem
import com.spacebachao.app.data.SkipException
import com.spacebachao.app.engine.ImageCompressor
import com.spacebachao.app.engine.PdfCompressor
import com.spacebachao.app.engine.VideoCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class JobState(
    val running: Boolean = true,
    val total: Int = 0,
    val index: Int = 0,
    val currentName: String = "",
    /** 0..1, ya -1 jab progress pata nahi */
    val currentProgress: Float = -1f,
    val savedSoFar: Long = 0,
    val results: List<ResultItem> = emptyList(),
    val cancelled: Boolean = false,
    val mode: Mode = Mode.REPLACE,
    val binUsed: Boolean = false,
)

data class UiState(
    val hasPermission: Boolean = false,
    val hasLocation: Boolean = false,
    val loading: Boolean = false,
    val images: List<MediaFile> = emptyList(),
    val videos: List<MediaFile> = emptyList(),
    val pdfs: List<MediaFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val tab: Kind = Kind.IMAGE,
    val mode: Mode = Mode.REPLACE,
    val quality: Quality = Quality.BALANCED,
    val binDays: Int = 7,
    val binEntries: List<BinEntry> = emptyList(),
    val storageFree: Long = 0,
    val storageTotal: Long = 0,
    val hiddenCount: Int = 0,
    val job: JobState? = null,
    val writeRequest: List<Uri>? = null,
    val message: String? = null,
) {
    val selectedFiles: List<MediaFile>
        get() = (images + videos + pdfs).filter { it.key in selected }
    val selectedSize: Long
        get() = selectedFiles.sumOf { it.size }
    val binSize: Long
        get() = binEntries.sumOf { it.originalSize }

    fun listFor(kind: Kind): List<MediaFile> = when (kind) {
        Kind.IMAGE -> images
        Kind.VIDEO -> videos
        Kind.PDF -> pdfs
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private sealed interface PendingAction {
        data class Compress(val items: List<MediaFile>) : PendingAction
        data class Restore(val entry: BinEntry) : PendingAction
    }

    private val ctx: Context get() = getApplication<Application>()
    private val prefs = Prefs(app)
    private val bin = BinManager(app)

    private val _state = MutableStateFlow(
        UiState(mode = prefs.mode, quality = prefs.quality, binDays = prefs.binDays)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pendingAction: PendingAction? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ctx.cacheDir.listFiles()?.forEach { if (it.name.startsWith("work_")) it.delete() }
            val days = prefs.binDays
            bin.purgeExpired(if (days == 0) 7 else days)
            refreshBinAndStorage()
        }
        checkPermissionAndLoad()
    }

    // ---------- Permission + loading ----------

    fun onResume() {
        if (_state.value.job?.running == true) return
        checkPermissionAndLoad()
        viewModelScope.launch(Dispatchers.IO) { refreshBinAndStorage() }
    }

    fun onPermissionsResult() = checkPermissionAndLoad()

    private fun checkPermissionAndLoad() {
        val had = _state.value.hasPermission
        val has = Perms.hasMediaAccess(ctx)
        _state.update { it.copy(hasPermission = has, hasLocation = Perms.hasLocation(ctx)) }
        if (has && !had) refresh()
    }

    fun refresh() {
        if (_state.value.loading || !_state.value.hasPermission) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val processed = prefs.processed()
            val allImages = runCatching { MediaScanner.scan(ctx, Kind.IMAGE) }.getOrDefault(emptyList())
            val allVideos = runCatching { MediaScanner.scan(ctx, Kind.VIDEO) }.getOrDefault(emptyList())
            val images = allImages.filter { it.key !in processed }
            val videos = allVideos.filter { it.key !in processed }
            val hidden = (allImages.size - images.size) + (allVideos.size - videos.size)
            _state.update { s ->
                val valid = (images + videos + s.pdfs).map { it.key }.toHashSet()
                s.copy(
                    loading = false,
                    images = images,
                    videos = videos,
                    hiddenCount = hidden,
                    selected = s.selected.filter { it in valid }.toSet(),
                )
            }
            refreshBinAndStorage()
        }
    }

    private fun refreshBinAndStorage() {
        val stat = StatFs(Environment.getDataDirectory().path)
        val entries = bin.list()
        _state.update {
            it.copy(binEntries = entries, storageFree = stat.availableBytes, storageTotal = stat.totalBytes)
        }
    }

    // ---------- Selection + settings ----------

    fun setTab(kind: Kind) = _state.update { it.copy(tab = kind) }

    fun toggle(file: MediaFile) = _state.update { s ->
        s.copy(selected = if (file.key in s.selected) s.selected - file.key else s.selected + file.key)
    }

    fun selectAllInTab() = _state.update { s -> s.copy(selected = s.selected + s.listFor(s.tab).map { it.key }) }

    fun clearTab() = _state.update { s ->
        val keys = s.listFor(s.tab).map { it.key }.toSet()
        s.copy(selected = s.selected - keys)
    }

    fun setMode(mode: Mode) {
        prefs.mode = mode
        _state.update { it.copy(mode = mode) }
    }

    fun setQuality(quality: Quality) {
        prefs.quality = quality
        _state.update { it.copy(quality = quality) }
    }

    fun setBinDays(days: Int) {
        prefs.binDays = days
        _state.update { it.copy(binDays = days) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun toast(msg: String) = _state.update { it.copy(message = msg) }

    // ---------- PDF ----------

    fun addPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val processed = prefs.processed()
            var already = 0
            val added = ArrayList<MediaFile>()
            for (uri in uris) {
                if (uri.toString() in processed) {
                    already++
                    continue
                }
                takePersistable(uri)
                MediaScanner.describePdf(ctx, uri)?.let { added += it }
            }
            _state.update { s ->
                val existing = s.pdfs.map { it.key }.toHashSet()
                val fresh = added.filter { it.key !in existing }
                s.copy(pdfs = s.pdfs + fresh, selected = s.selected + fresh.map { it.key }, tab = Kind.PDF)
            }
            if (already > 0) toast("$already PDF pehle hi compress ho chuke hain")
        }
    }

    private fun takePersistable(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, rw)
        } catch (e: Exception) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun removePdf(file: MediaFile) = _state.update { s ->
        s.copy(pdfs = s.pdfs.filter { it.key != file.key }, selected = s.selected - file.key)
    }

    // ---------- Compress ----------

    fun startCompress() {
        val s = _state.value
        if (s.job != null) return
        var items = s.selectedFiles
        if (items.isEmpty()) return
        if (items.size > MAX_BATCH) {
            items = items.take(MAX_BATCH)
            toast("Ek baar me $MAX_BATCH files tak. Baaki agli baar.")
        }
        val mediaUris = items.filter { it.kind != Kind.PDF }.map { it.uri }
        if (s.mode == Mode.REPLACE && Build.VERSION.SDK_INT >= 30 && mediaUris.isNotEmpty()) {
            // Android poochega: "Space Bachao ko ye files badalne do?" (ek hi popup)
            pendingAction = PendingAction.Compress(items)
            _state.update { it.copy(writeRequest = mediaUris) }
        } else {
            runCompress(items)
        }
    }

    /** Popup dikh gaya, ab dobara mat dikhao (rotation pe bhi). */
    fun onWriteRequestLaunched() = _state.update { it.copy(writeRequest = null) }

    fun onWriteRequestResult(granted: Boolean) {
        _state.update { it.copy(writeRequest = null) }
        val action = pendingAction
        pendingAction = null
        if (action == null) return
        if (!granted) {
            toast("Permission nahi mili, kuch nahi badla")
            return
        }
        when (action) {
            is PendingAction.Compress -> runCompress(action.items)
            is PendingAction.Restore -> doRestore(action.entry)
        }
    }

    private fun runCompress(items: List<MediaFile>) {
        val s = _state.value
        val mode = s.mode
        val quality = s.quality
        val useBin = mode == Mode.REPLACE && s.binDays > 0
        val hasLocation = s.hasLocation

        _state.update { it.copy(job = JobState(total = items.size, mode = mode, binUsed = useBin)) }

        val batchKeys = items.map { it.key }.toSet()
        job = viewModelScope.launch(Dispatchers.IO) {
            val results = ArrayList<ResultItem>()
            var saved = 0L
            try {
                for ((i, item) in items.withIndex()) {
                    ensureActive()
                    _state.update { st ->
                        st.copy(job = st.job?.copy(index = i, currentName = item.name, currentProgress = -1f))
                    }
                    val r = try {
                        processOne(item, mode, quality, useBin, hasLocation) { p ->
                            _state.update { st -> st.copy(job = st.job?.copy(currentProgress = p.coerceIn(0f, 1f))) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SkipException) {
                        ResultItem(item.name, item.kind, item.size, item.size, ItemStatus.SKIPPED, e.message ?: "")
                    } catch (e: Throwable) {
                        ResultItem(
                            item.name, item.kind, item.size, item.size, ItemStatus.FAILED,
                            e.message ?: e.javaClass.simpleName,
                        )
                    }
                    results += r
                    if (r.status == ItemStatus.SAVED) saved += (r.before - r.after)
                    val snapshot = results.toList()
                    val savedNow = saved
                    _state.update { st -> st.copy(job = st.job?.copy(savedSoFar = savedNow, results = snapshot)) }
                }
            } finally {
                val cancelled = !isActive
                val snapshot = results.toList()
                val savedNow = saved
                _state.update { st ->
                    st.copy(
                        job = st.job?.copy(
                            running = false,
                            cancelled = cancelled,
                            savedSoFar = savedNow,
                            results = snapshot,
                        ),
                        selected = emptySet(),
                        pdfs = st.pdfs.filter { it.key !in batchKeys },
                    )
                }
                refresh()
            }
        }
    }

    private suspend fun processOne(
        item: MediaFile,
        mode: Mode,
        quality: Quality,
        useBin: Boolean,
        hasLocation: Boolean,
        onProgress: (Float) -> Unit,
    ): ResultItem {
        val resolver = ctx.contentResolver
        // Location ke saath original padhna (warna Android GPS info chhupa deta hai)
        val readUri = if (item.kind != Kind.PDF && hasLocation) MediaStore.setRequireOriginal(item.uri) else item.uri
        val ext = when (item.kind) {
            Kind.IMAGE -> "jpg"
            Kind.VIDEO -> "mp4"
            Kind.PDF -> "pdf"
        }
        val tmp = File(ctx.cacheDir, "work_${System.nanoTime()}.$ext")
        try {
            val need = item.size * (if (useBin) 2 else 1) + 50L * 1024 * 1024
            if (ctx.filesDir.usableSpace < need) {
                throw SkipException("Phone me jagah kam hai (${need.readableSize()} chahiye)", permanent = false)
            }

            when (item.kind) {
                Kind.IMAGE -> ImageCompressor.compress(ctx, item, readUri, quality, tmp)
                Kind.VIDEO -> VideoCompressor.compress(ctx, item, readUri, quality, tmp, onProgress)
                Kind.PDF -> PdfCompressor.compress(ctx, item, quality, tmp, onProgress)
            }

            val newSize = tmp.length()
            val maxRatio = when (item.kind) {
                Kind.IMAGE -> 0.85
                Kind.VIDEO -> 0.90
                Kind.PDF -> 0.75
            }
            if (newSize <= 0L || newSize > item.size * maxRatio) {
                throw SkipException("Isme zyada bachat nahi thi, original waisa hi rakha")
            }

            withContext(NonCancellable) {
                when (mode) {
                    Mode.REPLACE -> {
                        replaceOriginal(item, readUri, tmp, useBin)
                        prefs.markProcessed(listOf(item.key))
                    }
                    Mode.KEEP_BOTH -> {
                        val mime = when (item.kind) {
                            Kind.IMAGE -> "image/jpeg"
                            Kind.VIDEO -> "video/mp4"
                            Kind.PDF -> "application/pdf"
                        }
                        val newUri = FileOps.insertNew(
                            resolver, item.kind, FileOps.compressedName(item.name, ext), mime, tmp, item.dateTakenMs
                        )
                        val keys = mutableListOf(item.key)
                        if (item.kind != Kind.PDF) {
                            keys += MediaFile.keyFor(item.kind, ContentUris.parseId(newUri), newUri)
                        }
                        prefs.markProcessed(keys)
                    }
                }
            }
            return ResultItem(item.name, item.kind, item.size, newSize, ItemStatus.SAVED)
        } catch (e: SkipException) {
            if (e.permanent) prefs.markProcessed(listOf(item.key))
            throw e
        } finally {
            tmp.delete()
        }
    }

    /**
     * Safe replace:
     * 1. Original ki copy Bin me
     * 2. Compressed data original file me
     * 3. Check: poora likha gaya? Nahi toh original wapas
     */
    private fun replaceOriginal(item: MediaFile, readUri: Uri, tmp: File, useBin: Boolean) {
        val resolver = ctx.contentResolver
        val backup = if (useBin) bin.backup(item, readUri) else null
        try {
            FileOps.overwrite(resolver, item.uri, tmp)
            val written = FileOps.sizeOf(resolver, item.uri)
            if (written >= 0 && written != tmp.length()) throw IOException("File poori nahi likhi gayi")
        } catch (e: Exception) {
            if (backup != null) {
                runCatching { FileOps.overwrite(resolver, item.uri, File(backup.binPath)) }
                bin.discardBackup(backup)
            }
            throw e
        }
        backup?.let { bin.add(it.copy(compressedSize = tmp.length())) }
    }

    fun cancelJob() {
        job?.cancel()
    }

    fun dismissJob() {
        if (_state.value.job?.running == true) return
        _state.update { it.copy(job = null) }
    }

    // ---------- Bin ----------

    fun restore(entry: BinEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(entry.uri)
            val needsPermission = entry.kind != Kind.PDF &&
                Build.VERSION.SDK_INT >= 30 &&
                FileOps.exists(ctx.contentResolver, uri)
            if (needsPermission) {
                pendingAction = PendingAction.Restore(entry)
                _state.update { it.copy(writeRequest = listOf(uri)) }
            } else {
                doRestore(entry)
            }
        }
    }

    private fun doRestore(entry: BinEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bin.restore(entry)
                prefs.unmarkProcessed(entry.key)
                toast("${entry.name} wapas original ho gayi")
            } catch (e: Exception) {
                toast("Restore nahi ho paaya: ${e.message ?: "error"}")
            }
            refreshBinAndStorage()
            refresh()
        }
    }

    fun deleteFromBin(entry: BinEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            bin.delete(entry.id)
            refreshBinAndStorage()
            toast("${entry.originalSize.readableSize()} free ho gaya")
        }
    }

    fun emptyBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = bin.clear()
            refreshBinAndStorage()
            toast("${freed.readableSize()} free ho gaya 🎉")
        }
    }

    companion object {
        const val MAX_BATCH = 500
    }
}
