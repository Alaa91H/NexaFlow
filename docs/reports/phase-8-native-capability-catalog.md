# المرحلة 8 — توسعة كتالوج القدرات العامة

## النطاق المنفذ

وسّع هذا التغيير `AndroidIntentCapabilityBackend` بقدرة `SETTINGS_LAUNCH`. وهي لا تقبل intent/action خاماً، بل معامل `page` مقيّداً بقائمة allowlist ثابتة: `WIFI` و`BLUETOOTH` و`LOCATION` و`SOUND` و`DISPLAY` و`BATTERY` و`NOTIFICATIONS`.

| action محفوظ | Capability | backend | نوع النتيجة |
|---|---|---|---|
| `SYSTEM_OPEN_URL` | `INTENT_LAUNCH` | `INTENT` | `PENDING_USER_ACTION` |
| `SYSTEM_OPEN_SETTINGS` | `SETTINGS_LAUNCH` | `INTENT` | `PENDING_USER_ACTION` |
| `PLUGIN_FIRE` بعد تهيئة جديدة | `PLUGIN_ACTION` | `PLUGIN` | structured Locale result |

تجري `CapabilityRequestValidator` مطابقة page مع القيم المسموح بها قبل resolver. ثم يتحقق backend من وجود Android activity يمكنها استقبال intent. تفتح صفحة الإشعارات إعدادات تطبيق NexaFlow نفسها مع `EXTRA_APP_PACKAGE`، ولا تدعي فتح إعدادات إشعارات نظام عامة غير معرفة.

## حدود صريحة

لا يضيف الكتالوج أي Root أو Shizuku أو ADB أو shell أو `SYSTEM_ALERT_WINDOW` أو action قابل للتمرير من config. ما لا يملك backend وvalidation وavailability منتهية لا يدخل descriptor catalog ولا يظهر كدعم إنتاجي.

## التحقق

اجتازت البوابة المستهدفة:

```text
:core:execution:testDebugUnitTest
:app:compileDebugKotlin
```

وتغطي `CapabilityActionMapperTest` تحويل صفحة Wi‑Fi إلى `SETTINGS_LAUNCH`، بينما يفرض descriptor القيم المسموح بها على كل الطلبات الأخرى.
