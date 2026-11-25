package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.BenchmarkReport
import com.example.engine.FluxNativeBridge
import com.example.viewmodel.LibraryUiState

@Composable
fun EngineLabScreen(
    uiState: LibraryUiState,
    onRunBenchmark: () -> Unit,
    onGenerateStressPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hw = uiState.hardwareProfile
    val tel = uiState.telemetry

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "TELEMETRY & BENCHMARKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Native Engine Lab",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Engine Architecture & Rust JNI Bridge Status Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DOCUMENT ENGINE PIPELINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = FluxNativeBridge.engineBackendLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (FluxNativeBridge.isRustLibraryLoaded) {
                            "Compiled Rust shared library (libfluxpdf_engine.so) active via JNI for mmap XRef parsing and search."
                        } else {
                            "Native C++ PDFium rasterizer + zero-copy MappedByteBuffer parser active. Rust crate ready under rust/fluxpdf-engine/ for cargo-ndk builds."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Hardware Profile & Adaptive Tier Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ADAPTIVE HARDWARE SCALING",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = hw.activeTier.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hw.activeTier.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            label = "CPU CORES",
                            value = "${hw.cpuCores}",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "HEAP CAP",
                            value = "${hw.maxHeapMb} MB",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "SURFACE",
                            value = hw.bitmapConfig.name,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            label = "MAX PAGE",
                            value = "${hw.maxPageRenderWidthPx}px",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "PREFETCH",
                            value = "+${hw.prefetchRadius} pg",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "LOW-RAM",
                            value = if (hw.isLowRamDevice) "YES" else "NO",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Live Bitmap Cache Telemetry Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "LIVE LRU BITMAP CACHE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${"%.1f".format(tel.cacheUsedMb)} / ${"%.1f".format(tel.cacheBudgetMb)} MB",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val ratio = if (tel.cacheBudgetMb > 0f) {
                        (tel.cacheUsedMb / tel.cacheBudgetMb).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            label = "HIT RATE",
                            value = "${tel.hitRatePercent}%",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "LAST FRAME",
                            value = "${"%.1f".format(tel.lastRenderMs)} ms",
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = "P95 RENDER",
                            value = "${"%.1f".format(tel.p95RenderMs)} ms",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Interactive Benchmark Controls
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reproducible Performance Suite",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Measures cold open, zero-copy XRef parsing, multi-page PDFium rasterization (avg & p95), text extraction throughput, and full-text search latency on real PDFs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onRunBenchmark,
                            enabled = !uiState.isBenchmarkRunning && !uiState.isGeneratingStressPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_benchmark_button"),
                        ) {
                            if (uiState.isBenchmarkRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Running…")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Benchmark")
                            }
                        }

                        OutlinedButton(
                            onClick = onGenerateStressPdf,
                            enabled = !uiState.isGeneratingStressPdf && !uiState.isBenchmarkRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_stress_pdf_button"),
                        ) {
                            if (uiState.isGeneratingStressPdf) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Building…")
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("120p Stress PDF")
                            }
                        }
                    }
                }
            }
        }

        // Latest Benchmark Results
        uiState.benchmarkReport?.let { report ->
            item {
                BenchmarkReportCard(report = report)
            }
        }
    }
}

@Composable
private fun BenchmarkReportCard(report: BenchmarkReport) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("benchmark_report_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "BENCHMARK RESULT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${report.documentTitle} (${report.pageCount}p • ${report.fileSizeKb} KB)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            BenchmarkBarRow("Cold Document Open", "${"%.2f".format(report.coldOpenMs)} ms", (report.coldOpenMs / 100f).coerceIn(0.05f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("Zero-Copy XRef + Outline Parse", "${"%.2f".format(report.xrefParseMs)} ms", (report.xrefParseMs / 80f).coerceIn(0.04f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("Avg Render (${report.samplePagesRendered} pages)", "${"%.2f".format(report.avgRenderMs)} ms", (report.avgRenderMs / 60f).coerceIn(0.05f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("P95 Render Latency", "${"%.2f".format(report.p95RenderMs)} ms", (report.p95RenderMs / 90f).coerceIn(0.05f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("Text Extraction Throughput", "${"%.0f".format(report.textExtractPagesPerSec)} pg/s", (report.textExtractPagesPerSec / 600f).coerceIn(0.15f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("Full-Text Search (${report.searchHitsFound} hits)", "${"%.2f".format(report.fullTextSearchMs)} ms", (report.fullTextSearchMs / 120f).coerceIn(0.05f, 1f))
            Spacer(Modifier.height(10.dp))
            BenchmarkBarRow("Peak Heap Delta", "${"%.2f".format(report.memoryDeltaMb)} MB", (report.memoryDeltaMb / 24f).coerceIn(0.04f, 1f))
        }
    }
}

@Composable
private fun BenchmarkBarRow(label: String, value: String, fillFraction: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fillFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
