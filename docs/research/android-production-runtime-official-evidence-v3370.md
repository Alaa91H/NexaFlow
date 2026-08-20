# أدلة Android الرسمية لتصميم Runtime الإنتاجي في NexaFlow v3.37.0

**تاريخ الجمع:** 20 أغسطس 2026  
**الغرض:** تثبيت حدود الجدولة والعمل الخلفي والخدمات الأمامية قبل أي توسعة لمحرك الأتمتة.

## AlarmManager والمهام الزمنية

وثائق Android توضح أن AlarmManager مناسب لإطلاق Intents في أوقات محددة حتى عندما لا يكون التطبيق قيد التشغيل أو يكون الجهاز نائماً، ويمكن ربطه بـBroadcastReceiver ثم Job/WorkRequest للعمل الأطول. كما توصي بتجنب الاعتماد على timers أو خدمات تعمل باستمرار. [1]

توضح الوثائق أن التنبيهات الدقيقة تستهلك موارد وبطارية، وأن استعمالها يجب أن يقتصر على وظيفة ظاهرة للمستخدم تتطلب توقيتاً دقيقاً. يتطلب `SCHEDULE_EXACT_ALARM` وصولاً خاصاً على Android 12+؛ ويجب إعادة جدولة التنبيهات عند تغير حالة الصلاحية. التنبيهات المتكررة غير دقيقة منذ Android 4.4. [1]

**أثر القرار:** يبقى `AutomationScheduler` القائم هو مجدول الوقت الوحيد. لا يضاف scheduler موازٍ. يجب أن تستعمل maintenance window AlarmManager فقط لبدء التقييم، لا لادعاء توقيت دقيق غير مضمون أو تنفيذ طويل داخل receiver.

## WorkManager والقيود وإعادة المحاولة

توثق Android أن WorkManager يحمل معلومات الجدولة والقيود وإعادة المحاولة، ويدعم قيود الشبكة غير المحدودة، الشحن، الخمول، البطارية غير المنخفضة، والتخزين غير المنخفض. عند فقدان قيد أثناء عمل Worker، يوقف WorkManager العمل ويعيد المحاولة حين تتحقق القيود. [2]

العمل الدوري لا يضمن توقيتاً دقيقاً؛ فالنظام قد يؤخر أو يتجاوز تشغيل دورة إذا لم تتحقق القيود. Backoff يمكن أن يكون خطياً أو أُسياً، والحد الأدنى لتأخير backoff هو 10 ثوانٍ. [2]

**أثر القرار:** يستعمل `MaintenanceWorker` القائم فقط للصيانة الدورية غير الدقيقة مثل الاحتفاظ والتحقق، ولا يُستخدم كبديل لجدولة alarm-time للمستخدم. `MaintenanceReadinessEvaluator` وقيود WorkManager يجب أن يبقيا متسقين ولا ينشأ منهما queue ثانية.

## قيود خدمات foreground من الخلفية

تمنع Android 12+ بدء foreground service من الخلفية إلا في استثناءات محددة، وترمي `ForegroundServiceStartNotAllowedException` عند عدم تحققها. توجد استثناءات مثل تفاعل المستخدم أو exact alarm ينجز عملاً طلبه المستخدم أو بعض broadcasts كـboot/timezone. [3]

على Android 14+، الخدمة التي تعتمد صلاحيات while-in-use مثل camera/microphone/location قد تفشل أمنياً إن بدأت من الخلفية، حتى مع بعض الاستثناءات؛ ويحتاج location الخلفي لنوع الخدمة والصلاحية المناسبين. [3]

**أثر القرار:** لا تعتمد توسعة runtime على ForegroundService دائم أو على بدء خدمة موقع/كاميرا من الخلفية. يستمر التطبيق في fallback القائم للمراقبة ويجب تسجيل capability/permission reason صراحةً عندما تمنع المنصة التنفيذ.

## المراجع

[1]: https://developer.android.com/develop/background-work/services/alarms "Schedule alarms — Android Developers"
[2]: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work "Define work requests — Android Developers"
[3]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start "Restrictions on starting a foreground service from the background — Android Developers"

## PackageInstaller والتحديثات

يوثق مرجع `PackageInstaller` أن جلسة التثبيت تستطيع تثبيت أو ترقية APK أو split APK عند توفير الحزم المتطابقة والموقعة، لكن commit قد يتطلب تدخل المستخدم. الإكمال التلقائي مقصور في النص المرجعي على **device owner** و**affiliated profile owner**. [4]

**أثر القرار:** لا يضاف `REQUEST_INSTALL_PACKAGES` ولا مسار تنزيل/تثبيت لتحديثات Google Play ضمن هذه الدفعة. يبقى تحديث Play للهاتف الشخصي غير مدعوم، وتظل حالات المستخدم المطلوبة أو فشل التحقق/المساحة نتائج typed واضحة، لا نجاحاً مزعوماً.

[4]: https://developer.android.com/reference/android/content/pm/PackageInstaller "PackageInstaller — Android Developers"
