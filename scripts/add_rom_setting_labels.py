# -*- coding: utf-8 -*-
"""Adds the ROM setting trigger label strings to feature/dashboard and
feature/automations across every locale. Line-based and EOL-preserving;
idempotent (skips keys that already exist)."""

import io
import os

MODULES = ["feature/dashboard", "feature/automations"]

# name -> English value (dashboard has no "_sub"; automations has both)
VALUES = {
    "trigger_rom_setting": "ROM setting",
    "trigger_rom_setting_sub": "Fires when a real Evolution X setting matches",
}

translations = {
    "ar": {
        "trigger_rom_setting": "إعداد ROM",
        "trigger_rom_setting_sub": "يعمل عند تطابق إعداد Evolution X حقيقي",
    },
    "de": {
        "trigger_rom_setting": "ROM-Einstellung",
        "trigger_rom_setting_sub": "Läuft, wenn eine echte Evolution X-Einstellung übereinstimmt",
    },
    "es": {
        "trigger_rom_setting": "Ajuste de ROM",
        "trigger_rom_setting_sub": "Se ejecuta cuando un ajuste real de Evolution X coincide",
    },
    "fr": {
        "trigger_rom_setting": "Réglage de la ROM",
        "trigger_rom_setting_sub": "Se déclenche quand un réglage réel d'Evolution X correspond",
    },
    "hi": {
        "trigger_rom_setting": "ROM सेटिंग",
        "trigger_rom_setting_sub": "वास्तविक Evolution X सेटिंग मेल खाने पर चलता है",
    },
    "ja": {
        "trigger_rom_setting": "ROM設定",
        "trigger_rom_setting_sub": "実際のEvolution X設定が一致したときに実行",
    },
    "pt": {
        "trigger_rom_setting": "Configuração da ROM",
        "trigger_rom_setting_sub": "Executa quando uma configuração real da Evolution X corresponde",
    },
    "ru": {
        "trigger_rom_setting": "Настройка ROM",
        "trigger_rom_setting_sub": "Срабатывает при совпадении реальной настройки Evolution X",
    },
    "tr": {
        "trigger_rom_setting": "ROM ayarı",
        "trigger_rom_setting_sub": "Gerçek bir Evolution X ayarı eşleştiğinde çalışır",
    },
    "zh-rCN": {
        "trigger_rom_setting": "ROM 设置",
        "trigger_rom_setting_sub": "当真实的 Evolution X 设置匹配时运行",
    },
}

# Which keys to insert in which module (dashboard: no sub)
KEYS_BY_MODULE = {
    "feature/dashboard": ["trigger_rom_setting"],
    "feature/automations": ["trigger_rom_setting", "trigger_rom_setting_sub"],
}

# Insert before this anchor (the network-mode label that follows webhook)
ANCHOR = "trigger_type_network_mode"


def main():
    for module in MODULES:
        base = os.path.join(module, "src/main/res")
        for locale in ["values"] + [f"values-{l}" for l in translations]:
            path = os.path.join(base, locale, "strings.xml")
            if not os.path.isfile(path):
                print(f"SKIP {module}/{locale}: no file")
                continue
            with io.open(path, "r", encoding="utf-8", newline="") as f:
                content = f.read()
            eol = "\r\n" if "\r\n" in content else "\n"
            lines = content.splitlines()
            existing = {
                line.split('name="')[1].split('"')[0]
                for line in lines
                if '<string name="' in line
            }
            keys = KEYS_BY_MODULE[module]
            new_lines = []
            inserted = False
            for ln in lines:
                if not inserted and f'name="{ANCHOR}"' in ln:
                    for key in keys:
                        if key in existing:
                            continue
                        val = translations[locale[7:]].get(key) if locale != "values" else VALUES[key]
                        new_lines.append(f'    <string name="{key}">{val}</string>')
                    inserted = True
                new_lines.append(ln)
            if not inserted:
                print(f"WARN {module}/{locale}: anchor not found")
            with io.open(path, "w", encoding="utf-8", newline="") as f:
                f.write(eol.join(new_lines) + eol)
            print(f"OK {module}/{locale}")


if __name__ == "__main__":
    main()
