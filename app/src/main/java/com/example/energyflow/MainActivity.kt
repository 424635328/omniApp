package com.example.energyflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.ui.navigation.AppNavGraph
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.EnergyFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val savedDarkTheme by userPreferences.isDarkTheme.collectAsState(initial = true)
            val followSystemTheme by userPreferences.followSystemTheme.collectAsState(initial = false)
            val darkTheme = if (followSystemTheme) isSystemInDarkTheme() else savedDarkTheme
            EnergyFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    // ViewModels 由 NavGraph 内部通过 hiltViewModel() 创建
                    AppNavGraph()
                }
            }
        }
    }
}
