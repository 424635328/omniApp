package com.example.energyflow.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.R
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全屏开屏动画。
 *
 * 多阶段动画序列：
 * 1. 暗色背景 → 中央光点亮起
 * 2. 图标弹性缩放入场 + 外层光晕扩散
 * 3. 波纹涟漪从图标向外扩散
 * 4. 旋转电弧环绕 + 粒子轨道
 * 5. 应用名称 "ENERGYFLOW" 从下方滑入
 * 6. 副标题淡入
 * 7. 全部淡出 → 进入主界面
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // ═══ 入场动画控制器 ═══
    val iconScale = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(30f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    // ═══ 无限循环动画（持续） ═══
    val infiniteTransition = rememberInfiniteTransition(label = "splashLoop")

    // 呼吸缩放（轻）
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "breathe"
    )

    // 波纹相位（驱动涟漪扩散）
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "ripplePhase"
    )

    // 旋转角度
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Restart),
        label = "rotation"
    )

    // 粒子轨道角度
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Restart),
        label = "orbit"
    )

    // ═══ 跳过标志 ═══
    var skipped by remember { mutableStateOf(false) }

    // 点击跳过 → 快速淡出并结束
    LaunchedEffect(skipped) {
        if (skipped) {
            exitAlpha.animateTo(0f, tween(150))
            onFinished()
        }
    }

    // ═══ 动画序列 ═══
    LaunchedEffect(Unit) {
        if (skipped) return@LaunchedEffect

        // Phase 1: 图标入场 (0→600ms)
        iconAlpha.animateTo(1f, tween(200))
        iconScale.animateTo(
            1.02f,
            spring(dampingRatio = 0.55f, stiffness = 300f)
        )
        iconScale.animateTo(1f, tween(150))

        // Phase 2: 标题 (600→1200ms)
        delay(200)
        titleAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        titleOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing))

        // Phase 3: 副标题 (900→1500ms)
        delay(300)
        subtitleAlpha.animateTo(1f, tween(400))

        // Phase 4: 持续展示 (1500→2500ms) — 分小段检查跳过
        repeat(10) { if (!skipped) delay(100) }

        // Phase 5: 退出 (2500→2800ms)
        if (!skipped) {
            exitAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            delay(100)
        }
        if (!skipped) onFinished()
    }

    val currentAlpha = exitAlpha.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { skipped = true }
            .graphicsLayer { alpha = currentAlpha },
        contentAlignment = Alignment.Center
    ) {
        // ═══════════════════════════════════════════
        // Canvas 层 — 波纹 + 旋转环 + 粒子
        // ═══════════════════════════════════════════
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = iconAlpha.value }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val iconRadius = 40.dp.toPx()  // 图标半径

            // ── 扩散波纹（3层交错） ──
            for (layer in 0..2) {
                val offset = layer * 0.33f
                val p = ((ripplePhase + offset) % 1f)
                val ringRadius = iconRadius + p * (iconRadius * 3.5f)
                val ringAlpha = (1f - p) * 0.25f

                drawCircle(
                    color = ElectricColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }

            // ── 旋转电弧环 ──
            val arcRadius = iconRadius * 1.6f
            val arcSweep = 90f
            val arcStart = rotation
            val arcOval = androidx.compose.ui.geometry.Rect(
                cx - arcRadius, cy - arcRadius,
                cx + arcRadius, cy + arcRadius
            )
            val arcPath = Path().apply {
                addArc(arcOval, arcStart, arcSweep)
            }
            drawPath(
                arcPath,
                color = ElectricColor.copy(alpha = 0.35f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
            // 反方向第二段弧线
            val arcPath2 = Path().apply {
                addArc(arcOval, arcStart + 180f, arcSweep * 0.6f)
            }
            drawPath(
                arcPath2,
                color = NeonYellow.copy(alpha = 0.25f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )

            // ── 轨道粒子（6颗） ──
            for (i in 0..5) {
                val angleDeg = orbitAngle + i * 60f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val orbitR = iconRadius * (2.0f + (i % 2) * 0.4f)
                val px = cx + orbitR * cos(angleRad).toFloat()
                val py = cy + orbitR * sin(angleRad).toFloat()
                val dotAlpha = if (i % 2 == 0) 0.5f else 0.3f

                // 光点外圈
                drawCircle(Color.Transparent, 4f, Offset(px, py))
                drawCircle(
                    ElectricColor.copy(alpha = dotAlpha * 0.3f),
                    5f, Offset(px, py)
                )
                drawCircle(
                    ElectricColor.copy(alpha = dotAlpha),
                    2.5f, Offset(px, py)
                )
            }
        }

        // ═══════════════════════════════════════════
        // 核心内容层
        // ═══════════════════════════════════════════
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ── 图标区域 ──
            Box(contentAlignment = Alignment.Center) {
                // 外层渐变光晕
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(breathe * iconScale.value)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ElectricColor.copy(alpha = 0.18f),
                                    ElectricColor.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 中层光晕
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ElectricColor.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 图标本身
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "EnergyFlow",
                    modifier = Modifier
                        .size(72.dp)
                        .scale(iconScale.value)
                        .graphicsLayer { alpha = iconAlpha.value }
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── 应用名称 ──
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetY.value * density
                    }
            ) {
                androidx.compose.material3.Text(
                    "ENERGYFLOW",
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 发光装饰线 ──
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(2.dp)
                    .graphicsLayer { alpha = titleAlpha.value }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                ElectricColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── 副标题 ──
            Box(
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value }
            ) {
                androidx.compose.material3.Text(
                    "能 耗 手 记",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 6.sp
                )
            }
        }
    }
}
