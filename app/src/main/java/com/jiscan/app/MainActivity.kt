package com.jiscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jiscan.app.data.DocumentStore
import com.jiscan.app.data.NamingMeta
import com.jiscan.app.data.ScanDocument
import com.jiscan.app.ocr.OcrEngine
import com.jiscan.app.scan.ColorFilterMode
import com.jiscan.app.scan.ImageProcessor
import com.jiscan.app.scan.Quad
import com.jiscan.app.scan.RectifyMode
import com.jiscan.app.scan.ScanMode
import com.jiscan.app.ui.CropScreen
import com.jiscan.app.ui.DocumentsScreen
import com.jiscan.app.ui.EraserScreen
import com.jiscan.app.ui.ScanScreen
import com.jiscan.app.ui.SettingsScreen
import com.jiscan.app.ui.theme.InkGreen
import com.jiscan.app.ui.theme.JiScanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var store: DocumentStore
    private lateinit var ocr: OcrEngine

    // Shared state lives at Activity level so both Compose UI and coroutine callbacks can read/write it.
    private val tab = mutableIntStateOf(0)
    private val refreshKey = mutableIntStateOf(0)
    private val currentDoc: MutableState<ScanDocument?> = mutableStateOf(null)
    private val targetDocId: MutableState<String?> = mutableStateOf(null)
    private val pendingJpeg: MutableState<List<ByteArray>> = mutableStateOf(emptyList())
    private val isProcessing: MutableState<Boolean> = mutableStateOf(false)
    private val cropTarget: MutableState<Triple<Bitmap, Quad, ScanMode>?> = mutableStateOf(null)
    private val eraseTarget: MutableState<Pair<String, String>?> = mutableStateOf(null)
    private val pendingFilter: MutableState<ColorFilterMode> = mutableStateOf(ColorFilterMode.COLOR)
    private val pendingNormalize: MutableState<Boolean> = mutableStateOf(false)
    private val cameraPermissionGranted: MutableState<Boolean> = mutableStateOf(false)

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DocumentStore(this)
        ocr = OcrEngine(this)
        cameraPermissionGranted.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        setContent { JiScanTheme { AppRoot() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocr.release()
    }

    private fun ensureDoc() {
        if (currentDoc.value != null) return
        val s = store.getSettings()
        val meta = NamingMeta(department = s.department, signature = s.signature)
        currentDoc.value = if (targetDocId.value != null) store.getDocument(targetDocId.value!!)
        else store.createDocument(meta)
    }

    @Composable
    private fun AppRoot() {
        // Crop confirmation overlay (manual / curve mode).
        val crop = cropTarget.value
        if (crop != null) {
            CropScreen(
                bitmap = crop.first,
                initial = crop.second,
                onCancel = { cropTarget.value = null },
                onConfirm = { quad ->
                    isProcessing.value = true
                    cropTarget.value = null
                    lifecycleScope.launch {
                        val jpeg = withContext(Dispatchers.IO) {
                            processToJpeg(crop.first, quad, crop.third, pendingFilter.value, pendingNormalize.value)
                        }
                        if (jpeg != null) {
                            pendingJpeg.value = pendingJpeg.value + jpeg
                            ensureDoc()
                        } else {
                            Toast.makeText(this@MainActivity, "处理失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                        isProcessing.value = false
                    }
                }
            )
            return
        }

        // Handwriting eraser overlay.
        val erase = eraseTarget.value
        if (erase != null) {
            EraserScreen(
                imageFile = store.pageFile(erase.first, erase.second),
                onCancel = { eraseTarget.value = null },
                onSave = { bytes ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.replacePageImage(erase.first, erase.second, bytes) }
                        eraseTarget.value = null
                        refreshKey.intValue++
                        Toast.makeText(this@MainActivity, "已保存擦除结果", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            return
        }

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = InkGreen) {
                    val items = listOf(
                        Triple("扫描", Icons.Filled.DocumentScanner, 0),
                        Triple("文档", Icons.Filled.Description, 1),
                        Triple("设置", Icons.Filled.Settings, 2)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = tab.intValue == index,
                            onClick = { tab.intValue = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = InkGreen, selectedTextColor = InkGreen,
                                unselectedIconColor = Color(0xFF8A9A94), unselectedTextColor = Color(0xFF8A9A94),
                                indicatorColor = Color(0xFFE3EFEA)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab.intValue) {
                    0 -> {
                        val doc = currentDoc.value
                        val lastPage = doc?.pages?.lastOrNull()?.let { store.pageFile(doc.id, it.fileName) }
                        ScanScreen(
                            currentDoc = doc,
                            pendingCount = pendingJpeg.value.size,
                            lastPageFile = lastPage,
                            hasCameraPermission = cameraPermissionGranted.value,
                            isProcessing = isProcessing.value,
                            onRequestCameraPermission = { requestCamera.launch(Manifest.permission.CAMERA) },
                            onCapture = { file, mode, filter, rectify, normalize ->
                                pendingFilter.value = filter; pendingNormalize.value = normalize
                                handleCapture(file, mode, filter, rectify, normalize)
                            },
                            onAddFromGallery = { uri, mode, filter, rectify, normalize ->
                                pendingFilter.value = filter; pendingNormalize.value = normalize
                                handleGallery(uri, mode, filter, rectify, normalize)
                            },
                            onRetake = {
                                pendingJpeg.value = emptyList()
                                if (targetDocId.value == null) currentDoc.value = null
                                Toast.makeText(this@MainActivity, "已清空待存页", Toast.LENGTH_SHORT).show()
                            },
                            onFinish = { finishPending() }
                        )
                    }
                    1 -> DocumentsScreen(
                        store = store, ocr = ocr, refreshKey = refreshKey.intValue,
                        onDeleted = { refreshKey.intValue++ },
                        onAddPagesToDoc = { docId ->
                            targetDocId.value = docId
                            currentDoc.value = store.getDocument(docId)
                            pendingJpeg.value = emptyList()
                            tab.intValue = 0
                            Toast.makeText(this@MainActivity, "正在为 $docId 补充扫描页", Toast.LENGTH_SHORT).show()
                        },
                        onErasePage = { docId, page -> eraseTarget.value = docId to page }
                    )
                    2 -> SettingsScreen(ocr, store)
                }
            }
        }
    }

    private fun handleCapture(file: File, mode: ScanMode, filter: ColorFilterMode, rectify: RectifyMode, normalize: Boolean) {
        isProcessing.value = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val rotated = ImageProcessor.correctRotation(file, raw)
                val quad = ImageProcessor.detectQuad(rotated, mode)
                if (rectify == RectifyMode.AUTO) {
                    Pair(processToJpeg(rotated, quad, mode, filter, normalize), null)
                } else {
                    Pair(null, Pair(rotated, quad))
                }
            }
            isProcessing.value = false
            when {
                result != null && result.first != null -> {
                    pendingJpeg.value = pendingJpeg.value + result.first!!
                    ensureDoc()
                }
                result != null && result.second != null ->
                    cropTarget.value = Triple(result.second!!.first, result.second!!.second, mode)
                else -> Toast.makeText(this@MainActivity, "拍照失败，请重试", Toast.LENGTH_SHORT).show()
            }
            file.delete()
        }
    }

    private fun handleGallery(uri: Uri, mode: ScanMode, filter: ColorFilterMode, rectify: RectifyMode, normalize: Boolean) {
        isProcessing.value = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: return@withContext null
                val quad = ImageProcessor.detectQuad(src, mode)
                if (rectify == RectifyMode.AUTO) Pair(processToJpeg(src, quad, mode, filter, normalize), null)
                else Pair(null, Pair(src, quad))
            }
            isProcessing.value = false
            when {
                result != null && result.first != null -> {
                    pendingJpeg.value = pendingJpeg.value + result.first!!
                    ensureDoc()
                }
                result != null && result.second != null ->
                    cropTarget.value = Triple(result.second!!.first, result.second!!.second, mode)
                else -> Toast.makeText(this@MainActivity, "读取图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishPending() {
        val doc = currentDoc.value ?: return
        val bytes = pendingJpeg.value
        if (bytes.isEmpty()) return
        isProcessing.value = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { bytes.forEach { store.addPage(doc.id, it) } }
            pendingJpeg.value = emptyList()
            currentDoc.value = null
            targetDocId.value = null
            isProcessing.value = false
            refreshKey.intValue++
            Toast.makeText(this@MainActivity, "已保存到 ${doc.id}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Detect -> correct -> normalize -> filter -> JPEG. All on-device. */
    private fun processToJpeg(source: Bitmap, quad: Quad, mode: ScanMode, filter: ColorFilterMode, normalize: Boolean): ByteArray? {
        return try {
            var corrected = ImageProcessor.perspective(source, quad)
            if (corrected.width < 10 || corrected.height < 10) {
                corrected.recycle()
                corrected = Bitmap.createBitmap(source)
            }
            val normalized = if (normalize) ImageProcessor.normalizeLighting(corrected) else corrected
            val filtered = ImageProcessor.applyFilter(normalized, filter)
            val out = ByteArrayOutputStream()
            filtered.compress(Bitmap.CompressFormat.JPEG, 90, out)
            if (filtered !== normalized) filtered.recycle()
            if (normalized !== corrected) normalized.recycle()
            corrected.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
