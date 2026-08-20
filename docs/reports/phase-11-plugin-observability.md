# المرحلة 11 — ملاحظة صحة الإضافات

أضيفت بطاقة **Plugin health** داخل قسم الأتمتة في شاشة الإعدادات. تعرض عداداً موجزاً للإضافات المتوافقة، والجزئية، وغير المتاحة، ونقرتها تطلب refresh صريحاً.

مصدر البيانات هو `PluginDiscoveryRegistry` singleton عبر `SettingsViewModel`. لا تقرأ البطاقة Bundle أو `bundleJson` أو Vault أو output variables، ولا تنفذ plugin ولا تبدأ polling. عملية refresh وحدها تستدعي PackageManager، بينما invalidation بعد install/update/remove يعاد استخدام `PackageMonitor` القائم.

| الحالة المعروضة | المصدر |
|---|---|
| Ready | `PluginCompatibilityStatus.COMPATIBLE` |
| Partial | `PluginCompatibilityStatus.PARTIALLY_COMPATIBLE` |
| Unavailable | أي حالة مكتشفة أخرى مثل missing receiver/edit activity أو disabled أو permission denied |

تبقى تفاصيل الأخطاء ونتائج كل invocation ضمن المسار المنقح للسجل والـ capability result؛ البطاقة ليست console debug ولا تكشف payload أو بيانات حساسة.
