package com.jiscan.app.scan

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Local hand-writing eraser. The user paints strokes; we fill those pixels by
 * diffusing colors from the surrounding area. Fully on-device, no library.
 */
object HandwritingEraser {

    /**
     * @param source original bitmap (will not be recycled)
     * @param strokes list of painted points in bitmap coordinates
     * @param brushRadius radius of each brush point in pixels
     * @return a new bitmap with the painted regions filled from neighbors
     */
    fun erase(source: Bitmap, strokes: List<Pair<Float, Float>>, brushRadius: Float): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)

        // 1. Build mask of painted pixels.
        val mask = BooleanArray(w * h)
        val r = max(1f, brushRadius).toInt()
        for ((sx, sy) in strokes) {
            val cx = sx.toInt()
            val cy = sy.toInt()
            for (dy in -r..r) {
                val y = cy + dy
                if (y < 0 || y >= h) continue
                for (dx in -r..r) {
                    val x = cx + dx
                    if (x < 0 || x >= w) continue
                    if (dx * dx + dy * dy <= r * r) mask[y * w + x] = true
                }
            }
        }

        if (!mask.any { it }) {
            out.setPixels(px, 0, w, 0, 0, w, h)
            return out
        }

        // 2. Seed boundary: for each masked pixel, find nearest unmasked color
        //    within a search window; use a light "paper" default if none found.
        val filled = IntArray(w * h)
        val search = max(12, r * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (!mask[i]) {
                    filled[i] = px[i]
                    continue
                }
                var best: Int? = null
                var bestDist = Int.MAX_VALUE
                var y0 = max(0, y - search); var y1 = min(h - 1, y + search)
                var x0 = max(0, x - search); var x1 = min(w - 1, x + search)
                // Prefer nearby pixels; sample on a grid for speed.
                var yy = y0
                while (yy <= y1) {
                    var xx = x0
                    while (xx <= x1) {
                        val j = yy * w + xx
                        if (!mask[j]) {
                            val d = (xx - x) * (xx - x) + (yy - y) * (yy - y)
                            if (d < bestDist) { bestDist = d; best = px[j] }
                            if (d == 1) break
                        }
                        xx += 2
                    }
                    yy += 2
                }
                filled[i] = best ?: Color.rgb(245, 243, 235)
            }
        }

        // 3. Smooth the filled region with a couple of passes so it blends.
        var current = filled
        repeat(2) {
            val next = current.copyOf()
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    if (!mask[i]) continue
                    val rs = Color.red(current[i - 1]) + Color.red(current[i + 1]) +
                            Color.red(current[i - w]) + Color.red(current[i + w])
                    val gs = Color.green(current[i - 1]) + Color.green(current[i + 1]) +
                            Color.green(current[i - w]) + Color.green(current[i + w])
                    val bs = Color.blue(current[i - 1]) + Color.blue(current[i + 1]) +
                            Color.blue(current[i - w]) + Color.blue(current[i + w])
                    next[i] = Color.rgb(rs / 4, gs / 4, bs / 4)
                }
            }
            current = next
        }

        out.setPixels(current, 0, w, 0, 0, w, h)
        return out
    }
}
