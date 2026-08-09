# ============================================================================
# NexaFlow — R8 rules
# R8 full mode is enabled for release builds. These rules protect the
# reflection and serialization surfaces that R8 cannot infer on its own.
# ============================================================================

# --- Shizuku ---------------------------------------------------------------
# PrivilegedRunner invokes Shizuku.newProcess via getDeclaredMethod (it was
# made private in Shizuku API 13.1.5). The Shizuku provider also performs
# binder IPC with these classes. Keep everything so the reflection and the
# manifest-registered provider keep working after shrinking.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# --- kotlinx.serialization ---------------------------------------------------
# All JSON serialization (Room columns, BackupManager export/import,
# ExecutionRecordMapper) uses kotlinx.serialization. R8 cannot infer the
# generated $$serializer classes and Companion serializer() members; without
# these rules release builds would silently produce corrupt JSON. Keep rules
# follow the official kotlinx.serialization documentation.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.nexaflow.**$$serializer { *; }
-keepclassmembers class com.nexaflow.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexaflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Hilt / Dagger ----------------------------------------------------------
# Dagger's generated factories and Hilt entry points are referenced from the
# manifest and generated code; keep the generated components intact.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-dontwarn dagger.hilt.**

# --- Framework reflection (documented, not R8-sensitive) -------------------
# android.os.SystemProperties, android.net.wifi.WifiManager and
# android.service.quicksettings.TileService are reflected by name, but they
# live in the platform jar (never shrunk), so no rules are needed for them.
