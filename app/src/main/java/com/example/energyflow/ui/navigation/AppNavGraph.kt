package com.example.energyflow.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val bottomBarScreens = listOf(Screen.Home, Screen.Chart, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkBackground,
                contentColor = TextPrimary
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomBarScreens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    // ── 图标弹跳动画 ──
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
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 4 } + fadeIn(tween(350))
            },
            exitTransition = {
                slideOutHorizontally(tween(350)) { -it / 4 } + fadeOut(tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(tween(350)) { -it / 4 } + fadeIn(tween(350))
            },
            popExitTransition = {
                slideOutHorizontally(tween(350)) { it / 4 } + fadeOut(tween(250))
            }
        ) {
            composable(Screen.Home.route) {
                val mainVM: MainViewModel = hiltViewModel()
                // ── OCR 结果消费：监听 scan 页面回传的识别文本 ──
                val ocrResult = it.savedStateHandle.get<String>("ocr_result")
                var ocrTriggered by remember { mutableStateOf(false) }
                LaunchedEffect(ocrResult) {
                    if (ocrResult != null && !ocrTriggered) {
                        ocrTriggered = true
                        it.savedStateHandle.remove<String>("ocr_result")
                        mainVM.ocrAutoFill(ocrResult)
                    }
                }
                MainScreen(
                    viewModel = mainVM,
                    onScan = { navController.navigate("scan") }
                )
            }

            composable(Screen.Chart.route) {
                val chartVM: ChartViewModel = hiltViewModel()
                ChartScreen(viewModel = chartVM)
            }

            composable(Screen.Settings.route) {
                val settingsVM: BillingSettingsViewModel = hiltViewModel()
                BillingSettingsScreen(viewModel = settingsVM)
            }

            composable("scan") {
                ScanScreen(
                    onResult = { recognizedText ->
                        // 返回识别结果到首页
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("ocr_result", recognizedText)
                        navController.popBackStack()
                    },
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}
