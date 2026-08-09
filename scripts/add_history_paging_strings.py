"""Add history paging state strings (load error + retry) to all locales."""
import os

BASE = "feature/history/src/main/res"
NEW = {
    "history_load_error_title": {
        "values": "Couldn't load history",
        "values-ar": "تعذّر تحميل السجل",
        "values-de": "Verlauf konnte nicht geladen werden",
        "values-es": "No se pudo cargar el historial",
        "values-fr": "Impossible de charger l'historique",
        "values-hi": "इतिहास लोड नहीं हो सका",
        "values-ja": "履歴を読み込めませんでした",
        "values-pt": "Não foi possível carregar o histórico",
        "values-ru": "Не удалось загрузить историю",
        "values-tr": "Geçmiş yüklenemedi",
        "values-zh-rCN": "无法加载历史记录",
    },
    "history_load_error_subtitle": {
        "values": "Something went wrong while reading your history.",
        "values-ar": "حدث خطأ ما أثناء قراءة سجلك.",
        "values-de": "Beim Lesen des Verlaufs ist ein Fehler aufgetreten.",
        "values-es": "Se produjo un error al leer tu historial.",
        "values-fr": "Une erreur s'est produite lors de la lecture de l'historique.",
        "values-hi": "आपका इतिहास पढ़ते समय कुछ गड़बड़ हुई।",
        "values-ja": "履歴の読み取り中にエラーが発生しました。",
        "values-pt": "Ocorreu um erro ao ler o seu histórico.",
        "values-ru": "При чтении истории произошла ошибка.",
        "values-tr": "Geçmişiniz okunurken bir sorun oluştu.",
        "values-zh-rCN": "读取历史记录时出现问题。",
    },
    "history_retry": {
        "values": "Retry",
        "values-ar": "إعادة المحاولة",
        "values-de": "Erneut versuchen",
        "values-es": "Reintentar",
        "values-fr": "Réessayer",
        "values-hi": "पुनः प्रयास करें",
        "values-ja": "再試行",
        "values-pt": "Tentar novamente",
        "values-ru": "Повторить",
        "values-tr": "Yeniden dene",
        "values-zh-rCN": "重试",
    },
}

for loc, values in [
    ("values", NEW["history_load_error_title"]["values"]),
]:
    pass

for key, by_loc in NEW.items():
    for loc_dir in sorted(os.listdir(BASE)):
        path = os.path.join(BASE, loc_dir, "strings.xml")
        if not os.path.isfile(path) or loc_dir not in by_loc:
            continue
        text = open(path, encoding="utf-8").read()
        if f'name="{key}"' in text:
            print(f"skip (exists) {loc_dir}/{key}")
            continue
        # Escape apostrophes per project convention: wrap value in double quotes
        # when it contains a raw single quote.
        value = by_loc[loc_dir]
        if "'" in value and not (value.startswith('"') and value.endswith('"')):
            value = '"' + value + '"'
        entry = f'    <string name="{key}">{value}</string>\n'
        text = text.replace("</resources>", entry + "</resources>")
        open(path, "w", encoding="utf-8").write(text)
        print(f"added {loc_dir}/{key}")

print("DONE")
