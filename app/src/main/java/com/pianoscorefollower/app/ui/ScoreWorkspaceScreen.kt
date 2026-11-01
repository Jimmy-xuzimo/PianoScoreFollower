package com.pianoscorefollower.app.ui

import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianoscorefollower.app.MainViewModel
import com.pianoscorefollower.app.audio.*
import com.pianoscorefollower.app.performance.FollowStatus
import com.pianoscorefollower.app.score.SampleScore
import com.pianoscorefollower.app.viewer.*
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreWorkspaceScreen(onExit: () -> Unit, onImport: () -> Unit, onLoadSample: (SampleScore) -> Unit, onOpenSettings: () -> Unit,
    onSwitchToScroll: (() -> Unit)?, viewModel: MainViewModel = viewModel(), audioViewModel: AudioViewModel = viewModel()) {
    val preferences=LocalContext.current.getSharedPreferences("performance",0)
    var dark by rememberSaveable { mutableStateOf(preferences.getBoolean("darkPerformance",true)) }
    val scheme=if(dark) darkColorScheme(background=androidx.compose.ui.graphics.Color(0xff14171d),surface=androidx.compose.ui.graphics.Color(0xff22262f),onSurface=androidx.compose.ui.graphics.Color(0xfff2f4f8),onPrimary=androidx.compose.ui.graphics.Color(0xff102a4c),secondaryContainer=androidx.compose.ui.graphics.Color(0xff30445f),onSecondaryContainer=androidx.compose.ui.graphics.Color(0xffe4efff),surfaceVariant=androidx.compose.ui.graphics.Color(0xff303744),onSurfaceVariant=androidx.compose.ui.graphics.Color(0xffc3ccd9),primary=androidx.compose.ui.graphics.Color(0xffa8c7fa)) else lightColorScheme()
    MaterialTheme(colorScheme=scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            ScoreWorkspaceContent(onExit,onImport,onLoadSample,onOpenSettings,dark,{dark=it;preferences.edit().putBoolean("darkPerformance",it).apply()},onSwitchToScroll,viewModel,audioViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreWorkspaceContent(onExit: () -> Unit, onImport: () -> Unit, onLoadSample: (SampleScore) -> Unit, onOpenSettings: () -> Unit,
    dark:Boolean, onDarkChange:(Boolean)->Unit, onSwitchToScroll: (() -> Unit)?, viewModel: MainViewModel = viewModel(), audioViewModel: AudioViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val follow by audioViewModel.followState.collectAsStateWithLifecycle()
    val listening by audioViewModel.listeningState.collectAsStateWithLifecycle()
    val source by audioViewModel.source.collectAsStateWithLifecycle()
    val reference by audioViewModel.referenceState.collectAsStateWithLifecycle()
    val midi by audioViewModel.midi.state.collectAsStateWithLifecycle()
    val assessment by audioViewModel.assessment.collectAsStateWithLifecycle()
    val assessmentMode by audioViewModel.assessmentMode.collectAsStateWithLifecycle()
    val assessmentOptions by audioViewModel.assessmentOptions.collectAsStateWithLifecycle()
    val prefs=LocalContext.current.getSharedPreferences("performance",0)
    var notation by rememberSaveable { mutableStateOf(prefs.getString("notation","staff") ?: "staff") }
    val controller = remember { ScoreViewerController() }
    val bytes = remember { AtomicReference<ByteArray?>(null) }
    val followingGeneration=remember { AtomicReference<Int?>(null) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var pendingPlay by remember { mutableStateOf(false) }
    var staffs by remember(state.loadGeneration) { mutableStateOf(listOf(0,1)) }
    var tracks by remember(state.loadGeneration) { mutableStateOf(emptyList<Int>()) }
    val snackbar = remember { SnackbarHostState() }
    val chrome = LocalPerformanceChrome.current
    val context = LocalContext.current
    val wide=LocalConfiguration.current.screenWidthDp>=600
    val diagnosticsHeight=LocalConfiguration.current.screenHeightDp.dp*.85f
    BackHandler { if(diagnostics) diagnostics=false else if(chrome.focused) chrome.focused=false else onExit() }
    DisposableEffect(context) {
        val window=(context as? android.app.Activity)?.window
        val bars=window?.let { WindowCompat.getInsetsController(it,it.decorView) }
        bars?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val start by rememberUpdatedState(newValue = {
        pendingPlay = false; controller.pause()
        audioViewModel.startFollowing(state.timeline, state.tempoBpm, state.seekTick, state.autoLocate)
    })
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else audioViewModel.onPermissionDenied()
    }
    val toggleFollow: () -> Unit = {
        if (follow.isFollowing) { viewModel.rememberPosition(follow.cursorTick); audioViewModel.stopListening() }
        else if (source == InputSource.MIDI || audioViewModel.hasRecordPermission()) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun resetToStart(){
        audioViewModel.stopListening();audioViewModel.resetAssessment();audioViewModel.seekToStart()
        viewModel.setAutoLocate(false);controller.seekTo(0);viewModel.rememberPosition(0)
    }
    fun prepareSelectionChange() {
        val tick=if(follow.isFollowing) follow.cursorTick else if(state.isPlaying) state.playbackTick else state.seekTick
        viewModel.rememberPosition(tick);audioViewModel.stopListening();controller.pause();pendingPlay=false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            if (audioViewModel.followState.value.isFollowing) viewModel.rememberPosition(audioViewModel.followState.value.cursorTick)
            audioViewModel.stopListening(); controller.pause(); pendingPlay = false
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); if (audioViewModel.followState.value.isFollowing) viewModel.rememberPosition(audioViewModel.followState.value.cursorTick); audioViewModel.stopListening(); controller.pause()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); viewModel.onViewerDetached() }
    }
    LaunchedEffect(state.loadedScore, state.loadGeneration, state.isViewerAttached) {
        state.loadedScore?.let { score -> bytes.set(score.bytes); if (state.isViewerAttached) controller.loadCurrentScore(state.loadGeneration) }
    }
    LaunchedEffect(notation,state.isScoreRendered) { controller.setNotationMode(notation) }
    LaunchedEffect(assessmentOptions.speed,state.isScoreRendered) { controller.setPracticeSpeed(assessmentOptions.speed) }
    LaunchedEffect(state.timeline, state.loadGeneration) {
        audioViewModel.bindAssessmentScore(state.timeline)
        audioViewModel.stopListening(); audioViewModel.seekTo(state.seekTick)
    }
    LaunchedEffect(state.timeline,state.loadGeneration,source) {
        if(!state.timeline.isEmpty && source==InputSource.MICROPHONE)audioViewModel.prepareFollowing()
    }
    LaunchedEffect(state.isScoreRendered, state.seekTick) {
        if (state.isScoreRendered) { controller.placeCursor(state.seekTick); audioViewModel.seekTo(state.seekTick) }
    }
    LaunchedEffect(follow.cursorTick, follow.position.eventIndex, follow.isFollowing, follow.confidence, state.navigationMode, state.autoPageTurn, state.userBrowsing) {
        val allowed = follow.isFollowing && com.pianoscorefollower.app.follower.PageNavigationPolicy.canFollow(follow.position, state.autoPageTurn, state.userBrowsing)
        val anchor=state.timeline.events.getOrNull(follow.position.eventIndex)
        controller.followPosition(follow.cursorTick,if(follow.isFollowing) anchor?.tick else null,
            anchor?.measure ?: follow.measure,state.navigationMode,allowed)
    }
    LaunchedEffect(follow.isFollowing) {
        if (follow.isFollowing) {
            followingGeneration.set(state.loadGeneration)
            while (true) {
                kotlinx.coroutines.delay(5000)
                viewModel.rememberPosition(audioViewModel.followState.value.cursorTick, updateAnchor = false)
            }
        } else {
            val generation=followingGeneration.getAndSet(null)
            if(generation!=null && generation==viewModel.uiState.value.loadGeneration)
                viewModel.rememberPosition(audioViewModel.followState.value.cursorTick)
        }
    }
    LaunchedEffect(state.isPlaying, state.autoPageTurn) { if (state.isPlaying) controller.navigation(state.navigationMode, state.autoPageTurn && !state.userBrowsing) }
    LaunchedEffect(pendingPlay, state.isPlayerReady) { if (pendingPlay && state.isPlayerReady) { pendingPlay = false; controller.play() } }
    LaunchedEffect(listening.errorMessage) { listening.errorMessage?.let { snackbar.showSnackbar(it); audioViewModel.clearError() } }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScoreViewerWebView(controller, { bytes.get() }, { event ->
                viewModel.onViewerEvent(event)
                if (event is ViewerEvent.ScoreStructure) tracks = event.selectedTracks
                if (event is ViewerEvent.SeekRequested) {
                    viewModel.setAutoLocate(false);viewModel.rememberPosition(event.tick)
                    if(assessmentMode && assessment.started && !assessment.finished) { audioViewModel.stopListening();audioViewModel.resetAssessment() }
                    audioViewModel.seekTo(event.tick)
                }
            }, Modifier.fillMaxSize())
            if (state.loadedScore == null) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Piano, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("打开琴谱，开始练习", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp)); Button(onClick = onImport) { Text("选择 MIDI / MusicXML") }
                com.pianoscorefollower.app.score.BuiltInSamples.filterNot { it.assetPath.contains("the_truth") }.take(2).forEach { sample -> TextButton(onClick = { onLoadSample(sample) }) { Text(sample.title) } }
            }
            if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (state.userBrowsing) FilledTonalButton(onClick = { viewModel.resumeFollow(); controller.resumeFollow() }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom=if(chrome.focused)12.dp else 80.dp)) { Text("回到跟随位置") }

            if(!chrome.focused) Surface(
                modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=12.dp,vertical=12.dp).widthIn(max=720.dp),
                shape=RoundedCornerShape(32.dp), tonalElevation=6.dp, shadowElevation=4.dp,
                color=MaterialTheme.colorScheme.surface.copy(alpha=.96f)) {
                Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick=onExit) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回曲库")}
                    if(wide) {
                        Column(Modifier.widthIn(max=180.dp).padding(end=8.dp)) {
                            Text(state.scoreTitle.ifBlank { "演奏" },maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
                            Text("第 ${(if(follow.isFollowing)follow.measure else state.currentMeasure)+1} 小节 · ${if(source==InputSource.MIDI) "MIDI" else "听音"}",style=MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick={viewModel.onPageChangedByUser(state.currentPage-1);controller.scrollToPage((state.currentPage-1).coerceAtLeast(0))},enabled=state.currentPage>0) {Icon(Icons.Default.ChevronLeft,"上一页")}
                    }
                    if(wide && !follow.isFollowing) IconButton(onClick=::resetToStart) {Icon(Icons.Default.Replay,"回到开头")}
                    FilledTonalButton(onClick={if(source==InputSource.MICROPHONE&&reference.error!=null)audioViewModel.prepareFollowing() else toggleFollow()},enabled=state.canFollow && !state.isLoading && (source==InputSource.MIDI||reference.ready||reference.error!=null),contentPadding=PaddingValues(horizontal=16.dp)) {
                        Icon(if(follow.isFollowing) Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if(source==InputSource.MICROPHONE&&reference.preparing) "准备跟谱…" else if(source==InputSource.MICROPHONE&&reference.error!=null) "重试跟谱准备" else if(follow.isFollowing) "暂停" else if(assessmentMode) "开始评分" else "开始跟谱")
                    }
                    if(wide) IconButton(onClick={controller.scrollToPage((state.currentPage+1).coerceAtMost(state.pageCount-1));viewModel.onPageChangedByUser(state.currentPage+1)},enabled=state.currentPage<state.pageCount-1) {Icon(Icons.Default.ChevronRight,"下一页")}
                    IconButton(onClick={diagnostics=true}) {Icon(Icons.Default.Tune,"演奏设置")}
                    IconButton(onClick={chrome.focused=true}) {Icon(Icons.Default.Fullscreen,"隐藏工具栏")}
                }
            }
            else Surface(Modifier.align(Alignment.TopEnd).padding(12.dp),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.8f)) {
                IconButton(onClick={chrome.focused=false}) {Icon(Icons.Default.FullscreenExit,"显示工具栏")}
            }
            if(!chrome.focused && follow.isFollowing && follow.position.status!=FollowStatus.FOLLOWING) Surface(Modifier.align(Alignment.TopCenter).padding(top=8.dp),shape=RoundedCornerShape(16.dp),tonalElevation=2.dp) {
                Text(if(source==InputSource.MICROPHONE&&listening.silentInput) "未收到麦克风声音，请检查输入设备或模拟器麦克风" else when(follow.position.status){FollowStatus.LOCATING->"定位中，请继续演奏";FollowStatus.LOST->"暂时失配，可点按谱面重新定位";else->"等待演奏"},Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)
            }
            if(assessmentMode && assessment.started && !chrome.focused) SuggestionChip(
                onClick={diagnostics=true},label={Text(assessment.score?.let { "%.1f 分 · 查看评分".format(it) } ?: "${assessment.correct}/${assessment.total} 音 · 查看评分")},
                modifier=Modifier.align(Alignment.TopEnd).padding(12.dp))
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=76.dp))
        }
        if (diagnostics) ModalBottomSheet(onDismissRequest={diagnostics=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true), modifier=Modifier.widthIn(max=680.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).heightIn(max=diagnosticsHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.scoreTitle.ifBlank { "演奏设置" },style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("第 ${(if(follow.isFollowing) follow.measure else state.currentMeasure)+1} 小节 · ${state.currentPage+1}/${state.pageCount.coerceAtLeast(1)} 页",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("深色演奏模式",Modifier.weight(1f));Switch(dark,onDarkChange) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("staff" to "五线谱","numbered" to "简谱").forEachIndexed { i,(id,label) ->
                        SegmentedButton(notation==id,{notation=id;prefs.edit().putString("notation",id).apply()},SegmentedButtonDefaults.itemShape(i,2)) { Text(label) }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "自由练习",true to "评分练习").forEachIndexed { i,(value,label) ->
                        SegmentedButton(assessmentMode==value,{audioViewModel.setAssessmentMode(value)},SegmentedButtonDefaults.itemShape(i,2)) { Text(label) }
                    }
                }
                if(assessmentMode && assessment.started) AssessmentSummary(assessment,
                    audioViewModel::finishAssessment,
                    {resetToStart()},
                    {tick->diagnostics=false;audioViewModel.stopListening();controller.placeCursor(tick);audioViewModel.seekTo(tick);viewModel.rememberPosition(tick)})
                HorizontalDivider()
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={viewModel.onPageChangedByUser(state.currentPage-1);controller.scrollToPage((state.currentPage-1).coerceAtLeast(0))},enabled=state.currentPage>0){Icon(Icons.Default.ChevronLeft,"上一页")}
                    Text("${state.currentPage+1}/${state.pageCount.coerceAtLeast(1)} 页",style=MaterialTheme.typography.bodySmall)
                    IconButton(onClick={controller.scrollToPage((state.currentPage+1).coerceAtMost(state.pageCount-1));viewModel.onPageChangedByUser(state.currentPage+1)},enabled=state.currentPage<state.pageCount-1){Icon(Icons.Default.ChevronRight,"下一页")}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(source == InputSource.MICROPHONE, { audioViewModel.setSource(InputSource.MICROPHONE) }, { Text("麦克风") })
                    FilterChip(source == InputSource.MIDI, { audioViewModel.setSource(InputSource.MIDI) }, { Text("USB MIDI") })
                }
                if (source == InputSource.MIDI) {
                    Text(midi.connected?.name ?: "等待 USB MIDI 设备", style = MaterialTheme.typography.bodySmall)
                    midi.endpoints.forEach { e -> TextButton(onClick = { audioViewModel.stopListening(); audioViewModel.midi.select(e) }) { Text(e.name) } }
                } else Text("输入电平 ${listening.levelDb.toInt()} dB · ${listening.sampleRate} Hz", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if(assessmentMode) "评分从选定位置开始" else "自动定位起弹位置", Modifier.weight(1f)); Switch(state.autoLocate && !assessmentMode, viewModel::setAutoLocate, Modifier.semantics { contentDescription="自动定位起弹位置" }, enabled=!assessmentMode)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(state.navigationMode == "line", { viewModel.setNavigationMode("line") }, { Text("逐行跟随") })
                    FilterChip(state.navigationMode == "page", { viewModel.setNavigationMode("page") }, { Text("分页跟随") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动移动谱面",Modifier.weight(1f))
                    Switch(state.autoPageTurn, viewModel::setAutoPageTurn, Modifier.semantics { contentDescription="自动移动谱面" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "高音谱表",1 to "低音谱表").forEach { (i,label) ->
                        FilterChip(i in staffs, { val selected=if(i in staffs) staffs-i else staffs+i
                            if(selected.isNotEmpty()) { prepareSelectionChange(); staffs=selected; controller.setStaffs(selected) }
                        }, { Text(label) })
                    }
                }
                if (state.trackNames.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.trackNames) { i, name -> FilterChip(i in tracks, {
                        val selected = if (i in tracks) tracks - i else tracks + i
                        if (selected.isNotEmpty()) { prepareSelectionChange(); tracks = selected; controller.setTracks(selected) }
                    }, { Text(name) }) }
                }
                if(assessmentMode) AssessmentSettings(assessmentOptions,!follow.isFollowing,audioViewModel::setAssessmentOptions)
                Text("音高候选：${follow.midiNotes.joinToString(" ") { midiNoteName(it) }.ifBlank { "—" }}\n目标：${follow.targetMidiNotes.joinToString(" ") { midiNoteName(it) }.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall)
                Text("匹配 ${(follow.bestSimilarity * 100).toInt()}% · 置信度 ${(follow.confidence * 100).toInt()}% · 第 ${follow.position.occurrence+1} 遍", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=::resetToStart) {Text("回到开头")}
                    OutlinedButton(onClick={if(state.isPlaying || pendingPlay){controller.pause();pendingPlay=false}else{audioViewModel.stopListening();pendingPlay=true;controller.ensureSoundFont()}},enabled=state.canPlay) {Text(if(state.isPlaying) "暂停示范" else if(pendingPlay) "加载音源…" else "播放示范")}
                }
                state.playerError?.let { TextButton(onClick={viewModel.onSoundFontRetry();controller.retrySoundFont();pendingPlay=true}){Text("音源失败，点此重试")} }
                TextButton(onClick = { pendingPlay = false; controller.stopPlayback() }, enabled = state.isPlaying || pendingPlay) { Text("停止示范") }
                HorizontalDivider()
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={diagnostics=false;onImport()}) {Text("导入乐谱")}
                    TextButton(onClick={diagnostics=false;onOpenSettings()}) {Text("应用设置")}
                    if(onSwitchToScroll!=null) TextButton(onClick={diagnostics=false;onSwitchToScroll()}){Text("图片琴谱")}
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
