#!/usr/bin/env python3
"""Adds the v3.63.0 feature strings to all modules and locales.

Modules touched: automation-builder, automations, history, app.
English authoritative; the other 10 locales get real translations.
All values are apostrophe-free (curly ' where needed) so no XML escaping
beyond & -> &amp; is required.
"""
import json
import re

# { module: { locale: { key: text } } }  locale "" = default English
CATALOG = {
    "automation-builder": {
        "": {
            "search_by_name": "Search by name",
            "search_by_package": "Search by package",
            "recently_used_apps": "Recently used in tasks",
            "bt_pick_ok": "Use this device",
            "calendar_pick_ok": "Use this calendar",
            "diagnostics_title": "Configuration problems",
            "diagnostics_empty": "No configuration problems found",
            "diagnostics_intro": "Tasks whose actions failed at runtime because of invalid configuration values",
            "diagnostics_open_task": "Open task",
            "blocked_calls_title": "Blocked calls",
            "blocked_calls_empty": "No blocked calls yet",
            "blocked_calls_filter_all": "All rules",
            "migrate_banner_title": "Update connectivity tasks",
            "migrate_banner_message": "Some tasks still use the old combined connection trigger. Migrate them to the dedicated Wi-Fi and mobile data triggers.",
            "migrate_banner_button": "Migrate now",
            "migrate_done": "Tasks migrated",
            "migrate_none": "Nothing to migrate",
        },
        "values-ar": {
            "search_by_name": "بحث بالاسم",
            "search_by_package": "بحث بالحزمة",
            "recently_used_apps": "المستخدمة حديثًا في المهام",
            "bt_pick_ok": "استخدام هذا الجهاز",
            "calendar_pick_ok": "استخدام هذا التقويم",
            "diagnostics_title": "مشاكل الإعداد",
            "diagnostics_empty": "لا توجد مشاكل إعداد",
            "diagnostics_intro": "مهام فشلت إجراءاتها وقت التنفيذ بسبب قيم إعداد غير صالحة",
            "diagnostics_open_task": "فتح المهمة",
            "blocked_calls_title": "المكالمات المحجوبة",
            "blocked_calls_empty": "لا توجد مكالمات محجوبة بعد",
            "blocked_calls_filter_all": "كل القواعد",
            "migrate_banner_title": "تحديث مهام الاتصال",
            "migrate_banner_message": "بعض المهام ما زالت تستخدم محفز الاتصال المدمج القديم. انقلها إلى محفزات الواي فاي وبيانات الجوال المخصصة.",
            "migrate_banner_button": "الترحيل الآن",
            "migrate_done": "تم ترحيل المهام",
            "migrate_none": "لا شيء للترحيل",
        },
        "values-de": {
            "search_by_name": "Nach Name suchen",
            "search_by_package": "Nach Paket suchen",
            "recently_used_apps": "Zuletzt in Aufgaben verwendet",
            "bt_pick_ok": "Dieses Gerät verwenden",
            "calendar_pick_ok": "Diesen Kalender verwenden",
            "diagnostics_title": "Konfigurationsprobleme",
            "diagnostics_empty": "Keine Konfigurationsprobleme gefunden",
            "diagnostics_intro": "Aufgaben, deren Aktionen wegen ungültiger Konfigurationswerte fehlschlugen",
            "diagnostics_open_task": "Aufgabe öffnen",
            "blocked_calls_title": "Blockierte Anrufe",
            "blocked_calls_empty": "Noch keine blockierten Anrufe",
            "blocked_calls_filter_all": "Alle Regeln",
            "migrate_banner_title": "Konnektivitäts-Aufgaben aktualisieren",
            "migrate_banner_message": "Einige Aufgaben verwenden noch den alten kombinierten Verbindungs-Auslöser. Migriere sie zu den dedizierten WLAN- und Mobilfunk-Auslösern.",
            "migrate_banner_button": "Jetzt migrieren",
            "migrate_done": "Aufgaben migriert",
            "migrate_none": "Nichts zu migrieren",
        },
        "values-es": {
            "search_by_name": "Buscar por nombre",
            "search_by_package": "Buscar por paquete",
            "recently_used_apps": "Usadas recientemente en tareas",
            "bt_pick_ok": "Usar este dispositivo",
            "calendar_pick_ok": "Usar este calendario",
            "diagnostics_title": "Problemas de configuración",
            "diagnostics_empty": "No se encontraron problemas de configuración",
            "diagnostics_intro": "Tareas cuyas acciones fallaron en tiempo de ejecución por valores de configuración no válidos",
            "diagnostics_open_task": "Abrir tarea",
            "blocked_calls_title": "Llamadas bloqueadas",
            "blocked_calls_empty": "Aún no hay llamadas bloqueadas",
            "blocked_calls_filter_all": "Todas las reglas",
            "migrate_banner_title": "Actualizar tareas de conectividad",
            "migrate_banner_message": "Algunas tareas siguen usando el activador de conexión combinado antiguo. Mígralas a los activadores dedicados de Wi-Fi y datos móviles.",
            "migrate_banner_button": "Migrar ahora",
            "migrate_done": "Tareas migradas",
            "migrate_none": "Nada que migrar",
        },
        "values-fr": {
            "search_by_name": "Rechercher par nom",
            "search_by_package": "Rechercher par paquet",
            "recently_used_apps": "Utilisées récemment dans des tâches",
            "bt_pick_ok": "Utiliser cet appareil",
            "calendar_pick_ok": "Utiliser ce calendrier",
            "diagnostics_title": "Problèmes de configuration",
            "diagnostics_empty": "Aucun problème de configuration trouvé",
            "diagnostics_intro": "Tâches dont les actions ont échoué à l\u2019exécution en raison de valeurs de configuration invalides",
            "diagnostics_open_task": "Ouvrir la tâche",
            "blocked_calls_title": "Appels bloqués",
            "blocked_calls_empty": "Aucun appel bloqué pour l\u2019instant",
            "blocked_calls_filter_all": "Toutes les règles",
            "migrate_banner_title": "Mettre à jour les tâches de connectivité",
            "migrate_banner_message": "Des tâches utilisent encore l\u2019ancien déclencheur de connexion combiné. Migrez-les vers les déclencheurs dédiés Wi-Fi et données mobiles.",
            "migrate_banner_button": "Migrer maintenant",
            "migrate_done": "Tâches migrées",
            "migrate_none": "Rien à migrer",
        },
        "values-hi": {
            "search_by_name": "नाम से खोजें",
            "search_by_package": "पैकेज से खोजें",
            "recently_used_apps": "हाल में कार्यों में उपयोग किए गए",
            "bt_pick_ok": "यह डिवाइस उपयोग करें",
            "calendar_pick_ok": "यह कैलेंडर उपयोग करें",
            "diagnostics_title": "कॉन्फ़िगरेशन समस्याएँ",
            "diagnostics_empty": "कोई कॉन्फ़िगरेशन समस्या नहीं मिली",
            "diagnostics_intro": "ऐसे कार्य जिनकी क्रियाएँ अमान्य कॉन्फ़िगरेशन मानों से चलने में विफल रहीं",
            "diagnostics_open_task": "कार्य खोलें",
            "blocked_calls_title": "अवरोधित कॉल",
            "blocked_calls_empty": "अभी कोई अवरोधित कॉल नहीं",
            "blocked_calls_filter_all": "सभी नियम",
            "migrate_banner_title": "कनेक्टिविटी कार्य अपडेट करें",
            "migrate_banner_message": "कुछ कार्य अभी भी पुराने संयुक्त कनेक्शन ट्रिगर का उपयोग करते हैं। उन्हें समर्पित Wi-Fi और मोबाइल डेटा ट्रिगर में बदलें।",
            "migrate_banner_button": "अभी माइग्रेट करें",
            "migrate_done": "कार्य माइग्रेट हुए",
            "migrate_none": "माइग्रेट करने के लिए कुछ नहीं",
        },
        "values-ja": {
            "search_by_name": "名前で検索",
            "search_by_package": "パッケージで検索",
            "recently_used_apps": "最近タスクで使用したアプリ",
            "bt_pick_ok": "このデバイスを使用",
            "calendar_pick_ok": "このカレンダーを使用",
            "diagnostics_title": "設定の問題",
            "diagnostics_empty": "設定の問題は見つかりませんでした",
            "diagnostics_intro": "無効な設定値により実行時にアクションが失敗したタスク",
            "diagnostics_open_task": "タスクを開く",
            "blocked_calls_title": "ブロックした着信",
            "blocked_calls_empty": "ブロックした着信はまだありません",
            "blocked_calls_filter_all": "すべてのルール",
            "migrate_banner_title": "接続タスクを更新",
            "migrate_banner_message": "一部のタスクは旧式の統合接続トリガーを使用しています。Wi-Fi とモバイルデータの専用トリガーに移行します。",
            "migrate_banner_button": "今すぐ移行",
            "migrate_done": "移行が完了しました",
            "migrate_none": "移行するものはありません",
        },
        "values-pt": {
            "search_by_name": "Pesquisar por nome",
            "search_by_package": "Pesquisar por pacote",
            "recently_used_apps": "Usados recentemente em tarefas",
            "bt_pick_ok": "Usar este dispositivo",
            "calendar_pick_ok": "Usar este calendário",
            "diagnostics_title": "Problemas de configuração",
            "diagnostics_empty": "Nenhum problema de configuração encontrado",
            "diagnostics_intro": "Tarefas cujas ações falharam em tempo de execução por valores de configuração inválidos",
            "diagnostics_open_task": "Abrir tarefa",
            "blocked_calls_title": "Chamadas bloqueadas",
            "blocked_calls_empty": "Ainda não há chamadas bloqueadas",
            "blocked_calls_filter_all": "Todas as regras",
            "migrate_banner_title": "Atualizar tarefas de conectividade",
            "migrate_banner_message": "Algumas tarefas ainda usam o antigo gatilho de conexão combinada. Migre-as para os gatilhos dedicados de Wi-Fi e dados móveis.",
            "migrate_banner_button": "Migrar agora",
            "migrate_done": "Tarefas migradas",
            "migrate_none": "Nada a migrar",
        },
        "values-ru": {
            "search_by_name": "Поиск по названию",
            "search_by_package": "Поиск по пакету",
            "recently_used_apps": "Недавно использованные в задачах",
            "bt_pick_ok": "Использовать это устройство",
            "calendar_pick_ok": "Использовать этот календарь",
            "diagnostics_title": "Проблемы конфигурации",
            "diagnostics_empty": "Проблем конфигурации не найдено",
            "diagnostics_intro": "Задачи, чьи действия завершились ошибкой из-за недопустимых значений конфигурации",
            "diagnostics_open_task": "Открыть задачу",
            "blocked_calls_title": "Заблокированные вызовы",
            "blocked_calls_empty": "Заблокированных вызовов пока нет",
            "blocked_calls_filter_all": "Все правила",
            "migrate_banner_title": "Обновить задачи подключения",
            "migrate_banner_message": "Некоторые задачи всё ещё используют старый объединённый триггер подключения. Перенесите их на отдельные триггеры Wi-Fi и мобильных данных.",
            "migrate_banner_button": "Мигрировать сейчас",
            "migrate_done": "Задачи перенесены",
            "migrate_none": "Переносить нечего",
        },
        "values-tr": {
            "search_by_name": "Ada göre ara",
            "search_by_package": "Pakete göre ara",
            "recently_used_apps": "Görevlerde son kullanılanlar",
            "bt_pick_ok": "Bu cihazı kullan",
            "calendar_pick_ok": "Bu takvimi kullan",
            "diagnostics_title": "Yapılandırma sorunları",
            "diagnostics_empty": "Yapılandırma sorunu bulunamadı",
            "diagnostics_intro": "Eylemleri geçersiz yapılandırma değerleri nedeniyle çalışma zamanında başarısız olan görevler",
            "diagnostics_open_task": "Görevi aç",
            "blocked_calls_title": "Engellenen aramalar",
            "blocked_calls_empty": "Henüz engellenen arama yok",
            "blocked_calls_filter_all": "Tüm kurallar",
            "migrate_banner_title": "Bağlantı görevlerini güncelle",
            "migrate_banner_message": "Bazı görevler hâlâ eski birleşik bağlantı tetikleyicisini kullanıyor. Bunları özel Wi-Fi ve mobil veri tetikleyicilerine taşıyın.",
            "migrate_banner_button": "Şimdi taşı",
            "migrate_done": "Görevler taşındı",
            "migrate_none": "Taşınacak bir şey yok",
        },
        "values-zh-rCN": {
            "search_by_name": "按名称搜索",
            "search_by_package": "按软件包搜索",
            "recently_used_apps": "最近在任务中使用",
            "bt_pick_ok": "使用此设备",
            "calendar_pick_ok": "使用此日历",
            "diagnostics_title": "配置问题",
            "diagnostics_empty": "未发现配置问题",
            "diagnostics_intro": "因配置值无效导致操作在运行时失败的任务",
            "diagnostics_open_task": "打开任务",
            "blocked_calls_title": "已拦截的来电",
            "blocked_calls_empty": "暂无已拦截的来电",
            "blocked_calls_filter_all": "所有规则",
            "migrate_banner_title": "更新连接类任务",
            "migrate_banner_message": "部分任务仍在使用旧的合并连接触发器。将它们迁移到独立的 Wi-Fi 和移动数据触发器。",
            "migrate_banner_button": "立即迁移",
            "migrate_done": "迁移完成",
            "migrate_none": "没有需要迁移的任务",
        },
    },
    "dashboard": {
        "": {
            "run_reason_triggers_not_met": "Triggers not met right now",
            "run_reason_trigger": "Trigger not met: %1$s",
            "run_reason_constraint": "Constraint not met: %1$s",
            "run_reason_unknown": "Some triggers could not be verified",
            "run_reason_no_exit": "No end behavior configured",
            "run_force_title": "Force run?",
            "run_force_message": "Force run skips the trigger and condition checks and runs the task now. End behavior still applies.",
            "run_force_confirm": "Force run",
            "run_gate_title": "Why can this task not run now?",
            "run_gate_close": "Close",
            "run_reason_task": "Task: %1$s",
            "run_reason_run_end": "Run end behavior",
            "run_force_short": "Force"
        },
        "values-ar": {
            "run_reason_triggers_not_met": "المحفزات غير متوفرة حاليًا",
            "run_reason_trigger": "المحفز غير متوفر: %1$s",
            "run_reason_constraint": "الشرط غير متوفر: %1$s",
            "run_reason_unknown": "تعذر التحقق من بعض المحفزات",
            "run_reason_no_exit": "لا يوجد سلوك عند الانتهاء",
            "run_force_title": "تشغيل قسري؟",
            "run_force_message": "التشغيل القسري يتجاوز فحص المحفزات والشروط وينفذ المهمة الآن. يبقى سلوك الانتهاء ساريًا.",
            "run_force_confirm": "تشغيل قسري",
            "run_gate_title": "لماذا لا يمكن تشغيل المهمة الآن؟",
            "run_gate_close": "إغلاق",
            "run_reason_task": "المهمة: %1$s",
            "run_reason_run_end": "تنفيذ سلوك الانتهاء",
            "run_force_short": "قسري"
        },
        "values-de": {
            "run_reason_triggers_not_met": "Auslöser derzeit nicht erfüllt",
            "run_reason_trigger": "Auslöser nicht erfüllt: %1$s",
            "run_reason_constraint": "Bedingung nicht erfüllt: %1$s",
            "run_reason_unknown": "Einige Auslöser konnten nicht geprüft werden",
            "run_reason_no_exit": "Kein Endverhalten konfiguriert",
            "run_force_title": "Erzwingen?",
            "run_force_message": "Erzwungenes Ausführen überspringt die Prüfung von Auslösern und Bedingungen und startet die Aufgabe jetzt. Das Endverhalten bleibt wirksam.",
            "run_force_confirm": "Erzwingen",
            "run_gate_title": "Warum kann diese Aufgabe jetzt nicht laufen?",
            "run_gate_close": "Schließen",
            "run_reason_task": "Aufgabe: %1$s",
            "run_reason_run_end": "Endverhalten ausführen",
            "run_force_short": "Erzwingen"
        },
        "values-es": {
            "run_reason_triggers_not_met": "Activadores no cumplidos ahora mismo",
            "run_reason_trigger": "Activador no cumplido: %1$s",
            "run_reason_constraint": "Condición no cumplida: %1$s",
            "run_reason_unknown": "No se pudieron verificar algunos activadores",
            "run_reason_no_exit": "Sin comportamiento al finalizar",
            "run_force_title": "¿Forzar ejecución?",
            "run_force_message": "Forzar ejecución omite la comprobación de activadores y condiciones y ejecuta la tarea ahora. El comportamiento al finalizar sigue aplicándose.",
            "run_force_confirm": "Forzar",
            "run_gate_title": "¿Por qué no puede ejecutarse esta tarea ahora?",
            "run_gate_close": "Cerrar",
            "run_reason_task": "Tarea: %1$s",
            "run_reason_run_end": "Ejecutar comportamiento final",
            "run_force_short": "Forzar"
        },
        "values-fr": {
            "run_reason_triggers_not_met": "Déclencheurs non réunis pour le moment",
            "run_reason_trigger": "Déclencheur non réuni : %1$s",
            "run_reason_constraint": "Condition non remplie : %1$s",
            "run_reason_unknown": "Certains déclencheurs n\u2019ont pas pu être vérifiés",
            "run_reason_no_exit": "Aucun comportement de fin configuré",
            "run_force_title": "Forcer l\u2019exécution ?",
            "run_force_message": "L\u2019exécution forcée ignore la vérification des déclencheurs et des conditions et lance la tâche maintenant. Le comportement de fin reste appliqué.",
            "run_force_confirm": "Forcer",
            "run_gate_title": "Pourquoi cette tâche ne peut-elle pas s\u2019exécuter maintenant ?",
            "run_gate_close": "Fermer",
            "run_reason_task": "Tâche : %1$s",
            "run_reason_run_end": "Exécuter le comportement de fin",
            "run_force_short": "Forcer"
        },
        "values-hi": {
            "run_reason_triggers_not_met": "ट्रिगर अभी पूरे नहीं हुए",
            "run_reason_trigger": "ट्रिगर पूरा नहीं: %1$s",
            "run_reason_constraint": "शर्त पूरी नहीं: %1$s",
            "run_reason_unknown": "कुछ ट्रिगर सत्यापित नहीं हो सके",
            "run_reason_no_exit": "कोई समाप्ति व्यवहार कॉन्फ़िगर नहीं",
            "run_force_title": "बलपूर्वक चलाएँ?",
            "run_force_message": "बलपूर्वक चलाना ट्रिगर और शर्त जाँच छोड़ देता है और कार्य अभी चलाता है। समाप्ति व्यवहार लागू रहेगा।",
            "run_force_confirm": "बलपूर्वक चलाएँ",
            "run_gate_title": "यह कार्य अभी क्यों नहीं चल सकता?",
            "run_gate_close": "बंद करें",
            "run_reason_task": "कार्य: %1$s",
            "run_reason_run_end": "समाप्ति व्यवहार चलाएँ",
            "run_force_short": "बल"
        },
        "values-ja": {
            "run_reason_triggers_not_met": "現在トリガー条件を満たしていません",
            "run_reason_trigger": "トリガー未達成: %1$s",
            "run_reason_constraint": "条件未達成: %1$s",
            "run_reason_unknown": "一部のトリガーを検証できませんでした",
            "run_reason_no_exit": "終了動作が設定されていません",
            "run_force_title": "強制実行しますか？",
            "run_force_message": "強制実行はトリガーと条件のチェックを省略して今すぐタスクを実行します。終了動作は適用されます。",
            "run_force_confirm": "強制実行",
            "run_gate_title": "このタスクが今実行できない理由",
            "run_gate_close": "閉じる",
            "run_reason_task": "タスク: %1$s",
            "run_reason_run_end": "終了動作を実行",
            "run_force_short": "強制"
        },
        "values-pt": {
            "run_reason_triggers_not_met": "Gatilhos não atendidos neste momento",
            "run_reason_trigger": "Gatilho não atendido: %1$s",
            "run_reason_constraint": "Condição não atendida: %1$s",
            "run_reason_unknown": "Alguns gatilhos não puderam ser verificados",
            "run_reason_no_exit": "Nenhum comportamento final configurado",
            "run_force_title": "Forçar execução?",
            "run_force_message": "Forçar execução ignora a verificação de gatilhos e condições e executa a tarefa agora. O comportamento final continua valendo.",
            "run_force_confirm": "Forçar",
            "run_gate_title": "Por que esta tarefa não pode ser executada agora?",
            "run_gate_close": "Fechar",
            "run_reason_task": "Tarefa: %1$s",
            "run_reason_run_end": "Executar comportamento final",
            "run_force_short": "Forçar"
        },
        "values-ru": {
            "run_reason_triggers_not_met": "Триггеры сейчас не выполнены",
            "run_reason_trigger": "Триггер не выполнен: %1$s",
            "run_reason_constraint": "Условие не выполнено: %1$s",
            "run_reason_unknown": "Некоторые триггеры не удалось проверить",
            "run_reason_no_exit": "Поведение завершения не настроено",
            "run_force_title": "Запустить принудительно?",
            "run_force_message": "Принудительный запуск пропускает проверку триггеров и условий и выполняет задачу сейчас. Поведение завершения сохраняется.",
            "run_force_confirm": "Запустить принудительно",
            "run_gate_title": "Почему задача не может выполниться сейчас?",
            "run_gate_close": "Закрыть",
            "run_reason_task": "Задача: %1$s",
            "run_reason_run_end": "Выполнить поведение завершения",
            "run_force_short": "Принудительно"
        },
        "values-tr": {
            "run_reason_triggers_not_met": "Tetikleyiciler şu anda sağlanmıyor",
            "run_reason_trigger": "Tetikleyici sağlanmadı: %1$s",
            "run_reason_constraint": "Koşul sağlanmadı: %1$s",
            "run_reason_unknown": "Bazı tetikleyiciler doğrulanamadı",
            "run_reason_no_exit": "Bitiş davranışı yapılandırılmamış",
            "run_force_title": "Zorla çalıştırılsın mı?",
            "run_force_message": "Zorla çalıştırma tetikleyici ve koşul kontrollerini atlar ve görevi şimdi çalıştırır. Bitiş davranışı geçerli kalır.",
            "run_force_confirm": "Zorla çalıştır",
            "run_gate_title": "Bu görev şu anda neden çalışamıyor?",
            "run_gate_close": "Kapat",
            "run_reason_task": "Görev: %1$s",
            "run_reason_run_end": "Bitiş davranışını çalıştır",
            "run_force_short": "Zorla"
        },
        "values-zh-rCN": {
            "run_reason_triggers_not_met": "触发条件当前未满足",
            "run_reason_trigger": "触发条件未满足：%1$s",
            "run_reason_constraint": "约束未满足：%1$s",
            "run_reason_unknown": "部分触发条件无法验证",
            "run_reason_no_exit": "未配置结束行为",
            "run_force_title": "强制运行？",
            "run_force_message": "强制运行会跳过触发条件和约束检查并立即执行任务。结束行为仍然生效。",
            "run_force_confirm": "强制运行",
            "run_gate_title": "为什么此任务现在无法运行？",
            "run_gate_close": "关闭",
            "run_reason_task": "任务：%1$s",
            "run_reason_run_end": "执行结束行为",
            "run_force_short": "强制"
        }
    }
}

MODULES = {
    "automation-builder": "feature/automation-builder",
    "dashboard": "feature/dashboard",
}

for module, res_dir in MODULES.items():
    strings = CATALOG[module]
    catalog_path = "scripts/i18n/" + {
        "automation-builder": "automation-builder_strings.json",
    }.get(module, "")
    if catalog_path.endswith(".json") and __import__("os").path.exists(catalog_path):
        with open(catalog_path, encoding="utf-8") as f:
            catalog = json.load(f)
        target = catalog[module] if module in catalog else catalog
        for key, values in strings.items():
            entry = target.setdefault(key, {})
            for locale, text in values.items():
                entry[locale] = text
        with open(catalog_path, "w", encoding="utf-8", newline="") as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2)
            f.write("\n")
    for locale, values in strings.items():
        path = (
            res_dir + "/src/main/res/values/strings.xml"
            if locale == ""
            else f"{res_dir}/src/main/res/{locale}/strings.xml"
        )
        with open(path, encoding="utf-8") as f:
            content = f.read()
        for key, text in values.items():
            xml_text = text.replace("&", "&amp;")
            pattern = rf'[ \t]*<string name="{key}">.*?</string>\n'
            entry = f'    <string name="{key}">{xml_text}</string>\n'
            if re.search(pattern, content):
                content = re.sub(pattern, entry, content, count=1)
            else:
                content = content.replace("</resources>", entry + "</resources>")
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(content)

print("done: v3.63.0 strings injected")
