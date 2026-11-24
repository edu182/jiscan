package com.jiscan.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

/**
 * Offline OCR using Tesseract 5 via Tesseract4Android.
 * Language data is bundled in assets/tessdata/ and copied to app-private storage
 * on first use. No network is ever contacted.
 */
class OcrEngine(private val context: Context) {

    private val dataDir = File(context.filesDir, "tesseract")
    private val tessdataDir = File(dataDir, "tessdata")
    private var api: TessBaseAPI? = null
    private var initialized = false

    fun isLanguageAvailable(lang: String): Boolean = File(tessdataDir, "$lang.traineddata").exists()

    fun availableLanguages(): List<String> {
        return tessdataDir.listFiles { f -> f.name.endsWith(".traineddata") }
            ?.map { it.name.removeSuffix(".traineddata") }?.sorted() ?: emptyList()
    }

    /** Copy bundled traineddata from assets to private dir. Idempotent. */
    fun prepareAssets(): Boolean {
        return try {
            tessdataDir.mkdirs()
            val assets = context.assets.list("tessdata") ?: return false
            var copied = false
            for (name in assets) {
                if (!name.endsWith(".traineddata")) continue
                val target = File(tessdataDir, name)
                if (!target.exists() || target.length() == 0L) {
                    context.assets.open("tessdata/$name").use { input ->
                        FileOutputStream(target).use { out ->
                            val buf = ByteArray(16 * 1024)
                            var n: Int
                            while (input.read(buf).also { n = it } > 0) out.write(buf, 0, n)
                        }
                    }
                    copied = true
                }
            }
            copied || availableLanguages().isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "prepareAssets failed", e)
            false
        }
    }

    @Synchronized
    fun recognize(bitmap: Bitmap, lang: String = "chi_sim"): String {
        prepareAssets()
        if (!isLanguageAvailable(lang)) return ""
        return try {
            val engine = api ?: TessBaseAPI().also { api = it }
            if (!initialized) {
                if (!engine.init(dataDir.absolutePath, lang)) return ""
                initialized = true
            }
            engine.setImage(bitmap)
            val text = engine.getUTF8Text() ?: ""
            text
        } catch (e: Exception) {
            Log.e(TAG, "recognize failed", e)
            ""
        }
    }

    @Synchronized
    fun release() {
        try { api?.recycle() } catch (_: Exception) {}
        api = null
        initialized = false
    }

    companion object {
        private const val TAG = "OcrEngine"
    }
}
