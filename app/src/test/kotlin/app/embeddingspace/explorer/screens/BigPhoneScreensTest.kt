package app.embeddingspace.explorer.screens

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The main screens again at 1080x2340. */
class BigPhoneScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PHONE_1080,
        theme = "android:Theme.Material.Light.NoActionBar",
        snapshotHandler = fullSizeSnapshotHandler(File("build/screens")),
        useDeviceResolution = true,
    )

    @Test fun big_mapDark() = paparazzi.snapshot { AppScreens.Map(true, "demo-img-beach_waves_0") }
    @Test fun big_mapLight() = paparazzi.snapshot { AppScreens.Map(false) }
    @Test fun big_settingsLight() = paparazzi.snapshot { AppScreens.Settings(false) }
}

/** Landscape must not break. */
class LandscapeScreensTest {
    @get:org.junit.Rule
    val paparazzi = Paparazzi(
        deviceConfig = PHONE_LANDSCAPE,
        theme = "android:Theme.Material.Light.NoActionBar",
        snapshotHandler = fullSizeSnapshotHandler(File("build/screens")),
        useDeviceResolution = true,
    )

    @Test fun land_mapDark() = paparazzi.snapshot { AppScreens.Map(true, compact = true) }
    @Test fun land_previewLight() = paparazzi.snapshot { AppScreens.Map(false, "demo-img-beach_waves_0", compact = true) }
    @Test fun land_settingsDark() = paparazzi.snapshot { AppScreens.Settings(true) }
}
