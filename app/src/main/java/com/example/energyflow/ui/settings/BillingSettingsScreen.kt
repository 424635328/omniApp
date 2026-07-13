package com.example.energyflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.ElectricPeakColor
import com.example.energyflow.ui.theme.ElectricValleyColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.WaterColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingSettingsScreen(
    viewModel: BillingSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val peakPrice by viewModel.peakPriceFlow.collectAsState(initial = 0.6)
    val valleyPrice by viewModel.valleyPriceFlow.collectAsState(initial = 0.3)
    val flatPrice by viewModel.flatPriceFlow.collectAsState(initial = 0.5)
    val waterPrice by viewModel.waterPriceFlow.collectAsState(initial = 3.5)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = DarkBackground,
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "计费规则",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NeonYellow
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ═══ 分时电费 ═══
            SectionHeader(
                icon = Icons.Default.Bolt,
                title = "分时电价（元/度）",
                color = ElectricColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            PriceInputRow(
                label = "峰电",
                value = peakPrice,
                onValueChange = { viewModel.updatePeakPrice(it) },
                color = ElectricPeakColor,
                hint = "0.6"
            )

            Spacer(modifier = Modifier.height(8.dp))

            PriceInputRow(
                label = "谷电",
                value = valleyPrice,
                onValueChange = { viewModel.updateValleyPrice(it) },
                color = ElectricValleyColor,
                hint = "0.3"
            )

            Spacer(modifier = Modifier.height(8.dp))

            PriceInputRow(
                label = "平电",
                value = flatPrice,
                onValueChange = { viewModel.updateFlatPrice(it) },
                color = ElectricColor,
                hint = "0.5"
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ═══ 水费 ═══
            SectionHeader(
                icon = Icons.Default.WaterDrop,
                title = "水费单价（元/吨）",
                color = WaterColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            PriceInputRow(
                label = "水费",
                value = waterPrice,
                onValueChange = { viewModel.updateWaterPrice(it) },
                color = WaterColor,
                hint = "3.5"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ═══ 阶梯说明 ═══
            TierExplanation()

            Spacer(modifier = Modifier.height(32.dp))

            // ═══ 保存按钮 ═══
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.saveAll()
                        snackbarHostState.showSnackbar("计费规则已保存")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElectricColor)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "保存设置",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PriceInputRow(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    color: androidx.compose.ui.graphics.Color,
    hint: String
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            modifier = Modifier.width(48.dp)
        )

        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    text = newValue
                    newValue.toDoubleOrNull()?.let { onValueChange(it) }
                }
            },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(hint, color = TextSecondary.copy(alpha = 0.5f), fontFamily = MonoFontFamily)
            },
            suffix = {
                Text("元/度", color = TextSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = DarkSurface,
                cursorColor = color,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun TierExplanation() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            "阶梯电价说明",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        TierRow("第一档", "0 - 200 度/月", "基准价 × 1.0")
        Spacer(modifier = Modifier.height(6.dp))
        TierRow("第二档", "201 - 400 度/月", "基准价 × 1.5")
        Spacer(modifier = Modifier.height(6.dp))
        TierRow("第三档", "> 400 度/月", "基准价 × 2.0")
    }
}

@Composable
private fun TierRow(tier: String, range: String, multiplier: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(tier, color = ElectricColor, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall)
        Text(range, color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall)
        Text(multiplier, color = TextSecondary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall)
    }
}
