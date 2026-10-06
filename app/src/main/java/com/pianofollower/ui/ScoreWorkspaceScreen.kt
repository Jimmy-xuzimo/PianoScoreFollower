package com.pianofollower.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianofollower.MainViewModel
import com.pianofollower.R
import com.pianofollower.ScoreUiState
import com.pianofollower.audio.AudioViewModel
import com.pianofollower.follower.PageTurnController
import com.pianofollower.follower.PageTurnReason
import com.pianofollower.score.BuiltInSamples
import com.pianofollower.score.SampleScore
import com.pianofollower.viewer.ScoreViewerController
import com.pianofollower.viewer.ScoreViewerWebView
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicReference

/** Below this width the workspace switches to the stacked, phone-friendly chrome. */
private const val COMPACT_WIDTH_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreWorkspaceScreen(
    onImport: () -> Unit,
    onLoadSample: (SampleScore) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchToScroll: (() -> Unit)?,
    viewModel: MainViewModel = viewModel(),
    audioViewModel: AudioViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listeningState by audioViewModel.listeningState.collectAsStateWithLifecycle()
    val followState by audioViewModel.followState.collectAsStateWithLifecycle()
    val isCompact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
    val controller = remember { ScoreViewerController() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scoreBytes = remember { AtomicReference<ByteArray?>(null) }
    val pageTurner = remember { PageTurnController() }
    val transportOwner = remember { Any() }

    val startCapture: () -> Unit = {
        if (state.canFollow) {
            audioViewModel.startFollowing(state.timeline, state.tempoBpm)
        } else {
            audioViewModel.startListening()
        }
    }
    val currentStartCapture by rememberUpdatedState(startCapture)

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentStartCapture() else audioViewModel.onPermissionDenied()
    }

    val toggleListening: () -> Unit = {
        when {
            listeningState.isRunning -> audioViewModel.stopListening()
            audioViewModel.hasRecordPermission() -> startCapture()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /*
     * A tap on play while the soundfont is still decoding is remembered rather than
     * dropped, so the request survives the wait instead of leaving the player stuck
     * on a hint with nothing happening.
     */
    var playRequested by remember { mutableStateOf(false) }

    // Playback starts from wherever the cursor currently sits, so a tap on the score
    // first, then play, is all it takes to rehearse from that point.
    val togglePlayback: () -> Unit = {
        when {
            state.isPlaying -> {
                playRequested = false
                controller.pause()
            }
            state.isPlayerReady -> controller.play()
            state.playerError != null -> controller.showHint(state.playerError!!)
            else -> {
                playRequested = true
                controller.showHint("音源正在加载，完成后自动开始播放…")
            }
        }
    }

    // The queued request fires the moment the soundfont finishes decoding.
    LaunchedEffect(state.isPlayerReady, playRequested) {
        if (playRequested && state.isPlayerReady) {
            playRequested = false
            controller.play()
        }
    }

    val retrySoundFont: () -> Unit = {
        playRequested = false
        viewModel.onSoundFontRetry()
        controller.retrySoundFont()
    }

    val stopPlayback: () -> Unit = { controller.stopPlayback() }

    val restart: () -> Unit = {
        if (followState.isFollowing) audioViewModel.seekToStart()
        controller.seekTo(0)
    }

    val goToPage: (Int) -> Unit = { raw ->
        val target = raw.coerceIn(0, maxOf(0, state.pageCount - 1))
        pageTurner.onManualPage(target)
        controller.scrollToPage(target)
        viewModel.onPageChangedByUser(target)
    }

    /*
     * The transport belongs to the shared bottom bar, on the same row as the tab
     * pills, rather than to a bar of this screen's own stacked above them.
     */
    val bottomBarHost = LocalBottomBarHost.current
    SideEffect {
        bottomBarHost.setTransport(
            transportOwner,
            if (state.loadedScore == null) {
                null
            } else {
                {
                    FollowTransport(
                        state = state,
                        onToggleAutoPageTurn = { viewModel.setAutoPageTurn(!state.autoPageTurn) },
                        onRestart = restart,
                        onTogglePlay = togglePlayback,
                        onStop = stopPlayback,
                        onPrevious = { goToPage(state.currentPage - 1) },
                        onNext = { goToPage(state.currentPage + 1) },
                        onRetrySoundFont = retrySoundFont,
                    )
                }
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { bottomBarHost.clearTransport(transportOwner) }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(listeningState.errorMessage) {
        val message = listeningState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        audioViewModel.clearError()
    }

    /*
     * The failure is announced once but stays in the state: the transport renders a
     * retry next to it, and dismissing it here would drop the player back into the
     * loading look with no way out.
     */
    LaunchedEffect(state.playerError) {
        val message = state.playerError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    // Leaving the app must not keep the microphone open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                audioViewModel.stopListening()
                controller.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioViewModel.stopListening()
        }
    }

    // Switching tabs tears the WebView down. The next visit builds a fresh one,
    // which will not re-announce itself through a state change, so the attachment
    // flag has to be cleared for the load to be triggered again.
    DisposableEffect(Unit) {
        onDispose { viewModel.onViewerDetached() }
    }

    // Hand the freshly imported bytes to the viewer page, then ask it to render.
    LaunchedEffect(state.isViewerAttached, state.loadGeneration) {
        val score = state.loadedScore
        if (state.isViewerAttached && score != null) {
            scoreBytes.set(score.bytes)
            controller.loadCurrentScore()
        }
    }

    LaunchedEffect(state.loadGeneration) {
        pageTurner.reset()
    }

    // The follower advances the cursor far faster than the UI needs to repaint, so
    // each tick is forwarded straight to the overlay instead of through composition.
    LaunchedEffect(Unit) {
        snapshotFlow { followState.isFollowing to followState.cursorTick }
            .distinctUntilChanged()
            .collect { (following, tick) ->
                if (following) controller.setCursorTick(tick) else controller.hideCursor()
            }
    }

    LaunchedEffect(
        followState.cursorTick,
        followState.confidence,
        followState.isFollowing,
        state.cursorPage,
        state.isLastMeasureOfPage,
        state.pageCount,
        state.autoPageTurn,
        state.loadGeneration,
    ) {
        if (!state.autoPageTurn || !followState.isFollowing) return@LaunchedEffect

        val decision = pageTurner.onCursor(
            cursorPage = state.cursorPage,
            cursorMeasure = followState.measure,
            pageCount = state.pageCount,
            isLastMeasureOfPage = state.isLastMeasureOfPage,
            measureProgress = state.timeline.progressInMeasure(
                followState.measure,
                followState.cursorTick,
            ),
            confidence = followState.confidence,
        )

        if (decision.reason != PageTurnReason.NONE) {
            controller.scrollToPage(decision.targetPage)
            viewModel.onPageChangedByUser(decision.targetPage)
        }
    }

    // During playback the cursor is driven by the player, so the page holding it is
    // simply scrolled into view once it moves on to the next one.
    LaunchedEffect(
        state.isPlaying,
        state.cursorPage,
        state.currentPage,
        state.autoPageTurn,
        state.pageCount,
    ) {
        if (!state.isPlaying || !state.autoPageTurn) return@LaunchedEffect
        val target = state.cursorPage
        if (target !in 0 until state.pageCount || target == state.currentPage) return@LaunchedEffect
        controller.scrollToPage(target)
        viewModel.onPageChangedByUser(target)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Insets are already consumed by the root column that hosts the tab bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            WorkspaceTopBar(
                title = state.scoreTitle.ifBlank { stringResource(R.string.app_name) },
                subtitle = buildSubtitle(state),
                hasScore = state.loadedScore != null,
                isLoading = state.isLoading,
                isListening = listeningState.isRunning,
                isCompact = isCompact,
                onToggleMic = toggleListening,
                onImport = onImport,
                onLoadSample = onLoadSample,
                onClearScore = { viewModel.clearLoadedScore() },
                onSwitchToScroll = onSwitchToScroll,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScoreViewerWebView(
                controller = controller,
                scoreBytesProvider = { scoreBytes.get() },
                onEvent = viewModel::onViewerEvent,
                modifier = Modifier.fillMaxSize(),
            )

            if (state.loadedScore == null) {
                EmptyState(
                    isLoading = state.isLoading,
                    onImport = onImport,
                    onLoadSample = onLoadSample,
                )
            } else {
                MicrophonePanel(
                    listening = listeningState,
                    follow = followState,
                    modifier = Modifier.fillMaxSize(),
                )
                // Decoding the sampled piano takes a moment; without a word from the
                // app the score just sits there and reads as a frozen screen.
                if (state.isPlayerLoading) {
                    SoundFontLoadingChip(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun buildSubtitle(state: ScoreUiState): String {
    val score = state.loadedScore ?: return "导入 MIDI 或 MusicXML 乐谱开始"
    val parts = mutableListOf(score.sourceFormat.displayName)
    if (state.trackNames.isNotEmpty()) parts += state.trackNames.joinToString(" / ")
    if (state.measureCount > 0) parts += "${state.measureCount} 小节"
    if (state.pageCount > 0) parts += "${state.pageCount} 页"
    return parts.joinToString("  ·  ")
}

/**
 * Current position. On a phone the units are dropped so the bar can hold a 48dp touch
 * target on every button without the readout pushing the row past the screen edge.
 */
private fun buildPositionLabel(state: ScoreUiState, compact: Boolean): String {
    val measure = when {
        state.measureCount <= 0 -> "—"
        compact -> "${state.currentMeasure + 1}/${state.measureCount}"
        else -> "第 ${state.currentMeasure + 1}/${state.measureCount} 小节"
    }
    val page = when {
        state.pageCount <= 0 -> null
        compact -> "${state.currentPage + 1}/${state.pageCount}"
        else -> "第 ${state.currentPage + 1}/${state.pageCount} 页"
    }
    return if (page != null) "$measure · $page" else measure
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceTopBar(
    title: String,
    subtitle: String,
    hasScore: Boolean,
    isLoading: Boolean,
    isListening: Boolean,
    isCompact: Boolean,
    onToggleMic: () -> Unit,
    onImport: () -> Unit,
    onLoadSample: (SampleScore) -> Unit,
    onClearScore: () -> Unit,
    onSwitchToScroll: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    var samplesOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }

            IconButton(onClick = onToggleMic) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (isListening) "停止聆听" else "开始聆听",
                    tint = if (isListening) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                if (isCompact) {
                    IconButton(onClick = { samplesOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = "示例乐谱",
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { samplesOpen = true },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("示例乐谱")
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = samplesOpen,
                    onDismissRequest = { samplesOpen = false },
                ) {
                    BuiltInSamples.forEach { sample ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sample.title)
                                    Text(
                                        text = sample.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                samplesOpen = false
                                onLoadSample(sample)
                            },
                        )
                    }
                }
            }

            if (isCompact) {
                IconButton(
                    onClick = onImport,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileOpen,
                        contentDescription = if (hasScore) "更换乐谱" else "导入乐谱",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasScore) "更换乐谱" else "导入乐谱")
                }
            }

            if (hasScore) {
                Box {
                    IconButton(
                        onClick = { overflowOpen = true },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("清除当前乐谱") },
                            onClick = {
                                overflowOpen = false
                                onClearScore()
                            },
                        )
                    }
                }
            }

            if (onSwitchToScroll != null) {
                IconButton(onClick = onSwitchToScroll) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "切换到滚动谱",
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                )
            }
        },
    )
}

/**
 * Transport for the score follower, drawn inside the shared bottom bar.
 *
 * Position, the auto-page-turn switch and all five playback buttons share one
 * line. This used to be two rows of its own sitting above the tab strip, which
 * together cost a band of staves at the bottom of every page.
 */
@Composable
private fun FollowTransport(
    state: ScoreUiState,
    onToggleAutoPageTurn: () -> Unit,
    onRestart: () -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetrySoundFont: () -> Unit,
) {
    // Play/pause, a spinner while the samples decode, or a retry once that failed.
    val primaryState = when {
        state.playerError != null -> PrimaryActionState.Failed
        state.isPlaying -> PrimaryActionState.Pause
        state.isPlayerLoading -> PrimaryActionState.Loading
        else -> PrimaryActionState.Play
    }

    /*
     * Every control keeps its 48dp touch target, so on a phone the two set-and-forget
     * actions move to the top bar's overflow menu and the bar keeps only the position
     * readout and the four playback actions.
     */
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildPositionLabel(state, compact),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))

        if (!compact) {
            AutoPageToggle(
                on = state.autoPageTurn,
                enabled = state.canFollow,
                onToggle = onToggleAutoPageTurn,
            )
            TransportIconButton(
                icon = Icons.Filled.RestartAlt,
                contentDescription = "回到开头",
                onClick = onRestart,
                enabled = state.canPlay,
            )
        }

        TransportIconButton(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = "上一页",
            onClick = onPrevious,
            enabled = state.currentPage > 0,
        )
        RoundPrimaryButton(
            state = primaryState,
            onClick = if (primaryState == PrimaryActionState.Failed) {
                onRetrySoundFont
            } else {
                onTogglePlay
            },
            enabled = state.canPlay,
        )
        TransportIconButton(
            icon = Icons.Filled.ChevronRight,
            contentDescription = "下一页",
            onClick = onNext,
            enabled = state.currentPage < state.pageCount - 1,
        )
        TransportIconButton(
            icon = Icons.Filled.Stop,
            contentDescription = "停止",
            onClick = onStop,
            enabled = state.isPlaying,
        )
    }
}

/**
 * A quiet "still working" note shown while the sampled piano is being decoded, so a
 * slow load is visibly progress rather than an apparently frozen score.
 */
@Composable
private fun SoundFontLoadingChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "音源加载中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    isLoading: Boolean,
    onImport: () -> Unit,
    onLoadSample: (SampleScore) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .heightIn(max = 560.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "导入你的乐谱",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "支持 MIDI（.mid / .midi）与 MusicXML（.musicxml / .xml / .mxl）。\n导入后自动排版为完整五线谱。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onImport,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("选择乐谱文件")
                }
            }

            Text(
                text = "或先试试内置示例",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            BuiltInSamples.forEach { sample ->
                OutlinedButton(
                    onClick = { onLoadSample(sample) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(sample.title)
                        Text(
                            text = sample.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}