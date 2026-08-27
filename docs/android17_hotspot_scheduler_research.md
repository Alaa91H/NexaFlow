# مرجع هندسي: نقطة الاتصال والجدولة في Android 17

**تاريخ المراجعة:** 27 أغسطس 2026  
**المشروع:** NexaFlow

## النتائج المؤكدة

| المجال | النتيجة القابلة للتحقق | أثرها على NexaFlow |
|---|---|---|
| رصد نقطة الاتصال | Android 17 يوفّر `TetheringManager.TetheringEventCallback.onTetheredInterfacesChanged(Set<TetheringInterface>)` ابتداءً من API 36، ويطلب تسجيله `ACCESS_NETWORK_STATE` فقط. يمكن تمييز واجهة Wi‑Fi من `TetheringInterface.getType()` ومقارنتها بـ `TetheringManager.TETHERING_WIFI`. | لا يجوز الاعتماد على المفتاح غير العام `Settings.Global.tether_on` وحده؛ يجب أن يصبح رد النداء الحديث هو المصدر الرئيس للحالة. |
| تشغيل/إيقاف نقطة اتصال الإنترنت | واجهات Soft AP التفصيلية تقيّد بعض الوظائف لتطبيقات النظام، بينما `TetheringManager` الحديث يبلّغ أخطاء صلاحية صريحة عند غياب امتياز التحكم في التقييد. | لا يوجد مسار قانوني لتجاوز صلاحية النظام أو شركة الاتصالات. يجب أن يصرّح التطبيق بدقة بالحاجة إلى Root أو Shizuku أو تطبيق نظام عند طلب تغيير وضع الشبكة الخلوية أو نقطة الاتصال الكاملة. |
| تغيير نوع الشبكة الخلوية | وضع الشبكة إعداد تابع للشريحة ومقيّد بالهاتف/الناقل/النظام. قراءة وإعادة تطبيق القناع تحتاج صلاحيات هاتف مناسبة ومسارًا ذا امتياز؛ لا يكفي طلب `MODIFY_PHONE_STATE` من تطبيق عادي لأن النظام لا يمنحه. | رسالة الصور ليست خطأً في قيمة WCDMA؛ هي دليل أن بيئة الامتياز غير مفعّلة. التطبيق يجب أن يطلب ويعيد التحقق من Root/Shizuku، ولا يدّعي نجاحًا دون قراءة لاحقة. |
| إنذارات 22:00 و06:00 | `setExactAndAllowWhileIdle` يحتاج إذن «المنبّهات والتذكيرات» من Android 12+، والإذن مرفوض افتراضيًا في التثبيتات الجديدة المستهدفة Android 13+ غير المعفاة. عند سحب الإذن تلغى الإنذارات الدقيقة المستقبلية. | من دون الإذن لا يمكن وعد المستخدم بتنفيذ 22:00 أو 06:00 في الوقت المحدد؛ بديل `setAndAllowWhileIdle` قد يتأخر حتى ساعة في Android 12+ ولذلك يجب أن يظهر كحالة متدهورة لا كنجاح كامل. |
| الاستمرارية | الإنذارات تلغى عند إعادة تشغيل الجهاز ويجب إعادة إنشائها عند `BOOT_COMPLETED`. وعند منح إذن الإنذارات الدقيقة يلزم إعادة الجدولة استجابةً للبث المقابل. | التنفيذ الحالي لديه جزء كبير من البنية المطلوبة، لكن يلزم اختبار حقيقي لمسار صلاحية الإنذار وحالة نهاية النطاق، وظهور حالة صريحة للمستخدم عندما تكون الدقة غير مضمونة. |

## المراجع

[1] [Android Developers — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)  
[2] [Android Developers — Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)  
[3] [Android Developers — TetheringManager.TetheringEventCallback](https://developer.android.com/reference/android/net/TetheringManager.TetheringEventCallback)  
[4] [Android Developers — TetheringInterface](https://developer.android.com/reference/android/net/TetheringInterface)  
[5] [AOSP — Wi‑Fi hotspot (Soft AP)](https://source.android.com/docs/core/connect/wifi-softap)  
[6] [Android Developers — Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
