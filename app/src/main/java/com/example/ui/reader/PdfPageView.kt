package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.AnnotationEntity
import com.example.data.AnnotationType
import com.example.data.NormalizedPoint
import com.example.data.ReadingColorMode
import com.example.engine.FluxPdfDocumentSession
import com.example.ui.theme.ReaderCanvasHighContrast
import com.example.ui.theme.ReaderCanvasMidnight
import com.example.ui.theme.ReaderCanvasOled
import com.example.ui.theme.ReaderCanvasPaper
import com.example.ui.theme.ReaderCanvasSepia
import com.example.viewmodel.ActiveAnnotationTool
import kotlin.math.roundToInt

fun readingBackdropColor(mode: ReadingColorMode): Color = when (mode) {
    ReadingColorMode.ORIGINAL -> ReaderCanvasPaper
    ReadingColorMode.SEPIA -> ReaderCanvasSepia
    ReadingColorMode.MIDNIGHT -> ReaderCanvasMidnight
    ReadingColorMode.OLED_DARK -> ReaderCanvasOled
    ReadingColorMode.HIGH_CONTRAST -> ReaderCanvasHighContrast
}

/**
 * Hardware-accelerated GPU ColorMatrix filters for instant zero-CPU optical themes.
 */
fun readingColorFilter(mode: ReadingColorMode): ColorFilter? = when (mode) {
    ReadingColorMode.ORIGINAL -> null
    ReadingColorMode.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.96f, 0.04f, 0.00f, 0f, -4f,
                0.02f, 0.90f, 0.02f, 0f, -8f,
                0.00f, 0.03f, 0.78f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    ReadingColorMode.MIDNIGHT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.84f, 0f, 0f, 0f, 232f,
                0f, -0.85f, 0f, 0f, 236f,
                0f, 0f, -0.82f, 0f, 244f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    ReadingColorMode.OLED_DARK -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.96f, 0f, 0f, 0f, 245f,
                0f, -0.96f, 0f, 0f, 245f,
                0f, 0f, -0.96f, 0f, 245f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    ReadingColorMode.HIGH_CONTRAST -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1.35f, 0f, 0f, 0f, 305f,
                0f, -1.35f, 0f, 0f, 305f,
                0f, 0f, -1.20f, 0f, 275f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

@Composable
fun PdfPageSurface(
    session: FluxPdfDocumentSession,
    pageIndex: Int,
    readingColorMode: ReadingColorMode,
    autoCropMargins: Boolean,
    annotations: List<AnnotationEntity>,
    activeTool: ActiveAnnotationTool,
    selectedInkColorArgb: Long,
    onTapPage: () -> Unit,
    onDoubleTapPage: () -> Unit,
    onCommitStroke: (AnnotationType, List<NormalizedPoint>) -> Unit,
    onRequestStickyNoteAt: (NormalizedPoint) -> Unit,
    onTapStickyNote: (AnnotationEntity) -> Unit,
    modifier: Modifier = Modifier,
    zoomBoostScale: Float = 1f,
) {
    val pageDim = remember(session.documentId, pageIndex) { session.getPageDimension(pageIndex) }
    var cropRect by remember(session.documentId, pageIndex, autoCropMargins) {
        mutableStateOf<RectF?>(null)
    }
    var pageBitmap by remember(session.documentId, pageIndex) { mutableStateOf<Bitmap?>(null) }

    val effectiveAspect = remember(pageDim, cropRect) {
        val c = cropRect
        if (c != null && c.height() > 0.1f) {
            (pageDim.widthPt * c.width()) / (pageDim.heightPt * c.height())
        } else {
            pageDim.aspectRatio
        }.coerceIn(0.45f, 2.0f)
    }

    val draftPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(effectiveAspect)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(if (readingColorMode == ReadingColorMode.ORIGINAL) Color.White else Color(0xFF1A1C22)),
    ) {
        val widthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(320) }
        val renderWidth = (widthPx * zoomBoostScale.coerceIn(1f, 2.2f)).roundToInt()

        // Progressive 2-pass native render: fast preview -> crisp full-DPI pass
        LaunchedEffect(session.documentId, pageIndex, renderWidth, autoCropMargins) {
            val resolvedCrop = if (autoCropMargins) {
                session.detectContentBounds(pageIndex).takeIf { it.width() < 0.94f || it.height() < 0.94f }
            } else {
                null
            }
            cropRect = resolvedCrop

            // Pass 1: Fast preview if we don't already have a bitmap
            if (pageBitmap == null) {
                val previewW = (widthPx * session.hardwareProfile.previewScale).roundToInt().coerceAtLeast(220)
                session.renderPage(
                    pageIndex = pageIndex,
                    requestedWidthPx = previewW,
                    isPreview = true,
                    cropNormalizedRect = resolvedCrop,
                )?.let { pageBitmap = it }
            }

            // Pass 2: Full crisp display-DPI pass
            session.renderPage(
                pageIndex = pageIndex,
                requestedWidthPx = renderWidth,
                isPreview = false,
                cropNormalizedRect = resolvedCrop,
            )?.let { pageBitmap = it }
        }

        val bmp = pageBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = readingColorFilter(readingColorMode),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
        }

        // Vector Annotation Layer (persisted + live in-progress stroke)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, pageIndex) {
                    if (activeTool == ActiveAnnotationTool.NONE) {
                        detectTapGestures(
                            onTap = { onTapPage() },
                            onDoubleTap = { onDoubleTapPage() },
                        )
                    } else if (activeTool == ActiveAnnotationTool.STICKY_NOTE) {
                        detectTapGestures(
                            onTap = { offset ->
                                val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.03f, 0.97f)
                                val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.03f, 0.97f)
                                onRequestStickyNoteAt(NormalizedPoint(nx, ny))
                            },
                        )
                    }
                }
                .pointerInput(activeTool, pageIndex, selectedInkColorArgb) {
                    if (activeTool == ActiveAnnotationTool.HIGHLIGHT ||
                        activeTool == ActiveAnnotationTool.UNDERLINE ||
                        activeTool == ActiveAnnotationTool.INK
                    ) {
                        detectDragGestures(
                            onDragStart = { start ->
                                draftPoints.clear()
                                draftPoints.add(
                                    NormalizedPoint(
                                        (start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                        (start.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    ),
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pt = NormalizedPoint(
                                    (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                )
                                if (activeTool == ActiveAnnotationTool.INK) {
                                    draftPoints.add(pt)
                                } else {
                                    // Keep highlight / underline crisp horizontal bands
                                    val anchorY = draftPoints.firstOrNull()?.y ?: pt.y
                                    if (draftPoints.size == 1) {
                                        draftPoints.add(NormalizedPoint(pt.x, anchorY))
                                    } else if (draftPoints.size >= 2) {
                                        draftPoints[1] = NormalizedPoint(pt.x, anchorY)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (draftPoints.size >= 2) {
                                    val type = when (activeTool) {
                                        ActiveAnnotationTool.HIGHLIGHT -> AnnotationType.HIGHLIGHT
                                        ActiveAnnotationTool.UNDERLINE -> AnnotationType.UNDERLINE
                                        else -> AnnotationType.INK
                                    }
                                    onCommitStroke(type, draftPoints.toList())
                                }
                                draftPoints.clear()
                            },
                            onDragCancel = { draftPoints.clear() },
                        )
                    }
                },
        ) {
            val w = size.width
            val h = size.height

            // Draw saved stroke annotations
            for (ann in annotations) {
                val pts = ann.decodePoints()
                if (pts.size < 2) continue
                val color = Color(ann.colorArgb.toInt())
                when (ann.annotationType()) {
                    AnnotationType.HIGHLIGHT -> {
                        drawLine(
                            color = color,
                            start = Offset(pts.first().x * w, pts.first().y * h),
                            end = Offset(pts.last().x * w, pts.last().y * h),
                            strokeWidth = (h * 0.024f).coerceAtLeast(14f),
                            cap = StrokeCap.Square,
                        )
                    }
                    AnnotationType.UNDERLINE -> {
                        drawLine(
                            color = color,
                            start = Offset(pts.first().x * w, pts.first().y * h),
                            end = Offset(pts.last().x * w, pts.last().y * h),
                            strokeWidth = (h * 0.0045f).coerceAtLeast(3f),
                            cap = StrokeCap.Round,
                        )
                    }
                    AnnotationType.INK -> {
                        val path = Path().apply {
                            moveTo(pts.first().x * w, pts.first().y * h)
                            for (i in 1 until pts.size) {
                                lineTo(pts[i].x * w, pts[i].y * h)
                            }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = (w * ann.strokeWidthNorm).coerceAtLeast(3f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                    AnnotationType.STICKY_NOTE -> Unit
                }
            }

            // Draw live draft stroke
            if (draftPoints.size >= 2) {
                val liveColor = Color(selectedInkColorArgb.toInt())
                when (activeTool) {
                    ActiveAnnotationTool.HIGHLIGHT -> drawLine(
                        color = liveColor,
                        start = Offset(draftPoints.first().x * w, draftPoints.first().y * h),
                        end = Offset(draftPoints.last().x * w, draftPoints.last().y * h),
                        strokeWidth = (h * 0.024f).coerceAtLeast(14f),
                        cap = StrokeCap.Square,
                    )
                    ActiveAnnotationTool.UNDERLINE -> drawLine(
                        color = liveColor,
                        start = Offset(draftPoints.first().x * w, draftPoints.first().y * h),
                        end = Offset(draftPoints.last().x * w, draftPoints.last().y * h),
                        strokeWidth = (h * 0.0045f).coerceAtLeast(3f),
                        cap = StrokeCap.Round,
                    )
                    ActiveAnnotationTool.INK -> {
                        val path = Path().apply {
                            moveTo(draftPoints.first().x * w, draftPoints.first().y * h)
                            for (i in 1 until draftPoints.size) {
                                lineTo(draftPoints[i].x * w, draftPoints[i].y * h)
                            }
                        }
                        drawPath(
                            path = path,
                            color = liveColor,
                            style = Stroke(width = (w * 0.006f).coerceAtLeast(3.5f), cap = StrokeCap.Round),
                        )
                    }
                    else -> Unit
                }
            }
        }

        // Interactive Sticky Margin Note Pins
        val stickyNotes = remember(annotations) {
            annotations.filter { it.annotationType() == AnnotationType.STICKY_NOTE }
        }
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        for (note in stickyNotes) {
            val pt = note.decodePoints().firstOrNull() ?: continue
            val pinHalf = with(density) { 16.dp.toPx() }
            Surface(
                shape = CircleShape,
                color = Color(note.colorArgb.toInt()).copy(alpha = 0.95f),
                border = BorderStroke(1.5.dp, Color.White),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (pt.x * boxW - pinHalf).roundToInt().coerceIn(0, (boxW - pinHalf * 2).roundToInt().coerceAtLeast(0)),
                            y = (pt.y * boxH - pinHalf).roundToInt().coerceIn(0, (boxH - pinHalf * 2).roundToInt().coerceAtLeast(0)),
                        )
                    }
                    .size(32.dp)
                    .clickable { onTapStickyNote(note) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = "Margin note: ${note.noteText}",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
