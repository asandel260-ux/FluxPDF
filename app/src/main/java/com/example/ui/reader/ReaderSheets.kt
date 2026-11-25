package com.example.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.AnnotationEntity
import com.example.data.BookmarkEntity
import com.example.data.ReaderPreferences
import com.example.data.ReadingColorMode
import com.example.data.ScrollMode
import com.example.engine.FluxNativeBridge
import com.example.engine.FluxPdfDocumentSession
import com.example.engine.PdfOutlineNode
import com.example.engine.PdfStructureMetadata
import com.example.engine.RenderTelemetrySnapshot
import com.example.engine.SearchProgress
import com.example.engine.SearchQueryOptions
import com.example.ui.home.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBottomSheet(
    session: FluxPdfDocumentSession,
    currentPageIndex: Int,
    outlines: List<PdfOutlineNode>,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    isExportingPdf: Boolean,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onAddCurrentBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportAnnotatedPdf: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(if (outlines.isNotEmpty()) 1 else 0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pages") },
                    icon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Outline") },
                    icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Saved (${bookmarks.size})") },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Notes (${annotations.size})") },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }

            when (selectedTab) {
                0 -> ThumbnailsGridTab(
                    session = session,
                    currentPageIndex = currentPageIndex,
                    onSelectPage = { p ->
                        onJumpToPage(p)
                        onDismiss()
                    },
                )
                1 -> OutlinesTab(
                    outlines = outlines,
                    currentPageIndex = currentPageIndex,
                    onSelectPage = { p ->
                        onJumpToPage(p)
                        onDismiss()
                    },
                )
                2 -> BookmarksTab(
                    bookmarks = bookmarks,
                    currentPageIndex = currentPageIndex,
                    onAddCurrentBookmark = onAddCurrentBookmark,
                    onSelectPage = { p ->
                        onJumpToPage(p)
                        onDismiss()
                    },
                    onDeleteBookmark = onDeleteBookmark,
                )
                3 -> AnnotationsLedgerTab(
                    annotations = annotations,
                    isExportingPdf = isExportingPdf,
                    onSelectPage = { p ->
                        onJumpToPage(p)
                        onDismiss()
                    },
                    onDeleteAnnotation = onDeleteAnnotation,
                    onExportMarkdown = onExportMarkdown,
                    onExportAnnotatedPdf = onExportAnnotatedPdf,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailsGridTab(
    session: FluxPdfDocumentSession,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(session.pageCount, key = { it }) { pageIdx ->
            var thumb by remember(session.documentId, pageIdx) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(session.documentId, pageIdx) {
                thumb = session.renderPage(pageIdx, requestedWidthPx = 220, isPreview = true)
            }
            val isCurrent = pageIdx == currentPageIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectPage(pageIdx) }
                    .padding(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = if (isCurrent) 2.5.dp else 1.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f),
                ) {
                    val bmp = thumb
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Thumbnail page ${pageIdx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${pageIdx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${pageIdx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun OutlinesTab(
    outlines: List<PdfOutlineNode>,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit,
) {
    if (outlines.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This PDF does not embed a Table of Contents (/Outlines). Use Pages or Bookmarks to navigate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(outlines) { idx, node ->
            val isActive = node.pageIndex == currentPageIndex
            Surface(
                onClick = { onSelectPage(node.pageIndex) },
                shape = RoundedCornerShape(10.dp),
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outline_item_$idx"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (12 + node.depth * 18).dp,
                            end = 12.dp,
                            top = 11.dp,
                            bottom = 11.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = node.title,
                        style = if (node.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "p.${node.pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<BookmarkEntity>,
    currentPageIndex: Int,
    onAddCurrentBookmark: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    var quickLabel by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = quickLabel,
                onValueChange = { quickLabel = it },
                placeholder = { Text("Bookmark page ${currentPageIndex + 1} as…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAddCurrentBookmark(quickLabel.ifBlank { "Page ${currentPageIndex + 1}" })
                    quickLabel = ""
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No bookmarks yet. Tap the bookmark ribbon in the top bar or add a named bookmark above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bookmarks, key = { it.id }) { bm ->
                    Surface(
                        onClick = { onSelectPage(bm.pageIndex) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bm.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Page ${bm.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete bookmark")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationsLedgerTab(
    annotations: List<AnnotationEntity>,
    isExportingPdf: Boolean,
    onSelectPage: (Int) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportAnnotatedPdf: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onExportMarkdown,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Markdown")
            }
            Button(
                onClick = onExportAnnotatedPdf,
                enabled = !isExportingPdf,
                modifier = Modifier.weight(1f),
            ) {
                if (isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Export PDF")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (annotations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No annotations recorded yet. Use the Annotate toolbar at the bottom to add highlights, underlines, freehand ink, or margin notes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(annotations, key = { it.id }) { ann ->
                    Surface(
                        onClick = { onSelectPage(ann.pageIndex) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(ann.colorArgb.toInt())),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ann.annotationType().label} • Page ${ann.pageIndex + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (ann.noteText.isNotBlank()) {
                                    Text(
                                        text = ann.noteText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteAnnotation(ann.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete annotation")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    query: String,
    options: SearchQueryOptions,
    progress: SearchProgress?,
    onQueryChange: (String) -> Unit,
    onOptionsChange: (SearchQueryOptions) -> Unit,
    onSelectHit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Full-Text Search",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search across all pages…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reader_search_input"),
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = options.caseSensitive,
                    onClick = { onOptionsChange(options.copy(caseSensitive = !options.caseSensitive)) },
                    label = { Text("Aa Case") },
                )
                FilterChip(
                    selected = options.wholeWord,
                    onClick = { onOptionsChange(options.copy(wholeWord = !options.wholeWord)) },
                    label = { Text("Whole Word") },
                )
                FilterChip(
                    selected = options.useRegex,
                    onClick = { onOptionsChange(options.copy(useRegex = !options.useRegex)) },
                    label = { Text("Regex .*") },
                )
            }

            progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                if (!p.isComplete) {
                    LinearProgressIndicator(
                        progress = {
                            if (p.totalPages > 0) p.scannedPages.toFloat() / p.totalPages else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (p.regexError != null) {
                    Text(
                        text = p.regexError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "${p.hits.size} matches across ${p.scannedPages}/${p.totalPages} pages  •  ${"%.1f".format(p.elapsedMs)} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val hits = progress?.hits.orEmpty()
            if (query.trim().length >= 2 && progress?.isComplete == true && hits.isEmpty() && progress.regexError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matches found for \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(hits) { idx, hit ->
                        val annotated = buildAnnotatedString {
                            val s = hit.snippet
                            val hlStart = hit.highlightStart.coerceIn(0, s.length)
                            val hlEnd = hit.highlightEnd.coerceIn(hlStart, s.length)
                            append(s.substring(0, hlStart))
                            withStyle(
                                SpanStyle(
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                ),
                            ) {
                                append(s.substring(hlStart, hlEnd))
                            }
                            append(s.substring(hlEnd))
                        }

                        Surface(
                            onClick = {
                                onSelectHit(idx)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_hit_$idx"),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PAGE ${hit.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptionsBottomSheet(
    preferences: ReaderPreferences,
    onSetReadingColorMode: (ReadingColorMode) -> Unit,
    onSetScrollMode: (ScrollMode) -> Unit,
    onSetAutoCrop: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetShowTelemetryHud: (Boolean) -> Unit,
    onSetReflowFontScale: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Display & Reading Optics", style = MaterialTheme.typography.headlineMedium)

            Column {
                Text("OPTICAL COLOR PIPELINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReadingColorMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.readingColorMode == mode,
                            onClick = { onSetReadingColorMode(mode) },
                            label = { Text(mode.shortLabel) },
                            modifier = Modifier.testTag("optical_${mode.name.lowercase()}"),
                        )
                    }
                }
            }

            Column {
                Text("PAGE LAYOUT MODE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScrollMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.scrollMode == mode,
                            onClick = { onSetScrollMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("layout_${mode.name.lowercase()}"),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SheetSwitchRow(
                label = "Auto Margin Crop",
                caption = "Trims excess whitespace around text columns",
                checked = preferences.autoCropMargins,
                onCheckedChange = onSetAutoCrop,
            )
            SheetSwitchRow(
                label = "Keep Screen Awake",
                caption = "Prevent auto-dim while reading",
                checked = preferences.keepScreenOn,
                onCheckedChange = onSetKeepScreenOn,
            )
            SheetSwitchRow(
                label = "Live Telemetry HUD",
                caption = "Show render latency (ms) and LRU cache MB",
                checked = preferences.showTelemetryHud,
                onCheckedChange = onSetShowTelemetryHud,
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Reflow Text Scale", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(preferences.reflowFontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = preferences.reflowFontScale,
                    onValueChange = onSetReflowFontScale,
                    valueRange = 0.85f..1.80f,
                )
            }
        }
    }
}

@Composable
private fun SheetSwitchRow(
    label: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInspectorBottomSheet(
    session: FluxPdfDocumentSession,
    metadata: PdfStructureMetadata,
    currentPageIndex: Int,
    telemetry: RenderTelemetrySnapshot,
    onDismiss: () -> Unit,
) {
    val dim = session.getPageDimension(currentPageIndex)
    val widthMm = (dim.widthPt * 0.352778f).toInt()
    val heightMm = (dim.heightPt * 0.352778f).toInt()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Document Inspector", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = FluxNativeBridge.engineBackendLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            InspectorRow("Title", metadata.title.ifBlank { session.file.nameWithoutExtension })
            if (metadata.author.isNotBlank()) InspectorRow("Author", metadata.author)
            if (metadata.creator.isNotBlank()) InspectorRow("Creator", metadata.creator)
            if (metadata.producer.isNotBlank()) InspectorRow("Producer", metadata.producer)
            InspectorRow("PDF Standard", "PDF ${metadata.pdfVersion} (${metadata.xrefType})")
            InspectorRow("Fast Web View", if (metadata.isLinearized) "Linearized" else "Standard")
            InspectorRow("Objects Indexed", "${metadata.objectCount} PDF objects")
            InspectorRow("File Size", formatFileSize(metadata.fileSizeBytes))
            InspectorRow("Page ${currentPageIndex + 1} Size", "${dim.widthPt} × ${dim.heightPt} pt ($widthMm × $heightMm mm)")
            InspectorRow("Cold Open / Parse", "${"%.1f".format(session.coldOpenMs)} ms / ${"%.1f".format(metadata.parseTimeMs)} ms")
            InspectorRow("Cache Residency", "${"%.1f".format(telemetry.cacheUsedMb)} / ${"%.1f".format(telemetry.cacheBudgetMb)} MB (${telemetry.hitRatePercent}% hit)")
            InspectorRow("P95 Frame Render", "${"%.1f".format(telemetry.p95RenderMs)} ms (${session.hardwareProfile.activeTier.label})")
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
        )
    }
}
