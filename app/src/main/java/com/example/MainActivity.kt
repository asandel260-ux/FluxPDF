package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppThemeMode
import com.example.ui.home.EngineLabScreen
import com.example.ui.home.LibraryScreen
import com.example.ui.home.SettingsScreen
import com.example.ui.reader.ReaderScreen
import com.example.ui.theme.FluxPdfTheme
import com.example.viewmodel.LibraryEvent
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.MutableSharedFlow

private enum class HomeTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    LIBRARY("Library", Icons.Filled.Book, Icons.Outlined.Book),
    ENGINE("Engine", Icons.Filled.Speed, Icons.Outlined.Speed),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

    private val incomingPdfUris = MutableSharedFlow<Uri>(extraBufferCapacity = 4)
    private val volumeKeyStepEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    @Volatile private var readerActive: Boolean = false
    @Volatile private var volumeNavEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractPdfUri(intent)?.let { incomingPdfUris.tryEmit(it) }

        val app = application as FluxPdfApplication
        setContent {
            FluxPdfRootApp(
                application = app,
                incomingPdfUris = incomingPdfUris,
                volumeKeyStepEvents = volumeKeyStepEvents,
                onReaderActiveChanged = { active, volNav ->
                    readerActive = active
                    volumeNavEnabled = volNav
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractPdfUri(intent)?.let { incomingPdfUris.tryEmit(it) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (readerActive && volumeNavEnabled && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeKeyStepEvents.tryEmit(1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeKeyStepEvents.tryEmit(-1)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }
}

@Composable
private fun FluxPdfRootApp(
    application: FluxPdfApplication,
    incomingPdfUris: MutableSharedFlow<Uri>,
    volumeKeyStepEvents: MutableSharedFlow<Int>,
    onReaderActiveChanged: (active: Boolean, volumeNav: Boolean) -> Unit,
) {
    val container = application.container
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(application, container),
    )
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val preferences by libraryViewModel.preferences.collectAsStateWithLifecycle()

    val darkTheme = when (preferences.appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.PAPER_LIGHT -> false
        AppThemeMode.OBSIDIAN_DARK -> true
    }

    var activeDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var currentTab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(activeDocumentId, preferences.volumeKeyNavigation) {
        onReaderActiveChanged(activeDocumentId != null, preferences.volumeKeyNavigation)
    }

    // Open PDFs launched via external ACTION_VIEW intents
    LaunchedEffect(incomingPdfUris) {
        incomingPdfUris.collect { uri ->
            libraryViewModel.importPdfUri(uri, openImmediately = true)
        }
    }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenDocument -> activeDocumentId = event.documentId
                is LibraryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Zero-permission SAF PDF picker
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            libraryViewModel.importPdfUri(uri, openImmediately = true)
        }
    }

    FluxPdfTheme(darkTheme = darkTheme) {
        val openDocId = activeDocumentId
        if (openDocId != null) {
            val readerViewModel: ReaderViewModel = viewModel(
                key = "reader_$openDocId",
                factory = ReaderViewModel.Factory(application, container, openDocId),
            )
            LaunchedEffect(volumeKeyStepEvents, readerViewModel) {
                volumeKeyStepEvents.collect { delta ->
                    readerViewModel.stepPage(delta)
                }
            }
            ReaderScreen(
                viewModel = readerViewModel,
                onCloseReader = {
                    activeDocumentId = null
                    libraryViewModel.refreshCacheMetrics()
                },
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 700.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        if (currentTab == HomeTab.LIBRARY) {
                            ExtendedFloatingActionButton(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                text = { Text("Open PDF") },
                                modifier = Modifier.testTag("open_pdf_fab"),
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                HomeTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                HomeTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (currentTab) {
                                HomeTab.LIBRARY -> LibraryScreen(
                                    uiState = libraryState,
                                    isGridView = preferences.libraryGridView,
                                    repository = container.documentRepository,
                                    onOpenDocument = { activeDocumentId = it },
                                    onImportPdfClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                    onGenerateStressPdf = { libraryViewModel.generateStressBenchmarkPdf(openAfter = false) },
                                    onSearchQueryChange = libraryViewModel::setSearchQuery,
                                    onFilterChange = libraryViewModel::setFilter,
                                    onTagSelect = libraryViewModel::setActiveTag,
                                    onSortChange = libraryViewModel::setSortOrder,
                                    onToggleGridView = libraryViewModel::setLibraryGridView,
                                    onTogglePin = libraryViewModel::togglePin,
                                    onSetCollectionTag = libraryViewModel::setCollectionTag,
                                    onDeleteDocument = libraryViewModel::deleteDocument,
                                    onBenchmarkDocument = { doc ->
                                        currentTab = HomeTab.ENGINE
                                        libraryViewModel.runLiveBenchmark(doc)
                                    },
                                )

                                HomeTab.ENGINE -> EngineLabScreen(
                                    uiState = libraryState,
                                    onRunBenchmark = { libraryViewModel.runLiveBenchmark() },
                                    onGenerateStressPdf = { libraryViewModel.generateStressBenchmarkPdf(openAfter = false) },
                                )

                                HomeTab.SETTINGS -> SettingsScreen(
                                    preferences = preferences,
                                    diskCacheKb = libraryState.diskCacheKb,
                                    onSetAppThemeMode = libraryViewModel::setAppThemeMode,
                                    onSetReadingColorMode = libraryViewModel::setReadingColorMode,
                                    onSetScrollMode = libraryViewModel::setScrollMode,
                                    onSetAutoCrop = libraryViewModel::setAutoCropMargins,
                                    onSetKeepScreenOn = libraryViewModel::setKeepScreenOn,
                                    onSetShowTelemetryHud = libraryViewModel::setShowTelemetryHud,
                                    onSetVolumeKeyNav = libraryViewModel::setVolumeKeyNavigation,
                                    onSetReflowFontScale = libraryViewModel::setReflowFontScale,
                                    onSetHardwareTierOverride = libraryViewModel::setHardwareTierOverride,
                                    onClearCaches = libraryViewModel::clearAllCaches,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
