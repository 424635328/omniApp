---
name: energyflow-data-migration
description: 数据迁移引导——Room schema 变更、DataStore 版本升级、数据修复
---

# EnergyFlow — 数据迁移引导

**用途**: 改 Room 实体 / 改 DataStore 结构 / 数据格式变更时使用。

## Room Schema 变更

### ⚠️ 当前状态
`AppDatabase` 使用 `fallbackToDestructiveMigration()`！意味着：
- **任何 schema 变更都会清空全部数据**
- 这是开发阶段的妥协，生产环境必须有 Migration

### 安全变更流程

#### 方案 A: 开发阶段（当前）
```kotlin
@Database(version = 3) // 从 2 → 3
abstract class AppDatabase : RoomDatabase() {
    // fallbackToDestructiveMigration() 保持不变
    // 开发数据会丢失，但没关系
}
```
- ✅ 简单
- ❌ 数据清零
- 适用: 开发/测试环境

#### 方案 B: 有 Migration（生产就绪）
```kotlin
@Database(version = 3)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE meter_records ADD COLUMN gasTotal REAL")
            }
        }
    }
}

// 在 DatabaseModule 中
Room.databaseBuilder(context, AppDatabase::class.java, "energy_flow_database")
    .addMigrations(AppDatabase.MIGRATION_2_3)
    .build()
```

### 常见 Schema 变更速查

| 变更 | SQL |
|------|-----|
| 加列 (nullable) | `ALTER TABLE meter_records ADD COLUMN new_col REAL` |
| 加列 (NOT NULL) | `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0` |
| 加索引 | `CREATE INDEX idx_name ON meter_records(column)` |
| 重命名表 | Room 不支持直接 ALTER，需要 Migration |

### 检查清单
- [ ] bump `@Database(version = N+1)`
- [ ] 添加 Migration 对象（或保持 destructive fallback）
- [ ] 更新 `MeterRecord` 实体（加新字段）
- [ ] 更新 `Converters.kt`（如果新字段需要类型转换）
- [ ] 更新 Room DAO（如果新字段需要新查询）
- [ ] 运行全量测试确认

---

## DataStore 版本迁移

### 计费规则迁移 (UserPreferences)
```kotlin
// UserPreferences.kt
companion object {
    const val CURRENT_BILLING_VERSION = 2 // bump this
}
```

**触发条件**: 存储的版本 < CURRENT_BILLING_VERSION
**行为**: 自动重置计费规则为默认值
**注意**: 只在 `billingRules.first()` 首次访问时触发，且有"已迁移"标记防重复

### 新增 DataStore Key 流程
```kotlin
// UserPreferences.kt
private val NEW_KEY = booleanPreferencesKey("new_setting")

val newSetting: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[NEW_KEY] ?: false // default
}

suspend fun setNewSetting(value: Boolean) {
    dataStore.edit { prefs -> prefs[NEW_KEY] = value }
}
```

### 检查清单
- [ ] Key 名称清晰 —— 不要缩写
- [ ] 默认值合理
- [ ] 如果 Key 改名 → 需要迁移旧 Key 的数据
- [ ] 不要用 DataStore 存大量数据 → 用 Room
- [ ] DataStore 异步，在协程中读写

---

## 数据修复

### 手动修复异常数据
```kotlin
// 在 MeterRepository 中添加修复函数
suspend fun fixDecreasingReadings(): Int {
    val records = dao.getAllRecords().first()
    var fixed = 0
    for (i in 1 until records.size) {
        if (records[i].electricTotal != null && records[i-1].electricTotal != null) {
            if (records[i].electricTotal!! < records[i-1].electricTotal!!) {
                dao.delete(records[i]) // 或标记为"换表"
                fixed++
            }
        }
    }
    return fixed
}
```

### 批量数据修复检查清单
- [ ] 备份: 在修数据前先导出 (设置页 → 导出数据)
- [ ] Dry-run: 先只打印要修改的记录，不实际修改
- [ ] 事务: 用 `@Transaction` 保证原子性
- [ ] 可逆: 提供回滚方式

## 禁止事项
- ❌ 不要在有用户数据的设备上使用 destructive migration
- ❌ 不要忘记 bump schema version
- ❌ 不要在 Migration 中写复杂业务逻辑 —— 只改 DDL
- ❌ 不要在主线程操作 DataStore edit

## 相关 Skills
- 跑测试: `energyflow-test` — 迁移后必须全量测试
- 预检: `energyflow-quick-scan` — 迁移后扫描遗留引用
