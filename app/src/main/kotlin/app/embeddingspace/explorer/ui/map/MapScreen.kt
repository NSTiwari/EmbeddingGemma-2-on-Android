package app.embeddingspace.explorer.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.data.DetailUi
import app.embeddingspace.explorer.data.LandedUi
import app.embeddingspace.explorer.data.TilesUi
import app.embeddingspace.explorer.data.ViewMode
import app.embeddingspace.explorer.ui.tiles.TilesView
import androidx.compose.ui.graphics.Color
import app.embeddingspace.explorer.domain.queue.Job
import app.embeddingspace.explorer.domain.queue.Stage
import app.embeddingspace.explorer.ui.components.Card20
import app.embeddingspace.explorer.ui.components.IconPill
import app.embeddingspace.explorer.ui.components.TypeBadge
import app.embeddingspace.explorer.ui.components.icon
import app.embeddingspace.explorer.ui.preview.PreviewActions
import app.embeddingspace.explorer.ui.preview.PreviewMedia
import app.embeddingspace.explorer.ui.preview.PreviewSheet
import app.embeddingspace.explorer.ui.theme.xc

data class MapScreenState(
    val scene: MapScene,
    /** The preview pager: every item of the tapped card's cluster, and the page being shown. */
    val pages: List<DetailUi> = emptyList(),
    val page: Int = 0,
    val viewMode: ViewMode = ViewMode.SPACE,
    val tiles: TilesUi = TilesUi.Groups(emptyList()),
    val query: String = "",
    val searchHits: Int = -1,
    val searchNote: String? = null,
    val jobs: List<Job> = emptyList(),
    val landed: LandedUi? = null,
    val relayoutProgress: Float? = null,
    /** Show the slim "these are demo images" hint under the search bar. */
    val demoHint: Boolean = false,
    /** True on short screens (landscape): smaller sheet. */
    val compact: Boolean = false,
)

class MapActions(
    val onSelect: (Int) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onNeedThumb: (String) -> Unit = {},
    val onNeedWave: (String) -> Unit = {},
    val onRetryJob: (String) -> Unit = {},
    val onDeleteDemo: () -> Unit = {},
    val onDismissHint: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onViewMode: (ViewMode) -> Unit = {},
    val onOpenTile: (List<String>, Int) -> Unit = { _, _ -> },
    val preview: PreviewActions = PreviewActions(),
)

/** The main screen: search on top, the 3D map of cards, a status strip and the preview pager. */
@Composable
fun MapScreen(state: MapScreenState, actions: MapActions, media: PreviewMedia, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchPill(state, actions, Modifier.weight(1f))
            ViewToggle(state.viewMode, actions.onViewMode)
        }
        AnimatedVisibility(visible = state.demoHint, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            DemoHint(actions)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          if (state.viewMode == ViewMode.TILES) {
            if (state.tiles is TilesUi.Groups && state.tiles.groups.isEmpty()) {
                app.embeddingspace.explorer.ui.components.EmptyState(stringResource(R.string.empty_map)) {
                    app.embeddingspace.explorer.ui.components.GlowButton(stringResource(R.string.action_add), Icons.Rounded.Add, onClick = actions.onAdd)
                }
            } else {
                TilesView(state.tiles, actions.onOpenTile, actions.onAdd)
            }
          } else {
            MapView(
                state.scene, Modifier.fillMaxSize(),
                onSelect = actions.onSelect, onNeedThumb = actions.onNeedThumb, onNeedWave = actions.onNeedWave,
            )
            if (state.scene.snapshot.items.isEmpty()) {
                app.embeddingspace.explorer.ui.components.EmptyState(stringResource(R.string.empty_map)) {
                    app.embeddingspace.explorer.ui.components.GlowButton(stringResource(R.string.action_add), Icons.Rounded.Add, onClick = actions.onAdd)
                }
            }
          }
        }
        StatusStrip(state, actions)
        AnimatedVisibility(
            visible = state.pages.isNotEmpty(),
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
        ) {
            PreviewSheet(state.pages, state.page, actions.preview, media, maxScroll = if (state.compact) 130.dp else (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.48f).dp)
        }
    }
}

/** One slim, dismissible line while the demo items are on the map. */
@Composable
private fun DemoHint(actions: MapActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(xc.surfaceHigh)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.demo_hint), color = xc.onSurface, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.action_delete_demo_short), color = xc.primary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = actions.onDeleteDemo).padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = actions.onDismissHint).semantics { contentDescription = "Dismiss" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = xc.muted, modifier = Modifier.size(20.dp)) }
    }
}

/** The one switch between the 3D space and the plain grid of results. Remembered in settings. */
@Composable
private fun ViewToggle(mode: ViewMode, onMode: (ViewMode) -> Unit) {
    Row(Modifier.height(48.dp).clip(RoundedCornerShape(50)).background(xc.surfaceHigh).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((m, label) in listOf(ViewMode.SPACE to R.string.view_space, ViewMode.TILES to R.string.view_tiles)) {
            val on = m == mode
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) xc.primary else Color.Transparent)
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onMode(m) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), color = if (on) xc.onPrimary else xc.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SearchPill(state: MapScreenState, actions: MapActions, modifier: Modifier) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(50)).background(xc.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = xc.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (state.query.isEmpty()) Text(stringResource(R.string.search_hint), color = xc.muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = state.query, onValueChange = actions.onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = xc.onSurface),
                cursorBrush = SolidColor(xc.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
            )
        }
        if (state.query.isNotEmpty() || state.searchHits >= 0) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = actions.onClearSearch)
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, null, tint = xc.muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun StatusStrip(state: MapScreenState, actions: MapActions) {
    val showJobs = state.jobs.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(visible = state.relayoutProgress != null) {
            LinearProgressIndicator(
                progress = { state.relayoutProgress ?: 0f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = xc.accent, trackColor = xc.outline, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
        if (state.landed != null) LandedChip(state.landed)
        if ((state.viewMode == ViewMode.SPACE && state.searchHits >= 0) || state.searchNote != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = state.searchNote ?: stringResource(R.string.search_matches, state.searchHits)
                Chip(text, Icons.Rounded.Search)
            }
        }
        if (showJobs) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.jobs, key = { it.id }) { JobChip(it, actions) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: (() -> Unit)? = null) {
    val base = Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(xc.surface)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = xc.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = xc.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LandedChip(l: LandedUi) {
    val tint = xc.cluster(0)
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(xc.primary).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeBadge(l.item.type, xc.onPrimary, 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (l.newCluster) stringResource(R.string.toast_new_island) else stringResource(R.string.toast_joined, l.clusterName),
            color = xc.onPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (tint != xc.primary) Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun JobChip(job: Job, actions: MapActions) {
    val failed = job.stage == Stage.FAILED
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(if (failed) xc.danger.copy(alpha = 0.2f) else xc.surface)
            .clickable { if (failed) actions.onRetryJob(job.id) else Unit }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed) {
            Icon(Icons.Rounded.Sync, null, tint = xc.danger, modifier = Modifier.size(22.dp))
        } else {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { job.progress.coerceAtLeast(0.06f) }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
                    color = xc.accent, trackColor = xc.outline,
                )
                Icon(jobIcon(job), null, tint = xc.onSurface, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stageText(job.stage) + if (job.kind.slow && job.stage.running) " · " + stringResource(R.string.job_slow) else "",
            color = xc.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
    }
}

private fun jobIcon(job: Job) = when (job.kind) {
    app.embeddingspace.explorer.domain.queue.JobKind.PHOTO -> app.embeddingspace.explorer.domain.model.MediaType.IMAGE.icon()
    app.embeddingspace.explorer.domain.queue.JobKind.VIDEO -> app.embeddingspace.explorer.domain.model.MediaType.VIDEO.icon()
    app.embeddingspace.explorer.domain.queue.JobKind.NOTE -> app.embeddingspace.explorer.domain.model.MediaType.TEXT.icon()
    else -> app.embeddingspace.explorer.domain.model.MediaType.AUDIO.icon()
}

@Composable
fun stageText(stage: Stage): String = stringResource(
    when (stage) {
        Stage.QUEUED -> R.string.stage_queued
        Stage.PREPARE -> R.string.stage_prepare
        Stage.EMBED -> R.string.stage_embed
        Stage.PLACE -> R.string.stage_place
        Stage.DONE -> R.string.stage_done
        Stage.FAILED -> R.string.stage_failed
    },
)
