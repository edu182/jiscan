package com.jiscan.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiscan.app.scan.Quad
import com.jiscan.app.ui.theme.InkGreenLight
import kotlin.math.max
import kotlin.math.min

/**
 * Manual corner adjustment. Shows the image with four draggable handles.
 * Corner order matches ImageProcessor: TL, TR, BR, BL.
 */
@Composable
fun CropScreen(
    bitmap: Bitmap,
    initial: Quad,
    onConfirm: (Quad) -> Unit,
    onCancel: () -> Unit
) {
    var corners by remember {
        mutableStateOf(
            listOf(
                Offset(initial.p1x, initial.p1y),
                Offset(initial.p2x, initial.p2y),
                Offset(initial.p3x, initial.p3y),
                Offset(initial.p4x, initial.p4y)
            )
        )
    }
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeCorner by remember { mutableStateOf(-1) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "取消", tint = Color.White) }
            Text("手动调整选区", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onConfirm(corners.toQuad()) }) {
                Icon(Icons.Filled.Check, "确认", tint = Color(0xFF7FE0C4))
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewSize = it }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activeCorner = corners.indices.minByOrNull { i ->
                                    val c = toScreen(corners[i], bitmap, viewSize, density)
                                    (c - offset).getDistanceSquared()
                                } ?: -1
                            },
                            onDragEnd = { activeCorner = -1 },
                            onDragCancel = { activeCorner = -1 },
                            onDrag = { change, _ ->
                                if (activeCorner in 0..3) {
                                    change.consume()
                                    val imgPt = toImage(change.position, bitmap, viewSize, density)
                                    val clamped = Offset(
                                        max(0f, min(bitmap.width.toFloat(), imgPt.x)),
                                        max(0f, min(bitmap.height.toFloat(), imgPt.y))
                                    )
                                    corners = corners.toMutableList().also { it[activeCorner] = clamped }
                                }
                            }
                        )
                    }
            ) {
                // Draw the image fitted with letterboxing.
                val (left, top, dispW, dispH) = fitRect(bitmap.width, bitmap.height, size.width, size.height)
                drawImage(
                    image = androidx.compose.ui.graphics.asImageBitmap(bitmap),
                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dispW.toInt(), dispH.toInt())
                )
                // Draw quad lines.
                val pts = corners.map { toScreen(it, bitmap, viewSize, density) }
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, InkGreenLight, style = Stroke(width = 3.dp.toPx()))
                // Draw handles.
                pts.forEach {
                    drawCircle(Color.White, radius = 12.dp.toPx(), center = it)
                    drawCircle(InkGreenLight, radius = 5.dp.toPx(), center = it)
                }
            }
        }

        Text(
            "拖动四个圆点框住文档，点右上角对勾确认",
            color = Color(0xFFB7E4D8),
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun fitRect(bw: Int, bh: Int, cw: Float, ch: Float): FloatArray {
    val scale = min(cw / bw, ch / bh)
    val dispW = bw * scale
    val dispH = bh * scale
    return floatArrayOf((cw - dispW) / 2f, (ch - dispH) / 2f, dispW, dispH)
}

private fun toScreen(pt: Offset, bmp: Bitmap, view: IntSize, density: androidx.compose.ui.unit.Density): Offset {
    val scale = min(view.width.toFloat() / bmp.width, view.height.toFloat() / bmp.height)
    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val left = (view.width - dispW) / 2f
    val top = (view.height - dispH) / 2f
    return Offset(left + pt.x * scale, top + pt.y * scale)
}

private fun toImage(pt: Offset, bmp: Bitmap, view: IntSize, density: androidx.compose.ui.unit.Density): Offset {
    val scale = min(view.width.toFloat() / bmp.width, view.height.toFloat() / bmp.height)
    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val left = (view.width - dispW) / 2f
    val top = (view.height - dispH) / 2f
    return Offset((pt.x - left) / scale, (pt.y - top) / scale)
}

private fun List<Offset>.toQuad(): Quad = Quad(
    this[0].x, this[0].y, this[1].x, this[1].y,
    this[2].x, this[2].y, this[3].x, this[3].y
)
