package com.example.energyflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRecordDao
import com.example.energyflow.data.ThemeDistColors
import com.example.energyflow.data.ThemeDistRepository
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.ui.OnboardingScreen
import com.example.energyflow.ui.SplashScreen
import com.example.energyflow.ui.navigation.AppNavGraph
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.EnergyFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var themeDistRepository: ThemeDistRepository
    @Inject lateinit var meterRecordDao: MeterRecordDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLink(intent)

        setContent {
            val savedDarkTheme by userPreferences.isDarkTheme.collectAsStateWithLifecycle(initialValue = true)
            val followSystemTheme by userPreferences.followSystemTheme.collectAsStateWithLifecycle(initialValue = false)
            val themeDistEnabled by userPreferences.themeDistEnabled.collectAsStateWithLifecycle(initialValue = true)
            val darkTheme = if (followSystemTheme) isSystemInDarkTheme() else savedDarkTheme

            var themeColors by remember { mutableStateOf<ThemeDistColors?>(null) }

            LaunchedEffect(themeDistEnabled) {
                if (!themeDistEnabled) {
                    themeColors = null
                    return@LaunchedEffect
                }
                val cached = withContext(Dispatchers.IO) {
                    themeDistRepository.loadCachedResponse()
                }
                cached?.let { themeColors = themeDistRepository.parseColors(it) }
                val fresh = withContext(Dispatchers.IO) {
                    themeDistRepository.fetchToday()
                }
                fresh?.let { themeColors = themeDistRepository.parseColors(it) }
            }

            var showSplash by remember { mutableStateOf(true) }
            val isOnboardingComplete by userPreferences.isOnboardingComplete.collectAsStateWithLifecycle(initialValue = true)
            var onboardingDismissed by remember { mutableStateOf(false) }

            LaunchedEffect(onboardingDismissed) {
                if (onboardingDismissed && !isOnboardingComplete) {
                    userPreferences.completeOnboarding()
                }
            }

            EnergyFlowTheme(
                darkTheme = darkTheme,
                dynamicColors = if (themeDistEnabled) themeColors else null
            ) {
                Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
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
                            color = AppBackground
                        ) {
                            if (!isOnboardingComplete && !onboardingDismissed) {
                                OnboardingScreen(
                                    onComplete = {
                                        onboardingDismissed = true
                                    }
                                )
                            } else {
                                AppNavGraph()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "energyflow" || uri.host != "record") return

        val electric = uri.getQueryParameter("electric")?.toDoubleOrNull()
        val water = uri.getQueryParameter("water")?.toDoubleOrNull()
        val gas = uri.getQueryParameter("gas")?.toDoubleOrNull()
        val peak = uri.getQueryParameter("peak")?.toDoubleOrNull()
        val valley = uri.getQueryParameter("valley")?.toDoubleOrNull()

        if (electric == null && water == null && gas == null) return

        scope.launch {
            try {
                meterRecordDao.insert(
                    MeterRecord(
                        timestamp = LocalDateTime.now(),
                        isElectricRecorded = electric != null,
                        electricTotal = electric,
                        electricPeak = peak,
                        electricValley = valley,
                        isWaterRecorded = water != null,
                        waterTotal = water,
                        isGasRecorded = gas != null,
                        gasTotal = gas
                    )
                )
                Toast.makeText(
                    this@MainActivity,
                    "深链记录已保存",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "保存失败：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel()
    }
}
