package app.embeddingspace.explorer.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.Tab
import app.embeddingspace.explorer.data.AppSettings
import app.embeddingspace.explorer.data.LandedUi
import app.embeddingspace.explorer.data.LinkUi
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.data.ThemeMode
import app.embeddingspace.explorer.domain.queue.Job
import app.embeddingspace.explorer.domain.queue.JobKind
import app.embeddingspace.explorer.domain.queue.Stage
import app.embeddingspace.explorer.ui.DockBar
import app.embeddingspace.explorer.ui.add.AddActions
import app.embeddingspace.explorer.ui.add.AddSheetContent
import app.embeddingspace.explorer.ui.add.ModelDownloadScreen
import app.embeddingspace.explorer.ui.add.RecordingContent
import app.embeddingspace.explorer.ui.map.LandingFx
import app.embeddingspace.explorer.ui.map.MapActions
import app.embeddingspace.explorer.ui.map.MapScreen
import app.embeddingspace.explorer.ui.map.MapScreenState
import app.embeddingspace.explorer.ui.preview.AudioPreviewContent
import app.embeddingspace.explorer.ui.preview.PreviewMedia
import app.embeddingspace.explorer.ui.preview.VideoPreviewStill
import app.embeddingspace.explorer.ui.settings.AboutScreen
import app.embeddingspace.explorer.ui.settings.SettingsActions
import app.embeddingspace.explorer.ui.settings.SettingsScreen
import app.embeddingspace.explorer.ui.theme.ExplorerTheme
import app.embeddingspace.explorer.ui.theme.xc

/** Every screen, built from the real demo data. */
object AppScreens {
    /** Still versions of the players, since no media player runs in a screenshot. */
    val stillMedia = PreviewMedia(
        audio = { item, tint -> AudioPreviewContent(DemoFixture.peaks(item.id), 0.38f, true, 10_000, tint, onToggle = {}) },
        video = { item, tint -> VideoPreviewStill(item, tint) },
    )

    @Composable
    fun Frame(dark: Boolean, tab: Tab = Tab.MAP, compact: Boolean = false, content: @Composable () -> Unit) {
        ExplorerTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(xc.background)) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    DockBar(tab, {}, {}, compact = compact)
                }
            }
        }
    }

    @Composable
    fun Map(
        dark: Boolean, id: String? = null, compact: Boolean = false, empty: Boolean = false, page: Int = 0,
        tweak: (MapScreenState) -> MapScreenState = { it },
    ) {
        val scene = if (empty) DemoFixture.emptyScene() else DemoFixture.scene()
        var state = MapScreenState(scene, compact = compact)
        if (id != null) {
            val pages = DemoFixture.pages(id)
            val shown = pages[page.coerceIn(0, pages.size - 1)].item.id
            val idx = DemoFixture.snapshot.indexOf(shown)
            scene.selected = idx
            // what the app does on a tap: fit the map in the room above the sheet, then ease to the card and come closer
            scene.camera.setViewport(720f, 640f)
            scene.heroPx = app.embeddingspace.explorer.domain.camera.CardSizing.heroWidth(720f, 640f, scene.count, 44f)
            scene.fit(false)
            scene.pendingFit = 0
            scene.focus(idx, 1)
            scene.animator.step(1f)
            state = state.copy(pages = pages, page = page)
        }
        Frame(dark, compact = compact) { MapScreen(tweak(state), MapActions(), stillMedia) }
    }

    @Composable
    fun Settings(dark: Boolean, model: ModelState = ModelState.Ready(484_622_336L)) {
        Frame(dark, Tab.SETTINGS) {
            SettingsScreen(AppSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), model, 12, 97, "1.0.0", SettingsActions())
        }
    }

    @Composable
    fun Add(dark: Boolean, note: Boolean = false) {
        ExplorerTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(xc.bgTop)) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                    Box(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().background(xc.surface)) { AddSheetContent(AddActions(), startWithNote = note) }
                }
            }
        }
    }

    @Composable
    fun Recording(dark: Boolean) {
        ExplorerTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(xc.bgTop)) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                    Box(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().background(xc.surface)) { RecordingContent(12_400, 0.6f, {}) }
                }
            }
        }
    }

    @Composable
    fun Download(dark: Boolean, state: ModelState) {
        ExplorerTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(xc.background)) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) { ModelDownloadScreen(state, {}, {}, {}) }
            }
        }
    }

    @Composable
    fun About(dark: Boolean) {
        ExplorerTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(xc.background)) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                    AboutScreen("1.0.0", DemoFixture.manifest.items.filter { it.type != "text" }, {})
                }
            }
        }
    }

    fun jobs(): List<Job> = listOf(
        Job("a", JobKind.PHOTO, "", "Photo", Stage.EMBED),
        Job("b", JobKind.VIDEO, "", "Clip", Stage.EMBED),
        Job("c", JobKind.NOTE, "", "Note", Stage.QUEUED),
    )

    fun landed(): LandedUi = LandedUi(DemoFixture.item("demo-img-beach_waves_0"), "Waves", false, listOf(LinkUi("demo-img-beach_waves_1", 71)))

    fun landing(scene: app.embeddingspace.explorer.ui.map.MapScene) {
        val idx = DemoFixture.snapshot.indexOf("demo-img-beach_waves_0")
        val l = intArrayOf(DemoFixture.snapshot.indexOf("demo-img-beach_waves_1"), DemoFixture.snapshot.indexOf("demo-txt-beach_waves-1"))
        scene.landing = LandingFx(idx, l, intArrayOf(71, 64))
    }
}
