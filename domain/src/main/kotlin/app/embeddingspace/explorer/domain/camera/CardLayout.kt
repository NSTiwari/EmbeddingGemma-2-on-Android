package app.embeddingspace.explorer.domain.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Sizes of the cards, in pixels, and how much they may overlap. */
class CardParams(
    /** World width of a hero card; the view sets it so a hero card is [CardSizing.heroWidth] pixels at the fitted zoom. */
    var worldW: Float,
    var minW: Float,
    var maxW: Float,
    val aspect: Float = 0.8f,
    /** Negative means cards may overlap by that much before they are pushed apart, like loose photos. */
    var gap: Float = 0f,
    /** Sound and note cards are this share of a photo card. */
    val smallRatio: Float = 0.62f,
    /** How far from the card's centre the item's dot may drift, as a share of the card size. The dot always stays on the card. */
    val dotReach: Float = 0.46f,
)

/** How big the cards are for a given map and screen. */
object CardSizing {
    /** Photos and videos are drawn this wide at most, as a share of the view width. */
    const val MAX_SHARE = 0.26f

    /**
     * Width in pixels of a photo card at the fitted zoom. A handful of items get big cards (about a quarter of the
     * screen width), and more items get smaller ones so everything still fits on screen.
     */
    fun heroWidth(viewW: Float, viewH: Float, items: Int, minPx: Float): Float {
        val byArea = sqrt(0.55f * viewW * viewH / (max(items, 1) * 1.2f))
        return min(MAX_SHARE * viewW, byArea).coerceAtLeast(minPx)
    }
}

/** How visible the spokes from the origin to each item are. */
object LineFade {
    const val FULL_UP_TO = 25
    const val FAINT_UP_TO = 60

    /**
     * Alpha of one spoke. Up to 25 items every spoke is clear. Up to 60 they are very faint. Above that only the
     * selected card and its cluster get one, and while the user drags the rest are barely there.
     */
    fun alpha(items: Int, relevant: Boolean, dragging: Boolean): Float = when {
        relevant -> 0.7f
        items <= FULL_UP_TO -> 0.5f
        items <= FAINT_UP_TO -> 0.13f
        dragging -> 0.06f
        else -> 0f
    }
}

/**
 * Where every card goes on screen. Each item is its own card. Cards start at their projected position and a few passes
 * of repulsion keep them from covering each other too much. The item's dot stays on its card, so the card never wanders
 * far from the item's true position. The pushes are remembered between frames and fade, so the picture is steady.
 */
class CardLayout(capacity: Int = 256) {
    var count = 0
        private set

    /** Per item: centre and size in pixels. */
    var cx = FloatArray(capacity)
        private set
    var cy = FloatArray(capacity)
        private set
    var w = FloatArray(capacity)
        private set
    var h = FloatArray(capacity)
        private set
    private var ox = FloatArray(capacity)
    private var oy = FloatArray(capacity)
    private var bx = FloatArray(capacity)
    private var by = FloatArray(capacity)

    /** Items that were not projected (behind the camera). */
    var skipped = 0
        private set

    /** The items to draw, far to near. Same as the projection's order. */
    var order = IntArray(capacity)
        private set

    private var gridHead = IntArray(8192)
    private var gridNext = IntArray(capacity)

    private fun ensure(n: Int) {
        if (cx.size >= n) return
        val cap = max(n, cx.size * 2)
        cx = cx.copyOf(cap); cy = cy.copyOf(cap); w = w.copyOf(cap); h = h.copyOf(cap)
        ox = ox.copyOf(cap); oy = oy.copyOf(cap); bx = bx.copyOf(cap); by = by.copyOf(cap)
        order = order.copyOf(cap); gridNext = IntArray(cap)
    }

    /** Card width in pixels for a point with the given projection [scale]. */
    fun cardWidth(scale: Float, p: CardParams): Float = (scale * p.worldW).coerceIn(p.minW, p.maxW)

    /**
     * Lays the cards out for the first [n] items of [proj]. [small] marks the sound and note cards, which are drawn
     * smaller.
     */
    fun layout(
        proj: Projection, n: Int, p: CardParams, small: BooleanArray?, viewW: Float, viewH: Float, passes: Int = 8,
    ) {
        ensure(n)
        val total = proj.count
        count = total
        skipped = n - total
        for (k in 0 until total) {
            val i = proj.order[k]
            order[k] = i
            val base = cardWidth(proj.scale[i], p)
            val cw = if (small != null && small[i]) base * p.smallRatio else base
            w[i] = cw
            h[i] = cw * p.aspect
            ox[i] *= 0.985f
            oy[i] *= 0.985f
            bx[i] = proj.sx[i]
            by[i] = proj.sy[i]
            cx[i] = proj.sx[i] + ox[i]
            cy[i] = proj.sy[i] + oy[i]
        }
        if (total > 1) {
            for (pass in 0 until passes) {
                val moved = relax(total, p)
                tether(total, p)
                keepInside(total, p, viewW, viewH)
                if (!moved) break
            }
        }
        for (k in 0 until total) {
            val i = order[k]
            ox[i] = cx[i] - bx[i]
            oy[i] = cy[i] - by[i]
        }
    }

    /** The item's dot stays on its card: a card may drift only part of its own size from the true position. */
    private fun tether(total: Int, p: CardParams) {
        for (k in 0 until total) {
            val i = order[k]
            val rx = w[i] * p.dotReach
            val ry = h[i] * p.dotReach
            cx[i] = cx[i].coerceIn(bx[i] - rx, bx[i] + rx)
            cy[i] = cy[i].coerceIn(by[i] - ry, by[i] + ry)
        }
    }

    private fun keepInside(total: Int, p: CardParams, viewW: Float, viewH: Float) {
        for (k in 0 until total) {
            val i = order[k]
            // a card whose point is off screen is left alone, so zooming in does not pile cards up at the edges
            if (bx[i] < 0f || bx[i] > viewW || by[i] < 0f || by[i] > viewH) continue
            val hw = w[i] / 2
            val hh = h[i] / 2
            cx[i] = if (viewW > 2 * hw) cx[i].coerceIn(hw, viewW - hw) else viewW / 2
            cy[i] = if (viewH > 2 * hh) cy[i].coerceIn(hh, viewH - hh) else viewH / 2
        }
    }

    /** One pass of pairwise pushing. Returns false when nothing overlapped more than allowed. */
    private fun relax(total: Int, p: CardParams): Boolean {
        var maxW = 0f
        for (k in 0 until total) maxW = max(maxW, w[order[k]])
        val cell = max(maxW, 8f)
        java.util.Arrays.fill(gridHead, -1)
        val mask = gridHead.size - 1
        for (k in 0 until total) {
            val i = order[k]
            val key = hash((cx[i] / cell).toInt(), (cy[i] / cell).toInt()) and mask
            gridNext[i] = gridHead[key]
            gridHead[key] = i
        }
        var moved = false
        for (k in 0 until total) {
            val i = order[k]
            val gx = (cx[i] / cell).toInt()
            val gy = (cy[i] / cell).toInt()
            for (dy in -1..1) for (dx in -1..1) {
                var j = gridHead[hash(gx + dx, gy + dy) and mask]
                while (j >= 0) {
                    if (j > i && push(i, j, p)) moved = true
                    j = gridNext[j]
                }
            }
        }
        return moved
    }

    private fun push(i: Int, j: Int, p: CardParams): Boolean {
        val dx = cx[j] - cx[i]
        val dy = cy[j] - cy[i]
        val px = (w[i] + w[j]) / 2 + p.gap - abs(dx)
        val py = (h[i] + h[j]) / 2 + p.gap - abs(dy)
        if (px <= 0f || py <= 0f) return false
        val sx = if (dx != 0f) (if (dx > 0) 1f else -1f) else if ((i + j) % 2 == 0) 1f else -1f
        val sy = if (dy != 0f) (if (dy > 0) 1f else -1f) else if ((i + j) % 3 == 0) 1f else -1f
        if (px < py) {
            val m = px / 2 * 1.05f + 0.01f
            cx[i] -= sx * m; cx[j] += sx * m
        } else {
            val m = py / 2 * 1.05f + 0.01f
            cy[i] -= sy * m; cy[j] += sy * m
        }
        return true
    }

    private fun hash(x: Int, y: Int): Int = (x * 73856093) xor (y * 19349663)

    /** The item whose card contains (x,y) within [slop] pixels, nearest first, or -1. */
    fun pick(x: Float, y: Float, slop: Float): Int {
        for (k in count - 1 downTo 0) {
            val i = order[k]
            if (abs(x - cx[i]) <= w[i] / 2 + slop && abs(y - cy[i]) <= h[i] / 2 + slop) return i
        }
        return -1
    }
}

/** Finds how far the camera must stand so that every card fits on screen. */
object AutoFit {
    /**
     * Smallest distance (from the scene centre, using the default angles) at which every point, with a card of
     * [halfW] by [halfH] pixels around it, stays inside a view of [viewW] by [viewH]. The origin
     * counts too, so the hub is always on screen.
     */
    fun distance(cam: Camera3D, pos: FloatArray, n: Int, cx: Float, cy: Float, cz: Float, radius: Float, halfW: Float, halfH: Float): Float {
        val probe = Camera3D()
        probe.setViewport(cam.viewW, cam.viewH)
        probe.reset(radius, cx, cy, cz)
        val out = FloatArray(4)
        var d = probe.minDistance
        val limit = probe.maxDistance
        while (d < limit) {
            probe.distance = d
            var fits = true
            for (i in 0 until n) {
                probe.projectInto(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], out)
                if (out[2] <= d * 0.12f || out[0] - halfW < 0f || out[0] + halfW > probe.viewW || out[1] - halfH < 0f || out[1] + halfH > probe.viewH) {
                    fits = false; break
                }
            }
            if (fits) return d
            d *= 1.04f
        }
        return limit
    }
}
