package app.embeddingspace.explorer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.domain.io.DemoItem
import app.embeddingspace.explorer.ui.components.Card20
import app.embeddingspace.explorer.ui.components.IconPill
import app.embeddingspace.explorer.ui.components.SectionLabel
import app.embeddingspace.explorer.ui.theme.xc

/** Licences, model credit and the demo media credits (title, creator, licence). */
@Composable
fun AboutScreen(version: String, credits: List<DemoItem>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(Icons.Rounded.ArrowBack, null, stringResource(R.string.cd_back), onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.about_title), color = xc.onSurface, style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card20(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.app_name), color = xc.onSurface, style = MaterialTheme.typography.titleLarge)
                        Text(version, color = xc.muted, style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.about_model), color = xc.onSurface, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.about_libs), color = xc.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.about_demo_credits)) }
            items(credits, key = { it.id }) { c ->
                Card20(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, color = xc.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.creator ?: "-", color = xc.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(c.license.uppercase(), color = xc.accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
