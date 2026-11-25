package com.example.engine

import android.graphics.Bitmap
import android.util.LruCache
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class PageRenderKey(
    val documentId: Long,
    val pageIndex: Int,
    val widthPx: Int,
    val isPreview: Boolean,
)

data class RenderTelemetrySnapshot(
    val cacheUsedMb: Float,
    val cacheBudgetMb: Float,
    val hitRatePercent: Int,
    val totalRenders: Int,
    val lastRenderMs: Float,
    val p95RenderMs: Float,
    val evictions: Int,
    val activeBitmapCount: Int,
)

/**
 * Memory-bounded LRU page bitmap cache with real-time frame latency telemetry.
 * Prevents GC thrashing and strictly enforces hardware-scaled byte ceilings.
 */
class FluxBitmapCache(initialBudgetBytes: Int) {
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val evictionCount = AtomicInteger(0)
    private val totalRenders = AtomicInteger(0)

    private val latencyLock = Any()
    private val recentRenderMs = FloatArray(64)
    private var latencyIndex = 0
    private var latencyCount = 0
    @Volatile private var lastRenderDurationMs = 0f

    @Volatile private var budgetBytes = initialBudgetBytes

    private val cacheLock = Any()
    private var lru: LruCache<PageRenderKey, Bitmap> = createLru(initialBudgetBytes)

    private fun createLru(maxBytes: Int): LruCache<PageRenderKey, Bitmap> {
        return object : LruCache<PageRenderKey, Bitmap>(maxBytes.coerceAtLeast(4 * 1024 * 1024)) {
            override fun sizeOf(key: PageRenderKey, value: Bitmap): Int {
                return value.allocationByteCount.coerceAtLeast(1)
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: PageRenderKey,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (evicted) {
                    evictionCount.incrementAndGet()
                }
            }
        }
    }

    fun updateBudget(newBudgetBytes: Int) {
        synchronized(cacheLock) {
            budgetBytes = newBudgetBytes
            lru.resize(newBudgetBytes.coerceAtLeast(4 * 1024 * 1024))
        }
    }

    fun get(key: PageRenderKey): Bitmap? {
        val bmp = synchronized(cacheLock) { lru.get(key) }
        if (bmp != null && !bmp.isRecycled) {
            cacheHits.incrementAndGet()
            return bmp
        }
        cacheMisses.incrementAndGet()
        return null
    }

    fun put(key: PageRenderKey, bitmap: Bitmap) {
        synchronized(cacheLock) {
            lru.put(key, bitmap)
        }
    }

    fun recordRenderDuration(durationMs: Float) {
        totalRenders.incrementAndGet()
        lastRenderDurationMs = durationMs
        synchronized(latencyLock) {
            recentRenderMs[latencyIndex] = durationMs
            latencyIndex = (latencyIndex + 1) % recentRenderMs.size
            if (latencyCount < recentRenderMs.size) latencyCount++
        }
    }

    fun evictDocument(documentId: Long) {
        synchronized(cacheLock) {
            val snapshot = lru.snapshot()
            for (key in snapshot.keys) {
                if (key.documentId == documentId) {
                    lru.remove(key)
                }
            }
        }
    }

    fun clearAll() {
        synchronized(cacheLock) {
            lru.evictAll()
        }
    }

    fun snapshotTelemetry(): RenderTelemetrySnapshot {
        val usedBytes: Int
        val count: Int
        synchronized(cacheLock) {
            usedBytes = lru.size()
            count = lru.snapshot().size
        }
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val totalLookups = hits + misses
        val hitRate = if (totalLookups == 0L) 100 else ((hits * 100L) / totalLookups).toInt()

        val p95 = synchronized(latencyLock) {
            if (latencyCount == 0) {
                0f
            } else {
                val sorted = recentRenderMs.copyOf(latencyCount).apply { sort() }
                val idx = ((latencyCount - 1) * 0.95f).toInt().coerceIn(0, latencyCount - 1)
                sorted[idx]
            }
        }

        return RenderTelemetrySnapshot(
            cacheUsedMb = usedBytes / (1024f * 1024f),
            cacheBudgetMb = budgetBytes / (1024f * 1024f),
            hitRatePercent = hitRate,
            totalRenders = totalRenders.get(),
            lastRenderMs = lastRenderDurationMs,
            p95RenderMs = p95,
            evictions = evictionCount.get(),
            activeBitmapCount = count,
        )
    }
}
