package com.jiscan.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import com.jiscan.app.scan.HandwritingEraser
import java.io.File

/** Finger-paint over handwriting to erase it; locally fills from surrounding pixels. */
@Composable
fun EraserScreen(
    imageFile: File,
    onSave: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val base = remember(imageFile.absolutePath) { BitmapFactory.decodeFile(imageFile.absolutePath) }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushPx by remember { mutableFloatStateOf(24f) }
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (base == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("图片加载失败", color = Color.White)
        }
        return
    }

    fun imgScale(): Float = min(viewSize.width.toFloat() / base.width, viewSize.height.toFloat() / base.height)
    fun toImage(o: Offset, scale: Float): Offset {
        val left = (viewSize.width - base.width * scale) / 2f
        val top = (viewSize.height - base.height * scale) / 2f
        return Offset((o.x - left) / scale, (o.y - top) / scale)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "取消", tint = Color.White) }
            Text("清除手写", color = Color.White, fontSize = 16.sp)
            Row {
                IconButton(onClick = {
                    if (strokes.isNotEmpty()) strokes = strokes.dropLast(1)
                }) { Icon(Icons.Filled.Undo, "撤销", tint = Color.White) }
                IconButton(onClick = {
                    val scale = imgScale()
                    val all = (strokes + listOf(currentStroke)).filter { it.isNotEmpty() }
                        .map { pts -> pts.map { toImage(it, scale) } }
                    val flat = all.flatMap { it }.map { it.x to it.y }
                    val r = brushPx / scale
                    resultBitmap = HandwritingEraser.erase(base, flat, r)
                }) { Icon(Icons.Filled.Check, "应用", tint = Color(0xFF7FE0C4)) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentStroke = listOf(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = currentStroke + change.position
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) strokes = strokes + listOf(currentStroke)
                        currentStroke = emptyList()
                    },
                    onDragCancel = { currentStroke = emptyList() }
                )
            }) {
                val scale = imgScale()
                val left = (size.width - base.width * scale) / 2f
                val top = (size.height - base.height * scale) / 2f
                val shown = resultBitmap ?: base
                drawImage(
                    image = shown.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize((base.width * scale).toInt(), (base.height * scale).toInt())
                )
                if (resultBitmap == null) {
                    val all = strokes + listOf(currentStroke)
                    for (pts in all) {
                        for (i in 1 until pts.size) {
                            drawLine(Color(0x88FF5252), pts[i - 1], pts[i],
                                strokeWidth = brushPx * 2, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("笔刷大小", color = Color.White, fontSize = 12.sp)
            Slider(value = brushPx, onValueChange = { brushPx = it },
                valueRange = 8f..80f, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { resultBitmap = null }, modifier = Modifier.weight(1f)) { Text("重新涂抹") }
                Button(onClick = {
                    val bmp = resultBitmap
                    if (bmp != null) {
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        onSave(out.toByteArray())
                    } else {
                        Toast.makeText(context, "请先涂抹并点应用", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.weight(1f)) { Text("保存") }
            }
            Spacer(Modifier.height(4.dp))
            Text("用手指涂抹手写痕迹，点右上角对预览效果，满意后保存。",
                color = Color(0xFFB7E4D8), fontSize = 11.sp)
        }
    }
}
