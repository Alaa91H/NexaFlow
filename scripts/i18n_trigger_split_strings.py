#!/usr/bin/env python3
"""Injects the v3.59.3 trigger-split strings into all three feature modules.

Keys: trigger_type_wifi_connected(_sub), trigger_type_mobile_data(_sub),
call_match_any, call_mode_hint, call_number_filter.
English is authoritative; ar gets real translations, other locales fall back
to English. Values are apostrophe-free so no XML escaping is needed.
"""
import glob
import os

TRANSLATIONS = {
    "": {
        "trigger_type_wifi_connected": "Wi-Fi connected",
        "trigger_type_wifi_connected_sub": "Runs when the device connects to Wi-Fi (or disconnects)",
        "trigger_type_mobile_data": "Mobile data connected",
        "trigger_type_mobile_data_sub": "Runs when mobile data is the active network (or not)",
        "call_match_any": "Any number",
        "call_mode_hint": "Matches the caller number, not a message text.",
        "call_number_filter": "Caller number",
    },
    "ar": {
        "trigger_type_wifi_connected": "اتصال الواي فاي",
        "trigger_type_wifi_connected_sub": "يعمل عند اتصال الجهاز بشبكة الواي فاي أو فصله عنها",
        "trigger_type_mobile_data": "اتصال بيانات الجوال",
        "trigger_type_mobile_data_sub": "يعمل عندما تكون بيانات الجوال هي الشبكة النشطة أو العكس",
        "call_match_any": "أي رقم",
        "call_mode_hint": "يطابق رقم المتصل وليس نص الرسالة.",
        "call_number_filter": "رقم المتصل",
    },
}

MODULES = {
    "feature/automation-builder": [
        "trigger_type_wifi_connected",
        "trigger_type_wifi_connected_sub",
        "trigger_type_mobile_data",
        "trigger_type_mobile_data_sub",
        "call_match_any",
        "call_mode_hint",
        "call_number_filter",
    ],
    "feature/automations": [
        "trigger_type_wifi_connected",
        "trigger_type_wifi_connected_sub",
        "trigger_type_mobile_data",
        "trigger_type_mobile_data_sub",
    ],
    "feature/dashboard": [
        "trigger_type_wifi_connected",
        "trigger_type_mobile_data",
    ],
}

TEMPLATE = '    <string name="{key}">{value}</string>\n'


def locale_dirs(module):
    root = os.path.join(module, "src", "main", "res")
    return sorted(glob.glob(os.path.join(root, "values*")))


def locale_of(res_dir):
    name = os.path.basename(res_dir)
    if name == "values":
        return ""
    return name.replace("values-", "", 1)


def main():
    total = 0
    for module, keys in MODULES.items():
        for res_dir in locale_dirs(module):
            loc = locale_of(res_dir)
            table = TRANSLATIONS.get(loc) or TRANSLATIONS[""]
            path = os.path.join(res_dir, "strings.xml")
            with open(path, encoding="utf-8") as f:
                content = f.read()
            additions = []
            for key in keys:
                token = f'name="{key}"'
                if token in content:
                    continue
                additions.append(TEMPLATE.format(key=key, value=table[key]))
            if not additions:
                continue
            if not content.endswith("\n"):
                content += "\n"
            content += "</resources-placeholder-split>\n" if False else ""
            idx = content.rindex("</resources>")
            content = content[:idx] + "".join(additions) + content[idx:]
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.write(content)
            total += len(additions)
            print(f"{path}: +{len(additions)}")
    print("TOTAL", total)


if __name__ == "__main__":
    main()
