package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

enum class HardwareTier(val label: String, val tagline: String) {
    CONSTRAINED("Constrained", "Low-RAM safeguard • RGB_565 • Compact cache"),
    BALANCED("Balanced", "Adaptive hybrid • ARGB_8888 • 2-page prefetch"),
    PERFORMANCE("Performance", "Full headroom • High-DPI tiles • 3-page prefetch"),
}

enum class HardwareTierPreference(val label: String) {
    AUTO("Automatic (Hardware Detected)"),
    CONSTRAINED("Memory Saver (Low-RAM)"),
    BALANCED("Balanced"),
    PERFORMANCE("Maximum Crispness"),
}

data class HardwareProfile(
    val detectedTier: HardwareTier,
    val activeTier: HardwareTier,
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val maxHeapMb: Int,
    val cpuCores: Int,
    val cacheBudgetBytes: Int,
    val bitmapConfig: Bitmap.Config,
    val maxPageRenderWidthPx: Int,
    val maxTileRenderWidthPx: Int,
    val prefetchRadius: Int,
    val previewScale: Float,
) {
    val cacheBudgetMb: Float get() = cacheBudgetBytes / (1024f * 1024f)

    companion object {
        fun detect(
            context: Context,
            overridePreference: HardwareTierPreference = HardwareTierPreference.AUTO,
        ): HardwareProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val isLowRam = am?.isLowRamDevice ?: false
            val memClassMb = am?.memoryClass ?: 128
            val maxHeapBytes = Runtime.getRuntime().maxMemory()
            val maxHeapMb = (maxHeapBytes / (1024L * 1024L)).toInt().coerceAtLeast(64)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

            val detected = when {
                isLowRam || maxHeapMb <= 160 || cores <= 2 -> HardwareTier.CONSTRAINED
                maxHeapMb >= 384 && cores >= 6 -> HardwareTier.PERFORMANCE
                else -> HardwareTier.BALANCED
            }

            val active = when (overridePreference) {
                HardwareTierPreference.AUTO -> detected
                HardwareTierPreference.CONSTRAINED -> HardwareTier.CONSTRAINED
                HardwareTierPreference.BALANCED -> HardwareTier.BALANCED
                HardwareTierPreference.PERFORMANCE -> HardwareTier.PERFORMANCE
            }

            // Cap bitmap cache strictly to <= 16% of JVM max heap to prevent OOM on weak devices
            val safeHeapCap = (maxHeapBytes * 0.16).toLong().coerceAtMost(96L * 1024L * 1024L)
            val desiredBudget = when (active) {
                HardwareTier.CONSTRAINED -> 14L * 1024L * 1024L
                HardwareTier.BALANCED -> 36L * 1024L * 1024L
                HardwareTier.PERFORMANCE -> 72L * 1024L * 1024L
            }
            val finalBudget = max(8L * 1024L * 1024L, min(desiredBudget, safeHeapCap)).toInt()

            return when (active) {
                HardwareTier.CONSTRAINED -> HardwareProfile(
                    detectedTier = detected,
                    activeTier = active,
                    isLowRamDevice = isLowRam,
                    memoryClassMb = memClassMb,
                    maxHeapMb = maxHeapMb,
                    cpuCores = cores,
                    cacheBudgetBytes = finalBudget,
                    bitmapConfig = Bitmap.Config.RGB_565,
                    maxPageRenderWidthPx = 1080,
                    maxTileRenderWidthPx = 1280,
                    prefetchRadius = 1,
                    previewScale = 0.30f,
                )
                HardwareTier.BALANCED -> HardwareProfile(
                    detectedTier = detected,
                    activeTier = active,
                    isLowRamDevice = isLowRam,
                    memoryClassMb = memClassMb,
                    maxHeapMb = maxHeapMb,
                    cpuCores = cores,
                    cacheBudgetBytes = finalBudget,
                    bitmapConfig = Bitmap.Config.ARGB_8888,
                    maxPageRenderWidthPx = 1440,
                    maxTileRenderWidthPx = 1600,
                    prefetchRadius = 2,
                    previewScale = 0.35f,
                )
                HardwareTier.PERFORMANCE -> HardwareProfile(
                    detectedTier = detected,
                    activeTier = active,
                    isLowRamDevice = isLowRam,
                    memoryClassMb = memClassMb,
                    maxHeapMb = maxHeapMb,
                    cpuCores = cores,
                    cacheBudgetBytes = finalBudget,
                    bitmapConfig = Bitmap.Config.ARGB_8888,
                    maxPageRenderWidthPx = 1800,
                    maxTileRenderWidthPx = 2048,
                    prefetchRadius = 3,
                    previewScale = 0.38f,
                )
            }
        }
    }
}
