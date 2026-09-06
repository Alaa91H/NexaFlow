# NexaFlow — Evolution X product makefile (cnb / Android 17, API 37)
# Include from your device.mk / evolution_<codename>.mk / common_full_phone.mk:
#
#   $(call inherit-product, packages/apps/NexaFlow/rom/NexaFlow.mk)
# or
#   $(call inherit-product, vendor/nexaflow/prebuilt/NexaFlow.mk)
#
# This installs:
#   - NexaFlow as a privileged system app (/system/priv-app/NexaFlow/NexaFlow.apk)
#   - The privapp-permissions whitelist to grant privileged permissions on boot
#
# Verified on: Evolution X cnb (Android 17, API 37, Cinnamon Bun)

PRODUCT_PACKAGES += NexaFlow
PRODUCT_PACKAGES += privapp-permissions-com.nexaflow.app.xml

# Optional: ensure NexaFlow survives OTA and is not removed by GMS overlay
# PRODUCT_PROPERTY_OVERRIDES += ro.nexaflow.rom_integration=true

# Optional 16 KB page-size footer check (Android 15+ requirement):
# The APK built by this repo is already 16 KB aligned (no native .so with
# unaligned PT_LOAD, sentry-ndk excluded). Keep this comment for auditing.

# Sepolicy (if you bake NexaFlow's priv-app without Magisk):
# No custom sepolicy is required — NexaFlow only uses standard privileged
# permissions (WRITE_SECURE_SETTINGS, STATUS_BAR, etc.) and binder services
# already allowed for priv-apps. If your device tree strips priv-app
# permissions, add an explicit allow in vendor/<oem>/sepolicy.
