package com.example.engine

/**
 * JNI bridge to the optional compiled Rust native library (`rust/fluxpdf-engine/libfluxpdf_engine.so`).
 *
 * When the Android NDK + cargo-ndk build artifact is bundled under `app/src/main/jniLibs/`,
 * this bridge routes memory-mapped PDF parsing, text extraction, and trigram search directly
 * into Rust. When running in environments without NDK-built `.so` libraries, FluxPDF transparently
 * executes on its zero-copy `MappedByteBuffer` parser + native Android PDFium (`PdfRenderer`) path
 * with identical API contracts.
 */
object FluxNativeBridge {
    val isRustLibraryLoaded: Boolean = try {
        System.loadLibrary("fluxpdf_engine")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    val engineBackendLabel: String
        get() = if (isRustLibraryLoaded) {
            runCatching { nativeEngineVersion() }.getOrDefault("Rust Native JNI + PDFium")
        } else {
            "Native PDFium + Zero-Copy mmap Parser (Rust JNI Ready)"
        }

    @JvmStatic external fun nativeEngineVersion(): String
    @JvmStatic external fun nativeOpenDocument(path: String): Long
    @JvmStatic external fun nativeCloseDocument(handle: Long)
    @JvmStatic external fun nativeGetMetadataJson(handle: Long): String
    @JvmStatic external fun nativeExtractPageText(handle: Long, pageIndex: Int): String
    @JvmStatic external fun nativeSearchJson(
        handle: Long,
        query: String,
        caseSensitive: Int,
        wholeWord: Int,
    ): String
}
