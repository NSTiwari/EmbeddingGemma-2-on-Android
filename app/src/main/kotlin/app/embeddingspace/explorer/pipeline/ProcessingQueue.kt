package app.embeddingspace.explorer.pipeline

import android.content.Context
import android.net.Uri
import app.embeddingspace.explorer.data.ExplorerRepository
import app.embeddingspace.explorer.data.LandedUi
import app.embeddingspace.explorer.data.db.PendingDao
import app.embeddingspace.explorer.data.db.PendingEntity
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.queue.Job
import app.embeddingspace.explorer.domain.queue.JobKind
import app.embeddingspace.explorer.domain.queue.QueueLogic
import app.embeddingspace.explorer.domain.queue.Stage
import app.embeddingspace.explorer.engine.Embedder
import app.embeddingspace.explorer.media.MediaPrep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Runs new items through prepare, embed and place, one at a time, in the app scope so it survives rotation.
 * Pending items are kept in Room and start again after a restart.
 */
class ProcessingQueue(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repo: ExplorerRepository,
    private val dao: PendingDao,
    private val prep: MediaPrep,
    private val embedder: suspend () -> Embedder?,
    private val modelReady: () -> Boolean,
) {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    private val _landed = MutableSharedFlow<LandedUi>(extraBufferCapacity = 16)
    val landed: SharedFlow<LandedUi> = _landed

    private var worker: kotlinx.coroutines.Job? = null
    private val pendingDir = File(context.filesDir, "pending").also { it.mkdirs() }

    suspend fun restore() {
        val saved = dao.all().map {
            Job(it.id, JobKind.fromKey(it.kind), it.source, it.title, app.embeddingspace.explorer.domain.queue.Stage.fromKey(it.stage), it.error, it.attempts, it.createdAt)
        }
        _jobs.value = QueueLogic.restoreAfterRestart(saved).filter { it.kind == JobKind.NOTE || File(it.source).exists() }
        kick()
    }

    /** Adds a picked uri. The file is copied first so the item survives a restart. */
    fun enqueueUri(kind: JobKind, uri: Uri, title: String? = null) {
        val id = UUID.randomUUID().toString()
        val name = title ?: prep.displayName(uri) ?: kind.key
        val job = Job(id, kind, uri.toString(), name, Stage.PREPARE, createdAt = System.currentTimeMillis())
        _jobs.update { QueueLogic.enqueue(it, job) }
        scope.launch(Dispatchers.IO) {
            try {
                val ext = when (kind) {
                    JobKind.PHOTO -> "jpg"
                    JobKind.VIDEO -> "mp4"
                    else -> name.substringAfterLast('.', "m4a").take(5)
                }
                val f = prep.copyUri(uri, File(pendingDir, "$id.$ext"))
                val ready = job.copy(source = f.absolutePath, stage = Stage.QUEUED)
                _jobs.update { list -> list.map { if (it.id == id) ready else it } }
                save(ready)
                kick()
            } catch (e: Throwable) {
                fail(id, "Could not read this file")
            }
        }
    }

    /** Adds a file that is already in app storage (camera shot, recording). */
    fun enqueueFile(kind: JobKind, file: File, title: String) {
        val job = Job(UUID.randomUUID().toString(), kind, file.absolutePath, title, Stage.QUEUED, createdAt = System.currentTimeMillis())
        _jobs.update { QueueLogic.enqueue(it, job) }
        scope.launch(Dispatchers.IO) { save(job); kick() }
    }

    fun enqueueNote(text: String) {
        val job = Job(UUID.randomUUID().toString(), JobKind.NOTE, text, text.take(40), Stage.QUEUED, createdAt = System.currentTimeMillis())
        _jobs.update { QueueLogic.enqueue(it, job) }
        scope.launch(Dispatchers.IO) { save(job); kick() }
    }

    fun retry(id: String) {
        _jobs.update { QueueLogic.retry(it, id) }
        kick()
    }

    fun dismiss(id: String) {
        _jobs.update { QueueLogic.remove(it, id) }
        scope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    fun kick() {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.Default) {
            while (true) {
                if (!modelReady()) return@launch
                val job = QueueLogic.next(_jobs.value) ?: return@launch
                if (!process(job)) return@launch
            }
        }
    }

    private suspend fun save(job: Job) {
        dao.upsert(PendingEntity(job.id, job.kind.key, job.source, job.title, job.stage.key, job.error, job.attempts, job.createdAt))
    }

    private suspend fun stage(id: String, s: Stage) {
        _jobs.update { QueueLogic.advance(it, id, s) }
        _jobs.value.firstOrNull { it.id == id }?.let { save(it) }
    }

    private suspend fun fail(id: String, msg: String) {
        _jobs.update { QueueLogic.fail(it, id, msg) }
        _jobs.value.firstOrNull { it.id == id }?.let { save(it) }
    }

    private suspend fun process(job: Job): Boolean {
        val id = job.id
        try {
            val emb = embedder() ?: return false
            stage(id, Stage.PREPARE)
            val newId = UUID.randomUUID().toString()
            val media = File(context.filesDir, "media").also { it.mkdirs() }
            val landed: LandedUi = when (job.kind) {
                JobKind.NOTE -> {
                    stage(id, Stage.EMBED)
                    val v = emb.embedText(job.source)
                    stage(id, Stage.PLACE)
                    repo.addItem(newId, MediaType.TEXT, job.title, v, text = job.source)
                }
                JobKind.PHOTO -> {
                    val p = withContext(Dispatchers.IO) { prep.prepareImage(newId, File(job.source), job.title) }
                    stage(id, Stage.EMBED)
                    val v = emb.embedImage(p.jpeg)
                    stage(id, Stage.PLACE)
                    repo.addItem(newId, MediaType.IMAGE, job.title, v, mediaPath = p.mediaPath, thumbPath = p.thumbPath)
                }
                JobKind.VIDEO -> {
                    val p = withContext(Dispatchers.IO) { prep.prepareVideo(newId, File(job.source), job.title) }
                    stage(id, Stage.EMBED)
                    val v = emb.embedFrames(p.frames)
                    stage(id, Stage.PLACE)
                    repo.addItem(newId, MediaType.VIDEO, job.title, v, mediaPath = p.mediaPath, thumbPath = p.thumbPath, durationMs = p.durationMs)
                }
                JobKind.AUDIO_FILE, JobKind.RECORDING -> {
                    val p = withContext(Dispatchers.IO) { prep.prepareAudio(newId, File(job.source), job.title) }
                    stage(id, Stage.EMBED)
                    val v = emb.embedAudio(p.wav)
                    stage(id, Stage.PLACE)
                    repo.addItem(newId, MediaType.AUDIO, job.title, v, mediaPath = p.mediaPath, durationMs = p.durationMs)
                }
            }
            stage(id, Stage.DONE)
            dao.delete(id)
            _landed.emit(landed)
            delay(900)
            _jobs.update { QueueLogic.remove(it, id) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(id, "Could not add this item")
            return true
        }
    }
}
