package com.example.energyflow.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    var isPressed by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "card_scale"
    )

    // 操作菜单对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "操作",
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = record.timestamp.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")),
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (record.isElectricRecorded && record.electricTotal != null) {
                        Text(
                            text = "电表: ${Formatters.formatElectric(record.electricTotal)}",
                            color = ElectricColor,
                            fontFamily = MonoFontFamily
                        )
                    }
                    if (record.isWaterRecorded && record.waterTotal != null) {
                        Text(
                            text = "水表: ${Formatters.formatWater(record.waterTotal)}",
                            color = WaterColor,
                            fontFamily = MonoFontFamily
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDelete?.invoke(record)
                        }
                    ) {
                        Text("删除", color = Color(0xFFFF6B6B), fontFamily = MonoFontFamily)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onEdit?.invoke(record)
                        }
                    ) {
                        Text("编辑", color = ElectricColor, fontFamily = MonoFontFamily)
                    }
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
            .scale(scale)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ElectricColor.copy(alpha = 0.05f),
                spotColor = ElectricColor.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { isPressed = !isPressed },
                    onLongClick = { showDeleteDialog = true }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 时间线指示器
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp)
                ) {
                    // 日期
                    Text(
                        text = record.timestamp.format(DateTimeFormatter.ofPattern("MM.dd")),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFontFamily
                    )

                    // 时间点
                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .size(10.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = CircleShape,
                                ambientColor = if (record.isElectricRecorded) ElectricColor else WaterColor,
                                spotColor = if (record.isElectricRecorded) ElectricColor else WaterColor
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (record.isElectricRecorded) ElectricColor else WaterColor,
                                        if (record.isElectricRecorded) ElectricColor.copy(alpha = 0.8f) else WaterColor.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )

                    // 时间
                    Text(
                        text = record.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 内容区域
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 数值显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 电表
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

                        // 水表
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
        }
    }
}

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
                    colors = listOf(
                        DarkSurface,
                        DarkSurface.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = MonoFontFamily
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = iconColor,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFontFamily,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = MonoFontFamily,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        // 峰谷值
        if (peak != null && valley != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "峰 ${Formatters.formatInt(peak)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricPeakColor,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp
                )
                Text(
                    text = "谷 ${Formatters.formatInt(valley)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricValleyColor,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp
                )
            }
        }
    }
}
