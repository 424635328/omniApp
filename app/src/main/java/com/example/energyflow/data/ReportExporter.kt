package com.example.energyflow.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
        try {
            writeToMediaStore(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(content: ReportContent): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = 3f

        // Colors — matching Color.kt theme
        val bgColor = 0xFF0A0C14.toInt()       // BackgroundDark
        val cardColor = 0xFF1A1E30.toInt()     // SurfaceVariant
        val accentColor = 0xFF0098FF.toInt()   // ElectricStart
        val accentGreen = 0xFF00D68F.toInt()   // SuccessGreen
        val textPrimary = 0xFFE2E8F0.toInt()
        val textSecondary = 0xFF94A3B8.toInt()
        val textTertiary = 0xFF64748B.toInt()
        val peakColor = 0xFFFF8800.toInt()     // StaticPeakColor
        val valleyColor = 0xFF8866DD.toInt()   // StaticValleyColor
        val flatColor = 0xFF30364B.toInt()
        val dividerColor = 0x18FFFFFF.toInt()

        // ── Helpers ──
        fun makePaint(color: Int, size: Float, bold: Boolean = false, subpixel: Boolean = true): Paint {
            return Paint(Paint.ANTI_ALIAS_FLAG or (if (subpixel) Paint.SUBPIXEL_TEXT_FLAG else 0)).apply {
                this.color = color
                textSize = size * density
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        fun drawTextCentered(text: String, paint: Paint, yPos: Float) {
            val x = (WIDTH - paint.measureText(text)) / 2f
            canvas.drawText(text, x, yPos, paint)
        }

        fun drawDivider(yPos: Float, margin: Float = 32f * density) {
            val divPaint = makePaint(dividerColor, 1f)
            canvas.drawLine(PADDING + margin, yPos, WIDTH - PADDING - margin, yPos, divPaint)
        }

        fun drawCardShadow(top: Float, cardHeight: Float) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22000000.toInt()
                maskFilter = BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL)
            }
            val offset = 4f * density
            canvas.drawRoundRect(
                RectF(PADDING + offset, top + offset, WIDTH - PADDING + offset, top + cardHeight + offset),
                CARD_RADIUS * density, CARD_RADIUS * density, shadowPaint
            )
        }

        fun drawCardBg(top: Float, cardHeight: Float) {
            val cardPaint = makePaint(cardColor, 1f).apply { isAntiAlias = true }
            canvas.drawRoundRect(
                RectF(PADDING, top, WIDTH - PADDING, top + cardHeight),
                CARD_RADIUS * density, CARD_RADIUS * density, cardPaint
            )
        }

        // Background
        canvas.drawColor(bgColor)

        // Subtle dot grid pattern
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x08FFFFFF.toInt() }
        val gridSpacing = 24f * density
        val dotRadius = 1.5f * density
        var gx = 0f
        while (gx < WIDTH) {
            var gy = 0f
            while (gy < HEIGHT) {
                canvas.drawCircle(gx, gy, dotRadius, dotPaint)
                gy += gridSpacing
            }
            gx += gridSpacing
        }

        // ── Gradient header ──
        val headerGradient = LinearGradient(
            0f, 0f, WIDTH.toFloat(), 300f * density,
            intArrayOf(0x440098FF.toInt(), 0x0D0098FF.toInt(), bgColor),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 300f * density, Paint().apply { shader = headerGradient })

        // ── Title paints ──
        val titlePaint = makePaint(0xFFFFFFFF.toInt(), 48f, bold = true)
        val subtitlePaint = makePaint(textSecondary, 28f)
        val valuePaint = makePaint(accentColor, 56f, bold = true)
        val labelPaint = makePaint(textTertiary, 24f)
        val greenValuePaint = makePaint(accentGreen, 48f, bold = true)
        val sectionPaint = makePaint(textPrimary, 30f, bold = true)
        val bodyPaint = makePaint(textPrimary, 32f)
        val smallPaint = makePaint(textSecondary, 22f)
        val kpiCostPaint = makePaint(accentGreen, 52f, bold = true)

        var y = PADDING

        // ── Header ──
        drawTextCentered("⚡ 能耗报告", titlePaint, y)
        y += 70f * density
        drawTextCentered(content.period, subtitlePaint, y)
        y += 55f * density

        // ── Card 1: Total KPI ──
        val card1ContentH = 210f * density
        val card1H = card1ContentH + 64f * density
        drawCardShadow(y, card1H)
        drawCardBg(y, card1H)
        var cy = y + 36f * density
        drawTextCentered("总用电量", labelPaint, cy)
        cy += 48f * density
        drawTextCentered("%.1f kWh".format(content.totalKwh), valuePaint, cy)
        cy += 64f * density
        drawTextCentered("总费用 ¥%.2f".format(content.totalCost), kpiCostPaint, cy)
        cy += 54f * density
        drawTextCentered("CO₂ %.1f kg".format(content.co2Kg), bodyPaint, cy)
        y += card1H + 24f * density

        // ── Card 2: Peak/Valley stacked bar ──
        val totalPv = content.peakKwh + content.valleyKwh + content.flatKwh
        val card2ContentH: Float
        if (totalPv > 0) {
            card2ContentH = 175f * density
        } else {
            card2ContentH = 100f * density
        }
        val card2H = card2ContentH + 64f * density
        drawCardShadow(y, card2H)
        drawCardBg(y, card2H)
        cy = y + 36f * density

        if (totalPv > 0) {
            val peakPct = (content.peakKwh / totalPv * 100).roundToInt()
            val valleyPct = (content.valleyKwh / totalPv * 100).roundToInt()
            val flatPct = (content.flatKwh / totalPv * 100).roundToInt()

            drawTextCentered("峰谷平占比", sectionPaint, cy)
            cy += 50f * density

            // Stacked bar
            val barLeft = (WIDTH * 0.13f)
            val barRight = (WIDTH * 0.87f)
            val barWidth = barRight - barLeft
            val barHeight = 40f * density
            val cornerR = 12f * density

            // Peak segment
            if (peakPct > 0) {
                val peakW = barWidth * peakPct / 100f
                val peakPaint = makePaint(peakColor, 1f)
                peakPaint.isAntiAlias = true
                canvas.drawRoundRect(RectF(barLeft, cy, barLeft + peakW, cy + barHeight), cornerR, 0f, peakPaint)
                // Overlap correction: draw left corners only
                canvas.drawRect(RectF(barLeft + cornerR, cy, barLeft + peakW, cy + barHeight), peakPaint)
            }
            // Flat segment
            if (flatPct > 0) {
                val flatLeft = barLeft + barWidth * peakPct / 100f
                val flatW = barWidth * flatPct / 100f
                val flatPaint = makePaint(flatColor, 1f)
                flatPaint.isAntiAlias = true
                canvas.drawRect(RectF(flatLeft, cy, flatLeft + flatW, cy + barHeight), flatPaint)
            }
            // Valley segment
            if (valleyPct > 0) {
                val valleyLeft = barLeft + barWidth * (peakPct + flatPct) / 100f
                val valleyW = barWidth * valleyPct / 100f
                val valleyPaint = makePaint(valleyColor, 1f)
                valleyPaint.isAntiAlias = true
                canvas.drawRoundRect(RectF(valleyLeft, cy, valleyLeft + valleyW, cy + barHeight), 0f, cornerR, valleyPaint)
                // Overlap correction: draw right side flat
                canvas.drawRect(RectF(valleyLeft, cy, valleyLeft + valleyW - cornerR, cy + barHeight), valleyPaint)
            }

            cy += barHeight + 32f * density

            // Legend
            val legendY = cy
            val lx = barLeft
            val dotR = 8f * density
            // Peak dot + label
            val dotPaint = makePaint(peakColor, 1f)
            dotPaint.isAntiAlias = true
            canvas.drawCircle(lx + dotR, legendY - 6f * density, dotR, dotPaint)
            canvas.drawText("峰 %dkWh (%d%%)".format(content.peakKwh.toInt(), peakPct), lx + 24f * density, legendY, smallPaint)

            // Valley dot + label
            val valleyDotX = WIDTH / 2f
            val valleyDotPaint = makePaint(valleyColor, 1f)
            valleyDotPaint.isAntiAlias = true
            canvas.drawCircle(valleyDotX + dotR, legendY - 6f * density, dotR, valleyDotPaint)
            canvas.drawText("谷 %dkWh (%d%%)".format(content.valleyKwh.toInt(), valleyPct), valleyDotX + 24f * density, legendY, smallPaint)

            // Flat dot + label (if exists)
            if (flatPct > 0) {
                val flatDotX = lx + barWidth * 0.55f
                val flatDotLPaint = makePaint(flatColor, 1f)
                flatDotLPaint.isAntiAlias = true
                canvas.drawCircle(flatDotX + dotR, legendY - 6f * density, dotR, flatDotLPaint)
                canvas.drawText("平 %dkWh (%d%%)".format(content.flatKwh.toInt(), flatPct), flatDotX + 24f * density, legendY, smallPaint)
            }
        } else {
            drawTextCentered("暂无峰谷数据", labelPaint, cy)
        }
        y += card2H + 24f * density

        // ── Card 3: Carbon footprint ──
        val card3ContentH = 115f * density
        val card3H = card3ContentH + 64f * density
        drawCardShadow(y, card3H)
        drawCardBg(y, card3H)
        cy = y + 36f * density
        drawTextCentered("🌳 碳足迹 & 树木", sectionPaint, cy)
        cy += 50f * density
        drawTextCentered("等效植树 %.1f 棵 / %d 天".format(content.co2Kg / 20.0, content.treeDays), greenValuePaint, cy)
        y += card3H + 24f * density

        // ── Card 4: Badges ──
        val badgeCount = content.badges.size.coerceAtLeast(1)
        val card4ContentH = 50f * density + badgeCount * 42f * density
        val card4H = card4ContentH + 64f * density
        drawCardShadow(y, card4H)
        drawCardBg(y, card4H)
        cy = y + 36f * density
        drawTextCentered("🏅 成就徽章", sectionPaint, cy)
        cy += 50f * density
        content.badges.forEach { badge ->
            val badgePaint = makePaint(accentGreen, 28f, bold = true)
            val badgeW = badgePaint.measureText(badge)
            val badgeX = (WIDTH - badgeW) / 2f
            canvas.drawText(badge, badgeX, cy, badgePaint)
            cy += 42f * density
        }
        y += card4H + 24f * density

        // ── Record count ──
        val recText = "📊 本月记录 ${content.recordCount} 条"
        val recPaint = makePaint(textSecondary, 24f)
        drawTextCentered(recText, recPaint, y)
        y += 44f * density

        // ── Divider before footer ──
        drawDivider(y, 60f * density)
        y += 28f * density

        // ── Footer ──
        val footerPaint = makePaint(textTertiary, 22f)
        drawTextCentered("Energy Flow · 你的能耗小助手", footerPaint, y)
        y += 32f * density
        drawTextCentered("💚 感谢你为节能减排做出的贡献！", footerPaint, y)

        return bitmap
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
