package com.example.energyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energyflow.ui.theme.DarkBackground
import com.example.energyflow.ui.theme.DarkCard
import com.example.energyflow.ui.theme.DarkSurface
import com.example.energyflow.ui.theme.ElectricColor
import com.example.energyflow.ui.theme.MonoFontFamily
import com.example.energyflow.ui.theme.NeonYellow
import com.example.energyflow.ui.theme.TextPrimary
import com.example.energyflow.ui.theme.TextSecondary

@Composable
fun BatchImportSheet(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
    importing: Boolean = false
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "取消",
                    tint = TextSecondary
                )
            }

            Text(
                text = "批量导入",
                style = MaterialTheme.typography.titleLarge,
                color = NeonYellow,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onImport(text)
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = "导入",
                    tint = if (text.isNotBlank()) ElectricColor else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 说明文字
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "支持的格式：",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = """
• 日期头：7.14
• 时间+数值：17.17 16776
• 时间+备注：16.39打开冰箱
• 日期+时间+数值：7.13 01.23 16672
• 电表+水表：7.1 16639 880 两家
• 中文时间：6.26下午六点开始启用冰箱
• 峰谷值：9310.75 / 7298.66（自动识别）
• 水表前缀：水0879
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = MonoFontFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 输入框
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            placeholder = {
                Text(
                    text = "粘贴能耗数据...\n\n示例：\n7.14\n17.17 16776\n16.39打开冰箱",
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontFamily = MonoFontFamily
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricColor,
                unfocusedBorderColor = DarkSurface,
                cursorColor = ElectricColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonoFontFamily
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 导入按钮
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onImport(text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = text.isNotBlank() && !importing,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricColor,
                contentColor = DarkBackground,
                disabledContainerColor = DarkCard,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (importing) {
                Text(
                    text = "导入中...",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "开始导入",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
