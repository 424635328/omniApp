package com.example.energyflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.ui.utils.Formatters
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineItem(
    record: MeterRecord,
    onDelete: ((MeterRecord) -> Unit)? = null,
    onEdit: ((MeterRecord) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var showActions by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── 按压动效：缩放 + 阴影变化 ──
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    val pressElevation by animateFloatAsState(
        targetValue = if (isPressed) 1f else 3f,
        animationSpec = tween(120),
        label = "pressElevation"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed || showActions) 0.3f else 0.05f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )

    // 删除确认弹窗
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "确认删除",
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        record.timestamp.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")),
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (record.isElectricRecorded && record.electricTotal != null) {
                        Text(
                            "电量 ${Formatters.formatElectric(record.electricTotal)} 度",
                            color = ElectricColor,
                            fontFamily = MonoFontFamily
                        )
                    }
                    if (record.isWaterRecorded && record.waterTotal != null) {
                        Text(
                            "水表 ${Formatters.formatWater(record.waterTotal)} 吨",
                            color = WaterColor,
                            fontFamily = MonoFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "此操作无法撤销",
                        color = Color(0xFFFF6B6B),
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete?.invoke(record)
                    }
                ) {
                    Text("删除", color = Color(0xFFFF6B6B), fontFamily = MonoFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = TextSecondary, fontFamily = MonoFontFamily)
                }
            },
            containerColor = DarkCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .shadow(
                elevation = pressElevation.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ElectricColor.copy(alpha = if (isPressed || showActions) 0.15f else 0.05f),
                spotColor = ElectricColor.copy(alpha = if (isPressed || showActions) 0.15f else 0.05f)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null, // 用自定义效果替代默认 ripple
                    onClick = { showActions = !showActions },
                    onLongClick = { showDeleteDialog = true }
                )
                .padding(16.dp)
        ) {
            // ── 按压时的发光边框叠加层 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isPressed || showActions)
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    ElectricColor.copy(alpha = borderAlpha),
                                    Color.Transparent
                                )
                            )
                        else Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // ── 时间线指示器 ──
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp)
                ) {
                    Text(
                        text = record.timestamp.format(DateTimeFormatter.ofPattern("MM.dd")),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFontFamily
                    )

                    // 时间点 — 按压时发光增强
                    val dotGlow = if (isPressed) 0.6f else 0.3f
                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .size(10.dp)
                            .shadow(
                                elevation = if (isPressed) 8.dp else 4.dp,
                                shape = CircleShape,
                                ambientColor = (if (record.isElectricRecorded) ElectricColor else WaterColor)
                                    .copy(alpha = dotGlow),
                                spotColor = (if (record.isElectricRecorded) ElectricColor else WaterColor)
                                    .copy(alpha = dotGlow)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (record.isElectricRecorded) ElectricColor else WaterColor,
                                        (if (record.isElectricRecorded) ElectricColor else WaterColor)
                                            .copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )

                    Text(
                        text = record.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ── 内容区域 ──
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (record.isElectricRecorded && record.electricTotal != null) {
                            MeterValueCard(
                                icon = Icons.Default.Bolt,
                                iconColor = ElectricColor,
                                label = "电量",
                                value = Formatters.formatElectric(record.electricTotal),
                                unit = "度",
                                peak = record.electricPeak,
                                valley = record.electricValley,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (record.isWaterRecorded && record.waterTotal != null) {
                            MeterValueCard(
                                icon = Icons.Default.WaterDrop,
                                iconColor = WaterColor,
                                label = "水表",
                                value = Formatters.formatWater(record.waterTotal),
                                unit = "吨",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 备注
                    if (!record.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            NeonBlue.copy(alpha = 0.1f),
                                            DarkSurface
                                        )
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = record.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonBlue,
                                fontFamily = MonoFontFamily
                            )
                        }
                    }
                }
            }

            // ── 行内操作按钮（点击展开/收起） ──
            AnimatedVisibility(
                visible = showActions,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(2.dp))

                    // 发光分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        ElectricColor.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 编辑按钮
                        ActionChip(
                            icon = Icons.Default.Edit,
                            label = "编辑",
                            color = ElectricColor,
                            onClick = {
                                showActions = false
                                onEdit?.invoke(record)
                            }
                        )
                        // 删除按钮
                        ActionChip(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            color = Color(0xFFFF6B6B),
                            onClick = { showDeleteDialog = true }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 行内操作胶囊按钮
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val chipScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "chipScale"
    )

    Row(
        modifier = Modifier
            .scale(chipScale)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (isPressed) 0.18f else 0.08f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            color = color,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 数值卡片（不包含点击逻辑，保持纯展示）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MeterValueCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    unit: String,
    peak: Double? = null,
    valley: Double? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkSurface, DarkSurface.copy(alpha = 0.8f))
                )
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = MonoFontFamily
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (peak != null && valley != null) {
            ValueRow("总", value, ElectricColor, unit)
            Spacer(modifier = Modifier.height(4.dp))
            ValueRow("峰", Formatters.formatInt(peak), ElectricPeakColor, unit)
            Spacer(modifier = Modifier.height(4.dp))
            ValueRow("谷", Formatters.formatInt(valley), ElectricValleyColor, unit)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFontFamily,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, color: Color, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "$label ",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFontFamily,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 1.dp)
        )
    }
}
