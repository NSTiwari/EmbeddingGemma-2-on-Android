package app.embeddingspace.explorer.screens

import app.cash.paparazzi.Paparazzi
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.data.TilesUi
import app.embeddingspace.explorer.data.ViewMode
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Every screen at 720x1600 (360x800 dp). The PNGs land in app/build/screens. */
class PhoneScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PHONE_720,
        theme = "android:Theme.Material.Light.NoActionBar",
        snapshotHandler = fullSizeSnapshotHandler(File("build/screens")),
        useDeviceResolution = true,
    )

    @Test fun mapLight() = paparazzi.snapshot { AppScreens.Map(false) }
    @Test fun mapDark() = paparazzi.snapshot { AppScreens.Map(true) }
    @Test fun mapDemoHintLight() = paparazzi.snapshot { AppScreens.Map(false, tweak = { it.copy(demoHint = true) }) }
    @Test fun mapEmptyLight() = paparazzi.snapshot { AppScreens.Map(false, empty = true) }
    @Test fun mapEmptyDark() = paparazzi.snapshot { AppScreens.Map(true, empty = true) }

    @Test fun tilesMatchLight() = paparazzi.snapshot {
        AppScreens.Map(false, tweak = { it.copy(viewMode = ViewMode.TILES, tiles = DemoFixture.tiles("train"), query = "train") })
    }
    @Test fun tilesMatchDark() = paparazzi.snapshot {
        AppScreens.Map(true, tweak = { it.copy(viewMode = ViewMode.TILES, tiles = DemoFixture.tiles("beach"), query = "beach") })
    }
    @Test fun tilesGroupsLight() = paparazzi.snapshot {
        AppScreens.Map(false, tweak = { it.copy(viewMode = ViewMode.TILES, tiles = DemoFixture.tiles(null)) })
    }
    @Test fun tilesNoMatchLight() = paparazzi.snapshot {
        AppScreens.Map(false, tweak = { it.copy(viewMode = ViewMode.TILES, tiles = TilesUi.NoMatch, query = "xylophone") })
    }

    @Test fun previewImageLight() = paparazzi.snapshot { AppScreens.Map(false, "demo-img-beach_waves_0", page = 0) }
    @Test fun previewImagePage3Dark() = paparazzi.snapshot { AppScreens.Map(true, "demo-img-beach_waves_0", page = 2) }
    @Test fun previewAudioLight() = paparazzi.snapshot { AppScreens.Map(false, "demo-aud-birdsong_0") }
    @Test fun previewNoteDark() = paparazzi.snapshot { AppScreens.Map(true, "demo-txt-train-1") }

    @Test fun mapLandedLight() = paparazzi.snapshot {
        AppScreens.Map(false, tweak = { it.copy(landed = AppScreens.landed(), jobs = AppScreens.jobs()).also { s -> AppScreens.landing(s.scene) } })
    }

    @Test fun mapSearchLight() = paparazzi.snapshot {
        AppScreens.Map(false, tweak = { s ->
            val g = FloatArray(s.scene.count)
            val cluster = DemoFixture.item("demo-img-train_0").clusterId
            s.scene.snapshot.items.forEachIndexed { i, it -> if (it.clusterId == cluster) g[i] = 1f }
            s.scene.glow = g
            s.copy(query = "train", searchHits = g.count { it > 0f })
        })
    }

    @Test fun addSheetLight() = paparazzi.snapshot { AppScreens.Add(false) }
    @Test fun addNoteLight() = paparazzi.snapshot { AppScreens.Add(false, note = true) }
    @Test fun recordingDark() = paparazzi.snapshot { AppScreens.Recording(true) }
    @Test fun downloadLight() = paparazzi.snapshot { AppScreens.Download(false, ModelState.Missing) }
    @Test fun downloadProgressDark() = paparazzi.snapshot { AppScreens.Download(true, ModelState.Downloading(212_000_000, 484_622_336)) }
    @Test fun settingsLight() = paparazzi.snapshot { AppScreens.Settings(false) }
    @Test fun settingsDark() = paparazzi.snapshot { AppScreens.Settings(true, ModelState.Missing) }
    @Test fun aboutLight() = paparazzi.snapshot { AppScreens.About(false) }
}
