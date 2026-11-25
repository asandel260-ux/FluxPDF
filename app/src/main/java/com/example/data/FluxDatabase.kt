package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FluxDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile
        private var instance: FluxDatabase? = null

        fun getInstance(context: Context): FluxDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FluxDatabase::class.java,
                    "fluxpdf_archive.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
