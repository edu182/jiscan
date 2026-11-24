package com.jiscan.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One scanned page. fileName is stable within the document folder. */
data class ScanPage(
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Naming metadata that follows the rule: YYMMDD_[dept]_[sign]_reason. */
data class NamingMeta(
    val department: String = "",
    val signature: String = "",
    val reason: String = ""
) {
    /** The user-visible suffix after the YYMMDD_id prefix. */
    fun suffix(): String {
        val parts = listOf(department, signature, reason)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parts.joinToString("_")
    }
}

/**
 * A document. [id] always starts with YYMMDD and is the folder name (safe ASCII).
 * [meta] holds department/signature/reason used to build the display name.
 */
data class ScanDocument(
    val id: String,
    val meta: NamingMeta = NamingMeta(),
    val pages: List<ScanPage>,
    val createdAt: Long
) {
    val displayName: String
        get() {
            val s = meta.suffix()
            return if (s.isEmpty()) id else "${id}_$s"
        }
}

/** Persisted app settings. */
data class AppSettings(
    val signature: String = "",
    val department: String = ""
)

/**
 * File-system backed store. All files live in app-private filesDir; no network.
 * Metadata is a single JSON index; pages are JPEGs in per-document folders.
 */
class DocumentStore(private val context: Context) {

    private val root: File get() = File(context.filesDir, "documents").apply { mkdirs() }
    private val indexFile: File get() = File(context.filesDir, "index.json")
    private val settingsFile: File get() = File(context.filesDir, "settings.json")

    // ---- Settings -------------------------------------------------------

    fun getSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            val o = JSONObject(settingsFile.readText())
            AppSettings(
                signature = o.optString("signature", ""),
                department = o.optString("department", "")
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        settingsFile.writeText(JSONObject().apply {
            put("signature", settings.signature.trim())
            put("department", settings.department.trim())
        }.toString())
    }

    // ---- Documents ------------------------------------------------------

    fun listDocuments(): List<ScanDocument> = readIndex().sortedByDescending { it.createdAt }

    fun getDocument(id: String): ScanDocument? = readIndex().firstOrNull { it.id == id }

    /** Create a document whose id starts with YYMMDD, with a daily increment. */
    fun createDocument(meta: NamingMeta = NamingMeta()): ScanDocument {
        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val docs = readIndex()
        var n = docs.count { it.id.startsWith(day) } + 1
        var id = "%s_%03d".format(day, n)
        while (docs.any { it.id == id }) {
            n++
            id = "%s_%03d".format(day, n)
        }
        val doc = ScanDocument(id = id, meta = meta, pages = emptyList(), createdAt = System.currentTimeMillis())
        File(root, id).mkdirs()
        writeIndex(docs + doc)
        return doc
    }

    /** Save a JPEG into the doc folder; returns the page for the in-memory session. */
    fun addPage(docId: String, jpegBytes: ByteArray): ScanPage {
        val dir = File(root, docId).apply { mkdirs() }
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        val nextNum = if (idx >= 0) docs[idx].pages.size + 1 else dir.listFiles()?.size?.plus(1) ?: 1
        val fileName = "${docId}_p${nextNum}.jpg"
        File(dir, fileName).writeBytes(jpegBytes)
        val page = ScanPage(fileName)
        if (idx >= 0) {
            val d = docs[idx]
            docs[idx] = d.copy(pages = d.pages + page)
            writeIndex(docs)
        }
        return page
    }

    /** Replace a page's image bytes (used by the hand-writing eraser). */
    fun replacePageImage(docId: String, fileName: String, jpegBytes: ByteArray) {
        File(File(root, docId), fileName).writeBytes(jpegBytes)
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            val d = docs[idx]
            docs[idx] = d.copy(pages = d.pages.map {
                if (it.fileName == fileName) it.copy(createdAt = System.currentTimeMillis()) else it
            })
            writeIndex(docs)
        }
    }

    fun pageFile(docId: String, fileName: String): File = File(File(root, docId), fileName)

    fun renameDocument(docId: String, meta: NamingMeta) {
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            docs[idx] = docs[idx].copy(meta = meta)
            writeIndex(docs)
        }
    }

    fun deleteDocument(docId: String) {
        File(root, docId).deleteRecursively()
        writeIndex(readIndex().filterNot { it.id == docId })
    }

    fun deletePage(docId: String, fileName: String) {
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            val d = docs[idx]
            val remaining = d.pages.filterNot { it.fileName == fileName }
            renumberAndSave(docs, idx, d, remaining, File(root, docId))
            File(File(root, docId), fileName).delete()
        }
    }

    /** Reorder pages. [newOrder] is the full list of page file names in the desired order. */
    fun reorderPages(docId: String, newOrder: List<String>) {
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            val d = docs[idx]
            val byName = d.pages.associateBy { it.fileName }
            val ordered = newOrder.mapNotNull { byName[it] }
            docs[idx] = d.copy(pages = ordered)
            writeIndex(docs)
        }
    }

    /** Append all pages from [sourceIds] into [targetId], then delete the sources. */
    fun mergeDocuments(targetId: String, sourceIds: List<String>): Boolean {
        val docs = readIndex().toMutableList()
        val tIdx = docs.indexOfFirst { it.id == targetId }
        if (tIdx < 0) return false
        val sources = sourceIds.mapNotNull { id -> docs.firstOrNull { it.id == id } }
        if (sources.isEmpty()) return false
        val targetDir = File(root, targetId)
        var targetPages = docs[tIdx].pages.toMutableList()
        for (src in sources) {
            val srcDir = File(root, src.id)
            for (page in src.pages) {
                val from = File(srcDir, page.fileName)
                if (!from.exists()) continue
                val newName = "${targetId}_m${System.currentTimeMillis()}_${targetPages.size}.jpg"
                val to = File(targetDir, newName)
                from.copyTo(to, overwrite = true)
                targetPages.add(ScanPage(newName, page.createdAt))
            }
        }
        docs[tIdx] = docs[tIdx].copy(pages = targetPages.toList())
        val removeIds = sources.map { it.id }.toSet()
        writeIndex(docs.filterNot { removeIds.contains(it.id) })
        removeIds.forEach { File(root, it).deleteRecursively() }
        return true
    }

    /**
     * Split a document. [firstPageCount] pages stay in the original; the rest
     * become a new document. Returns the new document id, or null if invalid.
     */
    fun splitDocument(docId: String, firstPageCount: Int): String? {
        val docs = readIndex().toMutableList()
        val idx = docs.indexOfFirst { it.id == docId }
        if (idx < 0) return null
        val d = docs[idx]
        if (firstPageCount <= 0 || firstPageCount >= d.pages.size) return null

        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        var n = docs.count { it.id.startsWith(day) } + 1
        var newId = "%s_%03d".format(day, n)
        while (docs.any { it.id == newId }) { n++; newId = "%s_%03d".format(day, n) }

        val newDir = File(root, newId).apply { mkdirs() }
        val movedPages = mutableListOf<ScanPage>()
        val oldDir = File(root, docId)
        d.pages.drop(firstPageCount).forEachIndexed { i, p ->
            val from = File(oldDir, p.fileName)
            val newName = "${newId}_p${i + 1}.jpg"
            if (from.exists()) from.copyTo(File(newDir, newName), overwrite = true)
            movedPages.add(ScanPage(newName, p.createdAt))
        }
        val keptPages = d.pages.take(firstPageCount).toMutableList()
        keptPages.forEachIndexed { i, p ->
            val oldFile = File(oldDir, p.fileName)
            val newName = "${docId}_p${i + 1}.jpg"
            if (p.fileName != newName && oldFile.exists()) {
                oldFile.renameTo(File(oldDir, newName))
            }
        }
        val keptRenamed = keptPages.mapIndexed { i, _ -> ScanPage("${docId}_p${i + 1}.jpg") }

        val newDoc = ScanDocument(
            id = newId,
            meta = d.meta.copy(reason = (d.meta.reason + " 续").trim()),
            pages = movedPages,
            createdAt = System.currentTimeMillis()
        )
        docs[idx] = d.copy(pages = keptRenamed)
        docs.add(newDoc)
        // Delete leftover original page files from the moved range.
        d.pages.drop(firstPageCount).forEach { File(oldDir, it.fileName).delete() }
        writeIndex(docs)
        return newId
    }

    private fun renumberAndSave(
        docs: MutableList<ScanDocument>,
        idx: Int,
        doc: ScanDocument,
        remaining: List<ScanPage>,
        dir: File
    ) {
        remaining.forEachIndexed { i, p ->
            val target = "${doc.id}_p${i + 1}.jpg"
            if (p.fileName != target) {
                val from = File(dir, p.fileName)
                if (from.exists()) from.renameTo(File(dir, target))
            }
        }
        docs[idx] = doc.copy(pages = remaining.mapIndexed { i, _ -> ScanPage("${doc.id}_p${i + 1}.jpg") })
        writeIndex(docs)
    }

    // ---- Index ----------------------------------------------------------

    private fun readIndex(): List<ScanDocument> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val metaObj = o.optJSONObject("meta")
                val meta = if (metaObj != null) NamingMeta(
                    department = metaObj.optString("dept", ""),
                    signature = metaObj.optString("sign", ""),
                    reason = metaObj.optString("reason", "")
                ) else NamingMeta()
                val pagesArr = o.optJSONArray("pages") ?: JSONArray()
                val pages = (0 until pagesArr.length()).map { j ->
                    val p = pagesArr.getJSONObject(j)
                    ScanPage(p.getString("f"), p.optLong("t"))
                }
                ScanDocument(
                    id = o.getString("id"),
                    meta = meta,
                    pages = pages,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(docs: List<ScanDocument>) {
        val arr = JSONArray()
        docs.forEach { d ->
            val o = JSONObject().apply {
                put("id", d.id)
                put("createdAt", d.createdAt)
                put("meta", JSONObject().apply {
                    put("dept", d.meta.department)
                    put("sign", d.meta.signature)
                    put("reason", d.meta.reason)
                })
                val pagesArr = JSONArray()
                d.pages.forEach { p ->
                    pagesArr.put(JSONObject().apply { put("f", p.fileName); put("t", p.createdAt) })
                }
                put("pages", pagesArr)
            }
            arr.put(o)
        }
        indexFile.writeText(arr.toString())
    }
}
