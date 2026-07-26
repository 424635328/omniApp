# App 入口链 · 扫表 · 年度报告 · 快捷磁贴

## File
- 入口: `app/src/main/java/com/example/energyflow/MainActivity.kt` (~180 lines)
- 开屏: `app/src/main/java/com/example/energyflow/ui/SplashScreen.kt` (~322 lines)
- 引导: `app/src/main/java/com/example/energyflow/ui/OnboardingScreen.kt` (~576 lines)
- 扫表: `app/src/main/java/com/example/energyflow/ui/camera/ScanScreen.kt` (~464 lines)
- 报告: `app/src/main/java/com/example/energyflow/ui/WrappedScreen.kt` (~778 lines) + `WrappedState.kt` + `WrappedViewModel.kt`
- 磁贴: `app/src/main/java/com/example/energyflow/ui/tile/EnergyTileService.kt` (~85 lines)

---

## 1. App 入口链 (MainActivity)

### 启动流程 (MainActivity.kt:84-122)
```
SplashScreen (showSplash=true)
  → onFinished 后 AnimatedVisibility 淡入主内容
    → !isOnboardingComplete && !onboardingDismissed → OnboardingScreen
    → 否则 → AppNavGraph()
```

| 状态 | 位置 | 说明 |
|------|------|------|
| `showSplash` | MainActivity.kt:84 | 本地 state，SplashScreen 的 `onFinished` 置 false |
| `isOnboardingComplete` | MainActivity.kt:85 | `userPreferences.isOnboardingComplete`，**initialValue = true**（避免老用户冷启动闪现引导页；新用户需等 DataStore 发射 false 才显示引导） |
| `onboardingDismissed` | MainActivity.kt:86-92 | 本地 state；LaunchedEffect 中调用 `userPreferences.completeOnboarding()` 持久化 |

MainActivity 还负责：
- ThemeDist 动态主题加载（缓存 → 网络，MainActivity.kt:69-82）
- 深链 `energyflow://record?electric=&water=&gas=&peak=&valley=` → 直接插入 MeterRecord（MainActivity.kt:133-173，`onNewIntent` 也处理）

### SplashScreen (SplashScreen.kt:73)
```kotlin
fun SplashScreen(onFinished: () -> Unit)
```
- 多阶段入场动画：图标弹性缩放 → 标题滑入 → 副标题淡入（LaunchedEffect 序列，SplashScreen.kt:126-141）
- 无限循环层：呼吸缩放 + 波纹涟漪 + 旋转电弧（Canvas 绘制，SplashScreen.kt:156-192）
- **退出只靠用户点击**：动画完成后显示"点击屏幕进入 →"脉冲提示（SplashScreen.kt:304-319），点击任意位置 → 150ms 淡出 → `onFinished()`（SplashScreen.kt:114-123）。没有超时自动进入。

### OnboardingScreen (OnboardingScreen.kt:78)
```kotlin
fun OnboardingScreen(onComplete: () -> Unit)
```
3 页 HorizontalPager（OnboardingScreen.kt:105-114）：

| 页 | 内容 |
|----|------|
| Page1 智能输入 | **真实可交互**：内嵌 `SmartInputParser` 实时解析用户输入并展示结果（OnboardingScreen.kt:185-338），带示例 chip |
| Page2 三表合一 | 电/水/气三张介绍卡片 |
| Page3 主动洞察 | 模拟 Insight Pill + 功能列表 |

`onComplete` 触发点：右上角"跳过"（OnboardingScreen.kt:100）或第 3 页"开始使用"按钮（OnboardingScreen.kt:153-162）。

---

## 2. ScanScreen — 拍照识别表读数

### 契约 (ScanScreen.kt:96-98)
```kotlin
fun ScanScreen(
    onResult: (String) -> Unit,  // 用户确认后的文本（可能已手动编辑）
    onDismiss: () -> Unit
)
```

### AppNavGraph 覆盖层接线
ScanScreen **不在标签导航里**，是 state 驱动的全屏覆盖层：
- `showScan` / `pendingOcrResult` 声明于 AppNavGraph.kt:86-87
- 覆盖层渲染 + PredictiveBackHandler：AppNavGraph.kt:212-223，`onResult` 把文本存入 `pendingOcrResult` 并关闭覆盖层
- Home 标签的 LaunchedEffect 消费结果：`mainVM.ocrAutoFill(it)` 后清空（AppNavGraph.kt:186-191）
- 入口：MainScreen 的 `onScan = { showScan = true }`（AppNavGraph.kt:194）

### 权限流 (ScanScreen.kt:101-113)
`ContextCompat.checkSelfPermission` 初始化 → 未授权时 LaunchedEffect 自动弹系统权限框 → 拒绝则显示 `PermissionDeniedView`（可重新请求，ScanScreen.kt:445）。

### CameraX + ML Kit 接线
- 识别器：`TextRecognition.getClient(ChineseTextRecognizerOptions...)` 中文模型（ScanScreen.kt:116-118），**DisposableEffect 中 close() 释放**（ScanScreen.kt:119-121）
- 相机：AndroidView 包 PreviewView，`ProcessCameraProvider.bindToLifecycle(preview, imageCapture)`（ScanScreen.kt:165-199）；`onRelease` 中 `unbindAll()` 防止后台持有相机
- 手电筒：保存 `cameraControl` 引用，`enableTorch()` 轻量切换（ScanScreen.kt:160, 245-250）
- 取景框：Canvas + `BlendMode.Clear` 镂空遮罩（需 `alpha = 0.99f` 开启离屏缓冲，ScanScreen.kt:202-230）

### 识别流水线 (ScanScreen.kt:292-346)
```
takePicture → ImagePreprocessor.preprocess(imageProxy)
  → textRecognizer.process(inputImage)
  → OcrSmartProcessor.process(rawText) + calculateConfidence()
  → confidence < 0.3 → 拒绝重拍；否则进入 ScanResultView
```
`ScanResultView`（ScanScreen.kt:357-441）：OCR 文本**可编辑**，用 `SmartInputParser.parseWithContext` 实时预览解析结果；仅当有 Success 结果时"使用"按钮可点，`onConfirm(editableText)` 传出的是编辑后文本。

> OCR 数据层（OcrSmartProcessor、ImagePreprocessor 算法细节）见 `.claude/docs/ui-layer/settings-and-reports.md`，此处不重复。

---

## 3. WrappedScreen — 月度能耗报告

### 状态模型 (WrappedState.kt:5-21)
```kotlin
data class WrappedState(
    val yearMonth: YearMonth,
    val totalKwh: Double, val totalCost: Double,
    val totalCo2Kg: Double, val treeDays: Int,
    val badges: List<String>,
    val peakKwh: Double, val valleyKwh: Double, val flatKwh: Double,
    val eventHighlights: List<String>,   // 当前始终 emptyList
    val previousPeriodKwh: Double?, val previousPeriodCost: Double?,
    val tips: List<String>, val recordCount: Int,
    val isLoading: Boolean = false
)
```

### WrappedViewModel.loadReport(yearMonth) (WrappedViewModel.kt:47-147)
1. 取电表记录，过滤当月 + `electricTotal != null`，按时间排序
2. **少于 2 条记录 → 空报告**（提示"添加更多电表数据"，WrappedViewModel.kt:61-73）
3. 总电量 = 月末读数 − 月初读数（累计值相减，`coerceAtLeast(0.0)`，WrappedViewModel.kt:77）
4. 峰/谷增量同样相减并 clamp 到 totalKwh 内，平 = 总 − 峰 − 谷（WrappedViewModel.kt:83-85）
5. `CostEngine.calculateBill()` → 费用；CO₂ = totalKwh × **0.583**（中国电网平均排放因子）；treeDays = CO₂/20（WrappedViewModel.kt:87-92）
6. 徽章规则（WrappedViewModel.kt:94-100）：<300 度→节能先锋、<500→合理用电、treeDays>5→绿色达人、峰占比<0.3→错峰能手等
7. 上月对比 + 节能 tips（WrappedViewModel.kt:102-126）

### 页面结构 (WrappedScreen.kt:102, 180-212)
```
pages = ["hero", "carbon", "peakvalley", "badges", "savings", "share"]
0 HeroPage       — 大字 kWh / 费用 / CO₂
1 CarbonTreePage — Canvas 动画树 (treeProgress = treeDays/365)
2 PeakValleyPage — 峰/平/谷堆叠条 + 图例（比例动画 tween 1000ms）
3 BadgesPage     — 成就徽章 + 未解锁提示
4 SavingsTipsPage — 上月对比 + tips 列表
5 SharePage      — 导出分享按钮
```
翻页：水平拖拽手势（±50px 阈值，WrappedScreen.kt:110-121）+ 底部"上页/下页"按钮；`AnimatedContent` 方向感知滑动过渡（WrappedScreen.kt:163-178）。作为覆盖层由 AppNavGraph 的 `showWrapped` 控制（AppNavGraph.kt:90, 226-231），入口是 ChartScreen 的 `onWrapped` 回调。

### 导出路径 (WrappedScreen.kt:186-211)
```
SharePage onExport → ReportExporter.ReportContent(...)
  → ReportExporter.export(context, content)   // 生成 PNG，返回 Uri
  → ShareUtils.shareImage(context, uri)       // 系统分享
```
`exporting` 三态 Int（0=idle/1=exporting/2=done）；导出异常被静默吞掉（`catch (_: Exception) {}`）。

---

## 4. EnergyTileService — 快捷设置磁贴

`app/src/main/java/com/example/energyflow/ui/tile/EnergyTileService.kt`

- `@AndroidEntryPoint` 的 TileService，Hilt 注入 `MeterRecordDao` / `CostEngine` / `UserPreferences`（EnergyTileService.kt:22-28）
- 生命周期：`onTileAdded` / `onStartListening` → `refreshTile()`（EnergyTileService.kt:32-40）
- `refreshTile()`（EnergyTileService.kt:56-84）：当月电表记录 ≥2 条时 `last - first` 算月用电，`costEngine.calculateSimple(kwh)` 估费用，写入 `tile.subtitle`（"xx度 / ¥xx"），否则"暂无数据"
- `onClick`（EnergyTileService.kt:42-54）：`unlockAndRun` 中构造 MainActivity Intent → 包成 **PendingIntent** → `startActivityAndCollapse(pendingIntent)`

### Android 14+ 陷阱
`startActivityAndCollapse(Intent)` 在 targetSdk 34+ 上直接抛 `UnsupportedOperationException`，必须使用 `PendingIntent` 重载（`FLAG_IMMUTABLE` 必填）。当前工作区有一处未提交改动即修复此问题（把裸 Intent 换成 PendingIntent）。

---

## 已知问题 / 陷阱
- SplashScreen 无超时自动进入，**必须点击**才会触发 `onFinished`
- `isOnboardingComplete` 的 collectAsStateWithLifecycle `initialValue = true`：新用户首帧可能先渲染 AppNavGraph，DataStore 发射 false 后才切到引导页
- ScanScreen 的确认文本是用户可编辑过的，`onResult` 收到的不一定是原始 OCR 输出
- OCR 置信度 < 0.3 会拦截并要求重拍（ScanScreen.kt:325-327）
- WrappedViewModel 只统计电表；当月不足 2 条记录时报告为空
- WrappedViewModel 构造器注入了 `PredictiveAnalyzer` 但 `loadReport` 未使用（疑似预留/死依赖）
- Wrapped 导出失败无任何用户提示（异常被吞）

## 相关文档
- 标签导航与 ViewModel 常驻策略: `.claude/docs/ui-layer/theme-and-navigation.md`
- 分析页 (Wrapped 入口): `.claude/docs/ui-layer/chart-screen.md`
- OCR 数据层 / 报告导出: `.claude/docs/ui-layer/settings-and-reports.md`
- 计费引擎: `.claude/docs/data-layer/cost-engine.md`
- 数据模型: `.claude/docs/data-layer/meter-record.md`
