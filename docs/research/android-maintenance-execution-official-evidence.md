# أدلة Android الرسمية لتنفيذ صيانة NexaFlow

**تاريخ الجمع:** 20 أغسطس 2026

> هذه الأدلة لا تفرض إنشاء مجدول جديد. الغرض منها هو اختيار استخدام صحيح للمجدول والمراقبات وWorkManager الموجودة في التطبيق، ومنع polling أو ادعاء توقيت لا تضمنه Android.

## النتائج الحاكمة

| الموضوع | الدليل الرسمي | أثره على NexaFlow |
|---|---|---|
| قيود العمل | يتيح WorkManager قيود الشبكة والشحن والبطارية/idle وإعادة المحاولة وbackoff؛ لا يبدأ العمل المقيد قبل تحقق جميع القيود، وقد يتوقف ويعاد عند فقدانها.[1] | صلاحية جيدة لعمل صيانة مؤجل محدود وغير دقيق، لكن لا يصح جعله بديلاً عن time trigger الدقيقة أو محرك تنفيذ موازٍ. |
| العمل الدوري | الفترة في `PeriodicWorkRequest` هي حد أدنى، والوقت الدقيق يخضع للقيود وتحسينات النظام وقد تؤجل أو تتجاوز دورة إذا لم تتحقق القيود.[1] | Daily/weekly/monthly wall-clock في NexaFlow تبقى على `AutomationScheduler` وحاسب الوقت الحالي؛ تستخدم نافذة الصيانة قرار قبول/إعادة تقييم لا ضمان موعد WorkManager. |
| Doze | أثناء Doze يؤجل Android الشبكة وCPU-intensive work وjobs/syncs والمنبهات العادية إلى maintenance windows؛ منبهات `setAndAllowWhileIdle`/`setExactAndAllowWhileIdle` هي الاستثناءات المناسبة للحالات الحرجة.[2] | لا polling نشط ولا وعد بتنفيذ فوري عند 03:00 تحت كل OEM/Doze. يستعمل التطبيق المنبه القائم بنطاق wake lock محدود، ويسجل waiting/reschedule عند نقص المورد بدلاً من failure. |
| اختبارات Doze | توصي Android باختبار جهاز حقيقي مع `adb shell dumpsys deviceidle force-idle` ثم `unforce` وملاحظة التعافي.[2] | هذا بروتوكول قبول جهاز فعلي، ولا يرفع اختبار الوحدة أو CI وحده إلى REAL_DEVICE_VERIFIED. |
| حالة الجهاز | يوفر `PowerManager` طرقاً لـ`isDeviceIdleMode` و`isInteractive` و`isPowerSaveMode` وحالة thermal، كما يرسل `ACTION_DEVICE_IDLE_MODE_CHANGED` للمستقبلات المسجلة.[3] | يمكن توسيع `ConstraintStateReader` القائم بمصادر رسمية للـidle/interactive/power saver/thermal، مع حراسة API لحالة thermal (29+)، ومن دون Context Engine مستقل. |
| حرارة الجهاز | `THERMAL_STATUS_SEVERE` وما فوق تمثل throttling مؤثراً؛ تذكر واجهة PowerManager status/headroom/listeners.[3] | لا تنفذ أعمال صيانة ثقيلة عند severe/critical؛ تسجل WAITING_FOR_CONDITIONS وتستأنف عند event أو المنبه التالي وفق السياسة. |

## حدود تصميم ملزمة

يجب ألا يطلب NexaFlow إعفاءً من تحسين البطارية تلقائياً لمجرد الصيانة. توضح Android أن الإعفاء محدود الاستخدام ويجب تبريره؛ كما أن تطبيقات الأتمتة قد تكون حالة مقبولة فقط إذا كانت Doze/App Standby تكسر وظيفتها الأساسية.[2] يظل الاستخدام الافتراضي موفراً للطاقة: المنبهات الحالية، المراقبات الحدثية القائمة، وتقييم قصير للحالة قبل التنفيذ.

لا يجب اعتبار الشحن أو Wi-Fi أو idle أو شرط التخزين «نجاحاً» قبل قياسه. عند عدم تحققه، النتيجة الصحيحة هي انتظار/تأجيل persisted قابل للتدقيق، لا تنفيذ جزئي ولا فشل نهائي.

## المراجع

[1]: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work "Android Developers — Define WorkRequest"
[2]: https://developer.android.com/training/monitoring-device-state/doze-standby "Android Developers — Optimize for Doze and App Standby"
[3]: https://developer.android.com/reference/android/os/PowerManager "Android Developers — PowerManager"

## التخزين والحزم: حدود أمنية وسياسية

| المجال | الدليل الرسمي | قرار التصميم |
|---|---|---|
| Scoped storage | لا يستطيع التطبيق على Android 11+ الوصول إلى private data أو app-specific directories لتطبيقات أخرى؛ يقتصر scoped storage افتراضياً على مساحة التطبيق وما يمنحه النظام.[4] | لا يجوز تنفيذ «تنظيف ملفات المستخدم» أو «تنظيف بيانات التطبيقات» عشوائياً. صيانة التخزين تبدأ بالتحليل والاختيار الصريح ومجلدات يوافق عليها المستخدم فقط. |
| تحرير مساحة الجهاز | توفر Android `ACTION_MANAGE_STORAGE` لفحص/إدارة المساحة و`ACTION_CLEAR_APP_CACHE` لطلب موافقة المستخدم قبل تنظيف caches.[4] | لا تستخدم NexaFlow حذف cache كل التطبيقات بصمت. يستخدم Intent النظامي مع موافقة المستخدم أو ينفذ cleanup داخل مساحة التطبيق فقط. |
| All-files access | `MANAGE_EXTERNAL_STORAGE` وصول خاص يطلبه المستخدم وتقيّمه سياسة Google Play، ولا يفتح private dirs للتطبيقات الأخرى.[5] | لا يضاف التصريح افتراضياً. لا يصبح مناسباً إلا إذا كانت إدارة/نسخ الملفات جوهر المنتج وتوفرت مراجعة سياسة منفصلة. |
| التثبيت | تقيّد Google Play `REQUEST_INSTALL_PACKAGES`: يلزم أن يكون إرسال/استقبال الحزم والتثبيت الذي يبدأه المستخدم جزءاً أساسياً من التطبيق، مع تصريح Play Console عند انطباقه.[6] | لا يضاف مسار silent install أو تنزيل APK عام لصيانة التطبيقات. يظل محرك Google Play capability-aware ويرفض بأمان عند غياب مصدر/سياسة إدارة مثبتة. |

### المراجع الإضافية

[4]: https://developer.android.com/about/versions/11/privacy/storage "Android Developers — Storage updates in Android 11"
[5]: https://developer.android.com/training/data-storage/manage-all-files "Android Developers — Manage all files on a storage device"
[6]: https://support.google.com/googleplay/android-developer/answer/12085295?hl=en "Google Play policy — REQUEST_INSTALL_PACKAGES"
