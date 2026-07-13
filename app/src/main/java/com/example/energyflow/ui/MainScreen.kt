package com.example.energyflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val records by viewModel.allRecords.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val anomalyWarnings by viewModel.anomalyWarnings.collectAsState()
    val showAnomalyDialog by viewModel.showAnomalyDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showAddSheet by remember { mutableStateOf(false) }
    var showBatchImport by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<com.example.energyflow.data.MeterRecord?>(null) }

    // FAB动画
    val fabScale by animateFloatAsState(
        targetValue = if (showAddSheet || showBatchImport) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_scale"
    )

    // 处理UI状态 → Snackbar
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
            }
            is UiState.Warning -> {
                snackbarHostState.showSnackbar("⚠️ ${state.message}")
                viewModel.clearState()
            }
            is UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
            }
            else -> {}
        }
    }

    // ⚠️ 异常确认弹窗
    if (showAnomalyDialog && anomalyWarnings.isNotEmpty()) {
        AnomalyWarningDialog(
            warnings = anomalyWarnings,
            onConfirm = {
                viewModel.confirmSaveWithAnomaly()
            },
            onDismiss = {
                viewModel.cancelSaveWithAnomaly()
            }
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
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.scale(fabScale)
                ) {
                    SmallFloatingActionButton(
                        onClick = { showBatchImport = true },
                        containerColor = DarkCard,
                        contentColor = ElectricColor
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "批量导入",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { showAddSheet = true },
                        containerColor = ElectricColor,
                        contentColor = DarkBackground,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加记录",
                            modifier = Modifier.size(28.dp)
                        )
                    }
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                TopBar(
                    recordCount = records.size,
                    onAddClick = { showAddSheet = true }
                )

                if (records.isEmpty() && !showAddSheet && !showBatchImport) {
                    EmptyState(onAddClick = { showAddSheet = true })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = records,
                            key = { it.id }
                        ) { record ->
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

            // 添加记录底部表单
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
                        onSave = { recordData ->
                            // 改用 validateAndSave → 先校验再决定是否保存
                            viewModel.validateAndSave(recordData)
                            showAddSheet = false
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        onDismiss = { showAddSheet = false }
                    )
                }
            }

            // 批量导入
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
                            viewModel.batchImport(text)
                            showBatchImport = false
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        onDismiss = { showBatchImport = false }
                    )
                }
            }

            // 编辑记录
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

// ════════════════════════════════════════════════════════
// 🚨 异常警告弹窗
// ════════════════════════════════════════════════════════

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
                text = "⚠️ 输入异常",
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
                                text = warning.message,
                                color = Color(0xFFFF6B6B),
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is AnomalyWarning.SpikeDetected -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚡ ${warning.detail}",
                                color = Color(0xFFFFA500),
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "请确认读数是否正确，或返回修改。",
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

// ════════════════════════════════════════════════════════
// 原有组件保持不变
// ════════════════════════════════════════════════════════

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
                .clickable { /* 阻止点击穿透 */ }
        ) {
            content()
        }
    }
}

@Composable
private fun TopBar(
    recordCount: Int,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkBackground.copy(alpha = 0.98f),
                        DarkBackground.copy(alpha = 0.9f),
                        DarkBackground.copy(alpha = 0f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "能耗手记",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonYellow,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "共 $recordCount 条记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddClick: () -> Unit) {
    val pulseScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ElectricColor.copy(alpha = 0.2f),
                                DarkCard
                            )
                        )
                    )
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SwipeUp,
                    contentDescription = null,
                    tint = ElectricColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "开始记录能耗",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "点击右下角 + 号添加记录\n或使用粘贴按钮批量导入",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontFamily = MonoFontFamily
            )
        }
    }
}

@Composable
private fun SmallFloatingActionButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "small_fab_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = contentColor.copy(alpha = 0.2f),
                spotColor = contentColor.copy(alpha = 0.2f)
            )
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                onClick = {
                    isPressed = true
                    onClick()
                    isPressed = false
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
