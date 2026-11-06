package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.camera.AutoFit
import app.embeddingspace.explorer.domain.camera.Camera3D
import app.embeddingspace.explorer.domain.camera.CardLayout
import app.embeddingspace.explorer.domain.camera.CardParams
import app.embeddingspace.explorer.domain.camera.CardSizing
import app.embeddingspace.explorer.domain.camera.LineFade
import app.embeddingspace.explorer.domain.camera.Projection
import app.embeddingspace.explorer.domain.layout.LayoutShape
import app.embeddingspace.explorer.domain.layout.UmapLite
import app.embeddingspace.explorer.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class CardLayoutTest {
    private val viewW = 720f
    private val viewH = 1000f

    /** A cloud of [n] points in a ball of radius 1 (deterministic). */
    private fun cloud(n: Int): FloatArray {
        val r = UmapLite.Rng(9)
        val out = FloatArray(n * 3)
        var i = 0
        while (i < n) {
            val x = r.nextFloat() * 2 - 1
            val y = r.nextFloat() * 2 - 1
            val z = r.nextFloat() * 2 - 1
            if (x * x + y * y + z * z > 1f) continue
            out[i * 3] = x; out[i * 3 + 1] = y; out[i * 3 + 2] = z
            i++
        }
        return out
    }

    private class Scene(val cam: Camera3D, val proj: Projection, val layout: CardLayout, val p: CardParams, val n: Int, val pos: FloatArray, val fitD: Float)

    /** The way the view runs it: size from the item count, camera fitted to all cards, a few frames to settle. */
    private fun scene(
        n: Int,
        small: BooleanArray? = if (n == 18) BooleanArray(18) { it >= 12 } else null,
        frames: Int = 12,
        pos: FloatArray = if (n == 18) ShippedDemo.space().positions() else cloud(n),
    ): Scene {
        val cam = Camera3D().also { it.setViewport(viewW, viewH) }
        val hero = CardSizing.heroWidth(viewW, viewH, n, 24f)
        var radius = 1f
        for (i in 0 until n) radius = max(radius, Math.sqrt((pos[i * 3] * pos[i * 3] + pos[i * 3 + 1] * pos[i * 3 + 1] + pos[i * 3 + 2] * pos[i * 3 + 2]).toDouble()).toFloat())
        cam.reset(radius)
        val fit = AutoFit.distance(cam, pos, n, 0f, 0f, 0f, radius, hero * 0.6f, hero * 0.5f)
        cam.distance = fit
        val p = CardParams(hero * fit / cam.focal, hero * 0.5f, hero * 2.6f, 0.8f, gap = -0.08f * hero)
        val proj = Projection(n)
        val layout = CardLayout()
        cam.project(pos, n, proj)
        repeat(frames) { layout.layout(proj, n, p, small, viewW, viewH) }
        return Scene(cam, proj, layout, p, n, pos, fit)
    }

    @Test fun everyItemIsItsOwnCard() {
        val s = scene(18)
        assertEquals(18, s.layout.count + s.layout.skipped)
        assertEquals(18, s.layout.order.take(s.layout.count).toSet().size + s.layout.skipped)
    }

    @Test fun aHandfulOfPhotosGetBigCardsAtTheDefaultView() {
        val s = scene(18)
        val photos = (0 until 12).map { s.layout.w[it] }
        assertTrue("min ${photos.min()}", photos.min() >= 110f)
        assertTrue("max ${photos.max()}", photos.max() <= 0.26f * viewW * 1.4f)
    }

    @Test fun heroWidthIsAboutAQuarterOfTheScreenForFewItemsAndShrinksForMany() {
        val few = CardSizing.heroWidth(720f, 1000f, 12, 20f)
        assertTrue("few $few", few in 110f..150f * 1.3f)
        assertTrue(CardSizing.heroWidth(720f, 1000f, 200, 20f) < few / 2)
        assertTrue(CardSizing.heroWidth(1080f, 1500f, 12, 20f) > few)
    }

    @Test fun heroWidthNeverDropsBelowTheMinimum() = assertEquals(20f, CardSizing.heroWidth(720f, 1000f, 100_000, 20f), 0f)

    @Test fun soundAndNoteCardsAreSmallerThanPhotos() {
        val n = 18
        val small = BooleanArray(n) { it >= 12 }
        val s = scene(n, small)
        val photo = (0 until 12).map { s.layout.w[it] }.average()
        val note = (12 until n).map { s.layout.w[it] }.average()
        assertTrue("photo $photo note $note", note < photo * 0.8)
    }

    @Test fun cardsOnlyOverlapALittle() {
        val s = scene(18)
        for (a in 0 until 18) for (b in a + 1 until 18) {
            val ox = (s.layout.w[a] + s.layout.w[b]) / 2 - abs(s.layout.cx[a] - s.layout.cx[b])
            val oy = (s.layout.h[a] + s.layout.h[b]) / 2 - abs(s.layout.cy[a] - s.layout.cy[b])
            if (ox > 0 && oy > 0) assertTrue("cards $a and $b overlap by $ox x $oy", minOf(ox, oy) <= 0.5f * minOf(s.layout.w[a], s.layout.w[b]))
        }
    }

    @Test fun theDotOfEveryItemStaysOnItsCard() {
        val s = scene(40)
        for (i in 0 until 40) {
            assertTrue(abs(s.proj.sx[i] - s.layout.cx[i]) <= s.layout.w[i] / 2)
            assertTrue(abs(s.proj.sy[i] - s.layout.cy[i]) <= s.layout.h[i] / 2)
        }
    }

    @Test fun cardsStayInsideTheViewAtTheFittedZoom() {
        val s = scene(18)
        for (i in 0 until 18) {
            assertTrue("card $i x ${s.layout.cx[i]} w ${s.layout.w[i]}", s.layout.cx[i] - s.layout.w[i] / 2 >= -0.5f && s.layout.cx[i] + s.layout.w[i] / 2 <= viewW + 0.5f)
            assertTrue("card $i y ${s.layout.cy[i]} h ${s.layout.h[i]} dotY ${s.proj.sy[i]} fit ${s.fitD} cam ${s.cam.distance} maxpos ${s.pos.maxOrNull()} minpos ${s.pos.minOrNull()} worldW ${s.p.worldW} focal ${s.cam.focal} vh ${s.cam.viewH}", s.layout.cy[i] - s.layout.h[i] / 2 >= -0.5f && s.layout.cy[i] + s.layout.h[i] / 2 <= viewH + 0.5f)
        }
    }

    @Test fun nearerCardsAreLargerAndDrawnLast() {
        val s = scene(18)
        val order = s.layout.order.take(s.layout.count)
        assertTrue(s.layout.w[order.last()] >= s.layout.w[order.first()])
    }

    @Test fun zoomingInMakesCardsLarger() {
        val s = scene(18)
        val i = s.layout.order[s.layout.count - 1]
        val before = s.layout.w[i]
        s.cam.zoomBy(2f)
        s.cam.project(s.pos, s.n, s.proj)
        s.layout.layout(s.proj, s.n, s.p, BooleanArray(18) { it >= 12 }, viewW, viewH)
        assertTrue("before $before after ${s.layout.w[s.layout.order[s.layout.count - 1]]}", s.layout.w[s.layout.order[s.layout.count - 1]] > before)
    }

    @Test fun autoFitContainsEveryCard() {
        val n = 150
        val pos = cloud(n)
        val cam = Camera3D().also { it.setViewport(viewW, viewH); it.reset(1f) }
        cam.distance = AutoFit.distance(cam, pos, n, 0f, 0f, 0f, 1f, 40f, 33f)
        val out = FloatArray(4)
        for (i in 0 until n) {
            cam.projectInto(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], out)
            assertTrue(out[0] - 40f >= 0f && out[0] + 40f <= viewW && out[1] - 33f >= 0f && out[1] + 33f <= viewH)
        }
    }

    @Test fun autoFitStandsFartherForBiggerCards() {
        val pos = cloud(100)
        val cam = Camera3D().also { it.setViewport(viewW, viewH); it.reset(1f) }
        assertTrue(AutoFit.distance(cam, pos, 100, 0f, 0f, 0f, 1f, 80f, 66f) > AutoFit.distance(cam, pos, 100, 0f, 0f, 0f, 1f, 20f, 16f))
    }

    @Test fun pickFindsTheNearestCardUnderTheFinger() {
        val s = scene(18)
        val i = s.layout.order[s.layout.count - 1]
        assertEquals(i, s.layout.pick(s.layout.cx[i], s.layout.cy[i], 0f))
        assertEquals(-1, s.layout.pick(-900f, -900f, 5f))
    }

    @Test fun layoutIsSteadyFromFrameToFrame() {
        val s = scene(18, frames = 60)
        val before = FloatArray(18) { s.layout.cx[it] }
        s.cam.project(s.pos, 18, s.proj)
        s.layout.layout(s.proj, 18, s.p, BooleanArray(18) { it >= 12 }, viewW, viewH)
        var moved = 0f
        for (i in 0 until 18) moved += abs(s.layout.cx[i] - before[i])
        assertTrue("mean move ${moved / 18}", moved / 18 < 1f)
    }

    @Test fun fifteenHundredCardsLayOutQuickly() {
        val n = 1500
        val pos = cloud(n)
        val cam = Camera3D().also { it.setViewport(viewW, viewH); it.reset(1f) }
        val hero = CardSizing.heroWidth(viewW, viewH, n, 14f)
        cam.distance = AutoFit.distance(cam, pos, n, 0f, 0f, 0f, 1f, hero * 0.6f, hero * 0.5f)
        val p = CardParams(hero * cam.distance / cam.focal, hero * 0.5f, hero * 2.6f, 0.8f, gap = -0.1f * hero)
        val layout = CardLayout()
        val proj = Projection(n)
        repeat(60) { cam.rotateBy(1f, 0f); cam.project(pos, n, proj); layout.layout(proj, n, p, null, viewW, viewH) }
        val frames = 300
        val t0 = System.nanoTime()
        for (f in 0 until frames) { cam.rotateBy(1f, 0f); cam.project(pos, n, proj); layout.layout(proj, n, p, null, viewW, viewH) }
        val ms = (System.nanoTime() - t0) / 1e6 / frames
        println("project + card layout, %d cards: %.3f ms per frame (JVM)".format(n, ms))
        assertEquals(n, layout.count + layout.skipped)
        assertTrue("took $ms ms", ms < 16.0)
    }

    @Test fun cardsOutsideTheViewAreNotPulledInToItsEdge() {
        val n = 60
        val pos = FloatArray(n * 3) { k -> if (k % 3 == 0) ((k / 3) % 10 - 5) * 0.3f else if (k % 3 == 1) ((k / 3) / 10 - 3) * 0.3f else 0f }
        val cam = Camera3D().apply { setViewport(720f, 900f); reset(1f); yaw = 0f; pitch = 0f; zoomBy(3f) }
        val proj = Projection(n)
        cam.project(pos, n, proj)
        val layout = CardLayout()
        val p = CardParams(0.4f, 70f, 240f, 0.8f, gap = -10f)
        repeat(6) { layout.layout(proj, n, p, null, 720f, 900f) }
        val outside = (0 until n).filter { proj.sx[it] < -200f || proj.sx[it] > 920f }
        assertTrue(outside.isNotEmpty())
        for (i in outside) assertTrue("card $i was pulled in", layout.cx[i] < -50f || layout.cx[i] > 770f)
    }
}

class SpokesAndHubTest {
    @Test fun theOriginIsTheMiddleOfTheViewWhenTheCameraLooksAtIt() {
        val cam = Camera3D().apply { setViewport(720f, 1000f); reset(1f) }
        val p = cam.projectOne(0f, 0f, 0f)
        assertEquals(360f, p[0], 0.01f)
        assertEquals(500f, p[1], 0.01f)
    }

    @Test fun theOriginMovesWithTheCameraWhenYouPan() {
        val cam = Camera3D().apply { setViewport(720f, 1000f); reset(1f) }
        cam.panBy(40f, -30f)
        val p = cam.projectOne(0f, 0f, 0f)
        assertEquals(400f, p[0], 0.01f)
        assertEquals(470f, p[1], 0.01f)
    }

    @Test fun theOriginStaysPutWhenTheViewIsRotated() {
        val cam = Camera3D().apply { setViewport(720f, 1000f); reset(1f) }
        cam.rotateBy(120f, 60f)
        val p = cam.projectOne(0f, 0f, 0f)
        assertEquals(360f, p[0], 0.01f)
        assertEquals(500f, p[1], 0.01f)
    }

    @Test fun aRecentredLayoutHasItsMeanAtTheOrigin() {
        val pos = floatArrayOf(5f, 5f, 5f, 7f, 5f, 3f, 9f, 11f, 1f)
        LayoutShape.recentre(pos)
        for (k in 0 until 3) assertEquals(0f, (pos[k] + pos[3 + k] + pos[6 + k]) / 3, 1e-5f)
    }

    @Test fun shippedDemoLayoutIsCentredOnTheOrigin() {
        val s = ShippedDemo.space()
        for (k in 0 until 3) {
            val m = s.entries.map { listOf(it.x, it.y, it.z)[k] }.average()
            assertEquals(0.0, m, 0.05)
        }
    }

    @Test fun spokesAreClearForAFewItems() {
        assertEquals(0.5f, LineFade.alpha(18, relevant = false, dragging = false), 0f)
        assertEquals(0.5f, LineFade.alpha(25, relevant = false, dragging = false), 0f)
    }

    @Test fun spokesAreVeryFaintBetween26And60() {
        val a = LineFade.alpha(26, false, false)
        assertTrue(a in 0.05f..0.2f)
        assertEquals(a, LineFade.alpha(60, false, false), 0f)
    }

    @Test fun aboveSixtyOnlyTheSelectedClusterHasSpokes() {
        assertEquals(0f, LineFade.alpha(61, false, false), 0f)
        assertTrue(LineFade.alpha(61, true, false) > 0.5f)
    }

    @Test fun whileDraggingBigMapsGetBarelyThereSpokes() {
        val a = LineFade.alpha(200, false, true)
        assertTrue(a > 0f && a < 0.1f)
    }

    @Test fun theSelectedClusterIsBrighterThanTheRestAtEverySize() {
        for (n in listOf(10, 40, 100)) assertTrue(LineFade.alpha(n, true, false) > LineFade.alpha(n, false, false))
    }

    @Test fun separatingClustersKeepsTheMeanAndTheSize() {
        val pos = floatArrayOf(-1f, 0f, 0f, -1.2f, 0.1f, 0f, 1f, 0f, 0f, 1.1f, -0.1f, 0f)
        val labels = intArrayOf(0, 0, 1, 1)
        fun radius(p: FloatArray) = (0 until 4).map { Math.sqrt((p[it * 3] * p[it * 3] + p[it * 3 + 1] * p[it * 3 + 1]).toDouble()) }.average()
        val before = radius(pos.copyOf().also { LayoutShape.recentre(it) })
        val out = LayoutShape.separate(pos.copyOf(), labels, 2f)
        assertEquals(before, radius(out), 1e-3)
        assertEquals(0f, (out[0] + out[3] + out[6] + out[9]) / 4, 1e-4f)
    }

    @Test fun separatingClustersMakesTheGapBiggerComparedToTheirSpread() {
        val pos = floatArrayOf(-1f, 0f, 0f, -1.2f, 0.1f, 0f, 1f, 0f, 0f, 1.1f, -0.1f, 0f)
        val labels = intArrayOf(0, 0, 1, 1)
        fun ratio(p: FloatArray): Float {
            val gap = abs((p[0] + p[3]) / 2 - (p[6] + p[9]) / 2)
            val spread = abs(p[0] - p[3])
            return gap / max(spread, 1e-6f)
        }
        assertTrue(ratio(LayoutShape.separate(pos.copyOf(), labels, 2f, 1f)) > ratio(pos) * 1.5f)
    }

    @Test fun spacingOutPushesCloseItemsApartAndKeepsFarOnesPut() {
        val pos = floatArrayOf(0f, 0f, 0f, 0.1f, 0f, 0f, 9f, 0f, 0f)
        val out = LayoutShape.spaceOut(pos.copyOf(), 1f)
        fun d(a: Int, b: Int) = Math.sqrt(((out[a * 3] - out[b * 3]) * (out[a * 3] - out[b * 3]) + (out[a * 3 + 1] - out[b * 3 + 1]) * (out[a * 3 + 1] - out[b * 3 + 1])).toDouble())
        assertTrue("d01 ${d(0, 1)}", d(0, 1) > 0.8)
        assertTrue(d(0, 2) > 8.0)
    }

    @Test fun spacingOutNeverCollapsesThePoints() {
        val pos = FloatArray(30) { 0.01f * (it % 7) }
        val out = LayoutShape.spaceOut(pos, 0.5f)
        for (a in 0 until 10) for (b in a + 1 until 10) {
            val d = Math.sqrt(((out[a * 3] - out[b * 3]) * (out[a * 3] - out[b * 3]) + (out[a * 3 + 1] - out[b * 3 + 1]) * (out[a * 3 + 1] - out[b * 3 + 1]) + (out[a * 3 + 2] - out[b * 3 + 2]) * (out[a * 3 + 2] - out[b * 3 + 2])).toDouble())
            assertTrue("pair $a $b at $d", d > 0.1)
        }
    }

    @Test fun mediaTypesOfSmallCardsAreSoundAndNotes() {
        val small = setOf(MediaType.AUDIO, MediaType.TEXT)
        assertFalse(MediaType.IMAGE in small || MediaType.VIDEO in small)
    }
}

class ShippedDemoTest {
    private val manifest = ShippedDemo.manifest

    @Test fun theDemoHasEighteenItems() = assertEquals(18, manifest.items.size)

    @Test fun twelvePhotosThreeSoundsThreeNotes() {
        val c = manifest.items.groupingBy { it.type }.eachCount()
        assertEquals(12, c["image"]); assertEquals(3, c["audio"]); assertEquals(3, c["text"]); assertEquals(null, c["video"])
    }

    @Test fun vectorsMatchTheItems() = assertEquals(18, ShippedDemo.vectors.size)

    @Test fun everyMediaFileAndThumbnailIsShipped() {
        for (it in manifest.items) {
            it.media?.let { m -> assertTrue(m, ShippedDemo.exists(m)) }
            it.thumb?.let { t -> assertTrue(t, ShippedDemo.exists(t)) }
        }
    }

    @Test fun everyPhotoHasARealPictureAndEveryMediaItemACredit() {
        for (it in manifest.items.filter { i -> i.type != "text" }) {
            assertTrue(it.id, it.license.isNotBlank() && it.licenseUrl.isNotBlank() && it.media != null)
            if (it.type == "image") assertTrue(it.id, it.thumb != null)
        }
    }

    @Test fun theDemoFallsIntoFiveOrSixClusters() {
        val k = manifest.items.map { it.cluster }.toSet().size
        assertTrue("clusters $k", k in 4..7)
        assertEquals(k, manifest.clusters.size)
    }

    @Test fun noClusterIsTiny() {
        val sizes = manifest.items.groupingBy { it.cluster }.eachCount().values
        assertTrue(sizes.all { it >= 2 })
    }

    @Test fun clustersAreWellSeparated() {
        val s = ShippedDemo.space()
        val by = s.entries.groupBy { it.clusterId }
        fun centre(l: List<app.embeddingspace.explorer.domain.model.SpaceEntry>) = floatArrayOf(l.map { it.x }.average().toFloat(), l.map { it.y }.average().toFloat(), l.map { it.z }.average().toFloat())
        fun spread(l: List<app.embeddingspace.explorer.domain.model.SpaceEntry>): Float {
            val c = centre(l)
            return l.map { Math.sqrt(((it.x - c[0]) * (it.x - c[0]) + (it.y - c[1]) * (it.y - c[1]) + (it.z - c[2]) * (it.z - c[2])).toDouble()).toFloat() }.average().toFloat()
        }
        val cs = by.values.toList()
        var closest = Float.MAX_VALUE
        for (a in cs.indices) for (b in a + 1 until cs.size) {
            val ca = centre(cs[a]); val cb = centre(cs[b])
            val d = Math.sqrt(((ca[0] - cb[0]) * (ca[0] - cb[0]) + (ca[1] - cb[1]) * (ca[1] - cb[1]) + (ca[2] - cb[2]) * (ca[2] - cb[2])).toDouble()).toFloat()
            closest = minOf(closest, d / (spread(cs[a]) + spread(cs[b]) + 1e-3f))
        }
        assertTrue("gap to spread $closest", closest > 0.5f)
    }

    @Test fun noTwoDemoItemsSitOnTopOfEachOther() {
        val s = ShippedDemo.space()
        var radius = 0f
        for (e in s.entries) radius = maxOf(radius, Math.sqrt((e.x * e.x + e.y * e.y + e.z * e.z).toDouble()).toFloat())
        for (a in s.entries.indices) for (b in a + 1 until s.entries.size) {
            val x = s.entries[a]; val y = s.entries[b]
            val d = Math.sqrt(((x.x - y.x) * (x.x - y.x) + (x.y - y.y) * (x.y - y.y) + (x.z - y.z) * (x.z - y.z)).toDouble())
            assertTrue("items $a and $b at $d of radius $radius", d > 0.2 * radius)
        }
    }

    @Test fun theClustersAreNamedAndHaveDistinctColours() {
        assertTrue(manifest.clusters.all { it.name.isNotBlank() })
        assertEquals(manifest.clusters.size, manifest.clusters.map { it.color }.toSet().size)
    }
}
