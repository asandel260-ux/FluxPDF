package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PageDimension(
    val widthPt: Int,
    val heightPt: Int,
) {
    val aspectRatio: Float
        get() = if (heightPt > 0) widthPt.toFloat() / heightPt.toFloat() else 0.707f
}

data class BenchmarkReport(
    val documentTitle: String,
    val pageCount: Int,
    val fileSizeKb: Long,
    val hardwareTier: HardwareTier,
    val engineBackend: String,
    val coldOpenMs: Float,
    val xrefParseMs: Float,
    val samplePagesRendered: Int,
    val avgRenderMs: Float,
    val p95RenderMs: Float,
    val textExtractPagesPerSec: Float,
    val fullTextSearchMs: Float,
    val searchHitsFound: Int,
    val memoryDeltaMb: Float,
)

/**
 * Core native PDF document session.
 *
 * Coordinates:
 * - Android's native C++ PDFium renderer (`PdfRenderer`) on a dedicated serialized render thread
 *   with coroutine cancellation so fast flings drop stale pages before rasterization.
 * - Zero-copy `MappedByteBuffer` structural parser (`FluxPdfBinaryParser`) for outlines, metadata,
 *   and text extraction (with optional Rust JNI acceleration when `libfluxpdf_engine.so` is present).
 * - Memory-bounded LRU page cache (`FluxBitmapCache`) tuned to `HardwareProfile`.
 * - Viewport-clipped high-DPI zoom tile rendering + automatic whitespace margin detection.
 */
class FluxPdfDocumentSession private constructor(
    val documentId: Long,
    val file: File,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val parser: FluxPdfBinaryParser?,
    private val rustHandle: Long,
    private val bitmapCache: FluxBitmapCache,
    @Volatile var hardwareProfile: HardwareProfile,
    val coldOpenMs: Float,
    val pageDimensions: List<PageDimension>,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val renderExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FluxPDF-NativeRender-$documentId").apply { priority = Thread.NORM_PRIORITY + 1 }
    }
    private val renderDispatcher: CoroutineDispatcher = renderExecutor.asCoroutineDispatcher()
    private val cropBoundsCache = ConcurrentHashMap<Int, RectF>()

    val pageCount: Int = renderer.pageCount

    val structureMetadata: PdfStructureMetadata = parser?.metadata ?: PdfStructureMetadata(
        pdfVersion = "1.7",
        isLinearized = false,
        isEncrypted = false,
        xrefType = "Native PDFium",
        objectCount = 0,
        fileSizeBytes = file.length(),
        title = file.nameWithoutExtension,
        author = "",
        subject = "",
        keywords = "",
        creator = "",
        producer = "",
        creationDate = "",
        outlines = emptyList(),
        parseTimeMs = 0f,
    )

    companion object {
        suspend fun open(
            documentId: Long,
            file: File,
            bitmapCache: FluxBitmapCache,
            hardwareProfile: HardwareProfile,
        ): FluxPdfDocumentSession = withContext(Dispatchers.IO) {
            val startNanos = System.nanoTime()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val parser = runCatching { FluxPdfBinaryParser.open(file) }.getOrNull()
            val rustHandle = if (FluxNativeBridge.isRustLibraryLoaded) {
                runCatching { FluxNativeBridge.nativeOpenDocument(file.absolutePath) }.getOrDefault(-1L)
            } else {
                -1L
            }

            // Precompute all page aspect ratios up front (ultra-fast for O(1) LazyColumn geometry).
            // For massive docs (>150 pages), sample first 24 pages + exact default fallback for instant open.
            val count = renderer.pageCount
            val dims = ArrayList<PageDimension>(count)
            val exactLimit = min(count, 160)
            var fallbackDim = PageDimension(612, 792)
            for (i in 0 until exactLimit) {
                renderer.openPage(i).use { page ->
                    val d = PageDimension(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
                    if (i == 0) fallbackDim = d
                    dims.add(d)
                }
            }
            while (dims.size < count) {
                dims.add(fallbackDim)
            }

            val coldOpenMs = (System.nanoTime() - startNanos) / 1_000_000f
            FluxPdfDocumentSession(
                documentId = documentId,
                file = file,
                pfd = pfd,
                renderer = renderer,
                parser = parser,
                rustHandle = rustHandle,
                bitmapCache = bitmapCache,
                hardwareProfile = hardwareProfile,
                coldOpenMs = coldOpenMs,
                pageDimensions = dims,
            )
        }
    }

    fun getPageDimension(pageIndex: Int): PageDimension {
        return pageDimensions.getOrElse(pageIndex) { PageDimension(612, 792) }
    }

    suspend fun renderPage(
        pageIndex: Int,
        requestedWidthPx: Int,
        isPreview: Boolean = false,
        cropNormalizedRect: RectF? = null,
    ): Bitmap? {
        if (closed.get() || pageIndex !in 0 until pageCount) return null
        val cappedWidth = requestedWidthPx.coerceIn(
            120,
            if (isPreview) 540 else hardwareProfile.maxPageRenderWidthPx,
        )
        // Quantize width to 64px buckets to maximize LRU cache hits across minor layout changes
        val bucketWidth = ((cappedWidth + 31) / 64 * 64).coerceAtLeast(128)
        val cacheKey = PageRenderKey(
            documentId = documentId,
            pageIndex = pageIndex,
            widthPx = if (cropNormalizedRect == null) bucketWidth else -bucketWidth,
            isPreview = isPreview,
        )
        bitmapCache.get(cacheKey)?.let { return it }

        return withContext(renderDispatcher) {
            coroutineContext.ensureActive()
            if (closed.get()) return@withContext null
            bitmapCache.get(cacheKey)?.let { return@withContext it }

            val startNanos = System.nanoTime()
            val rendered = renderPageLocked(
                pageIndex = pageIndex,
                targetWidth = bucketWidth,
                cropNormalized = cropNormalizedRect,
            )
            if (rendered != null) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f
                bitmapCache.recordRenderDuration(elapsedMs)
                bitmapCache.put(cacheKey, rendered)
            }
            rendered
        }
    }

    /**
     * Renders only the visible zoomed viewport tile at crisp high DPI without allocating
     * a full-page multi-megapixel bitmap.
     */
    suspend fun renderZoomTile(
        pageIndex: Int,
        normalizedViewport: RectF,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): Bitmap? {
        if (closed.get() || pageIndex !in 0 until pageCount) return null
        val safeW = targetWidthPx.coerceIn(256, hardwareProfile.maxTileRenderWidthPx)
        val safeH = targetHeightPx.coerceIn(256, hardwareProfile.maxTileRenderWidthPx)
        return withContext(renderDispatcher) {
            coroutineContext.ensureActive()
            if (closed.get()) return@withContext null
            val startNanos = System.nanoTime()
            val bmp = renderPageLocked(
                pageIndex = pageIndex,
                targetWidth = safeW,
                explicitHeight = safeH,
                cropNormalized = normalizedViewport,
            )
            if (bmp != null) {
                bitmapCache.recordRenderDuration((System.nanoTime() - startNanos) / 1_000_000f)
            }
            bmp
        }
    }

    /**
     * Detects non-white ink bounding box on a fast 240px thumbnail so Auto Margin Crop
     * trims excessive borders on academic papers and books.
     */
    suspend fun detectContentBounds(pageIndex: Int): RectF {
        cropBoundsCache[pageIndex]?.let { return it }
        val thumb = renderPage(pageIndex, requestedWidthPx = 220, isPreview = true)
            ?: return RectF(0f, 0f, 1f, 1f)
        val bounds = withContext(Dispatchers.Default) {
            val w = thumb.width
            val h = thumb.height
            val pixels = IntArray(w * h)
            thumb.getPixels(pixels, 0, w, 0, 0, w, h)

            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            var foundInk = false

            for (y in 0 until h step 2) {
                val rowOff = y * w
                for (x in 0 until w step 2) {
                    val px = pixels[rowOff + x]
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    if (r < 242 || g < 242 || b < 242) {
                        foundInk = true
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (!foundInk || maxX <= minX || maxY <= minY) {
                RectF(0f, 0f, 1f, 1f)
            } else {
                val padX = (w * 0.03f)
                val padY = (h * 0.025f)
                val left = ((minX - padX) / w).coerceIn(0f, 0.28f)
                val top = ((minY - padY) / h).coerceIn(0f, 0.25f)
                val right = ((maxX + padX) / w).coerceIn(0.72f, 1f)
                val bottom = ((maxY + padY) / h).coerceIn(0.75f, 1f)
                // Only crop if we reclaim meaningful margin (> 8% width)
                if ((right - left) < 0.92f) RectF(left, top, right, bottom) else RectF(0f, 0f, 1f, 1f)
            }
        }
        cropBoundsCache[pageIndex] = bounds
        return bounds
    }

    suspend fun prefetchPages(centerPage: Int, direction: Int, viewportWidthPx: Int) {
        val radius = hardwareProfile.prefetchRadius
        val order = ArrayList<Int>(radius * 2)
        for (step in 1..radius) {
            val forward = centerPage + step * (if (direction >= 0) 1 else -1)
            val backward = centerPage - step * (if (direction >= 0) 1 else -1)
            if (forward in 0 until pageCount) order.add(forward)
            if (step == 1 && backward in 0 until pageCount) order.add(backward)
        }
        for (page in order) {
            if (closed.get()) break
            renderPage(page, viewportWidthPx, isPreview = false)
        }
    }

    fun extractPageText(pageIndex: Int): String {
        if (rustHandle > 0 && FluxNativeBridge.isRustLibraryLoaded) {
            val rustText = runCatching {
                FluxNativeBridge.nativeExtractPageText(rustHandle, pageIndex)
            }.getOrNull()
            if (!rustText.isNullOrEmpty()) return rustText
        }
        return parser?.extractPageText(pageIndex).orEmpty()
    }

    /**
     * Executes a genuine, measurable end-to-end performance benchmark against this live PDF.
     */
    suspend fun runLiveBenchmark(sampleQuery: String = "FluxPDF"): BenchmarkReport =
        withContext(Dispatchers.Default) {
            val runtime = Runtime.getRuntime()
            val memBefore = runtime.totalMemory() - runtime.freeMemory()

            val sampleCount = min(pageCount, 20).coerceAtLeast(1)
            val renderTimes = FloatArray(sampleCount)
            val benchWidth = hardwareProfile.maxPageRenderWidthPx.coerceAtMost(1080)

            for (i in 0 until sampleCount) {
                val pageIdx = if (pageCount <= sampleCount) i else (i * pageCount) / sampleCount
                val t0 = System.nanoTime()
                withContext(renderDispatcher) {
                    if (!closed.get()) {
                        val bmp = renderPageLocked(pageIdx, benchWidth, cropNormalized = null)
                        bmp?.recycle()
                    }
                }
                renderTimes[i] = (System.nanoTime() - t0) / 1_000_000f
            }
            renderTimes.sort()
            val avgRender = renderTimes.average().toFloat()
            val p95Render = renderTimes[((sampleCount - 1) * 0.95f).toInt().coerceIn(0, sampleCount - 1)]

            val extractStart = System.nanoTime()
            val extractPages = min(pageCount, 60).coerceAtLeast(1)
            for (p in 0 until extractPages) {
                extractPageText(p)
            }
            val extractSec = ((System.nanoTime() - extractStart) / 1_000_000_000f).coerceAtLeast(0.0005f)
            val pagesPerSec = extractPages / extractSec

            val searchStart = System.nanoTime()
            var hits = 0
            val lowerQ = sampleQuery.lowercase()
            for (p in 0 until pageCount) {
                val txt = extractPageText(p).lowercase()
                var idx = 0
                while (true) {
                    val found = txt.indexOf(lowerQ, idx)
                    if (found < 0) break
                    hits++
                    idx = found + lowerQ.length
                }
            }
            val searchMs = (System.nanoTime() - searchStart) / 1_000_000f

            val memAfter = runtime.totalMemory() - runtime.freeMemory()
            val memDeltaMb = max(0f, (memAfter - memBefore) / (1024f * 1024f))

            BenchmarkReport(
                documentTitle = structureMetadata.title.ifBlank { file.nameWithoutExtension },
                pageCount = pageCount,
                fileSizeKb = (file.length() / 1024L).coerceAtLeast(1L),
                hardwareTier = hardwareProfile.activeTier,
                engineBackend = FluxNativeBridge.engineBackendLabel,
                coldOpenMs = coldOpenMs,
                xrefParseMs = structureMetadata.parseTimeMs,
                samplePagesRendered = sampleCount,
                avgRenderMs = avgRender,
                p95RenderMs = p95Render,
                textExtractPagesPerSec = pagesPerSec,
                fullTextSearchMs = searchMs,
                searchHitsFound = hits,
                memoryDeltaMb = memDeltaMb,
            )
        }

    private fun renderPageLocked(
        pageIndex: Int,
        targetWidth: Int,
        explicitHeight: Int? = null,
        cropNormalized: RectF? = null,
    ): Bitmap? {
        if (closed.get()) return null
        return try {
            renderer.openPage(pageIndex).use { page ->
                val pageW = page.width.coerceAtLeast(1).toFloat()
                val pageH = page.height.coerceAtLeast(1).toFloat()

                val crop = cropNormalized ?: RectF(0f, 0f, 1f, 1f)
                val cropW = (crop.width().coerceIn(0.05f, 1f)) * pageW
                val cropH = (crop.height().coerceIn(0.05f, 1f)) * pageH

                val outW = targetWidth.coerceAtLeast(64)
                val outH = explicitHeight ?: (outW * (cropH / cropW)).roundToInt().coerceIn(64, 3200)

                val bmp = Bitmap.createBitmap(outW, outH, hardwareProfile.bitmapConfig)
                // Always fill crisp paper white before native PDFium composite
                Canvas(bmp).drawColor(Color.WHITE)

                val matrix = Matrix().apply {
                    val scaleX = outW.toFloat() / cropW
                    val scaleY = outH.toFloat() / cropH
                    postTranslate(-crop.left * pageW, -crop.top * pageH)
                    postScale(scaleX, scaleY)
                }

                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        renderExecutor.execute {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            runCatching { parser?.close() }
            if (rustHandle > 0 && FluxNativeBridge.isRustLibraryLoaded) {
                runCatching { FluxNativeBridge.nativeCloseDocument(rustHandle) }
            }
        }
        renderExecutor.shutdown()
    }
}
