---
name: energyflow-data-migration
description: Data migration guide — Room schema changes, DataStore version upgrades, data repairs
---

# EnergyFlow — Data Migration Guide

**Use when**: Changing Room entities / changing DataStore structure / data format changes.

## Room Schema Changes

### ⚠️ Current State
`AppDatabase` uses `fallbackToDestructiveMigration()`! This means:
- **Any schema change will clear all data**
- This is a development-phase compromise; production requires Migrations

### Safe Change Flow

#### Option A: Development Phase (Current)
```kotlin
@Database(version = 3) // From 2 → 3
abstract class AppDatabase : RoomDatabase() {
    // fallbackToDestructiveMigration() remains unchanged
    // Development data will be lost, but that's okay
}
```
- ✅ Simple
- ❌ Data cleared
- Applies to: Dev/test environments

#### Option B: With Migration (Production-Ready)
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

// In DatabaseModule
Room.databaseBuilder(context, AppDatabase::class.java, "energy_flow_database")
    .addMigrations(AppDatabase.MIGRATION_2_3)
    .build()
```

### Common Schema Change Quick Reference

| Change | SQL |
|--------|-----|
| Add column (nullable) | `ALTER TABLE meter_records ADD COLUMN new_col REAL` |
| Add column (NOT NULL) | `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0` |
| Add index | `CREATE INDEX idx_name ON meter_records(column)` |
| Rename table | Room does not support direct ALTER, requires Migration |

### Checklist
- [ ] Bump `@Database(version = N+1)`
- [ ] Add Migration object (or keep destructive fallback)
- [ ] Update `MeterRecord` entity (add new field)
- [ ] Update `Converters.kt` (if new field needs type conversion)
- [ ] Update Room DAO (if new field needs new queries)
- [ ] Run all tests to confirm

---

## DataStore Version Migration

### Billing Rules Migration (UserPreferences)
```kotlin
// UserPreferences.kt
companion object {
    const val CURRENT_BILLING_VERSION = 2 // bump this
}
```

**Trigger condition**: Stored version < CURRENT_BILLING_VERSION
**Behavior**: Automatically resets billing rules to defaults
**Note**: Only triggers on first access of `billingRules.first()`, with "already migrated" flag to prevent duplicates

### New DataStore Key Flow
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

### Checklist
- [ ] Key name is clear — no abbreviations
- [ ] Default value is reasonable
- [ ] If key is renamed → need to migrate old key data
- [ ] Do not use DataStore for large amounts of data → use Room
- [ ] DataStore is async, read/write in coroutines

---

## Data Repair

### Manually Fix Anomalous Data
```kotlin
// Add repair function in MeterRepository
suspend fun fixDecreasingReadings(): Int {
    val records = dao.getAllRecords().first()
    var fixed = 0
    for (i in 1 until records.size) {
        if (records[i].electricTotal != null && records[i-1].electricTotal != null) {
            if (records[i].electricTotal!! < records[i-1].electricTotal!!) {
                dao.delete(records[i]) // Or mark as "meter replaced"
                fixed++
            }
        }
    }
    return fixed
}
```

### Batch Data Repair Checklist
- [ ] Backup: Export data before modifying (Settings → Export Data)
- [ ] Dry-run: Only print records to modify, do not actually modify
- [ ] Transaction: Use `@Transaction` for atomicity
- [ ] Reversible: Provide rollback method

## Prohibited Actions
- ❌ Do not use destructive migration on devices with user data
- ❌ Do not forget to bump schema version
- ❌ Do not write complex business logic in Migration — only DDL changes
- ❌ Do not operate DataStore edit on the main thread

## Related Skills
- Run tests: `energyflow-test` — must run all tests after migration
- Pre-scan: `energyflow-quick-scan` — scan for lingering references after migration
