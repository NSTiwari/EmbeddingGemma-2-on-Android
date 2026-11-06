package app.embeddingspace.explorer.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.embeddingspace.explorer.R
import app.embeddingspace.explorer.data.ModelInfo
import app.embeddingspace.explorer.data.ModelState
import app.embeddingspace.explorer.ui.components.GlowButton
import app.embeddingspace.explorer.ui.components.IconPill
import app.embeddingspace.explorer.ui.components.SoftButton
import app.embeddingspace.explorer.ui.preview.formatMs
import app.embeddingspace.explorer.ui.theme.xc

class AddActions(
    val onPhotoVideo: () -> Unit = {},
    val onCamera: () -> Unit = {},
    val onAudioFile: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onNote: (String) -> Unit = {},
)

/** The compact sheet behind the plus button: five big icon tiles and, for notes, a small text box. */
@Composable
fun AddSheetContent(actions: AddActions, modifier: Modifier = Modifier, startWithNote: Boolean = false) {
    var noteMode by rememberSaveable { mutableStateOf(startWithNote) }
    var text by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_title), color = xc.onSurface, style = MaterialTheme.typography.titleLarge)
        if (!noteMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Rounded.PhotoLibrary, stringResource(R.string.add_photo_video), xc.cluster(6), Modifier.weight(1f), actions.onPhotoVideo)
                Tile(Icons.Rounded.CameraAlt, stringResource(R.string.add_camera), xc.cluster(11), Modifier.weight(1f), actions.onCamera)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Rounded.AudioFile, stringResource(R.string.add_audio_file), xc.cluster(4), Modifier.weight(1f), actions.onAudioFile)
                Tile(Icons.Rounded.Mic, stringResource(R.string.add_record), xc.cluster(0), Modifier.weight(1f), actions.onRecord)
                Tile(Icons.Rounded.NoteAlt, stringResource(R.string.add_note), xc.cluster(2), Modifier.weight(1f)) { noteMode = true }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(xc.surfaceHigh).padding(14.dp)) {
                if (text.isEmpty()) Text(stringResource(R.string.note_hint), color = xc.muted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = text, onValueChange = { text = it.take(2000) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = xc.onSurface), cursorBrush = SolidColor(xc.accent),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(stringResource(R.string.cd_back), Icons.Rounded.ArrowBack) { noteMode = false }
                GlowButton(stringResource(R.string.action_add), Icons.Rounded.Add, Modifier.weight(1f), enabled = text.isNotBlank()) {
                    actions.onNote(text.trim()); text = ""; noteMode = false
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.24f))
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = xc.onSurface, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Shown while a voice note is being recorded. */
@Composable
fun RecordingContent(elapsedMs: Long, level: Float, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size((88 + 40 * level).dp).clip(CircleShape).background(xc.danger.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(xc.danger).clickable(onClick = onStop).semantics { contentDescription = "Stop" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Stop, null, tint = xc.onDanger, modifier = Modifier.size(36.dp)) }
        }
        Text(formatMs(elapsedMs), color = xc.onSurface, style = MaterialTheme.typography.displayLarge)
    }
}

/** The clean screen shown when + is used before the model is on the phone. */
@Composable
fun ModelDownloadScreen(
    state: ModelState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconPill(Icons.Rounded.ArrowBack, null, stringResource(R.string.cd_back), onClick = onBack) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(Brush.linearGradient(listOf(xc.primary, xc.accent))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CloudDownload, null, tint = xc.onPrimary, modifier = Modifier.size(64.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(ModelInfo.mb(ModelInfo.EXPECTED_BYTES), color = xc.onSurface, style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.model_name), color = xc.muted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, null, tint = xc.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.download_wifi), color = xc.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        when (state) {
            is ModelState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.fraction }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                    color = xc.accent, trackColor = xc.outline, gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${ModelInfo.mb(state.done)} / ${ModelInfo.mb(state.total)}",
                    color = xc.muted, style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(16.dp))
                SoftButton(stringResource(R.string.action_pause), onClick = onPause)
            }
            is ModelState.Failed -> {
                Text(stringResource(R.string.download_failed), color = xc.danger, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                GlowButton(stringResource(R.string.action_resume), Icons.Rounded.CloudDownload, Modifier.fillMaxWidth(), onClick = onDownload)
            }
            else -> GlowButton(
                stringResource(if (state is ModelState.Missing) R.string.action_download_model else R.string.action_resume),
                null, Modifier.fillMaxWidth(), onClick = onDownload,
            )
        }
        Spacer(Modifier.weight(1.4f))
    }
}
