# -*- coding: utf-8 -*-
"""Step-28 strings: search hint per file type, refresh. Run from project root."""
import re
KEYS = ["search_in_document", "search_in_pdf", "search_in_ppt", "search_in_excel", "refresh", "files_refreshed"]
T = {
"ar": ["البحث في المستند","البحث في PDF","البحث في PPT","البحث في Excel","تحديث","تم تحديث الملفات"],
"de": ["Im Dokument suchen","Im PDF suchen","In PPT suchen","In Excel suchen","Aktualisieren","Dateien aktualisiert"],
"es": ["Buscar en el documento","Buscar en PDF","Buscar en PPT","Buscar en Excel","Actualizar","Archivos actualizados"],
"fa": ["جستجو در سند","جستجو در PDF","جستجو در PPT","جستجو در Excel","بازخوانی","فایل‌ها به‌روز شدند"],
"fr": ["Rechercher dans le document","Rechercher dans le PDF","Rechercher dans le PPT","Rechercher dans Excel","Actualiser","Fichiers mis à jour"],
"in": ["Cari di Dokumen","Cari di PDF","Cari di PPT","Cari di Excel","Muat ulang","File diperbarui"],
"it": ["Cerca nel documento","Cerca nel PDF","Cerca nel PPT","Cerca in Excel","Aggiorna","File aggiornati"],
"ja": ["ドキュメント内を検索","PDF内を検索","PPT内を検索","Excel内を検索","更新","ファイルを更新しました"],
"ko": ["문서에서 검색","PDF에서 검색","PPT에서 검색","Excel에서 검색","새로고침","파일이 업데이트되었습니다"],
"ms": ["Cari dalam Dokumen","Cari dalam PDF","Cari dalam PPT","Cari dalam Excel","Muat semula","Fail dikemas kini"],
"pt": ["Pesquisar no documento","Pesquisar no PDF","Pesquisar no PPT","Pesquisar no Excel","Atualizar","Arquivos atualizados"],
"ru": ["Поиск в документе","Поиск в PDF","Поиск в PPT","Поиск в Excel","Обновить","Файлы обновлены"],
"tr": ["Belgede ara","PDF'de ara","PPT'de ara","Excel'de ara","Yenile","Dosyalar güncellendi"],
"vi": ["Tìm trong tài liệu","Tìm trong PDF","Tìm trong PPT","Tìm trong Excel","Làm mới","Đã cập nhật tệp"],
"uz": ["Hujjatda qidirish","PDF da qidirish","PPT da qidirish","Excel da qidirish","Yangilash","Fayllar yangilandi"],
"th": ["ค้นหาในเอกสาร","ค้นหาใน PDF","ค้นหาใน PPT","ค้นหาใน Excel","รีเฟรช","อัปเดตไฟล์แล้ว"],
"uk": ["Пошук у документі","Пошук у PDF","Пошук у PPT","Пошук в Excel","Оновити","Файли оновлено"],
"pl": ["Szukaj w dokumencie","Szukaj w PDF","Szukaj w PPT","Szukaj w Excelu","Odśwież","Pliki zaktualizowane"],
"tl": ["Maghanap sa Dokumento","Maghanap sa PDF","Maghanap sa PPT","Maghanap sa Excel","I-refresh","Na-update ang mga file"],
"zh-rTW": ["在文件中搜尋","在 PDF 中搜尋","在 PPT 中搜尋","在 Excel 中搜尋","重新整理","檔案已更新"],
"ur": ["دستاویز میں تلاش کریں","PDF میں تلاش کریں","PPT میں تلاش کریں","Excel میں تلاش کریں","ریفریش","فائلیں اپ ڈیٹ ہو گئیں"],
"zh-rCN": ["在文档中搜索","在 PDF 中搜索","在 PPT 中搜索","在 Excel 中搜索","刷新","文件已更新"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step28 -->.*?<!-- /step28 -->", "", s, flags=re.S)
    for k in KEYS:  # drop older copies of these keys
        s = re.sub(rf'\n    <string name="{k}">.*?</string>', "", s)
    block = "\n    <!-- step28 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step28 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step28 translations:", len(T))
