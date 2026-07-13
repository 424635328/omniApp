package com.example.energyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.MonoFontFamily
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    initiallyShowPeakValley: Boolean = false,
    onPeakValleyExpandedChange: (Boolean) -> Unit = {},
    onSave: (RecordData) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }

    var isElectricEnabled by remember { mutableStateOf(true) }
    var electricTotal by remember { mutableStateOf("") }
    var electricPeak by remember { mutableStateOf("") }
    var electricValley by remember { mutableStateOf("") }
    var showPeakValley by remember(initiallyShowPeakValley) { mutableStateOf(initiallyShowPeakValley) }

    var isWaterEnabled by remember { mutableStateOf(false) }
    var waterTotal by remember { mutableStateOf("") }

    var note by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // 验证
    val isElectricValid = !isElectricEnabled || electricTotal.toDoubleOrNull() != null
    val isWaterValid = !isWaterEnabled || waterTotal.toDoubleOrNull() != null
    val canSave = (isElectricEnabled || isWaterEnabled) && isElectricValid && isWaterValid

    // 错误提示
    val electricError = when {
        isElectricEnabled && electricTotal.isEmpty() -> "请输入电表读数"
        isElectricEnabled && electricTotal.toDoubleOrNull() == null -> "数值格式错误"
        else -> null
    }

    val waterError = when {
        isWaterEnabled && waterTotal.isEmpty() -> "请输入水表读数"
        isWaterEnabled && waterTotal.toDoubleOrNull() == null -> "数值格式错误"
        else -> null
    }

    // 保存按钮动画
    val saveButtonScale by animateFloatAsState(
        targetValue = if (canSave) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "save_button_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "取消",
                    tint = TextSecondary
                )
            }

            Text(
                text = "添加记录",
                style = MaterialTheme.typography.titleLarge,
                color = NeonYellow,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    if (canSave) {
                        val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                        onSave(
                            RecordData(
                                timestamp = timestamp,
                                isElectric = isElectricEnabled,
                                electricTotal = electricTotal.toDoubleOrNull(),
                                electricPeak = electricPeak.toDoubleOrNull(),
                                electricValley = electricValley.toDoubleOrNull(),
                                isWater = isWaterEnabled,
                                waterTotal = waterTotal.toDoubleOrNull(),
                                note = note.ifBlank { null }
                            )
                        )
                    }
                },
                enabled = canSave
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存",
                    tint = if (canSave) ElectricColor else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 日期时间选择
        DateTimeSection(
            date = selectedDate,
            time = selectedTime,
            onDateClick = { showDatePicker = true },
            onTimeClick = { showTimePicker = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 电表输入
        MeterSection(
            title = "电表",
            icon = Icons.Default.Bolt,
            iconColor = ElectricColor,
            isEnabled = isElectricEnabled,
            onToggle = { isElectricEnabled = it }
        ) {
            // 总电量
            MeterInputField(
                label = "总电量",
                value = electricTotal,
                onValueChange = { electricTotal = it },
                unit = "度",
                color = ElectricColor,
                error = electricError
            )

            // 峰谷展开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showPeakValley = !showPeakValley
                        onPeakValleyExpandedChange(showPeakValley)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (showPeakValley) ElectricColor else TextSecondary
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showPeakValley) "收起峰谷" else "展开峰谷明细",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
            }

            AnimatedVisibility(
                visible = showPeakValley,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column {
                    MeterInputField(
                        label = "峰电量",
                        value = electricPeak,
                        onValueChange = { electricPeak = it },
                        unit = "度",
                        color = ElectricPeakColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MeterInputField(
                        label = "谷电量",
                        value = electricValley,
                        onValueChange = { electricValley = it },
                        unit = "度",
                        color = ElectricValleyColor
                    )

                    // 校验提示
                    val peak = electricPeak.toDoubleOrNull()
                    val valley = electricValley.toDoubleOrNull()
                    val total = electricTotal.toDoubleOrNull()
                    if (peak != null && valley != null && total != null) {
                        val sum = peak + valley
                        val diff = kotlin.math.abs(sum - total)
                        if (diff < 0.1) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00FF88),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "峰谷合计吻合 (误差 ${Formatters.formatError(diff)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00FF88),
                                    fontFamily = MonoFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 水表输入
        MeterSection(
            title = "水表",
            icon = Icons.Default.WaterDrop,
            iconColor = WaterColor,
            isEnabled = isWaterEnabled,
            onToggle = { isWaterEnabled = it }
        ) {
            MeterInputField(
                label = "水表读数",
                value = waterTotal,
                onValueChange = { waterTotal = it },
                unit = "吨",
                color = WaterColor,
                error = waterError
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 备注
        NoteSection(
            note = note,
            onNoteChange = { note = it },
            quickTags = listOf("❄️开冰箱", "🔇关冰箱", "👥两家合用", "❄️空调", "🧺洗衣机")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 保存按钮
        Button(
            onClick = {
                if (canSave) {
                    val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                    onSave(
                        RecordData(
                            timestamp = timestamp,
                            isElectric = isElectricEnabled,
                            electricTotal = electricTotal.toDoubleOrNull(),
                            electricPeak = electricPeak.toDoubleOrNull(),
                            electricValley = electricValley.toDoubleOrNull(),
                            isWater = isWaterEnabled,
                            waterTotal = waterTotal.toDoubleOrNull(),
                            note = note.ifBlank { null }
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .scale(saveButtonScale)
                .shadow(
                    elevation = if (canSave) 4.dp else 0.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = ElectricColor.copy(alpha = 0.3f),
                    spotColor = ElectricColor.copy(alpha = 0.3f)
                ),
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricColor,
                contentColor = DarkBackground,
                disabledContainerColor = DarkCard,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "保存记录",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 日期选择弹窗
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("确定", color = ElectricColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DarkSurface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    selectedDayContainerColor = ElectricColor,
                    selectedDayContentColor = DarkBackground,
                    todayContentColor = ElectricColor,
                    todayDateBorderColor = ElectricColor
                )
            )
        }
    }

    // 时间选择弹窗
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("确定", color = ElectricColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DarkSurface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = ElectricColor,
                        periodSelectorSelectedContainerColor = ElectricColor,
                        periodSelectorSelectedContentColor = DarkBackground,
                        clockDialColor = DarkCard,
                        clockDialSelectedContentColor = DarkBackground
                    )
                )
            }
        }
    }
}

@Composable
private fun DateTimeSection(
    date: LocalDate,
    time: LocalTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 日期
        DateTimeChip(
            icon = Icons.Default.CalendarMonth,
            label = "日期",
            value = date.format(DateTimeFormatter.ofPattern("MM月dd日")),
            onClick = onDateClick,
            modifier = Modifier.weight(1f)
        )

        // 时间
        DateTimeChip(
            icon = Icons.Default.AccessTime,
            label = "时间",
            value = time.format(DateTimeFormatter.ofPattern("HH:mm")),
            onClick = onTimeClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DateTimeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "chip_scale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        DarkCard,
                        DarkCard.copy(alpha = 0.8f)
                    )
                )
            )
            .clickable(
                onClick = {
                    isPressed = true
                    onClick()
                    isPressed = false
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = ElectricColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = MonoFontFamily
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MeterSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkCard,
                        DarkCard.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = if (isEnabled) iconColor else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isEnabled) TextPrimary else TextSecondary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = iconColor,
                    checkedTrackColor = iconColor.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurface
                )
            )
        }

        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MeterInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    color: Color,
    error: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) Color(0xFFFF6B6B) else TextSecondary,
                fontFamily = MonoFontFamily,
                modifier = Modifier.width(64.dp)
            )

            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    // 只允许数字和小数点
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                        onValueChange(newValue)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "0.00",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontFamily = MonoFontFamily
                    )
                },
                suffix = {
                    Text(
                        text = unit,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                },
                isError = error != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (error != null) Color(0xFFFF6B6B) else color,
                    unfocusedBorderColor = if (error != null) Color(0xFFFF6B6B) else DarkSurface,
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

    // 错误提示
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF6B6B),
            fontFamily = MonoFontFamily,
            modifier = Modifier.padding(start = 64.dp)
        )
    }
}

@Composable
private fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    quickTags: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            text = "备注",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 快捷标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickTags.take(3).forEach { tag ->
                val isSelected = note.contains(tag)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) ElectricColor.copy(alpha = 0.2f) else DarkSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricColor else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            onNoteChange(if (isSelected) note.replace(tag, "").trim() else "$note $tag".trim())
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) ElectricColor else TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickTags.drop(3).forEach { tag ->
                val isSelected = note.contains(tag)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) ElectricColor.copy(alpha = 0.2f) else DarkSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricColor else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            onNoteChange(if (isSelected) note.replace(tag, "").trim() else "$note $tag".trim())
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) ElectricColor else TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 自由输入
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "输入备注或选择快捷标签...",
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
            shape = RoundedCornerShape(10.dp),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonoFontFamily
            )
        )
    }
}

data class RecordData(
    val timestamp: LocalDateTime,
    val isElectric: Boolean,
    val electricTotal: Double?,
    val electricPeak: Double?,
    val electricValley: Double?,
    val isWater: Boolean,
    val waterTotal: Double?,
    val note: String?
)
