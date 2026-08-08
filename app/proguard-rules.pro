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

# --- Gson-serialized models ------------------------------------------------
# BackupManager (export/import), Converters and ExecutionRecordMapper
# serialize these classes with Gson, which reads field names via reflection.
# Obfuscating the field names would silently corrupt backups and the Room
# JSON columns, so keep the full class structure.
-keep class com.nexaflow.domain.models.** { *; }
-keep class com.nexaflow.data.backup.** { *; }
-keep class com.nexaflow.data.mapper.** { *; }

# Attributes Gson relies on for generics (TypeToken<List<Trigger>>).
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod

# --- Enums -----------------------------------------------------------------
# Gson serializes enum constants by name (Enum.name()/valueOf via reflection).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
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
