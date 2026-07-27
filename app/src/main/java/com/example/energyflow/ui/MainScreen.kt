package com.example.energyflow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.energyflow.data.AnomalyWarning
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.components.AddRecordSheet
import com.example.energyflow.ui.components.BatchImportSheet
import com.example.energyflow.ui.components.EditRecordSheet
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.AppCard
import com.example.energyflow.ui.theme.AppSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import com.example.energyflow.ui.theme.WarningNeon
import com.example.energyflow.ui.theme.WaterColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecordFilter { ALL, ELECTRIC, WATER, GAS, WITH_NOTES }

/** 首页时间线相邻记录间的消耗差值（含总电 + 峰谷独立差值） */
private data class RecordDeltas(
    val electric: Double? = null,
    val peak: Double? = null,
    val valley: Double? = null,
    val water: Double? = null,
    val gas: Double? = null
)

// ── 缓存 DateTimeFormatter ──
private val DateDotFmt = java.time.format.DateTimeFormatter.ofPattern("MM.dd")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel(), onScan: (() -> Unit)? = null) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val anomalyWarnings by viewModel.anomalyWarnings.collectAsStateWithLifecycle()
    val showAnomalyDialog by viewModel.showAnomalyDialog.collectAsStateWithLifecycle()
    val peakValleyExpanded by viewModel.peakValleyExpanded.collectAsStateWithLifecycle()
    val tierProgress by viewModel.tierProgress.collectAsStateWithLifecycle()
    val commonTags by viewModel.commonTags.collectAsStateWithLifecycle()
    val insight by viewModel.insight.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val isPastFirstPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    // ── 顶栏折叠状态：快速滚过 30px 后压缩顶栏 ──
    val isCollapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 30
        }
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
        val dateFiltered = if (filterStartDate != null || filterEndDate != null) {
            typeFiltered.filter { record ->
                val date = record.timestamp.toLocalDate()
                (filterStartDate == null || !date.isBefore(filterStartDate)) &&
                    (filterEndDate == null || !date.isAfter(filterEndDate))
            }
        } else {
            typeFiltered
        }

        // DAO 已按 timestamp DESC、id DESC 返回数据；保留原顺序，避免每次筛选再排序。
        dateFiltered.filterIndexed { index, record ->
            val prev = dateFiltered.getOrNull(index - 1) ?: return@filterIndexed true
            if (record.timestamp != prev.timestamp) return@filterIndexed true
            if (!record.isElectricRecorded && !record.isWaterRecorded && !record.isGasRecorded) {
                return@filterIndexed true
            }
            fun same(d1: Double?, d2: Double?): Boolean = when {
                d1 == null && d2 == null -> true
                d1 == null || d2 == null -> false
                else -> kotlin.math.abs(d1 - d2) < 0.1
            }
            !(
                same(record.electricTotal, prev.electricTotal) &&
                    same(record.electricPeak, prev.electricPeak) &&
                    same(record.electricValley, prev.electricValley) &&
                    same(record.waterTotal, prev.waterTotal) &&
                    same(record.gasTotal, prev.gasTotal)
                )
        }
    }

    // ── 预计算 delta（含总电 + 峰谷独立差值），避免每帧重算；列表与详情覆盖层共用 ──
    val recordDeltas = remember(filteredRecords) {
        filteredRecords.mapIndexed { index, record ->
            val prev = filteredRecords.getOrNull(index + 1)
            val elecD = record.electricTotal?.let { rt ->
                prev?.electricTotal?.let { pt -> rt - pt }
            }
            val peakD = record.electricPeak?.let { rp -> prev?.electricPeak?.let { pp -> rp - pp } }
            val valleyD = record.electricValley?.let { rv ->
                prev?.electricValley?.let { pv ->
                    rv -
                        pv
                }
            }
            val waterD = record.waterTotal?.let { rw -> prev?.waterTotal?.let { pw -> rw - pw } }
            val gasD = record.gasTotal?.let { rg -> prev?.gasTotal?.let { pg -> rg - pg } }
            RecordDeltas(elecD, peakD, valleyD, waterD, gasD)
        }
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
    val pendingOcrData by viewModel.pendingOcrData.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOcrData) { if (pendingOcrData != null) showAddSheet = true }
    var showBatchImport by remember { mutableStateOf(false) }
    var pendingBatchImport by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<com.example.energyflow.data.MeterRecord?>(null) }
    var detailRecord by remember { mutableStateOf<MeterRecord?>(null) }
    val filterCounts by viewModel.filterCounts.collectAsStateWithLifecycle()

    // ── 软删除 + Snackbar 撤销（时间线卡片与详情覆盖层共用同一路径） ──
    val deleteWithUndo = remember {
        { deleted: MeterRecord ->
            viewModel.softDelete(deleted)
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "已删除 · 点击撤销",
                    actionLabel = "撤销",
                    withDismissAction = true
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDelete()
                } else {
                    viewModel.finalizeDelete()
                }
            }
            Unit
        }
    }

    // ── 返回键拦截：详情覆盖层/底部表单打开时 → 先关闭它们而不是退出应用 ──
    androidx.activity.compose.BackHandler(enabled = detailRecord != null) {
        detailRecord = null
    }
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
            onMeterReplacement = { viewModel.forceSaveAsMeterReplacement() },
            onDismiss = { viewModel.cancelSaveWithAnomaly() }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = AppCard,
                    contentColor = ElectricColor,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        floatingActionButton = {
            if (!showAddSheet && !showBatchImport && detailRecord == null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 回到顶部（列表滚动超过3项时显示）
                    AnimatedVisibility(
                        visible = isPastFirstPage,
                        enter = expandVertically(
                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        ),
                        exit = shrinkVertically(tween(200))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(
                                    animateFloatAsState(
                                        targetValue = if (isPastFirstPage) 1f else 0f,
                                        animationSpec = spring(
                                            Spring.DampingRatioMediumBouncy,
                                            Spring.StiffnessLow
                                        ),
                                        label = "top_scale"
                                    ).value
                                )
                                .shadow(6.dp, CircleShape, ambientColor = ElectricColor.copy(0.3f))
                                .clip(CircleShape)
                                .background(AppCard)
                                .clickable {
                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                "回到顶部",
                                tint = ElectricColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    FABColumn(
                        scale = fabScale,
                        onBatchImport = { showBatchImport = true },
                        onAddRecord = { showAddSheet = true },
                        onScan = onScan
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(paddingValues)
        ) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                val sharedTransitionScope = this
                AnimatedContent(
                    targetState = detailRecord,
                    modifier = Modifier.fillMaxSize(),
                    label = "record_detail_transition"
                ) { targetRecord ->
                    val animatedContentScope = this
                    if (targetRecord != null) {
                        val detailIndex = filteredRecords.indexOfFirst { it.id == targetRecord.id }
                        val deltas = recordDeltas.getOrNull(detailIndex) ?: RecordDeltas()
                        RecordDetailOverlay(
                            record = targetRecord,
                            electricDelta = deltas.electric,
                            peakDelta = deltas.peak,
                            valleyDelta = deltas.valley,
                            waterDelta = deltas.water,
                            gasDelta = deltas.gas,
                            onEdit = {
                                editingRecord = it
                                detailRecord = null
                            },
                            onDelete = {
                                deleteWithUndo(it)
                                detailRecord = null
                            },
                            onClose = { detailRecord = null },
                            modifier = with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(key = targetRecord.id),
                                    animatedVisibilityScope = animatedContentScope
                                )
                            }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HomeTopBar(recordCount = records.size, collapsed = isCollapsed)

                            AnimatedVisibility(
                                visible = insight != null && !isCollapsed,
                                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                                exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                            ) {
                                insight?.let { InsightPill(insight = it) }
                            }

                            AnimatedVisibility(
                                visible =
                                records.isNotEmpty() &&
                                    tierProgress.currentMonthKwh > 0 &&
                                    !isCollapsed,
                                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                                exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                            ) {
                                TierProgressBar(tierProgress = tierProgress)
                            }

                            if (records.isNotEmpty()) {
                                val counts = remember(records, filterCounts) {
                                    RecordFilter.entries.associateWith { f ->
                                        when (f) {
                                            RecordFilter.ALL -> filterCounts["total"]
                                                ?: records.size
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
                                    onClear = {
                                        filterStartDate = null
                                        filterEndDate = null
                                    }
                                )
                            }

                            if (records.isEmpty() && !showAddSheet && !showBatchImport) {
                                HomeEmptyState(
                                    onAddClick = { showAddSheet = true },
                                    onBatchImport = { showBatchImport = true }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    state = listState,
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = 88.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(items = filteredRecords, key = { _, r ->
                                        r.id
                                    }) { index, record ->
                                        val (
                                            elecDelta,
                                            peakDelta,
                                            valleyDelta,
                                            waterDelta,
                                            gasDelta
                                        ) = recordDeltas[index]

                                        // 记住 lambda，避免每帧创建新实例阻碍跳过重组
                                        val onEditRecord = remember(record.id) {
                                            { r: MeterRecord -> editingRecord = r }
                                        }
                                        val onOpenDetail = remember(record.id) {
                                            { r: MeterRecord -> detailRecord = r }
                                        }
                                        val itemModifier = with(sharedTransitionScope) {
                                            Modifier
                                                .animateItem(
                                                    fadeInSpec = tween(200),
                                                    fadeOutSpec = tween(150),
                                                    placementSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                                .sharedBounds(
                                                    rememberSharedContentState(key = record.id),
                                                    animatedVisibilityScope = animatedContentScope
                                                )
                                        }

                                        TimelineItem(
                                            record = record,
                                            electricDelta = elecDelta,
                                            peakDelta = peakDelta,
                                            valleyDelta = valleyDelta,
                                            waterDelta = waterDelta,
                                            gasDelta = gasDelta,
                                            onDelete = deleteWithUndo,
                                            onEdit = onEditRecord,
                                            onClick = onOpenDetail,
                                            modifier = itemModifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 底部表单 — 添加记录 ──
            if (showAddSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = {
                        showAddSheet = false
                        viewModel.clearPendingOcr()
                    },
                    sheetState = sheetState,
                    containerColor = AppBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = AppBackground.copy(alpha = 0.6f)
                ) {
                    AddRecordSheet(
                        initiallyShowPeakValley = peakValleyExpanded,
                        onPeakValleyExpandedChange = viewModel::setPeakValleyExpanded,
                        latestRecord = records.firstOrNull(),
                        prefillRecord = pendingOcrData,
                        quickTags = commonTags.ifEmpty {
                            listOf("❄️开冰箱", "🔇关冰箱", "👥两家合用", "❄️空调", "🧺洗衣机")
                        },
                        onSave = { recordData ->
                            viewModel.validateAndSave(recordData)
                            viewModel.clearPendingOcr()
                            // 延迟关闭，让 Room 刷盘 + Compose 重组先完成，避免关闭动画卡顿
                            coroutineScope.launch {
                                delay(180)
                                showAddSheet = false
                                listState.animateScrollToItem(0)
                            }
                        },
                        onDismiss = {
                            showAddSheet = false
                            viewModel.clearPendingOcr()
                        }
                    )
                }
            }

            // ── 底部表单 — 批量导入 ──
            if (showBatchImport) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showBatchImport = false },
                    sheetState = sheetState,
                    containerColor = AppBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = AppBackground.copy(alpha = 0.6f)
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
                    containerColor = AppBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    scrimColor = AppBackground.copy(alpha = 0.6f)
                ) {
                    EditRecordSheet(
                        record = record,
                        quickTags = commonTags.ifEmpty {
                            listOf("❄️开冰箱", "🔇关冰箱", "👥两家合用", "❄️空调", "🧺洗衣机")
                        },
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
            initialSelectedDateMillis =
            filterStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(
                            it
                        ).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (filterEndDate != null && selected.isAfter(filterEndDate)) {
                            filterStartDate = filterEndDate
                            filterEndDate = selected
                        } else {
                            filterStartDate = selected
                        }
                    }
                    showStartDatePicker = false
                }) { Text("确定", color = ElectricColor) }
            },
            dismissButton = {
                TextButton({ showStartDatePicker = false }) { Text("取消", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = AppSurface)
        ) {
            DatePicker(
                state,
                colors = DatePickerDefaults.colors(
                    containerColor = AppSurface,
                    selectedDayContainerColor = ElectricColor,
                    selectedDayContentColor = AppBackground,
                    todayContentColor = ElectricColor,
                    todayDateBorderColor = ElectricColor
                )
            )
        }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis =
            filterEndDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(
                            it
                        ).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (filterStartDate != null && selected.isBefore(filterStartDate)) {
                            filterEndDate = filterStartDate
                            filterStartDate = selected
                        } else {
                            filterEndDate = selected
                        }
                    }
                    showEndDatePicker = false
                }) { Text("确定", color = ElectricColor) }
            },
            dismissButton = {
                TextButton({ showEndDatePicker = false }) { Text("取消", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = AppSurface)
        ) {
            DatePicker(
                state,
                colors = DatePickerDefaults.colors(
                    containerColor = AppSurface,
                    selectedDayContainerColor = ElectricColor,
                    selectedDayContentColor = AppBackground,
                    todayContentColor = ElectricColor,
                    todayDateBorderColor = ElectricColor
                )
            )
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
    onMeterReplacement: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppCard,
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
            Column {
                TextButton(onClick = onConfirm) {
                    Text("确认保存", color = ElectricColor, fontFamily = MonoFontFamily)
                }
                TextButton(onClick = onMeterReplacement) {
                    Text(
                        "标记为换表",
                        color = WarningNeon,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp
                    )
                }
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
    onAddRecord: () -> Unit,
    onScan: (() -> Unit)? = null
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

        // 主 FAB — 添加记录（长按扫表）
        MainFAB(onClick = onAddRecord, onLongClick = onScan) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加记录",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainFAB(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
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

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(btnScale)
            .shadow(
                elevation = if (isPressed) 2.dp else 6.dp,
                shape = CircleShape,
                ambientColor = ElectricColor.copy(alpha = 0.3f),
                spotColor = ElectricColor.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(ElectricColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
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
            .background(AppCard)
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
    fun RecordFilter.chipColor(): Color = when (this) {
        RecordFilter.ALL -> ElectricColor
        RecordFilter.ELECTRIC -> ElectricColor
        RecordFilter.WATER -> WaterColor
        RecordFilter.GAS -> GasColor
        RecordFilter.WITH_NOTES -> NeonBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RecordFilter.entries.forEach { filter ->
            val isSelected = filter == currentFilter
            val count = counts[filter] ?: 0
            val chipColor = filter.chipColor()

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            chipColor.copy(
                                alpha = 0.12f
                            )
                        } else {
                            AppCard.copy(alpha = 0.45f)
                        }
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = chipColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onFilterChange(filter) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        when (filter) {
                            RecordFilter.ALL -> "全部"
                            RecordFilter.ELECTRIC -> "⚡电"
                            RecordFilter.WATER -> "💧水"
                            RecordFilter.GAS -> "🔥气"
                            RecordFilter.WITH_NOTES -> "📝备注"
                        },
                        color = if (isSelected) chipColor else TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (count > 0) {
                        Text(
                            "$count",
                            color = if (isSelected) chipColor.copy(alpha = 0.7f) else TextTertiary,
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
    val dateBg = if (hasFilter) {
        ElectricColor.copy(
            alpha = 0.12f
        )
    } else {
        ElectricColor.copy(alpha = 0.06f)
    }

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
                .background(dateBg)
                .clickable { onStartClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📅", fontSize = 11.sp)
                Text(
                    startDate?.let { "从 ${it.format(DateDotFmt)}" } ?: "开始日期",
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
                .background(dateBg)
                .clickable { onEndClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📅", fontSize = 11.sp)
                Text(
                    endDate?.let { "到 ${it.format(DateDotFmt)}" } ?: "结束日期",
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
private fun HomeTopBar(recordCount: Int, collapsed: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground)
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (collapsed) 6.dp else 8.dp,
                bottom = if (collapsed) 4.dp else 6.dp
            )
    ) {
        if (collapsed) {
            // ── 折叠态：单行紧凑图标+标题 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .shadow(
                            3.dp,
                            RoundedCornerShape(6.dp),
                            ambientColor = ElectricColor.copy(0.2f),
                            spotColor = ElectricColor.copy(0.2f)
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricColor.copy(alpha = 0.18f),
                                    ElectricColor.copy(alpha = 0.06f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "闪电图标",
                        tint = ElectricColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Energy Flow",
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$recordCount",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp
                )
            }
        } else {
            // ── 展开态：图标+大标题+统计行 ──
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .shadow(
                                4.dp,
                                RoundedCornerShape(8.dp),
                                ambientColor = ElectricColor.copy(0.3f),
                                spotColor = ElectricColor.copy(0.3f)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ElectricColor.copy(alpha = 0.18f),
                                        ElectricColor.copy(alpha = 0.06f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = ElectricColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Energy Flow",
                        color = TextPrimary,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricColor.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                ElectricColor.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
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
}

// ═══════════════════════════════════════════════════════════════
// 空状态 — 呼吸动画
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HomeEmptyState(onAddClick: () -> Unit, onBatchImport: () -> Unit = {}) {
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
                                AppCard
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

            Spacer(modifier = Modifier.height(24.dp))

            // 快捷操作 Chip
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionChip(
                    icon = "⚡",
                    label = "输入电表",
                    onClick = onAddClick
                )
                QuickActionChip(
                    icon = "📋",
                    label = "批量导入",
                    onClick = onBatchImport
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 阶梯电价水位线进度条 + 环比
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TierProgressBar(tierProgress: MainViewModel.TierProgress) {
    val barColor = when (tierProgress.tierColor) {
        MainViewModel.TierLevel.Tier3 -> ErrorNeon
        MainViewModel.TierLevel.Tier2 -> WarningNeon
        MainViewModel.TierLevel.Tier1 -> ElectricColor
    }
    val tier1Ratio = (tierProgress.tier1Limit / tierProgress.tier2Limit).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = tierProgress.progress.coerceAtMost(1f),
        animationSpec = tween(800),
        label = "tier_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        // 标题行 + 环比
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本月阶梯",
                    color = TextTertiary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${String.format("%.0f", tierProgress.currentMonthKwh)} 度",
                    color = barColor,
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 环比变化
            tierProgress.momChange?.let { change ->
                val isUp = change > 0
                val arrow = if (isUp) "↑" else "↓"
                val color = if (isUp) ErrorNeon else SuccessGreen
                Text(
                    "$arrow ${String.format("%.0f", kotlin.math.abs(change))}% 较上月",
                    color = color,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            when (tierProgress.tierColor) {
                                MainViewModel.TierLevel.Tier3 -> listOf(
                                    ErrorNeon,
                                    ErrorNeon.copy(alpha = 0.6f)
                                )
                                MainViewModel.TierLevel.Tier2 -> listOf(
                                    WarningNeon,
                                    WarningNeon.copy(alpha = 0.6f)
                                )
                                MainViewModel.TierLevel.Tier1 -> listOf(
                                    ElectricColor,
                                    ElectricColor.copy(alpha = 0.6f)
                                )
                            }
                        )
                    )
            )
            // 一档水位线标记
            if (tier1Ratio < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(tier1Ratio)
                        .height(6.dp)
                        .drawBehind {
                            drawLine(
                                Color.White.copy(alpha = 0.3f),
                                Offset(size.width - 1, 0f),
                                Offset(size.width - 1, size.height),
                                strokeWidth = 2f
                            )
                        }
                )
            }
        }

        // 档位标注
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "一档 ≤${tierProgress.tier1Limit.toInt()}",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp
            )
            Text(
                "二档 ${tierProgress.tier1Limit.toInt()}-${tierProgress.tier2Limit.toInt()}",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp
            )
            Text(
                "三档 >${tierProgress.tier2Limit.toInt()}",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp
            )
        }

        // 接近阈值提示
        val remaining = tierProgress.tier2Limit - tierProgress.currentMonthKwh
        if (remaining > 0 && remaining < 30) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "⚠ 距二档电价仅剩 ${String.format("%.0f", remaining)} 度",
                color = WarningNeon,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 空状态快捷操作 Chip
// ═══════════════════════════════════════════════════════════════

@Composable
private fun QuickActionChip(icon: String, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "quickChip"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(ElectricColor.copy(alpha = 0.08f))
            .border(1.dp, ElectricColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = ElectricColor,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 主动洞察胶囊 (AI Insight Pill)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun InsightPill(insight: com.example.energyflow.data.InsightGenerator.Insight) {
    var expanded by remember { mutableStateOf(false) }

    val bgColor = when (insight.level) {
        com.example.energyflow.data.InsightGenerator.Insight.Level.CRITICAL -> ErrorNeon.copy(
            alpha = 0.08f
        )
        com.example.energyflow.data.InsightGenerator.Insight.Level.WARNING -> WarningNeon.copy(
            alpha = 0.08f
        )
        com.example.energyflow.data.InsightGenerator.Insight.Level.INFO -> ElectricColor.copy(
            alpha = 0.06f
        )
    }
    val borderColor = when (insight.level) {
        com.example.energyflow.data.InsightGenerator.Insight.Level.CRITICAL -> ErrorNeon.copy(
            alpha = 0.2f
        )
        com.example.energyflow.data.InsightGenerator.Insight.Level.WARNING -> WarningNeon.copy(
            alpha = 0.2f
        )
        com.example.energyflow.data.InsightGenerator.Insight.Level.INFO -> ElectricColor.copy(
            alpha = 0.15f
        )
    }
    val textColor = when (insight.level) {
        com.example.energyflow.data.InsightGenerator.Insight.Level.CRITICAL -> ErrorNeon
        com.example.energyflow.data.InsightGenerator.Insight.Level.WARNING -> WarningNeon
        com.example.energyflow.data.InsightGenerator.Insight.Level.INFO -> ElectricColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(insight.emoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                insight.title,
                color = textColor,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (expanded) "收起" else "详情",
                color = TextTertiary,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    insight.detail,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        if (!expanded) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                insight.detail,
                color = TextSecondary.copy(alpha = 0.6f),
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
