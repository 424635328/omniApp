package com.example.energyflow.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import com.example.energyflow.ui.camera.ScanScreen
import com.example.energyflow.ui.chart.ChartScreen
import com.example.energyflow.ui.chart.ChartViewModel
import com.example.energyflow.ui.settings.BillingSettingsScreen
import com.example.energyflow.ui.settings.BillingSettingsViewModel
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
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
    // ── 当前标签页 ──
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    val bottomBarScreens = listOf(Screen.Home, Screen.Chart, Screen.Settings)

    // ── 扫码覆盖层 & OCR 回传 ──
    var showScan by remember { mutableStateOf(false) }
    var pendingOcrResult by remember { mutableStateOf<String?>(null) }

    // ── ViewModel 常驻内存：在此处一次性获取，切换时不重建 ──
    // hiltViewModel() 内部查 ViewModelStore，已存在则直接返回缓存实例
    val mainVM: MainViewModel = hiltViewModel()
    val chartVM: ChartViewModel = hiltViewModel()
    val settingsVM: BillingSettingsViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkBackground,
                contentColor = TextPrimary
            ) {
                bottomBarScreens.forEach { screen ->
                    val selected = currentTab == screen

                    // 图标弹跳动画
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
                                imageVector = screen.icon!!,
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
                        onClick = { currentTab = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricColor,
                            selectedTextColor = ElectricColor,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = DarkCard
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
            // ── 按当前标签渲染页面（Compose 树重建，但 ViewModel 数据瞬时可用） ──
            when (currentTab) {
                Screen.Home -> {
                    // OCR 结果消费：扫码页返回时触发自动填充
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
                    ChartScreen(viewModel = chartVM)
                }

                Screen.Settings -> {
                    BillingSettingsScreen(viewModel = settingsVM)
                }
            }

            // ── 扫码覆盖层（位于标签页之上） ──
            if (showScan) {
                ScanScreen(
                    onResult = { recognizedText ->
                        pendingOcrResult = recognizedText
                        showScan = false
                    },
                    onDismiss = { showScan = false }
                )
            }
        }
    }
}
