package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.AnnotationEntity
import com.example.data.AnnotationType
import com.example.data.BookmarkEntity
import com.example.data.DocumentEntity
import com.example.data.NormalizedPoint
import com.example.data.ReaderPreferences
import com.example.data.ReadingColorMode
import com.example.data.ScrollMode
import com.example.engine.FluxPdfDocumentSession
import com.example.engine.FluxSearchEngine
import com.example.engine.HardwareProfile
import com.example.engine.PdfOutlineNode
import com.example.engine.PdfStructureMetadata
import com.example.engine.RenderTelemetrySnapshot
import com.example.engine.SearchProgress
import com.example.engine.SearchQueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ActiveAnnotationTool(val label: String) {
    NONE("Read"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    INK("Ink"),
    STICKY_NOTE("Note"),
}

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data class Error(val message: String) : ReaderLoadState
    data class Ready(
        val document: DocumentEntity,
        val session: FluxPdfDocumentSession,
        val metadata: PdfStructureMetadata,
        val outlines: List<PdfOutlineNode>,
    ) : ReaderLoadState
}

data class ReaderUiState(
    val loadState: ReaderLoadState = ReaderLoadState.Loading,
    val currentPageIndex: Int = 0,
    val jumpHistoryBackStack: List<Int> = emptyList(),
    val isChromeVisible: Boolean = true,
    val isReflowMode: Boolean = false,
    val reflowPageText: String = "",
    val activeTool: ActiveAnnotationTool = ActiveAnnotationTool.NONE,
    val selectedInkColorArgb: Long = 0x88F5C542L,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    val searchQuery: String = "",
    val searchOptions: SearchQueryOptions = SearchQueryOptions(),
    val searchProgress: SearchProgress? = null,
    val activeSearchHitIndex: Int = -1,
    val telemetry: RenderTelemetrySnapshot,
    val isExportingPdf: Boolean = false,
) {
    val pageCount: Int
        get() = (loadState as? ReaderLoadState.Ready)?.session?.pageCount ?: 1

    val isCurrentPageBookmarked: Boolean
        get() = bookmarks.any { it.pageIndex == currentPageIndex }
}

sealed interface ReaderEvent {
    data class JumpToPage(val pageIndex: Int) : ReaderEvent
    data class ShowMessage(val message: String) : ReaderEvent
    data class ShareMarkdownText(val title: String, val markdown: String) : ReaderEvent
    data class SharePdfFile(val file: File, val title: String) : ReaderEvent
}

class ReaderViewModel(
    application: Application,
    private val container: AppContainer,
    private val documentId: Long,
) : AndroidViewModel(application) {

    private val repo = container.documentRepository
    private val prefsRepo = container.preferencesRepository

    val preferences: StateFlow<ReaderPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences(),
    )

    private val _uiState = MutableStateFlow(
        ReaderUiState(telemetry = container.bitmapCache.snapshotTelemetry()),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 12)
    val events = _events.asSharedFlow()

    private var activeSession: FluxPdfDocumentSession? = null
    private var searchJob: Job? = null
    private var savePositionJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastScrollDirection: Int = 1

    init {
        openDocument()
        observeSidecarData()
        observePreferencesForHardware()
        startTelemetryPolling()
    }

    private fun openDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadState = ReaderLoadState.Loading) }
            val doc = repo.getDocumentById(documentId)
            if (doc == null) {
                _uiState.update { it.copy(loadState = ReaderLoadState.Error("Document record not found.")) }
                return@launch
            }
            val file = File(doc.localFilePath)
            if (!file.exists()) {
                _uiState.update {
                    it.copy(loadState = ReaderLoadState.Error("PDF file is no longer available on disk."))
                }
                return@launch
            }

            runCatching {
                val hw = HardwareProfile.detect(getApplication(), preferences.value.hardwareTierOverride)
                FluxPdfDocumentSession.open(
                    documentId = doc.id,
                    file = file,
                    bitmapCache = container.bitmapCache,
                    hardwareProfile = hw,
                )
            }.onSuccess { session ->
                activeSession?.close()
                activeSession = session
                val resumePage = doc.lastPageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        loadState = ReaderLoadState.Ready(
                            document = doc,
                            session = session,
                            metadata = session.structureMetadata,
                            outlines = session.structureMetadata.outlines,
                        ),
                        currentPageIndex = resumePage,
                    )
                }
                loadReflowTextForPage(resumePage)
                repo.updateReadingPosition(doc.id, resumePage)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        loadState = ReaderLoadState.Error(
                            "Failed to render PDF: ${err.message ?: "File may be password-protected or corrupted."}",
                        ),
                    )
                }
            }
        }
    }

    private fun observeSidecarData() {
        viewModelScope.launch {
            repo.observeBookmarks(documentId).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
        viewModelScope.launch {
            repo.observeAnnotations(documentId).collect { list ->
                _uiState.update { it.copy(annotations = list) }
            }
        }
    }

    private fun observePreferencesForHardware() {
        viewModelScope.launch {
            preferences.collect { prefs ->
                val hw = HardwareProfile.detect(getApplication(), prefs.hardwareTierOverride)
                activeSession?.hardwareProfile = hw
                container.bitmapCache.updateBudget(hw.cacheBudgetBytes)
            }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(telemetry = container.bitmapCache.snapshotTelemetry()) }
                delay(900)
            }
        }
    }

    fun onPageVisible(pageIndex: Int, viewportWidthPx: Int = 1080) {
        val session = activeSession ?: return
        val clamped = pageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
        val prev = _uiState.value.currentPageIndex
        if (clamped != prev) {
            lastScrollDirection = if (clamped >= prev) 1 else -1
            _uiState.update { it.copy(currentPageIndex = clamped) }
            if (_uiState.value.isReflowMode) {
                loadReflowTextForPage(clamped)
            }
            scheduleSaveReadingPosition(clamped)
            schedulePrefetch(clamped, viewportWidthPx)
        }
    }

    private fun scheduleSaveReadingPosition(pageIndex: Int) {
        savePositionJob?.cancel()
        savePositionJob = viewModelScope.launch {
            delay(350)
            repo.updateReadingPosition(documentId, pageIndex)
        }
    }

    private fun schedulePrefetch(centerPage: Int, viewportWidthPx: Int) {
        val session = activeSession ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(80)
            session.prefetchPages(centerPage, lastScrollDirection, viewportWidthPx)
        }
    }

    fun jumpToPage(targetPage: Int, recordHistory: Boolean = true) {
        val session = activeSession ?: return
        val clamped = targetPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
        val current = _uiState.value.currentPageIndex
        if (recordHistory && clamped != current) {
            _uiState.update { state ->
                val updated = (state.jumpHistoryBackStack + current).takeLast(12)
                state.copy(jumpHistoryBackStack = updated, currentPageIndex = clamped)
            }
        } else {
            _uiState.update { it.copy(currentPageIndex = clamped) }
        }
        if (_uiState.value.isReflowMode) {
            loadReflowTextForPage(clamped)
        }
        scheduleSaveReadingPosition(clamped)
        viewModelScope.launch { _events.emit(ReaderEvent.JumpToPage(clamped)) }
    }

    fun navigateJumpHistoryBack() {
        val stack = _uiState.value.jumpHistoryBackStack
        if (stack.isEmpty()) return
        val previous = stack.last()
        _uiState.update { it.copy(jumpHistoryBackStack = stack.dropLast(1)) }
        jumpToPage(previous, recordHistory = false)
    }

    fun stepPage(delta: Int) {
        jumpToPage(_uiState.value.currentPageIndex + delta, recordHistory = false)
    }

    fun toggleChrome() {
        _uiState.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    fun setChromeVisible(visible: Boolean) {
        _uiState.update { it.copy(isChromeVisible = visible) }
    }

    fun toggleReflowMode() {
        val next = !_uiState.value.isReflowMode
        _uiState.update {
            it.copy(
                isReflowMode = next,
                activeTool = if (next) ActiveAnnotationTool.NONE else it.activeTool,
            )
        }
        if (next) {
            loadReflowTextForPage(_uiState.value.currentPageIndex)
        }
    }

    private fun loadReflowTextForPage(pageIndex: Int) {
        val session = activeSession ?: return
        viewModelScope.launch {
            val extracted = withContext(Dispatchers.Default) {
                session.extractPageText(pageIndex)
            }
            _uiState.update { it.copy(reflowPageText = extracted) }
        }
    }

    // Bookmarks
    fun toggleCurrentPageBookmark() {
        val page = _uiState.value.currentPageIndex
        viewModelScope.launch {
            if (_uiState.value.isCurrentPageBookmarked) {
                repo.removeBookmarksOnPage(documentId, page)
                _events.emit(ReaderEvent.ShowMessage("Removed bookmark on page ${page + 1}"))
            } else {
                val chapterHint = (uiState.value.loadState as? ReaderLoadState.Ready)
                    ?.outlines
                    ?.lastOrNull { it.pageIndex <= page }
                    ?.title
                val label = chapterHint ?: "Page ${page + 1}"
                repo.addBookmark(documentId, page, label)
                _events.emit(ReaderEvent.ShowMessage("Bookmarked page ${page + 1}"))
            }
        }
    }

    fun addNamedBookmark(pageIndex: Int, label: String, note: String) {
        viewModelScope.launch {
            repo.addBookmark(documentId, pageIndex, label, note)
            _events.emit(ReaderEvent.ShowMessage("Saved bookmark on page ${pageIndex + 1}"))
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repo.removeBookmark(id) }
    }

    // Annotations
    fun selectAnnotationTool(tool: ActiveAnnotationTool) {
        val defaultColor = when (tool) {
            ActiveAnnotationTool.HIGHLIGHT -> 0x88F5C542L
            ActiveAnnotationTool.UNDERLINE -> 0xFFC8412AL
            ActiveAnnotationTool.INK -> 0xFFC8412AL
            ActiveAnnotationTool.STICKY_NOTE -> 0xFFC8412AL
            ActiveAnnotationTool.NONE -> _uiState.value.selectedInkColorArgb
        }
        _uiState.update {
            it.copy(
                activeTool = if (it.activeTool == tool) ActiveAnnotationTool.NONE else tool,
                selectedInkColorArgb = defaultColor,
                isReflowMode = false,
            )
        }
    }

    fun setSelectedInkColor(colorArgb: Long) {
        _uiState.update { it.copy(selectedInkColorArgb = colorArgb) }
    }

    fun commitStrokeAnnotation(pageIndex: Int, type: AnnotationType, points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        val color = _uiState.value.selectedInkColorArgb
        val strokeNorm = when (type) {
            AnnotationType.HIGHLIGHT -> 0.026f
            AnnotationType.UNDERLINE -> 0.0045f
            AnnotationType.INK -> 0.0055f
            AnnotationType.STICKY_NOTE -> 0.01f
        }
        viewModelScope.launch {
            repo.addAnnotation(
                AnnotationEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    type = type.name,
                    colorArgb = color,
                    strokeWidthNorm = strokeNorm,
                    pointsSerialized = AnnotationEntity.encodePoints(points),
                ),
            )
        }
    }

    fun addStickyNoteAnnotation(pageIndex: Int, point: NormalizedPoint, noteText: String) {
        val trimmed = noteText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addAnnotation(
                AnnotationEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    type = AnnotationType.STICKY_NOTE.name,
                    colorArgb = _uiState.value.selectedInkColorArgb,
                    pointsSerialized = AnnotationEntity.encodePoints(listOf(point)),
                    noteText = trimmed,
                ),
            )
            _events.emit(ReaderEvent.ShowMessage("Added margin note on page ${pageIndex + 1}"))
        }
    }

    fun updateStickyNote(annotationId: Long, text: String) {
        viewModelScope.launch { repo.updateAnnotationNote(annotationId, text.trim()) }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch { repo.deleteAnnotation(annotationId) }
    }

    fun undoLastAnnotationOnCurrentPage() {
        val page = _uiState.value.currentPageIndex
        val last = _uiState.value.annotations.lastOrNull { it.pageIndex == page } ?: return
        viewModelScope.launch {
            repo.deleteAnnotation(last.id)
            _events.emit(ReaderEvent.ShowMessage("Undid last annotation on page ${page + 1}"))
        }
    }

    // Full-Text Search
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        runSearch(query, _uiState.value.searchOptions)
    }

    fun updateSearchOptions(options: SearchQueryOptions) {
        _uiState.update { it.copy(searchOptions = options) }
        val q = _uiState.value.searchQuery
        if (q.isNotBlank()) runSearch(q, options)
    }

    private fun runSearch(query: String, options: SearchQueryOptions) {
        searchJob?.cancel()
        val session = activeSession ?: return
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchProgress = null, activeSearchHitIndex = -1) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(180)
            FluxSearchEngine.search(session, query, options).collect { progress ->
                _uiState.update { state ->
                    state.copy(
                        searchProgress = progress,
                        activeSearchHitIndex = if (progress.hits.isEmpty()) {
                            -1
                        } else {
                            state.activeSearchHitIndex.coerceIn(0, progress.hits.lastIndex)
                        },
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchProgress = null, activeSearchHitIndex = -1) }
    }

    fun selectSearchHit(index: Int) {
        val hits = _uiState.value.searchProgress?.hits ?: return
        if (index !in hits.indices) return
        _uiState.update { it.copy(activeSearchHitIndex = index) }
        jumpToPage(hits[index].pageIndex, recordHistory = true)
    }

    fun stepSearchHit(delta: Int) {
        val hits = _uiState.value.searchProgress?.hits ?: return
        if (hits.isEmpty()) return
        val next = (_uiState.value.activeSearchHitIndex + delta).mod(hits.size)
        selectSearchHit(next)
    }

    // Exports & Sharing
    fun exportAnnotationsAsMarkdown() {
        val doc = (_uiState.value.loadState as? ReaderLoadState.Ready)?.document ?: return
        viewModelScope.launch {
            val md = repo.buildAnnotationsMarkdown(doc)
            _events.emit(ReaderEvent.ShareMarkdownText(doc.title, md))
        }
    }

    fun exportFlattenedPdf() {
        val doc = (_uiState.value.loadState as? ReaderLoadState.Ready)?.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingPdf = true) }
            val result = repo.exportFlattenedAnnotatedPdf(doc)
            _uiState.update { it.copy(isExportingPdf = false) }
            result.onSuccess { file ->
                _events.emit(ReaderEvent.SharePdfFile(file, "${doc.title} (Annotated)"))
            }.onFailure { err ->
                _events.emit(ReaderEvent.ShowMessage("Export failed: ${err.message}"))
            }
        }
    }

    fun shareOriginalPdf() {
        val doc = (_uiState.value.loadState as? ReaderLoadState.Ready)?.document ?: return
        val file = File(doc.localFilePath)
        if (file.exists()) {
            viewModelScope.launch { _events.emit(ReaderEvent.SharePdfFile(file, doc.title)) }
        }
    }

    // Reading preferences
    fun setReadingColorMode(mode: ReadingColorMode) = viewModelScope.launch {
        prefsRepo.setReadingColorMode(mode)
    }
    fun setScrollMode(mode: ScrollMode) = viewModelScope.launch {
        prefsRepo.setScrollMode(mode)
    }
    fun setAutoCropMargins(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAutoCropMargins(enabled)
    }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setKeepScreenOn(enabled)
    }
    fun setShowTelemetryHud(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setShowTelemetryHud(enabled)
    }
    fun setReflowFontScale(scale: Float) = viewModelScope.launch {
        prefsRepo.setReflowFontScale(scale)
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        savePositionJob?.cancel()
        prefetchJob?.cancel()
        activeSession?.close()
        activeSession = null
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
        private val documentId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(application, container, documentId) as T
        }
    }
}
