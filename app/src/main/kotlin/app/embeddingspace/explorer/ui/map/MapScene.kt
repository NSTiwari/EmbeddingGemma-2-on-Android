package app.embeddingspace.explorer.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import app.embeddingspace.explorer.data.ItemUi
import app.embeddingspace.explorer.data.MapSnapshot
import app.embeddingspace.explorer.domain.camera.Camera3D
import app.embeddingspace.explorer.domain.camera.CameraAnimator
import app.embeddingspace.explorer.domain.camera.AutoFit
import app.embeddingspace.explorer.domain.camera.CardLayout
import app.embeddingspace.explorer.domain.camera.PositionTween
import app.embeddingspace.explorer.domain.camera.Projection
import kotlin.math.max
import kotlin.math.sqrt

/** Short lived lines from a freshly landed item to its closest matches. */
class LandingFx(val index: Int, val links: IntArray, val percents: IntArray, var life: Float = 1f)

/**
 * Everything the map view draws from, held in plain arrays so the draw loop does not allocate.
 * The state that changes every frame (tick) is the only Compose state; the rest is read during drawing.
 */
class MapScene {
    var snapshot: MapSnapshot = MapSnapshot(emptyList(), FloatArray(0), emptyList())
        private set
    val tween = PositionTween()
    val camera = Camera3D()
    val animator = CameraAnimator(camera)
    val proj = Projection(2048)

    var colorIdx = IntArray(0)
        private set
    var typeOrd = IntArray(0)
        private set
    var tentative = BooleanArray(0)
        private set

    /** Sound and note cards, drawn smaller than photo cards. */
    var small = BooleanArray(0)
        private set

    var glow: FloatArray? = null
    var selected = -1
    var landing: LandingFx? = null

    /** Where every card is on screen this frame. Also used to pick a card on tap. */
    val layout = CardLayout()

    /** Width in pixels of a photo card at the fitted zoom, set by the view each frame. The auto fit needs it. */
    var heroPx = 140f

    /** True while a finger is moving the view, so big maps can show their spokes faintly. */
    var dragging = false

    /** Camera distance that shows the whole map, from the last fit. */
    var fitDistance = 0f
        private set

    /** 0 nothing, 1 fit the whole map on screen now, 2 fly there. Done in the next frame, once the view size is known. */
    var pendingFit = 0

    /** Bumped whenever something moved, so only the draw phase reruns. */
    var tick by mutableLongStateOf(0L)

    val count: Int get() = snapshot.size

    fun item(i: Int): ItemUi = snapshot.items[i]

    /** Takes a new snapshot. With [animate] the points glide from where they are to their new places. */
    fun apply(s: MapSnapshot, animate: Boolean, spawn: FloatArray? = null) {
        val firstFill = snapshot.size == 0
        val oldIds = snapshot.items.map { it.id }
        snapshot = s
        rebuildArrays()
        if (firstFill || !animate) {
            tween.setImmediate(s.positions)
        } else {
            // keep each surviving point where it is on screen: re-map the old positions by id
            val old = tween.current
            val idx = HashMap<String, Int>()
            oldIds.forEachIndexed { i, id -> idx[id] = i }
            val start = FloatArray(s.positions.size)
            for (i in s.items.indices) {
                val o = idx[s.items[i].id]
                for (c in 0 until 3) {
                    start[i * 3 + c] = if (o != null && o * 3 + c < old.size) old[o * 3 + c] else (spawn?.get(c) ?: s.positions[i * 3 + c])
                }
            }
            tween.setImmediate(start)
            tween.retarget(s.positions, 1100)
        }
        if (selected >= s.size) selected = -1
        glow = null
        pendingFit = if (s.size == 0) 0 else if (firstFill || !animate) 1 else 2
        tick++
    }

    private fun rebuildArrays() {
        val n = snapshot.size
        colorIdx = IntArray(n) { snapshot.items[it].clusterId.let { id -> snapshot.cluster(id)?.colorIndex ?: 0 } }
        typeOrd = IntArray(n) { snapshot.items[it].type.ordinal }
        tentative = BooleanArray(n) { snapshot.items[it].tentative }
        small = BooleanArray(n) { snapshot.items[it].type.let { t -> t == app.embeddingspace.explorer.domain.model.MediaType.AUDIO || t == app.embeddingspace.explorer.domain.model.MediaType.TEXT } }
        proj.ensure(n)
    }

    private fun centre(p: FloatArray): FloatArray {
        var cx = 0f; var cy = 0f; var cz = 0f
        for (i in 0 until count) { cx += p[i * 3]; cy += p[i * 3 + 1]; cz += p[i * 3 + 2] }
        if (count > 0) { cx /= count; cy /= count; cz /= count }
        return floatArrayOf(cx, cy, cz)
    }

    private fun maxRadius(p: FloatArray, c: FloatArray): Float {
        var r = 0f
        for (i in 0 until count) {
            val dx = p[i * 3] - c[0]; val dy = p[i * 3 + 1] - c[1]; val dz = p[i * 3 + 2] - c[2]
            r = max(r, sqrt(dx * dx + dy * dy + dz * dz))
        }
        return max(r, 1f)
    }

    /** Puts the camera where every card fits on screen, at once or with a flight. Used on open, after adds and deletes, and on double tap. */
    fun fit(animated: Boolean) {
        if (count == 0) return
        val p = tween.target
        val c = centre(p)
        val radius = maxRadius(p, c)
        camera.setSceneRadius(radius)
        val d = AutoFit.distance(camera, p, count, c[0], c[1], c[2], radius, heroPx * 0.68f, heroPx * 0.56f)
        fitDistance = d
        if (animated) {
            animator.flyTo(c[0], c[1], c[2], d, Camera3D.DEFAULT_YAW, Camera3D.DEFAULT_PITCH, 800)
        } else {
            camera.reset(radius, c[0], c[1], c[2])
            camera.distance = d
        }
        tick++
    }

    fun resetView(animated: Boolean = true) = fit(animated)

    /** Called by the view each frame after it knows its size. */
    fun runPendingFit() {
        val mode = pendingFit
        if (mode == 0) return
        pendingFit = 0
        fit(mode == 2)
    }

    /** Eases the camera towards an item and keeps the zoom, so you see where you are in the space. */
    fun easeTo(i: Int, ms: Int = 600) {
        if (i !in 0 until count) return
        val p = tween.target
        animator.flyTo(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], camera.distance, camera.yaw, camera.pitch, ms)
        tick++
    }

    /**
     * Opens a card: the camera eases to it and, if it is further out than half the fitted distance, comes closer so the
     * cards around it are big enough to read above the preview sheet.
     */
    fun focus(i: Int, ms: Int = 650) {
        if (i !in 0 until count) return
        val p = tween.target
        val d = if (fitDistance > 0f) minOf(camera.distance, fitDistance * 0.5f) else camera.distance
        animator.flyTo(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], d, camera.yaw, camera.pitch, ms)
        tick++
    }

    fun flyToItem(i: Int, ms: Int = 750, distFactor: Float = 1.1f) {
        if (i !in 0 until count) return
        val p = tween.target
        animator.flyTo(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], camera.sceneRadius * distFactor, ms = ms)
        tick++
    }

    /** Advances animations. Returns true if the picture changed. */
    fun step(dt: Float): Boolean {
        var changed = animator.step(dt)
        if (tween.step(dt)) changed = true
        landing?.let {
            it.life -= dt / 6f
            if (it.life <= 0f) landing = null
            changed = true
        }
        if (changed) tick++
        return changed
    }
}
