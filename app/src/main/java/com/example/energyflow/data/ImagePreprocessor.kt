package com.example.energyflow.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 图像预处理引擎 — 在送入 ML Kit OCR 前对 CameraX 捕获图像进行增强。
 *
 * ## 预处理管线
 * 1. YUV_420_888 → Bitmap（ARGB_8888）转换
 * 2. 对比度拉伸（直方图归一化）
 * 3. 自适应灰度归一化
 * 4. ROI 裁剪（取景框区域）
 * 5. 输出 InputImage 供 ML Kit 消费
 */
object ImagePreprocessor {

    private const val CONTRAST_CLIP_PERCENT = 2.0
    private const val TARGET_WIDTH = 720

    /**
     * 主处理入口：从 ImageProxy 生成预处理后的 InputImage。
     *
     * @param imageProxy CameraX 捕获的原始帧
     * @param roiRegion  可选 ROI 区域（相对于原图坐标），null = 整图
     */
    fun preprocess(
        imageProxy: ImageProxy,
        roiRegion: Rect? = null
    ): InputImage {
        val bitmap = imageProxy.toBitmap()
        val processed = applyPreprocessing(bitmap, roiRegion)
        return InputImage.fromBitmap(processed, imageProxy.imageInfo.rotationDegrees)
    }

    /**
     * YUV_420_888 → Bitmap 转换。
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val image = image ?: return fallbackBitmap()
        return when (format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
            ImageFormat.NV21 -> nv21ToBitmap(image)
            else -> fallbackBitmap()
        }
    }

    private fun yuv420ToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride
        val width = image.width
        val height = image.height

        var uvOffset = ySize
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uvIndex = row * uvRowStride + col * uvPixelStride
                nv21[uvOffset++] = vBuffer.get(uvIndex)
                nv21[uvOffset++] = uBuffer.get(uvIndex)
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
        val jpegData = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
    }

    private fun nv21ToBitmap(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val yuvImage = YuvImage(bytes, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
        val jpegData = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
    }

    private fun fallbackBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    /**
     * 预处理管线：分辨率缩放 → 对比度增强 → 灰度归一化 → ROI 裁剪。
     */
    private fun applyPreprocessing(bitmap: Bitmap, roiRegion: Rect?): Bitmap {
        val scaled = scaleToTargetWidth(bitmap)
        val contrasted = applyContrastStretching(scaled)
        val roi = roiRegion ?: Rect(0, 0, contrasted.width, contrasted.height)

        // 仅在 ROI 小于 80% 面积时才裁剪
        return if (roi.width() < contrasted.width * 0.8 ||
            roi.height() < contrasted.height * 0.8
        ) {
            Bitmap.createBitmap(
                contrasted,
                roi.left.coerceIn(0, contrasted.width - 1),
                roi.top.coerceIn(0, contrasted.height - 1),
                roi.width().coerceAtMost(contrasted.width - roi.left),
                roi.height().coerceAtMost(contrasted.height - roi.top)
            )
        } else {
            contrasted
        }
    }

    /**
     * 保持宽高比缩放到目标宽度（提高处理速度）。
     */
    private fun scaleToTargetWidth(source: Bitmap): Bitmap {
        if (source.width <= TARGET_WIDTH) return source
        val ratio = TARGET_WIDTH.toFloat() / source.width
        val newHeight = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, TARGET_WIDTH, newHeight, true)
    }

    /**
     * 对比度拉伸（直方图裁剪 + 线性归一化）。
     *
     * 先转为灰度，再计算像素值直方图，裁剪两端 [CONTRAST_CLIP_PERCENT] 的 outlier，
     * 将剩余像素线性映射到 [0, 255] 范围。
     */
    private fun applyContrastStretching(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 计算亮度直方图
        val histogram = IntArray(256)
        val grayValues = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = ((pixel shr 16 and 0xFF) * 0.299 +
                    (pixel shr 8 and 0xFF) * 0.587 +
                    (pixel and 0xFF) * 0.114).toInt().coerceIn(0, 255)
            grayValues[i] = gray
            histogram[gray]++
        }

        // 计算裁剪边界
        val totalPixels = pixels.size
        val clipCount = (totalPixels * CONTRAST_CLIP_PERCENT / 100.0).toInt()
        var accumulated = 0
        var minGray = 0
        while (minGray < 255 && accumulated < clipCount) {
            accumulated += histogram[minGray]
            minGray++
        }
        accumulated = 0
        var maxGray = 255
        while (maxGray > 0 && accumulated < clipCount) {
            accumulated += histogram[maxGray]
            maxGray--
        }

        val range = (maxGray - minGray).coerceAtLeast(1)

        // 应用线性映射 + 锐化边缘
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val g = grayValues[i]
            val mapped = ((g - minGray).coerceIn(0, range) * 255 / range).coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }
}
