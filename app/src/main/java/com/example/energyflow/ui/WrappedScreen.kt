package com.example.energyflow.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.energyflow.data.ReportExporter
import com.example.energyflow.data.ShareUtils
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.AppCard
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.OutlineDark
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import com.example.energyflow.ui.theme.WarningNeon
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun WrappedScreen(
    viewModel: WrappedViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPage by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableIntStateOf(0) } // 0=idle, 1=exporting, 2=done

    PredictiveBackHandler(enabled = true) {
        onDismiss()
    }

    LaunchedEffect(Unit) {
        viewModel.loadReport(YearMonth.now())
    }

    val pages = listOf("hero", "carbon", "peakvalley", "badges", "savings", "share")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(currentPage) {
                detectHorizontalDragGestures(
                    onDragEnd = { },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount < -50 && currentPage < pages.size - 1) {
                            currentPage++
                        } else if (dragAmount > 50 && currentPage > 0) {
                            currentPage--
                        }
                    }
                )
            }
    ) {
        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(AppCard)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index == currentPage) ElectricColor
                            else TextTertiary.copy(alpha = 0.4f)
                        )
                )
            }
        }

        // Page content
        Box(modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 24.dp)) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(350),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 3 }
                    ) + fadeIn(tween(250)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -direction * fullWidth / 6 }
                            ) + fadeOut(tween(200))
                        )
                },
                label = "pageTransition"
            ) { page ->
                when (page) {
                    0 -> HeroPage(state)
                    1 -> CarbonTreePage(state)
                    2 -> PeakValleyPage(state)
                    3 -> BadgesPage(state)
                    4 -> SavingsTipsPage(state)
                    5 -> SharePage(state, exporting, onExport = {
                        scope.launch {
                            exporting = 1
                            try {
                                val content = ReportExporter.ReportContent(
                                    period = "${state.yearMonth.year}年${state.yearMonth.monthValue}月",
                                    totalKwh = state.totalKwh,
                                    totalCost = state.totalCost,
                                    co2Kg = state.totalCo2Kg,
                                    peakKwh = state.peakKwh,
                                    valleyKwh = state.valleyKwh,
                                    flatKwh = state.flatKwh,
                                    treeDays = state.treeDays,
                                    badges = state.badges,
                                    previousCost = state.previousPeriodCost,
                                    tips = state.tips,
                                    recordCount = state.recordCount
                                )
                                val uri = ReportExporter.export(context, content)
                                if (uri != null) {
                                    ShareUtils.shareImage(context, uri)
                                }
                            } catch (_: Exception) { }
                            exporting = 0
                        }
                    })
                }
            }
        }

        // Bottom nav buttons
        Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    Button(
                        onClick = { currentPage-- },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppCard,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("← 上页", fontFamily = MonoFontFamily)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (currentPage < pages.size - 1) {
                    Button(
                        onClick = { currentPage++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricColor,
                            contentColor = AppBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("下页 →", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }

@Composable
private fun HeroPage(state: WrappedState) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricColor)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "你的 ${state.yearMonth.monthValue} 月",
            fontFamily = MonoFontFamily,
            fontSize = 20.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            "能耗报告",
            fontFamily = MonoFontFamily,
            fontSize = 36.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Big kWh number
        Text(
            "${"%.1f".format(state.totalKwh)}",
            fontFamily = MonoFontFamily,
            fontSize = 72.sp,
            color = ElectricColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            "kWh",
            fontFamily = MonoFontFamily,
            fontSize = 24.sp,
            color = ElectricColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Cost
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "¥${"%.2f".format(state.totalCost)}",
                fontFamily = MonoFontFamily,
                fontSize = 36.sp,
                color = SuccessGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "费用",
                fontFamily = MonoFontFamily,
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // CO2
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${"%.1f".format(state.totalCo2Kg)}",
                fontFamily = MonoFontFamily,
                fontSize = 28.sp,
                color = SuccessGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "kg CO₂",
                fontFamily = MonoFontFamily,
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun CarbonTreePage(state: WrappedState) {
    val treeProgress by animateFloatAsState(
        targetValue = (state.treeDays / 365f).coerceAtMost(1f),
        animationSpec = tween(1500),
        label = "treeProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🌳 碳足迹 & 树木",
            fontFamily = MonoFontFamily,
            fontSize = 28.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "本月碳排放等效",
            fontFamily = MonoFontFamily,
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${"%.1f".format(state.totalCo2Kg)} kg CO₂",
            fontFamily = MonoFontFamily,
            fontSize = 32.sp,
            color = SuccessGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Animated tree Canvas
        Box(modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val groundY = size.height
                val treeHeight = size.height * 0.8f * treeProgress
                val trunkWidth = 20f
                val trunkHeight = treeHeight * 0.35f

                // Trunk
                drawRect(
                    color = GasColor,
                    topLeft = Offset(centerX - trunkWidth / 2, groundY - trunkHeight),
                    size = androidx.compose.ui.geometry.Size(trunkWidth, trunkHeight)
                )

                // Canopy circles
                val canopyCenterY = groundY - trunkHeight - 10f
                val canopyRadius = treeHeight * 0.25f
                drawCircle(
                    color = SuccessGreen.copy(alpha = 0.9f),
                    radius = canopyRadius,
                    center = Offset(centerX, canopyCenterY)
                )
                drawCircle(
                    color = SuccessGreen.copy(alpha = 0.7f),
                    radius = canopyRadius * 0.8f,
                    center = Offset(centerX - canopyRadius * 0.6f, canopyCenterY + 10f)
                )
                drawCircle(
                    color = SuccessGreen.copy(alpha = 0.8f),
                    radius = canopyRadius * 0.8f,
                    center = Offset(centerX + canopyRadius * 0.6f, canopyCenterY + 5f)
                )

                // Ground line
                drawLine(
                    color = SuccessGreen.copy(alpha = 0.3f),
                    start = Offset(0f, groundY - 2f),
                    end = Offset(size.width, groundY - 2f),
                    strokeWidth = 4f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "🌿 等效植树 ${state.treeDays} 天",
            fontFamily = MonoFontFamily,
            fontSize = 18.sp,
            color = SuccessGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "目标: 365 天 (${(treeProgress * 100).toInt()}%)",
            fontFamily = MonoFontFamily,
            fontSize = 14.sp,
            color = TextTertiary
        )
    }
}

@Composable
private fun PeakValleyPage(state: WrappedState) {
    val peakRatio by animateFloatAsState(
        targetValue = if (state.totalKwh > 0) (state.peakKwh / state.totalKwh).toFloat() else 0f,
        animationSpec = tween(1000),
        label = "peakRatio"
    )
    val valleyRatio by animateFloatAsState(
        targetValue = if (state.totalKwh > 0) (state.valleyKwh / state.totalKwh).toFloat() else 0f,
        animationSpec = tween(1000),
        label = "valleyRatio"
    )
    val flatRatio by animateFloatAsState(
        targetValue = if (state.totalKwh > 0) (state.flatKwh / state.totalKwh).toFloat() else 0f,
        animationSpec = tween(1000),
        label = "flatRatio"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "⚡ 峰谷平分析",
            fontFamily = MonoFontFamily,
            fontSize = 28.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Stacked bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OutlineDark)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (peakRatio > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(peakRatio)
                            .fillMaxHeight()
                            .background(ElectricPeakColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (peakRatio > 0.12f) {
                            Text("峰", color = Color.White, fontFamily = MonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (flatRatio > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(flatRatio)
                            .fillMaxHeight()
                            .background(OutlineDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (flatRatio > 0.12f) {
                            Text("平", color = TextSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (valleyRatio > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(valleyRatio)
                            .fillMaxHeight()
                            .background(ElectricValleyColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (valleyRatio > 0.12f) {
                            Text("谷", color = Color.White, fontFamily = MonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Labels
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            LegendItem(ElectricPeakColor, "峰", "${"%.1f".format(state.peakKwh)} kWh", "${(peakRatio * 100).toInt()}%")
            LegendItem(OutlineDark, "平", "${"%.1f".format(state.flatKwh)} kWh", "${(flatRatio * 100).toInt()}%")
            LegendItem(ElectricValleyColor, "谷", "${"%.1f".format(state.valleyKwh)} kWh", "${(valleyRatio * 100).toInt()}%")
        }
    }
}

@Composable
private fun BadgesPage(state: WrappedState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🏅 成就徽章",
            fontFamily = MonoFontFamily,
            fontSize = 28.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Earned badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            state.badges.forEachIndexed { index, badge ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                when (index) {
                                    0 -> WarningNeon.copy(alpha = 0.2f)
                                    1 -> SuccessGreen.copy(alpha = 0.2f)
                                    2 -> ElectricColor.copy(alpha = 0.2f)
                                    else -> GasColor.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            badge.take(2),
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        badge,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Locked badges hint
        val lockedCount = (5 - state.badges.size).coerceAtLeast(0)
        if (lockedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppCard)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🔒 还有 $lockedCount 个未解锁",
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun SavingsTipsPage(state: WrappedState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "💡 节省建议",
            fontFamily = MonoFontFamily,
            fontSize = 28.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Comparison
        if (state.previousPeriodKwh != null) {
            val diff = state.totalKwh - state.previousPeriodKwh
            val isUp = diff > 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUp) ErrorNeon.copy(alpha = 0.1f)
                        else SuccessGreen.copy(alpha = 0.1f)
                    )
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isUp) "较上月 ↑" else "较上月 ↓",
                        fontFamily = MonoFontFamily,
                        fontSize = 16.sp,
                        color = if (isUp) ErrorNeon else SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${"%.1f".format(kotlin.math.abs(diff))} kWh",
                        fontFamily = MonoFontFamily,
                        fontSize = 28.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Tips
        state.tips.forEach { tip ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppCard)
                    .padding(12.dp)
            ) {
                Text(
                    "💡 $tip",
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SharePage(
    state: WrappedState,
    exporting: Int,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📤 分享报告",
            fontFamily = MonoFontFamily,
            fontSize = 28.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "将本月能耗数据分享给朋友",
            fontFamily = MonoFontFamily,
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onExport,
            enabled = exporting != 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricColor,
                contentColor = AppBackground
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (exporting == 1) {
                CircularProgressIndicator(
                    color = AppBackground,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "分享图片",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String, pct: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontFamily = MonoFontFamily, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontFamily = MonoFontFamily, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(pct, fontFamily = MonoFontFamily, fontSize = 11.sp, color = TextTertiary)
    }
}
