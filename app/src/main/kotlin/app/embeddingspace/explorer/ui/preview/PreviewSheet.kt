package app.embeddingspace.explorer.ui.preview

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.embeddingspace.explorer.domain.design.Contrast
import app.embeddingspace.explorer.domain.space.ClusterPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.data.DetailUi
import app.embeddingspace.explorer.data.ItemUi
import app.embeddingspace.explorer.data.NeighborUi
import app.embeddingspace.explorer.domain.audio.Waveform
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.model.Origin
import app.embeddingspace.explorer.media.AudioDecoder
import app.embeddingspace.explorer.ui.components.ClusterDot
import app.embeddingspace.explorer.ui.components.IconPill
import app.embeddingspace.explorer.ui.components.Thumb
import app.embeddingspace.explorer.ui.components.TypeBadge
import app.embeddingspace.explorer.ui.components.icon
import app.embeddingspace.explorer.ui.media.ImageLoad
import app.embeddingspace.explorer.ui.theme.xc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

const val WAVE_BARS = 72

/** Slots for the parts that need real players, so screenshot tests can swap in still versions. */
class PreviewMedia(
    val audio: @Composable (ItemUi, Color) -> Unit,
    val video: @Composable (ItemUi, Color) -> Unit,
)

val RealPreviewMedia = PreviewMedia(
    audio = { item, tint -> RealAudioPreview(item, tint) },
    video = { item, tint -> RealVideoPreview(item, tint) },
)

class PreviewActions(
    val onClose: () -> Unit = {},
    val onJump: (ItemUi) -> Unit = {},
    val onShare: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
)

/**
 * The bottom sheet for the tapped card. It is a horizontal pager over every item of that card's cluster, the
 * tapped one first and then the most similar ones. It is part of the layout, so the map above it shrinks instead of
 * being covered. A cluster of one item shows no pager chrome.
 */
@Composable
fun PreviewSheet(
    pages: List<DetailUi>,
    page: Int,
    actions: PreviewActions,
    media: PreviewMedia,
    modifier: Modifier = Modifier,
    maxScroll: androidx.compose.ui.unit.Dp = 300.dp,
) {
    if (pages.isEmpty()) return
    val current = pages[page.coerceIn(0, pages.size - 1)]
    val tint = current.cluster?.let { xc.cluster(it.colorIndex) } ?: xc.primary
    val state = rememberPagerState(initialPage = page.coerceIn(0, pages.size - 1)) { pages.size }
    val latestPage = rememberUpdatedState(page)
    val latestOnPage = rememberUpdatedState(actions.onPage)
    LaunchedEffect(page, pages.size) {
        if (state.currentPage != page && page in pages.indices) state.scrollToPage(page)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { if (it != latestPage.value) latestOnPage.value(it) }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(xc.surface),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(xc.outline))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ClusterDot(tint, 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                current.cluster?.name ?: "", color = xc.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (current.cluster?.guess == true) Badge(stringResource(R.string.badge_guess))
            Spacer(Modifier.weight(1f))
            ClusterPager.position(page, pages.size)?.let {
                Box(Modifier.clip(RoundedCornerShape(50)).background(tint).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(it, color = Color(0xFF000000.toInt() or Contrast.onColor(app.embeddingspace.explorer.ui.theme.xcRgb(current.cluster?.colorIndex ?: 0))), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
            }
            IconPill(Icons.Rounded.Close, description = stringResource(R.string.cd_close), onClick = actions.onClose)
        }
        HorizontalPager(state = state, key = { pages[it].item.id }, beyondViewportPageCount = 0) { p ->
            PreviewPage(pages[p], media, actions, maxScroll)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
            IconPill(Icons.Rounded.Share, stringResource(R.string.action_share), stringResource(R.string.action_share), onClick = actions.onShare)
            IconPill(Icons.Rounded.Delete, stringResource(R.string.action_delete), stringResource(R.string.action_delete), tint = xc.danger, onClick = actions.onDelete)
        }
    }
}

/** One page of the pager: the full preview for that item's type. */
@Composable
private fun PreviewPage(detail: DetailUi, media: PreviewMedia, actions: PreviewActions, maxScroll: androidx.compose.ui.unit.Dp) {
    val item = detail.item
    val tint = detail.cluster?.let { xc.cluster(it.colorIndex) } ?: xc.primary
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxScroll).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(item.type, tint, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(item.title, color = xc.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.tentative) Badge(stringResource(R.string.badge_tentative))
        }
        when (item.type) {
            MediaType.IMAGE -> ImagePreview(item)
            MediaType.TEXT -> NotePreview(item, tint)
            MediaType.AUDIO -> media.audio(item, tint)
            MediaType.VIDEO -> media.video(item, tint)
        }
        if (detail.neighbors.isNotEmpty()) NeighborRow(detail.neighbors, actions.onJump)
        if (item.origin == Origin.DEMO && item.creator != null) {
            Text(
                stringResource(R.string.credit_line, item.creator, (item.license ?: "").uppercase()),
                color = xc.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Box(Modifier.clip(RoundedCornerShape(50)).background(xc.surfaceHigh).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = xc.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Picture or frame that fills the full width. The height follows the aspect ratio up to a maximum; taller ones are cropped. */
@Composable
private fun FullWidthMedia(ratio: Float, content: @Composable () -> Unit) {
    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.32f).dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val h = minOf(maxWidth / ratio.coerceIn(0.3f, 4f), maxH)
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(20.dp)).background(xc.surfaceHigh), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ImagePreview(item: ItemUi) {
    val bmp = remember(item.mediaPath) { item.mediaPath?.let { ImageLoad.loadFit(it, 1080) } }
    FullWidthMedia(if (bmp != null) bmp.width.toFloat() / bmp.height else 1.5f) {
        if (bmp != null) {
            Image(bmp, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(MediaType.IMAGE.icon(), null, tint = xc.muted, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun NotePreview(item: ItemUi, tint: Color) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.14f)).padding(16.dp)) {
        Text(item.text ?: item.title, color = xc.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NeighborRow(list: List<NeighborUi>, onJump: (ItemUi) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.item.id }) { n ->
            val tint = xc.cluster(n.item.clusterId)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(xc.surfaceHigh)
                    .clickable { onJump(n.item) }
                    .semantics { contentDescription = n.item.title }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(n.item.thumbPath, n.item.type, 40.dp, tint, corner = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (n.likely) "~" else "") + "${n.simPercent}%", color = xc.onSurface,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- audio

/** Play button, waveform with a moving playhead, and the time. Stateless so it can be drawn in tests. */
@Composable
fun AudioPreviewContent(
    peaks: FloatArray,
    progress: Float,
    playing: Boolean,
    durationMs: Long,
    tint: Color,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(tint).clickable(onClick = onToggle)
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Waveform(peaks, progress, tint, onSeek)
            Text(formatMs((durationMs * progress).toLong()) + " / " + formatMs(durationMs), color = xc.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun Waveform(peaks: FloatArray, progress: Float, tint: Color, onSeek: (Float) -> Unit) {
    val rest = xc.muted.copy(alpha = 0.45f)
    Canvas(
        Modifier.fillMaxWidth().height(56.dp).pointerInput(Unit) {
            detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
        },
    ) {
        val n = peaks.size.coerceAtLeast(1)
        val slot = size.width / n
        val bar = slot * 0.62f
        val playedBars = (progress * n)
        for (i in 0 until n) {
            val h = (size.height * (0.1f + 0.9f * peaks.getOrElse(i) { 0f })).coerceAtLeast(4f)
            val x = i * slot + (slot - bar) / 2
            drawRoundRect(if (i < playedBars) tint else rest, Offset(x, (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
        val px = progress.coerceIn(0f, 1f) * size.width
        drawLine(tint, Offset(px, 0f), Offset(px, size.height), strokeWidth = 3f)
    }
}

fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Real audio: decodes the file for the waveform and plays it with MediaPlayer. */
@Composable
fun RealAudioPreview(item: ItemUi, tint: Color) {
    val path = item.mediaPath
    var peaks by remember(path) { mutableStateOf(FloatArray(WAVE_BARS)) }
    LaunchedEffect(path) {
        peaks = withContext(Dispatchers.IO) {
            path?.let { AudioDecoder.decodeFile(it)?.let { a -> Waveform.peaks(a.samples, WAVE_BARS) } } ?: FloatArray(WAVE_BARS)
        }
    }
    var playing by remember(path) { mutableStateOf(false) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    var duration by remember(path) { mutableStateOf(item.durationMs) }
    val player = remember(path) { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(path) {
        onDispose { player[0]?.release(); player[0] = null }
    }
    fun ensure(): MediaPlayer? {
        player[0]?.let { return it }
        val p = path ?: return null
        return try {
            MediaPlayer().apply {
                setDataSource(p); prepare()
                setOnCompletionListener { playing = false; progress = 0f }
                duration = this.duration.toLong()
            }.also { player[0] = it }
        } catch (e: Throwable) {
            null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player[0]
            if (p != null && p.duration > 0) progress = p.currentPosition.toFloat() / p.duration
            delay(50)
        }
    }
    AudioPreviewContent(
        peaks, progress, playing, duration, tint,
        onToggle = {
            val p = ensure() ?: return@AudioPreviewContent
            if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
        },
        onSeek = { f -> ensure()?.let { it.seekTo((it.duration * f).toInt()); progress = f } },
    )
}

// ---------------------------------------------------------------- video

@Composable
fun RealVideoPreview(item: ItemUi, tint: Color) {
    val path = item.mediaPath ?: return
    var playing by remember(path) { mutableStateOf(false) }
    val view = remember(path) { arrayOfNulls<VideoView>(1) }
    DisposableEffect(path) { onDispose { view[0]?.stopPlayback(); view[0] = null } }
    val ratio = remember(path) { videoRatio(path) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FullWidthMedia(ratio) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(File(path).absolutePath)
                        setOnPreparedListener { it.isLooping = true; seekTo(300) }
                        view[0] = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPill(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                if (playing) "Pause" else "Play", selected = true,
            ) {
                val v = view[0] ?: return@IconPill
                if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
            }
        }
    }
}

/** Width over height of a video, read from the file. Falls back to 16:9. */
private fun videoRatio(path: String): Float = try {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
        val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
        val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w > 0 && h > 0) (if (rot == 90 || rot == 270) h / w else w / h) else 16f / 9f
    } finally {
        r.release()
    }
} catch (e: Throwable) {
    16f / 9f
}

/** A still stand-in for the video player, used where a real player cannot run (screenshots). */
@Composable
fun VideoPreviewStill(item: ItemUi, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val bmp = remember(item.thumbPath) { item.thumbPath?.let { ImageLoad.loadFit(it, 640) } }
        FullWidthMedia(16f / 9f) {
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        IconPill(Icons.Rounded.PlayArrow, null, "Play", selected = true) {}
    }
}
