package com.example.energyflow.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.ui.MainScreen
import com.example.energyflow.ui.MainViewModel
import com.example.energyflow.ui.WrappedScreen
import com.example.energyflow.ui.camera.ScanScreen
import com.example.energyflow.ui.chart.ChartScreen
import com.example.energyflow.ui.chart.ChartViewModel
import com.example.energyflow.ui.settings.BillingSettingsScreen
import com.example.energyflow.ui.settings.BillingSettingsViewModel
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.AppCard
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    object Home : Screen("home", "记录", Icons.Default.Home)
    object Chart : Screen("chart", "分析", Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", "计费", Icons.Default.Settings)
}

/**
 * AppNavGraph — 快速标签页切换
 *
 * 核心策略：ViewModel 常驻内存（通过 Activity 级 hiltViewModel 缓存），
 * 标签页内容按需渲染（when 分支切换）。省去 NavHost 的 composable 销毁重建、
 * saveState/restoreState 开销以及 350ms 动画延迟，实现极速切换。
 *
 * 三个 ViewModel 在 AppNavGraph 层级一次性获取（hiltViewModel 内部缓存），
 * 切换标签时 Compose 树虽然重建，但 ViewModel 数据瞬时可用。
 *
 * 扫码页作为 state 驱动的覆盖层，OCR 结果通过 pendingOcrResult 回传。
 */
@Composable
fun AppNavGraph() {
    // ── 当前标签页 + 返回导航栈 ──
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    val tabHistory = remember { mutableListOf<Screen>() }
    val bottomBarScreens = listOf(Screen.Home, Screen.Chart, Screen.Settings)

    // ── 扫码覆盖层 & OCR 回传 ──
    var showScan by remember { mutableStateOf(false) }
    var pendingOcrResult by remember { mutableStateOf<String?>(null) }

    // ── 年度报告覆盖层 ──
    var showWrapped by remember { mutableStateOf(false) }

    // ── ViewModel 常驻内存 ──
    val mainVM: MainViewModel = hiltViewModel()
    val chartVM: ChartViewModel = hiltViewModel()
    val settingsVM: BillingSettingsViewModel = hiltViewModel()

    // ── 预测性返回：非 Home 标签时返回 Home ──
    PredictiveBackHandler(enabled = currentTab != Screen.Home) {
        val prevTab = tabHistory.removeLastOrNull() ?: Screen.Home
        currentTab = prevTab
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = AppBackground,
                contentColor = TextPrimary
            ) {
                bottomBarScreens.forEach { screen ->
                    val selected = currentTab == screen

                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.18f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "navIconScale"
                    )

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon ?: Icons.Default.Home,
                                contentDescription = screen.title,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontFamily = MonoFontFamily
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (currentTab != screen) {
                                tabHistory.add(currentTab)
                                currentTab = screen
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricColor,
                            selectedTextColor = ElectricColor,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AppCard
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── AnimatedContent + Predictive Back 过渡动画 ──
            AnimatedContent(
                targetState = currentTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val order = listOf(Screen.Home, Screen.Chart, Screen.Settings)
                    val direction = when {
                        order.indexOf(initialState) > order.indexOf(targetState) -> -1
                        else -> 1
                    }
                    (slideInHorizontally(
                        animationSpec = tween(280),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(280),
                                targetOffsetX = { fullWidth -> -direction * fullWidth / 4 }
                            ) + fadeOut(animationSpec = tween(200))
                        )
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    Screen.Home -> {
                        LaunchedEffect(pendingOcrResult) {
                            pendingOcrResult?.let {
                                mainVM.ocrAutoFill(it)
                                pendingOcrResult = null
                            }
                        }
                        MainScreen(
                            viewModel = mainVM,
                            onScan = { showScan = true }
                        )
                    }

                    Screen.Chart -> {
                        ChartScreen(
                            viewModel = chartVM,
                            onWrapped = { showWrapped = true }
                        )
                    }

                    Screen.Settings -> {
                        BillingSettingsScreen(viewModel = settingsVM)
                    }
                }
            }

            // ── 扫码覆盖层（预测性返回手势） ──
            if (showScan) {
                PredictiveBackHandler(enabled = showScan) {
                    showScan = false
                }
                ScanScreen(
                    onResult = { recognizedText ->
                        pendingOcrResult = recognizedText
                        showScan = false
                    },
                    onDismiss = { showScan = false }
                )
            }

            // ── 年度报告覆盖层 ──
            if (showWrapped) {
                PredictiveBackHandler(enabled = showWrapped) {
                    showWrapped = false
                }
                WrappedScreen(onDismiss = { showWrapped = false })
            }
        }
    }
}
