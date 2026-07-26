package com.example.energyflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ════════════════════════════════════════════
// M3 形状系统 — 圆角卡片、按钮、容器
// ════════════════════════════════════════════

val EnergyFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // 芯片、小标签 — 稍大更现代
    small = RoundedCornerShape(10.dp),       // 按钮、输入框 — 稍大更圆润
    medium = RoundedCornerShape(14.dp),      // 卡片、对话框 — 稍大更柔和
    large = RoundedCornerShape(20.dp),       // 底部弹窗、大面板 — 更圆润
    extraLarge = RoundedCornerShape(28.dp)   // 全屏弹窗 — 更圆润
)
