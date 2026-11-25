package com.example

import com.example.data.AnnotationEntity
import com.example.data.NormalizedPoint
import com.example.engine.FluxPdfBinaryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

class FluxPdfEngineTest {

    @Test
    fun zeroCopyBinaryParser_extractsMetadataOutlinesAndFlateText() {
        val tempPdf = File.createTempFile("fluxpdf_test_", ".pdf")
        try {
            writeMinimalValidPdf(tempPdf)
            FluxPdfBinaryParser.open(tempPdf).use { parser ->
                assertEquals("1.7", parser.metadata.pdfVersion)
                assertEquals("FluxPDF Unit Test Folio", parser.metadata.title)
                assertEquals("FluxPDF Engine", parser.metadata.author)
                assertEquals(2, parser.getParsedPageCount())
                assertEquals(2, parser.metadata.outlines.size)
                assertEquals("Chapter One", parser.metadata.outlines[0].title)
                assertEquals(0, parser.metadata.outlines[0].pageIndex)
                assertEquals("Chapter Two", parser.metadata.outlines[1].title)
                assertEquals(1, parser.metadata.outlines[1].pageIndex)

                val page0Text = parser.extractPageText(0)
                val page1Text = parser.extractPageText(1)
                assertTrue("Page 0 should contain FlateDecode text: $page0Text", page0Text.contains("ZeroCopyBenchmark"))
                assertTrue("Page 1 should contain second page text: $page1Text", page1Text.contains("MappedByteBuffer"))
            }
        } finally {
            tempPdf.delete()
        }
    }

    @Test
    fun annotationNormalizedPoints_roundTripAccurately() {
        val pts = listOf(
            NormalizedPoint(0.125f, 0.25f),
            NormalizedPoint(0.875f, 0.75f),
        )
        val encoded = AnnotationEntity.encodePoints(pts)
        val entity = AnnotationEntity(
            documentId = 1L,
            pageIndex = 0,
            type = "HIGHLIGHT",
            colorArgb = 0x88F5C542L,
            pointsSerialized = encoded,
        )
        val decoded = entity.decodePoints()
        assertEquals(2, decoded.size)
        assertEquals(0.125f, decoded[0].x, 0.001f)
        assertEquals(0.75f, decoded[1].y, 0.001f)
    }

    private fun writeMinimalValidPdf(file: File) {
        val stream1 = deflate("BT /F1 12 Tf 50 700 Td (ZeroCopyBenchmark FluxPDF) Tj ET".toByteArray(StandardCharsets.ISO_8859_1))
        val stream2 = deflate("BT /F1 12 Tf 50 700 Td (MappedByteBuffer XRef Parser) Tj ET".toByteArray(StandardCharsets.ISO_8859_1))

        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R /Outlines 8 0 R >>".toByteArray(),
            "<< /Type /Pages /Kids [4 0 R 6 0 R] /Count 2 >>".toByteArray(),
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".toByteArray(),
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>".toByteArray(),
            wrapStream(stream1),
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents 7 0 R >>".toByteArray(),
            wrapStream(stream2),
            "<< /Type /Outlines /First 9 0 R /Last 10 0 R /Count 2 >>".toByteArray(),
            "<< /Title (Chapter One) /Parent 8 0 R /Next 10 0 R /Dest [4 0 R /Fit] >>".toByteArray(),
            "<< /Title (Chapter Two) /Parent 8 0 R /Prev 9 0 R /Dest [6 0 R /Fit] >>".toByteArray(),
            "<< /Title (FluxPDF Unit Test Folio) /Author (FluxPDF Engine) >>".toByteArray(),
        )

        FileOutputStream(file).use { fos ->
            val offsets = IntArray(objects.size + 1)
            var cursor = 0
            fun write(bytes: ByteArray) {
                fos.write(bytes)
                cursor += bytes.size
            }
            write("%PDF-1.7\n".toByteArray())
            objects.forEachIndexed { i, bytes ->
                offsets[i + 1] = cursor
                write("${i + 1} 0 obj\n".toByteArray())
                write(bytes)
                write("\nendobj\n".toByteArray())
            }
            val xrefStart = cursor
            write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray())
            for (i in 1..objects.size) {
                write(String.format("%010d 00000 n \n", offsets[i]).toByteArray())
            }
            write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Info 11 0 R >>\nstartxref\n$xrefStart\n%%EOF\n".toByteArray())
        }
    }

    private fun wrapStream(compressed: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write("<< /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n".toByteArray())
        bos.write(compressed)
        bos.write("\nendstream".toByteArray())
        return bos.toByteArray()
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) {
            val n = d.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        d.end()
        return out.toByteArray()
    }
}
