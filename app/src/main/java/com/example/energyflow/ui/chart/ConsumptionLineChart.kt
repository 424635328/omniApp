package com.example.energyflow.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.data.DailyWeather
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricEnd
import com.example.energyflow.ui.theme.ElectricGradient
import com.example.energyflow.ui.theme.ElectricStart
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.utils.Formatters.formatDecimal1
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 自绘折线图 — 支持双指缩放、惯性滑动 (Fling)、
 *                         数据点选中 Tooltip、天气叠加、无障碍语义。
 *
 * ## 性能分层
 * - 背景层: 网格、刻度标签（每次重绘）
 * - 数据层: 折线、面积填充、数据点（受动画进度控制）
 * - 交互层: Tooltip、选中指示器（仅在选中时重绘开销）
 */
@Composable
fun ConsumptionLineChart(
    consumptions: List<DailyConsumption>,
    showCost: Boolean = false,
    selectedIndex: Int = -1,
    onSelectedIndexChange: (Int) -> Unit = {},
    weatherByDate: Map<LocalDate, DailyWeather> = emptyMap(),
    accentColor: Color = ElectricColor,
    unitLabel: String = "度",
    forecastConsumptions: List<DailyConsumption> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (consumptions.isEmpty()) return

    val animateProgress = remember { Animatable(0f) }
    val flingScope = rememberCoroutineScope()

    // 数据变化时重播动画
    LaunchedEffect(consumptions, showCost) {
        animateProgress.snapTo(0f)
        animateProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val chartColor = accentColor
    val gridColor = DarkCard.copy(alpha = 0.6f)
    val labelColor = TextSecondary

    // ── 双指缩放 + 平移 + 惯性滑动状态 ──
    var scale by remember(consumptions) { mutableFloatStateOf(1f) }
    var panX by remember(consumptions) { mutableFloatStateOf(0f) }

    // ── 双缓冲：Picture 录制静态背景层（只在数据变化时重录） ──
    var bgVersion by remember(consumptions) { mutableIntStateOf(0) }
    val bgPicture = remember { mutableStateOf<Picture?>(null) }

    // ── 无障碍描述 ──
    val semDesc = buildA11yDescription(consumptions, showCost, unitLabel)

    // ── 缓存的 Paint 对象 ──
    val density = LocalDensity.current
    val gridTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val dateTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val tooltipDatePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val tooltipValuePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            isFakeBoldText = true
        }
    }
    val tempMaxPaint = remember {
        android.graphics.Paint().apply {
            color = Color(0xFFFF8800).toArgb()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            isFakeBoldText = true
        }
    }
    val tempMinPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            isFakeBoldText = true
        }
    }
    val sepLabelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }

    // 动态属性更新
    LaunchedEffect(density, labelColor) {
        val s10 = with(density) { 10.sp.toPx() }
        val s13 = with(density) { 13.sp.toPx() }
        val s11 = with(density) { 11.sp.toPx() }
        val s9 = with(density) { 9.sp.toPx() }
        gridTextPaint.color = labelColor.copy(alpha = 0.7f).toArgb()
        gridTextPaint.textSize = s10
        dateTextPaint.color = labelColor.copy(alpha = 0.6f).toArgb()
        dateTextPaint.textSize = s9
        tooltipDatePaint.color = TextSecondary.toArgb()
        tooltipDatePaint.textSize = s10
        tooltipValuePaint.color = Color.White.toArgb()
        tooltipValuePaint.textSize = s13
        tempMaxPaint.textSize = s11
        tempMinPaint.color = NeonBlue.toArgb()
        tempMinPaint.textSize = s11
        sepLabelPaint.color = TextSecondary.copy(alpha = 0.4f).toArgb()
        sepLabelPaint.textSize = s11
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .semantics(mergeDescendants = false) {
                contentDescription = semDesc
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clearAndSetSemantics {}  // Canvas 内建的无障碍已通过父 Box 覆盖
                .pointerInput(consumptions, showCost) {
                    detectTapGestures { offset ->
                        val paddingLeft = 54f
                        val chartWidth = size.width - paddingLeft - 20f
                        val xStep = if (consumptions.size > 1)
                            chartWidth / (consumptions.size - 1) else chartWidth
                        val index = ((offset.x - paddingLeft) / xStep).roundToInt()
                            .coerceIn(0, consumptions.size - 1)
                        onSelectedIndexChange(if (selectedIndex == index) -1 else index)
                    }
                }
                .pointerInput(consumptions) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown()
                        var lastPos = firstDown.position
                        var lastTime = System.nanoTime()
                        var isHorizontal: Boolean? = null
                        var initialSpan = 0f
                        var velocityX = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            if (changes.isEmpty() || !changes.any { it.pressed }) {
                                // ── 手指抬起 → 启动惯性滑动 ──
                                if (isHorizontal == true && abs(velocityX) > 200f) {
                                    flingScope.launch {
                                        flingDecelerate(velocityX) { delta ->
                                            val maxPan = if (scale > 1f)
                                                (size.width * (scale - 1f) * 0.5f) else 0f
                                            panX = (panX + delta).coerceIn(-maxPan, maxPan)
                                        }
                                    }
                                }
                                break
                            }

                            // 双指缩放
                            if (changes.size >= 2 && changes.all { it.pressed }) {
                                val span = (changes[0].position - changes[1].position).getDistance()
                                if (initialSpan > 0f) {
                                    val newScale = (scale * span / initialSpan).coerceIn(1f, 4f)
                                    scale = newScale
                                    onSelectedIndexChange(-1)
                                }
                                initialSpan = span
                                changes.forEach { it.consume() }
                                continue
                            }
                            initialSpan = 0f

                            val change = changes.first()
                            if (!change.pressed) break

                            val now = System.nanoTime()
                            val dt = ((now - lastTime) / 1_000_000f).coerceAtLeast(1f)
                            val delta = change.position - lastPos

                            // 估算速度 (px/ms)
                            velocityX = (delta.x / dt) * 16f  // 归一化到 ~60fps 帧

                            lastPos = change.position
                            lastTime = now

                            if (isHorizontal == null) {
                                val totalDelta = change.position - firstDown.position
                                if (abs(totalDelta.x) > 8f || abs(totalDelta.y) > 8f) {
                                    isHorizontal = abs(totalDelta.x) > abs(totalDelta.y)
                                }
                            }

                            when (isHorizontal) {
                                true -> {
                                    change.consume()
                                    val maxPan = if (scale > 1f)
                                        (size.width * (scale - 1f) * 0.5f) else 0f
                                    panX = (panX + delta.x).coerceIn(-maxPan, maxPan)
                                    onSelectedIndexChange(-1)
                                }
                                false -> { /* 垂直滑动 → 传递父容器 */ }
                                null -> { change.consume() }
                            }
                        }
                    }
                }
        ) {
            // ── 计算尺寸 ──
            val canvasWidth = size.width
            val canvasHeight = size.height
            val transformOffsetX = panX + (canvasWidth * (scale - 1f) / 2f)

            val paddingLeft = 54f
            val paddingBottom = 44f
            val paddingTop = 12f
            val paddingRight = 20f
            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom
            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            val values = consumptions.map {
                if (showCost) it.estimatedCost else it.dailyConsumption
            }
            val maxValue = values.maxOrNull()?.let { if (it <= 0) 1.0 else it * 1.1 } ?: 1.0
            val minValue = 0.0
            val valueRange = maxValue - minValue
            val pointCount = consumptions.size
            val xStep = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth

            // 计算所有数据点
            val points = consumptions.mapIndexed { index, consumption ->
                val x = paddingLeft + index * xStep
                val value = if (showCost) consumption.estimatedCost else consumption.dailyConsumption
                val y = paddingTop + chartHeight -
                        ((value - minValue) / valueRange * chartHeight).toFloat()
                Offset(x, y)
            }

            val progress = animateProgress.value
            val visibleCount = (pointCount * progress).toInt().coerceIn(1, pointCount)

            // ═════════════════════════════════════════
            // 双缓冲：将静态背景层（网格 + Y 轴标签）录制成 Picture
            // 仅在 bgVersion 变化时重新录制
            // ═════════════════════════════════════════
            val bgPic = bgPicture.value
            if (bgPic == null || bgVersion == 0) {
                val picture = Picture()
                val recordingCanvas = picture.beginRecording(canvasWidth.toInt(), canvasHeight.toInt())
                recordingCanvas.save()
                recordingCanvas.translate(transformOffsetX, 0f)
                recordingCanvas.scale(scale, 1f, canvasWidth / 2f, 0f)

                drawGridLinesOnCanvas(
                    canvas = recordingCanvas,
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    maxValue = maxValue,
                    showCost = showCost,
                    gridColor = gridColor,
                    dashEffect = dashEffect,
                    gridTextPaint = gridTextPaint
                )

                recordingCanvas.restore()
                picture.endRecording()
                bgPicture.value = picture

                val next = bgVersion + 1
                bgVersion = if (next > 1000) 1 else next
            }

            // ═════════════════════════════════════════
            // 绘制静态背景层（从 Picture 回放）
            // ═════════════════════════════════════════
            bgPicture.value?.let { pic ->
                drawContext.canvas.nativeCanvas.drawPicture(pic)
            }

            // ═════════════════════════════════════════
            // 动态层：应用缩放 + 平移（只对动态层做变换）
            // ═════════════════════════════════════════
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.translate(transformOffsetX, 0f)
            drawContext.canvas.nativeCanvas.scale(scale, 1f, canvasWidth / 2f, 0f)

            if (visibleCount < 2) {
                val pt = points[0]
                drawCircle(chartColor.copy(alpha = 0.3f), 7f, pt)
                drawCircle(chartColor, 4f, pt)
                drawContext.canvas.nativeCanvas.restore()
                return@Canvas
            }

            // ═════════════════════════════════════════
            // 层 2: 折线 + 面积填充
            // ═════════════════════════════════════════
            val visiblePoints = points.take(visibleCount)

            val linePath = Path()
            val fillPath = Path()

            if (visiblePoints.isNotEmpty()) {
                linePath.moveTo(visiblePoints[0].x, visiblePoints[0].y)
                fillPath.moveTo(visiblePoints[0].x, paddingTop + chartHeight)
                fillPath.lineTo(visiblePoints[0].x, visiblePoints[0].y)

                for (i in 1 until visiblePoints.size) {
                    val prev = visiblePoints[i - 1]
                    val curr = visiblePoints[i]
                    linePath.cubicSmoothTo(prev, curr)
                    fillPath.cubicSmoothTo(prev, curr)
                }

                if (visibleCount < pointCount) {
                    val last = points[visibleCount - 1]
                    val next = points[visibleCount]
                    val fraction = (pointCount * progress) - visibleCount + 1
                    val interpX = last.x + (next.x - last.x) * fraction
                    val interpY = last.y + (next.y - last.y) * fraction
                    val interp = Offset(interpX, interpY)
                    linePath.cubicSmoothTo(last, interp)
                    fillPath.cubicSmoothTo(last, interp)
                    fillPath.lineTo(interpX, paddingTop + chartHeight)
                } else {
                    fillPath.lineTo(visiblePoints.last().x, paddingTop + chartHeight)
                }
            }
            fillPath.close()

            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    listOf(
                        ElectricStart.copy(alpha = 0.12f),
                        ElectricEnd.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight
                )
            )

            drawPath(
                linePath,
                brush = Brush.linearGradient(
                    listOf(ElectricStart.copy(alpha = 0.25f), ElectricEnd.copy(alpha = 0.15f))
                ),
                style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                linePath,
                brush = Brush.linearGradient(
                    listOf(ElectricStart.copy(alpha = 0.6f), ElectricEnd.copy(alpha = 0.6f))
                ),
                style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                linePath,
                brush = ElectricGradient,
                style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // ═════════════════════════════════════════
            // 层 2b: 数据点（稀疏绘制）
            // ═════════════════════════════════════════
            val pointStep = (pointCount / 120).coerceAtLeast(1)
            points.forEachIndexed { index, point ->
                if (index >= visibleCount) return@forEachIndexed
                if (index % pointStep != 0 && index != pointCount - 1) return@forEachIndexed

                val isSelected = index == selectedIndex
                drawCircle(
                    chartColor.copy(alpha = if (isSelected) 0.4f else 0.15f),
                    if (isSelected) 10f else 7f,
                    point
                )
                drawCircle(
                    if (isSelected) Color.White else chartColor,
                    if (isSelected) 5f else 3f,
                    point
                )
            }

            // ═════════════════════════════════════════
            // 层 2c: 预报投影虚线（从最后一个实际数据点延伸到月底）
            // ═════════════════════════════════════════
            if (forecastConsumptions.isNotEmpty() && visibleCount >= 2) {
                val lastActualPoint = points[visibleCount - 1]
                val forecastPath = Path().apply {
                    moveTo(lastActualPoint.x, lastActualPoint.y)
                    var fx = lastActualPoint.x + xStep
                    for (fc in forecastConsumptions) {
                        val fv = if (showCost) fc.estimatedCost else fc.dailyConsumption
                        val fy = paddingTop + chartHeight -
                                ((fv - minValue) / valueRange * chartHeight).toFloat()
                        lineTo(fx, fy)
                        fx += xStep
                    }
                }
                drawPath(
                    forecastPath,
                    color = chartColor.copy(alpha = 0.45f),
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
                // 预报起点的"预估"小标签
                val forecastLabelPaint = android.graphics.Paint().apply {
                    color = chartColor.copy(alpha = 0.6f).toArgb()
                    textSize = with(density) { 9.sp.toPx() }
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "预估",
                    lastActualPoint.x + xStep - 4f,
                    lastActualPoint.y - 10f,
                    forecastLabelPaint
                )
            }

            // ═════════════════════════════════════════
            // 层 3: 交互层 — Tooltip + 选中指示器
            // ═════════════════════════════════════════
            if (selectedIndex in points.indices && selectedIndex < visibleCount) {
                drawTooltip(
                    selectedIndex, points, values, consumptions, weatherByDate,
                    chartColor, paddingLeft, paddingTop, chartWidth, chartHeight,
                    tooltipDatePaint, tooltipValuePaint, tempMaxPaint, tempMinPaint,
                    sepLabelPaint, unitLabel, showCost
                )
            }

            // ═════════════════════════════════════════
            // 层 4: 日期标签
            // ═════════════════════════════════════════
            val dateStep = maxOf(1, pointCount / 6)
            consumptions.forEachIndexed { index, consumption ->
                if (index % dateStep == 0 || index == pointCount - 1) {
                    drawContext.canvas.nativeCanvas.drawText(
                        consumption.date.format(DateTimeFormatter.ofPattern("MM/dd")),
                        paddingLeft + index * xStep,
                        paddingTop + chartHeight + 22f,
                        dateTextPaint
                    )
                }
            }

            drawContext.canvas.nativeCanvas.restore()
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 扩展函数：Path 的三次贝塞尔平滑连接
// ════════════════════════════════════════════════════════════════

private fun Path.cubicSmoothTo(from: Offset, to: Offset) {
    val controlX1 = from.x + (to.x - from.x) / 2f
    val controlY1 = from.y
    val controlX2 = from.x + (to.x - from.x) / 2f
    val controlY2 = to.y
    cubicTo(controlX1, controlY1, controlX2, controlY2, to.x, to.y)
}

// ════════════════════════════════════════════════════════════════
// 惯性滑动（Fling）衰减动画
// ════════════════════════════════════════════════════════════════

/**
 * 执行指数衰减惯性滑动。
 * @param initialVelocity 初始速度 (px/frame, ~60fps)
 * @param onDelta 每帧回调，参数为本次位移增量
 */
private suspend fun flingDecelerate(
    initialVelocity: Float,
    onDelta: (Float) -> Unit
) {
    val friction = 0.85f       // 每帧衰减系数
    val minVelocity = 0.5f     // 停止阈值
    var velocity = initialVelocity

    while (abs(velocity) > minVelocity) {
        onDelta(velocity)
        velocity *= friction
        // 等下一帧（~16ms）
        kotlinx.coroutines.delay(16)
    }
}

// ════════════════════════════════════════════════════════════════
// 无障碍描述生成
// ════════════════════════════════════════════════════════════════

private fun buildA11yDescription(
    consumptions: List<DailyConsumption>,
    showCost: Boolean,
    unitLabel: String
): String {
    if (consumptions.isEmpty()) return "暂无数据"

    val values = consumptions.map {
        if (showCost) it.estimatedCost else it.dailyConsumption
    }
    val maxVal = values.maxOrNull() ?: 0.0
    val minVal = values.minOrNull() ?: 0.0
    val avgVal = values.average()
    val maxIdx = values.indexOfFirst { it == maxVal }
    val maxDate = consumptions.getOrNull(maxIdx)?.date?.format(
        DateTimeFormatter.ofPattern("M月d日")
    ) ?: ""

    return "能耗趋势图，共${consumptions.size}个数据点。最高${"%.1f".format(maxVal)}$unitLabel，出现在${maxDate}；最低${"%.1f".format(minVal)}$unitLabel，平均${"%.1f".format(avgVal)}$unitLabel。双指缩放，左右滑动查看。"
}

// ════════════════════════════════════════════════════════════════
// 工具函数：绘制网格线
// ════════════════════════════════════════════════════════════════

private fun DrawScope.drawGridLines(
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    maxValue: Double,
    showCost: Boolean,
    gridColor: Color,
    dashEffect: PathEffect,
    gridTextPaint: android.graphics.Paint
) {
    val lineCount = 4
    for (i in 0..lineCount) {
        val y = paddingTop + chartHeight * i / lineCount
        drawLine(
            gridColor.copy(alpha = 0.4f),
            Offset(paddingLeft, y),
            Offset(paddingLeft + chartWidth, y),
            1f,
            pathEffect = dashEffect
        )
        val gridValue = maxValue * (lineCount - i) / lineCount
        val label = if (showCost) "¥${formatInt(gridValue)}" else formatInt(gridValue)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            paddingLeft - 10f,
            y + 4f,
            gridTextPaint
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 工具函数：绘制 Tooltip
// ════════════════════════════════════════════════════════════════

private fun DrawScope.drawTooltip(
    selectedIndex: Int,
    points: List<Offset>,
    values: List<Double>,
    consumptions: List<DailyConsumption>,
    weatherByDate: Map<LocalDate, DailyWeather>,
    chartColor: Color,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    tooltipDatePaint: android.graphics.Paint,
    tooltipValuePaint: android.graphics.Paint,
    tempMaxPaint: android.graphics.Paint,
    tempMinPaint: android.graphics.Paint,
    sepLabelPaint: android.graphics.Paint,
    unitLabel: String,
    showCost: Boolean
) {
    val selPoint = points[selectedIndex]
    val selValue = values[selectedIndex]
    val selLabel = if (showCost) "¥${formatDecimal1(selValue)}" else "${formatDecimal1(selValue)} $unitLabel"
    val selDate = consumptions[selectedIndex].date.format(
        DateTimeFormatter.ofPattern("MM.dd")
    )

    // 竖线指示
    drawLine(
        chartColor.copy(alpha = 0.18f),
        Offset(selPoint.x, paddingTop),
        Offset(selPoint.x, paddingTop + chartHeight),
        2f
    )

    val selLocalDate = consumptions[selectedIndex].date.toLocalDate()
    val selWeather = weatherByDate[selLocalDate]

    val hasWeather = selWeather != null
    val tooltipH = if (hasWeather) 64f else 38f
    val tooltipPadH = 16f
    val tooltipRadius = 10f

    val dateStr = selDate
    val valueStr = selLabel
    val dw = tooltipDatePaint.measureText(dateStr)
    val vw = tooltipValuePaint.measureText(valueStr)
    val row1W = dw + vw + 12f

    val wHighStr = if (hasWeather) "H${selWeather!!.tempMax.toInt()}°" else ""
    val wLowStr  = if (hasWeather) "L${selWeather!!.tempMin.toInt()}°" else ""
    val sepStr   = if (hasWeather) "  ·  " else ""
    val whw = tempMaxPaint.measureText(wHighStr)
    val wsw = sepLabelPaint.measureText(sepStr)
    val wlw = tempMinPaint.measureText(wLowStr)
    val row2W = if (hasWeather) whw + wsw + wlw else 0f

    val contentW = maxOf(row1W, row2W)
    val tooltipW = contentW + tooltipPadH * 2f

    val tipX = selPoint.x.coerceIn(
        paddingLeft + tooltipW / 2f,
        paddingLeft + chartWidth - tooltipW / 2f
    )
    val tipY = (paddingTop + 6f).coerceAtLeast(selPoint.y - tooltipH - 12f)

    val left = tipX - tooltipW / 2f
    val right = tipX + tooltipW / 2f
    val baseY = tipY

    // 背景
    drawRoundRect(
        color = DarkSurface,
        topLeft = Offset(left, baseY),
        size = Size(tooltipW, tooltipH),
        cornerRadius = CornerRadius(tooltipRadius, tooltipRadius)
    )
    // 边框
    drawRoundRect(
        color = chartColor.copy(alpha = 0.3f),
        topLeft = Offset(left, baseY),
        size = Size(tooltipW, tooltipH),
        cornerRadius = CornerRadius(tooltipRadius, tooltipRadius),
        style = Stroke(1f)
    )

    // 第一行：日期 | 数值
    val row1Y = baseY + 20f
    drawContext.canvas.nativeCanvas.drawText(
        dateStr, left + tooltipPadH, row1Y, tooltipDatePaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        valueStr, right - tooltipPadH, row1Y, tooltipValuePaint
    )

    if (hasWeather) {
        val sepY = baseY + 29f
        drawLine(
            chartColor.copy(alpha = 0.12f),
            Offset(left + tooltipPadH, sepY),
            Offset(right - tooltipPadH, sepY),
            1f
        )
        val row2Y = baseY + 48f
        var cursorX = left + tooltipPadH
        drawContext.canvas.nativeCanvas.drawText(wHighStr, cursorX, row2Y, tempMaxPaint)
        cursorX += whw
        drawContext.canvas.nativeCanvas.drawText(sepStr, cursorX, row2Y, sepLabelPaint)
        cursorX += wsw
        drawContext.canvas.nativeCanvas.drawText(wLowStr, cursorX, row2Y, tempMinPaint)
    }

    // 小三角
    val triPath = Path().apply {
        moveTo(selPoint.x - 6f, baseY + tooltipH)
        lineTo(selPoint.x + 6f, baseY + tooltipH)
        lineTo(selPoint.x, baseY + tooltipH + 7f)
        close()
    }
    drawPath(triPath, color = DarkSurface)
    drawPath(triPath, chartColor.copy(alpha = 0.3f), style = Stroke(1f))
}

private fun formatInt(value: Double): String = "%.0f".format(value)

// ════════════════════════════════════════════════════════════════
// 双缓冲：Android Canvas 版本网格绘制（用于 Picture 录制）
// ════════════════════════════════════════════════════════════════

private fun drawGridLinesOnCanvas(
    canvas: android.graphics.Canvas,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    maxValue: Double,
    showCost: Boolean,
    gridColor: Color,
    dashEffect: PathEffect,
    gridTextPaint: android.graphics.Paint
) {
    val lineCount = 4
    val gridPaint = android.graphics.Paint().apply {
        color = gridColor.copy(alpha = 0.4f).toArgb()
        strokeWidth = 1f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }

    for (i in 0..lineCount) {
        val y = paddingTop + chartHeight * i / lineCount
        canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint)
        val gridValue = maxValue * (lineCount - i) / lineCount
        val label = if (showCost) "¥${formatInt(gridValue)}" else formatInt(gridValue)
        canvas.drawText(label, paddingLeft - 10f, y + 4f, gridTextPaint)
    }
}
