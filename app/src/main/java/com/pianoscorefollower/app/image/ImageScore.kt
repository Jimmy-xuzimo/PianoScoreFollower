package com.pianoscorefollower.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * One page of an imported paper score.
 *
 * Pages are rasterised once at import time and kept as JPEGs on disk, so a large
 * PDF neither has to be re-rendered while scrolling nor held in memory as a whole.
 */
data class ImageScorePage(
    val id: Long,
    val file: File,
    val width: Int,
    val height: Int,
    /** Name of the file this page came from, shown in the page manager. */
    val sourceName: String,
)

/**
 * Turns PDFs and photographs into a flat, ordered list of page images.
 *
 * A PDF contributes one page per PDF page; anything else is treated as a single
 * photograph. Files are ordered by a natural sort of their display name so
 * `page2` lands before `page10`, which is what a stack of photographed pages
 * usually needs.
 */
class ImageScoreImporter(private val context: Context) {

    private val outputDir: File
        get() = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    suspend fun import(
        uris: List<Uri>,
        startId: Long,
        onProgress: (String) -> Unit = {},
    ): Result<List<ImageScorePage>> = withContext(Dispatchers.IO) {
        runCatching {
            val ordered = uris.sortedWith { a, b -> naturalCompare(displayName(a), displayName(b)) }
            val pages = ArrayList<ImageScorePage>()
            var nextId = startId

            ordered.forEachIndexed { index, uri ->
                val name = displayName(uri)
                onProgress("正在导入 ${index + 1}/${ordered.size}：$name")
                val isPdf = name.endsWith(".pdf", ignoreCase = true) ||
                    context.contentResolver.getType(uri).orEmpty().contains("pdf")
                val produced = if (isPdf) renderPdf(uri, name, nextId) else decodeImage(uri, name, nextId)
                pages += produced
                // Keep ids unique even when a file yields nothing, so a later import
                // can never collide with this one.
                nextId += produced.size.coerceAtLeast(1)
            }

            if (pages.isEmpty()) error("没有读取到任何页面，请换一个文件试试")
            pages
        }
    }

    /** Removes a page's cached image; the page itself is dropped by the caller. */
    fun deletePage(page: ImageScorePage) {
        runCatching { page.file.delete() }
    }

    fun clearAll() {
        runCatching { outputDir.listFiles()?.forEach { it.delete() } }
    }

    private fun renderPdf(uri: Uri, name: String, idBase: Long): List<ImageScorePage> {
        val pages = ArrayList<ImageScorePage>()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return pages

        descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = TARGET_PAGE_WIDTH.toFloat() / page.width
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // PDF pages are transparent by default, which would turn the
                        // staves into black-on-black once flattened to JPEG.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val file = writeJpeg(bitmap, "pdf_${idBase}_$index")
                        bitmap.recycle()
                        pages += ImageScorePage(idBase + index, file, width, height, name)
                    }
                }
            }
        }
        return pages
    }

    private fun decodeImage(uri: Uri, name: String, id: Long): List<ImageScorePage> {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // A software bitmap can be drawn into a JPEG later; a hardware one cannot.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val size = info.size
            if (size.width > TARGET_PAGE_WIDTH) {
                val scale = TARGET_PAGE_WIDTH.toFloat() / size.width
                decoder.setTargetSize(
                    TARGET_PAGE_WIDTH,
                    (size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }

        val file = writeJpeg(bitmap, "img_$id")
        val page = ImageScorePage(id, file, bitmap.width, bitmap.height, name)
        bitmap.recycle()
        return listOf(page)
    }

    private fun writeJpeg(bitmap: Bitmap, baseName: String): File {
        val file = File(outputDir, "$baseName.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        return file
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val value = cursor.getString(0)
                        if (!value.isNullOrBlank()) return value
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "page"
    }

    companion object {
        private const val DIRECTORY = "image-score"
        private const val JPEG_QUALITY = 88

        /**
         * Pages are stored a little wider than any phone screen so they stay sharp
         * when stretched to full width, without a decoded page costing more than
         * roughly 8 MB.
         */
        const val TARGET_PAGE_WIDTH = 1400
    }
}

/**
 * Compares file names so that embedded numbers order numerically; a plain string
 * sort would put `scan10` before `scan2`.
 */
internal fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var left = 0L
            var right = 0L
            while (i < a.length && a[i].isDigit()) {
                left = left * 10 + (a[i] - '0')
                i++
            }
            while (j < b.length && b[j].isDigit()) {
                right = right * 10 + (b[j] - '0')
                j++
            }
            if (left != right) return if (left < right) -1 else 1
        } else {
            val la = ca.lowercaseChar()
            val lb = cb.lowercaseChar()
            if (la != lb) return la.compareTo(lb)
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}
