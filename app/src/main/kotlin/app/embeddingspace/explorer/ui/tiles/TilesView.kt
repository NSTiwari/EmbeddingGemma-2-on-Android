package app.embeddingspace.explorer.ui.tiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.data.TileUi
import app.embeddingspace.explorer.data.TilesUi
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.domain.search.TilesLogic
import app.embeddingspace.explorer.ui.components.EmptyState
import app.embeddingspace.explorer.ui.components.GlowButton
import app.embeddingspace.explorer.ui.media.ThumbCache
import app.embeddingspace.explorer.ui.media.WaveCache
import app.embeddingspace.explorer.ui.theme.xc

/**
 * The retrieval view: a clean grid of equal tiles. Type a query to see only the cluster that fits best, ranked by
 * similarity. With no query you see everything, one cluster after the other. Tiles carry no titles; the outline
 * colour is the cluster.
 */
@Composable
fun TilesView(
    state: TilesUi,
    onOpen: (ids: List<String>, index: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = TilesLogic.columns(LocalConfiguration.current.screenWidthDp)
    when (state) {
        is TilesUi.NoMatch -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.tiles_none)) {
                GlowButton(stringResource(R.string.action_add), Icons.Rounded.Add, onClick = onAdd)
            }
        }
        is TilesUi.Match -> Column(modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.tiles_cluster, state.clusterName, state.tiles.size),
                color = xc.muted, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            val ids = state.tiles.map { it.item.id }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.tiles, key = { _, t -> t.item.id }) { i, t -> Tile(t) { onOpen(ids, i) } }
            }
        }
        is TilesUi.Groups -> LazyVerticalGrid(
            columns = GridCells.Fixed(cols), modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
                Text(
                    stringResource(R.string.tiles_hint), color = xc.muted, style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
            state.groups.forEachIndexed { gi, group ->
                if (gi > 0) item(span = { GridItemSpan(maxLineSpan) }, key = "gap$gi") { Spacer(Modifier.height(10.dp)) }
                val ids = group.map { it.item.id }
                itemsIndexed(group, key = { _, t -> t.item.id }) { i, t -> Tile(t) { onOpen(ids, i) } }
            }
        }
    }
}

/** One tile: the asset itself inside a thin outline in the cluster colour. */
@Composable
private fun Tile(t: TileUi, onClick: () -> Unit) {
    val col = xc.cluster(t.colorIndex)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.25f).clip(shape).background(xc.surface).clickable(onClick = onClick)
            .border(2.dp, col, shape).padding(3.dp).clip(RoundedCornerShape(13.dp)),
    ) {
        val item = t.item
        when (item.type) {
            MediaType.IMAGE, MediaType.VIDEO -> {
                val bmp = remember(item.thumbPath) { item.thumbPath?.let { ThumbCache.peekCard(it) ?: ThumbCache.loadCard(it) } }
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(col.copy(alpha = 0.16f)))
                if (item.type == MediaType.VIDEO) PlayBadge(col, Modifier.align(Alignment.Center), 40.dp)
            }
            MediaType.AUDIO -> {
                val peaks = remember(item.mediaPath) { item.mediaPath?.let { WaveCache.peek(it) ?: WaveCache.load(it) } ?: WaveCache.placeholder(item.id.hashCode()) }
                Canvas(Modifier.fillMaxSize()) {
                    val pad = size.width * 0.1f
                    val slot = (size.width - 2 * pad) / peaks.size
                    val bw = slot * 0.62f
                    for (b in peaks.indices) {
                        val bh = (size.height * 0.55f * (0.14f + 0.86f * peaks[b])).coerceAtLeast(3f)
                        drawRoundRect(col, Offset(pad + b * slot + (slot - bw) / 2, size.height * 0.42f - bh / 2), Size(bw, bh), CornerRadius(bw / 2))
                    }
                }
                PlayBadge(col, Modifier.align(Alignment.BottomStart).padding(10.dp), 28.dp)
            }
            MediaType.TEXT -> Text(
                item.text ?: item.title, color = xc.onSurface, style = MaterialTheme.typography.bodyMedium,
                maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun PlayBadge(col: androidx.compose.ui.graphics.Color, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    Box(modifier.size(size).clip(CircleShape).background(col.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(size * 0.62f))
    }
}
