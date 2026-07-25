---
name: energyflow-build-debug
description: 构建与调试——编译失败排查、Gradle 问题、Hilt 缓存、设备调试
---

# EnergyFlow — 构建与调试

**用途**: 编译失败 / Gradle 报错 / Hilt 问题 / APK 安装调试时使用。

## 快速修复（按频率排序）

### 1. Hilt 构建缓存投毒（最常见）
```bash
# 症状: ClassNotFoundException，类明明存在
./gradlew :app:assembleDebug --rerun-tasks
```

### 2. Gradle 缓存问题
```bash
./gradlew clean
rm -rf ~/.gradle/caches
./gradlew :app:assembleDebug
```

### 3. SDK 路径问题
```bash
# 检查 local.properties 中的 SDK 路径
cat local.properties
# 应该类似: sdk.dir=C\:/Users/George/AppData/Local/Android/Sdk
```

### 4. JDK 版本问题
```bash
java -version
# 需要 JDK 17
```

## 编译错误速查

| 错误信息 | 原因 | 修复 |
|---------|------|------|
| `Unresolved reference: BuildConfig` | AGP buildFeatures 未启用 | build.gradle.kts: `buildFeatures { buildConfig = true }` |
| `Cannot find symbol class Hilt_*` | Hilt 注解处理器未运行 | 确认有 `@HiltAndroidApp` / `@AndroidEntryPoint` |
| `Duplicate class` | 依赖冲突 | `./gradlew :app:dependencies` 查冲突 |
| `java.time not found` | KMP shared 模块 | 改用 `kotlinx.datetime` |
| `expect/actual mismatch` | KMP 平台实现缺失 | 检查 androidMain/jvmMain 有对应 actual |

## 运行/调试

### 安装 APK
```bash
# 构建 debug APK
./gradlew :app:assembleDebug
# APK 位置: app/build/outputs/apk/debug/app-debug.apk

# 已连接设备时直接安装运行
./gradlew :app:installDebug

# 查看设备 logcat (过滤 EnergyFlow)
adb logcat | grep -i energyflow
```

### 清除 App 数据
```bash
adb shell pm clear com.example.energyflow
# 这会清除 Room 数据库 + DataStore！
```

### 查看数据库
```bash
# 方法1: Android Studio App Inspection (推荐)
# 方法2: adb + sqlite3
adb shell
run-as com.example.energyflow
cat /data/data/com.example.energyflow/databases/energy_flow_database

# 方法3: 导出数据库
adb exec-out run-as com.example.energyflow cat databases/energy_flow_database > db.sqlite
```

## Gradle 性能

### 加速构建
```bash
# 只编译 app 模块（跳过 shared 如果不是必须）
./gradlew :app:compileDebugKotlin

# 并行构建（默认已启用）
./gradlew :app:assembleDebug --parallel

# 离线模式（跳过网络依赖检查）
./gradlew :app:assembleDebug --offline

# 增大 Gradle 内存（gradle.properties）
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### Gradle 任务速查
```bash
# 列出所有可用任务
./gradlew tasks

# 查看依赖树
./gradlew :app:dependencies

# 查看每个任务的耗时
./gradlew :app:assembleDebug --profile
# 报告: build/reports/profile/

# 查看测试报告
open app/build/reports/tests/testDebugUnitTest/index.html
```

## 相关 Skills
- Bug诊断: `energyflow-diagnose` — 如果是运行时错误而非编译错误
- 跑测试: `energyflow-test` — 编译通过后跑测试验证
