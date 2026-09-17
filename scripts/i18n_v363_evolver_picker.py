#!/usr/bin/env python3
"""Localizes the Evolver setting picker and its 12 catalog categories.

Adds to the builder module: picker literals (title, subtitles, search,
loading, empty) and 12 category name/description pairs that were previously
hard-coded English in EvolverCatalog.Category and the picker dialog. All 11
locales receive real translations. Values avoid bare apostrophes/ampersands
so no XML escaping is needed.
"""
import json
import re

L = {
    "evolver_picker_title": {
        "": "System Settings",
        "values-ar": "إعدادات النظام",
        "values-de": "Systemeinstellungen",
        "values-es": "Ajustes del sistema",
        "values-fr": "Paramètres système",
        "values-hi": "सिस्टम सेटिंग्स",
        "values-ja": "システム設定",
        "values-pt": "Configurações do sistema",
        "values-ru": "Настройки системы",
        "values-tr": "Sistem ayarları",
        "values-zh-rCN": "系统设置",
    },
    "evolver_picker_subtitle_evo": {
        "": "Evolution X • %1$d keys • works on any ROM",
        "values-ar": "Evolution X • %1$d مفتاحاً • يعمل على أي نظام",
        "values-de": "Evolution X • %1$d Schlüssel • funktioniert auf jedem ROM",
        "values-es": "Evolution X • %1$d claves • funciona en cualquier ROM",
        "values-fr": "Evolution X • %1$d clés • fonctionne sur toute ROM",
        "values-hi": "Evolution X • %1$d कुंजियाँ • किसी भी ROM पर काम करता है",
        "values-ja": "Evolution X • %1$d個のキー • どのROMでも動作",
        "values-pt": "Evolution X • %1$d chaves • funciona em qualquer ROM",
        "values-ru": "Evolution X • ключей: %1$d • работает на любой ROM",
        "values-tr": "Evolution X • %1$d anahtar • her ROMda çalışır",
        "values-zh-rCN": "Evolution X • %1$d 个键 • 适用于任何 ROM",
    },
    "evolver_picker_subtitle_any": {
        "": "System settings • %1$d keys • works on any ROM",
        "values-ar": "إعدادات النظام • %1$d مفتاحاً • يعمل على أي نظام",
        "values-de": "Systemeinstellungen • %1$d Schlüssel • funktioniert auf jedem ROM",
        "values-es": "Ajustes del sistema • %1$d claves • funciona en cualquier ROM",
        "values-fr": "Paramètres système • %1$d clés • fonctionne sur toute ROM",
        "values-hi": "सिस्टम सेटिंग्स • %1$d कुंजियाँ • किसी भी ROM पर काम करता है",
        "values-ja": "システム設定 • %1$d個のキー • どのROMでも動作",
        "values-pt": "Configurações do sistema • %1$d chaves • funciona em qualquer ROM",
        "values-ru": "Настройки системы • ключей: %1$d • работает на любой ROM",
        "values-tr": "Sistem ayarları • %1$d anahtar • her ROMda çalışır",
        "values-zh-rCN": "系统设置 • %1$d 个键 • 适用于任何 ROM",
    },
    "evolver_picker_search": {
        "": "Search QS, status bar, lockscreen…",
        "values-ar": "ابحث في الإعدادات السريعة وشريط الحالة وشاشة القفل…",
        "values-de": "Schnelleinstellungen, Statusleiste, Sperrbildschirm durchsuchen…",
        "values-es": "Buscar en ajustes rápidos, barra de estado, pantalla de bloqueo…",
        "values-fr": "Rechercher réglages rapides, barre d état, écran verrouillage…",
        "values-hi": "क्विक सेटिंग्स, स्टेटस बार, लॉकस्क्रीन खोजें…",
        "values-ja": "クイック設定、ステータスバー、ロック画面を検索…",
        "values-pt": "Pesquisar ajustes rápidos, barra de status, tela de bloqueio…",
        "values-ru": "Поиск: быстрые настройки, строка состояния, экран блокировки…",
        "values-tr": "Hızlı ayarlar, durum çubuğu, kilit ekranı ara…",
        "values-zh-rCN": "搜索快速设置、状态栏、锁屏…",
    },
    "evolver_picker_loading": {
        "": "Loading settings keys…",
        "values-ar": "جارٍ تحميل مفاتيح الإعدادات…",
        "values-de": "Einstellungsschlüssel werden geladen…",
        "values-es": "Cargando claves de ajustes…",
        "values-fr": "Chargement des clés de paramètres…",
        "values-hi": "सेटिंग कुंजियाँ लोड हो रही हैं…",
        "values-ja": "設定キーを読み込み中…",
        "values-pt": "Carregando chaves de configurações…",
        "values-ru": "Загрузка ключей настроек…",
        "values-tr": "Ayar anahtarları yükleniyor…",
        "values-zh-rCN": "正在加载设置键…",
    },
    "evolver_picker_empty": {
        "": "No settings keys match the search",
        "values-ar": "لا توجد مفاتيح تطابق البحث",
        "values-de": "Keine Einstellungsschlüssel passen zur Suche",
        "values-es": "Ninguna clave coincide con la búsqueda",
        "values-fr": "Aucune clé ne correspond à la recherche",
        "values-hi": "कोई कुंजी खोज से मेल नहीं खाती",
        "values-ja": "検索に一致する設定キーがありません",
        "values-pt": "Nenhuma chave corresponde à pesquisa",
        "values-ru": "Ключи, соответствующие поиску, не найдены",
        "values-tr": "Aramayla eşleşen ayar anahtarı yok",
        "values-zh-rCN": "没有与搜索匹配的设置键",
    },
    "evolver_cat_qs": {"": "Quick Settings", "values-ar": "الإعدادات السريعة", "values-de": "Schnelleinstellungen", "values-es": "Ajustes rápidos", "values-fr": "Réglages rapides", "values-hi": "क्विक सेटिंग्स", "values-ja": "クイック設定", "values-pt": "Ajustes rápidos", "values-ru": "Быстрые настройки", "values-tr": "Hızlı ayarlar", "values-zh-rCN": "快速设置"},
    "evolver_cat_qs_desc": {"": "QS tiles, panel, brightness slider, footer", "values-ar": "بلاطات الإعدادات السريعة واللوحة ومنزلق السطوع والتذييل", "values-de": "QS-Kacheln, Panel, Helligkeitsregler, Fußzeile", "values-es": "Botones rápidos, panel, control de brillo, pie", "values-fr": "Tuiles rapides, panneau, curseur de luminosité, pied", "values-hi": "क्विक टाइल्स, पैनल, ब्राइटनेस स्लाइडर, फुटर", "values-ja": "クイック設定タイル、パネル、明るさスライダー、フッター", "values-pt": "Botões rápidos, painel, controle de brilho, rodapé", "values-ru": "Плитки QS, панель, ползунок яркости, подвал", "values-tr": "Hızlı ayar kutuları, panel, parlaklık kaydırıcısı, alt bilgi", "values-zh-rCN": "快速设置磁贴、面板、亮度滑块、页脚"},
    "evolver_cat_status": {"": "Status Bar", "values-ar": "شريط الحالة", "values-de": "Statusleiste", "values-es": "Barra de estado", "values-fr": "Barre d état", "values-hi": "स्टेटस बार", "values-ja": "ステータスバー", "values-pt": "Barra de status", "values-ru": "Строка состояния", "values-tr": "Durum çubuğu", "values-zh-rCN": "状态栏"},
    "evolver_cat_status_desc": {"": "Clock, battery, icons, network indicators", "values-ar": "الساعة والبطارية والأيقونات ومؤشرات الشبكة", "values-de": "Uhr, Akku, Symbole, Netzwerkanzeigen", "values-es": "Reloj, batería, iconos, indicadores de red", "values-fr": "Horloge, batterie, icônes, indicateurs réseau", "values-hi": "घड़ी, बैटरी, आइकन, नेटवर्क संकेतक", "values-ja": "時計、バッテリー、アイコン、ネットワーク表示", "values-pt": "Relógio, bateria, ícones, indicadores de rede", "values-ru": "Часы, батарея, значки, сетевые индикаторы", "values-tr": "Saat, pil, simgeler, ağ göstergeleri", "values-zh-rCN": "时钟、电池、图标、网络指示"},
    "evolver_cat_lock": {"": "Lockscreen", "values-ar": "شاشة القفل", "values-de": "Sperrbildschirm", "values-es": "Pantalla de bloqueo", "values-fr": "Écran de verrouillage", "values-hi": "लॉकस्क्रीन", "values-ja": "ロック画面", "values-pt": "Tela de bloqueio", "values-ru": "Экран блокировки", "values-tr": "Kilit ekranı", "values-zh-rCN": "锁屏"},
    "evolver_cat_lock_desc": {"": "Clock, shortcuts, weather, media art, fingerprint", "values-ar": "الساعة والاختصارات والطقس ووسائط الوسائط وبصمة الإصبع", "values-de": "Uhr, Verknüpfungen, Wetter, Medien, Fingerabdruck", "values-es": "Reloj, accesos, clima, arte de medios, huella", "values-fr": "Horloge, raccourcis, météo, média, empreinte", "values-hi": "घड़ी, शॉर्टकट, मौसम, मीडिया, फिंगरप्रिंट", "values-ja": "時計、ショートカット、天気、メディア、指紋", "values-pt": "Relógio, atalhos, clima, arte de mídia, impressão digital", "values-ru": "Часы, ярлыки, погода, медиа, отпечаток", "values-tr": "Saat, kısayollar, hava durumu, medya, parmak izi", "values-zh-rCN": "时钟、快捷方式、天气、媒体封面、指纹"},
    "evolver_cat_notif": {"": "Notifications", "values-ar": "الإشعارات", "values-de": "Benachrichtigungen", "values-es": "Notificaciones", "values-fr": "Notifications", "values-hi": "सूचनाएँ", "values-ja": "通知", "values-pt": "Notificações", "values-ru": "Уведомления", "values-tr": "Bildirimler", "values-zh-rCN": "通知"},
    "evolver_cat_notif_desc": {"": "Heads-up, vibration, ticker behavior", "values-ar": "الإشعار المنبثق والاهتزاز وسلوك الشريط", "values-de": "Heads-up, Vibration, Ticker-Verhalten", "values-es": "Aviso emergente, vibración, comportamiento del ticker", "values-fr": "Bannière, vibration, comportement du ticker", "values-hi": "हेड्स-अप, वाइब्रेशन, टिकर व्यवहार", "values-ja": "ポップアップ、振動、ティッカー動作", "values-pt": "Aviso flutuante, vibração, comportamento do ticker", "values-ru": "Всплывающие, вибрация, поведение тикера", "values-tr": "Kayan bildirim, titreşim, kayan yazı davranışı", "values-zh-rCN": "悬浮通知、振动、滚动文字行为"},
    "evolver_cat_nav": {"": "Navigation", "values-ar": "التنقل", "values-de": "Navigation", "values-es": "Navegación", "values-fr": "Navigation", "values-hi": "नेविगेशन", "values-ja": "ナビゲーション", "values-pt": "Navegação", "values-ru": "Навигация", "values-tr": "Gezinme", "values-zh-rCN": "导航"},
    "evolver_cat_nav_desc": {"": "Gestures, 3-button, navbar, swipe actions", "values-ar": "الإيماءات والأزرار الثلاثة وشريط التنقل وإجراءات السحب", "values-de": "Gesten, 3-Tasten, Navigationsleiste, Wischaktionen", "values-es": "Gestos, 3 botones, barra de navegación, gestos de deslizamiento", "values-fr": "Gestes, 3 boutons, barre de navigation, actions de balayage", "values-hi": "जेस्चर, 3-बटन, नेविगेशन बार, स्वाइप क्रियाएँ", "values-ja": "ジェスチャー、3ボタン、ナビゲーションバー、スワイプ操作", "values-pt": "Gestos, 3 botões, barra de navegação, ações de deslize", "values-ru": "Жесты, 3 кнопки, панель навигации, действия свайпа", "values-tr": "Jestler, 3 düğme, gezinme çubuğu, kaydırma eylemleri", "values-zh-rCN": "手势、三键、导航栏、滑动操作"},
    "evolver_cat_theming": {"": "Theming", "values-ar": "السمات", "values-de": "Design", "values-es": "Temas", "values-fr": "Thèmes", "values-hi": "थीमिंग", "values-ja": "テーマ", "values-pt": "Temas", "values-ru": "Темы", "values-tr": "Tema", "values-zh-rCN": "主题"},
    "evolver_cat_theming_desc": {"": "Accent, themed icons, fonts, shapes", "values-ar": "لون التمييز والأيقونات المُسمّاة والخطوط والأشكال", "values-de": "Akzent, thematisierte Symbole, Schriftarten, Formen", "values-es": "Acento, iconos temáticos, fuentes, formas", "values-fr": "Accent, icônes thématisées, polices, formes", "values-hi": "एक्सेंट, थीम्ड आइकन, फ़ॉन्ट, आकृतियाँ", "values-ja": "アクセント、テーマアイコン、フォント、形状", "values-pt": "Cor de destaque, ícones temáticos, fontes, formas", "values-ru": "Акцент, тематические значки, шрифты, формы", "values-tr": "Vurgu, temalı simgeler, yazı tipleri, şekiller", "values-zh-rCN": "强调色、主题图标、字体、形状"},
    "evolver_cat_aod": {"": "Ambient and AOD", "values-ar": "الشاشة المحيطة ودائمة العرض", "values-de": "Ambient und AOD", "values-es": "Ambiente y AOD", "values-fr": "Ambient et AOD", "values-hi": "एम्बिएंट और AOD", "values-ja": "アンビエントとAOD", "values-pt": "Ambiente e AOD", "values-ru": "Ambient и AOD", "values-tr": "Ortam ve AOD", "values-zh-rCN": "环境显示与AOD"},
    "evolver_cat_aod_desc": {"": "Always-on display, ambient ticker, pulse", "values-ar": "الشاشة دائمة العرض والشريط المحيط والنبض", "values-de": "Always-on-Display, Ambient-Ticker, Puls", "values-es": "Pantalla siempre activa, ticker ambiente, pulso", "values-fr": "Écran permanent, ticker ambient, pouls", "values-hi": "अलवेज-ऑन डिस्प्ले, एम्बिएंट टिकर, पल्स", "values-ja": "常時表示ディスプレイ、アンビエントティッカー、パルス", "values-pt": "Tela sempre ativa, ticker ambiente, pulso", "values-ru": "Всегда включённый экран, ambient-тикер, пульс", "values-tr": "Her zaman açık ekran, ortam kayanı, nabız", "values-zh-rCN": "常亮显示、环境滚动文字、脉冲"},
    "evolver_cat_buttons": {"": "Buttons and Haptics", "values-ar": "الأزرار واللمس الاهتزازي", "values-de": "Tasten und Haptik", "values-es": "Botones y háptica", "values-fr": "Boutons et haptique", "values-hi": "बटन और हैप्टिक्स", "values-ja": "ボタンと触覚", "values-pt": "Botões e háptica", "values-ru": "Кнопки и тактильность", "values-tr": "Düğmeler ve dokunsal", "values-zh-rCN": "按钮与触感"},
    "evolver_cat_buttons_desc": {"": "Button remap, haptics, back gesture", "values-ar": "إعادة تعيين الأزرار واللمس وإيماءة الرجوع", "values-de": "Tastenbelegung, Haptik, Rückgeste", "values-es": "Reasignar botones, háptica, gesto atrás", "values-fr": "Remap des boutons, haptique, geste retour", "values-hi": "बटन रीमैप, हैप्टिक्स, बैक जेस्चर", "values-ja": "ボタン再割り当て、触覚、戻るジェスチャー", "values-pt": "Remapeamento de botões, háptica, gesto de voltar", "values-ru": "Переназначение кнопок, тактильность, жест назад", "values-tr": "Düğme yeniden atama, dokunsal, geri jesti", "values-zh-rCN": "按钮重映射、触感、返回手势"},
    "evolver_cat_netbat": {"": "Network and Battery", "values-ar": "الشبكة والبطارية", "values-de": "Netzwerk und Akku", "values-es": "Red y batería", "values-fr": "Réseau et batterie", "values-hi": "नेटवर्क और बैटरी", "values-ja": "ネットワークとバッテリー", "values-pt": "Rede e bateria", "values-ru": "Сеть и батарея", "values-tr": "Ağ ve pil", "values-zh-rCN": "网络与电池"},
    "evolver_cat_netbat_desc": {"": "Smart charging, traffic, battery light", "values-ar": "الشحن الذكي وحركة البيانات وضوء البطارية", "values-de": "Intelligentes Laden, Datenverkehr, Akku-LED", "values-es": "Carga inteligente, tráfico, luz de batería", "values-fr": "Charge intelligente, trafic, voyant batterie", "values-hi": "स्मार्ट चार्जिंग, ट्रैफ़िक, बैटरी लाइट", "values-ja": "スマート充電、トラフィック、バッテリーLED", "values-pt": "Carregamento inteligente, tráfego, luz da bateria", "values-ru": "Умная зарядка, трафик, индикатор батареи", "values-tr": "Akıllı şarj, trafik, pil ışığı", "values-zh-rCN": "智能充电、流量、电池灯"},
    "evolver_cat_sysui": {"": "System UI", "values-ar": "واجهة النظام", "values-de": "System-UI", "values-es": "Interfaz del sistema", "values-fr": "Interface système", "values-hi": "सिस्टम UI", "values-ja": "システムUI", "values-pt": "Interface do sistema", "values-ru": "Системный интерфейс", "values-tr": "Sistem arayüzü", "values-zh-rCN": "系统界面"},
    "evolver_cat_sysui_desc": {"": "Animations, blur, extra dim, display tweaks", "values-ar": "الحركات والضبابية والتعتيم الإضافي وتعديلات العرض", "values-de": "Animationen, Unschärfe, Extra-Abdunklung, Display-Tweaks", "values-es": "Animaciones, desenfoque, atenuación extra, ajustes de pantalla", "values-fr": "Animations, flou, atténuation supplémentaire, réglages écran", "values-hi": "एनिमेशन, ब्लर, एक्स्ट्रा डिम, डिस्प्ले ट्वीक", "values-ja": "アニメーション、ぼかし、追加減光、表示調整", "values-pt": "Animações, desfoque, escurecimento extra, ajustes de tela", "values-ru": "Анимации, размытие, доп. затемнение, настройки экрана", "values-tr": "Animasyonlar, bulanıklık, ekstra karartma, ekran ince ayarları", "values-zh-rCN": "动画、模糊、额外调暗、显示调整"},
    "evolver_cat_dex": {"": "DEX and Windowing", "values-ar": "DEX والنوافذ", "values-de": "DEX und Fenster", "values-es": "DEX y ventanas", "values-fr": "DEX et fenêtrage", "values-hi": "DEX और विंडोइंग", "values-ja": "DEXとウィンドウ", "values-pt": "DEX e janelas", "values-ru": "DEX и окна", "values-tr": "DEX ve pencereleme", "values-zh-rCN": "DEX与窗口"},
    "evolver_cat_dex_desc": {"": "Desktop mode, freeform, multi-window", "values-ar": "وضع سطح المكتب والنوافذ الحرة وتعدد النوافذ", "values-de": "Desktop-Modus, Freiform, Mehrfenster", "values-es": "Modo escritorio, forma libre, multiventana", "values-fr": "Mode bureau, libre, multi-fenêtres", "values-hi": "डेस्कटॉप मोड, फ्रीफ़ॉर्म, मल्टी-विंडो", "values-ja": "デスクトップモード、フリーフォーム、マルチウィンドウ", "values-pt": "Modo desktop, forma livre, multi-janela", "values-ru": "Режим ПК, свободные окна, мультиокна", "values-tr": "Masaüstü modu, serbest biçim, çoklu pencere", "values-zh-rCN": "桌面模式、自由窗口、多窗口"},
    "evolver_cat_other": {"": "Other settings", "values-ar": "إعدادات أخرى", "values-de": "Weitere Einstellungen", "values-es": "Otros ajustes", "values-fr": "Autres paramètres", "values-hi": "अन्य सेटिंग्स", "values-ja": "その他の設定", "values-pt": "Outras configurações", "values-ru": "Другие настройки", "values-tr": "Diğer ayarlar", "values-zh-rCN": "其他设置"},
    "evolver_cat_other_desc": {"": "Misc system customizations", "values-ar": "تخصيصات نظام متنوعة", "values-de": "Sonstige Systemanpassungen", "values-es": "Personalizaciones varias del sistema", "values-fr": "Personnalisations système diverses", "values-hi": "विविध सिस्टम अनुकूलन", "values-ja": "その他のシステムカスタマイズ", "values-pt": "Personalizações diversas do sistema", "values-ru": "Прочие настройки системы", "values-tr": "Çeşitli sistem özelleştirmeleri", "values-zh-rCN": "杂项系统自定义"},
}

with open("scripts/i18n/automation-builder_strings.json", encoding="utf-8") as f:
    catalog = json.load(f)

def upsert(target):
    for key, values in L.items():
        for locale, text in values.items():
            target.setdefault(key, {})[locale] = text

if isinstance(catalog, dict) and "automation-builder" in catalog:
    upsert(catalog["automation-builder"])
else:
    upsert(catalog)

with open("scripts/i18n/automation-builder_strings.json", "w", encoding="utf-8", newline="") as f:
    json.dump(catalog, f, ensure_ascii=False, indent=2)
    f.write("\n")

for locale in [""] + [k for k in next(iter(L.values())).keys() if k]:
    path = (
        "feature/automation-builder/src/main/res/values/strings.xml"
        if locale == ""
        else f"feature/automation-builder/src/main/res/{locale}/strings.xml"
    )
    with open(path, encoding="utf-8") as f:
        content = f.read()
    for key, values in L.items():
        text = values[locale].replace("&", "&amp;")
        pattern = rf'[ \t]*<string name="{key}">.*?</string>\n'
        entry = f'    <string name="{key}">{text}</string>\n'
        if re.search(pattern, content):
            content = re.sub(pattern, entry, content, count=1)
        else:
            content = content.replace("</resources>", entry + "</resources>")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(content)

print(f"done: {len(L)} keys x 11 locales")
