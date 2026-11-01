package com.pianoscorefollower.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.pianoscorefollower.app.MainViewModel
import com.pianoscorefollower.app.audio.AudioViewModel
import com.pianoscorefollower.app.image.ImageScoreViewModel

@Stable
class BottomBarHost {
    private var owner: Any? = null
    var transport: (@Composable RowScope.() -> Unit)? by mutableStateOf(null); private set
    fun setTransport(owner: Any, content: (@Composable RowScope.() -> Unit)?) { this.owner = owner; transport = content }
    fun clearTransport(owner: Any) { if (this.owner === owner) transport = null }
}
val LocalBottomBarHost = staticCompositionLocalOf<BottomBarHost> { error("Missing transport host") }
@Stable class PerformanceChrome { var focused by mutableStateOf(false) }
val LocalPerformanceChrome = staticCompositionLocalOf { PerformanceChrome() }
enum class AppRoute { Library, Score, Recording, Tuner, Settings, Scroll }

@Composable
fun AppRoot(settings: SettingsStore) {
    val main: MainViewModel = viewModel()
    val audio: AudioViewModel = viewModel()
    val images: ImageScoreViewModel = viewModel()
    var route by rememberSaveable { mutableStateOf(AppRoute.Library) }
    val host = remember { BottomBarHost() }
    val chrome = remember { PerformanceChrome() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { if (audio.followState.value.isFollowing) main.rememberPosition(audio.followState.value.cursorTick) else if(main.uiState.value.isPlaying) main.rememberPosition(main.uiState.value.playbackTick); images.setPlaying(false); audio.stopRecording(); audio.stopPlayback(); audio.stopCalibration(); audio.stopListening() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val state by main.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val navigate: (AppRoute) -> Unit = { target ->
        if (route != target) {
            if (audio.followState.value.isFollowing) main.rememberPosition(audio.followState.value.cursorTick) else if(main.uiState.value.isPlaying) main.rememberPosition(main.uiState.value.playbackTick)
            audio.stopPlayback(); audio.stopListening(); audio.stopCalibration()
            if (route == AppRoute.Recording) audio.stopRecording()
            route = target
        }
    }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); main.dismissError() } }
    val entries = listOf(AppRoute.Library to (Icons.Default.LibraryMusic to "曲库"),
        AppRoute.Score to (Icons.Default.Piano to "演奏"), AppRoute.Recording to (Icons.Default.FiberManualRecord to "录制"),
        AppRoute.Tuner to (Icons.Default.GraphicEq to "校音"), AppRoute.Settings to (Icons.Default.Settings to "设置"))
    CompositionLocalProvider(LocalBottomBarHost provides host, LocalPerformanceChrome provides chrome) {
        Scaffold(contentWindowInsets=if(route==AppRoute.Score) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Column {
                if (route == AppRoute.Scroll) host.transport?.let { content ->
                    Surface(tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { content() } }
                }
                if (route != AppRoute.Score) NavigationBar {
                    entries.forEach { (item, visual) -> NavigationBarItem(selected = route == item || item == AppRoute.Library && route == AppRoute.Scroll,
                        onClick = { navigate(item) }, icon = { Icon(visual.first, contentDescription = visual.second) },
                        label = { Text(visual.second) }) }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (route) {
                    AppRoute.Library -> LibraryScreen(main, images, onPerform = { navigate(AppRoute.Score) }, onImages = { navigate(AppRoute.Scroll) })
                    AppRoute.Score -> ScoreScreen(onExit = {navigate(AppRoute.Library)}, onOpenSettings = { navigate(AppRoute.Settings) }, viewModel = main, imageViewModel = images, audioViewModel = audio)
                    AppRoute.Recording -> RecordingScreen(audio, onOpen = { session -> main.loadRecording(session); navigate(AppRoute.Score) })
                    AppRoute.Tuner -> CalibrationScreen(audioViewModel = audio, onBack = { navigate(AppRoute.Library) })
                    AppRoute.Settings -> SettingsScreen(settings, onBack = { navigate(AppRoute.Library) }, onOpenTuner = { navigate(AppRoute.Tuner) })
                    AppRoute.Scroll -> ImageScoreScreen(viewModel = images, onImport = { navigate(AppRoute.Library) },
                        onOpenSettings = { navigate(AppRoute.Settings) }, onSwitchToNotation = { navigate(AppRoute.Score) })
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(main: MainViewModel, images: ImageScoreViewModel, onPerform: () -> Unit, onImages: () -> Unit) {
    val state by main.uiState.collectAsStateWithLifecycle()
    val imageState by images.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { main.importScore(context.contentResolver, it); onPerform() }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { images.importPages(uris); onImages() }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { uris ->
        if (uris.isNotEmpty()) { images.importPages(uris); onImages() }
    }
    var rename by remember { mutableStateOf<com.pianoscorefollower.app.score.LibraryEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<com.pianoscorefollower.app.score.LibraryEntry?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
        item {
            Text("智能琴谱", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("让琴谱跟上你的演奏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("导入 MIDI / MusicXML")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pdf.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)) { Text("PDF / 图片") }
                OutlinedButton(onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("相册照片") }
            }
        }
        if (imageState.hasPages) item { OutlinedCard(onClick = onImages, modifier = Modifier.fillMaxWidth()) {
            ListItem(headlineContent = { Text("图片滚动谱") }, supportingContent = { Text("${imageState.pages.size} 页 · 定时滚动") }, leadingContent = { Icon(Icons.Default.PictureAsPdf, null) })
        } }
        if (state.library.isNotEmpty()) item { Text("我的曲谱", style = MaterialTheme.typography.titleMedium) }
        items(state.library, key = { it.id }) { entry ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { main.openLibrary(entry.id); onPerform() }.padding(vertical = 8.dp)) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        Text(entry.source.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { rename = entry; name = entry.title }) { Icon(Icons.Default.Edit, "重命名") }
                    IconButton(onClick = { delete = entry }) { Icon(Icons.Default.DeleteOutline, "删除曲谱") }
                }
            }
        }
        item { Text("内置练习", style = MaterialTheme.typography.titleMedium) }
        items(com.pianoscorefollower.app.score.BuiltInSamples) { sample ->
            Card(onClick = { main.loadSampleScore(context.assets, sample.assetPath, sample.title); onPerform() }, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(sample.title) }, supportingContent = { Text(sample.subtitle) }, leadingContent = { Icon(Icons.Default.MusicNote, null) })
            }
        }
        item { Text("MIDI / MusicXML 可听音跟谱；PDF / 照片按设置的时长滚动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    rename?.let { entry -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("曲谱名称") }, text = {
        OutlinedTextField(name, { name = it }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { main.renameLibrary(entry.id, name); rename = null }, enabled = name.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = { rename = null }) { Text("取消") } }) }
    delete?.let { entry -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除“${entry.title}”？") },
        confirmButton = { TextButton(onClick = { main.deleteLibrary(entry.id); delete = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } }) }
}
