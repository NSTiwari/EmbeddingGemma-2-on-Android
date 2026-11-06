package app.embeddingspace.explorer.domain.queue

/** The ways an item can be added. */
enum class JobKind(val key: String, val slow: Boolean = false) {
    PHOTO("photo"), VIDEO("video", slow = true), AUDIO_FILE("audio"), RECORDING("recording"), NOTE("note");

    companion object {
        fun fromKey(k: String): JobKind = entries.firstOrNull { it.key == k } ?: error("unknown job kind: $k")
    }
}

/** Where an item is on its way into the map. */
enum class Stage(val key: String) {
    QUEUED("queued"), PREPARE("prepare"), EMBED("embed"), PLACE("place"), DONE("done"), FAILED("failed");

    val running: Boolean get() = this == PREPARE || this == EMBED || this == PLACE
    val finished: Boolean get() = this == DONE || this == FAILED

    companion object {
        fun fromKey(k: String): Stage = entries.firstOrNull { it.key == k } ?: QUEUED
    }
}

data class Job(
    val id: String,
    val kind: JobKind,
    /** File path, content uri or the note text, depending on [kind]. */
    val source: String,
    val title: String = "",
    val stage: Stage = Stage.QUEUED,
    val error: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = 0L,
) {
    /** 0..1 for progress chips. */
    val progress: Float
        get() = when (stage) {
            Stage.QUEUED -> 0f
            Stage.PREPARE -> 0.2f
            Stage.EMBED -> 0.55f
            Stage.PLACE -> 0.9f
            Stage.DONE -> 1f
            Stage.FAILED -> 0f
        }
}

/** Pure bookkeeping for the processing queue. The coroutine worker lives in the app. */
object QueueLogic {
    const val MAX_ATTEMPTS = 3

    fun enqueue(jobs: List<Job>, job: Job): List<Job> = if (jobs.any { it.id == job.id }) jobs else jobs + job

    /** The next job to work on: the oldest one waiting. */
    fun next(jobs: List<Job>): Job? = jobs.filter { it.stage == Stage.QUEUED }.minByOrNull { it.createdAt }

    fun advance(jobs: List<Job>, id: String, stage: Stage): List<Job> =
        jobs.map { if (it.id == id) it.copy(stage = stage, error = null) else it }

    fun fail(jobs: List<Job>, id: String, message: String): List<Job> =
        jobs.map { if (it.id == id) it.copy(stage = Stage.FAILED, error = message, attempts = it.attempts + 1) else it }

    /** Failed jobs below the attempt limit go back to the line. */
    fun retry(jobs: List<Job>, id: String): List<Job> =
        jobs.map { if (it.id == id && it.stage == Stage.FAILED && it.attempts < MAX_ATTEMPTS) it.copy(stage = Stage.QUEUED, error = null) else it }

    fun retryAll(jobs: List<Job>): List<Job> = jobs.fold(jobs) { acc, j -> retry(acc, j.id) }

    fun remove(jobs: List<Job>, id: String): List<Job> = jobs.filterNot { it.id == id }

    fun clearFinished(jobs: List<Job>): List<Job> = jobs.filterNot { it.stage == Stage.DONE }

    /** After the app was killed mid-run, running jobs start again from the top. */
    fun restoreAfterRestart(jobs: List<Job>): List<Job> =
        jobs.map { if (it.stage.running) it.copy(stage = Stage.QUEUED) else it }

    fun pendingCount(jobs: List<Job>): Int = jobs.count { !it.stage.finished }

    /** Overall progress of the whole queue, 0..1. */
    fun overallProgress(jobs: List<Job>): Float =
        if (jobs.isEmpty()) 1f else jobs.map { it.progress }.average().toFloat()

    /** Text such as "Embedding" for the chip. */
    fun stageLabel(stage: Stage): String = when (stage) {
        Stage.QUEUED -> "Waiting"
        Stage.PREPARE -> "Preparing"
        Stage.EMBED -> "Embedding"
        Stage.PLACE -> "Placing"
        Stage.DONE -> "Done"
        Stage.FAILED -> "Failed"
    }
}
