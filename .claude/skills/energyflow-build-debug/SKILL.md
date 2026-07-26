---
name: energyflow-build-debug
description: Build and debugging — compilation failure troubleshooting, Gradle issues, Hilt cache, device debugging
---

# EnergyFlow — Build and Debugging

**Use when**: Compilation failure / Gradle errors / Hilt issues / APK installation debugging.

## Quick Fixes (Sorted by Frequency)

### 1. Hilt Build Cache Poisoning (Most Common)
```bash
# Symptom: ClassNotFoundException, class clearly exists
./gradlew :app:assembleDebug --rerun-tasks
```

### 2. Gradle Cache Issues
```bash
./gradlew clean
rm -rf ~/.gradle/caches
./gradlew :app:assembleDebug
```

### 3. SDK Path Issues
```bash
# Check SDK path in local.properties
cat local.properties
# Should be like: sdk.dir=C\:/Users/George/AppData/Local/Android/Sdk
```

### 4. JDK Version Issues
```bash
java -version
# Requires JDK 17
```

## Compilation Error Quick Reference

| Error Message | Cause | Fix |
|--------------|-------|-----|
| `Unresolved reference: BuildConfig` | AGP buildFeatures not enabled | build.gradle.kts: `buildFeatures { buildConfig = true }` |
| `Cannot find symbol class Hilt_*` | Hilt annotation processor not running | Confirm `@HiltAndroidApp` / `@AndroidEntryPoint` exists |
| `Duplicate class` | Dependency conflict | `./gradlew :app:dependencies` to check conflicts |
| `java.time not found` | KMP shared module | Use `kotlinx.datetime` instead |
| `expect/actual mismatch` | KMP platform implementation missing | Check androidMain/desktopMain has corresponding actual |

## Run/Debug

### Install APK
```bash
# Build debug APK
./gradlew :app:assembleDebug
# APK location: app/build/outputs/apk/debug/app-debug.apk

# Install and run directly on connected device
./gradlew :app:installDebug

# View device logcat (filter EnergyFlow)
adb logcat | grep -i energyflow
```

### Clear App Data
```bash
adb shell pm clear com.example.energyflow
# This will clear Room database + DataStore!
```

### View Database
```bash
# Method 1: Android Studio App Inspection (Recommended)
# Method 2: adb + sqlite3
adb shell
run-as com.example.energyflow
cat /data/data/com.example.energyflow/databases/energy_flow_database

# Method 3: Export database
adb exec-out run-as com.example.energyflow cat databases/energy_flow_database > db.sqlite
```

## Gradle Performance

### Speed Up Builds
```bash
# Only compile app module (skip shared if not needed)
./gradlew :app:compileDebugKotlin

# Parallel build (enabled by default)
./gradlew :app:assembleDebug --parallel

# Offline mode (skip network dependency check)
./gradlew :app:assembleDebug --offline

# Increase Gradle memory (gradle.properties)
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### Gradle Task Quick Reference
```bash
# List all available tasks
./gradlew tasks

# View dependency tree
./gradlew :app:dependencies

# View time per task
./gradlew :app:assembleDebug --profile
# Report: build/reports/profile/

# View test report
open app/build/reports/tests/testDebugUnitTest/index.html
```

## Related Skills
- Bug diagnosis: `energyflow-diagnose` — if it's a runtime error, not a compilation error
- Run tests: `energyflow-test` — run tests to verify after compilation passes
