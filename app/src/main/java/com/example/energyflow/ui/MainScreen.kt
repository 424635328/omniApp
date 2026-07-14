package com.example.energyflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.data.AnomalyWarning
import com.example.energyflow.ui.components.AddRecordSheet
import com.example.energyflow.ui.components.BatchImportSheet
import com.example.energyflow.ui.components.EditRecordSheet
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.WarningNeon
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class RecordFilter { ALL, ELECTRIC, WATER, GAS, WITH_NOTES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val records by viewModel.allRecords.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val anomalyWarnings by viewModel.anomalyWarnings.collectAsState()
    val showAnomalyDialog by viewModel.showAnomalyDialog.collectAsState()
    val peakValleyExpanded by viewModel.peakValleyExpanded.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val isPastFirstPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    var currentFilter by remember { mutableStateOf(RecordFilter.ALL) }
    var filterStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var filterEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, currentFilter, filterStartDate, filterEndDate) {
        val typeFiltered = when (currentFilter) {
            RecordFilter.ALL -> records
            RecordFilter.ELECTRIC -> records.filter { it.isElectricRecorded }
            RecordFilter.WATER -> records.filter { it.isWaterRecorded }
            RecordFilter.GAS -> records.filter { it.isGasRecorded }
            RecordFilter.WITH_NOTES -> records.filter { !it.note.isNullOrBlank() }
        }
        if (filterStartDate != null || filterEndDate != null) {
            typeFiltered.filter { record ->
                val date = record.timestamp.toLocalDate()
                (filterStartDate == null || !date.isBefore(filterStartDate)) &&
                (filterEndDate == null || !date.isAfter(filterEndDate))
            }
        } else typeFiltered
    }

    // 滚动到底部附近时加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showBatchImport by remember { mutableStateOf(false) }
    var pendingBatchImport by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<com.example.energyflow.data.MeterRecord?>(null) }
    val filterCounts by viewModel.filterCounts.collectAsState()

    // ── 返回键拦截：有底部表单打开时 → 关闭表单而不是退出应用 ──
    androidx.activity.compose.BackHandler(enabled = editingRecord != null) {
        editingRecord = null
    }
    androidx.activity.compose.BackHandler(enabled = showBatchImport) {
        showBatchImport = false
        pendingBatchImport = false
    }
    androidx.activity.compose.BackHandler(enabled = showAddSheet) {
        showAddSheet = false
    }

    // FAB 动画 — 主按钮 + 小按钮缩放
    val fabScale by animateFloatAsState(
        targetValue = if (showAddSheet || showBatchImport) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_scale"
    )

    // 处理 UI 状态 → Snackbar + 触觉反馈
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Success -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
                if (pendingBatchImport) {
                    showBatchImport = false
                    pendingBatchImport = false
                }
            }
            is UiState.Warning -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar("⚠️ ${state.message}")
                viewModel.clearState()
                if (pendingBatchImport) {
                    showBatchImport = false
                    pendingBatchImport = false
                }
            }
            is UiState.Error -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
                if (pendingBatchImport) {
                    pendingBatchImport = false
                }
            }
            else -> {}
        }
    }

    // ⚠️ 异常确认弹窗
    if (showAnomalyDialog && anomalyWarnings.isNotEmpty()) {
        AnomalyWarningDialog(
            warnings = anomalyWarnings,
            onConfirm = { viewModel.confirmSaveWithAnomaly() },
            onDismiss = { viewModel.cancelSaveWithAnomaly() }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkCard,
                    contentColor = ElectricColor,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        floatingActionButton = {
            if (!showAddSheet && !showBatchImport) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 回到顶部（列表滚动超过3项时显示）
                    AnimatedVisibility(
                        visible = isPastFirstPage,
                        enter = expandVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
                        exit = shrinkVertically(tween(200))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(
                                    animateFloatAsState(
                                        targetValue = if (isPastFirstPage) 1f else 0f,
                                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                                        label = "top_scale"
                                    ).value
                                )
                                .shadow(6.dp, CircleShape, ambientColor = ElectricColor.copy(0.3f))
                                .clip(CircleShape)
                                .background(DarkCard)
                                .clickable { coroutineScope.launch { listState.animateScrollToItem(0) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "回到顶部", tint = ElectricColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    FABColumn(
                        scale = fabScale,
                        onBatchImport = { showBatchImport = true },
                        onAddRecord = { showAddSheet = true }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeTopBar(recordCount = records.size)

                if (records.isNotEmpty()) {
                    val counts = remember(records, filterCounts) {
                        RecordFilter.entries.associateWith { f ->
                            when (f) {
                                RecordFilter.ALL -> filterCounts["total"] ?: records.size
                                RecordFilter.ELECTRIC -> filterCounts["electric"] ?: 0
                                RecordFilter.WATER -> filterCounts["water"] ?: 0
                                RecordFilter.GAS -> filterCounts["gas"] ?: 0
                                RecordFilter.WITH_NOTES -> filterCounts["notes"] ?: 0
                            }
                        }
                    }
                    FilterBar(
                        currentFilter = currentFilter,
                        onFilterChange = { currentFilter = it },
                        counts = counts
                    )
                    DateFilterBar(
                        startDate = filterStartDate,
                        endDate = filterEndDate,
                        onStartClick = { showStartDatePicker = true },
                        onEndClick = { showEndDatePicker = true },
                        onClear = { filterStartDate = null; filterEndDate = null }
                    )
                }

                if (records.isEmpty() && !showAddSheet && !showBatchImport) {
                    HomeEmptyState(onAddClick = { showAddSheet = true })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(items = filteredRecords, key = { _, r -> r.id }) { index, record ->
                            // 较前一次记录的消耗量（records 按时间倒序，前一次在后）
                            val prevRecord = filteredRecords.getOrNull(index + 1)
                            val elecDelta = if (prevRecord != null && record.electricTotal != null && prevRecord.electricTotal != null)
                                record.electricTotal!! - prevRecord.electricTotal!! else null
                            val waterDelta = if (prevRecord != null && record.waterTotal != null && prevRecord.waterTotal != null)
                                record.waterTotal!! - prevRecord.waterTotal!! else null
                            val gasDelta = if (prevRecord != null && record.gasTotal != null && prevRecord.gasTotal != null)
                                record.gasTotal!! - prevRecord.gasTotal!! else null
                            TimelineItem(
                                record = record,
                                electricDelta = elecDelta,
                                waterDelta = waterDelta,
                                gasDelta = gasDelta,
                                onDelete = { viewModel.deleteRecord(it) },
                                onEdit = { editingRecord = it },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(300),
                                    fadeOutSpec = tween(300),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            )
                        }
                    }
                }
            }

            // ── 底部表单 — 添加记录 ──
            if (showAddSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showAddSheet = false },
                    sheetState = sheetState,
                    containerColor = DarkBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = Color.Black.copy(alpha = 0.6f)
                ) {
                    AddRecordSheet(
                        initiallyShowPeakValley = peakValleyExpanded,
                        onPeakValleyExpandedChange = viewModel::setPeakValleyExpanded,
                        latestRecord = records.firstOrNull(),
                        onSave = { recordData ->
                            viewModel.validateAndSave(recordData)
                            showAddSheet = false
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        onDismiss = { showAddSheet = false }
                    )
                }
            }

            // ── 底部表单 — 批量导入 ──
            if (showBatchImport) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showBatchImport = false },
                    sheetState = sheetState,
                    containerColor = DarkBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = Color.Black.copy(alpha = 0.6f)
                ) {
                    BatchImportSheet(
                        onImport = { text ->
                            pendingBatchImport = true
                            viewModel.batchImport(text)
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        importing = pendingBatchImport,
                        onDismiss = { showBatchImport = false }
                    )
                }
            }

            // ── 底部表单 — 编辑记录 ──
            editingRecord?.let { record ->
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { editingRecord = null },
                    sheetState = sheetState,
                    containerColor = DarkBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = Color.Black.copy(alpha = 0.6f)
                ) {
                    EditRecordSheet(
                        record = record,
                        onSave = { recordData ->
                            viewModel.updateRecord(record, recordData)
                            editingRecord = null
                        },
                        onDismiss = { editingRecord = null }
                    )
                }
            }
        }
    }

    // ── 日期选择器 ──
    if (showStartDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filterStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        filterStartDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showStartDatePicker = false
                }) { Text("确定", color = ElectricColor) }
            },
            dismissButton = {
                TextButton({ showStartDatePicker = false }) { Text("取消", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(state, colors = DatePickerDefaults.colors(containerColor = DarkSurface,
                selectedDayContainerColor = ElectricColor, selectedDayContentColor = DarkBackground,
                todayContentColor = ElectricColor, todayDateBorderColor = ElectricColor))
        }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filterEndDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        filterEndDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("确定", color = ElectricColor) }
            },
            dismissButton = {
                TextButton({ showEndDatePicker = false }) { Text("取消", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(state, colors = DatePickerDefaults.colors(containerColor = DarkSurface,
                selectedDayContainerColor = ElectricColor, selectedDayContentColor = DarkBackground,
                todayContentColor = ElectricColor, todayDateBorderColor = ElectricColor))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 🚨 异常警告弹窗
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AnomalyWarningDialog(
    warnings: List<AnomalyWarning>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                "⚠️ 输入异常",
                color = ErrorNeon,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                warnings.forEach { warning ->
                    when (warning) {
                        is AnomalyWarning.ReadingLowerThanPrevious -> {
                            Text(
                                warning.message,
                                color = ErrorNeon,
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is AnomalyWarning.SpikeDetected -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚡ ${warning.detail}",
                                color = WarningNeon,
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "请确认读数是否正确，或返回修改。",
                    color = TextSecondary,
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认保存", color = ElectricColor, fontFamily = MonoFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("返回修改", color = TextSecondary, fontFamily = MonoFontFamily)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
// FAB 列 — 带按压动效
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FABColumn(
    scale: Float,
    onBatchImport: () -> Unit,
    onAddRecord: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.scale(scale)
    ) {
        // 小 FAB — 批量导入
        SmallFAB(onClick = onBatchImport) {
            Icon(
                Icons.Default.ContentPaste,
                contentDescription = "批量导入",
                modifier = Modifier.size(20.dp)
            )
        }

        // 主 FAB — 添加记录
        MainFAB(onClick = onAddRecord) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加记录",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun MainFAB(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "mainFabScale"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.scale(btnScale),
        containerColor = ElectricColor,
        contentColor = DarkBackground,
        shape = CircleShape,
        interactionSource = interactionSource,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (isPressed) 2.dp else 6.dp,
            pressedElevation = 2.dp
        )
    ) {
        content()
    }
}

@Composable
private fun SmallFAB(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "smallFabScale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(btnScale)
            .shadow(
                elevation = if (isPressed) 2.dp else 5.dp,
                shape = CircleShape,
                ambientColor = ElectricColor.copy(alpha = 0.2f),
                spotColor = ElectricColor.copy(alpha = 0.2f)
            )
            .clip(CircleShape)
            .background(DarkCard)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════
// 底部表单遮罩
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// 筛选栏
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FilterBar(
    currentFilter: RecordFilter,
    onFilterChange: (RecordFilter) -> Unit,
    counts: Map<RecordFilter, Int>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RecordFilter.entries.forEach { filter ->
            val isSelected = filter == currentFilter
            val count = counts[filter] ?: 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) ElectricColor.copy(alpha = 0.12f) else DarkCard.copy(alpha = 0.5f))
                    .then(
                        if (isSelected) Modifier.drawBehind {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(ElectricColor.copy(alpha = 0.25f), ElectricColor.copy(alpha = 0.08f))
                                ),
                                style = Stroke(width = 1.5f),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        } else Modifier
                    )
                    .clickable { onFilterChange(filter) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when (filter) {
                            RecordFilter.ALL -> "全部"
                            RecordFilter.ELECTRIC -> "⚡电"
                            RecordFilter.WATER -> "💧水"
                            RecordFilter.GAS -> "🔥气"
                            RecordFilter.WITH_NOTES -> "📝备注"
                        },
                        color = if (isSelected) ElectricColor else TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (count > 0) {
                        Text(
                            "$count",
                            color = if (isSelected) ElectricColor.copy(alpha = 0.7f) else TextTertiary,
                            fontFamily = MonoFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 日期筛选栏
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DateFilterBar(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onClear: () -> Unit
) {
    val hasFilter = startDate != null || endDate != null
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM.dd")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 开始日期
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ElectricColor.copy(alpha = 0.08f))
                .clickable { onStartClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📅", fontSize = 11.sp)
                Text(
                    if (startDate != null) "从 ${startDate!!.format(fmt)}" else "开始日期",
                    color = if (startDate != null) ElectricColor else TextSecondary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = if (startDate != null) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        // 结束日期
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ElectricColor.copy(alpha = 0.08f))
                .clickable { onEndClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📅", fontSize = 11.sp)
                Text(
                    if (endDate != null) "到 ${endDate!!.format(fmt)}" else "结束日期",
                    color = if (endDate != null) ElectricColor else TextSecondary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = if (endDate != null) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        // 清除按钮
        if (hasFilter) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ErrorNeon.copy(alpha = 0.1f))
                    .clickable { onClear() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✕ 清除",
                    color = ErrorNeon,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 顶栏 — 重新设计
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HomeTopBar(recordCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkBackground.copy(alpha = 0.97f),
                        DarkBackground.copy(alpha = 0.85f),
                        DarkBackground.copy(alpha = 0f)
                    )
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Column {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 图标徽标
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ElectricColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "能耗手记",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 统计行
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 记录计数 pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricColor.copy(alpha = 0.08f))
                        .border(1.dp, ElectricColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$recordCount",
                            color = ElectricColor,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "条记录",
                            color = TextTertiary,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }

                if (recordCount == 0) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "添加第一条能耗数据",
                        color = TextTertiary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 空状态 — 呼吸动画
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HomeEmptyState(onAddClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 呼吸光环
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(breatheScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ElectricColor.copy(alpha = breatheAlpha),
                                ElectricColor.copy(alpha = breatheAlpha * 0.3f),
                                DarkCard
                            )
                        )
                    )
                    .border(
                        1.dp,
                        ElectricColor.copy(alpha = breatheAlpha * 0.5f),
                        CircleShape
                    )
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SwipeUp,
                    contentDescription = null,
                    tint = ElectricColor.copy(alpha = breatheAlpha + 0.2f),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "开始记录能耗",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "点击右下角 ＋ 添加第一条记录\n或使用粘贴按钮批量导入数据",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
