package app.embeddingspace.explorer.ui.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.embeddingspace.explorer.data.MapSnapshot
import app.embeddingspace.explorer.domain.camera.CardParams
import app.embeddingspace.explorer.domain.camera.CardSizing
import app.embeddingspace.explorer.domain.camera.LineFade
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.ui.media.ThumbCache
import app.embeddingspace.explorer.ui.media.WaveCache
import app.embeddingspace.explorer.ui.theme.Explorer
import app.embeddingspace.explorer.ui.theme.xc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val TAP_MS = 320L

/** Cached note lines per item, so a frame does not build strings. */
private class CardText {
    private var owner: MapSnapshot? = null
    var lines = arrayOfNulls<Array<String>>(0)
    var lineBucket = IntArray(0)
    val wave = FloatArray(WaveCache.BARS)

    fun sync(s: MapSnapshot) {
        if (owner === s) return
        owner = s
        lines = arrayOfNulls(s.size); lineBucket = IntArray(s.size)
    }
}

private class Strokes(val normal: Stroke, val strong: Stroke, val dot: Stroke)

/**
 * The 3D vector space. A blue hub sits at the origin, and every item is a framed card with a dot on it at the item's
 * exact spot and a thin spoke of its cluster colour back to the hub. Nearer cards are larger and on top, farther
 * ones smaller and a little faded. Rotating, pinching and panning move the spokes and cards live.
 * One finger rotates, two fingers pinch and pan, a tap opens the card, a double tap fits everything on screen again.
 */
@Composable
fun MapView(
    scene: MapScene,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
    onNeedThumb: (String) -> Unit = {},
    onNeedWave: (String) -> Unit = {},
) {
    val ex = xc
    val density = LocalDensity.current.density
    val slop = LocalViewConfiguration.current.touchSlop
    val text = remember { CardText() }
    val play = remember { Glyphs.play() }
    val tmp = remember { FloatArray(4) }
    val requested = remember { HashSet<String>() }
    val params = remember(density) { CardParams(1f, 40f * density, 300f * density, 0.8f) }
    val bodyPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) } }
    val strokes = remember(density) { Strokes(Stroke(2.4f * density), Stroke(5f * density), Stroke(1.6f * density)) }

    LaunchedEffect(scene) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                scene.step(dt)
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(scene) {
                var lastTapTime = 0L
                var lastTapX = 0f
                var lastTapY = 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    scene.animator.touch()
                    val t0 = down.uptimeMillis
                    var moved = false
                    var prevSpan = 0f
                    var prevCx = 0f
                    var prevCy = 0f
                    var lastUp = t0
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        lastUp = event.changes[0].uptimeMillis
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) break
                        if (active.size >= 2) {
                            val a = active[0]
                            val b = active[1]
                            val span = hypot(a.position.x - b.position.x, a.position.y - b.position.y)
                            val cx = (a.position.x + b.position.x) / 2f
                            val cy = (a.position.y + b.position.y) / 2f
                            if (prevSpan > 0f) {
                                if (span > 0f) scene.camera.zoomBy(span / prevSpan)
                                scene.camera.panBy(cx - prevCx, cy - prevCy)
                            }
                            prevSpan = span; prevCx = cx; prevCy = cy
                            moved = true
                            scene.dragging = true
                            active.forEach { it.consume() }
                            scene.tick++
                        } else {
                            prevSpan = 0f
                            val c = active[0]
                            val d = c.positionChange()
                            if (!moved && hypot(c.position.x - down.position.x, c.position.y - down.position.y) > slop) moved = true
                            if (moved) {
                                scene.dragging = true
                                scene.camera.rotateBy(d.x, d.y)
                                c.consume()
                                scene.tick++
                            }
                        }
                        scene.animator.touch()
                    }
                    scene.dragging = false
                    scene.tick++
                    if (!moved && (lastUp - t0) < TAP_MS) {
                        val x = down.position.x
                        val y = down.position.y
                        val hit = scene.layout.pick(x, y, 4f * density)
                        val now = down.uptimeMillis
                        val dbl = now - lastTapTime < 350 && hypot(x - lastTapX, y - lastTapY) < 48f * density
                        if (dbl) {
                            if (hit >= 0) { onSelect(hit); scene.easeTo(hit) } else scene.resetView()
                            lastTapTime = 0L
                        } else {
                            onSelect(hit)
                            lastTapTime = now; lastTapX = x; lastTapY = y
                        }
                    }
                }
            },
    ) {
        scene.tick
        val cam = scene.camera
        cam.setViewport(size.width, size.height)
        val n = minOf(scene.count, scene.tween.current.size / 3)
        // a handful of items get big cards; more items get smaller ones so all of them fit
        scene.heroPx = CardSizing.heroWidth(size.width, size.height, n, 22f * density)
        scene.runPendingFit()
        drawRect(brush = ex.background)
        cam.project(scene.tween.current, n, scene.proj)
        val proj = scene.proj
        val glow = scene.glow
        val sel = scene.selected
        val hero = scene.heroPx
        val fitD = if (scene.fitDistance > 0f) scene.fitDistance else cam.distance
        params.worldW = hero * fitD / cam.focal
        params.minW = hero * 0.4f
        params.maxW = hero * 2.4f
        params.gap = -0.08f * hero
        val layout = scene.layout
        layout.layout(proj, n, params, scene.small, size.width, size.height)
        text.sync(scene.snapshot)
        val items = scene.snapshot.items
        val selCluster = if (sel in 0 until n) items[sel].clusterId else Int.MIN_VALUE

        // the hub at the origin, and a spoke from it to every item's dot
        cam.projectInto(0f, 0f, 0f, tmp)
        val hubX = tmp[0]
        val hubY = tmp[1]
        for (k in 0 until layout.count) {
            val i = layout.order[k]
            val hit = glow != null && glow[i] > 0f
            val relevant = i == sel || hit || items[i].clusterId == selCluster
            var a = LineFade.alpha(n, relevant, scene.dragging)
            if (glow != null && !hit) a *= 0.3f
            if (a <= 0f) continue
            val col = ex.cluster(scene.colorIdx[i])
            val width = if (i == sel || hit) 2.8f * density else 1.5f * density
            drawLine(col.copy(alpha = if (i == sel || hit) 0.95f else a), Offset(hubX, hubY), Offset(proj.sx[i], proj.sy[i]), width, StrokeCap.Round)
        }

        scene.landing?.let { fx ->
            if (fx.index in 0 until n) {
                val c = ex.accent.copy(alpha = (fx.life * 1.4f).coerceIn(0f, 1f))
                for (t in fx.links) {
                    if (t !in 0 until n) continue
                    drawLine(c, Offset(layout.cx[fx.index], layout.cy[fx.index]), Offset(layout.cx[t], layout.cy[t]), 2.5f * density)
                }
            }
        }

        val dim = if (glow != null) 0.25f else 1f
        for (k in 0 until layout.count) {
            val i = layout.order[k]
            val hit = glow != null && glow[i] > 0f
            val strong = i == sel || hit
            val fog = if (strong) 1f else 1f - 0.25f * proj.depth01(i)
            val a = fog * (if (glow == null || hit) 1f else dim)
            val w = layout.w[i]
            drawCard(
                scene, i, layout.cx[i] - w / 2, layout.cy[i] - layout.h[i] / 2, w, layout.h[i], a, ex.cluster(scene.colorIdx[i]),
                if (strong) strokes.strong else strokes.normal, strokes.dot, ex, density, text, bodyPaint, play,
                proj.sx[i], proj.sy[i], onNeedThumb, onNeedWave, requested,
            )
        }

        // the hub is drawn last so it is never lost behind a card
        drawCircle(ex.hub.copy(alpha = 0.22f), 15f * density, Offset(hubX, hubY))
        drawCircle(Color.White, 8.5f * density, Offset(hubX, hubY))
        drawCircle(ex.hub, 6.5f * density, Offset(hubX, hubY))

        scene.landing?.let { fx -> drawLandingPercents(scene, bodyPaint, density, ex.surface, ex.onSurface, fx) }
    }
}

/**
 * One card: a soft shadow, a white gap, the asset itself (photo, video frame, waveform or the note's text) and a thick
 * rounded outline in the cluster colour. A dot of the cluster colour marks the item's exact spot on the card.
 */
private fun DrawScope.drawCard(
    scene: MapScene, i: Int, left: Float, top: Float, w: Float, h: Float, a: Float, col: Color, outline: Stroke, dotStroke: Stroke,
    ex: Explorer, density: Float, text: CardText, paint: Paint, play: ImageBitmap, dotX: Float, dotY: Float,
    onNeedThumb: (String) -> Unit, onNeedWave: (String) -> Unit, requested: HashSet<String>,
) {
    val item = scene.snapshot.items[i]
    val r = w * 0.14f
    val corner = CornerRadius(r)
    val size = Size(w, h)
    drawRoundRect(Color.Black.copy(alpha = 0.10f * a), Offset(left, top + 2.5f * density), size, corner)
    drawRoundRect(ex.surface.copy(alpha = a), Offset(left, top), size, corner)
    val inset = 3.4f * density
    val iw = (w - 2 * inset).coerceAtLeast(1f)
    val ih = (h - 2 * inset).coerceAtLeast(1f)
    when (item.type) {
        MediaType.IMAGE, MediaType.VIDEO -> {
            val pic = item.thumbPath
            val bmp = if (pic != null) ThumbCache.peekCard(pic) else null
            if (pic != null && bmp == null && requested.add(pic)) onNeedThumb(pic)
            if (bmp != null) {
                drawImage(bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset((left + inset).toInt(), (top + inset).toInt()), IntSize(iw.toInt().coerceAtLeast(1), ih.toInt().coerceAtLeast(1)), alpha = a)
            } else {
                drawRoundRect(col.copy(alpha = 0.16f * a), Offset(left + inset, top + inset), Size(iw, ih), CornerRadius(r * 0.8f))
            }
            if (item.type == MediaType.VIDEO) playGlyph(play, Offset(left + w / 2, top + h / 2), w * 0.14f, col, a)
        }
        MediaType.AUDIO -> {
            drawWave(item.mediaPath, i, left + inset, top + inset, iw, ih, a, col, density, text, onNeedWave, requested)
            playGlyph(play, Offset(left + w * 0.2f, top + h * 0.8f), w * 0.1f, col, a)
        }
        MediaType.TEXT -> drawNote(item.text ?: item.title, i, left + inset, top + inset, iw, ih, a, ex.onSurface, density, text, paint)
    }
    drawRoundRect(col.copy(alpha = a), Offset(left, top), size, corner, style = outline)
    // the dot: the item's exact place in the space, sitting on its card
    drawCircle(Color.White.copy(alpha = a), 6.2f * density, Offset(dotX, dotY))
    drawCircle(col.copy(alpha = a), 4.4f * density, Offset(dotX, dotY))
}

private fun DrawScope.playGlyph(play: ImageBitmap, c: Offset, r: Float, col: Color, a: Float) {
    drawCircle(col.copy(alpha = 0.92f * a), r, c)
    val g = (r * 1.15f).toInt().coerceAtLeast(6)
    drawImage(play, IntOffset.Zero, IntSize(play.width, play.height), IntOffset((c.x - g / 2f + g * 0.06f).toInt(), (c.y - g / 2f).toInt()), IntSize(g, g), alpha = a)
}

private fun DrawScope.drawWave(
    path: String?, i: Int, left: Float, top: Float, w: Float, h: Float, a: Float, col: Color, density: Float, text: CardText,
    onNeedWave: (String) -> Unit, requested: HashSet<String>,
) {
    val real = if (path != null) WaveCache.peek(path) else null
    if (path != null && real == null && requested.add(path)) onNeedWave(path)
    val bars = real ?: WaveCache.placeholder(i, text.wave)
    val pad = w * 0.1f
    val slot = (w - 2 * pad) / bars.size
    val bw = slot * 0.62f
    val mid = top + h * 0.4f
    val maxH = h * 0.55f
    for (b in bars.indices) {
        val bh = (maxH * (0.14f + 0.86f * bars[b])).coerceAtLeast(2.5f * density)
        drawRoundRect(col.copy(alpha = (if (real != null) 0.95f else 0.55f) * a), Offset(left + pad + b * slot + (slot - bw) / 2, mid - bh / 2), Size(bw, bh), CornerRadius(bw / 2))
    }
}

/** The note's real text, wrapped to the card and clipped when it does not fit. */
private fun DrawScope.drawNote(
    body: String, i: Int, left: Float, top: Float, w: Float, h: Float, a: Float, fg: Color, density: Float, text: CardText, paint: Paint,
) {
    val size = min(max(w * 0.115f, 9f * density), 15f * density)
    paint.textSize = size
    val pad = w * 0.08f
    val lineH = size * 1.25f
    val maxLines = max(1, ((h - 2 * pad) / lineH).toInt())
    val bucket = (w / density).toInt() * 64 + maxLines
    var lines = text.lines[i]
    if (lines == null || text.lineBucket[i] != bucket) {
        val out = ArrayList<String>()
        var rest = body.trim()
        while (rest.isNotEmpty() && out.size < maxLines) {
            val fit = paint.breakText(rest, true, w - 2 * pad, null)
            var cut = fit
            if (fit < rest.length) {
                val sp = rest.lastIndexOf(' ', fit)
                if (sp > fit / 2) cut = sp
            }
            var line = rest.substring(0, max(1, cut)).trim()
            rest = rest.substring(max(1, cut)).trim()
            if (out.size == maxLines - 1 && rest.isNotEmpty()) line = line.dropLast(1).trimEnd() + "…"
            out.add(line)
        }
        lines = out.toTypedArray()
        text.lines[i] = lines
        text.lineBucket[i] = bucket
    }
    paint.color = fg.copy(alpha = a).toArgb()
    val nc = drawContext.canvas.nativeCanvas
    for (k in lines.indices) nc.drawText(lines[k], left + pad, top + pad + size * 0.95f + k * lineH, paint)
}

private fun DrawScope.drawLandingPercents(scene: MapScene, paint: Paint, density: Float, pill: Color, textColor: Color, fx: LandingFx) {
    val layout = scene.layout
    val n = scene.count
    if (fx.index !in 0 until n) return
    paint.textSize = 12f * density
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val nc = drawContext.canvas.nativeCanvas
    val alpha = (fx.life * 1.4f).coerceIn(0f, 1f)
    for (k in fx.links.indices) {
        val t = fx.links[k]
        if (t !in 0 until n) continue
        val mx = (layout.cx[fx.index] + layout.cx[t]) / 2
        val my = (layout.cy[fx.index] + layout.cy[t]) / 2
        val label = "${fx.percents[k]}%"
        val w = paint.measureText(label) + 14f * density
        drawRoundRect(pill.copy(alpha = 0.95f * alpha), Offset(mx - w / 2, my - 11f * density), Size(w, 22f * density), CornerRadius(11f * density))
        paint.color = textColor.copy(alpha = alpha).toArgb()
        nc.drawText(label, mx - w / 2 + 7f * density, my + paint.textSize * 0.35f, paint)
    }
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
}
