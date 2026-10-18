package com.inouiw.kilowatt.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayDeque

// Reads the LCD with TensorFlow Lite:
// 1. find the green LCD in the reticle crop, 2. split it into digit cells,
// 3. classify each cell with the 28x28 digit model, 4. place the decimal point where the dot is.
// A value is only suggested when several frames agree. The user still confirms it.
class TfLiteMeterReader(context: Context, private val onReading: (kWh: Double, raw: String) -> Unit) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter? = loadModel(context)
    private val votes = ArrayDeque<String>()
    private var lastFrameMillis = 0L

    @Volatile
    private var suggested = false

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (suggested || interpreter == null || now - lastFrameMillis < FRAME_INTERVAL_MILLIS) {
            imageProxy.close()
            return
        }
        lastFrameMillis = now

        val crop = try {
            reticleCrop(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            null
        }
        imageProxy.close()
        if (crop == null) return

        val digits = readDigits(crop) ?: return
        votes.addLast(digits)
        if (votes.size > VOTE_WINDOW) votes.removeFirst()
        val (value, count) = votes.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return
        Log.d(TAG, "read=$digits best=$value votes=$count/${votes.size}")
        if (count >= REQUIRED_VOTES) {
            suggested = true
            value.toDoubleOrNull()?.let { onReading(it, value) }
        }
    }

    fun close() {
        interpreter?.close()
    }

    /** Lets the same reader scan again after a reading has been suggested. */
    fun resetForNewScan() {
        suggested = false
        votes.clear()
    }

    private fun loadModel(context: Context): Interpreter? = try {
        val afd = context.assets.openFd(MODEL_FILE)
        val buffer: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        Interpreter(buffer)
    } catch (e: Exception) {
        Log.e(TAG, "Could not load $MODEL_FILE", e)
        null
    }

    // Same band as the reticle on screen
    private fun reticleCrop(imageProxy: ImageProxy): Bitmap {
        val raw = imageProxy.toBitmap()
        val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        val cropX = (upright.width * 0.10f).toInt()
        val cropY = (upright.height * 0.33f).toInt()
        val cropW = (upright.width * 0.80f).toInt().coerceAtLeast(1)
        val cropH = (upright.height * 0.25f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(upright, cropX, cropY, cropW, cropH)
    }

    // Returns the digits read from the LCD, e.g. "015794.8", or null if no LCD is found.
    private fun readDigits(crop: Bitmap): String? {
        val model = interpreter ?: return null

        // 1. Find the green LCD by colour
        val w = crop.width
        val h = crop.height
        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1
        var greenCount = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = pixels[y * w + x]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (g > 110 && g > r + 40 && g > b + 40) {
                greenCount++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (greenCount < w * h * 0.05f || maxX <= minX || maxY <= minY) return null

        val lcdW = maxX - minX + 1
        val lcdH = maxY - minY + 1
        val lcd = Bitmap.createBitmap(crop, minX, minY, lcdW, lcdH)
        val lcdPixels = IntArray(lcdW * lcdH)
        lcd.getPixels(lcdPixels, 0, lcdW, 0, 0, lcdW, lcdH)
        val lum = FloatArray(lcdPixels.size) { i ->
            val p = lcdPixels[i]
            0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }

        // 2. Ink = dark pixels on the green LCD
        val meanLum = lum.average().toFloat()
        val ink = BooleanArray(lum.size) { lum[it] < meanLum * 0.75f }

        val columnInk = IntArray(lcdW) { x -> (0 until lcdH).count { y -> ink[y * lcdW + x] } }
        val minColumnInk = maxOf(2, (lcdH * 0.08f).toInt())

        val segments = mutableListOf<IntRange>()
        var start = -1
        for (x in 0..lcdW) {
            val on = x < lcdW && columnInk[x] >= minColumnInk
            if (on && start < 0) start = x
            if (!on && start >= 0) {
                if (x - start >= 2) segments.add(start until x)
                start = -1
            }
        }
        if (segments.isEmpty()) return null

        // Digit strokes split into pieces with small gaps. Join pieces that are close together.
        val maxGap = (lcdH * 0.12f).toInt().coerceAtLeast(2)
        val merged = mutableListOf<IntRange>()
        for (seg in segments) {
            val last = merged.lastOrNull()
            if (last != null && seg.first - last.last <= maxGap) {
                merged[merged.size - 1] = last.first..seg.last
            } else {
                merged.add(seg)
            }
        }
        segments.clear()
        segments.addAll(merged)

        // Vertical extent of ink in each segment tells digits (tall) from the decimal dot (small, low)
        val extents = segments.map { seg ->
            var top = lcdH
            var bottom = -1
            for (y in 0 until lcdH) for (x in seg) if (ink[y * lcdW + x]) {
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
            Triple(seg, top, bottom)
        }.filter { it.third >= 0 }

        val tall = extents.filter { (it.third - it.second) >= lcdH * 0.45f }
        if (tall.isEmpty()) return null
        val tallWidths = tall.map { it.first.last - it.first.first + 1 }.sorted()
        val medianWidth = tallWidths[tallWidths.size / 2]

        val out = StringBuilder()
        for ((seg, top, bottom) in extents) {
            val height = bottom - top
            val isTall = height >= lcdH * 0.45f
            val isDot = height < lcdH * 0.25f && top > lcdH * 0.6f && (seg.last - seg.first + 1) <= medianWidth * 0.5f
            when {
                isTall -> {
                    val digit = classify(model, lcd, lcdPixels, lum, meanLum, seg, top, bottom, lcdW) ?: continue
                    out.append(digit)
                }
                isDot && out.isNotEmpty() -> out.append('.')
            }
        }
        return if (out.count { it.isDigit() } >= 3) out.toString() else null
    }

    // Classifies one digit cell with the 28x28 model. Input: 1 = dark LCD segment, matching training.
    private fun classify(
        model: Interpreter,
        lcd: Bitmap,
        lcdPixels: IntArray,
        lum: FloatArray,
        meanLum: Float,
        seg: IntRange,
        top: Int,
        bottom: Int,
        lcdW: Int,
    ): Char? {
        val cellW = seg.last - seg.first + 1
        val cellH = bottom - top + 1
        val cell = Bitmap.createBitmap(lcd, seg.first, top, cellW, cellH)
        val scaled = Bitmap.createScaledBitmap(cell, 28, 28, true)
        val px = IntArray(28 * 28)
        scaled.getPixels(px, 0, 28, 0, 0, 28, 28)
        val gray = FloatArray(px.size) { i ->
            val p = px[i]
            0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }
        val minG = gray.min()
        val maxG = gray.max()
        val range = (maxG - minG).coerceAtLeast(1f)

        val input = ByteBuffer.allocateDirect(28 * 28 * 4).order(ByteOrder.nativeOrder())
        for (g in gray) input.putFloat((maxG - g) / range)
        input.rewind()

        val output = Array(1) { FloatArray(10) }
        model.run(input, output)
        val probs = output[0]
        var best = 0
        for (i in probs.indices) if (probs[i] > probs[best]) best = i
        return if (probs[best] >= MIN_CONFIDENCE) ('0' + best) else null
    }

    companion object {
        const val TAG = "TfLiteMeterReader"
        const val MODEL_FILE = "digit_classifier.tflite"
        const val FRAME_INTERVAL_MILLIS = 300L
        const val VOTE_WINDOW = 6
        const val REQUIRED_VOTES = 3
        const val MIN_CONFIDENCE = 0.5f
    }
}
