#!/usr/bin/env python3
"""Adds the v3.64.0 option-customization strings to the builder module.

Keys: http_retry_timing_label, http_retry_base_ms, http_retry_cap_ms,
pointer_speed_range, screensaver_minutes_chip.
English authoritative; the other 10 locales get real translations.
All values are apostrophe-free so no XML escaping beyond & -> &amp; applies.
"""
import json
import re

CATALOG = {
    "automation-builder": {
        "": {
            "http_retry_timing_label": "Retry timing",
            "http_retry_base_ms": "Base: %1$d ms",
            "http_retry_cap_ms": "Cap: %1$d ms",
            "pointer_speed_range": "Pointer speed spans -7 (slowest) to 7 (fastest); 0 is the system default.",
            "screensaver_minutes_chip": "%1$d min",
        },
        "values-ar": {
            "http_retry_timing_label": "توقيت إعادة المحاولة",
            "http_retry_base_ms": "الأساس: %1$d مللي ثانية",
            "http_retry_cap_ms": "الحد الأقصى: %1$d مللي ثانية",
            "pointer_speed_range": "تمتد سرعة المؤشر من -7 (الأبطأ) إلى 7 (الأسرع)؛ 0 هو الافتراضي للنظام.",
            "screensaver_minutes_chip": "%1$d دقيقة",
        },
        "values-de": {
            "http_retry_timing_label": "Zeitplan der Wiederholungen",
            "http_retry_base_ms": "Basis: %1$d ms",
            "http_retry_cap_ms": "Obergrenze: %1$d ms",
            "pointer_speed_range": "Die Zeigergeschwindigkeit reicht von -7 (langsamste) bis 7 (schnellste); 0 ist der Systemstandard.",
            "screensaver_minutes_chip": "%1$d Min.",
        },
        "values-es": {
            "http_retry_timing_label": "Tiempo entre reintentos",
            "http_retry_base_ms": "Base: %1$d ms",
            "http_retry_cap_ms": "Límite: %1$d ms",
            "pointer_speed_range": "La velocidad del puntero va de -7 (la más lenta) a 7 (la más rápida); 0 es el valor del sistema.",
            "screensaver_minutes_chip": "%1$d min",
        },
        "values-fr": {
            "http_retry_timing_label": "Minuterie des tentatives",
            "http_retry_base_ms": "Base : %1$d ms",
            "http_retry_cap_ms": "Plafond : %1$d ms",
            "pointer_speed_range": "La vitesse du pointeur va de -7 (la plus lente) à 7 (la plus rapide) ; 0 est la valeur par défaut du système.",
            "screensaver_minutes_chip": "%1$d min",
        },
        "values-hi": {
            "http_retry_timing_label": "पुनः प्रयास समय",
            "http_retry_base_ms": "आधार: %1$d ms",
            "http_retry_cap_ms": "सीमा: %1$d ms",
            "pointer_speed_range": "पॉइंटर गति -7 (सबसे धीमी) से 7 (सबसे तेज़) तक है; 0 सिस्टम डिफ़ॉल्ट है।",
            "screensaver_minutes_chip": "%1$d मिनट",
        },
        "values-ja": {
            "http_retry_timing_label": "リトライ間隔",
            "http_retry_base_ms": "基本: %1$d ms",
            "http_retry_cap_ms": "上限: %1$d ms",
            "pointer_speed_range": "ポインター速度は -7（最も遅い）から 7（最も速い）です。0 はシステムのデフォルトです。",
            "screensaver_minutes_chip": "%1$d分",
        },
        "values-pt": {
            "http_retry_timing_label": "Tempo entre tentativas",
            "http_retry_base_ms": "Base: %1$d ms",
            "http_retry_cap_ms": "Limite: %1$d ms",
            "pointer_speed_range": "A velocidade do ponteiro vai de -7 (mais lenta) a 7 (mais rápida); 0 é o padrão do sistema.",
            "screensaver_minutes_chip": "%1$d min",
        },
        "values-ru": {
            "http_retry_timing_label": "Интервал повторов",
            "http_retry_base_ms": "База: %1$d мс",
            "http_retry_cap_ms": "Потолок: %1$d мс",
            "pointer_speed_range": "Скорость указателя от -7 (самая медленная) до 7 (самая быстрая); 0 — системное значение по умолчанию.",
            "screensaver_minutes_chip": "%1$d мин",
        },
        "values-tr": {
            "http_retry_timing_label": "Yeniden deneme zamanlaması",
            "http_retry_base_ms": "Temel: %1$d ms",
            "http_retry_cap_ms": "Üst sınır: %1$d ms",
            "pointer_speed_range": "İşaretçi hızı -7 (en yavaş) ile 7 (en hızlı) arasındadır; 0 sistem varsayılanıdır.",
            "screensaver_minutes_chip": "%1$d dk",
        },
        "values-zh-rCN": {
            "http_retry_timing_label": "重试间隔",
            "http_retry_base_ms": "基础：%1$d 毫秒",
            "http_retry_cap_ms": "上限：%1$d 毫秒",
            "pointer_speed_range": "指针速度范围为 -7（最慢）到 7（最快）；0 为系统默认值。",
            "screensaver_minutes_chip": "%1$d 分钟",
        },
    },
}

MODULES = {
    "automation-builder": "feature/automation-builder",
}

for module, res_dir in MODULES.items():
    strings = CATALOG[module]
    catalog_path = "scripts/i18n/automation-builder_strings.json"
    with open(catalog_path, encoding="utf-8") as f:
        catalog = json.load(f)
    target = catalog[""]
    # The catalog JSON stores locale dicts under "" (EN) and "-xx" keys.
    for locale, values in strings.items():
        key = "" if locale == "" else "-" + locale.removeprefix("values-")
        entry = catalog.setdefault(key, {})
        for k, text in values.items():
            entry[k] = text
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

print("done: v3.64.0 option strings injected")
