# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   https://developer.android.com/studio/build/shrink-code

# --- osmdroid (OpenStreetMap picker) ---------------------------------------
# osmdroid loads tile sources, overlays and repositories through reflection
# and keeps class metadata in its Configuration; R8 would strip the lookup
# paths. These rules mirror the ones osmdroid ships for shrinking builds.
-keep class org.osmdroid.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.slf4j.**

