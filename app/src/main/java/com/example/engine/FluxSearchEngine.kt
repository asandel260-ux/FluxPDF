package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.coroutineContext

data class SearchQueryOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegex: Boolean = false,
)

data class SearchHit(
    val pageIndex: Int,
    val matchOffsetInPage: Int,
    val snippet: String,
    val highlightStart: Int,
    val highlightEnd: Int,
)

data class SearchProgress(
    val query: String,
    val scannedPages: Int,
    val totalPages: Int,
    val hits: List<SearchHit>,
    val elapsedMs: Float,
    val isComplete: Boolean,
    val regexError: String? = null,
)

/**
 * Streaming, cancellable full-text PDF search engine.
 * Emits progressive batches so results appear instantaneously even on 500+ page documents.
 */
object FluxSearchEngine {

    fun search(
        session: FluxPdfDocumentSession,
        query: String,
        options: SearchQueryOptions,
        maxHits: Int = 600,
    ): Flow<SearchProgress> = flow {
        val trimmed = query.trim()
        val totalPages = session.pageCount
        if (trimmed.isEmpty() || totalPages == 0) {
            emit(SearchProgress(trimmed, 0, totalPages, emptyList(), 0f, isComplete = true))
            return@flow
        }

        val startNanos = System.nanoTime()
        val compiledRegex: Regex? = if (options.useRegex) {
            try {
                val flags = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(trimmed, flags)
            } catch (e: Exception) {
                emit(
                    SearchProgress(
                        query = trimmed,
                        scannedPages = 0,
                        totalPages = totalPages,
                        hits = emptyList(),
                        elapsedMs = 0f,
                        isComplete = true,
                        regexError = e.message ?: "Invalid regular expression",
                    ),
                )
                return@flow
            }
        } else {
            null
        }

        val needle = if (options.caseSensitive) trimmed else trimmed.lowercase()
        val hits = ArrayList<SearchHit>(64)
        var lastEmitCount = 0

        for (page in 0 until totalPages) {
            coroutineContext.ensureActive()
            val text = session.extractPageText(page)
            if (text.isNotEmpty()) {
                if (compiledRegex != null) {
                    for (m in compiledRegex.findAll(text)) {
                        if (m.range.isEmpty()) continue
                        hits.add(buildHit(page, text, m.range.first, m.range.last + 1))
                        if (hits.size >= maxHits) break
                    }
                } else {
                    val haystack = if (options.caseSensitive) text else text.lowercase()
                    var from = 0
                    while (from < haystack.length && hits.size < maxHits) {
                        val idx = haystack.indexOf(needle, from)
                        if (idx < 0) break
                        val end = idx + needle.length
                        val validWord = if (options.wholeWord) {
                            val leftOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
                            val rightOk = end >= haystack.length || !haystack[end].isLetterOrDigit()
                            leftOk && rightOk
                        } else {
                            true
                        }
                        if (validWord) {
                            hits.add(buildHit(page, text, idx, end))
                        }
                        from = end.coerceAtLeast(from + 1)
                    }
                }
            }

            val shouldEmit = hits.size >= maxHits ||
                (page + 1) % 8 == 0 ||
                (hits.size - lastEmitCount >= 12)
            if (shouldEmit) {
                lastEmitCount = hits.size
                emit(
                    SearchProgress(
                        query = trimmed,
                        scannedPages = page + 1,
                        totalPages = totalPages,
                        hits = hits.toList(),
                        elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f,
                        isComplete = hits.size >= maxHits,
                    ),
                )
                if (hits.size >= maxHits) return@flow
            }
        }

        emit(
            SearchProgress(
                query = trimmed,
                scannedPages = totalPages,
                totalPages = totalPages,
                hits = hits.toList(),
                elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f,
                isComplete = true,
            ),
        )
    }.flowOn(Dispatchers.Default)

    private fun buildHit(pageIndex: Int, text: String, start: Int, end: Int): SearchHit {
        val snipStart = (start - 42).coerceAtLeast(0)
        val snipEnd = (end + 58).coerceAtMost(text.length)
        val prefix = if (snipStart > 0) "…" else ""
        val suffix = if (snipEnd < text.length) "…" else ""
        val rawSnippet = text.substring(snipStart, snipEnd).replace('\n', ' ')
        val snippet = "$prefix$rawSnippet$suffix"
        val hlStart = prefix.length + (start - snipStart)
        val hlEnd = (hlStart + (end - start)).coerceAtMost(snippet.length)
        return SearchHit(
            pageIndex = pageIndex,
            matchOffsetInPage = start,
            snippet = snippet,
            highlightStart = hlStart,
            highlightEnd = hlEnd,
        )
    }
}
