# -*- coding: utf-8 -*-
"""Appends step-8 strings (Word to PDF picker/preview) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["select_a_file", "convert_to_word"]
T = {
"ar": ["اختر ملفًا","تحويل إلى Word"], "de": ["Datei auswählen","In Word umwandeln"], "es": ["Selecciona un archivo","Convertir a Word"],
"fa": ["یک فایل انتخاب کنید","تبدیل به Word"], "fr": ["Sélectionner un fichier","Convertir en Word"], "in": ["Pilih file","Konversi ke Word"],
"it": ["Seleziona un file","Converti in Word"], "ja": ["ファイルを選択","Wordに変換"], "ko": ["파일 선택","Word로 변환"],
"ms": ["Pilih fail","Tukar ke Word"], "pt": ["Selecione um arquivo","Converter para Word"], "ru": ["Выберите файл","Конвертировать в Word"],
"tr": ["Bir dosya seçin","Word'e dönüştür"], "vi": ["Chọn tệp","Chuyển sang Word"], "uz": ["Faylni tanlang","Word-ga aylantirish"],
"th": ["เลือกไฟล์","แปลงเป็น Word"], "uk": ["Виберіть файл","Конвертувати в Word"], "pl": ["Wybierz plik","Konwertuj do Word"],
"tl": ["Pumili ng file","I-convert sa Word"], "zh-rTW": ["選擇檔案","轉換為 Word"], "ur": ["فائل منتخب کریں","Word میں تبدیل کریں"],
"zh-rCN": ["选择文件","转换为 Word"],
}
def esc(s):
    out = s.replace("\\'", "\u0000").replace("'", "\\'").replace('"', '\\"')
    return out.replace("\u0000", "\\'")
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step8 -->.*?<!-- /step8 -->", "", s, flags=re.S)
    block = "\n    <!-- step8 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step8 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step8 translations:", len(T))
