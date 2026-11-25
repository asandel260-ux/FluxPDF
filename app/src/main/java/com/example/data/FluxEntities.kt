package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["localFilePath"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val fileName: String,
    val localFilePath: String,
    val sourceUri: String? = null,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val lastPageIndex: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val collectionTag: String = "General",
    val pdfVersion: String = "1.7",
    val isLinearized: Boolean = false,
    val isBuiltIn: Boolean = false,
) {
    val progressFraction: Float
        get() = if (pageCount <= 1) 1f else ((lastPageIndex + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
}

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val label: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

enum class AnnotationType(val label: String) {
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    INK("Freehand Ink"),
    STICKY_NOTE("Margin Note"),
}

data class NormalizedPoint(val x: Float, val y: Float)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "pageIndex"])],
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val type: String, // AnnotationType.name
    val colorArgb: Long,
    val strokeWidthNorm: Float = 0.006f,
    val pointsSerialized: String, // "x1,y1;x2,y2;..." normalized [0f..1f]
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun annotationType(): AnnotationType =
        AnnotationType.entries.firstOrNull { it.name == type } ?: AnnotationType.HIGHLIGHT

    fun decodePoints(): List<NormalizedPoint> {
        if (pointsSerialized.isBlank()) return emptyList()
        return pointsSerialized.split(';').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) NormalizedPoint(x, y) else null
            } else {
                null
            }
        }
    }

    companion object {
        fun encodePoints(points: List<NormalizedPoint>): String =
            points.joinToString(";") { p ->
                "${"%.4f".format(p.x.coerceIn(0f, 1f))},${"%.4f".format(p.y.coerceIn(0f, 1f))}"
            }
    }
}
