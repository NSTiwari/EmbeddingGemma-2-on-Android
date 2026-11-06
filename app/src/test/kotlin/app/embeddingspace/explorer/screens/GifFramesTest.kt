package app.embeddingspace.explorer.screens

import app.cash.paparazzi.Paparazzi
import app.embeddingspace.explorer.data.ViewMode
import app.embeddingspace.explorer.domain.camera.Camera3D
import app.embeddingspace.explorer.ui.map.MapActions
import app.embeddingspace.explorer.ui.map.MapScreen
import app.embeddingspace.explorer.ui.map.MapScreenState
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Frames for docs/demo.gif, rendered from the real screens and demo data.
 * They land in app/build/gif-frames as f000.png, f001.png and so on. Hold frames are plain copies.
 */
class GifFramesTest {
    private val dir = File("build/gif-frames")
    private var name = "f000"

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PHONE_720,
        theme = "android:Theme.Material.Light.NoActionBar",
        snapshotHandler = fullSizeSnapshotHandler(dir) { name },
        useDeviceResolution = true,
    )

    private var next = 0

    /** Renders one frame and repeats it `hold` times in total. */
    private fun shot(hold: Int = 1, content: @androidx.compose.runtime.Composable () -> Unit) {
        name = "f%03d".format(next)
        paparazzi.snapshot { content() }
        val first = File(dir, "$name.png")
        next++
        repeat(hold - 1) { first.copyTo(File(dir, "f%03d.png".format(next++)), overwrite = true) }
    }

    @Test fun frames() {
        dir.deleteRecursively()
        dir.mkdirs()
        val scene = DemoFixture.scene()
        val actions = MapActions()
        val base = Camera3D.DEFAULT_YAW

        // a few throwaway renders so the card layout settles before the first real frame
        name = "warm"
        repeat(8) { paparazzi.snapshot { AppScreens.Frame(false) { MapScreen(MapScreenState(scene), actions, AppScreens.stillMedia) } } }

        // the map swaying left and right, so the hub and the spokes show in 3D. It ends where it began.
        val sway = 30
        for (k in 0 until sway) {
            scene.camera.yaw = base + 0.6f * sin(2.0 * PI * k / sway).toFloat()
            scene.tick++
            shot { AppScreens.Frame(false) { MapScreen(MapScreenState(scene), actions, AppScreens.stillMedia) } }
        }

        // the search "train": the matching cluster lights up
        scene.camera.yaw = base
        val cluster = DemoFixture.item("demo-img-train_0").clusterId
        val glow = FloatArray(scene.count)
        scene.snapshot.items.forEachIndexed { i, it -> if (it.clusterId == cluster) glow[i] = 1f }
        scene.glow = glow
        val word = "train"
        val found = MapScreenState(scene, query = word, searchHits = glow.count { it > 0f })
        shot(hold = 8) { AppScreens.Frame(false) { MapScreen(found, actions, AppScreens.stillMedia) } }
        scene.glow = null

        // the Tiles view with the same query
        val tiles = MapScreenState(scene, viewMode = ViewMode.TILES, tiles = DemoFixture.tiles("train"), query = word)
        shot(hold = 30) { AppScreens.Frame(false) { MapScreen(tiles, actions, AppScreens.stillMedia) } }

        // the preview pager on a photo, then the next card in the pager
        shot(hold = 16) { AppScreens.Map(false, "demo-img-train_0", page = 0) }
        shot(hold = 16) { AppScreens.Map(false, "demo-img-train_0", page = 1) }

        // back to the map: the first frame again, so the loop is clean
        val start = File(dir, "f000.png")
        repeat(3) { start.copyTo(File(dir, "f%03d.png".format(next++)), overwrite = true) }
    }
}
