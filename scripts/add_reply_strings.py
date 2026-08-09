"""Add localized notification reply-button strings (P2-1 RemoteInput)
across all 10 locales using safe atomic writes."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": {
        "notification_button_reply": "Reply with text",
        "notification_button_reply_title": "Reply variable",
        "notification_button_reply_hint": "Typing in the notification stores the text into this variable so the task can use it (e.g. %MyReply).",
        "notification_button_reply_label": "Variable name (without %)",
        "notification_button_reply_sub": "Writes reply to %s",
    },
    "values-ar": {
        "notification_button_reply": "رد بنص",
        "notification_button_reply_title": "متغير الرد",
        "notification_button_reply_hint": "تخزَّن الكتابة في الإشعار داخل هذا المتغير ليستخدمها المهمة (مثال %MyReply).",
        "notification_button_reply_label": "اسم المتغير (بدون %)",
        "notification_button_reply_sub": "يكتب الرد إلى %s",
    },
    "values-de": {
        "notification_button_reply": "Mit Text antworten",
        "notification_button_reply_title": "Antwortvariable",
        "notification_button_reply_hint": "Die Eingabe in der Benachrichtigung wird in dieser Variable gespeichert, damit die Aufgabe sie nutzen kann (z. B. %MyReply).",
        "notification_button_reply_label": "Variablenname (ohne %)",
        "notification_button_reply_sub": "Antwort wird nach %s geschrieben",
    },
    "values-es": {
        "notification_button_reply": "Responder con texto",
        "notification_button_reply_title": "Variable de respuesta",
        "notification_button_reply_hint": "Escribir en la notificación guarda el texto en esta variable para que la tarea pueda usarlo (p. ej. %MyReply).",
        "notification_button_reply_label": "Nombre de variable (sin %)",
        "notification_button_reply_sub": "Escribe la respuesta en %s",
    },
    "values-fr": {
        "notification_button_reply": "Répondre par un texte",
        "notification_button_reply_title": "Variable de réponse",
        "notification_button_reply_hint": "La saisie dans la notification est stockée dans cette variable pour que la tâche puisse l'utiliser (p. ex. %MyReply).",
        "notification_button_reply_label": "Nom de la variable (sans %)",
        "notification_button_reply_sub": "Écrit la réponse dans %s",
    },
    "values-hi": {
        "notification_button_reply": "टेक्स्ट से जवाब दें",
        "notification_button_reply_title": "जवाब वेरिएबल",
        "notification_button_reply_hint": "नोटिफ़िकेशन में टाइप किया गया टेक्स्ट इस वेरिएबल में सहेजा जाता है ताकि कार्य उसे इस्तेमाल कर सके (जैसे %MyReply)।",
        "notification_button_reply_label": "वेरिएबल नाम (% के बिना)",
        "notification_button_reply_sub": "%s में जवाब लिखता है",
    },
    "values-ja": {
        "notification_button_reply": "テキストで返信",
        "notification_button_reply_title": "返信変数",
        "notification_button_reply_hint": "通知に入力したテキストはこの変数に保存され、タスクから利用できます（例: %MyReply）。",
        "notification_button_reply_label": "変数名（%なし）",
        "notification_button_reply_sub": "返信を%sに書き込み",
    },
    "values-pt": {
        "notification_button_reply": "Responder com texto",
        "notification_button_reply_title": "Variável de resposta",
        "notification_button_reply_hint": "Digitar na notificação guarda o texto nesta variável para a tarefa usar (ex.: %MyReply).",
        "notification_button_reply_label": "Nome da variável (sem %)",
        "notification_button_reply_sub": "Grava a resposta em %s",
    },
    "values-ru": {
        "notification_button_reply": "Ответить текстом",
        "notification_button_reply_title": "Переменная ответа",
        "notification_button_reply_hint": "Ввод в уведомлении сохраняется в эту переменную, чтобы задача могла его использовать (например, %MyReply).",
        "notification_button_reply_label": "Имя переменной (без %)",
        "notification_button_reply_sub": "Записывает ответ в %s",
    },
    "values-tr": {
        "notification_button_reply": "Metinle yanıtla",
        "notification_button_reply_title": "Yanıt değişkeni",
        "notification_button_reply_hint": "Bildirimde yazılan metin bu değişkende saklanır ve görev onu kullanabilir (örn. %MyReply).",
        "notification_button_reply_label": "Değişken adı (% olmadan)",
        "notification_button_reply_sub": "Yanıtı %s değişkenine yazar",
    },
    "values-zh-rCN": {
        "notification_button_reply": "文字回复",
        "notification_button_reply_title": "回复变量",
        "notification_button_reply_hint": "在通知中输入的文本会存入此变量，供任务使用（例如 %MyReply）。",
        "notification_button_reply_label": "变量名（不含%）",
        "notification_button_reply_sub": "将回复写入%s",
    },
}


def update_file(path, new_map):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for key, value in new_map.items():
        if re.search(r'name="%s"' % re.escape(key), content):
            continue
        safe = value.replace("'", "\\'")
        snippet = '    <string name="%s">%s</string>\n' % (key, safe)
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
    base = os.path.join(ROOT, "feature/automation-builder/src/main/res")
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
