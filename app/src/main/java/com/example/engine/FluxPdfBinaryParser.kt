package com.example.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import kotlin.math.min

data class PdfOutlineNode(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
)

data class PdfStructureMetadata(
    val pdfVersion: String,
    val isLinearized: Boolean,
    val isEncrypted: Boolean,
    val xrefType: String,
    val objectCount: Int,
    val fileSizeBytes: Long,
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val creator: String,
    val producer: String,
    val creationDate: String,
    val outlines: List<PdfOutlineNode>,
    val parseTimeMs: Float,
)

/**
 * High-performance zero-copy binary PDF 1.0–2.0 structural parser backed by `MappedByteBuffer` (`mmap`).
 *
 * Parses:
 * - Header version, linearization, encryption, and trailer metadata (`/Info`)
 * - Classic `xref` tables (including `/Prev` incremental update chains)
 * - PDF 1.5+ cross-reference streams (`/Type /XRef`) with PNG predictor decoding
 * - Compressed Object Streams (`/Type /ObjStm`)
 * - Page tree (`/Pages` -> `/Kids`) mapping object IDs to 0-based page indices
 * - Hierarchical Table of Contents (`/Outlines`) with destination resolution
 * - Per-page content stream decompression (`/FlateDecode`, `/ASCIIHexDecode`) + `/ToUnicode` CMap
 *   decoding + PDF text operator extraction (`BT`/`ET`, `Td`, `Tm`, `T*`, `Tj`, `TJ`)
 */
class FluxPdfBinaryParser private constructor(
    private val raf: RandomAccessFile,
    private val buffer: MappedByteBuffer,
    private val fileLength: Int,
) : AutoCloseable {

    private sealed interface XrefEntry {
        data class Uncompressed(val offset: Int) : XrefEntry
        data class Compressed(val objStmId: Int, val indexInStm: Int) : XrefEntry
    }

    private val xrefMap = HashMap<Int, XrefEntry>(512)
    private val objStmCache = HashMap<Int, Map<Int, ByteArray>>()
    private val pageObjectIds = ArrayList<Int>()
    private val pageIdToIndex = HashMap<Int, Int>()
    private val pageTextCache = HashMap<Int, String>()

    private var rootObjId: Int = -1
    private var infoObjId: Int = -1
    private var isEncrypted: Boolean = false
    private var xrefType: String = "Classic XRef"

    lateinit var metadata: PdfStructureMetadata
        private set

    companion object {
        fun open(file: File): FluxPdfBinaryParser {
            val startNanos = System.nanoTime()
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val length = channel.size().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, length.toLong())
            val parser = FluxPdfBinaryParser(raf, mapped, length)
            parser.parseStructure(file.length(), startNanos)
            return parser
        }
    }

    private fun parseStructure(fileSizeBytes: Long, startNanos: Long) {
        val header = readAscii(0, min(64, fileLength))
        val version = Regex("%PDF-([0-9]+\\.[0-9]+)").find(header)?.groupValues?.getOrNull(1) ?: "1.7"
        val headChunk = readAscii(0, min(2048, fileLength))
        val isLinearized = headChunk.contains("/Linearized")

        val startXref = findStartXref()
        if (startXref in 0 until fileLength) {
            parseXrefChain(startXref, depth = 0)
        }
        if (xrefMap.isEmpty()) {
            // Resilient linear fallback for damaged or non-standard PDFs
            xrefType = "Recovered Linear Scan"
            recoverObjectsLinearly()
        }

        // Resolve Catalog (/Root) -> /Pages and /Outlines
        val rootBody = if (rootObjId > 0) readObjectAsString(rootObjId) else findCatalogFallback()
        if (rootBody != null) {
            val pagesRef = extractRef(rootBody, "/Pages")
            if (pagesRef > 0) {
                traversePagesTree(pagesRef, visited = HashSet())
            }
        }
        if (pageObjectIds.isEmpty()) {
            discoverPagesFallback()
        }
        pageObjectIds.forEachIndexed { idx, objId -> pageIdToIndex[objId] = idx }

        val infoBody = if (infoObjId > 0) readObjectAsString(infoObjId) else ""
        val outlines = if (rootBody != null) {
            val outlinesRef = extractRef(rootBody, "/Outlines")
            if (outlinesRef > 0) parseOutlines(outlinesRef) else emptyList()
        } else {
            emptyList()
        }

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f
        metadata = PdfStructureMetadata(
            pdfVersion = version,
            isLinearized = isLinearized,
            isEncrypted = isEncrypted,
            xrefType = xrefType,
            objectCount = xrefMap.size,
            fileSizeBytes = fileSizeBytes,
            title = extractPdfStringValue(infoBody ?: "", "/Title"),
            author = extractPdfStringValue(infoBody ?: "", "/Author"),
            subject = extractPdfStringValue(infoBody ?: "", "/Subject"),
            keywords = extractPdfStringValue(infoBody ?: "", "/Keywords"),
            creator = extractPdfStringValue(infoBody ?: "", "/Creator"),
            producer = extractPdfStringValue(infoBody ?: "", "/Producer"),
            creationDate = formatPdfDate(extractPdfStringValue(infoBody ?: "", "/CreationDate")),
            outlines = outlines,
            parseTimeMs = elapsedMs,
        )
    }

    @Synchronized
    fun extractPageText(pageIndex: Int): String {
        pageTextCache[pageIndex]?.let { return it }
        if (pageIndex !in pageObjectIds.indices) return ""

        val pageObjId = pageObjectIds[pageIndex]
        val pageBody = readObjectAsString(pageObjId) ?: return ""
        val cmap = extractPageToUnicodeMap(pageBody)

        val contentRefs = extractContentRefs(pageBody)
        val sb = StringBuilder(1024)
        for (refId in contentRefs) {
            val rawStream = readObjectStreamBytes(refId) ?: continue
            extractTextFromContentStream(rawStream, cmap, sb)
            sb.append('\n')
        }

        val cleaned = sb.toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" ?\\n ?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        pageTextCache[pageIndex] = cleaned
        return cleaned
    }

    fun getParsedPageCount(): Int = pageObjectIds.size

    private fun findStartXref(): Int {
        val tailLen = min(8192, fileLength)
        val tail = readAscii(fileLength - tailLen, tailLen)
        val idx = tail.lastIndexOf("startxref")
        if (idx < 0) return -1
        val after = tail.substring(idx + 9)
        return Regex("\\d+").find(after)?.value?.toIntOrNull() ?: -1
    }

    private fun parseXrefChain(offset: Int, depth: Int) {
        if (depth > 8 || offset < 0 || offset >= fileLength) return
        val probe = readAscii(offset, min(32, fileLength - offset)).trimStart()
        if (probe.startsWith("xref")) {
            val prevOffset = parseClassicXrefTable(offset)
            if (prevOffset > 0) parseXrefChain(prevOffset, depth + 1)
        } else {
            xrefType = "Compressed XRef Stream (1.5+)"
            val prevOffset = parseXrefStream(offset)
            if (prevOffset > 0) parseXrefChain(prevOffset, depth + 1)
        }
    }

    private fun parseClassicXrefTable(offset: Int): Int {
        val windowLen = min(512 * 1024, fileLength - offset)
        val text = readAscii(offset, windowLen)
        val trailerIdx = text.indexOf("trailer")
        val tableSection = if (trailerIdx > 0) text.substring(0, trailerIdx) else text

        val lines = tableSection.lineSequence().iterator()
        if (lines.hasNext()) lines.next() // skip "xref"
        while (lines.hasNext()) {
            val headerLine = lines.next().trim()
            if (headerLine.isEmpty()) continue
            val headerParts = headerLine.split(Regex("\\s+"))
            if (headerParts.size == 2) {
                val startId = headerParts[0].toIntOrNull() ?: break
                val count = headerParts[1].toIntOrNull() ?: break
                for (i in 0 until count) {
                    if (!lines.hasNext()) break
                    val entry = lines.next().trim()
                    val parts = entry.split(Regex("\\s+"))
                    if (parts.size >= 3 && parts[2] == "n") {
                        val objOff = parts[0].toIntOrNull()
                        if (objOff != null && objOff > 0) {
                            xrefMap.putIfAbsent(startId + i, XrefEntry.Uncompressed(objOff))
                        }
                    }
                }
            }
        }

        if (trailerIdx > 0) {
            val trailerDict = text.substring(trailerIdx, min(text.length, trailerIdx + 4096))
            if (rootObjId < 0) rootObjId = extractRef(trailerDict, "/Root")
            if (infoObjId < 0) infoObjId = extractRef(trailerDict, "/Info")
            if (trailerDict.contains("/Encrypt")) isEncrypted = true
            val xrefStm = extractInt(trailerDict, "/XRefStm")
            if (xrefStm > 0) {
                xrefType = "Hybrid XRef + Stream"
                parseXrefStream(xrefStm)
            }
            return extractInt(trailerDict, "/Prev")
        }
        return -1
    }

    private fun parseXrefStream(offset: Int): Int {
        val rawObj = readRawObjectBytesAt(offset) ?: return -1
        val dictStr = extractDictHeader(rawObj)
        if (rootObjId < 0) rootObjId = extractRef(dictStr, "/Root")
        if (infoObjId < 0) infoObjId = extractRef(dictStr, "/Info")
        if (dictStr.contains("/Encrypt")) isEncrypted = true

        val wMatch = Regex("/W\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]").find(dictStr)
        val w1 = wMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val w2 = wMatch?.groupValues?.get(2)?.toIntOrNull() ?: 4
        val w3 = wMatch?.groupValues?.get(3)?.toIntOrNull() ?: 1
        val rowWidth = w1 + w2 + w3
        if (rowWidth <= 0) return extractInt(dictStr, "/Prev")

        val size = extractInt(dictStr, "/Size").coerceAtLeast(0)
        val indexRanges = ArrayList<Pair<Int, Int>>()
        val indexMatch = Regex("/Index\\s*\\[([^]]+)]").find(dictStr)
        if (indexMatch != null) {
            val nums = Regex("\\d+").findAll(indexMatch.groupValues[1])
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
            var i = 0
            while (i + 1 < nums.size) {
                indexRanges.add(nums[i] to nums[i + 1])
                i += 2
            }
        } else if (size > 0) {
            indexRanges.add(0 to size)
        }

        val streamBytes = decodeStreamFromRawObject(rawObj, dictStr) ?: return extractInt(dictStr, "/Prev")
        val predictor = extractInt(dictStr, "/Predictor")
        val columns = extractInt(dictStr, "/Columns").let { if (it > 0) it else rowWidth }
        val unpacked = if (predictor >= 10) unfilterPngPredictor(streamBytes, columns) else streamBytes

        var pos = 0
        for ((startObj, count) in indexRanges) {
            for (i in 0 until count) {
                if (pos + rowWidth > unpacked.size) break
                val type = if (w1 == 0) 1 else readBigEndianInt(unpacked, pos, w1)
                val f2 = readBigEndianInt(unpacked, pos + w1, w2)
                val f3 = readBigEndianInt(unpacked, pos + w1 + w2, w3)
                pos += rowWidth

                val objId = startObj + i
                when (type) {
                    1 -> if (f2 > 0) xrefMap.putIfAbsent(objId, XrefEntry.Uncompressed(f2))
                    2 -> xrefMap.putIfAbsent(objId, XrefEntry.Compressed(f2, f3))
                }
            }
        }

        return extractInt(dictStr, "/Prev")
    }

    private fun unfilterPngPredictor(data: ByteArray, columns: Int): ByteArray {
        val stride = columns + 1
        if (stride <= 1 || data.size < stride) return data
        val rows = data.size / stride
        val out = ByteArray(rows * columns)
        val prevRow = ByteArray(columns)
        val currRow = ByteArray(columns)

        for (r in 0 until rows) {
            val filter = data[r * stride].toInt() and 0xFF
            val srcBase = r * stride + 1
            for (c in 0 until columns) {
                val raw = data[srcBase + c].toInt() and 0xFF
                val left = if (c > 0) currRow[c - 1].toInt() and 0xFF else 0
                val up = prevRow[c].toInt() and 0xFF
                val upLeft = if (c > 0) prevRow[c - 1].toInt() and 0xFF else 0
                val value = when (filter) {
                    0 -> raw
                    1 -> (raw + left) and 0xFF
                    2 -> (raw + up) and 0xFF
                    3 -> (raw + ((left + up) ushr 1)) and 0xFF
                    4 -> {
                        val p = left + up - upLeft
                        val pa = kotlin.math.abs(p - left)
                        val pb = kotlin.math.abs(p - up)
                        val pc = kotlin.math.abs(p - upLeft)
                        val pr = if (pa <= pb && pa <= pc) left else if (pb <= pc) up else upLeft
                        (raw + pr) and 0xFF
                    }
                    else -> raw
                }
                currRow[c] = value.toByte()
            }
            System.arraycopy(currRow, 0, out, r * columns, columns)
            System.arraycopy(currRow, 0, prevRow, 0, columns)
        }
        return out
    }

    private fun recoverObjectsLinearly() {
        val chunkSize = min(fileLength, 2 * 1024 * 1024)
        val text = readAscii(0, chunkSize)
        val objRegex = Regex("(?m)^(\\d+)\\s+0\\s+obj\\b")
        for (match in objRegex.findAll(text)) {
            val id = match.groupValues[1].toIntOrNull() ?: continue
            xrefMap.putIfAbsent(id, XrefEntry.Uncompressed(match.range.first))
        }
    }

    private fun findCatalogFallback(): String? {
        for (objId in xrefMap.keys) {
            val s = readObjectAsString(objId) ?: continue
            if (s.contains("/Type") && Regex("/Type\\s*/Catalog\\b").containsMatchIn(s)) {
                rootObjId = objId
                return s
            }
        }
        return null
    }

    private fun traversePagesTree(nodeId: Int, visited: MutableSet<Int>) {
        if (!visited.add(nodeId)) return
        val body = readObjectAsString(nodeId) ?: return
        val kidsMatch = Regex("/Kids\\s*\\[([^]]+)]", RegexOption.DOT_MATCHES_ALL).find(body)
        if (kidsMatch != null) {
            val refMatches = Regex("(\\d+)\\s+\\d+\\s+R").findAll(kidsMatch.groupValues[1])
            for (m in refMatches) {
                val kidId = m.groupValues[1].toIntOrNull() ?: continue
                traversePagesTree(kidId, visited)
            }
        } else if (Regex("/Type\\s*/Page\\b").containsMatchIn(body) &&
            !Regex("/Type\\s*/Pages\\b").containsMatchIn(body)
        ) {
            pageObjectIds.add(nodeId)
        }
    }

    private fun discoverPagesFallback() {
        val sortedIds = xrefMap.keys.sorted()
        for (id in sortedIds) {
            val body = readObjectAsString(id) ?: continue
            if (Regex("/Type\\s*/Page\\b").containsMatchIn(body) &&
                !Regex("/Type\\s*/Pages\\b").containsMatchIn(body)
            ) {
                pageObjectIds.add(id)
            }
        }
    }

    private fun parseOutlines(outlinesObjId: Int): List<PdfOutlineNode> {
        val root = readObjectAsString(outlinesObjId) ?: return emptyList()
        val firstRef = extractRef(root, "/First")
        if (firstRef <= 0) return emptyList()
        val nodes = ArrayList<PdfOutlineNode>()
        val visited = HashSet<Int>()
        walkOutlineSiblings(firstRef, depth = 0, out = nodes, visited = visited)
        return nodes
    }

    private fun walkOutlineSiblings(
        startId: Int,
        depth: Int,
        out: MutableList<PdfOutlineNode>,
        visited: MutableSet<Int>,
    ) {
        if (depth > 6 || out.size > 400) return
        var current = startId
        while (current > 0 && visited.add(current) && out.size < 400) {
            val body = readObjectAsString(current) ?: break
            val title = extractPdfStringValue(body, "/Title").trim()
            if (title.isNotEmpty()) {
                val pageIdx = resolveOutlinePageIndex(body)
                out.add(PdfOutlineNode(title = title, pageIndex = pageIdx, depth = depth))
            }
            val childRef = extractRef(body, "/First")
            if (childRef > 0) {
                walkOutlineSiblings(childRef, depth + 1, out, visited)
            }
            current = extractRef(body, "/Next")
        }
    }

    private fun resolveOutlinePageIndex(outlineDict: String): Int {
        // Direct /Dest [pageObj 0 R ...]
        val destArray = Regex("/Dest\\s*\\[\\s*(\\d+)\\s+\\d+\\s+R").find(outlineDict)
        if (destArray != null) {
            val objId = destArray.groupValues[1].toIntOrNull()
            if (objId != null) return pageIdToIndex[objId] ?: 0
        }
        // /A << /S /GoTo /D [pageObj 0 R ...] >> inline or indirect
        val inlineActionDest = Regex("/D\\s*\\[\\s*(\\d+)\\s+\\d+\\s+R").find(outlineDict)
        if (inlineActionDest != null) {
            val objId = inlineActionDest.groupValues[1].toIntOrNull()
            if (objId != null) return pageIdToIndex[objId] ?: 0
        }
        val actionRef = extractRef(outlineDict, "/A")
        if (actionRef > 0) {
            val actionBody = readObjectAsString(actionRef) ?: ""
            val actionDest = Regex("/D\\s*\\[\\s*(\\d+)\\s+\\d+\\s+R").find(actionBody)
            if (actionDest != null) {
                val objId = actionDest.groupValues[1].toIntOrNull()
                if (objId != null) return pageIdToIndex[objId] ?: 0
            }
        }
        return 0
    }

    private fun extractContentRefs(pageBody: String): List<Int> {
        val refs = ArrayList<Int>()
        val arrayMatch = Regex("/Contents\\s*\\[([^]]+)]", RegexOption.DOT_MATCHES_ALL).find(pageBody)
        if (arrayMatch != null) {
            for (m in Regex("(\\d+)\\s+\\d+\\s+R").findAll(arrayMatch.groupValues[1])) {
                m.groupValues[1].toIntOrNull()?.let { refs.add(it) }
            }
            return refs
        }
        val singleRef = extractRef(pageBody, "/Contents")
        if (singleRef > 0) refs.add(singleRef)
        return refs
    }

    private fun extractPageToUnicodeMap(pageBody: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        val resourcesBody = run {
            val resRef = extractRef(pageBody, "/Resources")
            if (resRef > 0) readObjectAsString(resRef) ?: pageBody else pageBody
        }
        val fontDictBody = run {
            val fontRef = extractRef(resourcesBody, "/Font")
            if (fontRef > 0) readObjectAsString(fontRef) ?: resourcesBody else resourcesBody
        }
        val fontRefs = Regex("/F\\w*\\s+(\\d+)\\s+\\d+\\s+R").findAll(fontDictBody)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct()
            .take(16)

        for (fRef in fontRefs) {
            val fontObj = readObjectAsString(fRef) ?: continue
            val toUniRef = extractRef(fontObj, "/ToUnicode")
            if (toUniRef > 0) {
                val cmapBytes = readObjectStreamBytes(toUniRef) ?: continue
                parseToUnicodeCMap(String(cmapBytes, StandardCharsets.ISO_8859_1), map)
            }
        }
        return map
    }

    private fun parseToUnicodeCMap(cmap: String, out: MutableMap<Int, String>) {
        val bfCharBlocks = Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(cmap)
        for (block in bfCharBlocks) {
            val pairs = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").findAll(block.groupValues[1])
            for (p in pairs) {
                val src = p.groupValues[1].toIntOrNull(16) ?: continue
                val dst = decodeHexToUnicode(p.groupValues[2])
                if (dst.isNotEmpty()) out[src] = dst
            }
        }
        val bfRangeBlocks = Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(cmap)
        for (block in bfRangeBlocks) {
            val triples = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
                .findAll(block.groupValues[1])
            for (t in triples) {
                val start = t.groupValues[1].toIntOrNull(16) ?: continue
                val end = t.groupValues[2].toIntOrNull(16) ?: continue
                val baseDst = t.groupValues[3].toIntOrNull(16) ?: continue
                val span = (end - start).coerceAtMost(512)
                for (i in 0..span) {
                    out[start + i] = (baseDst + i).toChar().toString()
                }
            }
        }
    }

    private fun decodeHexToUnicode(hex: String): String {
        val clean = if (hex.length % 2 != 0) "${hex}0" else hex
        val bytes = ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return if (bytes.size >= 2 && bytes.size % 2 == 0) {
            String(bytes, StandardCharsets.UTF_16BE)
        } else {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }

    private fun extractTextFromContentStream(
        stream: ByteArray,
        cmap: Map<Int, String>,
        out: StringBuilder,
    ) {
        val len = stream.size
        var i = 0
        var inTextBlock = false
        var lastLineY = Float.NaN

        while (i < len) {
            val b = stream[i].toInt() and 0xFF
            // Skip PDF comments
            if (b == '%'.code) {
                while (i < len && stream[i] != '\n'.code.toByte() && stream[i] != '\r'.code.toByte()) i++
                continue
            }

            // Detect BT / ET markers
            if (b == 'B'.code && i + 1 < len && stream[i + 1] == 'T'.code.toByte() &&
                isDelimiterOrSpace(stream, i - 1) && isDelimiterOrSpace(stream, i + 2)
            ) {
                inTextBlock = true
                i += 2
                continue
            }
            if (b == 'E'.code && i + 1 < len && stream[i + 1] == 'T'.code.toByte() &&
                isDelimiterOrSpace(stream, i - 1) && isDelimiterOrSpace(stream, i + 2)
            ) {
                inTextBlock = false
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                i += 2
                continue
            }

            if (!inTextBlock) {
                i++
                continue
            }

            // Line break operators: T*, Td, TD, Tm
            if (b == 'T'.code && i + 1 < len) {
                val next = stream[i + 1].toInt() and 0xFF
                if ((next == '*'.code || next == 'd'.code || next == 'D'.code) &&
                    isDelimiterOrSpace(stream, i + 2)
                ) {
                    if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                    i += 2
                    continue
                }
                if (next == 'm'.code && isDelimiterOrSpace(stream, i + 2)) {
                    // Check vertical delta from Tm matrix if available
                    val prevSlice = String(
                        stream,
                        (i - 48).coerceAtLeast(0),
                        (i - (i - 48).coerceAtLeast(0)),
                        StandardCharsets.ISO_8859_1,
                    )
                    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(prevSlice).map { it.value.toFloat() }.toList()
                    if (nums.size >= 6) {
                        val y = nums[nums.size - 1]
                        if (!lastLineY.isNaN() && kotlin.math.abs(y - lastLineY) > 4f) {
                            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                        }
                        lastLineY = y
                    }
                    i += 2
                    continue
                }
            }

            // Literal string: (...)
            if (b == '('.code) {
                i++
                var depth = 1
                while (i < len && depth > 0) {
                    val ch = stream[i].toInt() and 0xFF
                    if (ch == '\\'.code && i + 1 < len) {
                        i++
                        when (val esc = stream[i].toInt() and 0xFF) {
                            'n'.code -> out.append('\n')
                            'r'.code -> out.append('\r')
                            't'.code -> out.append('\t')
                            'b'.code -> out.append('\b')
                            'f'.code -> out.append(' ')
                            '('.code, ')'.code, '\\'.code -> out.append(esc.toChar())
                            in '0'.code..'7'.code -> {
                                var oct = esc - '0'.code
                                var digits = 1
                                while (digits < 3 && i + 1 < len) {
                                    val d = stream[i + 1].toInt() and 0xFF
                                    if (d in '0'.code..'7'.code) {
                                        oct = oct * 8 + (d - '0'.code)
                                        i++
                                        digits++
                                    } else {
                                        break
                                    }
                                }
                                out.append(cmap[oct] ?: decodeWinAnsiByte(oct))
                            }
                            else -> out.append(esc.toChar())
                        }
                    } else if (ch == '('.code) {
                        depth++
                        out.append('(')
                    } else if (ch == ')'.code) {
                        depth--
                        if (depth > 0) out.append(')')
                    } else {
                        out.append(cmap[ch] ?: decodeWinAnsiByte(ch))
                    }
                    i++
                }
                continue
            }

            // Hex string: <...> (but not dictionary <<)
            if (b == '<'.code && (i + 1 >= len || stream[i + 1] != '<'.code.toByte())) {
                i++
                val hexBuf = StringBuilder()
                while (i < len && stream[i] != '>'.code.toByte()) {
                    val hc = stream[i].toInt().toChar()
                    if (hc in '0'..'9' || hc in 'a'..'f' || hc in 'A'..'F') hexBuf.append(hc)
                    i++
                }
                if (i < len) i++
                decodeHexStringInto(hexBuf.toString(), cmap, out)
                continue
            }

            // TJ kerning array spacing hint: negative numbers >= 180 usually mean a word space
            if (b == '-'.code && i + 1 < len && stream[i + 1].toInt() in '1'.code..'9'.code) {
                var j = i + 1
                var value = 0
                while (j < len && stream[j].toInt() in '0'.code..'9'.code) {
                    value = value * 10 + (stream[j].toInt() - '0'.code)
                    j++
                }
                if (value >= 180 && out.isNotEmpty() && !out.last().isWhitespace()) {
                    out.append(' ')
                }
                i = j
                continue
            }

            i++
        }
    }

    private fun decodeHexStringInto(hex: String, cmap: Map<Int, String>, out: StringBuilder) {
        if (hex.isEmpty()) return
        val clean = if (hex.length % 2 != 0) "${hex}0" else hex
        // Try 2-byte CID lookup if cmap uses >255 keys, else 1-byte
        val useTwoByte = cmap.keys.any { it > 0xFF } && clean.length % 4 == 0
        if (useTwoByte) {
            var idx = 0
            while (idx + 4 <= clean.length) {
                val code = clean.substring(idx, idx + 4).toInt(16)
                out.append(cmap[code] ?: if (code in 32..126) code.toChar().toString() else "")
                idx += 4
            }
        } else {
            var idx = 0
            while (idx + 2 <= clean.length) {
                val code = clean.substring(idx, idx + 2).toInt(16)
                out.append(cmap[code] ?: decodeWinAnsiByte(code))
                idx += 2
            }
        }
    }

    private fun decodeWinAnsiByte(b: Int): String = when (b) {
        0x91 -> "\u2018"
        0x92 -> "\u2019"
        0x93 -> "\u201C"
        0x94 -> "\u201D"
        0x95 -> "\u2022"
        0x96 -> "\u2013"
        0x97 -> "\u2014"
        in 32..126, in 160..255 -> b.toChar().toString()
        else -> ""
    }

    private fun isDelimiterOrSpace(arr: ByteArray, idx: Int): Boolean {
        if (idx < 0 || idx >= arr.size) return true
        val c = arr[idx].toInt() and 0xFF
        return c <= 32 || c == '/'.code || c == '['.code || c == ']'.code ||
            c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code
    }

    private fun readObjectAsString(objId: Int): String? {
        val bytes = readObjectRawBytes(objId) ?: return null
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun readObjectRawBytes(objId: Int): ByteArray? {
        return when (val entry = xrefMap[objId]) {
            is XrefEntry.Uncompressed -> readRawObjectBytesAt(entry.offset)
            is XrefEntry.Compressed -> readCompressedObject(entry.objStmId, objId)
            null -> null
        }
    }

    private fun readCompressedObject(objStmId: Int, targetObjId: Int): ByteArray? {
        val parsed = objStmCache[objStmId] ?: run {
            val stmRaw = readObjectRawBytes(objStmId) ?: return null
            val dict = extractDictHeader(stmRaw)
            val first = extractInt(dict, "/First")
            val n = extractInt(dict, "/N")
            val decoded = decodeStreamFromRawObject(stmRaw, dict) ?: return null
            if (first <= 0 || first > decoded.size || n <= 0) return null
            val headerStr = String(decoded, 0, first, StandardCharsets.ISO_8859_1)
            val nums = Regex("\\d+").findAll(headerStr).mapNotNull { it.value.toIntOrNull() }.toList()
            val pairs = ArrayList<Pair<Int, Int>>()
            var idx = 0
            while (idx + 1 < nums.size && pairs.size < n) {
                pairs.add(nums[idx] to nums[idx + 1])
                idx += 2
            }
            val map = HashMap<Int, ByteArray>(pairs.size)
            for (k in pairs.indices) {
                val (id, relOff) = pairs[k]
                val start = first + relOff
                val end = if (k + 1 < pairs.size) first + pairs[k + 1].second else decoded.size
                if (start in 0..end && end <= decoded.size) {
                    map[id] = decoded.copyOfRange(start, end)
                }
            }
            objStmCache[objStmId] = map
            map
        }
        return parsed[targetObjId]
    }

    private fun readObjectStreamBytes(objId: Int): ByteArray? {
        val raw = readObjectRawBytes(objId) ?: return null
        val dict = extractDictHeader(raw)
        return decodeStreamFromRawObject(raw, dict)
    }

    private fun readRawObjectBytesAt(offset: Int): ByteArray? {
        if (offset < 0 || offset >= fileLength) return null
        val maxRead = min(1024 * 1024, fileLength - offset)
        val window = ByteArray(maxRead)
        synchronized(buffer) {
            buffer.position(offset)
            buffer.get(window, 0, maxRead)
        }
        val endIdx = findSubArray(window, END_OBJ_BYTES)
        return if (endIdx > 0) window.copyOfRange(0, endIdx) else window
    }

    private fun extractDictHeader(rawObj: ByteArray): String {
        val streamIdx = findSubArray(rawObj, STREAM_BYTES)
        val end = if (streamIdx > 0) streamIdx else min(rawObj.size, 8192)
        return String(rawObj, 0, end, StandardCharsets.ISO_8859_1)
    }

    private fun decodeStreamFromRawObject(rawObj: ByteArray, dictStr: String): ByteArray? {
        val streamIdx = findSubArray(rawObj, STREAM_BYTES)
        if (streamIdx < 0) return null
        var start = streamIdx + STREAM_BYTES.size
        if (start < rawObj.size && rawObj[start] == '\r'.code.toByte()) start++
        if (start < rawObj.size && rawObj[start] == '\n'.code.toByte()) start++
        val endIdx = findSubArray(rawObj, END_STREAM_BYTES, start)
        if (endIdx < start) return null
        val payload = rawObj.copyOfRange(start, endIdx)

        return when {
            dictStr.contains("/FlateDecode") -> inflateZlib(payload)
            dictStr.contains("/ASCIIHexDecode") -> decodeAsciiHex(payload)
            else -> payload
        }
    }

    private fun inflateZlib(input: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream(input.size * 3)
            val buf = ByteArray(8192)
            var guard = 0
            while (!inflater.finished() && guard < 2048) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                }
                guard++
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun decodeAsciiHex(input: ByteArray): ByteArray {
        val hex = String(input, StandardCharsets.ISO_8859_1)
            .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        val padded = if (hex.length % 2 != 0) "${hex}0" else hex
        return ByteArray(padded.length / 2) { i ->
            padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun readAscii(offset: Int, length: Int): String {
        val safeLen = min(length, fileLength - offset).coerceAtLeast(0)
        val bytes = ByteArray(safeLen)
        synchronized(buffer) {
            buffer.position(offset)
            buffer.get(bytes, 0, safeLen)
        }
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun extractRef(dict: String, key: String): Int {
        val match = Regex("$key\\s+(\\d+)\\s+\\d+\\s+R").find(dict)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun extractInt(dict: String, key: String): Int {
        val match = Regex("$key\\s+(\\d+)").find(dict)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun extractPdfStringValue(dict: String, key: String): String {
        val keyIdx = dict.indexOf(key)
        if (keyIdx < 0) return ""
        var i = keyIdx + key.length
        while (i < dict.length && dict[i].isWhitespace()) i++
        if (i >= dict.length) return ""

        return when (dict[i]) {
            '(' -> {
                i++
                val sb = StringBuilder()
                var depth = 1
                while (i < dict.length && depth > 0) {
                    val c = dict[i]
                    if (c == '\\' && i + 1 < dict.length) {
                        i++
                        sb.append(
                            when (dict[i]) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> dict[i]
                            },
                        )
                    } else if (c == '(') {
                        depth++
                        sb.append(c)
                    } else if (c == ')') {
                        depth--
                        if (depth > 0) sb.append(c)
                    } else {
                        sb.append(c)
                    }
                    i++
                }
                val raw = sb.toString()
                // Detect UTF-16BE BOM (\u00FE\u00FF)
                if (raw.length >= 2 && raw[0].code == 0xFE && raw[1].code == 0xFF) {
                    val bytes = raw.substring(2).toByteArray(StandardCharsets.ISO_8859_1)
                    String(bytes, StandardCharsets.UTF_16BE)
                } else {
                    raw
                }
            }
            '<' -> {
                val close = dict.indexOf('>', i + 1)
                if (close < 0) return ""
                val hex = dict.substring(i + 1, close)
                    .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                if (hex.startsWith("FEFF", ignoreCase = true)) {
                    decodeHexToUnicode(hex.substring(4))
                } else {
                    String(decodeAsciiHex(hex.toByteArray()), StandardCharsets.ISO_8859_1)
                }
            }
            else -> ""
        }
    }

    private fun formatPdfDate(raw: String): String {
        if (!raw.startsWith("D:") || raw.length < 10) return raw
        val y = raw.substring(2, 6)
        val m = raw.substring(6, 8)
        val d = raw.substring(8, 10)
        return "$y-$m-$d"
    }

    private fun readBigEndianInt(arr: ByteArray, offset: Int, width: Int): Int {
        var value = 0
        for (i in 0 until width) {
            value = (value shl 8) or (arr[offset + i].toInt() and 0xFF)
        }
        return value
    }

    private fun findSubArray(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        val limit = haystack.size - needle.size
        outer@ for (i in from..limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    override fun close() {
        runCatching { raf.close() }
    }
}

private val STREAM_BYTES = "stream".toByteArray(StandardCharsets.ISO_8859_1)
private val END_STREAM_BYTES = "endstream".toByteArray(StandardCharsets.ISO_8859_1)
private val END_OBJ_BYTES = "endobj".toByteArray(StandardCharsets.ISO_8859_1)
