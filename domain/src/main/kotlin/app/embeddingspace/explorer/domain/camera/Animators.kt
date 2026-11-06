package app.embeddingspace.explorer.domain.camera

import kotlin.math.max
import kotlin.math.min

object Easing {
    fun inOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - Math.pow((-2.0 * x + 2.0), 3.0).toFloat() / 2f
    }

    fun outCubic(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x * x
    }
}

/** Smooth camera moves (fly to a point, reset) and the gentle idle spin. */
class CameraAnimator(private val cam: Camera3D) {
    private var active = false
    private var t = 0f
    private var dur = 1f
    private var fx = 0f; private var fy = 0f; private var fz = 0f; private var fd = 0f; private var fyaw = 0f; private var fpitch = 0f
    private var fpx = 0f; private var fpy = 0f
    private var tx = 0f; private var ty = 0f; private var tz = 0f; private var td = 0f; private var tyaw = 0f; private var tpitch = 0f

    /** Seconds since the user last touched the map. Auto-spin only runs once this is large enough. */
    var idleSeconds = 10f
    var spinEnabled = true
    var spinRadPerSec = 0.12f
    var idleBeforeSpin = 2.5f

    val isFlying: Boolean get() = active

    fun touch() {
        idleSeconds = 0f
        active = false
    }

    /** Fly so that (x,y,z) becomes the pivot at [dist] from the camera. Pan returns to zero. */
    fun flyTo(x: Float, y: Float, z: Float, dist: Float, yaw: Float = cam.yaw, pitch: Float = cam.pitch, ms: Int = 700) {
        fx = cam.targetX; fy = cam.targetY; fz = cam.targetZ; fd = cam.distance; fyaw = cam.yaw; fpitch = cam.pitch
        fpx = cam.panX; fpy = cam.panY
        tx = x; ty = y; tz = z; td = dist.coerceIn(cam.minDistance, cam.maxDistance)
        // take the short way round
        var dy = yaw - cam.yaw
        while (dy > Math.PI) dy -= (2 * Math.PI).toFloat()
        while (dy < -Math.PI) dy += (2 * Math.PI).toFloat()
        tyaw = cam.yaw + dy
        tpitch = pitch.coerceIn(-Camera3D.MAX_PITCH, Camera3D.MAX_PITCH)
        t = 0f; dur = max(ms, 1) / 1000f; active = true
    }

    /** Advances by [dtSec]. Returns true while something moved, so the caller knows to redraw. */
    fun step(dtSec: Float): Boolean {
        val dt = min(dtSec, 0.1f)
        if (active) {
            t += dt / dur
            val e = Easing.inOutCubic(t)
            cam.targetX = fx + (tx - fx) * e
            cam.targetY = fy + (ty - fy) * e
            cam.targetZ = fz + (tz - fz) * e
            cam.distance = fd + (td - fd) * e
            cam.yaw = fyaw + (tyaw - fyaw) * e
            cam.pitch = fpitch + (tpitch - fpitch) * e
            cam.panX = fpx * (1f - e)
            cam.panY = fpy * (1f - e)
            if (t >= 1f) active = false
            return true
        }
        idleSeconds += dt
        if (spinEnabled && idleSeconds >= idleBeforeSpin) {
            cam.yaw += spinRadPerSec * dt
            return true
        }
        return false
    }
}

/**
 * Moves displayed point positions towards their targets with easing, so relayouts and new arrivals glide.
 * [current] is what gets drawn, [target] is where the points belong.
 */
class PositionTween {
    var current = FloatArray(0)
        private set
    var target = FloatArray(0)
        private set
    private var from = FloatArray(0)
    private var t = 1f
    private var dur = 1f

    val isMoving: Boolean get() = t < 1f
    val count: Int get() = current.size / 3

    /** Sets new targets. Points that existed keep their current spot as the start; new points start at [spawn] if given. */
    fun retarget(newTarget: FloatArray, ms: Int = 900, spawn: FloatArray? = null) {
        val n = newTarget.size
        val start = FloatArray(n)
        for (i in 0 until n) start[i] = if (i < current.size) current[i] else (spawn?.get(i % 3) ?: newTarget[i])
        from = start
        current = start.copyOf()
        target = newTarget.copyOf()
        t = 0f; dur = max(ms, 1) / 1000f
        if (ms <= 0) snap()
    }

    /** Jump straight to the targets. */
    fun snap() {
        current = target.copyOf(); from = target.copyOf(); t = 1f
    }

    fun setImmediate(p: FloatArray) {
        target = p.copyOf(); current = p.copyOf(); from = p.copyOf(); t = 1f
    }

    /** Removes the point at [index], keeping the others where they are. */
    fun removeAt(index: Int) {
        fun cut(a: FloatArray) = FloatArray(a.size - 3).also { r ->
            System.arraycopy(a, 0, r, 0, index * 3)
            System.arraycopy(a, index * 3 + 3, r, index * 3, a.size - index * 3 - 3)
        }
        current = cut(current); target = cut(target); from = cut(from)
    }

    fun step(dtSec: Float): Boolean {
        if (t >= 1f) return false
        t = min(1f, t + min(dtSec, 0.1f) / dur)
        val e = Easing.outCubic(t)
        for (i in current.indices) current[i] = from[i] + (target[i] - from[i]) * e
        if (t >= 1f) System.arraycopy(target, 0, current, 0, target.size)
        return true
    }
}
