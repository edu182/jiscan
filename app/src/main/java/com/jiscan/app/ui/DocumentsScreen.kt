package com.jiscan.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiscan.app.data.DocumentStore
import com.jiscan.app.data.NamingMeta
import com.jiscan.app.data.ScanDocument
import com.jiscan.app.export.Exporter
import com.jiscan.app.ocr.OcrEngine
import com.jiscan.app.ui.theme.InkGreen
import com.jiscan.app.ui.theme.InkGreenSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocumentsScreen(
    store: DocumentStore,
    ocr: OcrEngine,
    refreshKey: Int,
    onDeleted: () -> Unit,
    onAddPagesToDoc: (String) -> Unit,
    onErasePage: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var docs by remember(refreshKey) { mutableStateOf(store.listDocuments()) }
    var selected by remember { mutableStateOf<ScanDocument?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var renameMeta by remember { mutableStateOf(NamingMeta()) }
    var organizeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var splitTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var splitCount by remember { mutableStateOf("1") }

    fun refresh() {
        docs = store.listDocuments()
        onDeleted()
    }

    if (selected != null) {
        val live = store.getDocument(selected!!.id) ?: selected!!
        DocumentDetail(
            doc = live,
            store = store,
            ocr = ocr,
            onBack = { selected = null; refresh() },
            onOcr = { bmp ->
                scope.launch {
                    ocrText = withContext(Dispatchers.IO) {
                        ocr.prepareAssets(); ocr.recognize(bmp, "chi_sim")
                    }
                }
            },
            onAddPages = { onAddPagesToDoc(live.id) },
            onErase = { fileName -> onErasePage(live.id, fileName) },
            onReorder = { order -> store.reorderPages(live.id, order); selected = store.getDocument(live.id) }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("文档数：${docs.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                organizeMode = !organizeMode
                mergeSelection = emptySet()
            }) { Text(if (organizeMode) "完成" else "文档整理") }
        }

        if (organizeMode) {
            Text("勾选两个及以上文档后合并；点单个文档卡片可拆分。",
                fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Description, null, Modifier.size(56.dp), tint = Color(0xFFB0C4BD))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有文档", color = Color(0xFF6E807A), fontSize = 15.sp)
                    Text("去「扫描」拍几页吧", color = Color(0xFF9AABA5), fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(docs, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc, store = store,
                        organizeMode = organizeMode,
                        selectedForMerge = mergeSelection.contains(doc.id),
                        onClick = {
                            if (organizeMode) {
                                splitTarget = doc; splitCount = "1"
                            } else {
                                selected = doc
                            }
                        },
                        onToggleMerge = {
                            mergeSelection = if (mergeSelection.contains(doc.id)) mergeSelection - doc.id
                            else mergeSelection + doc.id
                        },
                        onRename = { renameTarget = doc; renameMeta = doc.meta }
                    )
                }
            }
        }
    }

    if (organizeMode && mergeSelection.size >= 2) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = {
                    val sel = mergeSelection.toList()
                    val ok = store.mergeDocuments(sel[0], sel.drop(1))
                    Toast.makeText(context, if (ok) "已合并 ${sel.size} 个文档" else "合并失败", Toast.LENGTH_SHORT).show()
                    mergeSelection = emptySet(); organizeMode = false; refresh()
                },
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) { Icon(Icons.Filled.Merge, null); Spacer(Modifier.width(8.dp)); Text("合并选中的 ${mergeSelection.size} 个文档") }
        }
    }

    if (ocrText != null) {
        AlertDialog(
            onDismissRequest = { ocrText = null },
            title = { Text("识别结果") },
            text = {
                Text(ocrText!!.ifBlank { "未识别到文字，请确认图片清晰且已内置中文语言包。" },
                    fontSize = 14.sp, modifier = Modifier.height(300.dp))
            },
            confirmButton = { TextButton(onClick = { ocrText = null }) { Text("关闭") } }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("编辑文件名（${renameTarget!!.id}）") },
            text = {
                Column {
                    Text("日期编号锁定，可改部门/签名/事由。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(renameMeta.department, { renameMeta = renameMeta.copy(department = it) },
                        label = { Text("部门") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(renameMeta.signature, { renameMeta = renameMeta.copy(signature = it) },
                        label = { Text("签名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(renameMeta.reason, { renameMeta = renameMeta.copy(reason = it) },
                        label = { Text("事由") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.renameDocument(renameTarget!!.id, renameMeta)
                    renameTarget = null; refresh()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    if (splitTarget != null) {
        AlertDialog(
            onDismissRequest = { splitTarget = null },
            title = { Text("拆分文档") },
            text = {
                Column {
                    Text("原文档保留前 N 页，其余另存为新文档。", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(splitCount, { splitCount = it },
                        label = { Text("保留前几页（共 ${splitTarget!!.pages.size} 页）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = splitCount.toIntOrNull() ?: 0
                    val newId = store.splitDocument(splitTarget!!.id, n)
                    Toast.makeText(context, if (newId != null) "已拆分为 $newId" else "拆分失败", Toast.LENGTH_SHORT).show()
                    splitTarget = null; refresh()
                }) { Text("拆分") }
            },
            dismissButton = { TextButton(onClick = { splitTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun DocumentCard(
    doc: ScanDocument, store: DocumentStore, organizeMode: Boolean,
    selectedForMerge: Boolean, onClick: () -> Unit, onToggleMerge: () -> Unit, onRename: () -> Unit
) {
    val context = LocalContext.current
    val thumb = remember(doc.id + doc.pages.size) {
        doc.pages.firstOrNull()?.let { p ->
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                BitmapFactory.decodeFile(store.pageFile(doc.id, p.fileName).absolutePath, opts)
                    ?.let { Bitmap.createScaledBitmap(it, 120, 160, true) }
            } catch (_: Exception) { null }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = if (organizeMode) onToggleMerge else onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedForMerge) InkGreenSurface else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFEDEAE0)),
                contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Description, null, Modifier.size(40.dp), tint = InkGreen)
                if (organizeMode && selectedForMerge) {
                    Text("已选", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).background(InkGreen, RoundedCornerShape(4.dp)).padding(4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(doc.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${doc.pages.size} 页", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                if (!organizeMode) {
                    IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Edit, "重命名", tint = InkGreen, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        store.deleteDocument(doc.id)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, "删除", tint = Color(0xFFB05555), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentDetail(
    doc: ScanDocument, store: DocumentStore, ocr: OcrEngine,
    onBack: () -> Unit, onOcr: (android.graphics.Bitmap) -> Unit,
    onAddPages: () -> Unit, onErase: (String) -> Unit, onReorder: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(doc.id) { mutableStateOf(store.getDocument(doc.id) ?: doc) }
    var order by remember(doc.id) { mutableStateOf(current.pages.map { it.fileName }.toMutableList()) }

    fun refreshLocal() {
        val fresh = store.getDocument(doc.id)
        if (fresh != null) {
            current = fresh
            order = fresh.pages.map { it.fileName }.toMutableList()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.weight(1f))
            Text(current.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionBtn("补充扫描", Icons.Filled.Image, Modifier.weight(1f), onAddPages)
            ActionBtn("导出PDF", Icons.Filled.PictureAsPdf, Modifier.weight(1f), {
                scope.launch {
                    val files = order.map { store.pageFile(current.id, it) }
                    val pdf = withContext(Dispatchers.IO) { Exporter.createPdf(context, files, current.displayName.replace('/', '_')) }
                    if (pdf != null) context.startActivity(Exporter.shareFile(context, pdf, "application/pdf"))
                    else Toast.makeText(context, "PDF 生成失败", Toast.LENGTH_SHORT).show()
                }
            })
            ActionBtn("导出图片", Icons.Filled.Image, Modifier.weight(1f), {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        order.forEachIndexed { i, name ->
                            val b = BitmapFactory.decodeFile(store.pageFile(current.id, name).absolutePath)
                            if (b != null) { Exporter.saveToGallery(context, b, "${current.displayName}_p${i + 1}"); b.recycle() }
                        }
                    }
                    Toast.makeText(context, "已导出到相册", Toast.LENGTH_SHORT).show()
                }
            })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionBtn("系统分享", Icons.Filled.Share, Modifier.weight(1f), {
                val files = order.map { store.pageFile(current.id, it) }
                Exporter.shareFiles(context, files)?.let { context.startActivity(it) }
            })
            ActionBtn("重命名", Icons.Filled.Edit, Modifier.weight(1f), {
                // reuse the parent's dialog by requesting rename via back is not possible;
                // show a simple inline toast hint here.
                Toast.makeText(context, "返回文档列表可编辑文件名", Toast.LENGTH_SHORT).show()
            })
        }

        Text("长按 ↑↓ 调整页序 · 点铅笔清除手写", fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(order, key = { it }) { name ->
                val file = store.pageFile(current.id, name)
                val bmp = remember(file.absolutePath + current.pages.size) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 6 }
                        val raw = BitmapFactory.decodeFile(file.absolutePath, opts)
                        raw?.let { Bitmap.createScaledBitmap(it, 300, 400, true) }
                    } catch (_: Exception) { null }
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(56.dp, 74.dp), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(8.dp))
                        Text(name.replace(current.id + "_", ""), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val i = order.indexOf(name)
                            if (i > 0) {
                                val tmp = order[i - 1]; order[i - 1] = name; order[i] = tmp
                                onReorder(order.toList()); refreshLocal()
                            }
                        }, modifier = Modifier.size(32.dp)) { Text("↑", color = InkGreen, fontSize = 18.sp) }
                        IconButton(onClick = {
                            val i = order.indexOf(name)
                            if (i < order.size - 1) {
                                val tmp = order[i + 1]; order[i + 1] = name; order[i] = tmp
                                onReorder(order.toList()); refreshLocal()
                            }
                        }, modifier = Modifier.size(32.dp)) { Text("↓", color = InkGreen, fontSize = 18.sp) }
                        IconButton(onClick = { onErase(name) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, "清除手写", tint = InkGreen, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val full = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
                                if (full != null) onOcr(full)
                            }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Search, "OCR", tint = InkGreen, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            store.deletePage(current.id, name); refreshLocal()
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "删除", tint = Color(0xFFB05555), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBtn(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(38.dp)) {
        Icon(icon, null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}
