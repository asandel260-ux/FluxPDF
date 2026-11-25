package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY isPinned DESC, lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE localFilePath = :path LIMIT 1")
    suspend fun getByPath(path: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("UPDATE documents SET lastPageIndex = :pageIndex, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateReadingPosition(id: Long, pageIndex: Int, timestamp: Long)

    @Query("UPDATE documents SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE documents SET collectionTag = :tag WHERE id = :id")
    suspend fun setCollectionTag(id: Long, tag: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC, createdAt DESC")
    fun observeForDocument(documentId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun listForDocument(documentId: Long): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun deleteByPage(documentId: Long, pageIndex: Int)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY pageIndex ASC, createdAt ASC")
    fun observeForDocument(documentId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY pageIndex ASC, createdAt ASC")
    suspend fun listForDocument(documentId: Long): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Query("UPDATE annotations SET noteText = :noteText WHERE id = :id")
    suspend fun updateNoteText(id: Long, noteText: String)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM annotations WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun deleteAllOnPage(documentId: Long, pageIndex: Int)
}
