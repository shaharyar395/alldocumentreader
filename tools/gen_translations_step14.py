# -*- coding: utf-8 -*-
"""Appends step-14 strings (Save to Google Drive) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["save_to_drive", "drive_off", "drive_option_off"]
T = {
"ar": ["الحفظ في Google Drive","إيقاف","إيقاف"], "de": ["In Google Drive speichern","Aus","Aus"],
"es": ["Guardar en Google Drive","Desactivado","Desactivado"], "fa": ["ذخیره در Google Drive","خاموش","خاموش"],
"fr": ["Enregistrer dans Google Drive","Désactivé","Désactivé"], "in": ["Simpan ke Google Drive","Nonaktif","Nonaktif"],
"it": ["Salva su Google Drive","Disattivato","Disattivato"], "ja": ["Google ドライブに保存","オフ","オフ"],
"ko": ["Google 드라이브에 저장","끄기","끄기"], "ms": ["Simpan ke Google Drive","Mati","Mati"],
"pt": ["Salvar no Google Drive","Desativado","Desativado"], "ru": ["Сохранять в Google Диск","Выкл.","Выкл."],
"tr": ["Google Drive'a kaydet","Kapalı","Kapalı"], "vi": ["Lưu vào Google Drive","Tắt","Tắt"],
"uz": ["Google Drive'ga saqlash","O'chiq","O'chiq"], "th": ["บันทึกลง Google ไดรฟ์","ปิด","ปิด"],
"uk": ["Зберігати на Google Диск","Вимк.","Вимк."], "pl": ["Zapisuj na Dysku Google","Wył.","Wył."],
"tl": ["I-save sa Google Drive","Naka-off","Naka-off"], "zh-rTW": ["儲存到 Google 雲端硬碟","關閉","關閉"],
"ur": ["Google Drive میں محفوظ کریں","بند","بند"], "zh-rCN": ["保存到 Google 云端硬盘","关闭","关闭"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step14 -->.*?<!-- /step14 -->", "", s, flags=re.S)
    block = "\n    <!-- step14 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step14 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step14 translations:", len(T))
