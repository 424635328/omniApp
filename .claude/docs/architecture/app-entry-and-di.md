# App Entry Points & Dependency Injection

## EnergyFlowApplication
`app/src/main/java/com/example/energyflow/EnergyFlowApplication.kt`

```kotlin
@HiltAndroidApp
class EnergyFlowApplication : Application()
```
- Hilt 入口，无自定义逻辑

## MainActivity
`app/src/main/java/com/example/energyflow/MainActivity.kt`

### 启动流程
```
onCreate()
├── enableEdgeToEdge()
├── handleDeepLink(intent)
└── setContent {
    ├── 读取主题偏好 (darkTheme, followSystem, themeDistEnabled)
    ├── 加载 ThemeDist 缓存 → fetchToday() 刷新
    ├── EnergyFlowTheme(darkTheme, dynamicColors)
    └── Box {
        ├── SplashScreen (showSplash=true 时)
        └── AnimatedVisibility(!showSplash) {
            ├── OnboardingScreen (首次运行)
            └── AppNavGraph (正常入口)
        }
    }
}
```

### Deep Link
- Scheme: `energyflow://record`
- 参数: `electric`, `water`, `gas`, `peak`, `valley`
- 示例: `energyflow://record?electric=16639&water=880`
- 直接插入 MeterRecord 到数据库，无需经过 SmartInputParser

### 注入依赖
```kotlin
@Inject lateinit var userPreferences: UserPreferences
@Inject lateinit var themeDistRepository: ThemeDistRepository
@Inject lateinit var meterRecordDao: MeterRecordDao
```

---

## AppDatabase
`app/src/main/java/com/example/energyflow/data/AppDatabase.kt`

```kotlin
@Database(entities = [MeterRecord::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meterRecordDao(): MeterRecordDao
}
```
- 版本 2，destructive migration (fallbackToDestructiveMigration)
- 单一实体: MeterRecord
- 类型转换: LocalDateTime ↔ Long

---

## DI Modules

### DatabaseModule (`di/DatabaseModule.kt`)
```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase
    @Provides
    fun provideMeterRecordDao(database: AppDatabase): MeterRecordDao
}
```

### DataStoreModule (`di/DataStoreModule.kt`)
```kotlin
@Module @InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences>
    @Provides @Singleton
    fun provideUserPreferences(dataStore: DataStore<Preferences>): UserPreferences
}
```
- DataStore 文件名: `energy_flow_preferences`

### NetworkModule (`di/NetworkModule.kt`)
```kotlin
@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideHttpClient(): HttpClient
}
```
- Ktor HttpClient(Android engine)
- ContentNegotiation + kotlinx.serialization.json
- ignoreUnknownKeys = true, isLenient = true

---

## ThemeDistResponse
`app/src/main/java/com/example/energyflow/data/ThemeDistResponse.kt`

### 数据模型
```kotlin
@Serializable
data class ThemeDistResponse(
    val date: String,
    val generatedAt: String,
    val preset: String,
    val presetName: String?,
    val cssVars: Map<String, String>,  // --color-primary, --color-secondary, etc.
    val customCss: String?,
    val extensions: List<ThemeDistExtension>,
    val available: Int,
    val directory: List<ThemeDistPreset>,
    val dailyIsCommunity: Boolean,
    val apiVersion: String
)

data class ThemeDistColors(
    val primary: Color,    // 默认 #FFFF00
    val secondary: Color,  // 默认 #FF6600
    val accent: Color,     // 默认 #00BFFF
    val background: Color, // 默认 #0A0A0A
    val surface: Color,    // 默认 #1A1A1A
    val text: Color,       // 默认 White
    val textMuted: Color,  // 默认 #B0B0B0
    val border: Color,     // 默认 #2A2A2A
)
```

---

## Formatters
`app/src/main/java/com/example/energyflow/ui/utils/Formatters.kt`

### 函数
| 函数 | 用途 | 格式 |
|------|------|------|
| `formatInt(value)` | 整数 | `%.0f` |
| `formatDecimal1(value)` | 一位小数 | `%.1f` |
| `formatDecimal2(value)` | 两位小数 | `%.2f` |
| `formatElectric(value)` | 电表读数 | `%.2f`，null→"0.00" |
| `formatWater(value)` | 水表读数 | `%.2f`，null→"0.00" |
| `formatGas(value)` | 燃气读数 | `%.2f`，null→"0.00" |
| `formatElecDisplay(value)` | 主页电表显示 | `%.0f`，null→"-" |
| `formatWaterDisplay(value)` | 主页水表显示 | `%.0f`，null→"-" |
| `formatGasDisplay(value)` | 主页燃气显示 | `%.0f`，null→"-" |
| `formatPeakValleyDisplay(value)` | 峰谷显示 | `%.0f`，null→"-" |
| `formatDailyConsumption(value)` | 日均消耗 | `%.1f 度/天` |

### 使用规范
- 主页时间线: `formatElecDisplay` / `formatWaterDisplay` (整数)
- 编辑表单: `formatElectric` / `formatWater` (两位小数)
- 图表 KPI: `formatDecimal1` / `formatDecimal2`
- 费用: `formatDecimal2`

---

## MeterRecordDao 完整查询列表
`app/src/main/java/com/example/energyflow/data/MeterRecordDao.kt`

### 写操作
| 方法 | 说明 |
|------|------|
| `insert(record): Long` | 插入，返回自增 ID |
| `update(record)` | 更新 |
| `delete(record)` | 删除 |
| `deleteAll()` | 清空表 |

### 读操作 (Flow)
| 方法 | SQL | 用途 |
|------|-----|------|
| `getAllRecords()` | ORDER BY timestamp DESC, id DESC | 全量查询 |
| `getRecordsLimited(limit)` | LIMIT :limit | 分页加载 (首页 150) |
| `getRecordsByTimeRange(start, end)` | BETWEEN :start AND :end | 时间范围筛选 |
| `getElectricRecords()` | WHERE isElectricRecorded=1 | 电表记录 |
| `getWaterRecords()` | WHERE isWaterRecorded=1 | 水表记录 |
| `getGasRecords()` | WHERE isGasRecorded=1 | 燃气记录 |
| `getRecordsWithNotes()` | WHERE note IS NOT NULL AND note!='' | 有备注的记录 |
| `getRecordCount()` | COUNT(*) | 总数 |
| `getElectricCount()` | COUNT(*) WHERE electric | 电表计数 |
| `getWaterCount()` | COUNT(*) WHERE water | 水表计数 |
| `getGasCount()` | COUNT(*) WHERE gas | 燃气计数 |
| `getNoteCount()` | COUNT(*) WHERE note | 备注计数 |

### 读操作 (suspend)
| 方法 | SQL | 用途 |
|------|-----|------|
| `getLatestRecord()` | ORDER BY timestamp DESC LIMIT 1 | 最新记录 |
| `getPreviousRecord(currentTime)` | WHERE timestamp < :currentTime LIMIT 1 | 前一条记录 |
| `getLatestElectricRecord()` | WHERE electric AND total NOT NULL LIMIT 1 | 最新电表 |
| `getLatestWaterRecord()` | WHERE water AND total NOT NULL LIMIT 1 | 最新水表 |
| `loadMoreRecords(limit, offset)` | LIMIT :limit OFFSET :offset | 滚动加载 |
