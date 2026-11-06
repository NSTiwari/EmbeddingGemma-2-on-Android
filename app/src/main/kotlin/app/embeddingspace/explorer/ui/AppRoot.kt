package app.embeddingspace.explorer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.embeddingspace.explorer.BuildConfig
import app.embeddingspace.explorer.ExplorerViewModel
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.Confirm
import app.embeddingspace.explorer.Route
import app.embeddingspace.explorer.data.AppSettings
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.domain.queue.Job
import app.embeddingspace.explorer.Tab
import app.embeddingspace.explorer.UiEvent
import app.embeddingspace.explorer.ui.add.AddActions
import app.embeddingspace.explorer.ui.add.AddSheetContent
import app.embeddingspace.explorer.ui.add.ModelDownloadScreen
import app.embeddingspace.explorer.ui.add.RecordingContent
import app.embeddingspace.explorer.ui.map.MapActions
import app.embeddingspace.explorer.ui.map.MapScreen
import app.embeddingspace.explorer.ui.map.MapScreenState
import app.embeddingspace.explorer.ui.preview.PreviewActions
import app.embeddingspace.explorer.ui.preview.RealPreviewMedia
import app.embeddingspace.explorer.ui.settings.AboutScreen
import app.embeddingspace.explorer.ui.settings.ConfirmDialog
import app.embeddingspace.explorer.ui.settings.SettingsActions
import app.embeddingspace.explorer.ui.settings.SettingsScreen
import app.embeddingspace.explorer.ui.theme.ExplorerTheme
import app.embeddingspace.explorer.ui.theme.xc
import androidx.compose.runtime.collectAsState
import java.io.File

/** The whole app: the current tab, the docked bottom bar, and the sheets and dialogs on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: ExplorerViewModel) {
    val settings by vm.settings.collectAsState()
    val model by vm.modelState.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val ctx = LocalContext.current
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.addPhotosAndVideos(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addAudio(uri) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = cameraPath
        if (ok && p != null) vm.addCameraShot(File(p))
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startRecording(true) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.ShareText -> share(ctx, e)
                is UiEvent.ShareFiles -> share(ctx, e)
                UiEvent.NeedMic -> mic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    ExplorerTheme(settings.theme) {
        Box(Modifier.fillMaxSize().background(xc.background)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (vm.route) {
                        Route.DOWNLOAD -> ModelDownloadScreen(model, { vm.ask(Confirm.DOWNLOAD) }, vm::pauseDownload, onBack = { vm.route = Route.NONE })
                        Route.ABOUT -> AboutScreen(BuildConfig.VERSION_NAME, remember { vm.demoCredits() }, onBack = { vm.route = Route.NONE })
                        else -> MainContent(vm, settings, model, jobs, compact)
                    }
                }
                if (vm.route != Route.DOWNLOAD && vm.route != Route.ABOUT) {
                    DockBar(vm.tab, { vm.tab = it }, vm::onPlus, compact = compact)
                } else {
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            if (vm.route == Route.ADD) {
                ModalBottomSheet(onDismissRequest = { vm.route = Route.NONE }, sheetState = sheetState, containerColor = xc.surface) {
                    AddSheetContent(
                        AddActions(
                            onPhotoVideo = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                            onCamera = {
                                val f = File(ctx.filesDir, "pending").also { it.mkdirs() }.resolve("cam-${System.currentTimeMillis()}.jpg")
                                cameraPath = f.absolutePath
                                camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
                            },
                            onAudioFile = { audioPicker.launch(arrayOf("audio/*")) },
                            onRecord = {
                                val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                vm.route = Route.NONE
                                vm.startRecording(has)
                            },
                            onNote = vm::addNote,
                        ),
                    )
                }
            }
            if (vm.route == Route.RECORD) {
                ModalBottomSheet(onDismissRequest = vm::cancelRecording, sheetState = sheetState, containerColor = xc.surface) {
                    RecordingContent(vm.recordingMs, vm.recordingLevel, onStop = vm::stopRecording)
                }
            }
            vm.confirm?.let { what ->
                ConfirmDialog(
                    stringResource(
                        when (what) {
                            Confirm.DEMO -> R.string.confirm_delete_demo
                            Confirm.EVERYTHING -> R.string.confirm_delete_mine
                            Confirm.MODEL -> R.string.confirm_delete_model
                            Confirm.DOWNLOAD -> R.string.confirm_download
                        },
                    ),
                    onYes = vm::confirmed, onNo = vm::dismissConfirm,
                    yesLabel = stringResource(if (what == Confirm.DOWNLOAD) R.string.action_download else R.string.action_yes),
                )
            }
        }
    }
    BackHandler(enabled = vm.route != Route.NONE || vm.pages.isNotEmpty() || vm.tab != Tab.MAP) {
        when {
            vm.route != Route.NONE -> vm.route = Route.NONE
            vm.pages.isNotEmpty() -> vm.clearSelection()
            else -> vm.tab = Tab.MAP
        }
    }
}

@Composable
private fun MainContent(vm: ExplorerViewModel, settings: AppSettings, model: ModelState, jobs: List<Job>, compact: Boolean) {
    when (vm.tab) {
        Tab.MAP -> {
            val state = MapScreenState(
                scene = vm.scene, pages = vm.pages, page = vm.page, viewMode = settings.viewMode, tiles = vm.tiles, query = vm.query, searchHits = vm.searchHits, searchNote = vm.searchNote,
                jobs = jobs, landed = vm.landed, relayoutProgress = vm.relayoutProgress, demoHint = vm.demoHintVisible, compact = compact,
            )
            val actions = remember(vm) {
                MapActions(
                    onSelect = vm::select, onQuery = vm::onQuery, onSearch = vm::search, onClearSearch = vm::clearSearch,
                    onNeedThumb = vm::needThumb, onNeedWave = vm::needWave, onRetryJob = vm::retryJob,
                    onDeleteDemo = { vm.ask(Confirm.DEMO) }, onDismissHint = vm::dismissDemoHint, onAdd = vm::onPlus,
                    onViewMode = vm::setViewMode, onOpenTile = vm::openTile,
                    preview = PreviewActions(
                        onClose = vm::clearSelection, onJump = vm::jumpTo, onShare = vm::shareSelected,
                        onDelete = vm::deleteSelected, onPage = vm::onPage,
                    ),
                )
            }
            MapScreen(state, actions, RealPreviewMedia)
        }
        Tab.SETTINGS -> SettingsScreen(
            settings, model, vm.userItems, vm.demoItems, BuildConfig.VERSION_NAME,
            SettingsActions(
                onTheme = { vm.setTheme(it) }, onDownloadModel = { vm.route = Route.DOWNLOAD },
                onDeleteModel = { vm.ask(Confirm.MODEL) }, onDeleteDemo = { vm.ask(Confirm.DEMO) },
                onDeleteMine = { vm.ask(Confirm.EVERYTHING) }, onAbout = { vm.route = Route.ABOUT },
            ),
        )
    }
}

/** Docked bottom bar: Map, the plus button, Settings. It is part of the layout, never a floating overlay. */
@Composable
fun DockBar(tab: Tab, onTab: (Tab) -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(xc.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp)
            .height(if (compact) 56.dp else 68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem(Icons.Rounded.Hub, stringResource(R.string.tab_map), tab == Tab.MAP, Modifier.weight(1f), compact) { onTab(Tab.MAP) }
        Box(Modifier.weight(1.3f), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(if (compact) 48.dp else 56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(xc.primary, xc.accent)))
                    .clickable(role = Role.Button, onClick = onPlus).semantics { contentDescription = "Add" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = xc.onPrimary, modifier = Modifier.size(32.dp)) }
        }
        DockItem(Icons.Rounded.Settings, stringResource(R.string.tab_settings), tab == Tab.SETTINGS, Modifier.weight(1f), compact) { onTab(Tab.SETTINGS) }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    val tint = if (selected) xc.primary else xc.muted
    Column(
        modifier.height(if (compact) 52.dp else 64.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        if (!compact) Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "mp4" -> "video/mp4"
    "wav" -> "audio/wav"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    else -> "*/*"
}

private fun share(ctx: Context, e: UiEvent) {
    val intent = when (e) {
        is UiEvent.ShareText -> Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, e.text) }
        is UiEvent.ShareFiles -> {
            val uris = ArrayList<Uri>(e.paths.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(it)) })
            val mimes = e.paths.map { mimeOf(it) }.toSet()
            val mime = if (mimes.size == 1) mimes.first() else "*/*"
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uris[0]) }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = mime; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
            }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        UiEvent.NeedMic -> return
    }
    ctx.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
