"""Add localized in-app update checker strings (P2-6) to feature/settings."""
import io
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": {
        "section_updates": "Updates",
        "update_check": "Check for updates",
        "update_check_sub": "Check GitHub for a newer version",
        "update_checking": "Checking for updates…",
        "update_latest": "You are up to date",
        "update_latest_sub": "Latest release: %1$s",
        "state_ok": "OK",
        "state_update": "UPDATE",
        "update_available": "Update available",
        "update_available_sub": "Version %1$s is ready to install",
        "update_downloading": "Downloading…",
        "update_error": "Update check failed",
        "update_no_apk": "This release has no downloadable APK",
        "update_download_failed": "Download or verification failed",
        "update_install_failed": "Could not open the installer",
        "update_check_failed": "Could not reach GitHub. Try again later.",
    },
    "values-ar": {
        "section_updates": "التحديثات",
        "update_check": "التحقق من التحديثات",
        "update_check_sub": "التحقق من GitHub لوجود نسخة أحدث",
        "update_checking": "جارٍ التحقق من التحديثات…",
        "update_latest": "أنت على أحدث إصدار",
        "update_latest_sub": "أحدث إصدار: %1$s",
        "state_ok": "حسناً",
        "state_update": "تحديث",
        "update_available": "يتوفر تحديث",
        "update_available_sub": "النسخة %1$s جاهزة للتثبيت",
        "update_downloading": "جارٍ التنزيل…",
        "update_error": "فشل التحقق من التحديث",
        "update_no_apk": "لا يحتوي هذا الإصدار على APK قابل للتنزيل",
        "update_download_failed": "فشل التنزيل أو التحقق",
        "update_install_failed": "تعذر فتح المثبّت",
        "update_check_failed": "تعذر الوصول إلى GitHub. حاول لاحقاً.",
    },
    "values-de": {
        "section_updates": "Updates",
        "update_check": "Nach Updates suchen",
        "update_check_sub": "Auf GitHub nach einer neueren Version suchen",
        "update_checking": "Suche nach Updates…",
        "update_latest": "Sie sind auf dem neuesten Stand",
        "update_latest_sub": "Neueste Version: %1$s",
        "state_ok": "OK",
        "state_update": "UPDATE",
        "update_available": "Update verfügbar",
        "update_available_sub": "Version %1$s ist bereit zur Installation",
        "update_downloading": "Wird heruntergeladen…",
        "update_error": "Updateprüfung fehlgeschlagen",
        "update_no_apk": "Diese Version hat kein herunterladbares APK",
        "update_download_failed": "Download oder Prüfung fehlgeschlagen",
        "update_install_failed": "Installationsprogramm konnte nicht geöffnet werden",
        "update_check_failed": "GitHub nicht erreichbar. Später erneut versuchen.",
    },
    "values-es": {
        "section_updates": "Actualizaciones",
        "update_check": "Buscar actualizaciones",
        "update_check_sub": "Buscar una versión más reciente en GitHub",
        "update_checking": "Buscando actualizaciones…",
        "update_latest": "Estás actualizado",
        "update_latest_sub": "Última versión: %1$s",
        "state_ok": "OK",
        "state_update": "ACTUALIZAR",
        "update_available": "Actualización disponible",
        "update_available_sub": "La versión %1$s está lista para instalar",
        "update_downloading": "Descargando…",
        "update_error": "Error al buscar actualizaciones",
        "update_no_apk": "Esta versión no tiene un APK descargable",
        "update_download_failed": "Falló la descarga o la verificación",
        "update_install_failed": "No se pudo abrir el instalador",
        "update_check_failed": "No se pudo acceder a GitHub. Inténtalo más tarde.",
    },
    "values-fr": {
        "section_updates": "Mises à jour",
        "update_check": "Rechercher des mises à jour",
        "update_check_sub": "Vérifier sur GitHub une version plus récente",
        "update_checking": "Recherche de mises à jour…",
        "update_latest": "Vous êtes à jour",
        "update_latest_sub": "Dernière version : %1$s",
        "state_ok": "OK",
        "state_update": "MISE À JOUR",
        "update_available": "Mise à jour disponible",
        "update_available_sub": "La version %1$s est prête à installer",
        "update_downloading": "Téléchargement…",
        "update_error": "Échec de la recherche de mise à jour",
        "update_no_apk": "Cette version n'a pas d'APK téléchargeable",
        "update_download_failed": "Échec du téléchargement ou de la vérification",
        "update_install_failed": "Impossible d'ouvrir l'installateur",
        "update_check_failed": "Impossible de joindre GitHub. Réessayez plus tard.",
    },
    "values-hi": {
        "section_updates": "अपडेट",
        "update_check": "अपडेट जाँचें",
        "update_check_sub": "GitHub पर नया संस्करण जाँचें",
        "update_checking": "अपडेट जाँच रहे हैं…",
        "update_latest": "आप अप-टू-डेट हैं",
        "update_latest_sub": "नवीनतम रिलीज़: %1$s",
        "state_ok": "ठीक है",
        "state_update": "अपडेट",
        "update_available": "अपडेट उपलब्ध है",
        "update_available_sub": "संस्करण %1$s इंस्टॉल करने के लिए तैयार है",
        "update_downloading": "डाउनलोड हो रहा है…",
        "update_error": "अपडेट जाँच विफल",
        "update_no_apk": "इस रिलीज़ में डाउनलोड करने योग्य APK नहीं है",
        "update_download_failed": "डाउनलोड या सत्यापन विफल",
        "update_install_failed": "इंस्टॉलर नहीं खोल सके",
        "update_check_failed": "GitHub तक नहीं पहुँच सके। बाद में पुनः प्रयास करें।",
    },
    "values-ja": {
        "section_updates": "アップデート",
        "update_check": "アップデートを確認",
        "update_check_sub": "GitHubで新しいバージョンを確認",
        "update_checking": "アップデートを確認中…",
        "update_latest": "最新です",
        "update_latest_sub": "最新リリース: %1$s",
        "state_ok": "OK",
        "state_update": "更新",
        "update_available": "アップデートがあります",
        "update_available_sub": "バージョン %1$s をインストールできます",
        "update_downloading": "ダウンロード中…",
        "update_error": "アップデート確認に失敗",
        "update_no_apk": "このリリースにはダウンロード可能なAPKがありません",
        "update_download_failed": "ダウンロードまたは検証に失敗",
        "update_install_failed": "インストーラーを開けませんでした",
        "update_check_failed": "GitHubに接続できません。後でもう一度お試しください。",
    },
    "values-pt": {
        "section_updates": "Atualizações",
        "update_check": "Verificar atualizações",
        "update_check_sub": "Verificar no GitHub uma versão mais recente",
        "update_checking": "Verificando atualizações…",
        "update_latest": "Você está atualizado",
        "update_latest_sub": "Última versão: %1$s",
        "state_ok": "OK",
        "state_update": "ATUALIZAR",
        "update_available": "Atualização disponível",
        "update_available_sub": "A versão %1$s está pronta para instalar",
        "update_downloading": "Baixando…",
        "update_error": "Falha ao verificar atualizações",
        "update_no_apk": "Esta versão não tem APK para download",
        "update_download_failed": "Falha no download ou na verificação",
        "update_install_failed": "Não foi possível abrir o instalador",
        "update_check_failed": "Não foi possível acessar o GitHub. Tente novamente mais tarde.",
    },
    "values-ru": {
        "section_updates": "Обновления",
        "update_check": "Проверить обновления",
        "update_check_sub": "Проверить GitHub на наличие новой версии",
        "update_checking": "Проверка обновлений…",
        "update_latest": "У вас актуальная версия",
        "update_latest_sub": "Последний релиз: %1$s",
        "state_ok": "ОК",
        "state_update": "ОБНОВИТЬ",
        "update_available": "Доступно обновление",
        "update_available_sub": "Версия %1$s готова к установке",
        "update_downloading": "Загрузка…",
        "update_error": "Не удалось проверить обновления",
        "update_no_apk": "В этом релизе нет загружаемого APK",
        "update_download_failed": "Ошибка загрузки или проверки",
        "update_install_failed": "Не удалось открыть установщик",
        "update_check_failed": "Не удалось связаться с GitHub. Повторите позже.",
    },
    "values-tr": {
        "section_updates": "Güncellemeler",
        "update_check": "Güncellemeleri kontrol et",
        "update_check_sub": "GitHub'da daha yeni bir sürüm ara",
        "update_checking": "Güncellemeler kontrol ediliyor…",
        "update_latest": "Güncel sürümdesiniz",
        "update_latest_sub": "Son sürüm: %1$s",
        "state_ok": "Tamam",
        "state_update": "GÜNCELLE",
        "update_available": "Güncelleme mevcut",
        "update_available_sub": "%1$s sürümü kuruluma hazır",
        "update_downloading": "İndiriliyor…",
        "update_error": "Güncelleme kontrolü başarısız",
        "update_no_apk": "Bu sürümde indirilebilir APK yok",
        "update_download_failed": "İndirme veya doğrulama başarısız",
        "update_install_failed": "Yükleyici açılamadı",
        "update_check_failed": "GitHub'a ulaşılamadı. Daha sonra tekrar deneyin.",
    },
    "values-zh-rCN": {
        "section_updates": "更新",
        "update_check": "检查更新",
        "update_check_sub": "在 GitHub 上检查是否有新版本",
        "update_checking": "正在检查更新…",
        "update_latest": "您已是最新版本",
        "update_latest_sub": "最新版本：%1$s",
        "state_ok": "确定",
        "state_update": "更新",
        "update_available": "发现新版本",
        "update_available_sub": "版本 %1$s 已准备好安装",
        "update_downloading": "正在下载…",
        "update_error": "检查更新失败",
        "update_no_apk": "此版本没有可下载的 APK",
        "update_download_failed": "下载或验证失败",
        "update_install_failed": "无法打开安装程序",
        "update_check_failed": "无法访问 GitHub。请稍后重试。",
    },
}


def update_file(path, new_map):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for key, value in new_map.items():
        if re.search(r'name="%s"' % re.escape(key), content):
            continue
        if "'" in value:
            snippet = '    <string name="%s">"%s"</string>\n' % (key, value)
        else:
            snippet = '    <string name="%s">%s</string>\n' % (key, value)
        content = content.replace("</resources>", snippet + "</resources>", 1)
        changed = True
    if not changed:
        return False
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    except Exception:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise
    return True


def main():
    total = 0
    base = os.path.join(ROOT, "feature/settings/src/main/res")
    for locale, translations in LOCALES.items():
        path = os.path.join(base, locale, "strings.xml")
        if not os.path.exists(path):
            print("SKIP missing: %s" % path)
            continue
        if update_file(path, translations):
            total += len(translations)
            print("UPDATED: %s" % path)
    print("TOTAL keys inserted: %d" % total)


if __name__ == "__main__":
    main()
