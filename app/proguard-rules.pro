# ══════════════════════════════════════════════════════════
# Energy Flow ProGuard / R8 Rules
# ══════════════════════════════════════════════════════════

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ─────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ── Hilt ─────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.Module class *
-keep @dagger.hilt.InstallIn class *

# ── kotlinx.serialization ────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.energyflow.**$$serializer { *; }
-keepclassmembers class com.example.energyflow.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.energyflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor ─────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── DataStore ─────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Compose ───────────────────────────────────────────────
-keep class androidx.compose.** { *; }

# ── Coroutines ────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── App data classes ──────────────────────────────────────
-keep class com.example.energyflow.data.** { *; }
