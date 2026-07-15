package com.example.energyflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricStart
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import com.example.energyflow.ui.theme.WaterColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 跳过按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "跳过",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onComplete() }
                )
            }

            // 页面内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPage1()
                    1 -> OnboardingPage2()
                    2 -> OnboardingPage3()
                }
            }

            // 底部指示器 + 按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 页面指示器
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateFloatAsState(
                            targetValue = if (isSelected) 24f else 8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            ),
                            label = "dot"
                        )
                        Box(
                            modifier = Modifier
                                .width(width.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) ElectricColor
                                    else DarkSurface
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 下一步 / 开始使用 按钮
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricColor,
                        contentColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage < 2) "下一步" else "开始使用",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage1() {
    var inputText by remember { mutableStateOf("") }
    val parser = remember { com.example.energyflow.data.SmartInputParser() }

    val parseResults = remember(inputText) {
        if (inputText.isBlank()) emptyList()
        else parser.parseWithContext(inputText)
            .filterIsInstance<com.example.energyflow.data.ParseResult.Success>()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            "智能输入",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "试试输入电表读数，比如 16000",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 真实可交互输入框
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "试试输入: 16776 冰箱",
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontFamily = MonoFontFamily
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricColor,
                unfocusedBorderColor = DarkSurface,
                cursorColor = ElectricColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Medium
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 实时解析结果
        AnimatedVisibility(
            visible = parseResults.isNotEmpty(),
            enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuccessGreen.copy(alpha = 0.06f))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "识别到 ${parseResults.size} 条记录",
                        color = SuccessGreen,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                parseResults.forEach { result ->
                    val (icon, label) = when {
                        result.isElectric && result.isWater -> "⚡💧" to "电+水"
                        result.isElectric -> "⚡" to if (result.electricPeak != null) "峰谷" else "电表"
                        result.isWater -> "💧" to "水表"
                        result.note != null -> "📝" to result.note
                        else -> "📋" to "记录"
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            color = ElectricColor,
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (result.isElectric && result.electricTotal != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${String.format("%.0f", result.electricTotal)} 度",
                                color = TextSecondary,
                                fontFamily = MonoFontFamily,
                                fontSize = 13.sp
                            )
                        }
                        if (result.isWater && result.waterTotal != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${String.format("%.0f", result.waterTotal)} 吨",
                                color = TextSecondary,
                                fontFamily = MonoFontFamily,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 提示文字
        if (inputText.isBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "试试这些格式：",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
                ExampleChip("7.14 16776", "日期 + 电表", onClick = { inputText = "7.14 16776" })
                ExampleChip("水0879", "水表前缀", onClick = { inputText = "水0879" })
                ExampleChip("16.39打开冰箱", "时间 + 备注", onClick = { inputText = "16.39打开冰箱" })
            }
        }
    }
}

@Composable
private fun ExampleChip(text: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = ElectricColor,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            color = TextTertiary,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun OnboardingPage2() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "三表合一",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "电表 · 水表 · 燃气表\n一条记录，同时管理",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 三个表卡片
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MeterIntroCard(
                icon = Icons.Default.Bolt,
                label = "电表",
                color = ElectricColor,
                unit = "kWh",
                modifier = Modifier.weight(1f)
            )
            MeterIntroCard(
                icon = Icons.Default.WaterDrop,
                label = "水表",
                color = WaterColor,
                unit = "m³",
                modifier = Modifier.weight(1f)
            )
            MeterIntroCard(
                icon = Icons.Default.Bolt,
                label = "燃气",
                color = GasColor,
                unit = "m³",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MeterIntroCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "meter"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            label,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            unit,
            color = TextTertiary,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun OnboardingPage3() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "主动洞察",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "AI 自动发现能耗模式\n给出个性化节能建议",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 模拟 Insight Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ElectricColor.copy(alpha = 0.06f))
                .border(1.dp, ElectricColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "阶梯预警",
                        color = ElectricColor,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "本月用电已接近二档加价区，建议控制空调使用",
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 功能列表
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureItem("📊", "阶梯电价进度条", "可视化用电水位线")
            FeatureItem("🌡️", "天气关联分析", "温度与能耗一目了然")
            FeatureItem("📈", "月度预测", "提前预知账单金额")
        }
    }
}

@Composable
private fun FeatureItem(emoji: String, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
        }
    }
}
