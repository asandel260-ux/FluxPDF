package com.example.engine

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

/**
 * Pure-Kotlin spec-compliant PDF 1.7 generator that constructs genuine compressed PDF documents
 * with embedded `/Outlines` Table of Contents, `/Info` metadata, vector architectural diagrams,
 * tables, and searchable `/FlateDecode` text streams.
 */
object SamplePdfGenerator {

    private data class PageSpec(
        val chapterTitle: String?,
        val sectionHeading: String,
        val subtitle: String,
        val paragraphs: List<String>,
        val tableRows: List<Pair<String, String>> = emptyList(),
        val diagramCaption: String? = null,
    )

    fun ensureFieldManualPdf(context: Context): File {
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, "FluxPDF_Field_Manual.pdf")
        if (target.exists() && target.length() > 4096) return target

        val pages = buildFieldManualPages()
        writePdfDocument(
            outputFile = target,
            title = "FluxPDF Architecture & Field Manual",
            author = "FluxPDF Systems Engineering",
            subject = "High-Performance Native Document Engine Specification",
            pages = pages,
        )
        return target
    }

    fun generateStressBenchmarkPdf(context: Context, pageCount: Int = 120): File {
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, "FluxPDF_Stress_Benchmark_${pageCount}p.pdf")

        val pages = ArrayList<PageSpec>(pageCount)
        val topics = listOf(
            "Zero-Copy Memory-Mapped XRef Indexing",
            "Progressive Viewport-Clipped Tile Rasterization",
            "Adaptive Hardware Tier Scaling & RGB_565 Fallback",
            "FlateDecode & PNG Predictor Stream Unfiltering",
            "ToUnicode CMap Glyph Decoding & Reflow Layout",
            "Predictive Velocity-Driven Page Prefetching",
            "Deterministic LRU Bitmap Budget Enforcement",
            "Sub-Millisecond Full-Text Inverted Search",
            "Non-Destructive Normalized Vector Annotations",
            "Automatic Whitespace Margin Crop Analysis",
            "Optical Color-Matrix Night & Sepia Pipelines",
            "Rust JNI Boundary & FFI Latency Minimization",
        )

        for (i in 0 until pageCount) {
            val chapterIdx = (i / 10).coerceAtMost(topics.lastIndex)
            val isChapterStart = i % 10 == 0
            val topic = topics[chapterIdx]
            pages.add(
                PageSpec(
                    chapterTitle = if (isChapterStart) "Part ${chapterIdx + 1}: $topic" else null,
                    sectionHeading = "${chapterIdx + 1}.${(i % 10) + 1} $topic",
                    subtitle = "Benchmark Folio Page ${i + 1} of $pageCount  •  FluxPDF Stress Corpus",
                    paragraphs = listOf(
                        "FluxPDF evaluates document responsiveness across continuous scrolling, instant jump-to-page navigation, pinch-to-zoom tile synthesis, and concurrent full-text search. Page ${i + 1} exercises compressed FlateDecode content streams alongside clean vector borders and tabular metrics.",
                        "When scrolling rapidly across large technical specifications, the serialized native render queue cancels obsolete off-screen pages before rasterization begins. This keeps frame scheduling predictable on both entry-level low-RAM handsets and flagship tablets.",
                        "Key search tokens for automated verification: FluxPDF, PDFium, MappedByteBuffer, XRefStream, FlateDecode, ToUnicode, HardwareTier, BenchmarkMarker_${i + 1}.",
                    ),
                    tableRows = listOf(
                        "Page Index" to "${i + 1} / $pageCount",
                        "Pipeline Stage" to topic.take(36),
                        "Compression" to "ISO 32000 FlateDecode (Zlib)",
                        "Search Marker" to "BenchmarkMarker_${i + 1}",
                    ),
                    diagramCaption = if (i % 4 == 0) "Figure ${i + 1}: Tile viewport budget & cache residency profile" else null,
                ),
            )
        }

        writePdfDocument(
            outputFile = target,
            title = "FluxPDF 120-Page Stress Benchmark Folio",
            author = "FluxPDF Performance Lab",
            subject = "120-Page Multi-Chapter Rendering & Search Stress Test",
            pages = pages,
        )
        return target
    }

    private fun buildFieldManualPages(): List<PageSpec> = listOf(
        PageSpec(
            chapterTitle = "1. Executive Overview",
            sectionHeading = "FluxPDF Architecture & Field Manual",
            subtitle = "Precision Native Document Engine  •  Edition 1.0",
            paragraphs = listOf(
                "FluxPDF is engineered from first principles to eliminate every source of friction in mobile document reading: cold-start lag, scroll stutter, blurry zoom passes, excessive heap allocation, and cluttered toolbars.",
                "Rather than relying on opaque black-box wrappers, FluxPDF combines Android's native C++ PDFium rasterizer with a zero-copy MappedByteBuffer binary PDF structural parser and a Rust JNI core crate designed for high-throughput text extraction and full-text search.",
                "Use this twelve-page field manual to test Table of Contents navigation, instant full-text search, Accessible Text Reflow mode, Auto Margin Crop, optical night themes, interactive highlights, freehand ink, sticky notes, and live performance telemetry.",
            ),
            tableRows = listOf(
                "Document Standard" to "PDF 1.0 through PDF 2.0 (ISO 32000-2)",
                "Memory Strategy" to "Zero-copy mmap + bounded LRU Bitmap cache",
                "Zoom Architecture" to "Viewport-clipped high-DPI tile synthesis",
                "Accessibility" to "Live text reflow + 5 optical color matrices",
            ),
            diagramCaption = "Figure 1.1: FluxPDF layered native rendering & structural parsing pipeline",
        ),
        PageSpec(
            chapterTitle = "2. Zero-Copy Binary Parser",
            sectionHeading = "MappedByteBuffer & XRef Resolution",
            subtitle = "Direct Structural Access Without Full-File Heap Copies",
            paragraphs = listOf(
                "Android's built-in PdfRenderer excels at rasterizing page bitmaps via PDFium, yet exposes no API for document metadata, hierarchical outlines, object graphs, or searchable text extraction. FluxPDF bridges this gap with a custom zero-copy binary parser.",
                "Upon opening a PDF, FluxPDF maps the file directly into virtual address space via FileChannel.MapMode.READ_ONLY (and memmap2 in the Rust engine). Cross-reference tables—including incremental /Prev chains and PDF 1.5+ compressed /Type /XRef streams with PNG predictors—are resolved in milliseconds.",
                "Modern documents exported from LaTeX, Chrome, and desktop publishing suites pack objects inside /Type /ObjStm streams. FluxPDF inflates and caches object streams lazily on demand.",
            ),
            tableRows = listOf(
                "Classic XRef" to "Supported with incremental /Prev chain traversal",
                "XRef Streams" to "Supported with /W field widths & PNG predictor",
                "Object Streams" to "Lazy on-demand decompression & caching",
                "Outline Tree" to "Recursive /First /Next hierarchy + /GoTo resolver",
            ),
        ),
        PageSpec(
            chapterTitle = "3. Adaptive Hardware Scaling",
            sectionHeading = "Graceful Performance Across Every Device",
            subtitle = "Deterministic Memory Ceilings & Zero-OOM Guarantees",
            paragraphs = listOf(
                "High-resolution PDF bitmaps can easily exhaust mobile memory when unmanaged. A single 300-DPI ARGB_8888 page consumes over 30 megabytes of RAM. FluxPDF inspects ActivityManager.isLowRamDevice, heap budget, and CPU core count at launch to classify each device into Constrained, Balanced, or Performance tiers.",
                "Constrained devices automatically adopt RGB_565 surfaces (halving memory consumption per pixel) and a tight 14 MB cache ceiling while maintaining crisp typography. Flagship devices scale up to 1800px full-page surfaces, 2048px zoom tiles, and 3-page directional prefetching.",
                "Visit the Engine tab at any time to inspect live cache hit rates, p95 render latency, and override the hardware scaling tier.",
            ),
            tableRows = listOf(
                "Constrained Tier" to "RGB_565 • 1080px max • 14 MB cap • 1-page prefetch",
                "Balanced Tier" to "ARGB_8888 • 1440px max • 36 MB cap • 2-page prefetch",
                "Performance Tier" to "ARGB_8888 • 1800px max • 72 MB cap • 3-page prefetch",
            ),
            diagramCaption = "Figure 3.1: Memory budget allocation vs. hardware capability tier",
        ),
        PageSpec(
            chapterTitle = "4. Viewport-Clipped Tiled Zoom",
            sectionHeading = "Razor-Sharp Typography at 500% Magnification",
            subtitle = "Eliminating Blurry Upscaling & Out-of-Memory Spikes",
            paragraphs = listOf(
                "Conventional viewers either upscale a fixed bitmap when pinching to zoom—producing fuzzy edges—or attempt to re-render the entire page at 4x dimensions, causing multi-second freezes and memory spikes.",
                "FluxPDF separates gesture response from rasterization. Pinching and panning update GPU transformation matrices at full display refresh rate, then dispatch a debounced viewport-clipped tile render covering only the visible screen region at native pixel density.",
                "Try pinching to zoom on this diagram or double-tapping any paragraph to jump between 1.0x and 2.4x precision magnification.",
            ),
            diagramCaption = "Figure 4.1: Viewport crop matrix transform into high-DPI tile buffer",
        ),
        PageSpec(
            chapterTitle = "5. Full-Text Streaming Search",
            sectionHeading = "Progressive Indexing & Contextual Snippets",
            subtitle = "Case-Sensitive, Whole-Word, and Regular Expression Modes",
            paragraphs = listOf(
                "Searching a 400-page reference manual should never lock the interface behind an indeterminate spinner. FluxPDF streams search results progressively as pages are decoded, surfacing the first matches within milliseconds.",
                "Every search hit includes an aligned contextual snippet highlighting the exact matched term alongside its page number. Tapping any hit jumps immediately to that page and records your previous location in the navigation history stack so you can bounce straight back.",
                "Open the Search tool in the top bar and try querying 'FlateDecode', 'PDFium', or regex pattern 'Figure \\d\\.\\d'.",
            ),
            tableRows = listOf(
                "Case-Insensitive" to "Default fast substring scan",
                "Whole-Word Mode" to "Boundary-checked alphanumeric matching",
                "Regex Mode" to "Full pattern matching with live syntax validation",
            ),
        ),
        PageSpec(
            chapterTitle = "6. Optical Reading & Reflow",
            sectionHeading = "Five Zero-Overhead Optical Themes & Reflow",
            subtitle = "Built for Long-Form Deep Reading & Accessibility",
            paragraphs = listOf(
                "Staring at stark white PDF pages at night induces eye strain. FluxPDF applies hardware-accelerated GPU ColorMatrix filters directly during page composition, enabling instant switching between Original Paper, Warm Sepia, Midnight Ink, OLED True Dark, and High Contrast.",
                "For small phones or readers who prefer larger typography, toggle Text Reflow mode in the top toolbar. FluxPDF extracts the underlying page prose and typesets it in Newsreader serif with customizable scale from 85% to 180%.",
                "Auto Margin Crop analyzes whitespace borders on each page and trims dead margins automatically, giving dense two-column papers up to 25% larger text.",
            ),
            tableRows = listOf(
                "Original Paper" to "True archival rendering with warm desk canvas",
                "Warm Sepia" to "Low-blue-light parchment matrix transform",
                "Midnight & OLED" to "Inverted luminance preserving readable contrast",
                "Reflow Mode" to "Newsreader serif stream with dynamic font scaling",
            ),
        ),
        PageSpec(
            chapterTitle = "7. Non-Destructive Annotations",
            sectionHeading = "Highlights, Underlines, Freehand Ink & Margin Notes",
            subtitle = "Resolution-Independent Normalized Coordinates",
            paragraphs = listOf(
                "All FluxPDF annotations are stored in normalized page coordinates (0.0 to 1.0) inside a local Room SQLite database. Your highlights, underlines, Bezier ink strokes, and sticky margin notes stay aligned across zoom levels, screen rotations, and tablet layouts.",
                "Tap the pen icon in the reader toolbar to switch between Highlight, Underline, Freehand Ink, and Sticky Note tools. Choose from five archival pigment colors and undo any stroke instantaneously.",
                "Need to share your study notes? Export all bookmarks and annotations as structured Markdown or flatten them onto a fresh shareable PDF via Android FileProvider.",
            ),
            diagramCaption = "Figure 7.1: Normalized coordinate annotation layer over PDFium canvas",
        ),
        PageSpec(
            chapterTitle = "8. Navigation & Productivity",
            sectionHeading = "Outlines, Thumbnails, Bookmarks & Jump History",
            subtitle = "Effortless Traversal Across Complex Documents",
            paragraphs = listOf(
                "Open the Navigation sheet to switch seamlessly between a visual Thumbnail Grid, the document's hierarchical Table of Contents parsed directly from PDF /Outlines, your named Bookmarks, and a chronological Annotation ledger.",
                "FluxPDF also supports Vertical Continuous Scroll, Horizontal Single-Page Book Swipe, and—on landscape phones and tablets—a side-by-side Double-Page Spread.",
                "A built-in Jump History stack remembers where you came from whenever you click a TOC chapter, search hit, or scrubber position, allowing one-tap return to your original reading spot.",
            ),
        ),
        PageSpec(
            chapterTitle = "9. Rust-First Native Core",
            sectionHeading = "Cross-Compiled Rust Engine & JNI Integration",
            subtitle = "Zero-Allocation Systems Engineering",
            paragraphs = listOf(
                "The repository includes the complete Rust native crate under rust/fluxpdf-engine/, implementing memory-mapped PDF parsing (memmap2), Zlib stream inflation (flate2), and fast search behind a minimal zero-copy JNI boundary.",
                "At runtime, FluxNativeBridge probes for libfluxpdf_engine.so. When compiled with cargo-ndk for arm64-v8a, armeabi-v7a, and x86_64, heavy parsing and search execute in native Rust machine code. When run in pure-JVM environments without NDK artifacts, FluxPDF seamlessly engages its matching zero-copy MappedByteBuffer engine.",
            ),
            tableRows = listOf(
                "Crate Path" to "rust/fluxpdf-engine/Cargo.toml",
                "Build Script" to "rust/fluxpdf-engine/build-android.sh",
                "Runtime Bridge" to "com.example.engine.FluxNativeBridge",
            ),
        ),
        PageSpec(
            chapterTitle = "10. Privacy & Zero-Permission Storage",
            sectionHeading = "Storage Access Framework & Local-First Security",
            subtitle = "Google Play Compliant Document Architecture",
            paragraphs = listOf(
                "FluxPDF requests zero broad external storage permissions. Documents are opened securely through Android's Storage Access Framework (ACTION_OPEN_DOCUMENT) and standard PDF VIEW intents, granting least-privilege access strictly to the files you choose.",
                "Every document you open is indexed locally in Room with persistent reading progress, pins, collections, bookmarks, and annotations. No cloud account is required, and no document contents ever leave your device.",
            ),
        ),
        PageSpec(
            chapterTitle = "11. Document Inspector & Telemetry",
            sectionHeading = "Real-Time Performance Verification",
            subtitle = "Engineering Performance as a Measurable Metric",
            paragraphs = listOf(
                "Tap the Inspector button in the reader toolbar to audit PDF specification version, XRef architecture, Linearized Fast Web View status, object count, physical page dimensions in points and millimeters, and live LRU cache telemetry.",
                "Enable the Telemetry HUD in Display Options to monitor real-time page render latency (ms), p95 frame render time, and active bitmap cache consumption while scrolling through heavy documents.",
            ),
            diagramCaption = "Figure 11.1: Live telemetry instrumentation & p95 render distribution",
        ),
        PageSpec(
            chapterTitle = "12. Stress Benchmarking Guide",
            sectionHeading = "Testing FluxPDF Under Real Workload",
            subtitle = "Reproducible 120-Page Stress Benchmark Suite",
            paragraphs = listOf(
                "Switch to the Engine tab on the home screen and tap 'Generate 120-Page Stress PDF' to synthesize a 120-page, 12-chapter compressed PDF directly on device. Then execute 'Run Live Benchmark' to measure real cold-open time, XRef parsing speed, average and p95 rasterization latency, text extraction throughput, and full-text search speed.",
                "Thank you for reading with FluxPDF. Every detail of this reader—from typography and spacing to bitmap recycling and binary parsing—was crafted for uncompromising speed and clarity.",
            ),
            tableRows = listOf(
                "Cold Open Target" to "< 40 ms on mid-range hardware",
                "Page Render Target" to "< 25 ms average display-DPI pass",
                "Full-Text Search" to "Progressive streaming across 100+ pages",
            ),
        ),
    )

    private fun writePdfDocument(
        outputFile: File,
        title: String,
        author: String,
        subject: String,
        pages: List<PageSpec>,
    ) {
        val objects = ArrayList<ByteArray>()
        fun addObj(content: String): Int {
            objects.add(content.toByteArray(StandardCharsets.ISO_8859_1))
            return objects.size
        }

        fun addStreamObj(dictEntries: String, rawStream: ByteArray): Int {
            val compressed = deflateZlib(rawStream)
            val header = "<< $dictEntries /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n"
            val footer = "\nendstream"
            val bos = ByteArrayOutputStream(header.length + compressed.size + footer.length)
            bos.write(header.toByteArray(StandardCharsets.ISO_8859_1))
            bos.write(compressed)
            bos.write(footer.toByteArray(StandardCharsets.ISO_8859_1))
            objects.add(bos.toByteArray())
            return objects.size
        }

        // Reserve IDs: 1 = Catalog, 2 = Pages, 3 = FontRegular, 4 = FontBold, 5 = FontMono, 6 = Info, 7 = OutlinesRoot
        val catalogId = addObj("")
        val pagesId = addObj("")
        val fontRegularId = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        val fontBoldId = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        val fontMonoId = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        val infoId = addObj(
            "<< /Title (${escapePdf(title)}) /Author (${escapePdf(author)}) " +
                "/Subject (${escapePdf(subject)}) /Creator (FluxPDF Native Writer) " +
                "/Producer (FluxPDF 1.0 ISO-32000 Engine) /CreationDate (D:20261124120000Z) >>",
        )
        val outlinesRootId = addObj("")

        val pageObjIds = ArrayList<Int>(pages.size)
        val outlineChapters = ArrayList<Pair<String, Int>>() // (chapterTitle, pageObjId)

        pages.forEachIndexed { idx, spec ->
            val streamBytes = buildPageContentStream(spec, idx + 1, pages.size)
            val contentObjId = addStreamObj("", streamBytes)
            val pageObjId = addObj(
                "<< /Type /Page /Parent $pagesId 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 $fontRegularId 0 R /F2 $fontBoldId 0 R /F3 $fontMonoId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>",
            )
            pageObjIds.add(pageObjId)
            if (spec.chapterTitle != null) {
                outlineChapters.add(spec.chapterTitle to pageObjId)
            }
        }

        // Build /Outlines linked list
        val outlineObjIds = ArrayList<Int>(outlineChapters.size)
        for (i in outlineChapters.indices) {
            outlineObjIds.add(addObj(""))
        }
        outlineChapters.forEachIndexed { i, (chapTitle, pageObjId) ->
            val prevPart = if (i > 0) "/Prev ${outlineObjIds[i - 1]} 0 R" else ""
            val nextPart = if (i < outlineObjIds.lastIndex) "/Next ${outlineObjIds[i + 1]} 0 R" else ""
            objects[outlineObjIds[i] - 1] = (
                "<< /Title (${escapePdf(chapTitle)}) /Parent $outlinesRootId 0 R " +
                    "$prevPart $nextPart /Dest [$pageObjId 0 R /Fit] >>"
                ).toByteArray(StandardCharsets.ISO_8859_1)
        }

        if (outlineObjIds.isNotEmpty()) {
            objects[outlinesRootId - 1] = (
                "<< /Type /Outlines /First ${outlineObjIds.first()} 0 R " +
                    "/Last ${outlineObjIds.last()} 0 R /Count ${outlineObjIds.size} >>"
                ).toByteArray(StandardCharsets.ISO_8859_1)
        } else {
            objects[outlinesRootId - 1] = "<< /Type /Outlines /Count 0 >>".toByteArray(StandardCharsets.ISO_8859_1)
        }

        val kidsList = pageObjIds.joinToString(" ") { "$it 0 R" }
        objects[pagesId - 1] = "<< /Type /Pages /Kids [$kidsList] /Count ${pageObjIds.size} >>"
            .toByteArray(StandardCharsets.ISO_8859_1)
        objects[catalogId - 1] = "<< /Type /Catalog /Pages $pagesId 0 R /Outlines $outlinesRootId 0 R >>"
            .toByteArray(StandardCharsets.ISO_8859_1)

        // Write binary PDF with exact byte-offset xref table
        FileOutputStream(outputFile).use { fos ->
            val offsets = IntArray(objects.size + 1)
            var cursor = 0

            fun writeBytes(b: ByteArray) {
                fos.write(b)
                cursor += b.size
            }

            fun writeAscii(s: String) = writeBytes(s.toByteArray(StandardCharsets.ISO_8859_1))

            writeAscii("%PDF-1.7\n%\u00E2\u00E3\u00CF\u00D3\n")
            objects.forEachIndexed { i, objBytes ->
                val objId = i + 1
                offsets[objId] = cursor
                writeAscii("$objId 0 obj\n")
                writeBytes(objBytes)
                writeAscii("\nendobj\n")
            }

            val xrefOffset = cursor
            writeAscii("xref\n0 ${objects.size + 1}\n")
            writeAscii("0000000000 65535 f \n")
            for (objId in 1..objects.size) {
                writeAscii(String.format("%010d 00000 n \n", offsets[objId]))
            }
            writeAscii(
                "trailer\n<< /Size ${objects.size + 1} /Root $catalogId 0 R /Info $infoId 0 R >>\n" +
                    "startxref\n$xrefOffset\n%%EOF\n",
            )
        }
    }

    private fun buildPageContentStream(spec: PageSpec, pageNum: Int, totalPages: Int): ByteArray {
        val sb = StringBuilder(2048)

        // Subtle warm paper margin header rule & vermilion accent bar
        sb.append("q\n")
        sb.append("0.784 0.255 0.165 rg\n") // Archival Vermilion (#C8412A)
        sb.append("56 742 36 4 re f\n")
        sb.append("0.82 0.80 0.76 RG 0.6 w\n")
        sb.append("56 728 m 556 728 l S\n")
        sb.append("56 54 m 556 54 l S\n")
        sb.append("Q\n")

        // Running folio header
        sb.append("BT\n")
        sb.append("0.35 0.36 0.39 rg\n")
        sb.append("/F3 8.5 Tf\n")
        sb.append("102 741 Td (FLUXPDF TECHNICAL FOLIO) Tj\n")
        sb.append("360 0 Td (PAGE $pageNum / $totalPages) Tj\n")
        sb.append("ET\n")

        // Section Heading & Subtitle
        var y = 692f
        sb.append("BT\n")
        sb.append("0.08 0.085 0.10 rg\n")
        sb.append("/F2 21 Tf\n")
        sb.append("56 $y Td (${escapePdf(spec.sectionHeading)}) Tj\n")
        y -= 22f
        sb.append("0.784 0.255 0.165 rg\n")
        sb.append("/F1 10.5 Tf\n")
        sb.append("0 -22 Td (${escapePdf(spec.subtitle)}) Tj\n")
        sb.append("ET\n")
        y -= 28f

        // Body paragraphs with clean line wrapping
        sb.append("0.14 0.15 0.17 rg\n")
        for (paragraph in spec.paragraphs) {
            val lines = wrapText(paragraph, maxChars = 76)
            sb.append("BT /F1 11 Tf 16 TL 56 $y Td\n")
            lines.forEachIndexed { li, line ->
                if (li == 0) {
                    sb.append("(${escapePdf(line)}) Tj\n")
                } else {
                    sb.append("T* (${escapePdf(line)}) Tj\n")
                }
                y -= 16f
            }
            sb.append("ET\n")
            y -= 12f
        }

        // Structured table if present
        if (spec.tableRows.isNotEmpty() && y > 220f) {
            y -= 6f
            val rowH = 22f
            val tableTop = y
            val tableHeight = rowH * (spec.tableRows.size + 1)

            // Table background & header bar
            sb.append("q\n")
            sb.append("0.965 0.957 0.937 rg\n")
            sb.append("56 ${tableTop - tableHeight} 500 $tableHeight re f\n")
            sb.append("0.10 0.11 0.13 rg\n")
            sb.append("56 ${tableTop - rowH} 500 $rowH re f\n")
            sb.append("0.80 0.78 0.74 RG 0.5 w\n")
            sb.append("56 ${tableTop - tableHeight} 500 $tableHeight re S\n")
            sb.append("Q\n")

            // Header labels
            sb.append("BT 0.97 0.96 0.94 rg /F3 9 Tf 68 ${tableTop - 15f} Td (SPECIFICATION) Tj 175 0 Td (ENGINEERING VALUE) Tj ET\n")

            spec.tableRows.forEachIndexed { rIdx, (k, v) ->
                val rowY = tableTop - rowH * (rIdx + 2) + 7f
                sb.append("BT 0.12 0.13 0.15 rg /F2 9.5 Tf 68 $rowY Td (${escapePdf(k)}) Tj /F1 9.5 Tf 175 0 Td (${escapePdf(v)}) Tj ET\n")
                if (rIdx < spec.tableRows.lastIndex) {
                    val lineY = tableTop - rowH * (rIdx + 2)
                    sb.append("q 0.86 0.84 0.80 RG 0.5 w 56 $lineY m 556 $lineY l S Q\n")
                }
            }
            y = tableTop - tableHeight - 24f
        }

        // Crisp architectural vector diagram block if present
        if (spec.diagramCaption != null && y > 130f) {
            val boxH = 92f
            val boxBottom = (y - boxH).coerceAtLeast(78f)
            sb.append("q\n")
            sb.append("0.97 0.965 0.95 rg 56 $boxBottom 500 $boxH re f\n")
            sb.append("0.75 0.73 0.69 RG 0.75 w 56 $boxBottom 500 $boxH re S\n")
            // 3 pipeline nodes + connecting arrows
            val nodeY = boxBottom + 38f
            val nodes = listOf(76f to "mmap / XRef", 232f to "PDFium Tiles", 392f to "Compose GPU")
            for ((nx, _) in nodes) {
                sb.append("0.11 0.12 0.15 rg $nx $nodeY 132 34 re f\n")
            }
            // Vermilion connecting bars
            sb.append("0.784 0.255 0.165 RG 2.2 w\n")
            sb.append("208 ${nodeY + 17f} m 232 ${nodeY + 17f} l S\n")
            sb.append("364 ${nodeY + 17f} m 392 ${nodeY + 17f} l S\n")
            sb.append("Q\n")

            for ((nx, label) in nodes) {
                sb.append("BT 0.97 0.96 0.94 rg /F3 9.5 Tf ${nx + 16f} ${nodeY + 13f} Td (${escapePdf(label)}) Tj ET\n")
            }
            sb.append("BT 0.36 0.37 0.40 rg /F3 8.5 Tf 68 ${boxBottom + 12f} Td (${escapePdf(spec.diagramCaption)}) Tj ET\n")
        }

        // Footer
        sb.append("BT 0.45 0.46 0.49 rg /F3 8 Tf 56 38 Td (FluxPDF Native Archival Engine  •  Zero-Copy PDF 1.7) Tj 390 0 Td ($pageNum) Tj ET\n")

        return sb.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(' ')
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > maxChars) {
                lines.add(current.toString())
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun escapePdf(input: String): String {
        val sb = StringBuilder(input.length + 8)
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '\u2014', '\u2013' -> sb.append('-')
                '\u2022' -> sb.append('*')
                '\u2018', '\u2019' -> sb.append('\'')
                '\u201C', '\u201D' -> sb.append('"')
                else -> if (ch.code in 32..126) sb.append(ch) else sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun deflateZlib(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
