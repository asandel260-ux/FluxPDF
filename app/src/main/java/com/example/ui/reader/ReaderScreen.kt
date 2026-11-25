package com.example.ui.reader

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AnnotationEntity
import com.example.data.NormalizedPoint
import com.example.data.ScrollMode
import com.example.viewmodel.ActiveAnnotationTool
import com.example.viewmodel.ReaderEvent
import com.example.viewmodel.ReaderLoadState
import com.example.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ANNOTATION_PALETTE = listOf(
    0x88F5C542L, // Amber Highlighter
    0xCCC8412AL, // Archival Vermilion
    0xBB2E8B57L, // Sage Green
    0xCC2A6BB5L, // Cobalt Blue
    0xEE181A1FL, // Deep Charcoal Ink
)

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onCloseReader: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNavSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showAnnotateStrip by remember { mutableStateOf(false) }
    var pendingStickyNote by remember { mutableStateOf<Pair<Int, NormalizedPoint>?>(null) }
    var editingStickyNote by remember { mutableStateOf<AnnotationEntity?>(null) }

    // Zoom & pan state shared cleanly across the canvas
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Keep screen awake when toggled
    DisposableEffect(prefs.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Back handler exits annotation mode or zoom first before returning to Library
    BackHandler {
        when {
            uiState.activeTool != ActiveAnnotationTool.NONE -> {
                viewModel.selectAnnotationTool(ActiveAnnotationTool.NONE)
                showAnnotateStrip = false
            }
            zoomScale > 1.05f -> {
                zoomScale = 1f
                panOffset = Offset.Zero
            }
            else -> onCloseReader()
        }
    }

    // Handle one-shot events (toast messages, exports, shares)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReaderEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ReaderEvent.ShareMarkdownText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "${event.title} — FluxPDF Notes")
                        putExtra(Intent.EXTRA_TEXT, event.markdown)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Annotations"))
                }
                is ReaderEvent.SharePdfFile -> {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            event.file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, event.title)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                    }.onFailure {
                        snackbarHostState.showSnackbar("Unable to share PDF: ${it.message}")
                    }
                }
                is ReaderEvent.JumpToPage -> Unit
            }
        }
    }

    val backdrop = readingBackdropColor(prefs.readingColorMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop),
    ) {
        when (val load = uiState.loadState) {
            is ReaderLoadState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Mapping PDF objects…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            is ReaderLoadState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Unable to Open Document",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = load.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onCloseReader) { Text("Back to Library") }
                }
            }

            is ReaderLoadState.Ready -> {
                val session = load.session
                val annotationsByPage = remember(uiState.annotations) {
                    uiState.annotations.groupBy { it.pageIndex }
                }

                if (uiState.isReflowMode) {
                    ReflowReaderView(
                        pageIndex = uiState.currentPageIndex,
                        pageCount = session.pageCount,
                        extractedText = uiState.reflowPageText,
                        fontScale = prefs.reflowFontScale,
                        readingColorMode = prefs.readingColorMode,
                        onTapCanvas = { viewModel.toggleChrome() },
                        onPreviousPage = { viewModel.stepPage(-1) },
                        onNextPage = { viewModel.stepPage(1) },
                        onCopiedMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val viewportWidthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(360) }
                        val maxPanX = with(density) { maxWidth.toPx() * (zoomScale - 1f) * 0.55f }
                        val maxPanY = with(density) { maxHeight.toPx() * (zoomScale - 1f) * 0.55f }

                        val zoomGestureModifier = if (uiState.activeTool == ActiveAnnotationTool.NONE) {
                            Modifier.pointerInput(prefs.scrollMode, zoomScale > 1.05f) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (zoomScale * zoom).coerceIn(1f, 4.5f)
                                    zoomScale = newScale
                                    panOffset = if (newScale <= 1.02f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY),
                                        )
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }

                        val toggleDoubleTapZoom: () -> Unit = {
                            if (zoomScale > 1.15f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2.25f
                                panOffset = Offset.Zero
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(zoomGestureModifier)
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                        ) {
                            when (prefs.scrollMode) {
                                ScrollMode.VERTICAL_CONTINUOUS -> {
                                    val listState = rememberLazyListState(
                                        initialFirstVisibleItemIndex = uiState.currentPageIndex,
                                    )

                                    LaunchedEffect(viewModel) {
                                        viewModel.events.collect { ev ->
                                            if (ev is ReaderEvent.JumpToPage) {
                                                listState.scrollToItem(ev.pageIndex)
                                            }
                                        }
                                    }

                                    LaunchedEffect(listState) {
                                        snapshotFlow {
                                            val layout = listState.layoutInfo
                                            val centerY = layout.viewportStartOffset + layout.viewportSize.height / 2
                                            layout.visibleItemsInfo.minByOrNull { item ->
                                                kotlin.math.abs((item.offset + item.size / 2) - centerY)
                                            }?.index ?: listState.firstVisibleItemIndex
                                        }.distinctUntilChanged().collect { visibleIdx ->
                                            viewModel.onPageVisible(visibleIdx, viewportWidthPx)
                                        }
                                    }

                                    LazyColumn(
                                        state = listState,
                                        userScrollEnabled = uiState.activeTool == ActiveAnnotationTool.NONE,
                                        contentPadding = PaddingValues(
                                            top = 92.dp,
                                            bottom = 116.dp,
                                            start = 10.dp,
                                            end = 10.dp,
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(count = session.pageCount, key = { it }) { pageIdx ->
                                            Box(
                                                modifier = Modifier.widthIn(max = 860.dp),
                                            ) {
                                                PdfPageSurface(
                                                    session = session,
                                                    pageIndex = pageIdx,
                                                    readingColorMode = prefs.readingColorMode,
                                                    autoCropMargins = prefs.autoCropMargins,
                                                    annotations = annotationsByPage[pageIdx].orEmpty(),
                                                    activeTool = uiState.activeTool,
                                                    selectedInkColorArgb = uiState.selectedInkColorArgb,
                                                    onTapPage = { viewModel.toggleChrome() },
                                                    onDoubleTapPage = toggleDoubleTapZoom,
                                                    onCommitStroke = { type, pts ->
                                                        viewModel.commitStrokeAnnotation(pageIdx, type, pts)
                                                    },
                                                    onRequestStickyNoteAt = { pt ->
                                                        pendingStickyNote = pageIdx to pt
                                                    },
                                                    onTapStickyNote = { editingStickyNote = it },
                                                    zoomBoostScale = if (zoomScale > 1.3f) zoomScale else 1f,
                                                )
                                            }
                                        }
                                    }
                                }

                                ScrollMode.HORIZONTAL_PAGED -> {
                                    val pagerState = rememberPagerState(
                                        initialPage = uiState.currentPageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                                        pageCount = { session.pageCount },
                                    )

                                    LaunchedEffect(viewModel) {
                                        viewModel.events.collect { ev ->
                                            if (ev is ReaderEvent.JumpToPage) {
                                                pagerState.scrollToPage(ev.pageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)))
                                            }
                                        }
                                    }

                                    LaunchedEffect(pagerState) {
                                        snapshotFlow { pagerState.currentPage }
                                            .distinctUntilChanged()
                                            .collect { p -> viewModel.onPageVisible(p, viewportWidthPx) }
                                    }

                                    HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = uiState.activeTool == ActiveAnnotationTool.NONE && zoomScale <= 1.05f,
                                        modifier = Modifier.fillMaxSize(),
                                    ) { pageIdx ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp, vertical = 72.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(modifier = Modifier.widthIn(max = 820.dp)) {
                                                PdfPageSurface(
                                                    session = session,
                                                    pageIndex = pageIdx,
                                                    readingColorMode = prefs.readingColorMode,
                                                    autoCropMargins = prefs.autoCropMargins,
                                                    annotations = annotationsByPage[pageIdx].orEmpty(),
                                                    activeTool = uiState.activeTool,
                                                    selectedInkColorArgb = uiState.selectedInkColorArgb,
                                                    onTapPage = { viewModel.toggleChrome() },
                                                    onDoubleTapPage = toggleDoubleTapZoom,
                                                    onCommitStroke = { type, pts ->
                                                        viewModel.commitStrokeAnnotation(pageIdx, type, pts)
                                                    },
                                                    onRequestStickyNoteAt = { pt ->
                                                        pendingStickyNote = pageIdx to pt
                                                    },
                                                    onTapStickyNote = { editingStickyNote = it },
                                                    zoomBoostScale = if (zoomScale > 1.3f) zoomScale else 1f,
                                                )
                                            }
                                        }
                                    }
                                }

                                ScrollMode.DOUBLE_PAGE_SPREAD -> {
                                    val spreadCount = ((session.pageCount + 1) / 2).coerceAtLeast(1)
                                    val pagerState = rememberPagerState(
                                        initialPage = (uiState.currentPageIndex / 2).coerceIn(0, spreadCount - 1),
                                        pageCount = { spreadCount },
                                    )

                                    LaunchedEffect(viewModel) {
                                        viewModel.events.collect { ev ->
                                            if (ev is ReaderEvent.JumpToPage) {
                                                pagerState.scrollToPage((ev.pageIndex / 2).coerceIn(0, spreadCount - 1))
                                            }
                                        }
                                    }

                                    LaunchedEffect(pagerState) {
                                        snapshotFlow { pagerState.currentPage }
                                            .distinctUntilChanged()
                                            .collect { spread ->
                                                viewModel.onPageVisible((spread * 2).coerceAtMost(session.pageCount - 1), viewportWidthPx / 2)
                                            }
                                    }

                                    HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = uiState.activeTool == ActiveAnnotationTool.NONE && zoomScale <= 1.05f,
                                        modifier = Modifier.fillMaxSize(),
                                    ) { spreadIdx ->
                                        val leftIdx = spreadIdx * 2
                                        val rightIdx = leftIdx + 1
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp, vertical = 72.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                PdfPageSurface(
                                                    session = session,
                                                    pageIndex = leftIdx,
                                                    readingColorMode = prefs.readingColorMode,
                                                    autoCropMargins = prefs.autoCropMargins,
                                                    annotations = annotationsByPage[leftIdx].orEmpty(),
                                                    activeTool = uiState.activeTool,
                                                    selectedInkColorArgb = uiState.selectedInkColorArgb,
                                                    onTapPage = { viewModel.toggleChrome() },
                                                    onDoubleTapPage = toggleDoubleTapZoom,
                                                    onCommitStroke = { type, pts ->
                                                        viewModel.commitStrokeAnnotation(leftIdx, type, pts)
                                                    },
                                                    onRequestStickyNoteAt = { pt ->
                                                        pendingStickyNote = leftIdx to pt
                                                    },
                                                    onTapStickyNote = { editingStickyNote = it },
                                                    zoomBoostScale = if (zoomScale > 1.3f) zoomScale else 1f,
                                                )
                                            }
                                            if (rightIdx < session.pageCount) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PdfPageSurface(
                                                        session = session,
                                                        pageIndex = rightIdx,
                                                        readingColorMode = prefs.readingColorMode,
                                                        autoCropMargins = prefs.autoCropMargins,
                                                        annotations = annotationsByPage[rightIdx].orEmpty(),
                                                        activeTool = uiState.activeTool,
                                                        selectedInkColorArgb = uiState.selectedInkColorArgb,
                                                        onTapPage = { viewModel.toggleChrome() },
                                                        onDoubleTapPage = toggleDoubleTapZoom,
                                                        onCommitStroke = { type, pts ->
                                                            viewModel.commitStrokeAnnotation(rightIdx, type, pts)
                                                        },
                                                        onRequestStickyNoteAt = { pt ->
                                                            pendingStickyNote = rightIdx to pt
                                                        },
                                                        onTapStickyNote = { editingStickyNote = it },
                                                        zoomBoostScale = if (zoomScale > 1.3f) zoomScale else 1f,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Telemetry HUD Overlay
                if (prefs.showTelemetryHud) {
                    val tel = uiState.telemetry
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC121418),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = if (uiState.isChromeVisible) 70.dp else 10.dp, end = 12.dp),
                    ) {
                        Text(
                            text = "${"%.1f".format(tel.lastRenderMs)}ms (p95 ${"%.1f".format(tel.p95RenderMs)}ms) • ${"%.1f".format(tel.cacheUsedMb)}MB • ${tel.hitRatePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8BF0B0),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Zoom Reset Pill when zoomed in
                AnimatedVisibility(
                    visible = zoomScale > 1.08f && !uiState.isReflowMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = if (uiState.isChromeVisible) 70.dp else 12.dp, start = 14.dp),
                ) {
                    Surface(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
                    ) {
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%  •  Reset",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                // Floating Top Editorial Chrome Bar
                AnimatedVisibility(
                    visible = uiState.isChromeVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Column {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = onCloseReader,
                                    modifier = Modifier.testTag("reader_back_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to library",
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = load.document.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val activeChapter = load.outlines.lastOrNull { it.pageIndex <= uiState.currentPageIndex }?.title
                                    if (activeChapter != null) {
                                        Text(
                                            text = activeChapter,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (uiState.jumpHistoryBackStack.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.navigateJumpHistoryBack() },
                                        modifier = Modifier.testTag("reader_jump_back_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Return to previous page",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSearchSheet = true },
                                    modifier = Modifier.testTag("reader_search_button"),
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search document")
                                }
                                IconButton(
                                    onClick = { viewModel.toggleReflowMode() },
                                    modifier = Modifier.testTag("reader_reflow_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subject,
                                        contentDescription = "Toggle text reflow mode",
                                        tint = if (uiState.isReflowMode) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleCurrentPageBookmark() },
                                    modifier = Modifier.testTag("reader_bookmark_button"),
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isCurrentPageBookmarked) {
                                            Icons.Default.Bookmark
                                        } else {
                                            Icons.Default.BookmarkBorder
                                        },
                                        contentDescription = "Bookmark current page",
                                        tint = if (uiState.isCurrentPageBookmarked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = { showInspectorSheet = true },
                                    modifier = Modifier.testTag("reader_inspector_button"),
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "Document inspector")
                                }
                            }
                        }

                        // Active Search Match Stepper Strip
                        val activeHits = uiState.searchProgress?.hits.orEmpty()
                        if (uiState.searchQuery.isNotBlank() && activeHits.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "\"${uiState.searchQuery}\"  •  ${(uiState.activeSearchHitIndex + 1).coerceAtLeast(1)} / ${activeHits.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { viewModel.stepSearchHit(-1) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Previous search match",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.stepSearchHit(1) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Next search match",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close search bar",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Bottom Scrubber + Annotation & Tool Dock
                AnimatedVisibility(
                    visible = uiState.isChromeVisible || uiState.activeTool != ActiveAnnotationTool.NONE,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            // Expandable Annotation Toolbar Strip
                            AnimatedVisibility(visible = showAnnotateStrip || uiState.activeTool != ActiveAnnotationTool.NONE) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AnnotationToolChip(
                                            label = "Highlight",
                                            icon = Icons.Default.BorderColor,
                                            selected = uiState.activeTool == ActiveAnnotationTool.HIGHLIGHT,
                                            onClick = { viewModel.selectAnnotationTool(ActiveAnnotationTool.HIGHLIGHT) },
                                        )
                                        AnnotationToolChip(
                                            label = "Underline",
                                            icon = Icons.Default.FormatUnderlined,
                                            selected = uiState.activeTool == ActiveAnnotationTool.UNDERLINE,
                                            onClick = { viewModel.selectAnnotationTool(ActiveAnnotationTool.UNDERLINE) },
                                        )
                                        AnnotationToolChip(
                                            label = "Ink",
                                            icon = Icons.Default.Draw,
                                            selected = uiState.activeTool == ActiveAnnotationTool.INK,
                                            onClick = { viewModel.selectAnnotationTool(ActiveAnnotationTool.INK) },
                                        )
                                        AnnotationToolChip(
                                            label = "Note",
                                            icon = Icons.Default.StickyNote2,
                                            selected = uiState.activeTool == ActiveAnnotationTool.STICKY_NOTE,
                                            onClick = { viewModel.selectAnnotationTool(ActiveAnnotationTool.STICKY_NOTE) },
                                        )
                                        IconButton(onClick = { viewModel.undoLastAnnotationOnCurrentPage() }) {
                                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo annotation")
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ANNOTATION_PALETTE.forEach { argb ->
                                                val selected = uiState.selectedInkColorArgb == argb
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(argb.toInt()))
                                                        .border(
                                                            width = if (selected) 2.5.dp else 1.dp,
                                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                                            shape = CircleShape,
                                                        )
                                                        .clickable { viewModel.setSelectedInkColor(argb) },
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                viewModel.selectAnnotationTool(ActiveAnnotationTool.NONE)
                                                showAnnotateStrip = false
                                            },
                                        ) {
                                            Text("Done")
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }

                            // Precision Page Scrubber Row
                            if (session.pageCount > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        onClick = { showJumpDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.testTag("reader_page_counter"),
                                    ) {
                                        Text(
                                            text = "${uiState.currentPageIndex + 1} / ${session.pageCount}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Slider(
                                        value = uiState.currentPageIndex.toFloat(),
                                        onValueChange = { target ->
                                            viewModel.jumpToPage(target.roundToInt(), recordHistory = false)
                                        },
                                        valueRange = 0f..(session.pageCount - 1).coerceAtLeast(1).toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reader_page_scrubber"),
                                    )
                                }
                            }

                            // Primary Bottom Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BottomToolAction(
                                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    label = "Contents",
                                    active = false,
                                    testTag = "reader_contents_button",
                                    onClick = { showNavSheet = true },
                                )
                                BottomToolAction(
                                    icon = Icons.Default.Draw,
                                    label = "Annotate",
                                    active = showAnnotateStrip || uiState.activeTool != ActiveAnnotationTool.NONE,
                                    testTag = "reader_annotate_button",
                                    onClick = {
                                        val next = !showAnnotateStrip
                                        showAnnotateStrip = next
                                        if (next && uiState.activeTool == ActiveAnnotationTool.NONE) {
                                            viewModel.selectAnnotationTool(ActiveAnnotationTool.HIGHLIGHT)
                                        } else if (!next) {
                                            viewModel.selectAnnotationTool(ActiveAnnotationTool.NONE)
                                        }
                                    },
                                )
                                BottomToolAction(
                                    icon = Icons.Default.Tune,
                                    label = "Display",
                                    active = false,
                                    testTag = "reader_display_button",
                                    onClick = { showDisplaySheet = true },
                                )
                                BottomToolAction(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    active = false,
                                    testTag = "reader_share_button",
                                    onClick = { viewModel.shareOriginalPdf() },
                                )
                            }
                        }
                    }
                }

                // Bottom Sheets
                if (showNavSheet) {
                    NavigationBottomSheet(
                        session = session,
                        currentPageIndex = uiState.currentPageIndex,
                        outlines = load.outlines,
                        bookmarks = uiState.bookmarks,
                        annotations = uiState.annotations,
                        isExportingPdf = uiState.isExportingPdf,
                        onDismiss = { showNavSheet = false },
                        onJumpToPage = { viewModel.jumpToPage(it, recordHistory = true) },
                        onAddCurrentBookmark = { label ->
                            viewModel.addNamedBookmark(uiState.currentPageIndex, label, "")
                        },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
                        onExportMarkdown = { viewModel.exportAnnotationsAsMarkdown() },
                        onExportAnnotatedPdf = { viewModel.exportFlattenedPdf() },
                    )
                }

                if (showSearchSheet) {
                    SearchBottomSheet(
                        query = uiState.searchQuery,
                        options = uiState.searchOptions,
                        progress = uiState.searchProgress,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onOptionsChange = { viewModel.updateSearchOptions(it) },
                        onSelectHit = { viewModel.selectSearchHit(it) },
                        onDismiss = { showSearchSheet = false },
                    )
                }

                if (showDisplaySheet) {
                    DisplayOptionsBottomSheet(
                        preferences = prefs,
                        onSetReadingColorMode = { viewModel.setReadingColorMode(it) },
                        onSetScrollMode = { viewModel.setScrollMode(it) },
                        onSetAutoCrop = { viewModel.setAutoCropMargins(it) },
                        onSetKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                        onSetShowTelemetryHud = { viewModel.setShowTelemetryHud(it) },
                        onSetReflowFontScale = { viewModel.setReflowFontScale(it) },
                        onDismiss = { showDisplaySheet = false },
                    )
                }

                if (showInspectorSheet) {
                    DocumentInspectorBottomSheet(
                        session = session,
                        metadata = load.metadata,
                        currentPageIndex = uiState.currentPageIndex,
                        telemetry = uiState.telemetry,
                        onDismiss = { showInspectorSheet = false },
                    )
                }

                if (showJumpDialog) {
                    JumpToPageDialog(
                        currentPage = uiState.currentPageIndex + 1,
                        totalPages = session.pageCount,
                        onDismiss = { showJumpDialog = false },
                        onConfirm = { pageOneBased ->
                            viewModel.jumpToPage(pageOneBased - 1, recordHistory = true)
                            showJumpDialog = false
                        },
                    )
                }

                pendingStickyNote?.let { (pageIdx, point) ->
                    StickyNoteDialog(
                        title = "Add Margin Note (Page ${pageIdx + 1})",
                        initialText = "",
                        showDelete = false,
                        onDismiss = { pendingStickyNote = null },
                        onSave = { text ->
                            viewModel.addStickyNoteAnnotation(pageIdx, point, text)
                            pendingStickyNote = null
                        },
                        onDelete = {},
                    )
                }

                editingStickyNote?.let { note ->
                    StickyNoteDialog(
                        title = "Margin Note (Page ${note.pageIndex + 1})",
                        initialText = note.noteText,
                        showDelete = true,
                        onDismiss = { editingStickyNote = null },
                        onSave = { text ->
                            viewModel.updateStickyNote(note.id, text)
                            editingStickyNote = null
                        },
                        onDelete = {
                            viewModel.deleteAnnotation(note.id)
                            editingStickyNote = null
                        },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp),
        )
    }
}

@Composable
private fun BottomToolAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnnotationToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by remember { mutableStateOf(currentPage.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in 1..totalPages

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Page", style = MaterialTheme.typography.headlineMedium) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.all { c -> c.isDigit() }) input = it },
                label = { Text("Page number (1–$totalPages)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { parsed?.let(onConfirm) },
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StickyNoteDialog(
    title: String,
    initialText: String,
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write your margin annotation…") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onSave(text) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDelete) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
