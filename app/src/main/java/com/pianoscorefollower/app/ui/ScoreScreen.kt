package com.pianoscorefollower.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianoscorefollower.app.MainViewModel
import com.pianoscorefollower.app.audio.AudioViewModel
import com.pianoscorefollower.app.image.ImageScoreViewModel
import com.pianoscorefollower.app.score.SampleScore

/** Which of the two ways of reading a score the single screen is currently showing. */
enum class ScoreMode { Notation, Scroll }

/** Most photos the gallery picker is asked to allow in one go. */
private const val GALLERY_MAX_ITEMS = 60

/**
 * The one score screen.
 *
 * Notation (MIDI / MusicXML) and photographed or PDF scores used to be two tabs. They
 * share almost everything — a page of music, a transport, an import flow — so they are
 * now one screen that picks the right presentation from whatever was imported.
 */
@Composable
fun ScoreScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(),
    imageViewModel: ImageScoreViewModel = viewModel(),
    audioViewModel: AudioViewModel = viewModel(),
) {
    val scoreState by viewModel.uiState.collectAsStateWithLifecycle()
    val imageState by imageViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var mode by rememberSaveable { mutableStateOf(ScoreMode.Notation) }
    var modePicked by rememberSaveable { mutableStateOf(false) }
    var chooserOpen by remember { mutableStateOf(false) }

    // Whatever was restored from disk decides the opening mode. Both view models load
    // asynchronously, so this waits for the first one that has something to show.
    LaunchedEffect(scoreState.loadedScore, imageState.pages.size) {
        if (modePicked) return@LaunchedEffect
        when {
            scoreState.loadedScore != null -> {
                mode = ScoreMode.Notation
                modePicked = true
            }

            imageState.pages.isNotEmpty() -> {
                mode = ScoreMode.Scroll
                modePicked = true
            }
        }
    }

    val scorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mode = ScoreMode.Notation
            modePicked = true
            viewModel.importScore(context.contentResolver, uri)
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(GALLERY_MAX_ITEMS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            mode = ScoreMode.Scroll
            modePicked = true
            imageViewModel.importPages(uris)
        }
    }

    val imageFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            mode = ScoreMode.Scroll
            modePicked = true
            imageViewModel.importPages(uris)
        }
    }

    val loadSample: (SampleScore) -> Unit = { sample ->
        mode = ScoreMode.Notation
        modePicked = true
        viewModel.loadSampleScore(context.assets, sample.assetPath, sample.title)
    }

    val showChooser = { chooserOpen = true }

    when (mode) {
        ScoreMode.Notation -> ScoreWorkspaceScreen(
            viewModel = viewModel,
            audioViewModel = audioViewModel,
            onImport = showChooser,
            onLoadSample = loadSample,
            onOpenSettings = onOpenSettings,
            onSwitchToScroll = if (imageState.pages.isNotEmpty()) {
                { mode = ScoreMode.Scroll }
            } else {
                null
            },
        )

        ScoreMode.Scroll -> ImageScoreScreen(
            viewModel = imageViewModel,
            onImport = showChooser,
            onOpenSettings = onOpenSettings,
            onSwitchToNotation = if (scoreState.loadedScore != null) {
                { mode = ScoreMode.Notation }
            } else {
                null
            },
        )
    }

    if (chooserOpen) {
        ImportChooserDialog(
            onDismiss = { chooserOpen = false },
            onPickScoreFile = {
                chooserOpen = false
                scorePicker.launch(arrayOf("*/*"))
            },
            onPickGallery = {
                chooserOpen = false
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFiles = {
                chooserOpen = false
                imageFilePicker.launch(arrayOf("application/pdf", "image/*"))
            },
        )
    }
}

/**
 * One import button for both kinds of score: the file type decides which presentation
 * the screen switches to, so the player does not have to pick a tab first.
 */
@Composable
private fun ImportChooserDialog(
    onDismiss: () -> Unit,
    onPickScoreFile: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入乐谱") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "选择要打开的内容，导入后会自动切换到对应的显示方式。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))

                ImportOption(
                    icon = Icons.Filled.LibraryMusic,
                    title = "MIDI / MusicXML 乐谱",
                    subtitle = "自动排版为五线谱，可跟谱与播放",
                    onClick = onPickScoreFile,
                )
                ImportOption(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "相册照片",
                    subtitle = "一次可多选，适合翻拍的纸质谱",
                    onClick = onPickGallery,
                )
                ImportOption(
                    icon = Icons.Filled.PictureAsPdf,
                    title = "文件中的 PDF / 图片",
                    subtitle = "支持 PDF 与多个图片文件",
                    onClick = onPickFiles,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ImportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
