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
    private const val HEIGHT = 2400  // px — taller to accommodate water/gas cards
    private const val PADDING = 52f
    private const val CARD_RADIUS = 28f

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
        val recordCount: Int,
        val waterTons: Double = 0.0,
        val waterCost: Double = 0.0,
        val gasM3: Double = 0.0,
        val gasCost: Double = 0.0,
        val isDarkTheme: Boolean = true  // 新增: 支持亮/暗主题
    )

    suspend fun export(context: Context, content: ReportContent): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = render(content)
            try {
                writeToMediaStore(context, bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun render(content: ReportContent): Bitmap {
        val d = 3f // density multiplier

        // ── 预计算内容高度 ──
        var estimatedHeight = 0f
        estimatedHeight += 72f * d + 50f * d  // Header
        estimatedHeight += 240f * d + 48f * d + 16f * d  // KPI card
        if (content.waterTons > 0 || content.gasM3 > 0) {
            val utilityRows = if (content.waterTons > 0 && content.gasM3 > 0) 3 else 2
            estimatedHeight += (60f + utilityRows * 56f) * d + 48f * d + 16f * d
        }
        if (content.peakKwh + content.valleyKwh + content.flatKwh > 0) {
            estimatedHeight += 170f * d + 48f * d + 16f * d
        }
        if (content.badges.isNotEmpty()) {
            estimatedHeight += (40f + content.badges.size * 36f) * d + 48f * d + 16f * d
        }
        if (content.previousCost != null && content.previousCost > 0) {
            estimatedHeight += 80f * d + 48f * d + 16f * d
        }
        if (content.tips.isNotEmpty()) {
            estimatedHeight += (40f + content.tips.size * 32f) * d + 48f * d + 16f * d
        }
        estimatedHeight += 40f * d + 24f * d + 28f * d + PADDING * 2  // Footer + padding

        val height = estimatedHeight.roundToInt().coerceAtLeast(HEIGHT)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Colors — 根据主题选择色板
        val bg: Int
        val card: Int
        val accent: Int
        val green: Int
        val t1: Int   // text primary
        val t2: Int   // text secondary
        val t3: Int   // text tertiary
        val peak: Int
        val valley: Int
        val flat: Int
        val water: Int
        val gas: Int
        val div: Int
        val red: Int

        if (content.isDarkTheme) {
            // Obsidian 暗色主题
            bg = 0xFF0A0C14.toInt()
            card = 0xFF1A1E30.toInt()
            accent = 0xFF00A8FF.toInt()
            green = 0xFF00DD99.toInt()
            t1 = 0xFFE2E8F0.toInt()
            t2 = 0xFF94A3B8.toInt()
            t3 = 0xFF64748B.toInt()
            peak = 0xFFFF9922.toInt()
            valley = 0xFF9977EE.toInt()
            flat = 0xFF30364B.toInt()
            water = 0xFF00DDBB.toInt()
            gas = 0xFFFF8844.toInt()
            div = 0x18FFFFFF.toInt()
            red = 0xFFFF4466.toInt()
        } else {
            // Pearl 亮色主题
            bg = 0xFFF5F6FA.toInt()
            card = 0xFFFFFFFF.toInt()
            accent = 0xFF0058DD.toInt()
            green = 0xFF00AA77.toInt()
            t1 = 0xFF0F172A.toInt()
            t2 = 0xFF475569.toInt()
            t3 = 0xFF718096.toInt()
            peak = 0xFFDD7700.toInt()
            valley = 0xFF7755CC.toInt()
            flat = 0xFFD8DCE6.toInt()
            water = 0xFF009988.toInt()
            gas = 0xFFDD6622.toInt()
            div = 0x18000000.toInt()
            red = 0xFFDD2244.toInt()
        }

        // ── Paint factory ──
        fun paint(color: Int, sp: Float, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color; textSize = sp * d
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

        fun centered(text: String, p: Paint, y: Float) {
            canvas.drawText(text, (WIDTH - p.measureText(text)) / 2f, y, p)
        }

        fun drawCard(top: Float, h: Float) {
            // Outer shadow (depth)
            val shadowOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (content.isDarkTheme) 0x30000000.toInt() else 0x20000000.toInt()
                maskFilter = BlurMaskFilter(12f * d, BlurMaskFilter.Blur.NORMAL)
            }
            val r = CARD_RADIUS * d
            val rect = RectF(PADDING, top, WIDTH - PADDING, top + h)
            canvas.drawRoundRect(RectF(rect).apply { offset(4f * d, 4f * d) }, r, r, shadowOuter)
            // Inner shadow (subtle)
            val shadowInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (content.isDarkTheme) 0x15000000.toInt() else 0x10000000.toInt()
                maskFilter = BlurMaskFilter(6f * d, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(RectF(rect).apply { offset(1.5f * d, 1.5f * d) }, r, r, shadowInner)
            // Card background
            canvas.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            // Top edge highlight
            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (content.isDarkTheme) 0x0AFFFFFF.toInt() else 0x0A000000.toInt()
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            canvas.drawRoundRect(RectF(rect).apply { bottom = top + 1.5f * d }, r, r, highlight)
        }

        // ── Background + gradient ──
        canvas.drawColor(bg)
        val headerGrad = LinearGradient(
            0f, 0f, WIDTH.toFloat(), 320f * d,
            if (content.isDarkTheme) {
                intArrayOf(0x5500A8FF.toInt(), 0x1800A8FF.toInt(), bg)
            } else {
                intArrayOf(0x330058DD.toInt(), 0x110058DD.toInt(), bg)
            },
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 320f * d, Paint().apply { shader = headerGrad })

        // Subtle grid dots
        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (content.isDarkTheme) 0x06FFFFFF.toInt() else 0x06000000.toInt()
        }
        val gs = 28f * d
        var gx = 0f
        while (gx < WIDTH) {
            var gy = 0f
            while (gy < HEIGHT) { canvas.drawCircle(gx, gy, 1.2f * d, dotP); gy += gs }
            gx += gs
        }

        // ── Paints ──
        val titleP = paint(if (content.isDarkTheme) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt(), 44f, bold = true)
        val subP = paint(t2, 26f)
        val kpiP = paint(accent, 54f, bold = true)
        val costP = paint(green, 50f, bold = true)
        val labelP = paint(t3, 22f)
        val sectionP = paint(t1, 28f, bold = true)
        val bodyP = paint(t1, 30f)
        val smallP = paint(t2, 20f)
        val greenP = paint(green, 44f, bold = true)
        val waterP = paint(water, 32f, bold = true)
        val gasP = paint(gas, 32f, bold = true)
        val valueSmallP = paint(t1, 26f)

        var y = PADDING

        // ── Header ──
        centered("⚡ 能耗报告", titleP, y + 44f * d)
        y += 72f * d
        centered(content.period, subP, y)
        y += 50f * d

        // ── Card 1: KPI ──
        val c1h = 240f * d
        drawCard(y, c1h + 48f * d)
        var cy = y + 32f * d
        centered("总用电量", labelP, cy); cy += 42f * d
        centered("%.1f kWh".format(content.totalKwh), kpiP, cy); cy += 64f * d
        centered("¥%.2f".format(content.totalCost), costP, cy); cy += 52f * d
        centered("CO₂ %.1f kg · 等效植树 %d 天".format(content.co2Kg, content.treeDays), paint(t2, 20f), cy)
        y += c1h + 48f * d + 16f * d

        // ── Card 2: Utility breakdown (water/gas if available) ──
        val hasWater = content.waterTons > 0
        val hasGas = content.gasM3 > 0
        if (hasWater || hasGas) {
            val utilityRows = if (hasWater && hasGas) 3 else 2
            val cUtilH = (60f + utilityRows * 56f) * d
            drawCard(y, cUtilH + 48f * d)
            cy = y + 32f * d
            centered("💧 公用事业", sectionP, cy); cy += 50f * d

            if (hasWater) {
                val waterLabel = "水费  %.1f 吨  ¥%.2f".format(content.waterTons, content.waterCost)
                canvas.drawText(waterLabel, PADDING + 32f * d, cy, waterP)
                cy += 52f * d
            }
            if (hasGas) {
                val gasLabel = "燃气  %.1f m³  ¥%.2f".format(content.gasM3, content.gasCost)
                canvas.drawText(gasLabel, PADDING + 32f * d, cy, gasP)
                cy += 52f * d
            }
            // Total utilities cost
            val utilTotal = content.waterCost + content.gasCost
            if (utilTotal > 0) {
                val totalLabel = "合计  ¥%.2f".format(utilTotal)
                canvas.drawText(totalLabel, PADDING + 32f * d, cy, valueSmallP)
            }
            y += cUtilH + 48f * d + 16f * d
        }

        // ── Card 3: Peak/Valley bar ──
        val totalPv = content.peakKwh + content.valleyKwh + content.flatKwh
        if (totalPv > 0) {
            val peakPct = (content.peakKwh / totalPv * 100).roundToInt()
            val valleyPct = (content.valleyKwh / totalPv * 100).roundToInt()
            val flatPct = 100 - peakPct - valleyPct

            val c2h = 170f * d
            drawCard(y, c2h + 48f * d)
            cy = y + 32f * d
            centered("峰谷平占比", sectionP, cy); cy += 50f * d

            val barL = WIDTH * 0.12f; val barR = WIDTH * 0.88f
            val barW = barR - barL; val barH = 36f * d; val cr = 10f * d

            // Draw stacked bar with rounded ends
            val barRect = RectF(barL, cy, barR, cy + barH)
            canvas.drawRoundRect(barRect, cr, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = flat })

            if (peakPct > 0) {
                val pw = barW * peakPct / 100f
                val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = peak }
                canvas.drawRoundRect(RectF(barL, cy, barL + pw, cy + barH), cr, cr, pp)
                canvas.drawRect(RectF(barL + cr, cy, barL + pw, cy + barH), pp)
            }
            if (valleyPct > 0) {
                val vl = barL + barW * (peakPct + flatPct) / 100f
                val vw = barW * valleyPct / 100f
                val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = valley }
                canvas.drawRoundRect(RectF(vl, cy, vl + vw, cy + barH), cr, cr, vp)
                canvas.drawRect(RectF(vl, cy, vl + vw - cr, cy + barH), vp)
            }
            cy += barH + 24f * d

            // Legend
            val lx = barL; val dr = 7f * d
            canvas.drawCircle(lx + dr, cy - 5f * d, dr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = peak })
            canvas.drawText("峰 %dkWh (%d%%)".format(content.peakKwh.toInt(), peakPct), lx + 20f * d, cy, smallP)
            val vx = WIDTH * 0.52f
            canvas.drawCircle(vx + dr, cy - 5f * d, dr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = valley })
            canvas.drawText("谷 %dkWh (%d%%)".format(content.valleyKwh.toInt(), valleyPct), vx + 20f * d, cy, smallP)
            y += c2h + 48f * d + 16f * d
        }

        // ── Card 4: Badges ──
        if (content.badges.isNotEmpty()) {
            val bc = content.badges.size
            val c3h = (40f + bc * 36f) * d
            drawCard(y, c3h + 48f * d)
            cy = y + 32f * d
            centered("🏅 成就徽章", sectionP, cy); cy += 42f * d
            content.badges.forEach { badge ->
                centered(badge, paint(green, 26f, bold = true), cy)
                cy += 36f * d
            }
            y += c3h + 48f * d + 16f * d
        }

        // ── Card 5: Previous cost comparison ──
        if (content.previousCost != null && content.previousCost > 0) {
            val diff = content.totalCost - content.previousCost
            val isUp = diff > 0
            val c4h = 80f * d
            drawCard(y, c4h + 48f * d)
            cy = y + 32f * d
            centered("较上月", labelP, cy); cy += 38f * d
            val diffColor = if (isUp) red else green
            val arrow = if (isUp) "↑" else "↓"
            centered("%s ¥%.2f".format(arrow, kotlin.math.abs(diff)), paint(diffColor, 32f, bold = true), cy)
            y += c4h + 48f * d + 16f * d
        }

        // ── Tips ──
        if (content.tips.isNotEmpty()) {
            val tipH = (40f + content.tips.size * 32f) * d
            drawCard(y, tipH + 48f * d)
            cy = y + 32f * d
            centered("💡 节能建议", sectionP, cy); cy += 38f * d
            content.tips.forEach { tip ->
                centered(tip, paint(t2, 20f), cy)
                cy += 32f * d
            }
            y += tipH + 48f * d + 16f * d
        }

        // ── Record count ──
        centered("📊 本月记录 ${content.recordCount} 条", paint(t2, 22f), y)
        y += 40f * d

        // ── Footer ──
        canvas.drawLine(PADDING + 50f * d, y, WIDTH - PADDING - 50f * d, y, paint(div, 1f))
        y += 24f * d
        centered("Energy Flow · 你的能耗小助手", paint(t3, 20f), y)
        y += 28f * d
        centered("💚 感谢你为节能减排做出的贡献！", paint(t3, 20f), y)

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
