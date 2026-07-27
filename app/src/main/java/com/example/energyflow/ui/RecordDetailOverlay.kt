package com.example.energyflow.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.AppCard
import com.example.energyflow.ui.theme.AppSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.ui.utils.Formatters
import java.time.format.DateTimeFormatter

// ── 缓存 DateTimeFormatter ──
private val DetailDateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val DetailTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 全屏记录详情覆盖层。
 * 由 MainScreen 的 SharedTransitionLayout + AnimatedContent 驱动展开/收缩动画，
 * modifier 上会挂载 sharedBounds（key = record.id），与时间线卡片配对。
 */
@Composable
fun RecordDetailOverlay(
    record: MeterRecord,
    electricDelta: Double? = null,
    peakDelta: Double? = null,
    valleyDelta: Double? = null,
    waterDelta: Double? = null,
    gasDelta: Double? = null,
    onEdit: (MeterRecord) -> Unit,
    onDelete: (MeterRecord) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // ── 顶栏：关闭按钮 + 标题 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppCard)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "记录详情",
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // ── 内容区域（可滚动） ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日期时间
            Column {
                Text(
                    record.timestamp.format(DetailDateFmt),
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    record.timestamp.format(DetailTimeFmt),
                    color = TextSecondary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            }

            // 电表
            if (record.isElectricRecorded && record.electricTotal != null) {
                DetailSection(icon = Icons.Default.Bolt, color = ElectricColor, title = "电表") {
                    DetailValueRow(
                        label = "总读数",
                        value = Formatters.formatElecDisplay(record.electricTotal),
                        unit = "度",
                        color = ElectricColor,
                        delta = electricDelta
                    )
                    record.electricPeak?.let { peak ->
                        DetailValueRow(
                            label = "峰",
                            value = Formatters.formatPeakValleyDisplay(peak),
                            unit = "度",
                            color = ElectricPeakColor,
                            delta = peakDelta
                        )
                    }
                    record.electricValley?.let { valley ->
                        DetailValueRow(
                            label = "谷",
                            value = Formatters.formatPeakValleyDisplay(valley),
                            unit = "度",
                            color = ElectricValleyColor,
                            delta = valleyDelta
                        )
                    }
                }
            }

            // 水表
            if (record.isWaterRecorded && record.waterTotal != null) {
                DetailSection(icon = Icons.Default.WaterDrop, color = WaterColor, title = "水表") {
                    DetailValueRow(
                        label = "总读数",
                        value = Formatters.formatWaterDisplay(record.waterTotal),
                        unit = "吨",
                        color = WaterColor,
                        delta = waterDelta
                    )
                }
            }

            // 燃气
            if (record.isGasRecorded && record.gasTotal != null) {
                DetailSection(icon = Icons.Default.Bolt, color = GasColor, title = "燃气") {
                    DetailValueRow(
                        label = "总读数",
                        value = Formatters.formatGasDisplay(record.gasTotal),
                        unit = "m³",
                        color = GasColor,
                        delta = gasDelta
                    )
                }
            }

            // 备注
            if (!record.note.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonBlue.copy(alpha = 0.1f), AppSurface)
                            )
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        "备注",
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        record.note.orEmpty(),
                        color = NeonBlue,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // ── 底部操作按钮 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailActionButton(
                label = "编辑",
                icon = Icons.Default.Edit,
                color = ElectricColor,
                modifier = Modifier.weight(1f),
                onClick = { onEdit(record) }
            )
            DetailActionButton(
                label = "删除",
                icon = Icons.Default.Delete,
                color = ErrorNeon,
                modifier = Modifier.weight(1f),
                onClick = { onDelete(record) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 分类信息卡片（电/水/气各一张）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DetailSection(
    icon: ImageVector,
    color: Color,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppCard)
            .border(0.5.dp, color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                color = TextSecondary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

// ═══════════════════════════════════════════════════════════════
// 读数行：标签 + 读数 + 单位 + 与上一条记录的消耗差值
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DetailValueRow(
    label: String,
    value: String,
    unit: String,
    color: Color,
    delta: Double?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            label,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp,
            modifier = Modifier
                .width(64.dp)
                .padding(bottom = 3.dp)
        )
        Text(
            value,
            color = color,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            unit,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (delta != null) {
            val sign = if (delta >= 0) "+" else ""
            Text(
                "较上次 $sign${Formatters.formatDecimal2(delta)} $unit",
                color = color.copy(alpha = 0.6f),
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 底部操作按钮（编辑 / 删除）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DetailActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            color = color,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
