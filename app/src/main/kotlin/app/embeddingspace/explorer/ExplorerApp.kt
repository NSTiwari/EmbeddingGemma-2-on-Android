package app.embeddingspace.explorer

import android.app.Application
import app.embeddingspace.explorer.data.ExplorerRepository
import app.embeddingspace.explorer.data.ModelManager
import app.embeddingspace.explorer.data.SettingsStore
import app.embeddingspace.explorer.data.db.ExplorerDb
import app.embeddingspace.explorer.engine.Embedder
import app.embeddingspace.explorer.engine.LiteRtEmbedder
import app.embeddingspace.explorer.media.MediaPrep
import app.embeddingspace.explorer.pipeline.ProcessingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ExplorerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Long lived objects, created once. The scope outlives activities, so the queue survives rotation. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = ExplorerDb.open(app)
    val settings = SettingsStore(app)
    val repo = ExplorerRepository(app, db, settings)
    val models = ModelManager(app, scope)
    val prep = MediaPrep(app, File(app.filesDir, "media"))

    private val embedderLock = Mutex()
    private var embedder: Embedder? = null

    /** The embedder, created on first use once the model is on the phone. Null while the model is missing. */
    suspend fun embedder(): Embedder? = embedderLock.withLock {
        embedder?.let { return it }
        if (!models.isReady) return null
        LiteRtEmbedder(models.file, app.cacheDir).also { embedder = it }
    }

    /** Drops the engine, for example after the model is deleted or the backend changes. */
    suspend fun closeEmbedder() = embedderLock.withLock {
        embedder?.close()
        embedder = null
    }

    val queue = ProcessingQueue(app, scope, repo, db.pending(), prep, { embedder() }, { models.isReady })
}
