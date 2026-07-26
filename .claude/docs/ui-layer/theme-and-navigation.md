# UI Layer 设计

## 导航架构
`app/src/main/java/com/example/energyflow/ui/navigation/AppNavGraph.kt`

### 策略：Tab Instant Switch
- **无 NavHost**，使用 `when(tab)` 分支切换
- ViewModel 常驻内存（Activity 级 `hiltViewModel` 缓存）
- `AnimatedContent` + slide/fade 过渡动画（280ms）
- PredictiveBackHandler 支持预测性返回手势

### Tab 结构
| Tab | Screen | ViewModel | Icon |
|-----|--------|-----------|------|
| 记录 | `MainScreen` | `MainViewModel` | Home |
| 分析 | `ChartScreen` | `ChartViewModel` | List |
| 计费 | `BillingSettingsScreen` | `BillingSettingsViewModel` | Settings |

### 覆盖层
- **扫码页** (`ScanScreen`) — state 驱动，OCR 结果通过 `pendingOcrResult` 回传
- **年度报告** (`WrappedScreen`) — state 驱动，独立覆盖层

---

## Theme 设计
`app/src/main/java/com/example/energyflow/ui/theme/`

### 色彩系统 (Color.kt)
**重要**: `ElectricColor`, `WaterColor`, `GasColor` 等是动态值，跟随 `ThemeState.colors` 变化。`DarkBackground`, `DarkCard`, `DarkSurface`, `TextPrimary` 等是向后兼容别名，自动跟随暗/亮模式。

#### 动态语义色（跟随 ThemeState）
| 色彩 | 默认值 | 用途 |
|------|--------|------|
| `ElectricColor` | ElectricStart (#00A8FF) | 主色调，电表/强调，可被天气主题覆盖 |
| `ElectricPeakColor` | StaticPeakColor (#FF9922) | 峰电柱状图 |
| `ElectricValleyColor` | StaticValleyColor (#9977EE) | 谷电柱状图 |
| `WaterColor` | WaterStart (#00DDBB) | 水表 |
| `GasColor` | GasStart (#FF8844) | 燃气 |

#### 静态色（不跟随主题）
| 色彩 | 值 | 用途 |
|------|-----|------|
| `ErrorNeon` | #FF4466 | 错误/三档/偏高 |
| `WarningNeon` | #FFBB33 | 警告/二档 |
| `SuccessGreen` | #00DD99 | 成功/降序/偏低 |
| `StaticPeakColor` | #FF9922 | 峰电静态色 |
| `StaticValleyColor` | #9977EE | 谷电静态色 |

#### 向后兼容别名（自动跟随暗/亮模式）
| 别名 | 暗色值 | 用途 |
|------|--------|------|
| `DarkBackground` | BackgroundDark (#080A12) | 背景 |
| `DarkSurface` | SurfaceDark (#111425) | 表面 |
| `DarkCard` | SurfaceVariant (#1B2035) | 卡片 |
| `OutlineDark` | #2A304A | 边框/平电柱状图 |
| `TextPrimary` | #E2E8F0 (暗) / #0F172A (亮) | 主文本 |
| `TextSecondary` | #94A3B8 (暗) / #475569 (亮) | 次文本 |
| `TextTertiary` | #64748B | 三级文本 |

#### 遗留别名（向后兼容）
| 别名 | 实际指向 | 说明 |
|------|---------|------|
| `NeonYellow` | ElectricStart (#00A8FF) | 原名青绿，现为电光蓝 |
| `NeonBlue` | WaterStart (#00DDBB) | 实际是碧波青 |
| `NeonOrange` | StaticPeakColor (#FF9922) | |
| `NeonCyan` | #4499FF | |
| `NeonRed` | GasStart (#FF8844) | |

#### 天气主题覆盖 (ThemeState.applyWeatherTheme)
```
tempMax > 38°C → ElectricColor = #FF4500 (酷暑红)
tempMax > 32°C → ElectricColor = #FF8800 (炎热橙)
tempMax > 20°C → ElectricColor = ElectricStart (#00A8FF 常温电光蓝)
tempMax > 10°C → ElectricColor = ElectricEnd (#0058DD 偏冷深蓝)
else           → ElectricColor = ElectricValleyColor (#9977EE 寒冷蓝紫)
```

### 字体
`MonoFontFamily` — 等宽字体，全局使用

### 主题动态化
`ThemeState` — 根据天气温度动态调整主题色
- `ThemeDistRepository` — 每日主题分发
- `UserPreferences.themeDistEnabled` — 开关

---

## MainScreen 组件树
```
Scaffold
├── HomeTopBar (可折叠，滚动时压缩)
├── InsightPill (AI 洞察胶囊，可展开)
├── TierProgressBar (阶梯电价水位线 + 环比)
├── FilterBar (全部/电/水/气/备注 + 计数)
├── DateFilterBar (日期范围筛选)
├── LazyColumn (时间线列表)
│   └── TimelineItem × N
│       ├── 时间戳 + 类型图标
│       ├── 读数 + 消耗差值
│       ├── 备注标签
│       └── 滑动删除 / 点击编辑
├── FABColumn
│   ├── SmallFAB (批量导入)
│   └── MainFAB (添加记录，长按扫码)
├── AddRecordSheet (ModalBottomSheet)
├── BatchImportSheet (ModalBottomSheet)
├── EditRecordSheet (ModalBottomSheet)
└── AnomalyWarningDialog (AlertDialog)
```

## 动画规范
- FAB: spring DampingRatioMediumBouncy + StiffnessLow
- 列表项: animateItem(fadeIn 200ms, fadeOut 150ms, spring placement)
- 筛选栏: spring DampingRatioMediumBouncy + StiffnessMedium
- 空状态: infiniteRepeatable breathe animation (scale 0.95-1.05, alpha 0.3-0.6)
- 顶栏折叠: tween 250ms
