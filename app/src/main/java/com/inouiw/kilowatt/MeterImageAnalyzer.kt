package com.inouiw.kilowatt

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class MeterImageAnalyzer(
    context: Context,
    private val isLocked: () -> Boolean,
    private val onReadingDetected: (kWh: Double, rawText: String, hasFault: Boolean, faultKeyword: String?) -> Unit,
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter? = null

    init {
        val modelBuffer = loadModelFile(context, MODEL_FILENAME)
        if (modelBuffer != null) {
            try {
                interpreter = Interpreter(modelBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid TFLite model '$MODEL_FILENAME'; using 7-segment fallback decoder", e)
            }
        }
    }

    /** Releases the native TFLite interpreter. Call from the same thread that runs [analyze]. */
    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(ctx: Context, modelFilename: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = ctx.assets.openFd(modelFilename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model '$modelFilename' from assets; using 7-segment fallback decoder", e)
            null
        }
    }

    private fun classifyDigitsWithTFLite(croppedBitmap: Bitmap): Double? {
        val width = croppedBitmap.width
        val height = croppedBitmap.height
        if (width <= 0 || height <= 0) return null

        val grayPixels = IntArray(width * height)
        croppedBitmap.getPixels(grayPixels, 0, width, 0, 0, width, height)

        val normalizedLuma = FloatArray(width * height)
        for (i in grayPixels.indices) {
            val pixel = grayPixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            normalizedLuma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
        }

        // Horizontal projection to locate digit candidate slots
        val verticalProjection = IntArray(width)
        for (x in 0 until width) {
            var darkCount = 0
            for (y in 0 until height) {
                if (normalizedLuma[y * width + x] < 0.48f) {
                    darkCount++
                }
            }
            verticalProjection[x] = darkCount
        }

        val digitSegments = mutableListOf<Pair<Int, Int>>()
        var inSegment = false
        var segStart = 0

        for (x in 0 until width) {
            val isDark = verticalProjection[x] > (height * 0.08)
            if (isDark && !inSegment) {
                inSegment = true
                segStart = x
            } else if (!isDark && inSegment) {
                inSegment = false
                if (x - segStart >= 3) {
                    digitSegments.add(Pair(segStart, x))
                }
            }
        }

        val detectedDigits = StringBuilder()
        var hasDecimalPoint = false

        for ((start, end) in digitSegments) {
            val segWidth = end - start
            val slotW = (end - start).coerceAtLeast(1)

            // Slice slot bitmap
            val slotBitmap = try {
                Bitmap.createBitmap(croppedBitmap, start, 0, slotW, height)
            } catch (e: Exception) {
                null
            }

            if (slotBitmap != null) {
                val predictedDigit = predictDigit(slotBitmap)
                if (predictedDigit != null) {
                    detectedDigits.append(predictedDigit)
                } else if (segWidth <= 3 && !hasDecimalPoint) {
                    detectedDigits.append(".")
                    hasDecimalPoint = true
                }
            } else if (segWidth <= 3 && !hasDecimalPoint) {
                detectedDigits.append(".")
                hasDecimalPoint = true
            }
        }

        var parsedText = detectedDigits.toString()
        // Standard 5-digit displays show two decimal places (e.g. 22495 -> 224.95)
        // when the decimal point segment is too faint to be detected.
        if (!hasDecimalPoint && parsedText.length == 5) {
            parsedText = parsedText.substring(0, 3) + "." + parsedText.substring(3)
        }
        val numericVal = parsedText.toDoubleOrNull()
        if (numericVal != null && numericVal in 100.0..99999.0) {
            return numericVal
        }

        return null
    }

    private fun predictDigit(slotBitmap: Bitmap): Char? {
        val tflite = interpreter
        if (tflite != null) {
            try {
                val scaled = Bitmap.createScaledBitmap(slotBitmap, 28, 28, true)
                val inputBuffer = ByteBuffer.allocateDirect(1 * 28 * 28 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())

                val pixels = IntArray(28 * 28)
                scaled.getPixels(pixels, 0, 28, 0, 0, 28, 28)

                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b)
                    // Invert so dark LCD segments are high intensity
                    val invertedNorm = (255f - gray) / 255.0f
                    inputBuffer.putFloat(invertedNorm)
                }

                inputBuffer.rewind()
                val outputArray = Array(1) { FloatArray(10) }
                tflite.run(inputBuffer, outputArray)

                val probabilities = outputArray[0]
                var maxIdx = -1
                var maxProb = -1.0f
                for (i in probabilities.indices) {
                    if (probabilities[i] > maxProb) {
                        maxProb = probabilities[i]
                        maxIdx = i
                    }
                }

                if (maxIdx in 0..9 && maxProb > 0.35f) {
                    return ('0' + maxIdx)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback 7-segment feature decoder if TFLite model is uninitialized
        return fallback7SegmentDecoder(slotBitmap)
    }

    private fun fallback7SegmentDecoder(slotBitmap: Bitmap): Char? {
        val w = slotBitmap.width
        val h = slotBitmap.height
        if (w <= 0 || h <= 0) return null

        val pixels = IntArray(w * h)
        slotBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
        }

        val midY = h / 2
        val topY = h / 4
        val botY = (h * 3) / 4
        val midX = w / 2

        val topSeg = checkSeg(luma, w, midX, topY)
        val midSeg = checkSeg(luma, w, midX, midY)
        val botSeg = checkSeg(luma, w, midX, botY)

        val topLeftSeg = checkSeg(luma, w, 1, topY)
        val topRightSeg = checkSeg(luma, w, (w - 2).coerceAtLeast(0), topY)
        val botLeftSeg = checkSeg(luma, w, 1, botY)
        val botRightSeg = checkSeg(luma, w, (w - 2).coerceAtLeast(0), botY)

        return when {
            topSeg && topLeftSeg && topRightSeg && !midSeg && botLeftSeg && botRightSeg && botSeg -> '0'
            !topSeg && !topLeftSeg && topRightSeg && !midSeg && !botLeftSeg && botRightSeg && !botSeg -> '1'
            topSeg && !topLeftSeg && topRightSeg && midSeg && botLeftSeg && !botRightSeg && botSeg -> '2'
            topSeg && !topLeftSeg && topRightSeg && midSeg && !botLeftSeg && botRightSeg && botSeg -> '3'
            !topSeg && topLeftSeg && topRightSeg && midSeg && !botLeftSeg && botRightSeg && !botSeg -> '4'
            topSeg && topLeftSeg && !topRightSeg && midSeg && !botLeftSeg && botRightSeg && botSeg -> '5'
            topSeg && topLeftSeg && !topRightSeg && midSeg && botLeftSeg && botRightSeg && botSeg -> '6'
            topSeg && !topLeftSeg && topRightSeg && !midSeg && !botLeftSeg && botRightSeg && !botSeg -> '7'
            topSeg && topLeftSeg && topRightSeg && midSeg && botLeftSeg && botRightSeg && botSeg -> '8'
            topSeg && topLeftSeg && topRightSeg && midSeg && !botLeftSeg && botRightSeg && botSeg -> '9'
            else -> null
        }
    }

    private fun checkSeg(luma: FloatArray, width: Int, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= width) return false
        val idx = y * width + x
        return idx < luma.size && luma[idx] < 0.5f
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isLocked()) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            // Center reticle area crop (middle 40% width, middle 25% height)
            val cropW = (bitmap.width * 0.40f).toInt().coerceAtLeast(1)
            val cropH = (bitmap.height * 0.25f).toInt().coerceAtLeast(1)
            val cropX = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
            val cropY = ((bitmap.height - cropH) / 2).coerceAtLeast(0)

            val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)

            val detectedValue = classifyDigitsWithTFLite(croppedBitmap)

            if (detectedValue != null && !isLocked()) {
                val rawText = String.format(Locale.US, "%.2f", detectedValue)
                onReadingDetected(detectedValue, rawText, false, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Guaranteed imageProxy close in finally block
            imageProxy.close()
        }
    }

    private companion object {
        const val TAG = "MeterImageAnalyzer"
        const val MODEL_FILENAME = "digit_classifier.tflite"
    }
}
