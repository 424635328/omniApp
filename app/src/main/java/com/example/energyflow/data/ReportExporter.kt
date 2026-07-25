package com.example.energyflow.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Renders a summary report card as a PNG bitmap using Android Canvas + Paint,
 * then writes it to MediaStore. No Compose dependency.
 */
object ReportExporter {

    private const val WIDTH = 1080   // px
    private const val HEIGHT = 1920  // px
    private const val PADDING = 60f
    private const val CARD_RADIUS = 24f

    data class ReportContent(
        val period: String,          // e.g. "2024年1月"
        val totalKwh: Double,
        val totalCost: Double,
        val co2Kg: Double,
        val peakKwh: Double,
        val valleyKwh: Double,
        val flatKwh: Double,
        val treeDays: Int,
        val badges: List<String>,
        val previousCost: Double?,
        val tips: List<String>,
        val recordCount: Int
    )

    suspend fun export(context: Context, content: ReportContent): Uri? = withContext(Dispatchers.IO) {
        val bitmap = render(content)
        val uri = writeToMediaStore(context, bitmap)
        bitmap.recycle()
        uri
    }

    private fun render(content: ReportContent): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = 3f

        // Colors
        val bgColor = 0xFF0C0E14.toInt()
        val cardColor = 0xFF1F242F.toInt()
        val accentColor = 0xFF00A3FF.toInt()
        val accentGreen = 0xFF00E676.toInt()
        val textPrimary = 0xFFE2E8F0.toInt()
        val textSecondary = 0xFF94A3B8.toInt()
        val textTertiary = 0xFF64748B.toInt()
        val peakColor = 0xFFFF8800.toInt()
        val valleyColor = 0xFF8866DD.toInt()
        val flatColor = 0xFF30364B.toInt()

        // Background
        canvas.drawColor(bgColor)

        var y = PADDING

        // Title
        val titlePaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 48f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitlePaint = Paint().apply {
            color = textSecondary
            textSize = 28f * density
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = textPrimary
            textSize = 32f * density
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = accentColor
            textSize = 56f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val labelPaint = Paint().apply {
            color = textTertiary
            textSize = 24f * density
            isAntiAlias = true
        }
        val greenValuePaint = Paint().apply {
            color = accentGreen
            textSize = 48f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val sectionPaint = Paint().apply {
            color = textPrimary
            textSize = 30f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        fun drawTextCentered(text: String, paint: Paint, yPos: Float) {
            val x = (WIDTH - paint.measureText(text)) / 2f
            canvas.drawText(text, x, yPos, paint)
        }

        // ── Header ──
        drawTextCentered("⚡ 能耗报告", titlePaint, y)
        y += 70f * density
        drawTextCentered(content.period, subtitlePaint, y)
        y += 50f * density

        // ── Card: Total KPI ──
        y = drawCard(canvas, y, cardColor, density) {
            drawTextCentered("总用电量", labelPaint, it)
            val valueY = it + 50f * density
            drawTextCentered("%.1f kWh".format(content.totalKwh), valuePaint, valueY)

            val costY = valueY + 60f * density
            drawTextCentered("总费用 ¥%.2f".format(content.totalCost), greenValuePaint, costY)

            val co2Y = costY + 50f * density
            drawTextCentered("CO₂ %.1f kg".format(content.co2Kg), bodyPaint, co2Y)
        }

        y += 30f * density
        y = drawCard(canvas, y, cardColor, density) {
            val totalPv = content.peakKwh + content.valleyKwh + content.flatKwh
            if (totalPv > 0) {
                val peakPct = (content.peakKwh / totalPv * 100).roundToInt()
                val valleyPct = (content.valleyKwh / totalPv * 100).roundToInt()
                val flatPct = (content.flatKwh / totalPv * 100).roundToInt()

                drawTextCentered("峰谷平占比", sectionPaint, it)
                val barY = it + 50f * density

                // Draw stacked bar
                val barLeft = (WIDTH * 0.15f)
                val barRight = (WIDTH * 0.85f)
                val barWidth = barRight - barLeft
                val barHeight = 36f * density
                val barTop = barY
                val barBottom = barTop + barHeight

                if (peakPct > 0) {
                    val peakW = barWidth * peakPct / 100f
                    canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + peakW, barBottom), 18f * density, 18f * density, Paint().apply { color = peakColor })
                }
                if (flatPct > 0) {
                    val flatW = barWidth * flatPct / 100f
                    val flatLeft = barLeft + barWidth * peakPct / 100f
                    canvas.drawRoundRect(RectF(flatLeft, barTop, flatLeft + flatW, barBottom), 0f, 0f, Paint().apply { color = flatColor })
                }
                if (valleyPct > 0) {
                    val valleyW = barWidth * valleyPct / 100f
                    val valleyLeft = barLeft + barWidth * (peakPct + flatPct) / 100f
                    canvas.drawRoundRect(RectF(valleyLeft, barTop, valleyLeft + valleyW, barBottom), 0f, 18f * density, Paint().apply { color = valleyColor })
                }

                val legendY = barBottom + 40f * density
                val legendPaint = Paint().apply { color = textSecondary; textSize = 22f * density; isAntiAlias = true }
                canvas.drawText("峰 ${content.peakKwh.toInt()}kWh ($peakPct%)", barLeft, legendY, legendPaint)
                canvas.drawText("谷 ${content.valleyKwh.toInt()}kWh ($valleyPct%)", WIDTH / 2f, legendY, legendPaint)
            } else {
                drawTextCentered("暂无峰谷数据", labelPaint, it)
            }
        }

        y += 30f * density
        y = drawCard(canvas, y, cardColor, density) {
            drawTextCentered("🌳 碳足迹 & 树木", sectionPaint, it)
            val treeY = it + 50f * density
            drawTextCentered("等效植树 %.1f 棵 / %d 天".format(content.co2Kg / 20.0, content.treeDays), greenValuePaint, treeY)
        }

        y += 30f * density
        y = drawCard(canvas, y, cardColor, density) {
            drawTextCentered("🏅 成就徽章", sectionPaint, it)
            var badgeY = it + 50f * density
            content.badges.forEach { badge ->
                canvas.drawText(badge, WIDTH / 2f - 150f, badgeY, Paint().apply {
                    color = accentGreen
                    textSize = 28f * density
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                badgeY += 38f * density
            }
        }

        // ── Footer ──
        val footerPaint = Paint().apply {
            color = textTertiary
            textSize = 22f * density
            isAntiAlias = true
        }
        canvas.drawText("Energy Flow · 你的能耗小助手", (WIDTH - footerPaint.measureText("Energy Flow · 你的能耗小助手")) / 2f, HEIGHT - PADDING, footerPaint)

        return bitmap
    }

    private inline fun drawCard(canvas: Canvas, top: Float, bgColor: Int, density: Float, block: (Float) -> Unit): Float {
        val cardPaint = Paint().apply {
            color = bgColor
            isAntiAlias = true
        }
        val cardRect = RectF(PADDING, top, WIDTH - PADDING, top + 180f * density)
        canvas.drawRoundRect(cardRect, CARD_RADIUS * density, CARD_RADIUS * density, cardPaint)
        val contentTop = top + 40f * density
        block(contentTop)
        return cardRect.bottom
    }

    private fun writeToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "EnergyFlow_Report_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EnergyFlow")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
