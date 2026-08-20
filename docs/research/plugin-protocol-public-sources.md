# مصادر عامة — بروتوكولات Locale وTasker للإضافات

**تاريخ المراجعة:** 2026-08-19
**الغرض:** ضبط طبقة توافق NexaFlow على البروتوكولات العامة فقط، بلا APIs خاصة أو هندسة عكسية أو افتراض أن كل Plugin يملك الامتدادات نفسها.

## النتائج المعتمدة

| المصدر | النتيجة العملية لنكسا فلو |
|---|---|
| Tasker Plugin Introduction | البروتوكول يقدم ثلاثة أنواع: action/setting وcondition/state وevent. يعتمد action/condition الأساسي على Activity للإعداد وBroadcastReceiver للاستدعاء، بينما event امتداد Tasker فوق نموذج condition/query. |
| Tasker Plugin Extensions | امتدادات Tasker تضيف completion status وtimeout وoutput/local variables للـ action، وcondition variables، وmessage identifiers وevent payload؛ ينبغي اكتشافها per-plugin لا افتراضها. |
| Locale Developer Platform | المسار الأساسي هو `EDIT_SETTING` ثم نتيجة Activity حاوية `EXTRA_BUNDLE` وblurb، ثم `FIRE_SETTING` explicit broadcast. حجم Bundle أقل من 25KB ولا تحمل credentials. مستقبلات broadcast يجب أن تنتهي بسرعة، والعمليات الأطول تستخدم service داخل plugin. |
| Locale SDK repository | يوضح actions العامة `EDIT_SETTING`/`EDIT_CONDITION` و`FIRE_SETTING`/`QUERY_CONDITION` وحالات condition الثلاث: SATISFIED وUNSATISFIED وUNKNOWN، مع ارتباط ثابت بين package/type/edit-activity لتجنب orphaned configuration. المستودع مؤرشف لصالح monorepo؛ لا يعتمد NexaFlow على artifact قديم من jCenter. |

## قرارات تصميم ملزمة

1. يدعم NexaFlow **Locale setting/action** كالبروتوكول الأساسي الموثق عبر discovery للـ edit activity والـ fire receiver، ويدعم condition/query ضمن حالة ثلاثية صريحة.
2. لا يعلن event plugin compatibility كقدرة عامة إلا عند اكتشاف extension Tasker/matching metadata بصورة قابلة للتحقق؛ event لا يسمح له بتشغيل workflow مباشرة، بل ينتج `NexaFlowEvent` محدود الحجم ومُزال الأسرار.
3. لا تمر قيم Android `Bundle` أو objects إلى runtime الداخلي. يستخدم الجسر primitives/Lists/Maps فقط ثم `RuntimeValue`; المخرجات غير الصالحة تصبح result منظماً، لا نجاحاً ضمنياً.
4. لا تمنح الإضافة secrets أو root أو Shizuku أو repositories أو arbitrary context. config المحفوظ لا يحتوي secret plaintext؛ يستخدم reference opaque أو تبقى credential داخل sandbox الإضافة نفسها.
5. يطبق host timeout/cancellation/duplicate correlation في adapter/backend، مع ملاحظة أن بروتوكولات receivers قد تتطلب execution سريعاً ولا تضمن cancellation حقيقياً.
6. لا يستخدم NexaFlow SDK العميل المؤرشف كاعتماد إنتاجي؛ يطبق الحد الأدنى من constants/Intents العامة، ويوثق كل compatibility extension منفصلاً.

## المراجع

[1]: https://tasker.joaoapps.com/plugins-intro.html "Tasker Plugin Introduction"
[2]: https://tasker.joaoapps.com/plugins.html "Tasker Plugin Extensions"
[3]: https://www.twofortyfouram.com/developer "Locale Developer Platform"
[4]: https://github.com/twofortyfouram/android-plugin-client-sdk-for-locale "Locale client SDK repository"

## تفاصيل Host SDK ومواصفة API الملزمة

تؤكد مواصفة API العامة أن المضيف يمر بمراحل **scan ثم edit ثم persist ثم execute**. معرف plugin المحفوظ يجب أن يبنى من type وpackage وedit-activity class، بينما receiver يعاد حله من discovery عند التنفيذ وليس معرفاً دائماً. يجب على المضيف رفض plugin لا يملك activity وreceiver متوافقين من النوع نفسه، أو يملك عدة receivers مبهمة لنفس action، أو لم تكن components/app exported وenabled وقابلة لتلقي Intent من المضيف.

| بند المواصفة | تطبيق NexaFlow المخطط |
|---|---|
| Setting | pairing بين `EDIT_SETTING` activity وreceiver واحد لـ `FIRE_SETTING`، ثم explicit ordered invocation |
| Condition | pairing بين `EDIT_CONDITION` وreceiver واحد لـ `QUERY_CONDITION`، ثم نتيجة SATISFIED/UNSATISFIED/UNKNOWN صريحة |
| Instance persistence | يخزن type/package/editActivity/config bundle المنقى/blurb/protocol metadata؛ receiver يعاد اكتشافه |
| Activity result | لا يحفظ instance إلا مع `RESULT_OK` وblurb وBundle صالح محدود الحجم |
| Broadcast correctness | `FIRE_SETTING` و`QUERY_CONDITION` explicit ويستعملان `FLAG_INCLUDE_STOPPED_PACKAGES`؛ لا تضاف extras خاصة مطلوبة للتوافق الأساسي |
| Bundle | أقل من 25KB، بلا Parcelable أو custom classes أو credential plaintext |
| Host validation | يتحقق من exports/enabled/permission/component pairing والتباس receivers قبل قبول plugin |
| Plugin identity | يحفظ package + edit Activity + type، لا receiver وحده، لتفادي انقطاع configs عند تغيير receiver |

SDK المضيف التاريخي مؤرشف ويشير إلى monorepo؛ لذلك لا يضاف dependency من jCenter. يستخدم NexaFlow constants والسلوكيات العامة الموثقة فقط وينفذ adapter محلياً قابلاً للاختبار.

[5]: https://github.com/twofortyfouram/android-plugin-host-sdk-for-locale "Locale host SDK repository"
[6]: https://github.com/twofortyfouram/android-plugin-api-for-locale "Locale plugin API specification repository"

## قيود Android على اكتشاف الإضافات

توضح وثائق Android أن نتائج `PackageManager` لعمليات مثل `queryIntentActivities()` و`queryBroadcastReceivers()` تخضع لفلترة **package visibility** في Android 11 وما بعده. لذلك يبني NexaFlow discovery على intent filters العامة المطلوبة فعلياً فقط، ويخزن النتيجة، ويعيد الفحص عند package lifecycle أو refresh صريح. لا يستخدم `QUERY_ALL_PACKAGES` كخيار افتراضي لمجرد convenience؛ إن بقي موجوداً في التطبيق، يجب مراجعته مقابل سياسة التوزيع وأقل مدى رؤية ممكن.

| القيد | قرار التنفيذ |
|---|---|
| visibility مفلترة | query موجه لكل action protocol مع manifest `<queries>` مناسب عند الحاجة |
| تشغيل discovery | startup عند الحاجة، package add/update/remove، أو refresh يدوي فقط |
| لا full scan دوري | cache bounded مع invalidation بالأحداث |
| component resolution | يحصل من PackageManager عند discovery ويعاد التحقق قبل invocation |

[7]: https://developer.android.com/training/package-visibility "Android package visibility filtering"
[8]: https://developer.android.com/training/package-visibility/declaring "Declaring package visibility needs"
[9]: https://developer.android.com/guide/components/intents-filters "Android intents and intent filters"
