package app.embeddingspace.explorer.domain.camera

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Result of projecting every point for one frame. Arrays are reused, so drawing does not allocate. */
class Projection(capacity: Int) {
    var sx = FloatArray(capacity)
    var sy = FloatArray(capacity)
    var depth = FloatArray(capacity)
    var scale = FloatArray(capacity)

    /** Point indices sorted far to near (draw order). Only the first [count] entries are valid. */
    var order = IntArray(capacity)
    var count = 0
    var minDepth = 0f
    var maxDepth = 1f
    internal var keys = LongArray(capacity)

    fun ensure(n: Int) {
        if (sx.size >= n) return
        val cap = max(n, sx.size * 2)
        sx = FloatArray(cap); sy = FloatArray(cap); depth = FloatArray(cap); scale = FloatArray(cap)
        order = IntArray(cap); keys = LongArray(cap)
    }

    /** 0 for the nearest point of this frame, 1 for the farthest. */
    fun depth01(i: Int): Float {
        val span = maxDepth - minDepth
        return if (span < 1e-6f) 0f else ((depth[i] - minDepth) / span).coerceIn(0f, 1f)
    }
}

/**
 * Orbit camera with a software perspective projection. The scene turns around [target];
 * [panX] and [panY] slide the picture on screen in pixels.
 */
class Camera3D {
    var yaw = DEFAULT_YAW
    var pitch = DEFAULT_PITCH
    var distance = 2f
    var panX = 0f
    var panY = 0f
    var targetX = 0f
    var targetY = 0f
    var targetZ = 0f
    var sceneRadius = 1f
        private set
    var viewW = 720f
    var viewH = 1280f

    private var cosY = 1f
    private var sinY = 0f
    private var cosP = 1f
    private var sinP = 0f

    val focal: Float get() = min(viewW, viewH) * FOCAL_FACTOR
    val minDistance: Float get() = sceneRadius * MIN_DISTANCE_FACTOR
    val maxDistance: Float get() = sceneRadius * MAX_DISTANCE_FACTOR

    fun setViewport(w: Float, h: Float) {
        viewW = w; viewH = h
    }

    /** Frames a scene of the given radius around the origin and resets pan and angles. */
    fun reset(radius: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f) {
        sceneRadius = max(radius, 1e-3f)
        yaw = DEFAULT_YAW; pitch = DEFAULT_PITCH
        distance = sceneRadius * DEFAULT_DISTANCE_FACTOR
        panX = 0f; panY = 0f
        targetX = cx; targetY = cy; targetZ = cz
    }

    fun setSceneRadius(radius: Float) {
        sceneRadius = max(radius, 1e-3f)
        distance = distance.coerceIn(minDistance, maxDistance)
    }

    /** One-finger drag. Moving right turns the scene right, moving down tips it towards the viewer. */
    fun rotateBy(dxPx: Float, dyPx: Float) {
        yaw += dxPx * ROTATE_SPEED
        pitch = (pitch + dyPx * ROTATE_SPEED).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    /** Pinch: a factor above 1 brings the scene closer. */
    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        distance = (distance / factor).coerceIn(minDistance, maxDistance)
    }

    /** Two-finger drag. */
    fun panBy(dxPx: Float, dyPx: Float) {
        val lim = max(viewW, viewH)
        panX = (panX + dxPx).coerceIn(-lim, lim)
        panY = (panY + dyPx).coerceIn(-lim, lim)
    }

    fun lookAt(x: Float, y: Float, z: Float) {
        targetX = x; targetY = y; targetZ = z
    }

    private fun prepare() {
        cosY = cos(yaw); sinY = sin(yaw); cosP = cos(pitch); sinP = sin(pitch)
    }

    /** Fills [out] for the first [n] points of [pos] (flat x,y,z) and sorts them far to near. */
    fun project(pos: FloatArray, n: Int, out: Projection, skip: BooleanArray? = null) {
        out.ensure(n)
        prepare()
        val cx = viewW * 0.5f + panX
        val cy = viewH * 0.5f + panY
        val f = focal
        val near = distance * 0.12f
        var cnt = 0
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (i in 0 until n) {
            if (skip != null && skip[i]) continue
            val px = pos[i * 3] - targetX
            val py = pos[i * 3 + 1] - targetY
            val pz = pos[i * 3 + 2] - targetZ
            val x1 = px * cosY + pz * sinY
            val z1 = -px * sinY + pz * cosY
            val y2 = py * cosP - z1 * sinP
            val z2 = py * sinP + z1 * cosP
            val d = distance + z2
            if (d <= near) continue
            val s = f / d
            out.sx[i] = cx + x1 * s
            out.sy[i] = cy - y2 * s
            out.depth[i] = d
            out.scale[i] = s
            if (d < mn) mn = d
            if (d > mx) mx = d
            out.keys[cnt] = (java.lang.Float.floatToRawIntBits(d).toLong() shl 32) or i.toLong()
            cnt++
        }
        java.util.Arrays.sort(out.keys, 0, cnt)
        // keys ascend from near to far; store far to near for painter drawing
        for (p in 0 until cnt) out.order[p] = (out.keys[cnt - 1 - p] and 0xffffffffL).toInt()
        out.count = cnt
        out.minDepth = if (cnt == 0) 0f else mn
        out.maxDepth = if (cnt == 0) 1f else mx
    }

    /** Same as [projectOne] but writes sx, sy, depth, scale into [out] (size 4) so nothing is allocated. */
    fun projectInto(x: Float, y: Float, z: Float, out: FloatArray) {
        prepare()
        val px = x - targetX
        val py = y - targetY
        val pz = z - targetZ
        val x1 = px * cosY + pz * sinY
        val z1 = -px * sinY + pz * cosY
        val y2 = py * cosP - z1 * sinP
        val z2 = py * sinP + z1 * cosP
        val d = distance + z2
        val s = focal / max(d, 1e-4f)
        out[0] = viewW * 0.5f + panX + x1 * s
        out[1] = viewH * 0.5f + panY - y2 * s
        out[2] = d
        out[3] = s
    }

    /** Projects one point (allocates, so use it for overlays and tests, not per point per frame). */
    fun projectOne(x: Float, y: Float, z: Float): FloatArray {
        prepare()
        val px = x - targetX
        val py = y - targetY
        val pz = z - targetZ
        val x1 = px * cosY + pz * sinY
        val z1 = -px * sinY + pz * cosY
        val y2 = py * cosP - z1 * sinP
        val z2 = py * sinP + z1 * cosP
        val d = distance + z2
        val s = focal / max(d, 1e-4f)
        return floatArrayOf(viewW * 0.5f + panX + x1 * s, viewH * 0.5f + panY - y2 * s, d, s)
    }

    /**
     * Index of the point the finger meant, or -1. A point is a hit if the touch is inside its draw radius
     * (or within [slopPx]). Bigger, nearer points win ties.
     */
    fun pick(out: Projection, x: Float, y: Float, radiusOf: (Int) -> Float, slopPx: Float = 18f): Int {
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (p in out.count - 1 downTo 0) {
            val i = out.order[p]
            val dx = out.sx[i] - x
            val dy = out.sy[i] - y
            val d = sqrt(dx * dx + dy * dy)
            val r = radiusOf(i)
            if (d > max(r, slopPx)) continue
            val score = d - 0.35f * r
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    companion object {
        const val DEFAULT_YAW = 0.55f
        const val DEFAULT_PITCH = 0.32f
        const val FOCAL_FACTOR = 3.2f
        const val DEFAULT_DISTANCE_FACTOR = 2.4f
        const val MIN_DISTANCE_FACTOR = 0.35f
        const val MAX_DISTANCE_FACTOR = 24f
        const val ROTATE_SPEED = 0.0085f
        const val MAX_PITCH = 1.45f
    }
}
