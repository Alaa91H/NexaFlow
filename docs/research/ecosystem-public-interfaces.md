# مصادر عامة — واجهات NexaFlow الموسعة

**تاريخ المراجعة:** 19 أغسطس 2026
**الغرض:** تثبيت ما تسمح به البروتوكولات وAndroid علناً قبل إنشاء adapters أو backends، ومنع الادعاء بأن واجهة خاصة أو broadcast غير موثوق يمكن أن يصبح قدرة منتجية.

## Shizuku وامتيازات ADB/Root

| المعلومة العامة | القرار الهندسي |
|---|---|
| Shizuku يعمل بهوية root أو shell/ADB؛ `getUid()` يفرق بين UID 0 و2000، والقدرات الفعلية تختلف حسب الهوية وإصدار Android | لا يوجد fallback صامت بين Root وShizuku/ADB. كل backend يعلن availability وprivilege المطلوب ويعيد حالة منظمة عند غيابه. |
| Shizuku يوصي بـ `UserService` ويصرح بأن `newProcess` سيزال وأنه نصي وغير موثوق | إزالة مسار reflection `newProcess` الحالي وعدم بناء feature جديدة فوق shell command نصي. يستخدم backend typed UserService مع operation id ومعاملات allowlist. |
| UserService ليس process تطبيق Android صالحاً كاملاً؛ بعض Context APIs لا تعمل فيه | لا يمنح service `Context` أو repository أو engine؛ يستقبل request typed ويعيد response محدوداً فقط. |
| Shizuku يحتاج binder lifecycle وpermission listener، والـ binder قد يموت | backend يراقب binder death/permission ويوزع `SHIZUKU_UNAVAILABLE` أو `SHIZUKU_DENIED` ولا يرمي exception إلى workflow. |

## Locale / Tasker Plugins

| البروتوكول | الحالة والقرار |
|---|---|
| Locale setting/action | scan → edit → persist → explicit `FIRE_SETTING`; هذا المسار مدعوم محلياً ويجب أن يبقى مع Bundle أقل من 25KB وبدون أسرار. |
| Locale condition | `QUERY_CONDITION` ordered broadcast ونتيجة `SATISFIED`/`UNSATISFIED`/`UNKNOWN`. يمكن بناء adapter ثلاثي نتيجة لهذا المسار فقط. |
| Locale condition background | توثيق Locale الحديث يصف conditions بأنها deprecated بسبب قيود background Android وعدم موثوقية التشغيل المستمر. لذلك تعرض كـ PARTIAL/UNAVAILABLE عند عدم وجود query فوري موثوق، لا كـ trigger مستمر. |
| Tasker event extension | event plugin يرسل `REQUEST_QUERY` ثم يقوم المضيف بـ query condition؛ updates خاصة بـ Tasker extension. لا يوجد generic direct event execution في بروتوكول Locale الأساسي. |
| Tasker outputs | outputs في مكتبة Tasker قد تتحول إلى strings/arrays وفق extension؛ لا تفترضها NexaFlow إلا بعد إعلان metadata/extension مثبت لكل plugin. |

## Broadcast Security وlifecycle

| المعلومة العامة | القرار الهندسي |
|---|---|
| Android 14+ يوفر `getSentFromPackage()` و`getSentFromUid()` فقط من API 34 | مصدر plugin event لا يقبل كبroadcast عام موثوق على إصدارات أقدم بلا permission/signature contract. يستخدم event adapter فقط حيث تكون هوية المصدر أو permission قابلة للتحقق. |
| receivers يمكن أن تُقيّد بـ permission؛ Android يوصي `RECEIVER_EXPORTED` فقط لمصادر خارجية وunregister دائماً | كل listener خارجي يملك receiver lifecycle صريحاً، حد تسجيل واحد، permission عند توفره، وإلغاء تسجيل تام. |
| broadcast receivers قصيرة العمر ولا يجب أن تنفذ عملاً طويلاً أو تشغل threads طويلة من `onReceive` | receiver يتحقق ويطبع event محدوداً فقط؛ التنفيذ بعد ذلك يمر EventBus/TriggerIndex ومسار runtime القائم. |
| APIs 26+ تقيد implicit manifest broadcasts | لا يضاف monitor قائم على implicit broadcast محظور؛ تستخدم dynamic receiver محدود أو system service documented. |

## Accessibility

| المعلومة العامة | القرار الهندسي |
|---|---|
| AccessibilityService يفعلها المستخدم من إعدادات النظام فقط ويحتاج `BIND_ACCESSIBILITY_SERVICE` | availability لا تكون success إلا عند اتصال الخدمة صراحة؛ workflow لا يصل مباشرة إلى service. |
| `AccessibilityNodeInfo` قد يصبح stale في أي وقت | backend يحتفظ selectors فقط، يعيد resolve node لكل action، ويعيد `STALE_TARGET`/`UNAVAILABLE` بدل object node في RuntimeValue. |
| الإيماءات تتطلب `canPerformGestures=true` وتملك callback completion | gesture capability محدود بالمدة والنقاط وapp allowlist، وتعتبر completion مختلفة عن verification UI. |
| سياسة Google Play تسمح automation rule-based static، لكنها تمنع autonomous initiation/planning؛ تتطلب disclosure/consent للتطبيقات غير accessibility tools | أي UI automation يبقى workflow معرفاً من المستخدم، ومقيداً بالـ policy، ويتطلب disclosure وموافقة قبل التفعيل. |

## المراجع

[1]: https://github.com/RikkaApps/Shizuku-API "Shizuku API developer guide"
[2]: https://shizuku.rikka.app/guide/setup/ "Shizuku setup guide"
[3]: https://github.com/rikkaapps/shizuku "Shizuku architecture"
[4]: https://tasker.joaoapps.com/plugins-intro.html "Tasker Plugin Introduction"
[5]: https://tasker.joaoapps.com/pluginslibrary.html "Tasker Plugin Library"
[6]: https://www.twofortyfouram.com/developer "Locale Developer API"
[7]: https://github.com/twofortyfouram/android-plugin-api-for-locale "Locale Plugin API"
[8]: https://developer.android.com/develop/background-work/background-tasks/broadcasts "Android broadcasts overview"
[9]: https://developer.android.com/guide/topics/manifest/receiver-element "Android receiver declaration"
[10]: https://developer.android.com/reference/android/content/BroadcastReceiver "BroadcastReceiver API"
[11]: https://developer.android.com/guide/topics/ui/accessibility/service "Create an accessibility service"
[12]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "AccessibilityService API"
[13]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Google Play AccessibilityService policy"
