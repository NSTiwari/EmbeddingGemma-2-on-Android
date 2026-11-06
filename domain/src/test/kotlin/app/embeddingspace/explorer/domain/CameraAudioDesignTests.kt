package app.embeddingspace.explorer.domain

import app.embeddingspace.explorer.domain.audio.Pcm
import app.embeddingspace.explorer.domain.audio.PcmAudio
import app.embeddingspace.explorer.domain.audio.Wav
import app.embeddingspace.explorer.domain.audio.Waveform
import app.embeddingspace.explorer.domain.camera.Camera3D
import app.embeddingspace.explorer.domain.camera.CameraAnimator
import app.embeddingspace.explorer.domain.camera.Easing
import app.embeddingspace.explorer.domain.camera.PositionTween
import app.embeddingspace.explorer.domain.camera.Projection
import app.embeddingspace.explorer.domain.design.ClusterPalette
import app.embeddingspace.explorer.domain.design.Contrast
import app.embeddingspace.explorer.domain.design.ThemeTokens
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class CameraTest {
    private fun cam(): Camera3D = Camera3D().apply { setViewport(720f, 1280f); reset(1f) }

    @Test fun targetProjectsToTheMiddleOfTheView() {
        val c = cam()
        val p = c.projectOne(0f, 0f, 0f)
        assertEquals(360f, p[0], 1e-3f)
        assertEquals(640f, p[1], 1e-3f)
    }

    @Test fun nearerPointsAreBiggerThanFarOnes() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val near = c.projectOne(0f, 0f, -0.5f)
        val far = c.projectOne(0f, 0f, 0.5f)
        assertTrue(near[3] > far[3])
        assertTrue(near[2] < far[2])
    }

    @Test fun movingUpInTheWorldMovesUpOnScreen() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        assertTrue(c.projectOne(0f, 0.5f, 0f)[1] < c.projectOne(0f, 0f, 0f)[1])
    }

    @Test fun yawTurnsTheSceneAroundTheTarget() {
        val c = cam().apply { yaw = (PI / 2).toFloat(); pitch = 0f }
        // a point on the +x axis swings round to face the camera, straight in front of the target
        val p = c.projectOne(0.5f, 0f, 0f)
        assertEquals(360f, p[0], 1f)
        assertTrue(p[2] < c.distance)
    }

    @Test fun rotateByChangesYawAndClampsPitch() {
        val c = cam()
        val y = c.yaw
        c.rotateBy(100f, 0f)
        assertTrue(c.yaw > y)
        c.rotateBy(0f, 100_000f)
        assertEquals(Camera3D.MAX_PITCH, c.pitch, 1e-6f)
    }

    @Test fun zoomInBringsTheCameraCloserWithinLimits() {
        val c = cam()
        val d = c.distance
        c.zoomBy(2f)
        assertEquals(d / 2, c.distance, 1e-5f)
        c.zoomBy(1000f)
        assertEquals(c.minDistance, c.distance, 1e-6f)
        c.zoomBy(0.0001f)
        assertEquals(c.maxDistance, c.distance, 1e-6f)
    }

    @Test fun zoomIgnoresNonsenseFactors() {
        val c = cam()
        val d = c.distance
        c.zoomBy(0f); c.zoomBy(-3f)
        assertEquals(d, c.distance, 0f)
    }

    @Test fun panSlidesThePictureInPixels() {
        val c = cam()
        val before = c.projectOne(0f, 0f, 0f)
        c.panBy(30f, -20f)
        val after = c.projectOne(0f, 0f, 0f)
        assertEquals(before[0] + 30f, after[0], 1e-3f)
        assertEquals(before[1] - 20f, after[1], 1e-3f)
    }

    @Test fun projectFillsEveryPointAndSortsFarToNear() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val pos = floatArrayOf(0f, 0f, -0.5f, 0f, 0f, 0.5f, 0f, 0f, 0f)
        val out = Projection(3)
        c.project(pos, 3, out)
        assertEquals(3, out.count)
        assertEquals(1, out.order[0])
        assertEquals(2, out.order[1])
        assertEquals(0, out.order[2])
        assertTrue(out.minDepth < out.maxDepth)
    }

    @Test fun skippedPointsAreLeftOut() {
        val out = Projection(3)
        cam().project(FloatArray(9), 3, out, booleanArrayOf(false, true, false))
        assertEquals(2, out.count)
        assertTrue(1 !in out.order.take(2))
    }

    @Test fun pointsBehindTheCameraAreDropped() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val out = Projection(2)
        c.project(floatArrayOf(0f, 0f, 0f, 0f, 0f, -10f), 2, out)
        assertEquals(1, out.count)
    }

    @Test fun depth01SpansZeroToOne() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val out = Projection(2)
        c.project(floatArrayOf(0f, 0f, -0.5f, 0f, 0f, 0.5f), 2, out)
        assertEquals(0f, out.depth01(0), 1e-5f)
        assertEquals(1f, out.depth01(1), 1e-5f)
    }

    @Test fun pickReturnsThePointUnderTheFinger() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val pos = floatArrayOf(-0.3f, 0f, 0f, 0.3f, 0f, 0f)
        val out = Projection(2)
        c.project(pos, 2, out)
        val hit = c.pick(out, out.sx[1], out.sy[1], { 20f })
        assertEquals(1, hit)
    }

    @Test fun pickMissesWhenNothingIsNear() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val out = Projection(1)
        c.project(floatArrayOf(0f, 0f, 0f), 1, out)
        assertEquals(-1, c.pick(out, 5f, 5f, { 10f }, slopPx = 10f))
    }

    @Test fun pickPrefersTheNearerOfTwoOverlappingPoints() {
        val c = cam().apply { yaw = 0f; pitch = 0f }
        val out = Projection(2)
        c.project(floatArrayOf(0f, 0f, 0.5f, 0f, 0f, -0.5f), 2, out)
        assertEquals(1, c.pick(out, out.sx[0], out.sy[0], { i -> 10f + out.scale[i] * 0.01f }))
    }

    @Test fun projectIntoMatchesProjectOne() {
        val c = cam().apply { yaw = 0.7f; pitch = 0.2f; panBy(12f, 8f) }
        val a = c.projectOne(0.2f, -0.3f, 0.4f)
        val b = FloatArray(4)
        c.projectInto(0.2f, -0.3f, 0.4f, b)
        assertArrayEquals(a, b, 1e-4f)
    }

    @Test fun resetFramesTheScene() {
        val c = cam()
        c.zoomBy(3f); c.panBy(50f, 50f); c.rotateBy(80f, 40f)
        c.reset(2f, 1f, 2f, 3f)
        assertEquals(0f, c.panX, 0f)
        assertEquals(2f * Camera3D.DEFAULT_DISTANCE_FACTOR, c.distance, 1e-5f)
        assertEquals(2f, c.targetY, 0f)
    }

    /** Micro benchmark of the per-frame work: project and depth sort 1,500 points. */
    @Test fun projectingFifteenHundredPointsIsCheap() {
        val n = 1500
        val pos = FloatArray(n * 3) { ((it * 7919) % 1000) / 100f - 5f }
        val c = cam()
        val out = Projection(n)
        repeat(200) { c.project(pos, n, out) } // warm up
        val frames = 1000
        val t0 = System.nanoTime()
        for (f in 0 until frames) { c.rotateBy(1f, 0f); c.project(pos, n, out) }
        val msPerFrame = (System.nanoTime() - t0) / 1e6 / frames
        println("project + depth sort, %d points: %.3f ms per frame (JVM)".format(n, msPerFrame))
        assertTrue("took $msPerFrame ms", msPerFrame < 8.0)
    }
}

class AnimatorTest {
    private fun cam(): Camera3D = Camera3D().apply { setViewport(720f, 1280f); reset(1f) }

    @Test fun easingStartsAtZeroAndEndsAtOne() {
        assertEquals(0f, Easing.inOutCubic(0f), 1e-6f)
        assertEquals(1f, Easing.inOutCubic(1f), 1e-6f)
        assertEquals(0.5f, Easing.inOutCubic(0.5f), 1e-6f)
        assertEquals(1f, Easing.outCubic(1f), 1e-6f)
        assertEquals(1f, Easing.inOutCubic(7f), 1e-6f)
    }

    @Test fun flyToArrivesAtTheTarget() {
        val c = cam()
        val a = CameraAnimator(c)
        a.flyTo(1f, 2f, 3f, 1.2f, ms = 500)
        repeat(20) { a.step(0.05f) }
        assertEquals(1f, c.targetX, 1e-4f)
        assertEquals(3f, c.targetZ, 1e-4f)
        assertEquals(1.2f, c.distance, 1e-4f)
        assertFalse(a.isFlying)
    }

    @Test fun flyToTakesTheShortWayRoundInYaw() {
        val c = cam().apply { yaw = 3f }
        val a = CameraAnimator(c).apply { spinEnabled = false }
        a.flyTo(0f, 0f, 0f, 2f, yaw = -3f, ms = 100)
        repeat(10) { a.step(0.05f) }
        assertEquals(-3f + (2 * PI).toFloat(), c.yaw, 1e-3f)
    }

    @Test fun touchStopsAFlight() {
        val c = cam()
        val a = CameraAnimator(c)
        a.flyTo(5f, 0f, 0f, 1f)
        a.touch()
        assertFalse(a.isFlying)
    }

    @Test fun idleSpinStartsOnlyAfterAPause() {
        val c = cam()
        val a = CameraAnimator(c)
        a.touch()
        val y = c.yaw
        a.step(0.5f)
        assertEquals(y, c.yaw, 0f)
        repeat(40) { a.step(0.5f) }
        assertTrue(c.yaw > y)
    }

    @Test fun spinCanBeSwitchedOff() {
        val c = cam()
        val a = CameraAnimator(c).apply { spinEnabled = false }
        val y = c.yaw
        repeat(20) { a.step(0.5f) }
        assertEquals(y, c.yaw, 0f)
    }

    @Test fun tweenGlidesToTheTargetAndStops() {
        val t = PositionTween()
        t.setImmediate(floatArrayOf(0f, 0f, 0f))
        t.retarget(floatArrayOf(3f, 6f, 9f), 400)
        assertTrue(t.isMoving)
        repeat(20) { t.step(0.05f) }
        assertArrayEquals(floatArrayOf(3f, 6f, 9f), t.current, 1e-5f)
        assertFalse(t.isMoving)
    }

    @Test fun tweenStartsNewPointsAtTheSpawnPoint() {
        val t = PositionTween()
        t.setImmediate(floatArrayOf(0f, 0f, 0f))
        t.retarget(floatArrayOf(0f, 0f, 0f, 4f, 4f, 4f), 1000, spawn = floatArrayOf(10f, 10f, 10f))
        assertArrayEquals(floatArrayOf(10f, 10f, 10f), t.current.copyOfRange(3, 6), 1e-6f)
    }

    @Test fun tweenRemoveKeepsTheOtherPoints() {
        val t = PositionTween()
        t.setImmediate(floatArrayOf(1f, 1f, 1f, 2f, 2f, 2f, 3f, 3f, 3f))
        t.removeAt(1)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 3f, 3f, 3f), t.current, 0f)
        assertEquals(2, t.count)
    }

    @Test fun tweenWithZeroDurationSnaps() {
        val t = PositionTween()
        t.setImmediate(floatArrayOf(0f, 0f, 0f))
        t.retarget(floatArrayOf(1f, 1f, 1f), 0)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), t.current, 0f)
    }
}

class PcmTest {
    private fun sine(freq: Double, rate: Int, seconds: Double, amp: Int = 12000): ShortArray =
        ShortArray((rate * seconds).toInt()) { (amp * sin(2 * PI * freq * it / rate)).toInt().toShort() }

    @Test fun stereoIsAveragedToMono() {
        val mono = Pcm.interleavedToMono(shortArrayOf(100, 300, -50, 50), 2)
        assertArrayEquals(shortArrayOf(200, 0), mono)
    }

    @Test fun monoPassesThrough() {
        val d = shortArrayOf(1, 2, 3)
        assertTrue(Pcm.interleavedToMono(d, 1) === d)
    }

    @Test fun resampleKeepsTheDuration() {
        val out = Pcm.resample(ShortArray(48_000), 48_000, 16_000)
        assertEquals(16_000, out.size)
    }

    @Test fun resampleUpwardsGrowsTheLength() {
        assertEquals(2000, Pcm.resample(ShortArray(1000), 8000, 16000).size)
    }

    @Test fun resampleToTheSameRateIsANoOp() {
        val d = shortArrayOf(1, 2, 3)
        assertTrue(Pcm.resample(d, 16000, 16000) === d)
    }

    @Test fun downsamplingKeepsALowToneAndKillsAHighOne() {
        val low = Pcm.resample(sine(200.0, 48_000, 0.5), 48_000, 16_000)
        val high = Pcm.resample(sine(20_000.0, 48_000, 0.5), 48_000, 16_000)
        assertTrue("low ${Pcm.peak(low)}", Pcm.peak(low) > 10_000)
        assertTrue("high ${Pcm.peak(high)}", Pcm.peak(high) < 6_000)
    }

    @Test fun upsamplingInterpolatesBetweenSamples() {
        val out = Pcm.resample(shortArrayOf(0, 1000), 8000, 16000)
        assertEquals(0, out[0].toInt())
        assertEquals(500, out[1].toInt())
    }

    @Test(expected = IllegalArgumentException::class) fun zeroRateIsRejected() {
        Pcm.resample(ShortArray(4), 0, 16000)
    }

    @Test fun toModelInputGivesSixteenKilohertzMono() {
        val a = Pcm.toModelInput(ShortArray(44_100 * 2 * 2), 2, 44_100)
        assertEquals(16_000, a.sampleRate)
        assertEquals(2 * 16_000, a.samples.size)
    }

    @Test fun capKeepsTheStart() {
        val a = PcmAudio(ShortArray(16_000 * 10) { it.toShort() }, 16_000)
        val c = Pcm.cap(a, 3)
        assertEquals(48_000, c.samples.size)
        assertEquals(5, c.samples[5].toInt())
    }

    @Test fun capLeavesShortClipsAlone() {
        val a = PcmAudio(ShortArray(100), 16_000)
        assertTrue(Pcm.cap(a, 3) === a)
    }

    @Test fun durationIsInMilliseconds() = assertEquals(1500, PcmAudio(ShortArray(24_000), 16_000).durationMs)

    @Test fun wavRoundTrips() {
        val a = PcmAudio(sine(440.0, 16_000, 0.25), 16_000)
        val back = Wav.parse(Wav.encode(a))!!
        assertEquals(16_000, back.sampleRate)
        assertArrayEquals(a.samples, back.samples)
    }

    @Test fun wavHeaderIsStandard() {
        val b = Wav.encode(PcmAudio(ShortArray(10), 16_000))
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals(44 + 20, b.size)
    }

    @Test fun wavParserRejectsGarbage() {
        assertNull(Wav.parse(ByteArray(10)))
        assertNull(Wav.parse(ByteArray(100)))
    }

    @Test fun wavParserMixesStereoDown() {
        val stereo = Wav.encode(PcmAudio(shortArrayOf(100, 300, 100, 300), 16_000))
        stereo[22] = 2 // channels
        val parsed = Wav.parse(stereo)!!
        assertEquals(2, parsed.samples.size)
        assertEquals(200, parsed.samples[0].toInt())
    }

    @Test fun shippedDemoClipIsSixteenKilohertzTenSeconds() {
        val bytes = PcmTest::class.java.classLoader.getResourceAsStream("media/birdsong_0.wav")!!.use { it.readBytes() }
        val a = Wav.parse(bytes)!!
        assertEquals(16_000, a.sampleRate)
        assertTrue(abs(a.durationMs - 10_000) < 50)
    }

    @Test fun waveformHasOneValueInRangePerBar() {
        val w = Waveform.peaks(sine(300.0, 16_000, 1.0), 40)
        assertEquals(40, w.size)
        assertTrue(w.all { it in 0f..1f })
        assertEquals(1f, w.max(), 1e-6f)
    }

    @Test fun waveformFollowsTheLoudness() {
        val s = ShortArray(16_000) { if (it < 8_000) 1_000 else 20_000 }
        val w = Waveform.peaks(s, 2)
        assertTrue(w[0] < w[1] * 0.2f)
    }

    @Test fun silenceGivesAFlatWaveform() = assertTrue(Waveform.peaks(ShortArray(500), 10).all { it == 0f })

    @Test fun fewerSamplesThanBarsStillWorks() = assertEquals(8, Waveform.peaks(shortArrayOf(5, 6), 8).size)

    @Test fun playheadStaysInsideTheBars() {
        assertEquals(0, Waveform.playheadBar(0, 10_000, 72))
        assertEquals(36, Waveform.playheadBar(5_000, 10_000, 72))
        assertEquals(71, Waveform.playheadBar(99_000, 10_000, 72))
        assertEquals(0, Waveform.playheadBar(500, 0, 72))
    }
}

class ThemeTokensTest {
    @Test fun blackOnWhiteIsTwentyOne() = assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 0.01)

    @Test fun sameColourIsOne() = assertEquals(1.0, Contrast.ratio(0x336699, 0x336699), 1e-9)

    @Test fun luminanceOfWhiteIsOne() = assertEquals(1.0, Contrast.luminance(0xFFFFFF), 1e-9)

    @Test fun overBlendsTowardsTheForeground() {
        assertEquals(0x7F7F7F, Contrast.over(0xFFFFFF, 0x000000, 0.5))
        assertEquals(0xFF0000, Contrast.over(0xFF0000, 0x00FF00, 1.0))
    }

    private fun check(t: ThemeTokens) {
        for ((name, fg, bg) in t.textPairs()) {
            val r = Contrast.ratio(fg, bg)
            assertTrue("${if (t.dark) "dark" else "light"} $name is %.2f".format(r), r >= 4.5)
        }
    }

    @Test fun darkThemeTextMeetsContrastRules() = check(ThemeTokens.Dark)

    @Test fun lightThemeTextMeetsContrastRules() = check(ThemeTokens.Light)

    @Test fun primaryButtonsAreReadableInBothThemes() {
        assertTrue(Contrast.ratio(ThemeTokens.Dark.onPrimary, ThemeTokens.Dark.primary) >= 4.5)
        assertTrue(Contrast.ratio(ThemeTokens.Light.onPrimary, ThemeTokens.Light.primary) >= 4.5)
    }

    @Test fun paletteHasAtLeastTwelveDistinctHues() {
        for (set in listOf(ClusterPalette.CARD_DARK, ClusterPalette.CARD_LIGHT)) {
            assertTrue(set.size >= 12)
            assertEquals(set.size, set.toSet().size)
            assertTrue("min distance ${ClusterPalette.minDistance(set)}", ClusterPalette.minDistance(set) > 35.0)
        }
    }

    @Test fun outlinesStandOutFromTheBackgroundInBothThemes() {
        // thick outlines of flat, medium-bright colours: 3 to 1 on the dark map, 1.7 to 1 or more on the pale one
        for (c in ClusterPalette.CARD_DARK) assertTrue("%06X".format(c), Contrast.ratio(c, ThemeTokens.Dark.bgBottom) >= 3.0)
        for (c in ClusterPalette.CARD_LIGHT) assertTrue("%06X".format(c), Contrast.ratio(c, ThemeTokens.Light.bgTop) >= 1.7)
    }

    @Test fun theFirstThreeClusterColoursAreBlueGreenAndSalmon() {
        assertEquals(listOf(0x4A90D9, 0x00B050, 0xF4A582), ClusterPalette.CARD_LIGHT.take(3).toList())
    }

    @Test fun theDarkPaletteKeepsTheHuesButIsLighter() {
        for (i in 0 until 12) assertTrue(Contrast.luminance(ClusterPalette.CARD_DARK[i]) >= Contrast.luminance(ClusterPalette.CARD_LIGHT[i]) - 1e-9)
    }

    @Test fun textOnAClusterColourChipIsReadable() {
        for (c in ClusterPalette.CARD_LIGHT + ClusterPalette.CARD_DARK) assertTrue("%06X".format(c), Contrast.ratio(Contrast.onColor(c), c) >= 3.0)
    }

    @Test fun paletteFollowsTheTheme() {
        assertTrue(ClusterPalette.colors(true) === ClusterPalette.CARD_DARK)
        assertTrue(ClusterPalette.colors(false) === ClusterPalette.CARD_LIGHT)
    }

    @Test fun lightAndDarkAreDifferentThemes() {
        assertTrue(ThemeTokens.Dark.dark && !ThemeTokens.Light.dark)
        assertTrue(abs(Contrast.luminance(ThemeTokens.Dark.bgTop) - Contrast.luminance(ThemeTokens.Light.bgTop)) > 0.5)
    }
}
