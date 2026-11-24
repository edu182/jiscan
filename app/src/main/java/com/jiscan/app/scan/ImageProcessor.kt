package com.jiscan.app.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ScanMode { DOCUMENT, BOOK, CARD }
enum class ColorFilterMode { COLOR, BLACK_WHITE }
enum class RectifyMode { AUTO, MANUAL, CURVE }

/** Four-corner quad. Order: topLeft, topRight, bottomRight, bottomLeft. */
data class Quad(val p1x: Float, val p1y: Float, val p2x: Float, val p2y: Float,
                val p3x: Float, val p3y: Float, val p4x: Float, val p4y: Float)

/**
 * Fully on-device document image processing. No network, no OpenCV dependency.
 * Added: multi-point edge sampling for warped/curved pages, brightness
 * normalization for creases, and tolerant handling of missing corners.
 */
object ImageProcessor {

    fun correctRotation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bitmap else rotate(bitmap, degrees)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /**
     * Multi-point edge detection. Instead of a single median per side, we sample
     * three bands per edge and take a robust percentile, which tolerates curved
     * edges, folds and a missing corner far better than a single sample.
     */
    fun detectQuad(source: Bitmap, mode: ScanMode): Quad {
        val w = source.width
        val h = source.height
        val scale = 400f / max(w, h)
        val sw = max(8, (w * scale).toInt())
        val sh = max(8, (h * scale).toInt())
        val small = Bitmap.createBitmap(source, 0, 0, w, h, Matrix().apply { setScale(scale, scale) }, true)
        try {
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            val gray = IntArray(sw * sh)
            for (i in px.indices) {
                gray[i] = (0.299f * Color.red(px[i]) + 0.587f * Color.green(px[i]) + 0.114f * Color.blue(px[i])).toInt()
            }

            val marginX = (sw * 0.05f).toInt()
            val marginY = (sh * 0.05f).toInt()
            val threshold = 16

            val lefts = ArrayList<Int>()
            val rights = ArrayList<Int>()
            val rows = sampleBands(sh, marginY, 3)
            for (y in rows) {
                var bL = -1; var vL = 0
                var bR = -1; var vR = 0
                for (x in marginX until sw / 2) {
                    val g = abs(gray[y * sw + x + 1] - gray[y * sw + x])
                    if (g > vL) { vL = g; bL = x }
                }
                for (x in sw / 2 until sw - marginX - 1) {
                    val g = abs(gray[y * sw + x + 1] - gray[y * sw + x])
                    if (g > vR) { vR = g; bR = x }
                }
                if (vL >= threshold) lefts.add(bL)
                if (vR >= threshold) rights.add(bR)
            }

            val tops = ArrayList<Int>()
            val bottoms = ArrayList<Int>()
            val cols = sampleBands(sw, marginX, 3)
            for (x in cols) {
                var bT = -1; var vT = 0
                var bB = -1; var vB = 0
                for (y in marginY until sh / 2) {
                    val g = abs(gray[(y + 1) * sw + x] - gray[y * sw + x])
                    if (g > vT) { vT = g; bT = y }
                }
                for (y in sh / 2 until sh - marginY - 1) {
                    val g = abs(gray[(y + 1) * sw + x] - gray[y * sw + x])
                    if (g > vB) { vB = g; bB = y }
                }
                if (vT >= threshold) tops.add(bT)
                if (vB >= threshold) bottoms.add(bB)
            }

            val minSamples = max(4, rows.size / 4)
            if (lefts.size < minSamples || rights.size < minSamples ||
                tops.size < minSamples || bottoms.size < minSamples) {
                return fullQuad(w, h)
            }

            val left = robustPercentile(lefts, 0.35f) / scale
            val right = (robustPercentile(rights, 0.65f) + 1) / scale
            val top = robustPercentile(tops, 0.35f) / scale
            val bottom = (robustPercentile(bottoms, 0.65f) + 1) / scale

            if (right - left < w * 0.35f || bottom - top < h * 0.30f ||
                left < -w * 0.02f || top < -h * 0.02f ||
                right > w * 1.02f || bottom > h * 1.02f) {
                return fullQuad(w, h)
            }

            val padX: Float
            val padY: Float
            when (mode) {
                ScanMode.BOOK -> { padX = w * 0.01f; padY = h * 0.01f }
                ScanMode.CARD -> { padX = (right - left) * 0.015f; padY = (bottom - top) * 0.015f }
                ScanMode.DOCUMENT -> { padX = w * 0.006f; padY = h * 0.006f }
            }
            val l = max(0f, left - padX); val r = min(w.toFloat(), right + padX)
            val t = max(0f, top - padY); val b = min(h.toFloat(), bottom + padY)

            val corner = min(r - l, b - t) * 0.02f
            return Quad(
                l + corner, t + corner, r - corner, t + corner,
                r - corner, b - corner, l + corner, b - corner
            )
        } finally {
            small.recycle()
        }
    }

    /** Reduces uneven lighting / fold shadows using a coarse background estimate. */
    fun normalizeLighting(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)

        val cell = 32
        val cols = (w + cell - 1) / cell
        val rowsN = (h + cell - 1) / cell
        val bg = FloatArray(cols * rowsN)
        val counts = IntArray(cols * rowsN)
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val ci = (y / cell) * cols + (x / cell)
                val c = px[y * w + x]
                val g = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
                bg[ci] += g
                counts[ci]++
            }
        }
        for (i in bg.indices) bg[i] = if (counts[i] > 0) bg[i] / counts[i] else 235f

        val target = 235f
        val result = IntArray(w * h)
        for (y in 0 until h) {
            val row = y / cell
            for (x in 0 until w) {
                val i = y * w + x
                val ci = row * cols + (x / cell)
                val c = px[i]
                val gain = (target / max(60f, bg[ci])).coerceIn(0.6f, 1.7f)
                val r = (Color.red(c) * gain).toInt().coerceIn(0, 255)
                val g2 = (Color.green(c) * gain).toInt().coerceIn(0, 255)
                val b = (Color.blue(c) * gain).toInt().coerceIn(0, 255)
                result[i] = Color.rgb(r, g2, b)
            }
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    fun fullQuad(w: Int, h: Int): Quad =
        Quad(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())

    /** Perspective transform mapping the detected quad onto a flat rectangle. */
    fun perspective(source: Bitmap, quad: Quad): Bitmap {
        val outW = max(abs(quad.p3x - quad.p4x), abs(quad.p2x - quad.p1x)).toInt().coerceAtLeast(1)
        val outH = max(abs(quad.p4y - quad.p1y), abs(quad.p3y - quad.p2y)).toInt().coerceAtLeast(1)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val src = floatArrayOf(quad.p1x, quad.p1y, quad.p2x, quad.p2y, quad.p3x, quad.p3y, quad.p4x, quad.p4y)
        val m = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(source, m, paint)
        return out
    }

    fun applyFilter(source: Bitmap, filter: ColorFilterMode): Bitmap {
        if (filter == ColorFilterMode.COLOR) return source
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val gray = ByteArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val g = (0.299f * Color.red(px[i]) + 0.587f * Color.green(px[i]) + 0.114f * Color.blue(px[i])).toInt()
            gray[i] = g.toByte()
            hist[g and 0xFF]++
        }
        val threshold = otsuThreshold(hist, w * h)
        val result = IntArray(w * h)
        for (i in gray.indices) {
            val v = if ((gray[i].toInt() and 0xFF) >= threshold) 255 else 0
            result[i] = Color.rgb(v, v, v)
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    private fun otsuThreshold(hist: IntArray, total: Int): Int {
        var sum = 0
        for (t in 0..255) sum += t * hist[t]
        var sumB = 0; var wB = 0; var maxVar = 0.0; var threshold = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB.toDouble()
            val mF = (sum - sumB) / wF.toDouble()
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    private fun sampleBands(size: Int, margin: Int, bands: Int): List<Int> {
        val result = ArrayList<Int>()
        val start = margin
        val end = size - margin - 1
        val span = end - start
        for (b in 0 until bands) {
            val center = start + (span * (b + 1) / (bands + 1))
            for (d in -1..1) {
                val v = center + d
                if (v in start..end) result.add(v)
            }
        }
        return result.distinct().sorted()
    }

    /** A percentile that ignores outliers (missing corners / folds). */
    private fun robustPercentile(values: List<Int>, p: Float): Float {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0f
        val idx = (sorted.size * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx].toFloat()
    }
}
