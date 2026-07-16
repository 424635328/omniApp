package com.example.energyflow.ui.settings

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.example.energyflow.ui.theme.ErrorNeon
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonBlue
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.SuccessGreen
import com.example.energyflow.ui.theme.WarningNeon
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary
import com.example.energyflow.ui.theme.TextTertiary
import com.example.energyflow.ui.theme.WaterColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.YearMonth

@Composable
fun BillingSettingsScreen(viewModel: BillingSettingsViewModel = hiltViewModel()) {
    val rules by viewModel.billingRulesFlow.collectAsState(initial = BillingRules())
    val deepSeekApiKey by viewModel.deepSeekApiKeyFlow.collectAsState(initial = "")
    val isDark by viewModel.isDarkThemeFlow.collectAsState(initial = true)
    val followSystem by viewModel.followSystemThemeFlow.collectAsState(initial = false)
    val peakValleyExpanded by viewModel.peakValleyExpandedFlow.collectAsState(initial = false)
    val themeDistEnabled by viewModel.themeDistEnabledFlow.collectAsState(initial = true)

    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    /** 导出文本到 Downloads（公共可访问）并同时存一份到 app Documents */
    fun exportToMediaStore(fileName: String, text: String, mimeType: String, toastLabel: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    // 1. 先删除 Downloads 中同名文件，确保覆盖
                    val existingUri = resolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME}=?",
                        arrayOf(fileName), null
                    )
                    existingUri?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                                .appendPath(id.toString()).build()
                            resolver.delete(uri, null, null)
                        }
                    }
                    // 2. 写入 Downloads
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    // 3. 也存一份到 app 内部
                    val appDir = java.io.File(
                        context.getExternalFilesDir(null), "Documents"
                    )
                    appDir.mkdirs()
                    java.io.File(appDir, fileName).writeText(text, Charsets.UTF_8)
                }
                toast(context, "已导出到 Downloads/$fileName")
            } catch (e: Exception) {
                toast(context, "导出失败: ${e.message}")
            }
        }
    }

    fun exportToFile() {
        scope.launch {
            val text = viewModel.exportRecordsToText()
            exportToMediaStore("EnergyFlow_Backup.txt", text, "text/plain", "数据备份")
        }
    }

    fun exportRulesToFile() {
        scope.launch {
            val json = viewModel.exportRulesToJson()
            exportToMediaStore("EnergyFlow_Rules.json", json, "application/json", "规则模板")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                        } ?: ""
                    }
                    if (text.isBlank()) {
                        toast(context, "文件内容为空")
                        return@launch
                    }
                    val msg = viewModel.importRecordsFromText(text)
                    toast(context, msg)
                } catch (e: Exception) {
                    toast(context, "导入失败: ${e.message}")
                }
            }
        }
    }

    val rulesImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                        } ?: ""
                    }
                    val msg = viewModel.importRulesFromJson(json)
                    toast(context, msg)
                } catch (e: Exception) {
                    toast(context, "导入失败: ${e.message}")
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DarkCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text("⚠️ 清除所有数据", color = ErrorNeon, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("此操作将删除所有能耗记录，且不可撤销。确定要清除吗？", fontFamily = MonoFontFamily)
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.deleteAllRecords()
                    toast(context, "已清除所有数据")
                }) {
                    Text("确认清除", color = ErrorNeon, fontFamily = MonoFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary, fontFamily = MonoFontFamily)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            color = NeonYellow,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold
        )
        Text(
            "南京建邺区 2026年居民水电收费标准。按实际账单调整。",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.bodySmall
        )

        // ═══════════════════════════════════════════════
        // 分时电价
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(24.dp))
        SectionHeader(Icons.Default.Bolt, "分时电价", ElectricColor)
        Spacer(Modifier.height(4.dp))
        SectionHint("峰谷分时电价（开通后一年内不得变更），按\"先峰谷、后阶梯\"计算")
        Spacer(Modifier.height(12.dp))
        PriceInputRow("峰电", rules.peakPrice, viewModel::updatePeakPrice, ElectricPeakColor, "8:00-21:00", "元/度")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("谷电", rules.valleyPrice, viewModel::updateValleyPrice, ElectricValleyColor, "0:00-8:00 / 21:00-24:00", "元/度")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("平电", rules.flatPrice, viewModel::updateFlatPrice, ElectricColor, "未开通峰谷时使用", "元/度")

        // ═══════════════════════════════════════════════
        // 用电阶梯
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Bolt, "用电阶梯（年累计，按月估算）", ElectricColor)
        Spacer(Modifier.height(4.dp))
        SectionHint("年用电 ≤${(rules.electricTier1Limit * 12).toInt()} 度为一档，≤${(rules.electricTier2Limit * 12).toInt()} 度为二档，超出为三档")
        Spacer(Modifier.height(12.dp))
        PriceInputRow("一档上限", rules.electricTier1Limit, viewModel::updateElecTier1Limit, ElectricColor, "月均 度", "度/月")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档上限", rules.electricTier2Limit, viewModel::updateElecTier2Limit, ElectricColor, "月均 度", "度/月")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档加价", rules.electricTier2Surcharge, viewModel::updateElecTier2Surcharge, WarningNeon, "基础价+此值", "元/度")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("三档加价", rules.electricTier3Surcharge, viewModel::updateElecTier3Surcharge, ErrorNeon, "基础价+此值", "元/度")

        // ═══════════════════════════════════════════════
        // 阶梯水价
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.WaterDrop, "阶梯水价（年累计，按月估算）", WaterColor)
        Spacer(Modifier.height(4.dp))
        SectionHint("年用水 ≤${(rules.waterTier1Limit * 12).toInt()} 吨为一档，≤${(rules.waterTier2Limit * 12).toInt()} 吨为二档，超出为三档（含供水价+水资源税+污水处理费）")
        Spacer(Modifier.height(12.dp))
        PriceInputRow("一档上限", rules.waterTier1Limit, viewModel::updateWaterTier1Limit, WaterColor, "月均 吨", "吨/月")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档上限", rules.waterTier2Limit, viewModel::updateWaterTier2Limit, WaterColor, "月均 吨", "吨/月")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("一档单价", rules.waterTier1Price, viewModel::updateWaterTier1Price, WaterColor, "≤一档上限", "元/吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("二档单价", rules.waterTier2Price, viewModel::updateWaterTier2Price, WaterColor, "一二档之间", "元/吨")
        Spacer(Modifier.height(8.dp))
        PriceInputRow("三档单价", rules.waterTier3Price, viewModel::updateWaterTier3Price, WaterColor, ">二档上限", "元/吨")

        // ── 保存按钮 ──
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.saveBillingRules()
                toast(context, "计费规则已保存")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricColor, contentColor = DarkBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("保存计费规则", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        }

        // ═══════════════════════════════════════════════
        // DeepSeek AI
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Bolt, "DeepSeek AI 能耗分析", ElectricColor)
        Spacer(Modifier.height(4.dp))
        SectionHint("填入 DeepSeek API Key 后，在分析页点击「🤖 AI 分析」按钮获取全局洞察。在 platform.deepseek.com 获取")
        Spacer(Modifier.height(12.dp))
        var apiKeyText by remember(deepSeekApiKey) { mutableStateOf(deepSeekApiKey) }
        // 已保存时显示脱敏 Key
        val displayHint = if (deepSeekApiKey.isNotBlank()) {
            "已配置: ${deepSeekApiKey.take(5)}...${deepSeekApiKey.takeLast(4)}"
        } else "sk-..."
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(displayHint, color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(ElectricColor),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontFamily = MonoFontFamily)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.saveDeepSeekApiKey(apiKeyText)
                        toast(context, "API Key 已保存")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricColor, contentColor = DarkBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("保存", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            if (deepSeekApiKey.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.saveDeepSeekApiKey("")
                        apiKeyText = ""
                        toast(context, "API Key 已清除")
                    }
                ) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = ErrorNeon.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("清除 Key", color = ErrorNeon.copy(alpha = 0.6f), fontFamily = MonoFontFamily, fontSize = 11.sp)
                }
            }
        }

        // ═══════════════════════════════════════════════
        // 账单分享（月份选择 + 多种分享方式）
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Cloud, "📋 账单分享", NeonYellow)
        Spacer(Modifier.height(12.dp))

        // ── 月份选择器 ──
        var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, "上月", tint = TextSecondary, modifier = Modifier.size(28.dp))
            }
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${selectedMonth.year}年${selectedMonth.monthValue}月",
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            IconButton(onClick = {
                val now = YearMonth.now()
                if (selectedMonth.isBefore(now)) selectedMonth = selectedMonth.plusMonths(1)
            }) {
                Icon(
                    Icons.Default.ChevronRight, "下月", tint = if (selectedMonth.isBefore(YearMonth.now())) TextSecondary else TextTertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        SectionHint("选择月份后，使用下方按钮分享该月账单")

        // ── 分享操作按钮组 ──
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShareActionButton(
                icon = Icons.Default.Share,
                label = "文字",
                desc = "纯文本分享",
                color = ElectricColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        val report = viewModel.generateShareReport(selectedMonth)
                        if (report != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享 ${selectedMonth.year}年${selectedMonth.monthValue}月账单"))
                        } else {
                            toast(context, "该月数据不足，至少需要 2 条电表读数")
                        }
                    }
                }
            )
            ShareActionButton(
                icon = Icons.Default.Cloud,
                label = "HTML",
                desc = "精美格式",
                color = NeonYellow,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        val html = viewModel.generateShareHtml(selectedMonth)
                        if (html != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/html"
                                putExtra(Intent.EXTRA_TEXT, html)
                                putExtra(Intent.EXTRA_HTML_TEXT, html)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享 ${selectedMonth.year}年${selectedMonth.monthValue}月账单"))
                        } else {
                            toast(context, "该月数据不足，至少需要 2 条电表读数")
                        }
                    }
                }
            )
            ShareActionButton(
                icon = Icons.Default.ContentCopy,
                label = "复制",
                desc = "复制文字到剪贴板",
                color = WaterColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        val report = viewModel.generateShareReport(selectedMonth)
                        if (report != null) {
                            clipboardManager.setText(AnnotatedString(report))
                            toast(context, "账单已复制到剪贴板")
                        } else {
                            toast(context, "该月数据不足，至少需要 2 条电表读数")
                        }
                    }
                }
            )
        }

        // ═══════════════════════════════════════════════
        // 天气叠层（Open-Meteo）
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(8.dp))
        Text(
            "图表页面选择「周」或「月」，点击「显示温度」即可查看天气叠层。数据来源 Open-Meteo，完全免费，无需配置。",
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall
        )

        // ═══════════════════════════════════════════════
        // 数据管理
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.FileDownload, "数据管理", ElectricColor)
        Spacer(Modifier.height(12.dp))

        DataActionButton(
            icon = Icons.Default.FileDownload,
            label = "导出数据到文件",
            desc = "覆盖导出至 Downloads/EnergyFlow_Backup.txt",
            color = ElectricColor,
            onClick = { exportToFile() }
        )
        Spacer(Modifier.height(8.dp))
        DataActionButton(
            icon = Icons.Default.FileUpload,
            label = "从文件导入数据",
            desc = "选择之前导出的 .txt 文件恢复数据",
            color = NeonBlue,
            onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }
        )
        Spacer(Modifier.height(16.dp))

        // ── 计费规则模板 JSON ──
        DataActionButton(
            icon = Icons.Default.FileDownload,
            label = "导出规则模板 (JSON)",
            desc = "覆盖导出至 Downloads/EnergyFlow_Rules.json",
            color = SuccessGreen,
            onClick = { exportRulesToFile() }
        )
        Spacer(Modifier.height(8.dp))
        DataActionButton(
            icon = Icons.Default.FileUpload,
            label = "导入规则模板 (JSON)",
            desc = "选择 .json 模板一键切换计费规则",
            color = SuccessGreen,
            onClick = { rulesImportLauncher.launch(arrayOf("application/json", "*/*")) }
        )
        Spacer(Modifier.height(8.dp))
        DataActionButton(
            icon = Icons.Default.Delete,
            label = "清除所有数据",
            desc = "删除全部记录，此操作不可撤销",
            color = ErrorNeon,
            onClick = { showClearDialog = true }
        )

        // ═══════════════════════════════════════════════
        // 显示偏好
        // ═══════════════════════════════════════════════
        Spacer(Modifier.height(28.dp))
        SectionHeader(Icons.Default.Palette, "显示偏好", NeonYellow)
        Spacer(Modifier.height(12.dp))
        PreferenceSwitch("跟随系统深色模式", followSystem) { viewModel.setTheme(isDark, it) }
        if (!followSystem) {
            Spacer(Modifier.height(8.dp))
            PreferenceSwitch("使用深色模式", isDark) { viewModel.setTheme(it, false) }
        }
        Spacer(Modifier.height(8.dp))
        PreferenceSwitch("新增读数时默认展开峰谷", peakValleyExpanded, viewModel::setPeakValleyExpanded)
        Spacer(Modifier.height(8.dp))
        PreferenceSwitch("每日主题（ThemeDist）", themeDistEnabled, viewModel::setThemeDistEnabled)

        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// 组件
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionHint(text: String) {
    Text(
        text,
        color = TextTertiary,
        fontFamily = MonoFontFamily,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ShareActionButton(
    icon: ImageVector,
    label: String,
    desc: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = color),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
            Text(desc, fontFamily = MonoFontFamily, fontSize = 8.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DataActionButton(
    icon: ImageVector,
    label: String,
    desc: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                Text(desc, fontFamily = MonoFontFamily, fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
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
    color: Color,
    hint: String,
    unit: String
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = TextSecondary,
            fontFamily = MonoFontFamily,
            modifier = Modifier.width(76.dp),
            fontSize = 13.sp
        )
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,4}$"))) {
                    text = newValue
                    newValue.toDoubleOrNull()?.let(onValueChange)
                }
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(hint, color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 11.sp) },
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
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ElectricColor,
                checkedTrackColor = ElectricColor.copy(alpha = 0.35f)
            )
        )
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

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
