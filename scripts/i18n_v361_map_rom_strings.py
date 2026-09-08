#!/usr/bin/env python3
"""Injects the v3.61.0 strings into the automation-builder module.

Covers: map picker (clipboard round-trip), ROM-setting chip editor, and
builder misc. English is authoritative; other locales get English fallback
except Arabic which gets real translations. All values are apostrophe-free
so no XML escaping is needed.
"""
import json
import os

EN = {
    # Map picker (clipboard round-trip)
    "map_picker_title": "Choose location",
    "map_selected_location": "Selected location",
    "map_picker_hint": "Open your installed maps app to search or select a place, then copy its coordinates and paste them here.",
    "map_open_maps_app": "Open maps app",
    "map_no_maps_app": "No compatible maps application is installed. Enter coordinates manually.",
    "map_paste_coordinates": "Paste coordinates",
    "map_copy_coordinates": "Copy coordinates",
    "map_copied_feedback": "Coordinates copied to clipboard",
    "map_paste_failed": "No coordinates found in the clipboard. Copy a place or a lat,lng pair first.",
    "map_latitude": "Latitude",
    "map_longitude": "Longitude",
    "map_radius_meters": "Radius (meters)",
    "map_radius_range": "Allowed range: %1$d to %2$d meters",
    "map_error_coordinates": "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).",
    "map_error_radius": "Radius must be between 50 and 2000 meters.",
    "map_save_location": "Save location",
    # ROM setting trigger editor (chip-driven)
    "rom_setting_trigger_title": "ROM setting change",
    "rom_setting_trigger_hint": "Fires when a system (Evolver) key changes to the target value. Pick a key from your device — no typing needed.",
    "rom_setting_pick_key": "Pick a setting key (categorized)",
    "rom_setting_key_category": "%1$s • %2$s",
}

AR = {
    # Map picker (clipboard round-trip)
    "map_picker_title": "اختيار الموقع",
    "map_selected_location": "الموقع المحدد",
    "map_picker_hint": "افتح تطبيق الخرائط المثبت للبحث عن مكان أو تحديده، ثم انسخ إحداثياته والصقها هنا.",
    "map_open_maps_app": "فتح تطبيق الخرائط",
    "map_no_maps_app": "لا يوجد تطبيق خرائط متوافق مثبت. أدخل الإحداثيات يدويا.",
    "map_paste_coordinates": "لصق الإحداثيات",
    "map_copy_coordinates": "نسخ الإحداثيات",
    "map_copied_feedback": "تم نسخ الإحداثيات إلى الحافظة",
    "map_paste_failed": "لم يتم العثور على إحداثيات في الحافظة. انسخ مكانا أو زوج خط طول وعرض أولا.",
    "map_latitude": "خط العرض",
    "map_longitude": "خط الطول",
    "map_radius_meters": "نطاق التغطية (بالمتر)",
    "map_radius_range": "النطاق المسموح: من %1$d إلى %2$d مترا",
    "map_error_coordinates": "أدخل خط عرض صحيحا (من -90 إلى 90) وخط طول صحيحا (من -180 إلى 180).",
    "map_error_radius": "يجب أن يكون النطاق بين 50 و2000 متر.",
    "map_save_location": "حفظ الموقع",
    # ROM setting trigger editor (chip-driven)
    "rom_setting_trigger_title": "تغيير إعداد النظام",
    "rom_setting_trigger_hint": "يُشغَّل عند تغيّر مفتاح نظام (Evolver) إلى القيمة المستهدفة. اختر مفتاحا من جهازك دون كتابة.",
    "rom_setting_pick_key": "اختيار مفتاح إعداد (مصنّف)",
    "rom_setting_key_category": "%1$s • %2$s",
}

OTHER = dict(EN)

path = "scripts/i18n/automation-builder_strings.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)

locales = ["-ar", "-de", "-es", "-fr", "-hi", "-ja", "-pt", "-ru", "-tr", "-zh-rCN"]
data.setdefault("", {}).update(EN)
data.setdefault("-ar", {}).update(AR)
for l in locales:
    data.setdefault(l, {}).update(OTHER)

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print("updated", path)
