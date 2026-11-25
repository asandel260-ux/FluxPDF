package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.engine.FluxBitmapCache
import com.example.engine.FluxPdfBinaryParser
import com.example.engine.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Coordinates local Room storage, zero-permission SAF PDF ingestion, cover thumbnail
 * rendering, built-in field manual / stress benchmark creation, and Markdown / Flattened-PDF export.
 */
class FluxDocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    val bitmapCache: FluxBitmapCache,
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()

    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForDocument(documentId)

    fun observeAnnotations(documentId: Long): Flow<List<AnnotationEntity>> =
        annotationDao.observeForDocument(documentId)

    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getById(id)

    suspend fun ensureBuiltInFieldManual(): DocumentEntity = withContext(Dispatchers.IO) {
        val file = SamplePdfGenerator.ensureFieldManualPdf(context)
        val existing = documentDao.getByPath(file.absolutePath)
        if (existing != null) return@withContext existing

        val entity = inspectAndBuildEntity(
            file = file,
            displayFileName = file.name,
            sourceUri = null,
            collectionTag = "Manuals",
            isBuiltIn = true,
        )
        val id = documentDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun generateAndRegisterStressPdf(pageCount: Int = 120): DocumentEntity =
        withContext(Dispatchers.IO) {
            val file = SamplePdfGenerator.generateStressBenchmarkPdf(context, pageCount)
            val existing = documentDao.getByPath(file.absolutePath)
            val entity = inspectAndBuildEntity(
                file = file,
                displayFileName = file.name,
                sourceUri = null,
                collectionTag = "Benchmarks",
                isBuiltIn = true,
            )
            if (existing != null) {
                val updated = entity.copy(id = existing.id, lastOpenedAt = System.currentTimeMillis())
                documentDao.update(updated)
                updated
            } else {
                val id = documentDao.insert(entity)
                entity.copy(id = id)
            }
        }

    /**
     * Imports a user-selected PDF URI (from SAF OpenDocument or external ACTION_VIEW intent)
     * into app-private storage so zero-copy mmap and reliable random-access PdfRenderer work
     * seamlessly even for cloud or non-seekable ContentProviders.
     */
    suspend fun importFromUri(uri: Uri): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(uri) ?: "Document_${System.currentTimeMillis()}.pdf"
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }

            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val destFile = uniqueDestinationFile(docsDir, safeName)

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open PDF stream")
            input.use { src ->
                FileOutputStream(destFile).use { dst ->
                    src.copyTo(dst, bufferSize = 32 * 1024)
                }
            }

            // Verify PDF can actually be opened by native PDFium before indexing
            val entity = inspectAndBuildEntity(
                file = destFile,
                displayFileName = fileName,
                sourceUri = uri.toString(),
                collectionTag = "Imported",
                isBuiltIn = false,
            )
            val id = documentDao.insert(entity)
            entity.copy(id = id)
        }
    }

    private fun inspectAndBuildEntity(
        file: File,
        displayFileName: String,
        sourceUri: String?,
        collectionTag: String,
        isBuiltIn: Boolean,
    ): DocumentEntity {
        val pageCount = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
        val parsedMeta = runCatching {
            FluxPdfBinaryParser.open(file).use { it.metadata }
        }.getOrNull()

        val title = parsedMeta?.title?.takeIf { it.isNotBlank() }
            ?: displayFileName.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ')

        return DocumentEntity(
            title = title,
            author = parsedMeta?.author.orEmpty(),
            fileName = displayFileName,
            localFilePath = file.absolutePath,
            sourceUri = sourceUri,
            fileSizeBytes = file.length(),
            pageCount = pageCount.coerceAtLeast(1),
            lastPageIndex = 0,
            lastOpenedAt = System.currentTimeMillis(),
            addedAt = System.currentTimeMillis(),
            collectionTag = collectionTag,
            pdfVersion = parsedMeta?.pdfVersion ?: "1.7",
            isLinearized = parsedMeta?.isLinearized ?: false,
            isBuiltIn = isBuiltIn,
        )
    }

    suspend fun renderCoverThumbnail(doc: DocumentEntity, widthPx: Int = 260): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(doc.localFilePath)
            if (!file.exists()) return@withContext null
            val thumbDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "doc_${doc.id}_${file.lastModified()}.jpg")
            if (thumbFile.exists()) {
                android.graphics.BitmapFactory.decodeFile(thumbFile.absolutePath)?.let { return@withContext it }
            }
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                            val h = (widthPx * ratio).toInt().coerceIn(120, 480)
                            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.RGB_565)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(thumbFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 84, fos)
                            }
                            bmp
                        }
                    }
                }
            }.getOrNull()
        }

    suspend fun updateReadingPosition(documentId: Long, pageIndex: Int) {
        documentDao.updateReadingPosition(documentId, pageIndex, System.currentTimeMillis())
    }

    suspend fun togglePin(doc: DocumentEntity) {
        documentDao.setPinned(doc.id, !doc.isPinned)
    }

    suspend fun setCollectionTag(documentId: Long, tag: String) {
        documentDao.setCollectionTag(documentId, tag)
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        bitmapCache.evictDocument(doc.id)
        documentDao.deleteById(doc.id)
        if (!doc.isBuiltIn) {
            runCatching { File(doc.localFilePath).delete() }
        }
    }

    suspend fun addBookmark(documentId: Long, pageIndex: Int, label: String, note: String = "") {
        bookmarkDao.insert(
            BookmarkEntity(
                documentId = documentId,
                pageIndex = pageIndex,
                label = label.ifBlank { "Page ${pageIndex + 1}" },
                note = note,
            ),
        )
    }

    suspend fun removeBookmark(bookmarkId: Long) = bookmarkDao.deleteById(bookmarkId)

    suspend fun removeBookmarksOnPage(documentId: Long, pageIndex: Int) =
        bookmarkDao.deleteByPage(documentId, pageIndex)

    suspend fun addAnnotation(annotation: AnnotationEntity): Long = annotationDao.insert(annotation)

    suspend fun updateAnnotationNote(id: Long, noteText: String) =
        annotationDao.updateNoteText(id, noteText)

    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteById(id)

    suspend fun clearAnnotationsOnPage(documentId: Long, pageIndex: Int) =
        annotationDao.deleteAllOnPage(documentId, pageIndex)

    /**
     * Generates a clean Markdown summary of all bookmarks and annotations for sharing.
     */
    suspend fun buildAnnotationsMarkdown(doc: DocumentEntity): String = withContext(Dispatchers.IO) {
        val bookmarks = bookmarkDao.listForDocument(doc.id)
        val annotations = annotationDao.listForDocument(doc.id)
        buildString {
            appendLine("# ${doc.title}")
            if (doc.author.isNotBlank()) appendLine("*Author: ${doc.author}*")
            appendLine("Pages: ${doc.pageCount}  |  Exported from FluxPDF")
            appendLine()
            if (bookmarks.isNotEmpty()) {
                appendLine("## Bookmarks (${bookmarks.size})")
                for (bm in bookmarks) {
                    append("- **Page ${bm.pageIndex + 1}**: ${bm.label}")
                    if (bm.note.isNotBlank()) append(" — ${bm.note}")
                    appendLine()
                }
                appendLine()
            }
            if (annotations.isNotEmpty()) {
                appendLine("## Annotations (${annotations.size})")
                for (ann in annotations) {
                    val kind = ann.annotationType().label
                    val detail = ann.noteText.ifBlank { "Visual $kind mark" }
                    appendLine("- **Page ${ann.pageIndex + 1}** [$kind]: $detail")
                }
            }
            if (bookmarks.isEmpty() && annotations.isEmpty()) {
                appendLine("_No bookmarks or annotations recorded yet._")
            }
        }
    }

    /**
     * Flattens all user annotations directly onto a freshly exported PDF file using Android's
     * native PdfDocument + PdfRenderer so exported annotations are visible in any PDF viewer.
     */
    suspend fun exportFlattenedAnnotatedPdf(doc: DocumentEntity): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(doc.localFilePath)
                val annotationsByPage = annotationDao.listForDocument(doc.id).groupBy { it.pageIndex }
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(
                    exportDir,
                    "${doc.fileName.removeSuffix(".pdf")}_annotated.pdf",
                )

                val outPdf = PdfDocument()
                try {
                    ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val maxPages = renderer.pageCount.coerceAtMost(80)
                            for (i in 0 until maxPages) {
                                renderer.openPage(i).use { srcPage ->
                                    val w = srcPage.width
                                    val h = srcPage.height
                                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                                    val outPage = outPdf.startPage(pageInfo)

                                    val renderW = (w * 1.5f).toInt().coerceIn(600, 1440)
                                    val renderH = (h * 1.5f).toInt().coerceIn(800, 2000)
                                    val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                    Canvas(bmp).drawColor(Color.WHITE)
                                    srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                    val destRect = android.graphics.Rect(0, 0, w, h)
                                    outPage.canvas.drawBitmap(bmp, null, destRect, null)
                                    bmp.recycle()

                                    // Composite normalized annotations onto the PDF canvas
                                    val pageAnns = annotationsByPage[i].orEmpty()
                                    drawAnnotationsOnCanvas(outPage.canvas, w.toFloat(), h.toFloat(), pageAnns)
                                    outPdf.finishPage(outPage)
                                }
                            }
                        }
                    }
                    FileOutputStream(outFile).use { fos -> outPdf.writeTo(fos) }
                } finally {
                    outPdf.close()
                }
                outFile
            }
        }

    private fun drawAnnotationsOnCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        annotations: List<AnnotationEntity>,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (ann in annotations) {
            val pts = ann.decodePoints()
            if (pts.isEmpty()) continue
            val colorInt = ann.colorArgb.toInt()
            when (ann.annotationType()) {
                AnnotationType.HIGHLIGHT -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.color = colorInt
                    paint.strokeWidth = (height * 0.022f).coerceAtLeast(10f)
                    val p0 = pts.first()
                    val p1 = pts.last()
                    canvas.drawLine(p0.x * width, p0.y * height, p1.x * width, p1.y * height, paint)
                }
                AnnotationType.UNDERLINE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = colorInt
                    paint.strokeWidth = (height * 0.004f).coerceAtLeast(2f)
                    val p0 = pts.first()
                    val p1 = pts.last()
                    canvas.drawLine(p0.x * width, p0.y * height, p1.x * width, p1.y * height, paint)
                }
                AnnotationType.INK -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = colorInt
                    paint.strokeWidth = (width * ann.strokeWidthNorm).coerceAtLeast(2.5f)
                    val path = Path()
                    path.moveTo(pts.first().x * width, pts.first().y * height)
                    for (k in 1 until pts.size) {
                        path.lineTo(pts[k].x * width, pts[k].y * height)
                    }
                    canvas.drawPath(path, paint)
                }
                AnnotationType.STICKY_NOTE -> {
                    val pt = pts.first()
                    paint.style = Paint.Style.FILL
                    paint.color = colorInt
                    canvas.drawCircle(pt.x * width, pt.y * height, 9f, paint)
                }
            }
        }
    }

    fun computeThumbnailCacheBytes(): Long {
        val thumbDir = File(context.cacheDir, "thumbnails")
        val exportDir = File(context.cacheDir, "exports")
        return dirSize(thumbDir) + dirSize(exportDir)
    }

    suspend fun clearCaches() = withContext(Dispatchers.IO) {
        bitmapCache.clearAll()
        File(context.cacheDir, "thumbnails").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueDestinationFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.removeSuffix(".pdf")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$counter.pdf")
            counter++
        }
        return candidate
    }
}
