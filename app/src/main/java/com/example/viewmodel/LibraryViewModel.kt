package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.AppThemeMode
import com.example.data.DocumentEntity
import com.example.data.ReaderPreferences
import com.example.data.ReadingColorMode
import com.example.data.ScrollMode
import com.example.engine.BenchmarkReport
import com.example.engine.FluxPdfDocumentSession
import com.example.engine.HardwareProfile
import com.example.engine.HardwareTierPreference
import com.example.engine.RenderTelemetrySnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class LibraryFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    IN_PROGRESS("Reading"),
}

enum class LibrarySort(val label: String) {
    RECENT("Recent"),
    TITLE("Title A–Z"),
    PAGES("Pages"),
    SIZE("File Size"),
}

data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val filteredDocuments: List<DocumentEntity> = emptyList(),
    val continueReadingDoc: DocumentEntity? = null,
    val availableTags: List<String> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val activeTag: String? = null,
    val sortOrder: LibrarySort = LibrarySort.RECENT,
    val isImporting: Boolean = false,
    val isGeneratingStressPdf: Boolean = false,
    val isBenchmarkRunning: Boolean = false,
    val benchmarkReport: BenchmarkReport? = null,
    val diskCacheKb: Long = 0L,
    val hardwareProfile: HardwareProfile,
    val telemetry: RenderTelemetrySnapshot,
)

sealed interface LibraryEvent {
    data class OpenDocument(val documentId: Long) : LibraryEvent
    data class ShowMessage(val message: String) : LibraryEvent
}

class LibraryViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val repo = container.documentRepository
    private val prefsRepo = container.preferencesRepository

    val preferences: StateFlow<ReaderPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences(),
    )

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(LibraryFilter.ALL)
    private val activeTag = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(LibrarySort.RECENT)
    private val isImporting = MutableStateFlow(false)
    private val isGeneratingStressPdf = MutableStateFlow(false)
    private val isBenchmarkRunning = MutableStateFlow(false)
    private val benchmarkReport = MutableStateFlow<BenchmarkReport?>(null)
    private val diskCacheKb = MutableStateFlow(0L)
    private val hardwareProfileFlow = MutableStateFlow(container.hardwareProfile)
    private val telemetryFlow = MutableStateFlow(container.bitmapCache.snapshotTelemetry())

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        repo.allDocuments,
        searchQuery,
        activeFilter,
        activeTag,
        sortOrder,
    ) { docs, query, filter, tag, sort ->
        val trimmed = query.trim().lowercase()
        val filtered = docs.filter { doc ->
            val matchesQuery = trimmed.isEmpty() ||
                doc.title.lowercase().contains(trimmed) ||
                doc.author.lowercase().contains(trimmed) ||
                doc.fileName.lowercase().contains(trimmed) ||
                doc.collectionTag.lowercase().contains(trimmed)
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PINNED -> doc.isPinned
                LibraryFilter.IN_PROGRESS -> doc.lastPageIndex > 0 && doc.lastPageIndex < doc.pageCount - 1
            }
            val matchesTag = tag == null || doc.collectionTag.equals(tag, ignoreCase = true)
            matchesQuery && matchesFilter && matchesTag
        }.let { list ->
            when (sort) {
                LibrarySort.RECENT -> list.sortedWith(
                    compareByDescending<DocumentEntity> { it.isPinned }.thenByDescending { it.lastOpenedAt },
                )
                LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
                LibrarySort.PAGES -> list.sortedByDescending { it.pageCount }
                LibrarySort.SIZE -> list.sortedByDescending { it.fileSizeBytes }
            }
        }
        val tags = docs.map { it.collectionTag }.filter { it.isNotBlank() }.distinct().sorted()
        val continueDoc = docs.maxByOrNull { it.lastOpenedAt }
        Triple(docs, filtered, Triple(continueDoc, tags, query))
    }.combine(
        combine(
            isImporting,
            isGeneratingStressPdf,
            isBenchmarkRunning,
            benchmarkReport,
            combine(diskCacheKb, hardwareProfileFlow, telemetryFlow) { cache, hw, tel ->
                Triple(cache, hw, tel)
            },
        ) { importing, genStress, benchRun, report, sys ->
            arrayOf<Any?>(importing, genStress, benchRun, report, sys)
        },
    ) { libData, aux ->
        val (docs, filtered, meta) = libData
        val (continueDoc, tags, q) = meta
        @Suppress("UNCHECKED_CAST")
        val sys = aux[4] as Triple<Long, HardwareProfile, RenderTelemetrySnapshot>
        LibraryUiState(
            documents = docs,
            filteredDocuments = filtered,
            continueReadingDoc = continueDoc,
            availableTags = tags,
            searchQuery = q,
            activeFilter = activeFilter.value,
            activeTag = activeTag.value,
            sortOrder = sortOrder.value,
            isImporting = aux[0] as Boolean,
            isGeneratingStressPdf = aux[1] as Boolean,
            isBenchmarkRunning = aux[2] as Boolean,
            benchmarkReport = aux[3] as BenchmarkReport?,
            diskCacheKb = sys.first,
            hardwareProfile = sys.second,
            telemetry = sys.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(
            hardwareProfile = container.hardwareProfile,
            telemetry = container.bitmapCache.snapshotTelemetry(),
        ),
    )

    init {
        viewModelScope.launch {
            repo.ensureBuiltInFieldManual()
            refreshCacheMetrics()
        }
        viewModelScope.launch {
            preferences.collect { prefs ->
                val updated = HardwareProfile.detect(getApplication(), prefs.hardwareTierOverride)
                hardwareProfileFlow.value = updated
                container.bitmapCache.updateBudget(updated.cacheBudgetBytes)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                telemetryFlow.value = container.bitmapCache.snapshotTelemetry()
                delay(1500)
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        activeFilter.value = filter
    }

    fun setActiveTag(tag: String?) {
        activeTag.value = if (activeTag.value == tag) null else tag
    }

    fun setSortOrder(sort: LibrarySort) {
        sortOrder.value = sort
    }

    fun importPdfUri(uri: Uri, openImmediately: Boolean = true) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repo.importFromUri(uri)
            isImporting.value = false
            result.onSuccess { doc ->
                _events.emit(LibraryEvent.ShowMessage("Imported \"${doc.title}\" (${doc.pageCount} pages)"))
                if (openImmediately) {
                    _events.emit(LibraryEvent.OpenDocument(doc.id))
                }
            }.onFailure { err ->
                _events.emit(
                    LibraryEvent.ShowMessage("Could not open PDF: ${err.message ?: "Unsupported or damaged file"}"),
                )
            }
        }
    }

    fun togglePin(doc: DocumentEntity) {
        viewModelScope.launch { repo.togglePin(doc) }
    }

    fun setCollectionTag(documentId: Long, tag: String) {
        viewModelScope.launch {
            repo.setCollectionTag(documentId, tag.trim().ifBlank { "General" })
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repo.deleteDocument(doc)
            _events.emit(LibraryEvent.ShowMessage("Removed \"${doc.title}\""))
            refreshCacheMetrics()
        }
    }

    fun generateStressBenchmarkPdf(openAfter: Boolean = false) {
        viewModelScope.launch {
            if (isGeneratingStressPdf.value) return@launch
            isGeneratingStressPdf.value = true
            val doc = repo.generateAndRegisterStressPdf(pageCount = 120)
            isGeneratingStressPdf.value = false
            _events.emit(LibraryEvent.ShowMessage("Generated 120-page Stress Benchmark PDF"))
            if (openAfter) {
                _events.emit(LibraryEvent.OpenDocument(doc.id))
            }
        }
    }

    fun runLiveBenchmark(targetDocument: DocumentEntity? = null) {
        viewModelScope.launch {
            if (isBenchmarkRunning.value) return@launch
            isBenchmarkRunning.value = true
            try {
                val doc = targetDocument
                    ?: uiState.value.documents.maxByOrNull { it.pageCount }
                    ?: repo.ensureBuiltInFieldManual()
                val file = File(doc.localFilePath)
                if (!file.exists()) {
                    _events.emit(LibraryEvent.ShowMessage("Document file not found for benchmark"))
                    return@launch
                }
                val session = FluxPdfDocumentSession.open(
                    documentId = doc.id,
                    file = file,
                    bitmapCache = container.bitmapCache,
                    hardwareProfile = hardwareProfileFlow.value,
                )
                try {
                    val report = session.runLiveBenchmark(sampleQuery = "FluxPDF")
                    benchmarkReport.value = report
                    telemetryFlow.value = container.bitmapCache.snapshotTelemetry()
                    _events.emit(
                        LibraryEvent.ShowMessage(
                            "Benchmark complete: ${"%.1f".format(report.avgRenderMs)} ms avg render",
                        ),
                    )
                } finally {
                    session.close()
                }
            } catch (e: Exception) {
                _events.emit(LibraryEvent.ShowMessage("Benchmark failed: ${e.message}"))
            } finally {
                isBenchmarkRunning.value = false
            }
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            repo.clearCaches()
            refreshCacheMetrics()
            telemetryFlow.value = container.bitmapCache.snapshotTelemetry()
            _events.emit(LibraryEvent.ShowMessage("Cleared bitmap and thumbnail caches"))
        }
    }

    fun refreshCacheMetrics() {
        diskCacheKb.value = repo.computeThumbnailCacheBytes() / 1024L
    }

    // Preference updates
    fun setAppThemeMode(mode: AppThemeMode) = viewModelScope.launch { prefsRepo.setAppThemeMode(mode) }
    fun setReadingColorMode(mode: ReadingColorMode) = viewModelScope.launch { prefsRepo.setReadingColorMode(mode) }
    fun setScrollMode(mode: ScrollMode) = viewModelScope.launch { prefsRepo.setScrollMode(mode) }
    fun setAutoCropMargins(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoCropMargins(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { prefsRepo.setKeepScreenOn(enabled) }
    fun setShowTelemetryHud(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowTelemetryHud(enabled) }
    fun setVolumeKeyNavigation(enabled: Boolean) = viewModelScope.launch { prefsRepo.setVolumeKeyNavigation(enabled) }
    fun setReflowFontScale(scale: Float) = viewModelScope.launch { prefsRepo.setReflowFontScale(scale) }
    fun setHardwareTierOverride(tier: HardwareTierPreference) = viewModelScope.launch {
        prefsRepo.setHardwareTierOverride(tier)
    }
    fun setLibraryGridView(grid: Boolean) = viewModelScope.launch { prefsRepo.setLibraryGridView(grid) }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application, container) as T
        }
    }
}
