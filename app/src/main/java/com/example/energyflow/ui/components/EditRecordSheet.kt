package com.example.energyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.ui.utils.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordSheet(
    record: MeterRecord,
    onSave: (RecordData) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(record.timestamp.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(record.timestamp.toLocalTime().withSecond(0).withNano(0)) }

    var isElectricEnabled by remember { mutableStateOf(record.isElectricRecorded) }
    var electricTotal by remember { mutableStateOf(record.electricTotal?.let { Formatters.formatElectric(it) } ?: "") }
    var electricPeak by remember { mutableStateOf(record.electricPeak?.let { Formatters.formatElectric(it) } ?: "") }
    var electricValley by remember { mutableStateOf(record.electricValley?.let { Formatters.formatElectric(it) } ?: "") }
    var showPeakValley by remember { mutableStateOf(record.electricPeak != null || record.electricValley != null) }

    var isWaterEnabled by remember { mutableStateOf(record.isWaterRecorded) }
    var waterTotal by remember { mutableStateOf(record.waterTotal?.let { Formatters.formatWater(it) } ?: "") }

    var isGasEnabled by remember { mutableStateOf(record.isGasRecorded) }
    var gasTotal by remember { mutableStateOf(record.gasTotal?.let { Formatters.formatGas(it) } ?: "") }

    var note by remember { mutableStateOf(record.note ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // ── 焦点离开时推导 ──
    fun deriveElectric(changed: String? = null) {
        val p = electricPeak.toDoubleOrNull()
        val v = electricValley.toDoubleOrNull()
        val t = electricTotal.toDoubleOrNull()
        val fmt = { d: Double -> String.format(Locale.US, "%.2f", d) }
        when {
            p != null && v != null && t == null -> electricTotal = fmt(p + v)
            p != null && t != null && v == null -> electricValley = fmt(t - p)
            v != null && t != null && p == null -> electricPeak = fmt(t - v)
            p != null && v != null && t != null -> {
                when (changed) {
                    "peak" -> electricValley = fmt(t - p)
                    "valley" -> electricPeak = fmt(t - v)
                }
            }
        }
    }

    // ── 保存时安全推导（兜底） ──
    fun deriveSaveValues(): Triple<Double?, Double?, Double?> {
        val p = electricPeak.toDoubleOrNull()
        val v = electricValley.toDoubleOrNull()
        val t = electricTotal.toDoubleOrNull()
        return when {
            p != null && v != null -> Triple(p, v, t ?: (p + v))
            p != null && t != null -> Triple(p, (t - p).coerceAtLeast(0.0), t)
            v != null && t != null -> Triple((t - v).coerceAtLeast(0.0), v, t)
            else -> Triple(p, v, t)
        }
    }

    val isElectricValid = !isElectricEnabled || electricTotal.toDoubleOrNull() != null
    val isWaterValid = !isWaterEnabled || waterTotal.toDoubleOrNull() != null
    val isGasValid = !isGasEnabled || gasTotal.toDoubleOrNull() != null
    val canSave = (isElectricEnabled || isWaterEnabled || isGasEnabled) && isElectricValid && isWaterValid && isGasValid

    val saveButtonScale by animateFloatAsState(
        targetValue = if (canSave) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "save_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "取消", tint = TextSecondary)
            }
            Text(
                text = "编辑记录",
                style = MaterialTheme.typography.titleLarge,
                color = NeonYellow,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    if (canSave) {
                        val (peak, valley, total) = deriveSaveValues()
                    onSave(RecordData(
                            timestamp = LocalDateTime.of(selectedDate, selectedTime),
                            isElectric = isElectricEnabled,
                            electricTotal = total,
                            electricPeak = peak,
                            electricValley = valley,
                            isWater = isWaterEnabled,
                            waterTotal = waterTotal.toDoubleOrNull(),
                            isGas = isGasEnabled,
                            gasTotal = gasTotal.toDoubleOrNull(),
                            note = note.ifBlank { null }
                        ))
                    }
                },
                enabled = canSave
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存", tint = if (canSave) ElectricColor else TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 日期时间
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeChip(Icons.Default.CalendarMonth, "日期", selectedDate.format(DateTimeFormatter.ofPattern("MM月dd日")), { showDatePicker = true }, Modifier.weight(1f))
            DateTimeChip(Icons.Default.AccessTime, "时间", selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")), { showTimePicker = true }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 电表
        SectionCard("电表", Icons.Default.Bolt, ElectricColor, isElectricEnabled, { isElectricEnabled = it }) {
            InputField("总电量", electricTotal, { electricTotal = it }, "度", ElectricColor, onDoneEditing = { deriveElectric("total") })
            Row(modifier = Modifier.fillMaxWidth().clickable { showPeakValley = !showPeakValley }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (showPeakValley) ElectricColor else TextSecondary))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showPeakValley) "收起峰谷" else "展开峰谷明细", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = MonoFontFamily)
            }
            AnimatedVisibility(visible = showPeakValley, enter = expandVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)), exit = shrinkVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))) {
                Column {
                    InputField("峰电量", electricPeak, { electricPeak = it }, "度", ElectricPeakColor, onDoneEditing = { deriveElectric("peak") })
                    Spacer(modifier = Modifier.height(8.dp))
                    InputField("谷电量", electricValley, { electricValley = it }, "度", ElectricValleyColor, onDoneEditing = { deriveElectric("valley") })
                    val peak = electricPeak.toDoubleOrNull()
                    val valley = electricValley.toDoubleOrNull()
                    val total = electricTotal.toDoubleOrNull()
                    if (peak != null && valley != null && total != null) {
                        val diff = kotlin.math.abs(peak + valley - total)
                        if (diff < 0.1) {
                            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("峰谷合计吻合 (误差 ${Formatters.formatError(diff)})", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontFamily = MonoFontFamily)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 水表
        SectionCard("水表", Icons.Default.WaterDrop, WaterColor, isWaterEnabled, { isWaterEnabled = it }) {
            InputField("水表读数", waterTotal, { waterTotal = it }, "吨", WaterColor)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 燃气
        SectionCard("燃气", Icons.Default.Bolt, GasColor, isGasEnabled, { isGasEnabled = it }) {
            InputField("燃气读数", gasTotal, { gasTotal = it }, "m³", GasColor)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 备注
        NoteSection(note, { note = it })

        Spacer(modifier = Modifier.height(24.dp))

        // 保存按钮
        Button(
            onClick = {
                if (canSave) {
                    val (peak, valley, total) = deriveSaveValues()
                    onSave(RecordData(
                        timestamp = LocalDateTime.of(selectedDate, selectedTime),
                        isElectric = isElectricEnabled,
                        electricTotal = total,
                        electricPeak = peak,
                        electricValley = valley,
                        isWater = isWaterEnabled,
                        waterTotal = waterTotal.toDoubleOrNull(),
                        isGas = isGasEnabled,
                        gasTotal = gasTotal.toDoubleOrNull(),
                        note = note.ifBlank { null }
                    ))
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).scale(saveButtonScale)
                .shadow(if (canSave) 4.dp else 0.dp, RoundedCornerShape(12.dp), ambientColor = ElectricColor.copy(0.3f), spotColor = ElectricColor.copy(0.3f)),
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(ElectricColor, DarkBackground, DarkCard, TextSecondary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("保存修改", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text("确定", color = ElectricColor) } },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消", color = TextSecondary) } },
            colors = DatePickerDefaults.colors(DarkSurface)
        ) { DatePicker(state, colors = DatePickerDefaults.colors(DarkSurface, ElectricColor, DarkBackground, ElectricColor, ElectricColor)) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(selectedTime.hour, selectedTime.minute)
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton({ selectedTime = LocalTime.of(state.hour, state.minute); showTimePicker = false }) { Text("确定", color = ElectricColor) } },
            dismissButton = { TextButton({ showTimePicker = false }) { Text("取消", color = TextSecondary) } },
            colors = DatePickerDefaults.colors(DarkSurface)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                TimePicker(state, colors = TimePickerDefaults.colors(ElectricColor, ElectricColor, DarkBackground, DarkCard, DarkBackground))
            }
        }
    }
}

@Composable
private fun DateTimeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(DarkCard, DarkCard.copy(0.8f)))).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = ElectricColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = MonoFontFamily)
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, isEnabled: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(DarkCard, DarkCard.copy(0.9f)))).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, title, tint = if (isEnabled) iconColor else TextSecondary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (isEnabled) TextPrimary else TextSecondary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            }
            Switch(isEnabled, onToggle, colors = SwitchDefaults.colors(iconColor, iconColor.copy(0.3f), TextSecondary, DarkSurface))
        }
        AnimatedVisibility(isEnabled, enter = expandVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)), exit = shrinkVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))) {
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun InputField(label: String, value: String, onValueChange: (String) -> Unit, unit: String, color: Color, onDoneEditing: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontFamily = MonoFontFamily, modifier = Modifier.width(64.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(it) },
            modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onDoneEditing() },
            placeholder = { Text("0.00", color = TextSecondary.copy(0.5f), fontFamily = MonoFontFamily) },
            suffix = { Text(unit, color = TextSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = DarkSurface,
                cursorColor = color,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun NoteSection(note: String, onNoteChange: (String) -> Unit) {
    val tags = listOf("❄️开冰箱", "🔇关冰箱", "👥两家合用", "❄️空调", "🧺洗衣机")
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).padding(16.dp)) {
        Text("备注", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.take(3).forEach { tag ->
                val sel = note.contains(tag)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) ElectricColor.copy(0.2f) else DarkSurface)
                        .clickable { onNoteChange(if (sel) note.replace(tag, "").trim() else "$note $tag".trim()) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(tag, style = MaterialTheme.typography.labelMedium, color = if (sel) ElectricColor else TextSecondary, fontFamily = MonoFontFamily)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.drop(3).forEach { tag ->
                val sel = note.contains(tag)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) ElectricColor.copy(0.2f) else DarkSurface)
                        .clickable { onNoteChange(if (sel) note.replace(tag, "").trim() else "$note $tag".trim()) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(tag, style = MaterialTheme.typography.labelMedium, color = if (sel) ElectricColor else TextSecondary, fontFamily = MonoFontFamily)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入备注...", color = TextSecondary.copy(0.5f), fontFamily = MonoFontFamily) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricColor,
                unfocusedBorderColor = DarkSurface,
                cursorColor = ElectricColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
        )
    }
}
