package com.jiscan.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * All outputs stay on device. Sharing uses FileProvider with a single-file URI grant.
 * Saving to gallery uses MediaStore (no storage permission needed on modern Android).
 */
object Exporter {

    /** Save a page to the gallery using MediaStore. No permission required on API 29+. */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JiScan")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            putPendingFalse(values)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun putPendingFalse(values: ContentValues) {
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
    }

    /** Build a multi-page PDF from already-cropped JPEG files. */
    fun createPdf(context: Context, jpegFiles: List<File>, outputName: String): File? {
        if (jpegFiles.isEmpty()) return null
        val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val pdfFile = File(outDir, "$outputName.pdf")
        val doc = PdfDocument()
        try {
            for (i in jpegFiles.indices) {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(jpegFiles[i].absolutePath, opts)
                val pageW = opts.outWidth.coerceAtLeast(1)
                val pageH = opts.outHeight.coerceAtLeast(1)
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                val page = doc.startPage(pageInfo)
                val bitmap = android.graphics.BitmapFactory.decodeFile(jpegFiles[i].absolutePath)
                if (bitmap != null) {
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    bitmap.recycle()
                }
                doc.finishPage(page)
            }
            FileOutputStream(pdfFile).use { doc.writeTo(it) }
            return pdfFile
        } catch (e: Exception) {
            pdfFile.delete()
            return null
        } finally {
            doc.close()
        }
    }

    /** Build a share Intent for a single file with a temporary read grant. */
    fun shareFile(context: Context, file: File, mime: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Build a system share Intent for multiple image files (ACTION_SEND_MULTIPLE). */
    fun shareFiles(context: Context, files: List<File>): Intent? {
        val existing = files.filter { it.exists() }
        if (existing.isEmpty()) return null
        val uris = ArrayList<Uri>()
        existing.forEach { uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)) }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}
