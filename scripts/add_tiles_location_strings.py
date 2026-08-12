# -*- coding: utf-8 -*-
"""Adds tiles 5-8 (widgets) and custom location interval strings (settings) to every locale except English."""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

T = {
    "tile_5_label": {
        "ar": "\u0627\u0644\u0645\u0647\u0645\u0629 5", "de": "Aufgabe 5", "es": "Tarea 5", "fr": "T\u00e2che 5",
        "hi": "\u0915\u093e\u0930\u094d\u092f 5", "ja": "\u30bf\u30b9\u30af 5", "pt": "Tarefa 5", "ru": "\u0417\u0430\u0434\u0430\u0447\u0430 5",
        "tr": "G\u00f6rev 5", "zh-rCN": "\u4efb\u52a1 5",
    },
    "tile_6_label": {
        "ar": "\u0627\u0644\u0645\u0647\u0645\u0629 6", "de": "Aufgabe 6", "es": "Tarea 6", "fr": "T\u00e2che 6",
        "hi": "\u0915\u093e\u0930\u094d\u092f 6", "ja": "\u30bf\u30b9\u30af 6", "pt": "Tarefa 6", "ru": "\u0417\u0430\u0434\u0430\u0447\u0430 6",
        "tr": "G\u00f6rev 6", "zh-rCN": "\u4efb\u52a1 6",
    },
    "tile_7_label": {
        "ar": "\u0627\u0644\u0645\u0647\u0645\u0629 7", "de": "Aufgabe 7", "es": "Tarea 7", "fr": "T\u00e2che 7",
        "hi": "\u0915\u093e\u0930\u094d\u092f 7", "ja": "\u30bf\u30b9\u30af 7", "pt": "Tarefa 7", "ru": "\u0417\u0430\u0434\u0430\u0447\u0430 7",
        "tr": "G\u00f6rev 7", "zh-rCN": "\u4efb\u52a1 7",
    },
    "tile_8_label": {
        "ar": "\u0627\u0644\u0645\u0647\u0645\u0629 8", "de": "Aufgabe 8", "es": "Tarea 8", "fr": "T\u00e2che 8",
        "hi": "\u0915\u093e\u0930\u094d\u092f 8", "ja": "\u30bf\u30b9\u30af 8", "pt": "Tarefa 8", "ru": "\u0417\u0430\u0434\u0430\u0447\u0430 8",
        "tr": "G\u00f6rev 8", "zh-rCN": "\u4efb\u52a1 8",
    },
    "tile_5_desc": {
        "ar": "\u0628\u062f\u0651\u0644 \u0627\u0644\u0645\u0647\u0645\u0629 \u0627\u0644\u062e\u0627\u0645\u0633\u0629 \u0645\u0628\u0627\u0634\u0631\u0629 \u0645\u0646 \u0644\u0648\u062d\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0633\u0631\u064a\u0639\u0629.",
        "de": "Schalte die f\u00fcnfte Aufgabe direkt \u00fcber das Schnelleinstellungsfeld um.",
        "es": "Alterna la quinta tarea directamente desde el panel de ajustes r\u00e1pidos.",
        "fr": "Bascule la cinqui\u00e8me t\u00e2che directement depuis le panneau des r\u00e9glages rapides.",
        "hi": "\u092a\u093e\u0901\u091a\u0935\u093e\u0901 \u0915\u093e\u0930\u094d\u092f \u0938\u0940\u0927\u0947 \u0915\u094d\u0935\u093f\u0915 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938 \u092a\u0948\u0928\u0932 \u0938\u0947 \u091f\u0949\u0917\u0932 \u0915\u0930\u0947\u0902\u0964",
        "ja": "\u30af\u30a4\u30c3\u30af\u8a2d\u5b9a\u30d1\u30cd\u30eb\u304b\u30895\u756a\u76ee\u306e\u30bf\u30b9\u30af\u3092\u5207\u308a\u66ff\u3048\u307e\u3059\u3002",
        "pt": "Alterna a quinta tarefa diretamente no painel de defini\u00e7\u00f5es r\u00e1pidas.",
        "ru": "\u041f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0430\u0439\u0442\u0435 \u043f\u044f\u0442\u0443\u044e \u0437\u0430\u0434\u0430\u0447\u0443 \u043f\u0440\u044f\u043c\u043e \u0438\u0437 \u043f\u0430\u043d\u0435\u043b\u0438 \u0431\u044b\u0441\u0442\u0440\u044b\u0445 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a.",
        "tr": "Be\u015finci g\u00f6revi do\u011frudan H\u0131zl\u0131 Ayarlar panelinden de\u011fi\u015ftirin.",
        "zh-rCN": "\u76f4\u63a5\u4ece\u5feb\u901f\u8bbe\u7f6e\u9762\u677f\u5207\u6362\u7b2c\u4e94\u4e2a\u4efb\u52a1\u3002",
    },
    "tile_6_desc": {
        "ar": "\u0628\u062f\u0651\u0644 \u0627\u0644\u0645\u0647\u0645\u0629 \u0627\u0644\u0633\u0627\u062f\u0633\u0629 \u0645\u0628\u0627\u0634\u0631\u0629 \u0645\u0646 \u0644\u0648\u062d\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0633\u0631\u064a\u0639\u0629.",
        "de": "Schalte die sechste Aufgabe direkt \u00fcber das Schnelleinstellungsfeld um.",
        "es": "Alterna la sexta tarea directamente desde el panel de ajustes r\u00e1pidos.",
        "fr": "Bascule la sixi\u00e8me t\u00e2che directement depuis le panneau des r\u00e9glages rapides.",
        "hi": "\u091b\u0920\u093e \u0915\u093e\u0930\u094d\u092f \u0938\u0940\u0927\u0947 \u0915\u094d\u0935\u093f\u0915 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938 \u092a\u0948\u0928\u0932 \u0938\u0947 \u091f\u0949\u0917\u0932 \u0915\u0930\u0947\u0902\u0964",
        "ja": "\u30af\u30a4\u30c3\u30af\u8a2d\u5b9a\u30d1\u30cd\u30eb\u304b\u30896\u756a\u76ee\u306e\u30bf\u30b9\u30af\u3092\u5207\u308a\u66ff\u3048\u307e\u3059\u3002",
        "pt": "Alterna a sexta tarefa diretamente no painel de defini\u00e7\u00f5es r\u00e1pidas.",
        "ru": "\u041f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0430\u0439\u0442\u0435 \u0448\u0435\u0441\u0442\u0443\u044e \u0437\u0430\u0434\u0430\u0447\u0443 \u043f\u0440\u044f\u043c\u043e \u0438\u0437 \u043f\u0430\u043d\u0435\u043b\u0438 \u0431\u044b\u0441\u0442\u0440\u044b\u0445 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a.",
        "tr": "Alt\u0131nc\u0131 g\u00f6revi do\u011frudan H\u0131zl\u0131 Ayarlar panelinden de\u011fi\u015ftirin.",
        "zh-rCN": "\u76f4\u63a5\u4ece\u5feb\u901f\u8bbe\u7f6e\u9762\u677f\u5207\u6362\u7b2c\u516d\u4e2a\u4efb\u52a1\u3002",
    },
    "tile_7_desc": {
        "ar": "\u0628\u062f\u0651\u0644 \u0627\u0644\u0645\u0647\u0645\u0629 \u0627\u0644\u0633\u0627\u0628\u0639\u0629 \u0645\u0628\u0627\u0634\u0631\u0629 \u0645\u0646 \u0644\u0648\u062d\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0633\u0631\u064a\u0639\u0629.",
        "de": "Schalte die siebte Aufgabe direkt \u00fcber das Schnelleinstellungsfeld um.",
        "es": "Alterna la s\u00e9ptima tarea directamente desde el panel de ajustes r\u00e1pidos.",
        "fr": "Bascule la septi\u00e8me t\u00e2che directement depuis le panneau des r\u00e9glages rapides.",
        "hi": "\u0938\u093e\u0924\u0935\u093e\u0901 \u0915\u093e\u0930\u094d\u092f \u0938\u0940\u0927\u0947 \u0915\u094d\u0935\u093f\u0915 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938 \u092a\u0948\u0928\u0932 \u0938\u0947 \u091f\u0949\u0917\u0932 \u0915\u0930\u0947\u0902\u0964",
        "ja": "\u30af\u30a4\u30c3\u30af\u8a2d\u5b9a\u30d1\u30cd\u30eb\u304b\u30897\u756a\u76ee\u306e\u30bf\u30b9\u30af\u3092\u5207\u308a\u66ff\u3048\u307e\u3059\u3002",
        "pt": "Alterna a s\u00e9tima tarefa diretamente no painel de defini\u00e7\u00f5es r\u00e1pidas.",
        "ru": "\u041f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0430\u0439\u0442\u0435 \u0441\u0435\u0434\u044c\u043c\u0443\u044e \u0437\u0430\u0434\u0430\u0447\u0443 \u043f\u0440\u044f\u043c\u043e \u0438\u0437 \u043f\u0430\u043d\u0435\u043b\u0438 \u0431\u044b\u0441\u0442\u0440\u044b\u0445 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a.",
        "tr": "Yedinci g\u00f6revi do\u011frudan H\u0131zl\u0131 Ayarlar panelinden de\u011fi\u015ftirin.",
        "zh-rCN": "\u76f4\u63a5\u4ece\u5feb\u901f\u8bbe\u7f6e\u9762\u677f\u5207\u6362\u7b2c\u4e03\u4e2a\u4efb\u52a1\u3002",
    },
    "tile_8_desc": {
        "ar": "\u0628\u062f\u0651\u0644 \u0627\u0644\u0645\u0647\u0645\u0629 \u0627\u0644\u062b\u0627\u0645\u0646\u0629 \u0645\u0628\u0627\u0634\u0631\u0629 \u0645\u0646 \u0644\u0648\u062d\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0633\u0631\u064a\u0639\u0629.",
        "de": "Schalte die achte Aufgabe direkt \u00fcber das Schnelleinstellungsfeld um.",
        "es": "Alterna la octava tarea directamente desde el panel de ajustes r\u00e1pidos.",
        "fr": "Bascule la huiti\u00e8me t\u00e2che directement depuis le panneau des r\u00e9glages rapides.",
        "hi": "\u0906\u0920\u0935\u093e\u0901 \u0915\u093e\u0930\u094d\u092f \u0938\u0940\u0927\u0947 \u0915\u094d\u0935\u093f\u0915 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938 \u092a\u0948\u0646\u0932 \u0938\u0947 \u091f\u0949\u0917\u0932 \u0915\u0930\u0947\u0902\u0964",
        "ja": "\u30af\u30a4\u30c3\u30af\u8a2d\u5b9a\u30d1\u30cd\u30eb\u304b\u30898\u756a\u76ee\u306e\u30bf\u30b9\u30af\u3092\u5207\u308a\u66ff\u3048\u307e\u3059\u3002",
        "pt": "Alterna a oitava tarefa diretamente no painel de defini\u00e7\u00f5es r\u00e1pidas.",
        "ru": "\u041f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0430\u0439\u0442\u0435 \u0432\u043e\u0441\u044c\u043c\u0443\u044e \u0437\u0430\u0434\u0430\u0447\u0443 \u043f\u0440\u044f\u043c\u043e \u0438\u0437 \u043f\u0430\u043d\u0435\u043b\u0438 \u0431\u044b\u0441\u0442\u0440\u044b\u0445 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a.",
        "tr": "Sekizinci g\u00f6revi do\u011frudan H\u0131zl\u0131 Ayarlar panelinden de\u011fi\u015ftirin.",
        "zh-rCN": "\u76f4\u63a5\u4ece\u5feb\u901f\u8bbe\u7f6e\u9762\u677f\u5207\u6362\u7b2c\u516b\u4e2a\u4efb\u52a1\u3002",
    },
    "location_check_custom": {
        "ar": "\u0645\u062e\u0635\u0635\u2026", "de": "Benutzerdefiniert\u2026", "es": "Personalizado\u2026",
        "fr": "Personnalis\u00e9\u2026", "hi": "\u0915\u0938\u094d\u091f\u092e\u2026", "ja": "\u30ab\u30b9\u30bf\u30e0\u2026",
        "pt": "Personalizado\u2026", "ru": "\u0421\u0432\u043e\u0439\u2026", "tr": "\u00d6zel\u2026", "zh-rCN": "\u81ea\u5b9a\u4e49\u2026",
    },
    "location_check_custom_minutes": {
        "ar": "\u0643\u0644 %1$d \u062f\u0642\u064a\u0642\u0629", "de": "Alle %1$d Minuten", "es": "Cada %1$d minutos",
        "fr": "Toutes les %1$d minutes", "hi": "\u0939\u0930 %1$d \u092e\u093f\u0928\u091f", "ja": "%1$d\u5206\u3054\u3068",
        "pt": "A cada %1$d minutos", "ru": "\u041a\u0430\u0436\u0434\u044b\u0435 %1$d \u043c\u0438\u043d\u0443\u0442",
        "tr": "Her %1$d dakikada bir", "zh-rCN": "\u6bcf %1$d \u5206\u949f",
    },
    "location_check_minutes": {
        "ar": "\u062f\u0642\u0627\u0626\u0642", "de": "Minuten", "es": "Minutos", "fr": "Minutes",
        "hi": "\u092e\u093f\u0928\u091f", "ja": "\u5206", "pt": "Minutos", "ru": "\u041c\u0438\u043d\u0443\u0442",
        "tr": "Dakika", "zh-rCN": "\u5206\u949f",
    },
    "location_check_apply": {
        "ar": "\u062a\u0637\u0628\u064a\u0642", "de": "\u00dcbernehmen", "es": "Aplicar", "fr": "Appliquer",
        "hi": "\u0932\u093e\u0917\u0942 \u0915\u0930\u0947\u0902", "ja": "\u9069\u7528", "pt": "Aplicar", "ru": "\u041f\u0440\u0438\u043c\u0435\u043d\u0438\u0442\u044c",
        "tr": "Uygula", "zh-rCN": "\u5e94\u7528",
    },
}

MODULES = {
    "feature/widgets": [
        "tile_5_label", "tile_6_label", "tile_7_label", "tile_8_label",
        "tile_5_desc", "tile_6_desc", "tile_7_desc", "tile_8_desc",
    ],
    "feature/settings": [
        "location_check_custom", "location_check_custom_minutes",
        "location_check_minutes", "location_check_apply",
    ],
}

APOS = re.compile("(?<!\\\\)'")


def esc(text):
    return APOS.sub("\\'", text)


def add(path, keys, locale):
    with io.open(path, "r", encoding="utf-8") as fh:
        content = fh.read()
    existing = set(re.findall(r'<string name="([^"]+)">', content))
    lines = []
    for key in keys:
        if key in existing or key not in T or locale not in T[key]:
            continue
        lines.append('    <string name="%s">%s</string>' % (key, esc(T[key][locale])))
    if not lines:
        return 0
    insert = "\n" + "\n".join(lines) + "\n"
    content = content.replace("</resources>", insert + "</resources>", 1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(content)
    return len(lines)


total = 0
for module, keys in MODULES.items():
    base = os.path.join(ROOT, module, "src", "main", "res")
    for entry in sorted(os.listdir(base)):
        if not entry.startswith("values-"):
            continue
        locale = entry[len("values-"):]
        path = os.path.join(base, entry, "strings.xml")
        if os.path.exists(path):
            added = add(path, keys, locale)
            if added:
                print("%s/%s: +%d" % (module, entry, added))
                total += added
print("TOTAL added: %d" % total)
