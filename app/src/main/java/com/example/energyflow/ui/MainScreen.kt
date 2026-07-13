package com.example.energyflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import kotlinx.coroutines.launch

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

    var showAddSheet by remember { mutableStateOf(false) }
    var showBatchImport by remember { mutableStateOf(false) }
    var pendingBatchImport by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<com.example.energyflow.data.MeterRecord?>(null) }

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

    // 处理 UI 状态 → Snackbar
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
                if (pendingBatchImport) {
                    showBatchImport = false
                    pendingBatchImport = false
                }
            }
            is UiState.Warning -> {
                snackbarHostState.showSnackbar("⚠️ ${state.message}")
                viewModel.clearState()
                if (pendingBatchImport) {
                    showBatchImport = false
                    pendingBatchImport = false
                }
            }
            is UiState.Error -> {
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
                FABColumn(
                    scale = fabScale,
                    onBatchImport = { showBatchImport = true },
                    onAddRecord = { showAddSheet = true }
                )
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
                        items(items = records, key = { it.id }) { record ->
                            TimelineItem(
                                record = record,
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
            AnimatedVisibility(
                visible = showAddSheet,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                )
            ) {
                BottomSheetOverlay(onDismiss = { showAddSheet = false }) {
                    AddRecordSheet(
                        initiallyShowPeakValley = peakValleyExpanded,
                        onPeakValleyExpandedChange = viewModel::setPeakValleyExpanded,
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
            AnimatedVisibility(
                visible = showBatchImport,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                )
            ) {
                BottomSheetOverlay(onDismiss = { showBatchImport = false }) {
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
            AnimatedVisibility(
                visible = editingRecord != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                )
            ) {
                editingRecord?.let { record ->
                    BottomSheetOverlay(onDismiss = { editingRecord = null }) {
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
                color = Color(0xFFFF6B6B),
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
                                color = Color(0xFFFF6B6B),
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is AnomalyWarning.SpikeDetected -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚡ ${warning.detail}",
                                color = Color(0xFFFFA500),
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

@Composable
private fun BottomSheetOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(DarkBackground)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ambientColor = ElectricColor.copy(alpha = 0.1f),
                    spotColor = ElectricColor.copy(alpha = 0.1f)
                )
                .clickable { /* 阻止穿透 */ }
        ) {
            content()
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
