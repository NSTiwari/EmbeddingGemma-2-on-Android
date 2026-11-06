package app.embeddingspace.explorer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.data.AppSettings
import app.embeddingspace.explorer.data.ModelInfo
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.data.ThemeMode
import app.embeddingspace.explorer.ui.components.Card20
import app.embeddingspace.explorer.ui.components.GlowButton
import app.embeddingspace.explorer.ui.components.ScreenTitle
import app.embeddingspace.explorer.ui.components.SectionLabel
import app.embeddingspace.explorer.ui.components.SoftButton
import app.embeddingspace.explorer.ui.theme.xc

class SettingsActions(
    val onTheme: (ThemeMode) -> Unit = {},
    val onDownloadModel: () -> Unit = {},
    val onDeleteModel: () -> Unit = {},
    val onDeleteDemo: () -> Unit = {},
    val onDeleteMine: () -> Unit = {},
    val onAbout: () -> Unit = {},
)

/** Settings: theme, the model, deleting the demo or your own items, and About. */
@Composable
fun SettingsScreen(
    settings: AppSettings, model: ModelState, userItems: Int, demoItems: Int, version: String,
    actions: SettingsActions, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.tab_settings))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.section_look))
            Card20(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) { ThemePicker(settings.theme, actions.onTheme) }
            }
            SectionLabel(stringResource(R.string.model_name))
            Card20(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModelRow(model, actions)
                }
            }
            SectionLabel(stringResource(R.string.section_data))
            Card20(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$userItems", color = xc.accent, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.caption_your_items), color = xc.muted, style = MaterialTheme.typography.labelMedium)
                    }
                    if (demoItems > 0) {
                        SoftButton(stringResource(R.string.action_delete_demo), Icons.Rounded.DeleteForever, Modifier.fillMaxWidth(), danger = true, onClick = actions.onDeleteDemo)
                    }
                    SoftButton(stringResource(R.string.action_delete_mine), Icons.Rounded.DeleteForever, Modifier.fillMaxWidth(), danger = true, onClick = actions.onDeleteMine)
                }
            }
            Card20(Modifier.fillMaxWidth(), onClick = actions.onAbout) {
                Row(Modifier.padding(14.dp).defaultMinSize(minHeight = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = xc.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.about_title), color = xc.onSurface, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(version, color = xc.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** The questions behind every deletion that cannot be undone. */
@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit, yesLabel: String = stringResource(R.string.action_yes)) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = xc.surface,
        title = { Text(text, color = xc.onSurface, style = MaterialTheme.typography.titleMedium) },
        confirmButton = { TextButton(onClick = onYes) { Text(yesLabel, color = xc.danger) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.action_no), color = xc.onSurface) } },
    )
}

@Composable
private fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(xc.surfaceHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Seg(Icons.Rounded.BrightnessAuto, stringResource(R.string.theme_system), current == ThemeMode.SYSTEM, Modifier.weight(1f)) { onPick(ThemeMode.SYSTEM) }
        Seg(Icons.Rounded.LightMode, stringResource(R.string.theme_light), current == ThemeMode.LIGHT, Modifier.weight(1f)) { onPick(ThemeMode.LIGHT) }
        Seg(Icons.Rounded.DarkMode, stringResource(R.string.theme_dark), current == ThemeMode.DARK, Modifier.weight(1f)) { onPick(ThemeMode.DARK) }
    }
}

@Composable
private fun Seg(icon: ImageVector?, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (selected) xc.onPrimary else xc.onSurface
    Row(
        modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(50)).background(if (selected) xc.primary else xc.surfaceHigh)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ModelRow(model: ModelState, actions: SettingsActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(xc.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudDownload, null, tint = xc.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (model is ModelState.Ready) R.string.model_ready else R.string.model_name),
                color = xc.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1,
            )
            Text(
                when (model) {
                    is ModelState.Ready -> ModelInfo.mb(model.bytes)
                    is ModelState.Downloading -> "${(model.fraction * 100).toInt()}%"
                    is ModelState.Failed -> stringResource(R.string.model_failed)
                    ModelState.Missing -> ModelInfo.mb(ModelInfo.EXPECTED_BYTES)
                },
                color = xc.muted, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    when (model) {
        is ModelState.Ready -> SoftButton(stringResource(R.string.action_delete), Icons.Rounded.DeleteForever, Modifier.fillMaxWidth(), danger = true, onClick = actions.onDeleteModel)
        is ModelState.Downloading -> Unit
        else -> GlowButton(stringResource(R.string.action_download_model), null, Modifier.fillMaxWidth(), onClick = actions.onDownloadModel)
    }
}
