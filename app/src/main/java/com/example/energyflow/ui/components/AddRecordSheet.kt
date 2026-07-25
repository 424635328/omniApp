package com.example.energyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import com.example.energyflow.data.MeterRecord
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
fun AddRecordSheet(
    initiallyShowPeakValley: Boolean = false,
    onPeakValleyExpandedChange: (Boolean) -> Unit = {},
    latestRecord: MeterRecord? = null,
    prefillRecord: RecordData? = null,
    quickTags: List<String> = listOf("❄️开冰箱", "🔇关冰箱", "👥两家合用", "❄️空调", "🧺洗衣机"),
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

    var isGasEnabled by remember { mutableStateOf(false) }
    var gasTotal by remember { mutableStateOf("") }

    var note by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // ── OCR 预填：有 prefillRecord 时自动填入 ──
    LaunchedEffect(prefillRecord) {
        prefillRecord?.let { r ->
            selectedDate = r.timestamp.toLocalDate()
            selectedTime = r.timestamp.toLocalTime().withSecond(0).withNano(0)
            val f = { d: Double -> String.format(Locale.US, "%.2f", d) }
            if (r.isElectric && r.electricTotal != null) {
                isElectricEnabled = true
                electricTotal = f(r.electricTotal)
                r.electricPeak?.let { electricPeak = f(it); showPeakValley = true }
                r.electricValley?.let { electricValley = f(it); showPeakValley = true }
            }
            if (r.isWater && r.waterTotal != null) {
                isWaterEnabled = true
                waterTotal = f(r.waterTotal)
            }
            r.note?.let { note = it }
        }
    }

    // ── 焦点离开时推导 ──
    // changed 标记刚编辑的字段，当三值齐全时重新计算对侧字段
    fun deriveElectric(changed: String? = null) {
        val p = electricPeak.toDoubleOrNull()
        val v = electricValley.toDoubleOrNull()
        val t = electricTotal.toDoubleOrNull()
        val fmt = { d: Double -> String.format(Locale.US, "%.2f", d) }
        when {
            // 缺一补一
            p != null && v != null && t == null -> electricTotal = fmt(p + v)
            p != null && t != null && v == null -> electricValley = fmt(t - p)
            v != null && t != null && p == null -> electricPeak = fmt(t - v)
            // 三值齐全 → 对侧重算：编辑峰则重算谷，编辑谷则重算峰
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
            else -> {
                val derived = when {
                    p != null && v != null -> p + v
                    else -> t
                }
                Triple(p, v, derived)
            }
        }
    }

    // 验证
    val isElectricValid = !isElectricEnabled ||
        electricTotal.toDoubleOrNull() != null ||
        (electricPeak.toDoubleOrNull() != null && electricValley.toDoubleOrNull() != null)
    val isWaterValid = !isWaterEnabled || waterTotal.toDoubleOrNull() != null
    val isGasValid = !isGasEnabled || gasTotal.toDoubleOrNull() != null
    val canSave = (isElectricEnabled || isWaterEnabled || isGasEnabled) && isElectricValid && isWaterValid && isGasValid

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

    val gasError = when {
        isGasEnabled && gasTotal.isEmpty() -> "请输入燃气读数"
        isGasEnabled && gasTotal.toDoubleOrNull() == null -> "数值格式错误"
        else -> null
    }

    // ── 自动聚焦电表输入框 ──
    val electricFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { electricFocus.requestFocus() }

    // ── 键盘相关 ──
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // ── 保存按钮呼吸微动效（表单有效时） ──
    val infiniteTransition = rememberInfiniteTransition(label = "saveBreathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .imePadding()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
    ) {
        // ── 固定顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "取消", tint = TextSecondary)
            }
            Text(
                text = "添加记录",
                style = MaterialTheme.typography.titleLarge,
                color = NeonYellow,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
            // 占位保持标题居中
            Spacer(modifier = Modifier.size(48.dp))
        }

        // ── 滚动内容 ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── 上次读数 ──
            latestRecord?.let { last ->
            val lastElectric = last.electricTotal?.let { Formatters.formatDecimal2(it) }
            val lastWater = last.waterTotal?.let { Formatters.formatWater(it) }
            val lastGas = last.gasTotal?.let { Formatters.formatGas(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard.copy(alpha = 0.6f))
                    .clickable {
                        val f = { d: Double -> String.format(Locale.US, "%.2f", d) }
                        // 一键沿用 — 填入上次的值
                        if (last.isElectricRecorded) {
                            isElectricEnabled = true
                            electricTotal = last.electricTotal?.let { f(it) } ?: ""
                            electricPeak = last.electricPeak?.let { f(it) } ?: ""
                            electricValley = last.electricValley?.let { f(it) } ?: ""
                            if (last.electricPeak != null || last.electricValley != null) {
                                showPeakValley = true
                                onPeakValleyExpandedChange(true)
                            }
                        }
                        if (last.isWaterRecorded) {
                            isWaterEnabled = true
                            waterTotal = last.waterTotal?.let { f(it) } ?: ""
                        }
                        if (last.isGasRecorded) {
                            isGasEnabled = true
                            gasTotal = last.gasTotal?.let { f(it) } ?: ""
                        }
                        // 自动使用上次的日期+1天
                        selectedDate = last.timestamp.toLocalDate().plusDays(1)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "上次读数",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (lastElectric != null) {
                            Text(
                                text = "⚡ $lastElectric 度",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricColor,
                                fontFamily = MonoFontFamily
                            )
                        }
                        if (lastWater != null) {
                            Text(
                                text = "💧 $lastWater 吨",
                                style = MaterialTheme.typography.bodySmall,
                                color = WaterColor,
                                fontFamily = MonoFontFamily
                            )
                        }
                        if (lastGas != null) {
                            Text(
                                text = "🔥 $lastGas m³",
                                style = MaterialTheme.typography.bodySmall,
                                color = GasColor,
                                fontFamily = MonoFontFamily
                            )
                        }
                    }
                }
                Text(
                    text = "沿用 ↻",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricColor,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                onDoneEditing = { deriveElectric("total") },
                unit = "度",
                color = ElectricColor,
                error = electricError,
                modifier = Modifier.focusRequester(electricFocus)
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
                        onDoneEditing = { deriveElectric("peak") },
                        unit = "度",
                        color = ElectricPeakColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MeterInputField(
                        label = "谷电量",
                        value = electricValley,
                        onValueChange = { electricValley = it },
                        onDoneEditing = { deriveElectric("valley") },
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
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "峰谷合计吻合 (误差 ${Formatters.formatError(diff)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
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

        // 燃气表
        MeterSection(
            title = "燃气",
            icon = Icons.Default.Bolt,
            iconColor = GasColor,
            isEnabled = isGasEnabled,
            onToggle = { isGasEnabled = it }
        ) {
            MeterInputField(
                label = "燃气读数",
                value = gasTotal,
                onValueChange = { gasTotal = it },
                unit = "m³",
                color = GasColor,
                error = gasError
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 备注
        NoteSection(
            note = note,
            onNoteChange = { note = it },
            quickTags = quickTags
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── 固定底部保存按钮（始终可见，呼吸微动效） ──
    Button(
        onClick = {
            if (canSave) {
                keyboardController?.hide()
                val (peak, valley, total) = deriveSaveValues()
                val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                onSave(
                    RecordData(
                        timestamp = timestamp,
                        isElectric = isElectricEnabled,
                        electricTotal = total,
                        electricPeak = peak,
                        electricValley = valley,
                        isWater = isWaterEnabled,
                        waterTotal = waterTotal.toDoubleOrNull(),
                        isGas = isGasEnabled,
                        gasTotal = gasTotal.toDoubleOrNull(),
                        note = note.ifBlank { null }
                    )
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp)
            .scale(if (canSave) breatheScale else 0.97f)
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
    modifier: Modifier = Modifier,
    error: String? = null,
    onDoneEditing: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) ErrorNeon else TextSecondary,
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
                modifier = Modifier.weight(1f).then(modifier).onFocusChanged { if (!it.isFocused) onDoneEditing() },
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
                    focusedBorderColor = if (error != null) ErrorNeon else color,
                    unfocusedBorderColor = if (error != null) ErrorNeon else DarkSurface,
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
            color = ErrorNeon,
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

@Immutable
data class RecordData(
    val timestamp: LocalDateTime,
    val isElectric: Boolean,
    val electricTotal: Double?,
    val electricPeak: Double?,
    val electricValley: Double?,
    val isWater: Boolean,
    val waterTotal: Double?,
    val isGas: Boolean = false,
    val gasTotal: Double? = null,
    val note: String?
)
