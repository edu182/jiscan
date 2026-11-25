package com.jiscan.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jiscan.app.data.ScanDocument
import com.jiscan.app.scan.CameraController
import com.jiscan.app.scan.ColorFilterMode
import com.jiscan.app.scan.RectifyMode
import com.jiscan.app.scan.ScanMode
import com.jiscan.app.scan.TorchMode
import com.jiscan.app.ui.theme.InkGreenLight
import java.io.File

enum class GuideLine { GRID, CROSS, NONE }

@Composable
fun ScanScreen(
    currentDoc: ScanDocument?,
    pendingCount: Int,
    lastPageFile: File?,
    onCapture: (File, ScanMode, ColorFilterMode, RectifyMode, Boolean) -> Unit,
    onAddFromGallery: (android.net.Uri, ScanMode, ColorFilterMode, RectifyMode, Boolean) -> Unit,
    onRetake: () -> Unit,
    onFinish: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    hasCameraPermission: Boolean,
    isProcessing: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val prefs = remember { context.getSharedPreferences("jiscan_ui", android.content.Context.MODE_PRIVATE) }
    var scanMode by remember { mutableStateOf(ScanMode.DOCUMENT) }
    var rectifyMode by remember { mutableStateOf(RectifyMode.AUTO) }
    var colorFilter by remember { mutableStateOf(ColorFilterMode.COLOR) }
    var torchMode by remember { mutableStateOf(TorchMode.AUTO) }
    var guideLine by remember { mutableStateOf(GuideLine.GRID) }
    var normalize by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(prefs.getBoolean("show_guide", true)) }
    var showOptions by remember { mutableStateOf(prefs.getBoolean("show_options", true)) }

    val cameraController = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAddFromGallery(uri, scanMode, colorFilter, rectifyMode, normalize)
    }

    DisposableEffect(Unit) { onDispose { cameraController.shutdown() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            DisposableEffect(lifecycleOwner) {
                cameraController.bind(lifecycleOwner, previewView)
                cameraController.setTorch(torchMode)
                onDispose { }
            }
            GuideLines(guideLine)
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要相机权限才能扫描", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestCameraPermission) { Text("授予相机权限") }
            }
        }

        if (showGuide && hasCameraPermission && pendingCount == 0) {
            GuideOverlay(
                onDismiss = { showGuide = false },
                onNeverShow = {
                    showGuide = false
                    prefs.edit().putBoolean("show_guide", false).apply()
                }
            )
        }

        // Top bar: retake / import / save N pages.
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButtonDark("重拍", enabled = pendingCount > 0, onClick = onRetake)
            TextButtonDark("导入", enabled = true) { galleryLauncher.launch("image/*") }
            TextButtonDark(
                if (pendingCount > 0) "保存${pendingCount}页" else "保存",
                enabled = pendingCount > 0,
                onClick = onFinish
            )
        }

        // Right side: last page preview strip.
        if (lastPageFile != null) {
            val bmp = remember(lastPageFile.absolutePath) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    BitmapFactory.decodeFile(lastPageFile.absolutePath, opts)
                } catch (_: Exception) { null }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                    .width(64.dp).height(180.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), "上一页", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("上一页", color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Bottom control panel — five rows matching the user's sketch.
        Surface(color = Color(0xF21C1C1C), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("选项", color = Color(0xFFB8CFC7), fontSize = 12.sp)
                    Text(
                        if (showOptions) "收起" else "展开",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            showOptions = !showOptions
                            prefs.edit().putBoolean("show_options", showOptions).apply()
                        }.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (showOptions) {
                    MatrixRow("模式", listOf("文档", "书本", "证件"),
                        listOf(ScanMode.DOCUMENT, ScanMode.BOOK, ScanMode.CARD), scanMode) { scanMode = it }
                    MatrixRow("矫正", listOf("自动", "手动", "曲线"),
                        listOf(RectifyMode.AUTO, RectifyMode.MANUAL, RectifyMode.CURVE), rectifyMode) { rectifyMode = it }
                    MatrixRow("滤镜", listOf("彩色", "黑白"),
                        listOf(ColorFilterMode.COLOR, ColorFilterMode.BLACK_WHITE), colorFilter) { colorFilter = it }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MatrixLabel("灯光")
                        val torchLabels = listOf("自动", "长开", "长闭")
                        val torchValues = listOf(TorchMode.AUTO, TorchMode.ON, TorchMode.OFF)
                        OptionGroup(torchLabels, torchValues.indexOf(torchMode)) { idx ->
                            torchMode = torchValues[idx]
                            cameraController.setTorch(torchMode)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MatrixLabel("参考线")
                        val gLabels = listOf("九宫格", "十字线", "无")
                        val gValues = listOf(GuideLine.GRID, GuideLine.CROSS, GuideLine.NONE)
                        OptionGroup(gLabels, gValues.indexOf(guideLine)) { guideLine = gValues[it] }
                    }
                }

                // Shutter + normalize toggle + doc info.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SmallToggle("去阴影 ${if (normalize) "开" else "关"}", normalize) { normalize = !normalize }
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape)
                            .clickable(enabled = hasCameraPermission && !isProcessing) {
                                val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                                cameraController.capture(file) { ok, _ ->
                                    if (ok) {
                                        // capture callback is on a background executor; post to main.
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            onCapture(file, scanMode, colorFilter, rectifyMode, normalize)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White))
                    }
                    Text(
                        currentDoc?.let { "${it.id}\n${it.pages.size + pendingCount}页" } ?: "未开始",
                        color = Color.White, fontSize = 11.sp, textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp)
                    )
                }
            }
        }

        if (isProcessing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xCC000000), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = InkGreenLight)
                        Spacer(Modifier.height(12.dp))
                        Text("正在校正并保存…", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixLabel(text: String) {
    Text(text, color = Color(0xFFB8CFC7), fontSize = 12.sp,
        modifier = Modifier.width(38.dp), fontWeight = FontWeight.Medium)
}

@Composable
private fun <T> MatrixRow(label: String, labels: List<String>, values: List<T>, current: T, onChange: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MatrixLabel(label)
        OptionGroup(labels, values.indexOf(current)) { onChange(values[it]) }
    }
}

@Composable
private fun OptionGroup(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier.weight(1f).height(30.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) InkGreenLight else Color(0x33FFFFFF))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.White else Color(0xDDFFFFFF), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TextButtonDark(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(5.dp))
            .background(if (enabled) Color(0x44FFFFFF) else Color(0x22FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (enabled) Color.White else Color(0x88FFFFFF), fontSize = 13.sp)
    }
}

@Composable
private fun SmallToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) InkGreenLight else Color(0x33FFFFFF),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun GuideLines(mode: GuideLine) {
    if (mode == GuideLine.NONE) return
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c = Color.White.copy(alpha = 0.35f)
        if (mode == GuideLine.CROSS) {
            drawLine(c, androidx.compose.ui.geometry.Offset(w / 2, 0f), androidx.compose.ui.geometry.Offset(w / 2, h), 1.dp.toPx())
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, h / 2), androidx.compose.ui.geometry.Offset(w, h / 2), 1.dp.toPx())
        } else {
            for (i in 1..2) {
                drawLine(c, androidx.compose.ui.geometry.Offset(w * i / 3, 0f), androidx.compose.ui.geometry.Offset(w * i / 3, h), 1.dp.toPx())
                drawLine(c, androidx.compose.ui.geometry.Offset(0f, h * i / 3), androidx.compose.ui.geometry.Offset(w, h * i / 3), 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun GuideOverlay(onDismiss: () -> Unit, onNeverShow: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss).background(Color(0x88000000)),
        contentAlignment = Alignment.Center) {
        Surface(color = Color(0xE6222222), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("简扫 · 快速扫描", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("对准文档，自动框选并校正；可连续拍多页", color = Color(0xFFC8E0D9), fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("顶部可重拍/导入/保存，底部切换模式", color = Color(0xFF9FC4BA), fontSize = 12.sp, textAlign = TextAlign.Center)
                Button(onClick = onDismiss) { Text("开始扫描") }
                TextButton(onClick = onNeverShow) {
                    Text("不再提示", color = Color(0xFF9FC4BA), fontSize = 12.sp)
                }
            }
        }
    }
}
