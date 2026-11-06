package app.embeddingspace.explorer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.embeddingspace.explorer.data.AppSettings
import app.embeddingspace.explorer.data.DetailUi
import app.embeddingspace.explorer.data.LandedUi
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.data.ThemeMode
import app.embeddingspace.explorer.data.TilesUi
import app.embeddingspace.explorer.data.ViewMode
import app.embeddingspace.explorer.domain.io.DemoItem
import app.embeddingspace.explorer.domain.queue.JobKind
import app.embeddingspace.explorer.domain.space.ClusterPager
import app.embeddingspace.explorer.domain.space.DemoPolicy
import app.embeddingspace.explorer.domain.space.SpaceConfig
import app.embeddingspace.explorer.media.Recorder
import app.embeddingspace.explorer.ui.map.LandingFx
import app.embeddingspace.explorer.ui.map.MapScene
import app.embeddingspace.explorer.ui.media.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One-off things the activity has to do: open a share sheet, ask for the microphone. */
sealed interface UiEvent {
    data class ShareText(val text: String) : UiEvent
    data class ShareFiles(val paths: List<String>) : UiEvent
    data object NeedMic : UiEvent
}

enum class Tab { MAP, SETTINGS }

enum class Route { NONE, ADD, DOWNLOAD, ABOUT, RECORD }

/** Questions the user has to confirm before something is deleted for good. */
enum class Confirm { DEMO, EVERYTHING, MODEL, DOWNLOAD }

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as ExplorerApp).container
    private val repo = c.repo
    private val config = SpaceConfig()

    val scene = MapScene()

    val settings: StateFlow<AppSettings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val modelState: StateFlow<ModelState> = c.models.state
    val jobs = c.queue.jobs

    var ready by mutableStateOf(false)
        private set
    var tab by mutableStateOf(Tab.MAP)
    var route by mutableStateOf(Route.NONE)
    var confirm by mutableStateOf<Confirm?>(null)

    /** The preview pager: every item of the tapped card's cluster, and the page being shown. */
    var pages by mutableStateOf<List<DetailUi>>(emptyList())
        private set
    var page by mutableStateOf(0)
        private set
    val detail: DetailUi? get() = pages.getOrNull(page)
    var landed by mutableStateOf<LandedUi?>(null)
        private set
    var relayoutProgress by mutableStateOf<Float?>(null)
        private set
    var userItems by mutableStateOf(0)
        private set
    var demoItems by mutableStateOf(0)
        private set
    val itemCount: Int get() = userItems + demoItems

    /** The slim hint under the search bar: shown while demo items exist, until dismissed. */
    val demoHintVisible: Boolean get() = ready && DemoPolicy.hintVisible(demoItems, settings.value.demoHintDismissed)

    var query by mutableStateOf("")

    /** What the Tiles view shows right now. */
    var tiles by mutableStateOf<TilesUi>(TilesUi.Groups(emptyList()))
        private set
    val viewMode: ViewMode get() = settings.value.viewMode
    var searchHits by mutableStateOf(-1)
        private set
    var searchNote by mutableStateOf<String?>(null)
        private set

    var recordingMs by mutableStateOf(0L)
        private set
    var recordingLevel by mutableStateOf(0f)
        private set
    private val recorder = Recorder()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    init {
        viewModelScope.launch {
            repo.load()
            refresh(animate = false)
            c.queue.restore()
            ready = true
        }
        viewModelScope.launch { c.queue.landed.collect { onLanded(it) } }
        viewModelScope.launch {
            modelState.collect { st ->
                if (st is ModelState.Ready) c.queue.kick()
            }
        }
    }

    // ---------------------------------------------------------------- map state

    private suspend fun refresh(animate: Boolean, spawn: FloatArray? = null) {
        val snap = repo.snapshot()
        scene.apply(snap, animate, spawn)
        userItems = repo.userItemCount()
        demoItems = repo.demoCount()
        if (viewMode == ViewMode.TILES) refreshTiles()
    }

    /** Opens the pager on the card at [index] of the map. */
    fun select(index: Int) {
        if (index < 0 || index >= scene.count) {
            clearSelection()
            return
        }
        scene.selected = index
        scene.glow = null
        val id = scene.item(index).id
        viewModelScope.launch {
            val list = repo.pager(id)
            pages = list
            page = 0
            scene.focus(index)
            scene.tick++
        }
    }

    /** The pager moved to another page: the map follows, highlighting that card and easing towards it. */
    fun onPage(p: Int) {
        if (p !in pages.indices || p == page) return
        page = p
        val idx = scene.snapshot.indexOf(pages[p].item.id)
        if (idx >= 0) {
            scene.selected = idx
            scene.easeTo(idx)
        }
        scene.tick++
    }

    fun clearSelection() {
        scene.selected = -1
        pages = emptyList()
        page = 0
        scene.tick++
    }

    fun jumpTo(item: app.embeddingspace.explorer.data.ItemUi) {
        val i = scene.snapshot.indexOf(item.id)
        if (i < 0) return
        select(i)
    }

    fun needThumb(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ThumbCache.loadCard(path)
            withContext(Dispatchers.Main) { scene.tick++ }
        }
    }

    fun needWave(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.embeddingspace.explorer.ui.media.WaveCache.load(path)
            withContext(Dispatchers.Main) { scene.tick++ }
        }
    }

    // ---------------------------------------------------------------- search

    fun onQuery(q: String) {
        query = q
        if (q.isBlank()) clearSearch()
    }

    /** The vector of the typed text: a known word needs no model, anything else is embedded on the device. */
    private suspend fun queryVector(q: String): FloatArray? {
        val vec: FloatArray? = repo.vocabVector(q) ?: run {
            val e = c.embedder()
            if (e == null) null else try { e.embedText(q) } catch (t: Throwable) { null }
        }
        if (vec == null) {
            searchNote = getApplication<Application>().getString(R.string.search_needs_model)
            searchHits = -1
        } else {
            searchNote = null
        }
        return vec
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            val vec = queryVector(q) ?: return@launch
            if (viewMode == ViewMode.TILES) {
                tiles = repo.tiles(vec)
                clearSelection()
                return@launch
            }
            val (res, ids) = repo.search(vec)
            val g = FloatArray(scene.count)
            val snapIds = scene.snapshot.items.map { it.id }
            for (h in res.hits) {
                val id = repo.idAt(h.index) ?: continue
                val i = snapIds.indexOf(id)
                if (i >= 0) g[i] = res.glow[h.index]
            }
            scene.glow = g
            searchHits = g.count { it > 0f }
            ids.firstOrNull()?.let { id -> scene.snapshot.indexOf(id).takeIf { it >= 0 }?.let { scene.flyToItem(it) } }
            scene.tick++
        }
    }

    /** Recomputes the grid for the current query (or everything grouped, with no query). */
    private fun refreshTiles() {
        viewModelScope.launch {
            val q = query.trim()
            val vec = if (q.isEmpty()) null else (queryVector(q) ?: return@launch)
            tiles = repo.tiles(vec)
        }
    }

    fun setViewMode(m: ViewMode) {
        if (m == viewMode) return
        viewModelScope.launch {
            c.settings.setViewMode(m)
            clearSelection()
            scene.glow = null
            if (m == ViewMode.TILES) tiles = repo.tiles(query.trim().takeIf { it.isNotEmpty() }?.let { queryVector(it) })
            scene.tick++
        }
    }

    /** Opens the pager on the tapped tile, swiping through the tiles that are on screen. */
    fun openTile(ids: List<String>, index: Int) {
        viewModelScope.launch {
            val list = repo.pagesFor(ids)
            if (list.isEmpty()) return@launch
            pages = list
            page = index.coerceIn(0, list.size - 1)
            scene.selected = scene.snapshot.indexOf(list[page].item.id)
            scene.tick++
        }
    }

    fun clearSearch() {
        query = ""
        searchHits = -1
        searchNote = null
        scene.glow = null
        scene.tick++
        if (viewMode == ViewMode.TILES) refreshTiles()
    }

    // ---------------------------------------------------------------- preview actions

    fun shareSelected() {
        val d = detail ?: return
        viewModelScope.launch {
            val p = d.item.mediaPath
            if (p != null) _events.emit(UiEvent.ShareFiles(listOf(p))) else _events.emit(UiEvent.ShareText(d.item.text ?: d.item.title))
        }
    }

    /** Deletes the page being shown. The pager moves on to the next page, or closes when the cluster is empty. */
    fun deleteSelected() {
        val d = detail ?: return
        val old = pages
        val at = page
        viewModelScope.launch {
            repo.delete(d.item.id)
            refresh(animate = true)
            val next = ClusterPager.indexAfterDelete(old.size, at)
            if (next == null) {
                clearSelection()
            } else {
                val left = old.filterIndexed { k, _ -> k != at }
                pages = left
                page = next
                val idx = scene.snapshot.indexOf(left[next].item.id)
                scene.selected = idx
                if (idx >= 0) scene.easeTo(idx)
            }
            scene.tick++
        }
    }

    // ---------------------------------------------------------------- adding

    fun onPlus() {
        route = if (modelState.value is ModelState.Ready) Route.ADD else Route.DOWNLOAD
    }

    fun addPhotosAndVideos(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        for (u in uris) {
            val mime = resolver.getType(u).orEmpty()
            c.queue.enqueueUri(if (mime.startsWith("video")) JobKind.VIDEO else JobKind.PHOTO, u)
        }
        route = Route.NONE
        tab = Tab.MAP
    }

    fun addAudio(uri: Uri) {
        c.queue.enqueueUri(JobKind.AUDIO_FILE, uri)
        route = Route.NONE
        tab = Tab.MAP
    }

    fun addCameraShot(file: File) {
        c.queue.enqueueFile(JobKind.PHOTO, file, "Photo")
        route = Route.NONE
        tab = Tab.MAP
    }

    fun addNote(text: String) {
        c.queue.enqueueNote(text)
        route = Route.NONE
        tab = Tab.MAP
    }

    fun startRecording(hasPermission: Boolean) {
        if (!hasPermission) {
            viewModelScope.launch { _events.emit(UiEvent.NeedMic) }
            return
        }
        if (!recorder.start()) return
        route = Route.RECORD
        viewModelScope.launch {
            while (recorder.isRecording) {
                recordingMs = recorder.elapsedMs
                recordingLevel = recorder.level
                delay(80)
            }
            if (route == Route.RECORD) stopRecording()
        }
    }

    fun stopRecording() {
        val f = File(getApplication<Application>().filesDir, "pending").also { it.mkdirs() }.resolve("rec-${System.currentTimeMillis()}.wav")
        val out = recorder.stop(f)
        route = Route.NONE
        recordingMs = 0
        if (out != null) {
            c.queue.enqueueFile(JobKind.RECORDING, out, "Voice note")
            tab = Tab.MAP
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        route = Route.NONE
    }

    fun retryJob(id: String) = c.queue.retry(id)

    private fun onLanded(l: LandedUi) {
        viewModelScope.launch {
            val spawnY = scene.camera.sceneRadius * 1.8f
            val spawn = floatArrayOf(scene.camera.targetX, scene.camera.targetY + spawnY, scene.camera.targetZ)
            refresh(animate = true, spawn = spawn)
            val idx = scene.snapshot.indexOf(l.item.id)
            if (idx >= 0) {
                val links = l.links.map { scene.snapshot.indexOf(it.itemId) to it.simPercent }.filter { it.first >= 0 }
                scene.landing = LandingFx(idx, links.map { it.first }.toIntArray(), links.map { it.second }.toIntArray())
            }
            landed = l
            val n = settings.value.additionsSinceRelayout + 1
            if (n >= config.relayoutEvery) {
                c.settings.setAdditions(0)
                relayout()
            } else {
                c.settings.setAdditions(n)
            }
            delay(4200)
            if (landed == l) landed = null
        }
    }

    /** Refits the layout in the background. It runs by itself; there is no button for it. */
    private fun relayout() {
        if (relayoutProgress != null) return
        viewModelScope.launch {
            relayoutProgress = 0f
            repo.relayout { p -> relayoutProgress = p; true }
            relayoutProgress = null
            refresh(animate = true)
        }
    }

    // ---------------------------------------------------------------- settings and deleting

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }

    fun dismissDemoHint() = viewModelScope.launch { c.settings.setHintDismissed(true) }

    fun ask(what: Confirm) { confirm = what }

    fun dismissConfirm() { confirm = null }

    /** Runs the deletion the user confirmed. */
    fun confirmed() {
        val what = confirm ?: return
        confirm = null
        when (what) {
            Confirm.DEMO -> deleteDemo()
            Confirm.EVERYTHING -> deleteEverythingAdded()
            Confirm.MODEL -> deleteModel()
            Confirm.DOWNLOAD -> downloadModel()
        }
    }

    private fun deleteDemo() {
        viewModelScope.launch {
            clearSelection(); clearSearch()
            repo.deleteDemo()
            refresh(animate = true)
            scene.resetView(animated = false)
            relayout()
        }
    }

    private fun deleteEverythingAdded() {
        viewModelScope.launch {
            clearSelection(); clearSearch()
            repo.deleteUserItems()
            refresh(animate = true)
            scene.resetView(animated = false)
            relayout()
        }
    }

    fun downloadModel() = c.models.download()
    fun pauseDownload() = c.models.pause()

    private fun deleteModel() {
        viewModelScope.launch {
            c.closeEmbedder()
            c.models.delete()
        }
    }

    fun demoCredits(): List<DemoItem> = repo.loadDemoManifest().items.filter { it.type != "text" }

    override fun onCleared() {
        recorder.cancel()
        super.onCleared()
    }
}
