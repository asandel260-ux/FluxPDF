package com.example

import android.app.Application
import com.example.data.FluxDatabase
import com.example.data.FluxDocumentRepository
import com.example.data.ReaderPreferencesRepository
import com.example.engine.FluxBitmapCache
import com.example.engine.HardwareProfile

class AppContainer(application: Application) {
    val hardwareProfile: HardwareProfile = HardwareProfile.detect(application)
    val bitmapCache: FluxBitmapCache = FluxBitmapCache(hardwareProfile.cacheBudgetBytes)
    val database: FluxDatabase = FluxDatabase.getInstance(application)
    val preferencesRepository: ReaderPreferencesRepository = ReaderPreferencesRepository(application)
    val documentRepository: FluxDocumentRepository = FluxDocumentRepository(
        context = application,
        documentDao = database.documentDao(),
        bookmarkDao = database.bookmarkDao(),
        annotationDao = database.annotationDao(),
        bitmapCache = bitmapCache,
    )
}

class FluxPdfApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            container.bitmapCache.clearAll()
        }
    }
}
