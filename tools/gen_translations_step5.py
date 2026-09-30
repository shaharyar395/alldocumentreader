# -*- coding: utf-8 -*-
"""Appends step-5 strings (import flow, viewer pills, pages, PDF edit) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["page_by_page","continuous_pages","view","extracted_successfully","saved_as","page_setup","page_for_preview",
        "apply_to_selected","all_pages_title","pen","highlight","eraser"]
T = {
"ar": ["صفحة بصفحة","صفحات متصلة","عرض","تم الاستخراج بنجاح","تم الحفظ باسم \"%s\".","إعداد الصفحة","هذه الصفحة للمعاينة","تطبيق على المحدد","كل الصفحات (%1$d/%2$d)","قلم","تمييز","ممحاة"],
"de": ["Seite für Seite","Fortlaufende Seiten","Ansehen","Erfolgreich extrahiert","Gespeichert als \"%s\".","Seite einrichten","Diese Seite dient zur Vorschau","Auf Auswahl anwenden","Alle Seiten (%1$d/%2$d)","Stift","Markieren","Radierer"],
"es": ["Página por página","Páginas continuas","Ver","Extraído correctamente","Guardado como \"%s\".","Configurar página","Esta página es una vista previa","Aplicar a la selección","Todas las páginas (%1$d/%2$d)","Lápiz","Resaltar","Borrador"],
"fa": ["صفحه به صفحه","صفحات پیوسته","مشاهده","با موفقیت استخراج شد","با نام \"%s\" ذخیره شد.","تنظیم صفحه","این صفحه برای پیش‌نمایش است","اعمال روی انتخاب‌شده‌ها","همه صفحات (%1$d/%2$d)","قلم","هایلایت","پاک‌کن"],
"fr": ["Page par page","Pages continues","Voir","Extrait avec succès","Enregistré sous \"%s\".","Mise en page","Cette page sert d'aperçu","Appliquer à la sélection","Toutes les pages (%1$d/%2$d)","Stylo","Surligner","Gomme"],
"in": ["Halaman per halaman","Halaman bersambung","Lihat","Berhasil diekstrak","Disimpan sebagai \"%s\".","Pengaturan halaman","Halaman ini untuk pratinjau","Terapkan ke yang dipilih","Semua halaman (%1$d/%2$d)","Pena","Sorot","Penghapus"],
"it": ["Pagina per pagina","Pagine continue","Visualizza","Estratto correttamente","Salvato come \"%s\".","Imposta pagina","Questa pagina è un'anteprima","Applica alla selezione","Tutte le pagine (%1$d/%2$d)","Penna","Evidenzia","Gomma"],
"ja": ["1ページずつ","連続ページ","表示","抽出しました","\"%s\" として保存しました。","ページ設定","このページはプレビューです","選択したページに適用","すべてのページ (%1$d/%2$d)","ペン","ハイライト","消しゴム"],
"ko": ["한 페이지씩","연속 페이지","보기","추출되었습니다","\"%s\"(으)로 저장했습니다.","페이지 설정","이 페이지는 미리보기입니다","선택 항목에 적용","모든 페이지 (%1$d/%2$d)","펜","형광펜","지우개"],
"ms": ["Halaman demi halaman","Halaman berterusan","Lihat","Berjaya diekstrak","Disimpan sebagai \"%s\".","Persediaan halaman","Halaman ini untuk pratonton","Guna pada yang dipilih","Semua halaman (%1$d/%2$d)","Pen","Serlah","Pemadam"],
"pt": ["Página por página","Páginas contínuas","Ver","Extraído com sucesso","Salvo como \"%s\".","Configurar página","Esta página é uma prévia","Aplicar à seleção","Todas as páginas (%1$d/%2$d)","Caneta","Destacar","Borracha"],
"ru": ["Постранично","Непрерывные страницы","Открыть","Успешно извлечено","Сохранено как \"%s\".","Параметры страницы","Эта страница для предпросмотра","Применить к выбранным","Все страницы (%1$d/%2$d)","Ручка","Выделение","Ластик"],
"tr": ["Sayfa sayfa","Sürekli sayfalar","Görüntüle","Başarıyla çıkarıldı","\"%s\" olarak kaydedildi.","Sayfa ayarı","Bu sayfa önizleme içindir","Seçilenlere uygula","Tüm sayfalar (%1$d/%2$d)","Kalem","Vurgula","Silgi"],
"vi": ["Từng trang","Trang liên tục","Xem","Trích xuất thành công","Đã lưu thành \"%s\".","Thiết lập trang","Trang này để xem trước","Áp dụng cho mục đã chọn","Tất cả trang (%1$d/%2$d)","Bút","Tô sáng","Tẩy"],
"uz": ["Sahifama-sahifa","Uzluksiz sahifalar","Ko\\'rish","Muvaffaqiyatli ajratildi","\"%s\" sifatida saqlandi.","Sahifa sozlamasi","Bu sahifa oldindan ko\\'rish uchun","Tanlanganlarga qo\\'llash","Barcha sahifalar (%1$d/%2$d)","Qalam","Belgilash","O\\'chirg\\'ich"],
"th": ["ทีละหน้า","หน้าต่อเนื่อง","ดู","แยกหน้าสำเร็จ","บันทึกเป็น \"%s\" แล้ว","ตั้งค่าหน้า","หน้านี้ใช้สำหรับแสดงตัวอย่าง","ใช้กับที่เลือก","ทุกหน้า (%1$d/%2$d)","ปากกา","ไฮไลต์","ยางลบ"],
"uk": ["Посторінково","Безперервні сторінки","Відкрити","Успішно витягнуто","Збережено як \"%s\".","Параметри сторінки","Ця сторінка для попереднього перегляду","Застосувати до вибраних","Усі сторінки (%1$d/%2$d)","Ручка","Виділення","Гумка"],
"pl": ["Strona po stronie","Strony ciągłe","Wyświetl","Wyodrębniono","Zapisano jako \"%s\".","Ustawienia strony","Ta strona służy do podglądu","Zastosuj do zaznaczonych","Wszystkie strony (%1$d/%2$d)","Pióro","Zakreślacz","Gumka"],
"tl": ["Pahina-pahina","Tuloy-tuloy na pahina","Tingnan","Matagumpay na na-extract","Na-save bilang \"%s\".","Page setup","Ang pahinang ito ay preview","Ilapat sa napili","Lahat ng pahina (%1$d/%2$d)","Panulat","I-highlight","Pambura"],
"zh-rTW": ["逐頁","連續頁面","檢視","擷取成功","已儲存為「%s」。","頁面設定","此頁僅供預覽","套用至所選頁面","所有頁面 (%1$d/%2$d)","畫筆","螢光筆","橡皮擦"],
"ur": ["صفحہ بہ صفحہ","مسلسل صفحات","دیکھیں","کامیابی سے نکالا گیا","\"%s\" کے نام سے محفوظ ہو گیا۔","صفحہ سیٹ اپ","یہ صفحہ پیش نظارہ کے لیے ہے","منتخب پر لاگو کریں","تمام صفحات (%1$d/%2$d)","قلم","نمایاں کریں","مٹانے والا"],
"zh-rCN": ["逐页","连续页面","查看","提取成功","已保存为“%s”。","页面设置","此页仅供预览","应用到所选页面","所有页面 (%1$d/%2$d)","画笔","荧光笔","橡皮擦"],
}
def esc(s):
    out = s.replace("\\'", "\u0000").replace("'", "\\'").replace('"', '\\"')
    return out.replace("\u0000", "\\'")
for loc, vals in T.items():
    assert len(vals) == len(KEYS), (loc, len(vals))
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step5 -->.*?<!-- /step5 -->", "", s, flags=re.S)
    block = "\n    <!-- step5 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step5 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step5 translations:", len(T))
