# -*- coding: utf-8 -*-
"""Appends step-16 strings (template editor) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["tpl_save", "tpl_letter", "tpl_briefing", "tpl_poster", "tpl_need_more", "tpl_fonts", "tpl_current_font",
        "tpl_format", "tpl_size_color", "tpl_replace", "tpl_select_format", "tpl_png_desc", "tpl_jpg_desc",
        "tpl_pdf_desc", "tpl_quit_title", "tpl_quit_message"]
T = {
"ar": ["حفظ","رسالة","موجز","ملصق","هل تحتاج إلى المزيد من القوالب؟","الخطوط","الخط الحالي","التنسيق","الحجم واللون","استبدال","اختر تنسيقًا","مناسب للملفات التي تحتوي على صور","مناسب للملفات الأصغر","مناسب لمعظم الملفات","الخروج الآن؟","لم تحفظ تغييراتك بعد. هل تريد حفظها الآن؟"],
"de": ["Speichern","Brief","Briefing","Poster","Brauchst du mehr Vorlagen?","Schriftarten","Aktuelle Schriftart","Format","Größe & Farbe","Ersetzen","Format auswählen","Geeignet für Dateien mit Bildern","Geeignet für kleinere Dateien","Geeignet für die meisten Dateien","Jetzt beenden?","Du hast deine Änderungen noch nicht gespeichert. Möchtest du sie jetzt speichern?"],
"es": ["Guardar","Carta","Informe","Póster","¿Necesitas más plantillas?","Fuentes","Fuente actual","Formato","Tamaño y color","Reemplazar","Selecciona un formato","Adecuado para archivos con imágenes","Adecuado para archivos más pequeños","Adecuado para la mayoría de archivos","¿Salir ahora?","Aún no has guardado los cambios. ¿Quieres guardarlos ahora?"],
"fa": ["ذخیره","نامه","گزارش","پوستر","قالب‌های بیشتری لازم دارید؟","فونت‌ها","فونت فعلی","قالب‌بندی","اندازه و رنگ","جایگزینی","یک قالب انتخاب کنید","مناسب برای فایل‌های دارای تصویر","مناسب برای فایل‌های کوچک‌تر","مناسب برای بیشتر فایل‌ها","اکنون خارج می‌شوید؟","هنوز تغییرات خود را ذخیره نکرده‌اید. می‌خواهید اکنون ذخیره کنید؟"],
"fr": ["Enregistrer","Lettre","Briefing","Affiche","Besoin de plus de modèles ?","Polices","Police actuelle","Format","Taille et couleur","Remplacer","Choisir un format","Adapté aux fichiers avec images","Adapté aux fichiers plus légers","Adapté à la plupart des fichiers","Quitter maintenant ?","Vous n'avez pas encore enregistré vos modifications. Voulez-vous les enregistrer maintenant ?"],
"in": ["Simpan","Surat","Ringkasan","Poster","Butuh template lainnya?","Font","Font saat ini","Format","Ukuran & Warna","Ganti","Pilih format","Cocok untuk file dengan gambar","Cocok untuk file yang lebih kecil","Cocok untuk sebagian besar file","Keluar sekarang?","Anda belum menyimpan perubahan. Simpan sekarang?"],
"it": ["Salva","Lettera","Briefing","Poster","Ti servono altri modelli?","Caratteri","Carattere attuale","Formato","Dimensione e colore","Sostituisci","Seleziona un formato","Adatto a file con immagini","Adatto a file più piccoli","Adatto alla maggior parte dei file","Uscire ora?","Non hai ancora salvato le modifiche. Vuoi salvarle adesso?"],
"ja": ["保存","レター","ブリーフィング","ポスター","もっとテンプレートが必要ですか？","フォント","現在のフォント","書式","サイズと色","置き換え","形式を選択","画像を含むファイルに最適","小さいファイルに最適","ほとんどのファイルに最適","終了しますか？","変更がまだ保存されていません。今すぐ保存しますか？"],
"ko": ["저장","편지","브리핑","포스터","더 많은 템플릿이 필요하신가요?","글꼴","현재 글꼴","서식","크기 및 색상","바꾸기","형식 선택","이미지가 있는 파일에 적합","작은 파일에 적합","대부분의 파일에 적합","지금 나가시겠어요?","변경 사항을 아직 저장하지 않았습니다. 지금 저장하시겠어요?"],
"ms": ["Simpan","Surat","Taklimat","Poster","Perlukan lebih banyak templat?","Fon","Fon semasa","Format","Saiz & Warna","Ganti","Pilih format","Sesuai untuk fail dengan imej","Sesuai untuk fail lebih kecil","Sesuai untuk kebanyakan fail","Keluar sekarang?","Anda belum menyimpan perubahan. Simpan sekarang?"],
"pt": ["Salvar","Carta","Resumo","Pôster","Precisa de mais modelos?","Fontes","Fonte atual","Formato","Tamanho e cor","Substituir","Selecione um formato","Ideal para arquivos com imagens","Ideal para arquivos menores","Ideal para a maioria dos arquivos","Sair agora?","Você ainda não salvou as alterações. Deseja salvá-las agora?"],
"ru": ["Сохранить","Письмо","Брифинг","Постер","Нужно больше шаблонов?","Шрифты","Текущий шрифт","Формат","Размер и цвет","Заменить","Выберите формат","Подходит для файлов с изображениями","Подходит для файлов меньшего размера","Подходит для большинства файлов","Выйти сейчас?","Вы ещё не сохранили изменения. Сохранить их сейчас?"],
"tr": ["Kaydet","Mektup","Brifing","Poster","Daha fazla şablon mu lazım?","Yazı tipleri","Geçerli yazı tipi","Biçim","Boyut ve Renk","Değiştir","Bir biçim seçin","Resimli dosyalar için uygun","Daha küçük dosyalar için uygun","Çoğu dosya için uygun","Şimdi çıkılsın mı?","Değişikliklerinizi henüz kaydetmediniz. Şimdi kaydetmek ister misiniz?"],
"vi": ["Lưu","Thư","Bản tóm tắt","Áp phích","Cần thêm mẫu?","Phông chữ","Phông chữ hiện tại","Định dạng","Kích thước & Màu","Thay thế","Chọn định dạng","Phù hợp với tệp có hình ảnh","Phù hợp với tệp nhỏ hơn","Phù hợp với hầu hết các tệp","Thoát ngay?","Bạn chưa lưu thay đổi. Bạn có muốn lưu ngay không?"],
"uz": ["Saqlash","Xat","Brifing","Poster","Ko'proq shablon kerakmi?","Shriftlar","Joriy shrift","Format","O'lcham va rang","Almashtirish","Formatni tanlang","Rasmli fayllar uchun mos","Kichikroq fayllar uchun mos","Ko'pchilik fayllar uchun mos","Hozir chiqasizmi?","O'zgarishlar hali saqlanmagan. Hozir saqlaysizmi?"],
"th": ["บันทึก","จดหมาย","สรุปการประชุม","โปสเตอร์","ต้องการเทมเพลตเพิ่มไหม","แบบอักษร","แบบอักษรปัจจุบัน","รูปแบบ","ขนาดและสี","แทนที่","เลือกรูปแบบไฟล์","เหมาะกับไฟล์ที่มีรูปภาพ","เหมาะกับไฟล์ขนาดเล็ก","เหมาะกับไฟล์ส่วนใหญ่","ออกตอนนี้ไหม","คุณยังไม่ได้บันทึกการเปลี่ยนแปลง ต้องการบันทึกตอนนี้ไหม"],
"uk": ["Зберегти","Лист","Брифінг","Постер","Потрібно більше шаблонів?","Шрифти","Поточний шрифт","Формат","Розмір і колір","Замінити","Виберіть формат","Підходить для файлів із зображеннями","Підходить для менших файлів","Підходить для більшості файлів","Вийти зараз?","Ви ще не зберегли зміни. Зберегти їх зараз?"],
"pl": ["Zapisz","List","Briefing","Plakat","Potrzebujesz więcej szablonów?","Czcionki","Bieżąca czcionka","Formatowanie","Rozmiar i kolor","Zamień","Wybierz format","Dla plików z obrazami","Dla mniejszych plików","Dla większości plików","Wyjść teraz?","Nie zapisano jeszcze zmian. Zapisać je teraz?"],
"tl": ["I-save","Liham","Briefing","Poster","Kailangan pa ng mga template?","Mga font","Kasalukuyang font","Format","Laki at Kulay","Palitan","Pumili ng format","Angkop sa mga file na may larawan","Angkop sa mas maliliit na file","Angkop sa karamihan ng file","Umalis na ngayon?","Hindi mo pa nase-save ang mga pagbabago. Gusto mo bang i-save ngayon?"],
"zh-rTW": ["儲存","信件","簡報","海報","需要更多範本嗎？","字型","目前字型","格式","大小與顏色","取代","選擇格式","適合含圖片的檔案","適合較小的檔案","適合大多數檔案","現在離開？","你還沒有儲存變更，要現在儲存嗎？"],
"ur": ["محفوظ کریں","خط","بریفنگ","پوسٹر","مزید ٹیمپلیٹس چاہئیں؟","فونٹس","موجودہ فونٹ","فارمیٹ","سائز اور رنگ","تبدیل کریں","فارمیٹ منتخب کریں","تصاویر والی فائلوں کے لیے موزوں","چھوٹی فائلوں کے لیے موزوں","زیادہ تر فائلوں کے لیے موزوں","ابھی باہر نکلیں؟","آپ نے ابھی تک اپنی تبدیلیاں محفوظ نہیں کیں۔ کیا انہیں ابھی محفوظ کرنا چاہتے ہیں؟"],
"zh-rCN": ["保存","信函","简报","海报","需要更多模板？","字体","当前字体","格式","大小和颜色","替换","选择格式","适合含图片的文件","适合较小的文件","适合大多数文件","现在退出？","你还没有保存更改，要现在保存吗？"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    assert len(vals) == len(KEYS), loc
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step16 -->.*?<!-- /step16 -->", "", s, flags=re.S)
    block = "\n    <!-- step16 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step16 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step16 translations:", len(T))
