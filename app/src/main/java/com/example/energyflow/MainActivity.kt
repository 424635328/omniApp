package com.example.energyflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.energyflow.data.ThemeDistColors
import com.example.energyflow.data.ThemeDistRepository
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.ui.SplashScreen
import com.example.energyflow.ui.navigation.AppNavGraph
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.EnergyFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var themeDistRepository: ThemeDistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val savedDarkTheme by userPreferences.isDarkTheme.collectAsState(initial = true)
            val followSystemTheme by userPreferences.followSystemTheme.collectAsState(initial = false)
            val themeDistEnabled by userPreferences.themeDistEnabled.collectAsState(initial = true)
            val darkTheme = if (followSystemTheme) isSystemInDarkTheme() else savedDarkTheme

            var themeColors by remember { mutableStateOf<ThemeDistColors?>(null) }

            LaunchedEffect(themeDistEnabled) {
                if (!themeDistEnabled) {
                    themeColors = null
                    return@LaunchedEffect
                }
                themeDistRepository.loadCachedResponse()?.let { cached ->
                    themeColors = themeDistRepository.parseColors(cached)
                }
                themeDistRepository.fetchToday()?.let { fresh ->
                    themeColors = themeDistRepository.parseColors(fresh)
                }
            }

            var showSplash by remember { mutableStateOf(true) }

            EnergyFlowTheme(
                darkTheme = darkTheme,
                dynamicColors = if (themeDistEnabled) themeColors else null
            ) {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    }

                    AnimatedVisibility(
                        visible = !showSplash,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(300))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = DarkBackground
                        ) {
                            AppNavGraph()
                        }
                    }
                }
            }
        }
    }
}
