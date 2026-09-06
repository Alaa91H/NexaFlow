# NexaFlow — Evolution X Integration (cnb / Android 17, API 37)

هذا المجلد يجعل NexaFlow جزءاً من نظام Evolution X نفسه (**system/priv-app**) بدلاً من كونه تطبيق عادي. النتيجة: لا حاجة لـ Shizuku أو Root لكل أمر محمي، وكل إمكانيات الروم (Evolver keys مثل `evo_*` و `lineage_*`) تعمل مباشرة.

## لماذا "متوافق تماماً" مع cnb؟

- **compileSdk / targetSdk = 37** — يطابق Android 17 Cinnamon Bun بالضبط (`ro.evolution.version` + `ro.evolution.buildtype` تكشفها `RomDetectionMatrix`).
- **إتاحة اللغة العربية و 10 لغات** عبر `localeFilters` + `locales_config.xml` (مطابق لـ Evolution X التي تفصل locales).
- **وصول للشبكة المحلية** `ACCESS_LOCAL_NETWORK` (جديد في API 37) مطلوب لفعل HTTP الخاص بـ NexaFlow لأجهزة LAN.
- **محاذاة 16 KB** — كل مكتبات `.so` داخل APK محاذاة على 16 KB (`scripts/check_16kb.py` + استبعاد `sentry-ndk`). شرط أساسي لأجهزة Android 15+ (Evolution X cnb يبنى مع 16KB ELF alignment).
- **اكتشاف الروم** يميز Evolution X عن باقي عائلات Lineage (crDroid, ArrowOS ...) ويعرض `evo_*` / `lineage_*` / `sysui_*` في `EvolutionXSettingsBridge`.

## طريقتان للدمج

### 1) Prebuilt APK (موصى بها — أسهل)

> لا تحتاج لإعادة تجميع الروم كاملاً، فقط إضافة APK الجاهز.

```bash
# 1) ابنِ NexaFlow (يتطلب JDK 17 + Android SDK 37):
./gradlew assembleRelease
# التحقق:
python3 scripts/check_16kb.py app/build/outputs/apk/release/app-release.apk
python3 scripts/check_apk_libs.py app/build/outputs/apk/release/app-release.apk

# 2) انسخ APK إلى حزمة الروم:
cp app/build/outputs/apk/release/app-release.apk rom/prebuilt/NexaFlow.apk

# 3) ضع مجلد rom في شجرة Evolution X بإحدى الطريقتين:
#   a) كـ vendor overlay (أنظف — لا يلمس device tree):
#      vendor/nexaflow/prebuilt/  ← انسخ محتويات rom/ إليه
#   b) كـ package رسمي:
#      packages/apps/NexaFlow/    ← انسخ محتويات rom/ إليه

# 4) فعّل الحزمة من ملف المنتج الخاص بجهازك (مثال whyred / duchamp):
# في device/xiaomi/whyred/device.mk أو evolution_whyred.mk أضف:
#   $(call inherit-product, vendor/nexaflow/prebuilt/NexaFlow.mk)
# أو
#   $(call inherit-product, packages/apps/NexaFlow/rom/NexaFlow.mk)

# 5) ابنِ الروم:
. build/envsetup.sh
lunch lineage_whyred-bp2a-user
m evolution   # أو: m NexaFlow  للاختبار السريع
```

النتيجة بعد التفليش:
```
/system/priv-app/NexaFlow/NexaFlow.apk
/system/etc/permissions/privapp-permissions-com.nexaflow.app.xml
```
عند أول إقلاع: `Settings > Apps > NexaFlow` يظهر كـ System app، و `dumpsys package com.nexaflow.app | grep -A2 privapp` يظهر الصلاحيات الممنوحة.

### 2) Magisk Module (للتجربة بدون إعادة بناء الروم)

على جهازك الحالي المفلّش عليه Evolution X (أي فرع: vic/bka/cnb):

1. افتح NexaFlow > Settings > ROM Integration > **Install as System App (Magisk)**.
2. أو يدوياً عبر ADB (الجهاز متصل و لديك Magisk):
```powershell
adb push rom/prebuilt/NexaFlow.apk /sdcard/
adb shell su -c "magisk --install-module /sdcard/nexaflow_system_integration.zip"
adb reboot
```
يبنيه `SystemAppInstaller` من `core/rom-integration` كـ Magisk module `nexaflow_system_integration` (يحتوي على `system/priv-app/NexaFlow/NexaFlow.apk` + whitelist). بعد Reboot يصبح المستوى `PRIVILEGED_SYSTEM_APP` بدلاً من `SHIZUKU`/`ROOT`.

## التحقق بعد التثبيت

```bash
# هل التطبيق priv-app؟
adb shell dumpsys package com.nexaflow.app | grep flags
# يجب أن ترى: FLAG_SYSTEM | FLAG_PRIVILEGED

# هل الصلاحيات ممنوحة؟
adb shell dumpsys package com.nexaflow.app | grep -A 20 "install permissions:"

# هل NexaFlow يكتشف Evolution X؟
adb shell logcat -s NexaFlowRom
# السطر: RomDetector: family=EVOLUTION_X android=17 (sdk 37) ... level=PRIVILEGED_SYSTEM_APP

# اختبار كتابة مفتاح Evolver (يتطلب priv-app):
adb shell "settings list secure" | grep -i evo_
```

## التوقيع

- **presigned:true (افتراضي):** APK يبقى بتوقيع NexaFlow الأصلي. الـ whitelist يمنح الصلاحيات بدون الحاجة لمفتاح platform. أسهل للتوزيع.
- **platform (اختياري):** في `rom/Android.bp` بدّل إلى `certificate: "platform"` لإعادة توقيعه بمفتاح Evolution X. يرفع المستوى إلى `PLATFORM_SIGNED_SYSTEM_APP` (أعلى مستوى في `SystemAppStatusDetector`).

## إلغاء الدمج

- **ROM baked:** احذف `$(call inherit-product,...)` وأعد بناء الروم.
- **Magisk:** `adb shell su -c "magisk --remove-modules nexaflow_system_integration"` أو من تطبيق Magisk > Modules > Remove، ثم reboot.

## ملاحظات خاصة بـ whyred (حالتك)

مجلد عملك `D:\whyred` يشير لجهاز **Redmi Note 5 Pro (whyred)**. Evolution X الرسمي لـ whyred متوقف عند `vic` (Android 15) في الشجرة الرسمية (128 جهاز نشط لا يشمل whyred في قائمة cnb). لكن حزمة `rom/` هذه تعمل على أي فرع (vic/bka/cnb) لأن compileSdk 37 متوافق نزولاً حتى minSdk 26، والـ whitelist نفسه. إذا كنت تبني whyred غير رسمي على cnb، فقط تأكد أن `device/xiaomi/whyred` مُحدّث لـ API 37 blobs.
