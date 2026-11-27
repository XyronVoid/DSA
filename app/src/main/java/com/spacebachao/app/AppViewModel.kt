package com.spacebachao.app

import android.app.Activity
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
import com.spacebachao.app.billing.ProBilling
import com.spacebachao.app.billing.ProPlan
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

/** Free plan: files up to this total (original size, all types together) can be compressed. */
const val FREE_LIMIT_BYTES = 5L * 1024 * 1024 * 1024
const val FREE_BIN_DAYS = 7

data class JobState(
    val running: Boolean = true,
    val total: Int = 0,
    val index: Int = 0,
    val currentName: String = "",
    val currentKind: Kind = Kind.IMAGE,
    /** 0..1, or -1 when unknown */
    val currentProgress: Float = -1f,
    val savedSoFar: Long = 0,
    val results: List<ResultItem> = emptyList(),
    val cancelled: Boolean = false,
    val mode: Mode = Mode.REPLACE,
    val binUsed: Boolean = false,
    val totals: Map<Kind, Int> = emptyMap(),
    /** Stopped because the free 5 GB ran out. */
    val limitReached: Boolean = false,
) {
    val doneCount: Int get() = results.size
    val leftCount: Int get() = (total - results.size).coerceAtLeast(0)
    fun doneOf(kind: Kind): Int = results.count { it.kind == kind }
}

/** reason = null when the user opened the Pro screen themselves. */
data class Paywall(val reason: String?, val freeItems: List<MediaFile> = emptyList())

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
    val binDays: Int = FREE_BIN_DAYS,
    val binEntries: List<BinEntry> = emptyList(),
    val storageFree: Long = 0,
    val storageTotal: Long = 0,
    /** Space taken by all photos / all videos (for the "how full" bar). */
    val photosBytes: Long = 0,
    val videosBytes: Long = 0,
    /** Files compressed earlier, per type (shown as "done"). */
    val doneCount: Map<Kind, Int> = emptyMap(),
    val totalSaved: Long = 0,
    val isPro: Boolean = false,
    /** Test build: Pro is unlocked for the developer. */
    val devBuild: Boolean = false,
    val plans: List<ProPlan> = emptyList(),
    /** Original size of files compressed on the free plan so far. */
    val freeUsed: Long = 0,
    val paywall: Paywall? = null,
    val job: JobState? = null,
    val writeRequest: List<Uri>? = null,
    /** Originals to delete after they were replaced by HEIC files (one system popup). */
    val deleteRequest: List<Uri>? = null,
    /** Save photos as HEIC (same quality, smaller). */
    val heic: Boolean = true,
    val heicSupported: Boolean = true,
    val message: String? = null,
) {
    val selectedFiles: List<MediaFile>
        get() = (images + videos + pdfs).filter { it.key in selected }
    val selectedSize: Long
        get() = selectedFiles.sumOf { it.size }
    val binSize: Long
        get() = binEntries.sumOf { it.originalSize }

    /** Settings the user can actually use on their plan. */
    val effectiveQuality: Quality
        get() = if (!isPro && quality.proOnly) Quality.BALANCED else quality
    val effectiveBinDays: Int
        get() = if (!isPro && binDays > FREE_BIN_DAYS) FREE_BIN_DAYS else binDays
    val freeLeft: Long
        get() = (FREE_LIMIT_BYTES - freeUsed).coerceAtLeast(0)

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
        UiState(
            mode = prefs.mode,
            quality = prefs.quality,
            binDays = prefs.binDays,
            isPro = BuildConfig.DEV_PRO || prefs.proCached,
            devBuild = BuildConfig.DEV_PRO,
            totalSaved = prefs.totalSaved,
            freeUsed = prefs.freeUsed,
            heic = prefs.heic,
            heicSupported = ImageCompressor.heicSupported,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pendingAction: PendingAction? = null

    /** A photo that now exists as a new HEIC file; its original is deleted after the user confirms. */
    private data class PendingReplace(
        val originalUri: Uri,
        val newUri: Uri,
        val key: String,
        val newKey: String,
        val binId: String?,
        val gained: Long,
        val originalSize: Long,
        val countedFree: Boolean,
    )

    private val pendingReplaces = mutableListOf<PendingReplace>()

    private val billing = ProBilling(app) { isPro, plans ->
        if (isPro != null) prefs.proCached = isPro
        _state.update {
            it.copy(
                isPro = BuildConfig.DEV_PRO || (isPro ?: it.isPro),
                plans = plans ?: it.plans,
            )
        }
        if (isPro == true && _state.value.paywall != null) {
            _state.update { it.copy(paywall = null, message = "Welcome to Pro! Everything is unlocked.") }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ctx.cacheDir.listFiles()?.forEach { if (it.name.startsWith("work_")) it.delete() }
            bin.purgeExpired(_state.value.effectiveBinDays.takeIf { it > 0 } ?: FREE_BIN_DAYS)
            refreshBinAndStorage()
        }
        billing.connect()
        checkPermissionAndLoad()
    }

    // ---------- Permission + loading ----------

    fun onResume() {
        billing.connect()
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
            val saved = prefs.saved()
            val allImages = runCatching { MediaScanner.scan(ctx, Kind.IMAGE) }.getOrDefault(emptyList())
            val allVideos = runCatching { MediaScanner.scan(ctx, Kind.VIDEO) }.getOrDefault(emptyList())
            val images = allImages.filter { it.key !in processed }
            val videos = allVideos.filter { it.key !in processed }
            val photosBytes = runCatching { MediaScanner.totalBytes(ctx, Kind.IMAGE) }.getOrDefault(0L)
            val videosBytes = runCatching { MediaScanner.totalBytes(ctx, Kind.VIDEO) }.getOrDefault(0L)
            val done = mapOf(
                Kind.IMAGE to allImages.count { it.key in saved },
                Kind.VIDEO to allVideos.count { it.key in saved },
                Kind.PDF to saved.count { it.startsWith("content://") },
            )
            _state.update { s ->
                val valid = (images + videos + s.pdfs).map { it.key }.toHashSet()
                s.copy(
                    loading = false,
                    images = images,
                    videos = videos,
                    doneCount = done,
                    photosBytes = photosBytes,
                    videosBytes = videosBytes,
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

    /** Drag-to-select sets the whole selection at once. */
    fun setSelected(keys: Set<String>) = _state.update { it.copy(selected = keys) }

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
        if (quality.proOnly && !_state.value.isPro) {
            openPro()
            return
        }
        prefs.quality = quality
        _state.update { it.copy(quality = quality) }
    }

    fun setBinDays(days: Int) {
        if (days > FREE_BIN_DAYS && !_state.value.isPro) {
            openPro()
            return
        }
        prefs.binDays = days
        _state.update { it.copy(binDays = days) }
    }

    fun setHeic(on: Boolean) {
        prefs.heic = on
        _state.update { it.copy(heic = on) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun toast(msg: String) = _state.update { it.copy(message = msg) }

    // ---------- Pro ----------

    fun openPro() = _state.update { it.copy(paywall = Paywall(reason = null)) }

    fun dismissPaywall() = _state.update { it.copy(paywall = null) }

    fun subscribe(activity: Activity, plan: ProPlan?) {
        if (_state.value.isPro) return
        if (plan == null || !billing.buy(activity, plan)) {
            billing.connect()
            toast("Subscriptions work when the app is installed from Google Play.")
        }
    }

    fun restorePurchase() {
        billing.connect()
        billing.refresh()
        toast("Checking your purchases…")
    }

    /** From the paywall: compress only the files that still fit in the free 5 GB. */
    fun continueFree() {
        val items = _state.value.paywall?.freeItems.orEmpty()
        _state.update { it.copy(paywall = null) }
        if (items.isNotEmpty()) launchCompress(items)
    }

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
            if (already > 0) toast("$already PDF(s) were already compressed")
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
            toast("Up to $MAX_BATCH files at a time. The rest next time.")
        }

        if (!s.isPro) {
            val left = s.freeLeft
            val total = items.sumOf { it.size }
            if (total > left) {
                // Keep the files that still fit in the free 5 GB
                var budget = left
                val fit = ArrayList<MediaFile>()
                for (f in items) {
                    if (f.size <= budget) {
                        fit += f
                        budget -= f.size
                    }
                }
                val reason = if (left == 0L) {
                    "You've used your free 5 GB. Subscribe to Pro to compress unlimited files."
                } else {
                    "The free plan compresses up to 5 GB of files. You have ${left.readableSize()} left " +
                        "and selected ${total.readableSize()}."
                }
                _state.update { it.copy(paywall = Paywall(reason, fit)) }
                return
            }
        }
        launchCompress(items)
    }

    private fun launchCompress(items: List<MediaFile>) {
        val s = _state.value
        val heicOn = s.heic && s.heicSupported
        // Photos that become new HEIC files don't modify the original, so they don't need this popup
        val mediaUris = items
            .filter { it.kind != Kind.PDF && !(it.kind == Kind.IMAGE && heicOn && FileOps.heicAllowedIn(it.relativePath)) }
            .map { it.uri }
        if (s.mode == Mode.REPLACE && Build.VERSION.SDK_INT >= 30 && mediaUris.isNotEmpty()) {
            // Android shows one system popup: "Allow Space Bachao to modify these files?"
            pendingAction = PendingAction.Compress(items)
            _state.update { it.copy(writeRequest = mediaUris) }
        } else {
            runCompress(items)
        }
    }

    /** The popup is on screen; don't show it again (e.g. on rotation). */
    fun onWriteRequestLaunched() = _state.update { it.copy(writeRequest = null) }

    fun onWriteRequestResult(granted: Boolean) {
        _state.update { it.copy(writeRequest = null) }
        val action = pendingAction
        pendingAction = null
        if (action == null) return
        if (!granted) {
            toast("Permission not given. Nothing was changed.")
            return
        }
        when (action) {
            is PendingAction.Compress -> runCompress(action.items)
            is PendingAction.Restore -> doRestore(action.entry)
        }
    }

    fun onDeleteRequestLaunched() = _state.update { it.copy(deleteRequest = null) }

    /** User answered "Allow Space Bachao to delete these originals?" */
    fun onDeleteRequestResult(granted: Boolean) {
        _state.update { it.copy(deleteRequest = null) }
        val list = synchronized(pendingReplaces) { pendingReplaces.toList().also { pendingReplaces.clear() } }
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val undo = if (granted) list.filter { FileOps.exists(ctx.contentResolver, it.originalUri) } else list
            undo.forEach { undoReplace(it) }
            if (undo.isNotEmpty()) {
                val undoneKeys = undo.map { it.key }.toSet()
                val lost = undo.sumOf { it.gained }
                _state.update { st ->
                    st.copy(
                        job = st.job?.let { j ->
                            j.copy(
                                savedSoFar = (j.savedSoFar - lost).coerceAtLeast(0),
                                results = j.results.map { r ->
                                    if (r.key in undoneKeys) {
                                        r.copy(status = ItemStatus.SKIPPED, after = r.before, note = "Original kept (delete not allowed)")
                                    } else {
                                        r
                                    }
                                },
                            )
                        },
                        totalSaved = prefs.totalSaved,
                        freeUsed = prefs.freeUsed,
                    )
                }
            }
            toast(
                if (undo.isEmpty()) "Done. ${list.size} photos are now HEIC."
                else "Originals kept for ${undo.size} photos. Nothing changed for them."
            )
            refreshBinAndStorage()
            refresh()
        }
    }

    /** Puts things back as they were before a HEIC replace (removes the new file and its Bin copy). */
    private fun undoReplace(pr: PendingReplace) {
        runCatching { ctx.contentResolver.delete(pr.newUri, null, null) }
        pr.binId?.let { bin.delete(it) }
        prefs.unmark(pr.key)
        prefs.unmark(pr.newKey)
        prefs.totalSaved = (prefs.totalSaved - pr.gained).coerceAtLeast(0)
        if (pr.countedFree) prefs.freeUsed = (prefs.freeUsed - pr.originalSize).coerceAtLeast(0)
    }

    private fun runCompress(items: List<MediaFile>) {
        val s = _state.value
        val mode = s.mode
        val quality = s.effectiveQuality
        val useBin = mode == Mode.REPLACE && s.effectiveBinDays > 0
        val hasLocation = s.hasLocation
        val isPro = s.isPro
        val useHeic = s.heic && s.heicSupported
        val totals = items.groupingBy { it.kind }.eachCount()

        _state.update {
            it.copy(job = JobState(total = items.size, mode = mode, binUsed = useBin, totals = totals))
        }

        val batchKeys = items.map { it.key }.toSet()
        job = viewModelScope.launch(Dispatchers.IO) {
            val results = ArrayList<ResultItem>()
            var saved = 0L
            var limitHit = false
            try {
                for ((i, item) in items.withIndex()) {
                    ensureActive()
                    if (!isPro && prefs.freeUsed + item.size > FREE_LIMIT_BYTES) {
                        limitHit = true
                        break
                    }
                    _state.update { st ->
                        st.copy(
                            job = st.job?.copy(
                                index = i,
                                currentName = item.name,
                                currentKind = item.kind,
                                currentProgress = -1f,
                            )
                        )
                    }
                    val r = try {
                        processOne(item, mode, quality, useBin, hasLocation, useHeic, isPro) { p ->
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
                    if (r.status == ItemStatus.SAVED) {
                        val gained = r.before - r.after
                        saved += gained
                        prefs.totalSaved = prefs.totalSaved + gained
                        if (!isPro) prefs.freeUsed = prefs.freeUsed + item.size
                    }
                    val snapshot = results.toList()
                    val savedNow = saved
                    val lifetime = prefs.totalSaved
                    val used = prefs.freeUsed
                    _state.update { st ->
                        st.copy(
                            job = st.job?.copy(savedSoFar = savedNow, results = snapshot),
                            totalSaved = lifetime,
                            freeUsed = used,
                        )
                    }
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
                            limitReached = limitHit,
                        ),
                        selected = emptySet(),
                        pdfs = st.pdfs.filter { it.key !in batchKeys },
                    )
                }
                val toDelete = synchronized(pendingReplaces) { pendingReplaces.map { it.originalUri } }
                if (toDelete.isNotEmpty()) {
                    // One system popup: "Allow Space Bachao to delete these photos?"
                    _state.update { it.copy(deleteRequest = toDelete) }
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
        useHeic: Boolean,
        isPro: Boolean,
        onProgress: (Float) -> Unit,
    ): ResultItem {
        val resolver = ctx.contentResolver
        // Read the original WITH location (otherwise Android hides GPS info)
        val readUri = if (item.kind != Kind.PDF && hasLocation) MediaStore.setRequireOriginal(item.uri) else item.uri
        val ext = when (item.kind) {
            Kind.IMAGE -> "jpg"
            Kind.VIDEO -> "mp4"
            Kind.PDF -> "pdf"
        }
        val tmp = File(ctx.cacheDir, "work_${System.nanoTime()}.$ext")
        val heicTmp = File(ctx.cacheDir, "work_${System.nanoTime()}.heic")
        // HEIC: new file next to the original (camera/Pictures folders), or the copy in Keep-both mode
        val heicEligible = item.kind == Kind.IMAGE && useHeic &&
            (mode == Mode.KEEP_BOTH || FileOps.heicAllowedIn(item.relativePath))
        try {
            val need = item.size * (if (useBin) 2 else 1) + 50L * 1024 * 1024
            if (ctx.filesDir.usableSpace < need) {
                throw SkipException("Not enough free space (${need.readableSize()} needed)", permanent = false)
            }

            var madeHeic = false
            when (item.kind) {
                Kind.IMAGE -> {
                    if (heicEligible) {
                        madeHeic = try {
                            ImageCompressor.compressHeic(ctx, item, readUri, quality, heicTmp)
                        } catch (e: SkipException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (!madeHeic) {
                        if (heicEligible && mode == Mode.REPLACE) {
                            // We didn't ask to modify this original, so leave it untouched
                            throw SkipException("Couldn't convert this photo, original kept", permanent = false)
                        }
                        ImageCompressor.compress(ctx, item, readUri, quality, tmp)
                    }
                }
                Kind.VIDEO -> VideoCompressor.compress(ctx, item, readUri, quality, tmp, onProgress)
                Kind.PDF -> PdfCompressor.compress(ctx, item, quality, tmp, onProgress)
            }
            val out = if (madeHeic) heicTmp else tmp

            val newSize = out.length()
            val maxRatio = when (item.kind) {
                Kind.IMAGE -> 0.85
                Kind.VIDEO -> 0.90
                Kind.PDF -> 0.75
            }
            if (newSize <= 0L || newSize > item.size * maxRatio) {
                throw SkipException("Saving would be too small, original kept")
            }

            withContext(NonCancellable) {
                when {
                    mode == Mode.REPLACE && madeHeic -> {
                        replaceWithHeic(item, readUri, heicTmp, useBin, isPro)
                    }
                    mode == Mode.REPLACE -> {
                        replaceOriginal(item, readUri, tmp, useBin)
                        prefs.markSaved(listOf(item.key))
                    }
                    else -> {
                        val mime = when {
                            madeHeic -> "image/heic"
                            item.kind == Kind.IMAGE -> "image/jpeg"
                            item.kind == Kind.VIDEO -> "video/mp4"
                            else -> "application/pdf"
                        }
                        val newUri = FileOps.insertNew(
                            resolver, item.kind, FileOps.compressedName(item.name, if (madeHeic) "heic" else ext),
                            mime, out, item.dateTakenMs
                        )
                        if (item.kind != Kind.PDF) {
                            FileOps.restoreDates(ctx, newUri, null, item.dateModifiedMs, item.dateTakenMs)
                            prefs.markProcessed(listOf(MediaFile.keyFor(item.kind, ContentUris.parseId(newUri), newUri)))
                        }
                        prefs.markSaved(listOf(item.key))
                    }
                }
            }
            return ResultItem(item.name, item.kind, item.size, newSize, ItemStatus.SAVED, key = item.key)
        } catch (e: SkipException) {
            if (e.permanent) prefs.markProcessed(listOf(item.key))
            throw e
        } finally {
            tmp.delete()
            heicTmp.delete()
        }
    }

    /**
     * HEIC replace (the format changes, so the original can't be overwritten):
     * 1. copy of the original into the Bin
     * 2. new .heic file in the same folder, with the original dates
     * 3. original is deleted after the job, with one system confirmation (Android 11+)
     */
    private fun replaceWithHeic(item: MediaFile, readUri: Uri, heic: File, useBin: Boolean, isPro: Boolean) {
        val resolver = ctx.contentResolver
        val backup = if (useBin) bin.backup(item, readUri) else null
        val newUri = try {
            FileOps.insertNew(
                resolver, Kind.IMAGE, FileOps.withExtension(item.name, "heic"), "image/heic", heic,
                item.dateTakenMs, relativePathOverride = item.relativePath,
            )
        } catch (e: Exception) {
            backup?.let { bin.discardBackup(it) }
            throw e
        }
        FileOps.restoreDates(ctx, newUri, null, item.dateModifiedMs, item.dateTakenMs)
        val newKey = MediaFile.keyFor(Kind.IMAGE, ContentUris.parseId(newUri), newUri)
        backup?.let {
            bin.add(
                it.copy(
                    compressedSize = heic.length(),
                    relativePath = item.relativePath,
                    replacementUri = newUri.toString(),
                )
            )
        }
        prefs.markSaved(listOf(item.key))
        prefs.markProcessed(listOf(newKey))

        if (Build.VERSION.SDK_INT < 30) {
            // Android 10: the original can be deleted directly
            val deleted = runCatching { resolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
            if (!deleted) {
                runCatching { resolver.delete(newUri, null, null) }
                backup?.let { bin.delete(it.id) }
                prefs.unmark(item.key)
                prefs.unmark(newKey)
                throw IOException("Could not remove the original")
            }
            return
        }
        synchronized(pendingReplaces) {
            pendingReplaces += PendingReplace(
                originalUri = item.uri,
                newUri = newUri,
                key = item.key,
                newKey = newKey,
                binId = backup?.id,
                gained = item.size - heic.length(),
                originalSize = item.size,
                countedFree = !isPro,
            )
        }
    }

    /**
     * Safe replace:
     * 1. copy of the original into the Bin
     * 2. compressed data written into the original file
     * 3. check it was fully written, otherwise put the original back
     * 4. put the original dates back
     */
    private fun replaceOriginal(item: MediaFile, readUri: Uri, tmp: File, useBin: Boolean) {
        val resolver = ctx.contentResolver
        val backup = if (useBin) bin.backup(item, readUri) else null
        try {
            FileOps.overwrite(resolver, item.uri, tmp)
            val written = FileOps.sizeOf(resolver, item.uri)
            if (written >= 0 && written != tmp.length()) throw IOException("File was not fully written")
        } catch (e: Exception) {
            if (backup != null) {
                runCatching { FileOps.overwrite(resolver, item.uri, File(backup.binPath)) }
                bin.discardBackup(backup)
            }
            throw e
        }
        backup?.let { bin.add(it.copy(compressedSize = tmp.length())) }
        if (item.kind != Kind.PDF) {
            FileOps.restoreDates(ctx, item.uri, item.path, item.dateModifiedMs, item.dateTakenMs)
        }
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
                prefs.unmark(entry.key)
                val gained = (entry.originalSize - entry.compressedSize).coerceAtLeast(0)
                prefs.totalSaved = (prefs.totalSaved - gained).coerceAtLeast(0)
                _state.update { it.copy(totalSaved = prefs.totalSaved) }
                toast("${entry.name} is back to the original")
            } catch (e: Exception) {
                toast("Could not restore: ${e.message ?: "error"}")
            }
            refreshBinAndStorage()
            refresh()
        }
    }

    fun deleteFromBin(entry: BinEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            bin.delete(entry.id)
            refreshBinAndStorage()
            toast("${entry.originalSize.readableSize()} freed")
        }
    }

    fun emptyBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = bin.clear()
            refreshBinAndStorage()
            toast("${freed.readableSize()} freed 🎉")
        }
    }

    companion object {
        const val MAX_BATCH = 500
    }
}
