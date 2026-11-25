package com.example.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentEntity
import com.example.data.FluxDocumentRepository
import com.example.ui.theme.TechnicalMonoFamily
import com.example.viewmodel.LibraryFilter
import com.example.viewmodel.LibrarySort
import com.example.viewmodel.LibraryUiState
import java.text.DecimalFormat

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    isGridView: Boolean,
    repository: FluxDocumentRepository,
    onOpenDocument: (Long) -> Unit,
    onImportPdfClick: () -> Unit,
    onGenerateStressPdf: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onTagSelect: (String?) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleGridView: (Boolean) -> Unit,
    onTogglePin: (DocumentEntity) -> Unit,
    onSetCollectionTag: (Long, String) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onBenchmarkDocument: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var tagDialogDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gridColumns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Swiss Editorial Masthead
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "FOLIO ARCHIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // Engine tier pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = "${uiState.documents.size} PDF",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.testTag("library_sort_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort documents",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sort.label,
                                        fontWeight = if (sort == uiState.sortOrder) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sort == uiState.sortOrder) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    onSortChange(sort)
                                    sortMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onToggleGridView(!isGridView) },
                    modifier = Modifier.testTag("library_view_toggle"),
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.library_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("library_search_input"),
            )

            Spacer(Modifier.height(10.dp))

            // Filter + Collection tag chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.activeFilter == filter && uiState.activeTag == null,
                        onClick = {
                            onFilterChange(filter)
                            if (uiState.activeTag != null) onTagSelect(null)
                        },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
                    )
                }
                uiState.availableTags.forEach { tag ->
                    FilterChip(
                        selected = uiState.activeTag == tag,
                        onClick = { onTagSelect(tag) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Label,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            AnimatedVisibility(visible = uiState.isImporting || uiState.isGeneratingStressPdf) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            if (uiState.filteredDocuments.isEmpty()) {
                EmptyLibraryState(
                    hasQuery = uiState.searchQuery.isNotBlank() || uiState.activeFilter != LibraryFilter.ALL,
                    onImportPdfClick = onImportPdfClick,
                    onGenerateStressPdf = onGenerateStressPdf,
                    onResetFilters = {
                        onSearchQueryChange("")
                        onFilterChange(LibraryFilter.ALL)
                        onTagSelect(null)
                    },
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val featured = uiState.continueReadingDoc
                    if (featured != null && uiState.searchQuery.isBlank() && uiState.activeFilter == LibraryFilter.ALL && uiState.activeTag == null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "continue_card") {
                            ContinueReadingCard(
                                doc = featured,
                                repository = repository,
                                onOpen = { onOpenDocument(featured.id) },
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.all_documents),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${uiState.filteredDocuments.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(uiState.filteredDocuments, key = { it.id }) { doc ->
                        DocumentGridCard(
                            doc = doc,
                            repository = repository,
                            onClick = { onOpenDocument(doc.id) },
                            onTogglePin = { onTogglePin(doc) },
                            onEditTag = { tagDialogDoc = doc },
                            onBenchmark = { onBenchmarkDocument(doc) },
                            onDelete = { onDeleteDocument(doc) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val featured = uiState.continueReadingDoc
                    if (featured != null && uiState.searchQuery.isBlank() && uiState.activeFilter == LibraryFilter.ALL && uiState.activeTag == null) {
                        item(key = "continue_card") {
                            ContinueReadingCard(
                                doc = featured,
                                repository = repository,
                                onOpen = { onOpenDocument(featured.id) },
                            )
                        }
                    }
                    items(uiState.filteredDocuments, key = { it.id }) { doc ->
                        DocumentListRow(
                            doc = doc,
                            repository = repository,
                            onClick = { onOpenDocument(doc.id) },
                            onTogglePin = { onTogglePin(doc) },
                            onEditTag = { tagDialogDoc = doc },
                            onBenchmark = { onBenchmarkDocument(doc) },
                            onDelete = { onDeleteDocument(doc) },
                        )
                    }
                }
            }
        }
    }

    tagDialogDoc?.let { target ->
        CollectionTagDialog(
            currentTag = target.collectionTag,
            existingTags = uiState.availableTags,
            onDismiss = { tagDialogDoc = null },
            onSave = { newTag ->
                onSetCollectionTag(target.id, newTag)
                tagDialogDoc = null
            },
        )
    }
}

@Composable
private fun ContinueReadingCard(
    doc: DocumentEntity,
    repository: FluxDocumentRepository,
    onOpen: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(initialValue = null, doc.id) {
        value = repository.renderCoverThumbnail(doc, widthPx = 220)
    }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_reading_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Miniature crisp page preview
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .aspectRatio(0.73f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = "Cover preview of ${doc.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONTINUE READING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (doc.author.isNotBlank()) {
                    Text(
                        text = doc.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { doc.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Page ${doc.lastPageIndex + 1} / ${doc.pageCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(doc.progressFraction * 100).toInt()}% • ${formatFileSize(doc.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentGridCard(
    doc: DocumentEntity,
    repository: FluxDocumentRepository,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onEditTag: () -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(initialValue = null, doc.id) {
        value = repository.renderCoverThumbnail(doc, widthPx = 280)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}"),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = "First page of ${doc.title}",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Bottom gradient for crisp badge legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )

                // Page badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "${doc.pageCount}p • PDF ${doc.pdfVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }

                // Pin indicator
                if (doc.isPinned) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                // Context menu button
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Document options",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    DocumentContextMenu(
                        expanded = menuExpanded,
                        isPinned = doc.isPinned,
                        onDismiss = { menuExpanded = false },
                        onTogglePin = { menuExpanded = false; onTogglePin() },
                        onEditTag = { menuExpanded = false; onEditTag() },
                        onBenchmark = { menuExpanded = false; onBenchmark() },
                        onDelete = { menuExpanded = false; onDelete() },
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = doc.collectionTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = formatFileSize(doc.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (doc.lastPageIndex > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { doc.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentListRow(
    doc: DocumentEntity,
    repository: FluxDocumentRepository,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onEditTag: () -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(initialValue = null, doc.id) {
        value = repository.renderCoverThumbnail(doc, widthPx = 160)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_row_${doc.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (doc.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("${doc.pageCount} pages")
                        append("  •  ${formatFileSize(doc.fileSizeBytes)}")
                        append("  •  ${doc.collectionTag}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Document options")
                }
                DocumentContextMenu(
                    expanded = menuExpanded,
                    isPinned = doc.isPinned,
                    onDismiss = { menuExpanded = false },
                    onTogglePin = { menuExpanded = false; onTogglePin() },
                    onEditTag = { menuExpanded = false; onEditTag() },
                    onBenchmark = { menuExpanded = false; onBenchmark() },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun DocumentContextMenu(
    expanded: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onEditTag: () -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isPinned) "Unpin" else "Pin to top") },
            leadingIcon = {
                Icon(
                    imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                    contentDescription = null,
                )
            },
            onClick = onTogglePin,
        )
        DropdownMenuItem(
            text = { Text("Assign Collection") },
            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
            onClick = onEditTag,
        )
        DropdownMenuItem(
            text = { Text("Run Engine Benchmark") },
            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
            onClick = onBenchmark,
        )
        DropdownMenuItem(
            text = { Text("Remove from Library", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = onDelete,
        )
    }
}

@Composable
private fun EmptyLibraryState(
    hasQuery: Boolean,
    onImportPdfClick: () -> Unit,
    onGenerateStressPdf: () -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_library),
            contentDescription = "Archival reading desk illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (hasQuery) "No matching documents" else stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasQuery) {
                "Try clearing your search query or switching collection filters."
            } else {
                stringResource(R.string.library_empty_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (hasQuery) {
            Button(onClick = onResetFilters) {
                Text("Reset Filters")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onImportPdfClick) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open PDF")
                }
                OutlinedButton(onClick = onGenerateStressPdf) {
                    Text("120p Stress PDF")
                }
            }
        }
    }
}

@Composable
private fun CollectionTagDialog(
    currentTag: String,
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var tagInput by remember { mutableStateOf(currentTag) }
    val presets = (listOf("Research", "Manuals", "Books", "Invoices", "Benchmarks") + existingTags).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collection Tag", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Collection name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = tagInput.equals(preset, ignoreCase = true),
                            onClick = { tagInput = preset },
                            label = { Text(preset) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(tagInput) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${DecimalFormat("0.#").format(kb)} KB"
    val mb = kb / 1024.0
    return "${DecimalFormat("0.00").format(mb)} MB"
}
