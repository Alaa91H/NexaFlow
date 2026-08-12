# -*- coding: utf-8 -*-
"""Adds the new feature strings to every locale except values/ (English)."""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> { locale -> translation }
TRANSLATIONS = {
    "battery_level_section": {
        "ar": "\u0645\u0633\u062a\u0648\u0649 \u0627\u0644\u0628\u0637\u0627\u0631\u064a\u0629",
        "de": "Akkuladestand",
        "es": "Nivel de bater\u00eda",
        "fr": "Niveau de batterie",
        "hi": "\u092c\u0948\u091f\u0930\u0940 \u0938\u094d\u0924\u0930",
        "ja": "\u30d0\u30c3\u30c6\u30ea\u30fc\u6b8b\u91cf",
        "pt": "N\u00edvel da bateria",
        "ru": "\u0423\u0440\u043e\u0432\u0435\u043d\u044c \u0437\u0430\u0440\u044f\u0434\u0430",
        "tr": "Pil seviyesi",
        "zh-rCN": "\u7535\u6c60\u7535\u91cf",
    },
    "charging_state_section": {
        "ar": "\u062d\u0627\u0644\u0629 \u0627\u0644\u0634\u062d\u0646",
        "de": "Ladestatus",
        "es": "Estado de carga",
        "fr": "\u00c9tat de charge",
        "hi": "\u091a\u093e\u0930\u094d\u091c\u093f\u0902\u0917 \u0938\u094d\u0925\u093f\u0924\u093f",
        "ja": "\u5145\u96fb\u72b6\u614b",
        "pt": "Estado de carregamento",
        "ru": "\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 \u0437\u0430\u0440\u044f\u0434\u043a\u0438",
        "tr": "\u015earj durumu",
        "zh-rCN": "\u5145\u7535\u72b6\u6001",
    },
    "charging_any": {
        "ar": "\u0623\u064a",
        "de": "Beliebig",
        "es": "Cualquiera",
        "fr": "Tous",
        "hi": "\u0915\u094b\u0908 \u092d\u0940",
        "ja": "\u3059\u3079\u3066",
        "pt": "Qualquer",
        "ru": "\u041b\u044e\u0431\u043e\u0435",
        "tr": "Herhangi biri",
        "zh-rCN": "\u4efb\u610f",
    },
    "charging_yes": {
        "ar": "\u064a\u0634\u062d\u0646",
        "de": "Wird geladen",
        "es": "Cargando",
        "fr": "En charge",
        "hi": "\u091a\u093e\u0930\u094d\u091c \u0939\u094b \u0930\u0939\u093e \u0939\u0948",
        "ja": "\u5145\u96fb\u4e2d",
        "pt": "A carregar",
        "ru": "\u0417\u0430\u0440\u044f\u0436\u0430\u0435\u0442\u0441\u044f",
        "tr": "\u015earj oluyor",
        "zh-rCN": "\u5145\u7535\u4e2d",
    },
    "charging_no": {
        "ar": "\u0644\u0627 \u064a\u0634\u062d\u0646",
        "de": "Wird nicht geladen",
        "es": "No cargando",
        "fr": "Pas en charge",
        "hi": "\u091a\u093e\u0930\u094d\u091c \u0928\u0939\u0940\u0902 \u0939\u094b \u0930\u0939\u093e",
        "ja": "\u5145\u96fb\u306a\u3057",
        "pt": "Sem carregar",
        "ru": "\u041d\u0435 \u0437\u0430\u0440\u044f\u0436\u0430\u0435\u0442\u0441\u044f",
        "tr": "\u015earj olmuyor",
        "zh-rCN": "\u672a\u5145\u7535",
    },
    "action_network_mode": {
        "ar": "\u0648\u0636\u0639 \u0627\u0644\u0634\u0628\u0643\u0629",
        "de": "Netzwerkmodus",
        "es": "Modo de red",
        "fr": "Mode r\u00e9seau",
        "hi": "\u0928\u0947\u091f\u0935\u0930\u094d\u0915 \u092e\u094b\u0921",
        "ja": "\u30cd\u30c3\u30c8\u30ef\u30fc\u30af\u30e2\u30fc\u30c9",
        "pt": "Modo de rede",
        "ru": "\u0420\u0435\u0436\u0438\u043c \u0441\u0435\u0442\u0438",
        "tr": "A\u011f modu",
        "zh-rCN": "\u7f51\u7edc\u6a21\u5f0f",
    },
    "action_network_mode_sub": {
        "ar": "\u0641\u0631\u0636 2G \u0623\u0648 3G \u0623\u0648 4G \u0623\u0648 5G \u0623\u0648 \u062a\u0644\u0642\u0627\u0626\u064a",
        "de": "2G, 3G, 4G, 5G oder automatisch erzwingen",
        "es": "Forzar 2G, 3G, 4G, 5G o autom\u00e1tico",
        "fr": "Forcer 2G, 3G, 4G, 5G ou automatique",
        "hi": "2G, 3G, 4G, 5G \u092f\u093e \u0911\u091f\u094b \u092a\u0930 \u092c\u0932 \u0926\u0947\u0902",
        "ja": "2G/3G/4G/5G\u307e\u305f\u306f\u81ea\u52d5\u3092\u5f37\u5236",
        "pt": "For\u00e7ar 2G, 3G, 4G, 5G ou autom\u00e1tico",
        "ru": "\u041f\u0440\u0438\u043d\u0443\u0434\u0438\u0442\u0435\u043b\u044c\u043d\u043e 2G, 3G, 4G, 5G \u0438\u043b\u0438 \u0430\u0432\u0442\u043e",
        "tr": "2G, 3G, 4G, 5G veya otomatik zorla",
        "zh-rCN": "\u5f3a\u5236 2G\u30013G\u30014G\u30015G \u6216\u81ea\u52a8",
    },
    "action_set_ringtone": {
        "ar": "\u062a\u0639\u064a\u064a\u0646 \u0627\u0644\u0646\u063a\u0645\u0629",
        "de": "Klingelton festlegen",
        "es": "Establecer tono",
        "fr": "D\u00e9finir la sonnerie",
        "hi": "\u0930\u093f\u0902\u0917\u091f\u094b\u0928 \u0938\u0947\u091f \u0915\u0930\u0947\u0902",
        "ja": "\u7740\u4fe1\u97f3\u3092\u8a2d\u5b9a",
        "pt": "Definir toque",
        "ru": "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0440\u0438\u043d\u0433\u0442\u043e\u043d",
        "tr": "Zil sesini ayarla",
        "zh-rCN": "\u8bbe\u7f6e\u94c3\u58f0",
    },
    "action_set_ringtone_sub": {
        "ar": "\u0627\u062e\u062a\u0631 \u0646\u063a\u0645\u0629 \u0627\u0644\u0631\u0646\u064a\u0646 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629 \u0644\u0644\u0645\u0643\u0627\u0644\u0645\u0627\u062a",
        "de": "Standard-Klingelton ausw\u00e4hlen",
        "es": "Elegir el tono de llamada predeterminado",
        "fr": "Choisir la sonnerie d\u2019appel par d\u00e9faut",
        "hi": "\u0921\u093f\u092b\u093c\u0949\u0932\u094d\u091f \u0915\u0949\u0932 \u0930\u093f\u0902\u0917\u091f\u094b\u0928 \u091a\u0941\u0928\u0947\u0902",
        "ja": "\u30c7\u30d5\u30a9\u30eb\u30c8\u306e\u7740\u4fe1\u97f3\u3092\u9078\u629e",
        "pt": "Escolher o toque de chamada padr\u00e3o",
        "ru": "\u0412\u044b\u0431\u0440\u0430\u0442\u044c \u0440\u0438\u043d\u0433\u0442\u043e\u043d \u0437\u0432\u043e\u043d\u043a\u043e\u0432 \u043f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e",
        "tr": "Varsay\u0131lan arama zil sesini se\u00e7",
        "zh-rCN": "\u9009\u62e9\u9ed8\u8ba4\u6765\u7535\u94c3\u58f0",
    },
    "network_hotspot": {
        "ar": "\u0646\u0642\u0637\u0629 \u0627\u0644\u0627\u062a\u0635\u0627\u0644",
        "de": "Hotspot",
        "es": "Punto de acceso",
        "fr": "Point d\u2019acc\u00e8s",
        "hi": "\u0939\u0949\u091f\u0938\u094d\u092a\u0949\u091f",
        "ja": "\u30db\u30c3\u30c8\u30b9\u30dd\u30c3\u30c8",
        "pt": "Ponto de acesso",
        "ru": "\u0422\u043e\u0447\u043a\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u0430",
        "tr": "Etkin nokta",
        "zh-rCN": "\u70ed\u70b9",
    },
    "network_mode": {
        "ar": "\u0648\u0636\u0639 \u0627\u0644\u0634\u0628\u0643\u0629",
        "de": "Netzwerkmodus",
        "es": "Modo de red",
        "fr": "Mode r\u00e9seau",
        "hi": "\u0928\u0947\u091f\u0935\u0930\u094d\u0915 \u092e\u094b\u0921",
        "ja": "\u30cd\u30c3\u30c8\u30ef\u30fc\u30af\u30e2\u30fc\u30c9",
        "pt": "Modo de rede",
        "ru": "\u0420\u0435\u0436\u0438\u043c \u0441\u0435\u0442\u0438",
        "tr": "A\u011f modu",
        "zh-rCN": "\u7f51\u7edc\u6a21\u5f0f",
    },
    "network_mode_label": {
        "ar": "\u062c\u064a\u0644 \u0627\u0644\u0634\u0628\u0643\u0629 \u0627\u0644\u0645\u0641\u0636\u0644",
        "de": "Bevorzugte Netzwerkgeneration",
        "es": "Generaci\u00f3n de red preferida",
        "fr": "G\u00e9n\u00e9ration r\u00e9seau pr\u00e9f\u00e9r\u00e9e",
        "hi": "\u092a\u0938\u0902\u0926\u0940\u0926\u093e \u0928\u0947\u091f\u0935\u0930\u094d\u0915 \u092a\u0940\u0922\u093c\u0940",
        "ja": "\u512a\u5148\u30cd\u30c3\u30c8\u30ef\u30fc\u30af\u4e16\u4ee3",
        "pt": "Gera\u00e7\u00e3o de rede preferida",
        "ru": "\u041f\u0440\u0435\u0434\u043f\u043e\u0447\u0438\u0442\u0430\u0435\u043c\u043e\u0435 \u043f\u043e\u043a\u043e\u043b\u0435\u043d\u0438\u0435 \u0441\u0435\u0442\u0438",
        "tr": "Tercih edilen a\u011f nesli",
        "zh-rCN": "\u9996\u9009\u7f51\u7edc\u5236\u5f0f",
    },
    "network_mode_sub": {
        "ar": "\u064a\u062a\u0637\u0644\u0628 \u0635\u0644\u0627\u062d\u064a\u0627\u062a root \u0623\u0648 Shizuku \u0623\u0648 \u0635\u0644\u0627\u062d\u064a\u0627\u062a \u0627\u0644\u0646\u0638\u0627\u0645\u061b \u0627\u0644\u0623\u062c\u0647\u0632\u0629 \u0627\u0644\u062a\u064a \u0644\u0627 \u062a\u062f\u0639\u0645 \u0627\u0644\u062c\u064a\u0644 \u062a\u064f\u0628\u0642\u064a \u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062d\u0627\u0644\u064a",
        "de": "Erfordert Root, Shizuku oder Systemrechte; Ger\u00e4te ohne passendes Funkmodul behalten den aktuellen Modus",
        "es": "Requiere root, Shizuku o privilegios de sistema; los dispositivos sin radio compatible mantienen el modo actual",
        "fr": "N\u00e9cessite root, Shizuku ou des privil\u00e8ges syst\u00e8me ; les appareils sans radio compatible conservent le mode actuel",
        "hi": "\u0930\u0942\u091f, \u0936\u093f\u091c\u0941\u0915\u0941 \u092f\u093e \u0938\u093f\u0938\u094d\u091f\u092e \u0935\u093f\u0936\u0947\u0937\u093e\u0927\u093f\u0915\u093e\u0930 \u091a\u093e\u0939\u093f\u090f; \u092c\u093f\u0928\u093e \u0938\u0902\u0917\u0924 \u0930\u0947\u0921\u093f\u092f\u094b \u0935\u093e\u0932\u0947 \u0909\u092a\u0915\u0930\u0923 \u0935\u0930\u094d\u0924\u092e\u093e\u0928 \u092e\u094b\u0921 \u092c\u0928\u093e\u090f \u0930\u0916\u0924\u0947 \u0939\u0948\u0902",
        "ja": "root\u3001Shizuku\u307e\u305f\u306f\u30b7\u30b9\u30c6\u30e0\u6a29\u9650\u304c\u5fc5\u8981\u3067\u3059\u3002\u5bfe\u5fdc\u3057\u3066\u3044\u306a\u3044\u7aef\u672b\u306f\u73fe\u5728\u306e\u30e2\u30fc\u30c9\u3092\u7dad\u6301\u3057\u307e\u3059",
        "pt": "Requer root, Shizuku ou privil\u00e9gios de sistema; dispositivos sem r\u00e1dio compat\u00edvel mant\u00eam o modo atual",
        "ru": "\u0422\u0440\u0435\u0431\u0443\u044e\u0442\u0441\u044f root, Shizuku \u0438\u043b\u0438 \u0441\u0438\u0441\u0442\u0435\u043c\u043d\u044b\u0435 \u043f\u0440\u0430\u0432\u0430; \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0430 \u0431\u0435\u0437 \u0441\u043e\u0432\u043c\u0435\u0441\u0442\u0438\u043c\u043e\u0433\u043e \u0440\u0430\u0434\u0438\u043e \u0441\u043e\u0445\u0440\u0430\u043d\u044f\u044e\u0442 \u0442\u0435\u043a\u0443\u0449\u0438\u0439 \u0440\u0435\u0436\u0438\u043c",
        "tr": "root, Shizuku veya sistem ayr\u0131cal\u0131klar\u0131 gerektirir; uyumlu radyosu olmayan cihazlar ge\u00e7erli modu korur",
        "zh-rCN": "\u9700\u8981 root\u3001Shizuku \u6216\u7cfb\u7edf\u6743\u9650\uff1b\u4e0d\u652f\u6301\u76f8\u5e94\u5236\u5f0f\u7684\u8bbe\u5907\u5c06\u4fdd\u6301\u5f53\u524d\u6a21\u5f0f",
    },
    "network_mode_auto": {
        "ar": "\u062a\u0644\u0642\u0627\u0626\u064a",
        "de": "Automatisch",
        "es": "Autom\u00e1tico",
        "fr": "Automatique",
        "hi": "\u0911\u091f\u094b",
        "ja": "\u81ea\u52d5",
        "pt": "Autom\u00e1tico",
        "ru": "\u0410\u0432\u0442\u043e",
        "tr": "Otomatik",
        "zh-rCN": "\u81ea\u52a8",
    },
    "network_mode_2g": {"ar": "2G", "de": "2G", "es": "2G", "fr": "2G", "hi": "2G", "ja": "2G", "pt": "2G", "ru": "2G", "tr": "2G", "zh-rCN": "2G"},
    "network_mode_3g": {"ar": "3G", "de": "3G", "es": "3G", "fr": "3G", "hi": "3G", "ja": "3G", "pt": "3G", "ru": "3G", "tr": "3G", "zh-rCN": "3G"},
    "network_mode_4g": {"ar": "4G", "de": "4G", "es": "4G", "fr": "4G", "hi": "4G", "ja": "4G", "pt": "4G", "ru": "4G", "tr": "4G", "zh-rCN": "4G"},
    "network_mode_5g": {"ar": "5G", "de": "5G", "es": "5G", "fr": "5G", "hi": "5G", "ja": "5G", "pt": "5G", "ru": "5G", "tr": "5G", "zh-rCN": "5G"},
    "state_on": {
        "ar": "\u062a\u0634\u063a\u064a\u0644",
        "de": "Ein",
        "es": "Activado",
        "fr": "Activ\u00e9",
        "hi": "\u091a\u093e\u0932\u0942",
        "ja": "\u30aa\u30f3",
        "pt": "Ligado",
        "ru": "\u0412\u043a\u043b",
        "tr": "A\u00e7\u0131k",
        "zh-rCN": "\u5f00\u542f",
    },
    "state_off": {
        "ar": "\u0625\u064a\u0642\u0627\u0641",
        "de": "Aus",
        "es": "Desactivado",
        "fr": "D\u00e9sactiv\u00e9",
        "hi": "\u092c\u0902\u0926",
        "ja": "\u30aa\u30d5",
        "pt": "Desligado",
        "ru": "\u0412\u044b\u043a\u043b",
        "tr": "Kapal\u0131",
        "zh-rCN": "\u5173\u95ed",
    },
    "ringtone_label": {
        "ar": "\u0646\u063a\u0645\u0629 \u0627\u0644\u0631\u0646\u064a\u0646 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629",
        "de": "Standard-Klingelton",
        "es": "Tono predeterminado",
        "fr": "Sonnerie par d\u00e9faut",
        "hi": "\u0921\u093f\u092b\u093c\u0949\u0932\u094d\u091f \u0930\u093f\u0902\u0917\u091f\u094b\u0646",
        "ja": "\u30c7\u30d5\u30a9\u30eb\u30c8\u306e\u7740\u4fe1\u97f3",
        "pt": "Toque padr\u00e3o",
        "ru": "\u0420\u0438\u043d\u0433\u0442\u043e\u043d \u043f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e",
        "tr": "Varsay\u0131lan zil sesi",
        "zh-rCN": "\u9ed8\u8ba4\u94c3\u58f0",
    },
    "choose_ringtone": {
        "ar": "\u0627\u062e\u062a\u0631 \u0627\u0644\u0646\u063a\u0645\u0629",
        "de": "Klingelton ausw\u00e4hlen",
        "es": "Elegir tono",
        "fr": "Choisir la sonnerie",
        "hi": "\u0930\u093f\u0902\u0917\u091f\u094b\u0928 \u091a\u0941\u0928\u0947\u0902",
        "ja": "\u7740\u4fe1\u97f3\u3092\u9078\u629e",
        "pt": "Escolher toque",
        "ru": "\u0412\u044b\u0431\u0440\u0430\u0442\u044c \u0440\u0438\u043d\u0433\u0442\u043e\u043d",
        "tr": "Zil sesi se\u00e7",
        "zh-rCN": "\u9009\u62e9\u94c3\u58f0",
    },
    "backup_save": {
        "ar": "\u062d\u0641\u0638 \u0641\u064a \u0645\u0644\u0641",
        "de": "In Datei speichern",
        "es": "Guardar en archivo",
        "fr": "Enregistrer dans un fichier",
        "hi": "\u092b\u093c\u093e\u0907\u0932 \u092e\u0947\u0902 \u0938\u0939\u0947\u091c\u0947\u0902",
        "ja": "\u30d5\u30a1\u30a4\u30eb\u306b\u4fdd\u5b58",
        "pt": "Guardar em ficheiro",
        "ru": "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0432 \u0444\u0430\u0439\u043b",
        "tr": "Dosyaya kaydet",
        "zh-rCN": "\u4fdd\u5b58\u5230\u6587\u4ef6",
    },
    "backup_save_sub": {
        "ar": "\u0627\u0643\u062a\u0628 \u0627\u0644\u0646\u0633\u062e\u0629 \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a\u0629 \u0625\u0644\u0649 \u0645\u0644\u0641 \u0645\u062d\u0644\u064a",
        "de": "Backup in einer lokalen Datei speichern",
        "es": "Escribir la copia de seguridad en un archivo local",
        "fr": "\u00c9crire la sauvegarde dans un fichier local",
        "hi": "\u092c\u0948\u0915\u0905\u092a \u0915\u094b \u0938\u094d\u0925\u0627\u0928\u0940\u092f \u092b\u093c\u093e\u0907\u0932 \u092e\u0947\u0902 \u0932\u093f\u0916\u0947\u0902",
        "ja": "\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u3092\u30ed\u30fc\u30ab\u30eb\u30d5\u30a1\u30a4\u30eb\u306b\u4fdd\u5b58",
        "pt": "Escrever a c\u00f3pia de seguran\u00e7a num ficheiro local",
        "ru": "\u0417\u0430\u043f\u0438\u0441\u0430\u0442\u044c \u0440\u0435\u0437\u0435\u0440\u0432\u043d\u0443\u044e \u043a\u043e\u043f\u0438\u044e \u0432 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 \u0444\u0430\u0439\u043b",
        "tr": "Yede\u011fi yerel bir dosyaya yaz",
        "zh-rCN": "\u5c06\u5907\u4efd\u5199\u5165\u672c\u5730\u6587\u4ef6",
    },
    "backup_saved": {
        "ar": "\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0646\u0633\u062e\u0629 \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a\u0629",
        "de": "Backup gespeichert",
        "es": "Copia de seguridad guardada",
        "fr": "Sauvegarde enregistr\u00e9e",
        "hi": "\u092c\u0948\u0915\u0905\u092a \u0938\u0939\u0947\u091c \u0917\u092f\u093e",
        "ja": "\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u3092\u4fdd\u5b58\u3057\u307e\u3057\u305f",
        "pt": "C\u00f3pia de seguran\u00e7a guardada",
        "ru": "\u0420\u0435\u0437\u0435\u0440\u0432\u043d\u0430\u044f \u043a\u043e\u043f\u0438\u044f \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0430",
        "tr": "Yedek kaydedildi",
        "zh-rCN": "\u5907\u4efd\u5df2\u4fdd\u5b58",
    },
    "backup_save_failed": {
        "ar": "\u062a\u0639\u0630\u0631 \u062d\u0641\u0638 \u0645\u0644\u0641 \u0627\u0644\u0646\u0633\u062e\u0629 \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a\u0629",
        "de": "Backup konnte nicht gespeichert werden",
        "es": "No se pudo guardar la copia de seguridad",
        "fr": "Impossible d\u2019enregistrer la sauvegarde",
        "hi": "\u092c\u0948\u0915\u0905\u092a \u092b\u093c\u093e\u0907\u0932 \u0938\u0939\u0947\u091c\u0940 \u0928\u0939\u0940\u0902 \u091c\u093e \u0938\u0915\u0940",
        "ja": "\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u30d5\u30a1\u30a4\u30eb\u3092\u4fdd\u5b58\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f",
        "pt": "N\u00e3o foi poss\u00edvel guardar a c\u00f3pia de seguran\u00e7a",
        "ru": "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0444\u0430\u0439\u043b \u0440\u0435\u0437\u0435\u0440\u0432\u043d\u043e\u0439 \u043a\u043e\u043f\u0438\u0438",
        "tr": "Yedek dosyas\u0131 kaydedilemedi",
        "zh-rCN": "\u65e0\u6cd5\u4fdd\u5b58\u5907\u4efd\u6587\u4ef6",
    },
}

# Which module each key lives in (to target the right strings.xml files).
BUILDER_KEYS = {
    "battery_level_section", "charging_state_section", "charging_any", "charging_yes", "charging_no",
    "action_network_mode", "action_network_mode_sub", "action_set_ringtone", "action_set_ringtone_sub",
    "network_hotspot", "network_mode", "network_mode_label", "network_mode_sub",
    "network_mode_auto", "network_mode_2g", "network_mode_3g", "network_mode_4g", "network_mode_5g",
    "state_on", "state_off", "ringtone_label", "choose_ringtone",
}
SETTINGS_KEYS = {"backup_save", "backup_save_sub", "backup_saved", "backup_save_failed"}

AUTOMATIONS_KEYS = {
    "action_network_mode", "action_network_mode_sub",
    "action_set_ringtone", "action_set_ringtone_sub",
}

MODULES = {
    "feature/automation-builder": BUILDER_KEYS,
    "feature/automations": AUTOMATIONS_KEYS,
    "feature/settings": SETTINGS_KEYS,
}

APOS = re.compile("(?<!\\\\)'")


def escape_apostrophes(text):
    """Android string XML escapes single quotes as \\'."""
    return APOS.sub("\\'", text)


def add_strings(path, keys, locale):
    with io.open(path, "r", encoding="utf-8") as fh:
        content = fh.read()
    existing = set(re.findall(r'<string name="([^"]+)">', content))
    lines = []
    added = 0
    for key in keys:
        if key in existing:
            continue
        if key not in TRANSLATIONS:
            print("WARN: no translation for %s" % key)
            continue
        value = TRANSLATIONS[key].get(locale)
        if value is None:
            print("WARN: no %s translation for %s" % (locale, key))
            continue
        value = escape_apostrophes(value)
        lines.append('    <string name="%s">%s</string>' % (key, value))
        added += 1
    if not lines:
        return 0
    insert = "\n" + "\n".join(lines) + "\n"
    content = content.replace("</resources>", insert + "</resources>", 1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(content)
    return added


total = 0
for module, keys in MODULES.items():
    base = os.path.join(ROOT, module, "src", "main", "res")
    for entry in sorted(os.listdir(base)):
        if not entry.startswith("values-"):
            continue
        locale = entry[len("values-"):]
        path = os.path.join(base, entry, "strings.xml")
        if not os.path.exists(path):
            continue
        added = add_strings(path, keys, locale)
        if added:
            print("%s/%s: +%d" % (module, entry, added))
            total += added
print("TOTAL added: %d" % total)
