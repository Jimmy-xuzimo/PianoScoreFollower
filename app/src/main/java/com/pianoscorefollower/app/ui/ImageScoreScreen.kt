package com.pianoscorefollower.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianoscorefollower.app.image.ImageScorePage
import com.pianoscorefollower.app.image.ImageScoreViewModel
import com.pianoscorefollower.app.image.MAX_SCROLL_DURATION_SECONDS
import com.pianoscorefollower.app.image.MIN_SCROLL_DURATION_SECONDS
import com.pianoscorefollower.app.image.PageLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Below this width the transport controls stack instead of sharing one row. */
private const val SHEET_COMPACT_WIDTH_DP = 600

/** Gap drawn between two consecutive pages. */
private val PAGE_GAP = 10.dp

/** Roughly 40 MB of decoded pages, which covers a few screens' worth either side. */
private const val PAGE_CACHE_BYTES = 40 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScoreScreen(
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchToNotation: (() -> Unit)?,
    viewModel: ImageScoreViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isCompact = LocalConfiguration.current.screenWidthDp < SHEET_COMPACT_WIDTH_DP
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { PAGE_GAP.roundToPx() }
    val transportOwner = remember { Any() }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var showManager by remember { mutableStateOf(false) }
    var durationDialogOpen by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    val layout = remember(state.pages, viewport, gapPx) {
        PageLayout(
            aspects = state.pages.map { page -> page.width.toFloat() / page.height },
            viewportWidth = viewport.width,
            viewportHeight = viewport.height,
            gapPx = gapPx,
        )
    }
    val currentLayout by rememberUpdatedState(layout)

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissStatus()
    }

    // Constant-speed playback: every frame advances the list by exactly the number
    // of pixels that keeps the whole stack on screen for the configured duration.
    LaunchedEffect(state.isPlaying, layout.maxScroll, state.totalDurationSeconds) {
        if (!state.isPlaying || !layout.isReady || layout.maxScroll <= 0) return@LaunchedEffect
        val pixelsPerMs = layout.maxScroll.toFloat() / (state.totalDurationSeconds * 1000f)
        var previous = withFrameNanos { it }

        while (isActive) {
            val now = withFrameNanos { it }
            // Clamping the step keeps a stalled frame from jumping the score ahead.
            val elapsedMs = ((now - previous) / 1_000_000f).coerceIn(0f, 64f)
            previous = now

            if (listState.pixelOffset(layout) >= layout.maxScroll) {
                viewModel.saveScrollOffset(layout.maxScroll)
                viewModel.setPlaying(false)
                break
            }
            listState.scrollBy(pixelsPerMs * elapsedMs)
        }
    }

    // Coming back from another tab should land where practice was left off.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(layout.isReady, state.pages.size) {
        if (restored || !layout.isReady || state.pages.isEmpty()) return@LaunchedEffect
        restored = true
        val target = state.scrollOffset.coerceIn(0, layout.maxScroll)
        if (target > 0) {
            val (index, within) = layout.itemAt(target)
            listState.scrollToItem(index, within)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveScrollOffset(listState.pixelOffset(currentLayout))
        }
    }

    val pauseForManualControl: () -> Unit = {
        if (state.isPlaying) viewModel.setPlaying(false)
    }

    val togglePlay: () -> Unit = {
        when {
            state.isPlaying -> pauseForManualControl()
            !layout.isReady || layout.maxScroll <= 0 -> Unit
            else -> scope.launch {
                // Starting from the very end would otherwise finish on the first
                // frame, so a replay first rewinds to the top.
                if (listState.pixelOffset(layout) >= layout.maxScroll) {
                    listState.scrollToItem(0, 0)
                }
                viewModel.setPlaying(true)
            }
        }
    }

    val restart: () -> Unit = {
        viewModel.setPlaying(false)
        scope.launch { listState.scrollToItem(0, 0) }
        viewModel.saveScrollOffset(0)
    }

    /*
     * The scroll transport belongs to the shared bottom bar, on the same row as the
     * tab pills, rather than to a bar of this screen's own stacked above them.
     */
    val bottomBarHost = LocalBottomBarHost.current
    val showTransport = state.pages.isNotEmpty() && !showManager
    SideEffect {
        bottomBarHost.setTransport(
            transportOwner,
            if (!showTransport) {
                null
            } else {
                {
                    SheetTransport(
                        isCompact = isCompact,
                        isPlaying = state.isPlaying,
                        canScroll = layout.maxScroll > 0,
                        layout = layout,
                        listState = listState,
                        durationSeconds = state.totalDurationSeconds,
                        onTogglePlay = togglePlay,
                        onRestart = restart,
                        onStop = {
                            viewModel.setPlaying(false)
                            scope.launch { listState.scrollToItem(0, 0) }
                        },
                        onOpenDuration = { durationDialogOpen = true },
                        onSeek = { fraction ->
                            if (state.isPlaying) pauseForManualControl()
                            seekJob?.cancel()
                            val target = (fraction * layout.maxScroll).roundToInt()
                            seekJob = scope.launch {
                                val (index, within) = layout.itemAt(target)
                                listState.scrollToItem(index, within)
                            }
                        },
                    )
                }
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { bottomBarHost.clearTransport(transportOwner) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ImageScoreTopBar(
                pageCount = state.pages.size,
                durationSeconds = state.totalDurationSeconds,
                isCompact = isCompact,
                isImporting = state.isImporting,
                isManaging = showManager,
                onImport = onImport,
                onToggleManager = { showManager = !showManager },
                onSwitchToNotation = onSwitchToNotation,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                showManager -> PageManager(
                    pages = state.pages,
                    onImport = onImport,
                    onMove = viewModel::movePage,
                    onRemove = viewModel::removePage,
                    onClear = viewModel::clearPages,
                )

                state.pages.isNotEmpty() -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .onSizeChanged { viewport = it }
                        .pointerInput(state.isPlaying) {
                            if (!state.isPlaying) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    pauseForManualControl()
                                    // Swallow the rest of this gesture so one drag
                                    // cannot pause more than once.
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                ) {
                    items(state.pages, key = { it.id }) { page ->
                        PageImage(page = page)
                    }
                }

                else -> ImageScoreEmptyState(
                    isImporting = state.isImporting,
                    onImport = onImport,
                )
            }

            if (state.isImporting && state.pages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在导入页面…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (durationDialogOpen) {
        DurationDialog(
            seconds = state.totalDurationSeconds,
            onChange = viewModel::setDurationSeconds,
            onDismiss = { durationDialogOpen = false },
        )
    }
}

/** Pixel offset of the list's top edge within the whole content. */
private fun LazyListState.pixelOffset(layout: PageLayout): Int {
    if (layout.pageCount == 0) return 0
    val index = firstVisibleItemIndex.coerceIn(0, layout.pageCount - 1)
    return (layout.starts[index] + firstVisibleItemScrollOffset).coerceIn(0, layout.maxScroll)
}

@Composable
private fun PageImage(page: ImageScorePage) {
    val bitmap = rememberPageBitmap(page)
    val aspect = page.width.toFloat() / page.height

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // The box already carries the page's aspect ratio, so filling the
                // width is exact and never crops a stave.
                contentScale = ContentScale.FillWidth,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF8A94A2),
            )
        }
    }
}

@Composable
private fun rememberPageBitmap(page: ImageScorePage) = produceState<Bitmap?>(
    initialValue = PageBitmapCache.get(page.file.path),
    page.id,
    page.file.path,
) {
    value = withContext(Dispatchers.IO) {
        PageBitmapCache.get(page.file.path) ?: PageBitmapCache.load(page.file.path)
    }
}.value

/**
 * Decoded pages are large and only a handful are ever on screen, so they are held
 * in a byte-bounded LRU instead of being decoded on every scroll.
 */
private object PageBitmapCache {

    private val cache = object : LruCache<String, Bitmap>(PAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun load(path: String): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        cache.put(path, bitmap)
        return bitmap
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageScoreTopBar(
    pageCount: Int,
    durationSeconds: Int,
    isCompact: Boolean,
    isImporting: Boolean,
    isManaging: Boolean,
    onImport: () -> Unit,
    onToggleManager: () -> Unit,
    onSwitchToNotation: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
            Column {
                Text(
                    text = "图片滚动谱",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pageCount > 0) {
                        "$pageCount 页 · 总时长 ${formatClock(durationSeconds)} · 已保存"
                    } else {
                        "从相册或文件导入 PDF / 照片，自动保存"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (pageCount > 0) {
                TextButton(onClick = onToggleManager) {
                    Text(if (isManaging) "完成" else "管理")
                }
            }

            if (isCompact) {
                IconButton(onClick = onImport, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加页面",
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
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加页面")
                }
            }

            if (onSwitchToNotation != null) {
                IconButton(onClick = onSwitchToNotation) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = "切换到五线谱",
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
 * Transport for the scrolling score, drawn inside the shared bottom bar.
 *
 * Playback buttons, the seek slider and the clock all share one line; the page
 * readout only appears when there is room for a second line of text, since the
 * clock is the number the player actually watches.
 */
@Composable
private fun SheetTransport(
    isCompact: Boolean,
    isPlaying: Boolean,
    canScroll: Boolean,
    layout: PageLayout,
    listState: LazyListState,
    durationSeconds: Int,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onOpenDuration: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val maxScroll = layout.maxScroll
    val offset = listState.pixelOffset(layout)
    val progress = if (maxScroll > 0) (offset.toFloat() / maxScroll).coerceIn(0f, 1f) else 0f
    val elapsedSeconds = (progress * durationSeconds).roundToInt()
    val (index, within) = layout.itemAt(offset)
    val pageLabel = if (layout.pageCount > 0) {
        val withinFraction = if (layout.itemHeights[index] > 0) {
            within.toFloat() / layout.itemHeights[index]
        } else {
            0f
        }
        "第 ${index + 1}/${layout.pageCount} 页 · ${(withinFraction * 100).roundToInt()}%"
    } else {
        "—"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportIconButton(
            icon = Icons.Filled.RestartAlt,
            contentDescription = "回到开头",
            onClick = onRestart,
        )
        RoundPrimaryButton(
            state = if (isPlaying) PrimaryActionState.Pause else PrimaryActionState.Play,
            onClick = onTogglePlay,
            enabled = canScroll,
        )
        TransportIconButton(
            icon = Icons.Filled.Stop,
            contentDescription = "停止",
            onClick = onStop,
        )
        TransportIconButton(
            icon = Icons.Filled.Timer,
            contentDescription = "设置总时长",
            onClick = onOpenDuration,
        )

        Spacer(Modifier.width(6.dp))
        Slider(
            value = progress,
            onValueChange = onSeek,
            enabled = canScroll,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${formatClock(elapsedSeconds)} / ${formatClock(durationSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (!isCompact) {
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DurationDialog(
    seconds: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置全曲总时长") },
        text = {
            Column {
                Text(
                    text = formatClock(seconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = seconds.toFloat(),
                    onValueChange = { onChange(it.roundToInt()) },
                    valueRange = MIN_SCROLL_DURATION_SECONDS.toFloat()..MAX_SCROLL_DURATION_SECONDS.toFloat(),
                )
                Text(
                    text = "滚动速度按这个时长均分整份谱面，之后可随时暂停或拖动调整。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 120, 180, 300).forEach { preset ->
                        OutlinedButton(onClick = { onChange(preset) }) {
                            Text(formatClock(preset), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun PageManager(
    pages: List<ImageScorePage>,
    onImport: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共 ${pages.size} 页 · 拖动顺序即演奏顺序",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClear, enabled = pages.isNotEmpty()) {
                Text("清空")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                PageManagerRow(
                    index = index,
                    page = page,
                    canMoveUp = index > 0,
                    canMoveDown = index < pages.lastIndex,
                    onMove = onMove,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
private fun PageManagerRow(
    index: Int,
    page: ImageScorePage,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val bitmap = rememberPageBitmap(page)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 62.dp)
                    .background(Color.White, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第 ${index + 1} 页",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = page.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onMove(index, index - 1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = { onMove(index, index + 1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
            }
            IconButton(onClick = { onRemove(page.id) }) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImageScoreEmptyState(isImporting: Boolean, onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text("导入纸质谱或 PDF", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "可从相册一次选中多张照片，也可从文件中选择 PDF 或多个图片。" +
                    "页面按文件名顺序竖向排列，设定总时长即可从第一页匀速滚到最后一页。" +
                    "导入的内容会一直保存在应用内，下次打开仍在。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("添加页面")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "提示：播放中触摸画面即可暂停并手动拖动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** `m:ss`, which is the right resolution for a multi-minute scroll. */
internal fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}
