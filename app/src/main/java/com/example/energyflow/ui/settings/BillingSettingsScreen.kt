package com.example.energyflow.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.data.BillingRules
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor

@Composable
fun BillingSettingsScreen(viewModel: BillingSettingsViewModel = hiltViewModel()) {
    val rules by viewModel.billingRulesFlow.collectAsState(initial = BillingRules())
    val isDark by viewModel.isDarkThemeFlow.collectAsState(initial = true)
    val followSystem by viewModel.followSystemThemeFlow.collectAsState(initial = false)
    val savedApiKey by viewModel.weatherApiKeyFlow.collectAsState(initial = "")
    val savedCityId by viewModel.weatherCityIdFlow.collectAsState(initial = "101010100")
    val peakValleyExpanded by viewModel.peakValleyExpandedFlow.collectAsState(initial = false)
    var weatherApiKey by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var weatherCityId by remember(savedCityId) { mutableStateOf(savedCityId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, color = NeonYellow, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        Text("所有价格仅用于估算，按本地账单规则填写。", color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))
        SectionHeader(Icons.Default.Bolt, "分时与阶梯电价", ElectricColor)
        Spacer(Modifier.height(12.dp))
        PriceInputRow("峰电", rules.peakPrice, viewModel::updatePeakPrice, ElectricPeakColor, "元/度")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("谷电", rules.valleyPrice, viewModel::updateValleyPrice, ElectricValleyColor, "元/度")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("平电", rules.flatPrice, viewModel::updateFlatPrice, ElectricColor, "元/度")
        Spacer(Modifier.height(8.dp))
        Text("电费阶梯固定为：0–${rules.electricTier1Limit.toInt()} 度 × 1.0；${rules.electricTier1Limit.toInt() + 1}–${rules.electricTier2Limit.toInt()} 度 × 1.5；其余 × 2.0。", color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.WaterDrop, "阶梯水价", WaterColor)
        Spacer(Modifier.height(12.dp))
        PriceInputRow("一档上限", rules.waterTier1Limit, viewModel::updateWaterTier1Limit, WaterColor, "吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档上限", rules.waterTier2Limit, viewModel::updateWaterTier2Limit, WaterColor, "吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("一档单价", rules.waterTier1Price, viewModel::updateWaterTier1Price, WaterColor, "元/吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档单价", rules.waterTier2Price, viewModel::updateWaterTier2Price, WaterColor, "元/吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("三档单价", rules.waterTier3Price, viewModel::updateWaterTier3Price, WaterColor, "元/吨")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::saveBillingRules,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricColor, contentColor = DarkBackground),
            shape = RoundedCornerShape(12.dp)
        ) { Text("保存计费规则", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Cloud, "天气叠层（和风天气）", NeonBlue)
        Spacer(Modifier.height(12.dp))
        TextInputRow("API Key", weatherApiKey, { weatherApiKey = it }, "粘贴你的和风天气 API Key", false)
        Spacer(Modifier.height(8.dp))
        TextInputRow("城市 ID", weatherCityId, { weatherCityId = it }, "北京：101010100", false)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.setWeatherConfig(weatherApiKey, weatherCityId) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = NeonBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text("保存天气配置", fontFamily = MonoFontFamily) }
        Text("API Key 只保存在此设备的 DataStore 中；未填写时图表不会发起天气请求。", color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))

        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Palette, "显示偏好", NeonYellow)
        Spacer(Modifier.height(12.dp))
        PreferenceSwitch("跟随系统深色模式", followSystem, { viewModel.setTheme(isDark, it) })
        if (!followSystem) {
            Spacer(Modifier.height(8.dp))
            PreferenceSwitch("使用深色模式", isDark, { viewModel.setTheme(it, false) })
        }
        Spacer(Modifier.height(8.dp))
        PreferenceSwitch("新增读数时默认展开峰谷", peakValleyExpanded, viewModel::setPeakValleyExpanded)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PriceInputRow(label: String, value: Double, onValueChange: (Double) -> Unit, color: Color, unit: String) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontFamily = MonoFontFamily, modifier = Modifier.width(84.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    text = newValue
                    newValue.toDoubleOrNull()?.let(onValueChange)
                }
            },
            modifier = Modifier.weight(1f),
            suffix = { Text(unit, color = TextSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            colors = fieldColors(color),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary, fontFamily = MonoFontFamily)
        )
    }
}

@Composable
private fun TextInputRow(label: String, value: String, onValueChange: (String) -> Unit, hint: String, secret: Boolean) {
    Column {
        Text(label, color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = TextSecondary.copy(alpha = 0.5f), fontFamily = MonoFontFamily) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = fieldColors(NeonBlue),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontFamily = MonoFontFamily)
        )
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkCard).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextPrimary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = ElectricColor, checkedTrackColor = ElectricColor.copy(alpha = 0.35f)))
    }
}

@Composable
private fun fieldColors(color: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = DarkSurface,
    cursorColor = color,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
