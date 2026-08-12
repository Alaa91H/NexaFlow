#!/usr/bin/env python3
"""Add update-dialog strings after the update_downloading line in each locale."""
import io, os, re

BASE = "feature/settings/src/main/res"

# locale -> { key: value }
STRINGS = {
    "values": {
        "update_dialog_title": "Update available",
        "update_dialog_download": "Download & install",
        "update_dialog_later": "Later",
        "update_dialog_size": "Size: %1$s",
    },
    "values-ar": {
        "update_dialog_title": "يتوفر تحديث",
        "update_dialog_download": "تنزيل وتثبيت",
        "update_dialog_later": "لاحقاً",
        "update_dialog_size": "الحجم: %1$s",
    },
    "values-de": {
        "update_dialog_title": "Update verfügbar",
        "update_dialog_download": "Herunterladen und installieren",
        "update_dialog_later": "Später",
        "update_dialog_size": "Größe: %1$s",
    },
    "values-es": {
        "update_dialog_title": "Actualización disponible",
        "update_dialog_download": "Descargar e instalar",
        "update_dialog_later": "Más tarde",
        "update_dialog_size": "Tamaño: %1$s",
    },
    "values-fr": {
        "update_dialog_title": "Mise à jour disponible",
        "update_dialog_download": "Télécharger et installer",
        "update_dialog_later": "Plus tard",
        "update_dialog_size": "Taille : %1$s",
    },
    "values-hi": {
        "update_dialog_title": "अपडेट उपलब्ध है",
        "update_dialog_download": "डाउनलोड करें और इنس्टॉल करें",
        "update_dialog_later": "बाद में",
        "update_dialog_size": "आकार: %1$s",
    },
    "values-ja": {
        "update_dialog_title": "アップデートがあります",
        "update_dialog_download": "ダウンロードしてインストール",
        "update_dialog_later": "後で",
        "update_dialog_size": "サイズ: %1$s",
    },
    "values-pt": {
        "update_dialog_title": "Atualização disponível",
        "update_dialog_download": "Baixar e instalar",
        "update_dialog_later": "Mais tarde",
        "update_dialog_size": "Tamanho: %1$s",
    },
    "values-ru": {
        "update_dialog_title": "Доступно обновление",
        "update_dialog_download": "Скачать и установить",
        "update_dialog_later": "Позже",
        "update_dialog_size": "Размер: %1$s",
    },
    "values-tr": {
        "update_dialog_title": "Güncelleme mevcut",
        "update_dialog_download": "İndir ve kur",
        "update_dialog_later": "Sonra",
        "update_dialog_size": "Boyut: %1$s",
    },
    "values-zh-rCN": {
        "update_dialog_title": "发现新版本",
        "update_dialog_download": "下载并安装",
        "update_dialog_later": "稍后",
        "update_dialog_size": "大小：%1$s",
    },
}

for locale, entries in STRINGS.items():
    path = os.path.join(BASE, locale, "strings.xml")
    with io.open(path, encoding="utf-8") as f:
        content = f.read()
    # Anchor on the existing update_downloading line for this locale.
    m = re.search(r'<string name="update_downloading">[^<]*</string>', content)
    if not m:
        raise SystemExit(f"anchor not found in {path}")
    block = "\n".join(
        '    <string name="%s">%s</string>' % (k, v) for k, v in entries.items()
    )
    new_content = content[: m.end()] + "\n" + block + content[m.end():]
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_content)
    print("updated", path)
