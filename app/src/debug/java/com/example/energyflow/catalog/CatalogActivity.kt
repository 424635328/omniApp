package com.example.energyflow.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.TimelineItem
import com.example.energyflow.ui.theme.AppBackground
import com.example.energyflow.ui.theme.ElectricEnd
import com.example.energyflow.ui.theme.ElectricStart
import com.example.energyflow.ui.theme.EnergyFlowTheme
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.GasEnd
import com.example.energyflow.ui.theme.GasStart
import com.example.energyflow.ui.theme.HighlightYellow
import com.example.energyflow.ui.theme.InfoBlue
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WarningNeon
import com.example.energyflow.ui.theme.WaterEnd
import com.example.energyflow.ui.theme.WaterStart
import java.time.LocalDateTime

/**
 * Debug-only UI 组件目录 — 在明/暗主题下预览核心组件与设计令牌。
 * 仅存在于 debug 构建，通过独立启动器入口「EnergyFlow Catalog」打开。
 */
class CatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            EnergyFlowTheme(darkTheme = darkTheme) {
                CatalogScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = it }
                )
            }
        }
    }
}

private val sampleFull = MeterRecord(
    id = 1,
    timestamp = LocalDateTime.of(2026, 7, 15, 8, 30),
    isElectricRecorded = true,
    electricTotal = 16543.0,
    electricPeak = 9876.5,
    electricValley = 6666.5,
    isWaterRecorded = true,
    waterTotal = 321.4,
    note = "空调清洗后首次记录"
)

private val sampleElectricOnly = MeterRecord(
    id = 2,
    timestamp = LocalDateTime.of(2026, 7, 16, 21, 5),
    isElectricRecorded = true,
    electricTotal = 16555.0
)

private val sampleGas = MeterRecord(
    id = 3,
    timestamp = LocalDateTime.of(2026, 7, 17, 12, 0),
    isGasRecorded = true,
    gasTotal = 888.8
)

@Composable
private fun CatalogScreen(darkTheme: Boolean, onToggleTheme: (Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EnergyFlow Catalog",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("暗色", color = TextSecondary, fontFamily = MonoFontFamily)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = darkTheme, onCheckedChange = onToggleTheme)
                }
            }
        }

        item { SectionTitle("颜色令牌") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorSwatch("ElectricStart", ElectricStart)
                ColorSwatch("ElectricEnd", ElectricEnd)
                ColorSwatch("WaterStart", WaterStart)
                ColorSwatch("WaterEnd", WaterEnd)
                ColorSwatch("GasStart", GasStart)
                ColorSwatch("GasEnd", GasEnd)
                ColorSwatch("ErrorNeon", ErrorNeon)
                ColorSwatch("WarningNeon", WarningNeon)
                ColorSwatch("SuccessGreen", SuccessGreen)
                ColorSwatch("InfoBlue", InfoBlue)
                ColorSwatch("HighlightYellow", HighlightYellow)
            }
        }

        item { SectionTitle("字体层级") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "headlineMedium 16543",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily
                )
                Text(
                    "titleMedium 峰谷电量",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily
                )
                Text(
                    "bodyMedium 每日能耗记录正文",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
                Text(
                    "labelSmall 辅助说明",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
            }
        }

        item { SectionTitle("TimelineItem · 电+水+备注") }
        item {
            TimelineItem(
                record = sampleFull,
                electricDelta = 12.5,
                peakDelta = 8.0,
                valleyDelta = 4.5,
                waterDelta = 0.8
            )
        }

        item { SectionTitle("TimelineItem · 仅电表") }
        item { TimelineItem(record = sampleElectricOnly, electricDelta = 12.0) }

        item { SectionTitle("TimelineItem · 燃气") }
        item { TimelineItem(record = sampleGas, gasDelta = 1.2) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun ColorSwatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontFamily = MonoFontFamily
        )
    }
}
