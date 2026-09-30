# -*- coding: utf-8 -*-
"""Appends step-4 strings (Create files / Convert format) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["create_files","import_files","use_templates","convert_format","image_to_pdf","scan_to_pdf","word_to_pdf","ppt_to_pdf",
        "select_template","file_name","create","convert_to_pdf","converting","converted_successfully","color_inversion_on","color_inversion_off"]
T = {
"ar": ["إنشاء ملفات","استيراد ملفات","استخدام القوالب","تحويل الصيغة","صورة إلى PDF","مسح إلى PDF","Word إلى PDF","PPT إلى PDF","اختر قالبًا","اسم الملف","إنشاء","تحويل إلى PDF","جارٍ التحويل…","تم التحويل بنجاح","عكس الألوان: تشغيل","عكس الألوان: إيقاف"],
"de": ["Dateien erstellen","Dateien importieren","Vorlagen verwenden","Format konvertieren","Bild zu PDF","Scan zu PDF","Word zu PDF","PPT zu PDF","Vorlage auswählen","Dateiname","Erstellen","In PDF umwandeln","Wird konvertiert…","Erfolgreich konvertiert","Farbumkehr: Ein","Farbumkehr: Aus"],
"es": ["Crear archivos","Importar archivos","Usar plantillas","Convertir formato","Imagen a PDF","Escanear a PDF","Word a PDF","PPT a PDF","Selecciona una plantilla","Nombre del archivo","Crear","Convertir a PDF","Convirtiendo…","Convertido correctamente","Inversión de color: activada","Inversión de color: desactivada"],
"fa": ["ایجاد فایل","وارد کردن فایل","استفاده از قالب","تبدیل قالب","تصویر به PDF","اسکن به PDF","Word به PDF","PPT به PDF","یک قالب انتخاب کنید","نام فایل","ایجاد","تبدیل به PDF","در حال تبدیل…","با موفقیت تبدیل شد","وارونگی رنگ: روشن","وارونگی رنگ: خاموش"],
"fr": ["Créer des fichiers","Importer des fichiers","Utiliser des modèles","Convertir le format","Image en PDF","Numériser en PDF","Word en PDF","PPT en PDF","Choisir un modèle","Nom du fichier","Créer","Convertir en PDF","Conversion…","Converti avec succès","Inversion des couleurs : activée","Inversion des couleurs : désactivée"],
"in": ["Buat file","Impor file","Gunakan template","Konversi format","Gambar ke PDF","Pindai ke PDF","Word ke PDF","PPT ke PDF","Pilih template","Nama file","Buat","Konversi ke PDF","Mengonversi…","Berhasil dikonversi","Balik warna: Aktif","Balik warna: Nonaktif"],
"it": ["Crea file","Importa file","Usa modelli","Converti formato","Immagine in PDF","Scansiona in PDF","Word in PDF","PPT in PDF","Seleziona un modello","Nome file","Crea","Converti in PDF","Conversione…","Convertito correttamente","Inversione colori: attiva","Inversione colori: disattiva"],
"ja": ["ファイルを作成","ファイルをインポート","テンプレートを使用","形式を変換","画像をPDFに","スキャンしてPDFに","WordをPDFに","PPTをPDFに","テンプレートを選択","ファイル名","作成","PDFに変換","変換中…","変換しました","色反転：オン","色反転：オフ"],
"ko": ["파일 만들기","파일 가져오기","템플릿 사용","형식 변환","이미지를 PDF로","스캔하여 PDF로","Word를 PDF로","PPT를 PDF로","템플릿 선택","파일 이름","만들기","PDF로 변환","변환 중…","변환되었습니다","색상 반전: 켜짐","색상 반전: 꺼짐"],
"ms": ["Cipta fail","Import fail","Guna templat","Tukar format","Imej ke PDF","Imbas ke PDF","Word ke PDF","PPT ke PDF","Pilih templat","Nama fail","Cipta","Tukar ke PDF","Menukar…","Berjaya ditukar","Songsang warna: Hidup","Songsang warna: Mati"],
"pt": ["Criar arquivos","Importar arquivos","Usar modelos","Converter formato","Imagem para PDF","Digitalizar para PDF","Word para PDF","PPT para PDF","Selecione um modelo","Nome do arquivo","Criar","Converter para PDF","Convertendo…","Convertido com sucesso","Inversão de cores: ativada","Inversão de cores: desativada"],
"ru": ["Создать файлы","Импорт файлов","Шаблоны","Конвертировать","Изображение в PDF","Скан в PDF","Word в PDF","PPT в PDF","Выберите шаблон","Имя файла","Создать","Конвертировать в PDF","Конвертация…","Успешно конвертировано","Инверсия цветов: вкл.","Инверсия цветов: выкл."],
"tr": ["Dosya oluştur","Dosya içe aktar","Şablon kullan","Biçim dönüştür","Resimden PDF","Taramadan PDF","Word'den PDF","PPT'den PDF","Şablon seçin","Dosya adı","Oluştur","PDF'ye dönüştür","Dönüştürülüyor…","Başarıyla dönüştürüldü","Renk ters çevirme: Açık","Renk ters çevirme: Kapalı"],
"vi": ["Tạo tệp","Nhập tệp","Dùng mẫu","Chuyển đổi định dạng","Ảnh sang PDF","Quét sang PDF","Word sang PDF","PPT sang PDF","Chọn mẫu","Tên tệp","Tạo","Chuyển sang PDF","Đang chuyển đổi…","Chuyển đổi thành công","Đảo màu: Bật","Đảo màu: Tắt"],
"uz": ["Fayl yaratish","Fayl import qilish","Shablonlardan foydalanish","Formatni o\\'zgartirish","Rasmdan PDF","Skanerdan PDF","Word-dan PDF","PPT-dan PDF","Shablonni tanlang","Fayl nomi","Yaratish","PDF-ga aylantirish","Aylantirilmoqda…","Muvaffaqiyatli aylantirildi","Rang inversiyasi: yoqilgan","Rang inversiyasi: o\\'chirilgan"],
"th": ["สร้างไฟล์","นำเข้าไฟล์","ใช้เทมเพลต","แปลงรูปแบบ","รูปภาพเป็น PDF","สแกนเป็น PDF","Word เป็น PDF","PPT เป็น PDF","เลือกเทมเพลต","ชื่อไฟล์","สร้าง","แปลงเป็น PDF","กำลังแปลง…","แปลงสำเร็จ","กลับสี: เปิด","กลับสี: ปิด"],
"uk": ["Створити файли","Імпорт файлів","Шаблони","Конвертувати","Зображення в PDF","Скан у PDF","Word у PDF","PPT у PDF","Виберіть шаблон","Назва файлу","Створити","Конвертувати в PDF","Конвертація…","Успішно конвертовано","Інверсія кольорів: увімк.","Інверсія кольорів: вимк."],
"pl": ["Utwórz pliki","Importuj pliki","Użyj szablonów","Konwertuj format","Obraz do PDF","Skan do PDF","Word do PDF","PPT do PDF","Wybierz szablon","Nazwa pliku","Utwórz","Konwertuj do PDF","Konwertowanie…","Przekonwertowano","Odwrócenie kolorów: wł.","Odwrócenie kolorów: wył."],
"tl": ["Gumawa ng file","Mag-import ng file","Gumamit ng template","I-convert ang format","Larawan sa PDF","I-scan sa PDF","Word sa PDF","PPT sa PDF","Pumili ng template","Pangalan ng file","Gumawa","I-convert sa PDF","Kino-convert…","Matagumpay na na-convert","Pag-invert ng kulay: On","Pag-invert ng kulay: Off"],
"zh-rTW": ["建立檔案","匯入檔案","使用範本","轉換格式","圖片轉 PDF","掃描轉 PDF","Word 轉 PDF","PPT 轉 PDF","選擇範本","檔案名稱","建立","轉換為 PDF","轉換中…","轉換成功","色彩反轉：開啟","色彩反轉：關閉"],
"ur": ["فائلیں بنائیں","فائلیں درآمد کریں","ٹیمپلیٹس استعمال کریں","فارمیٹ تبدیل کریں","تصویر سے PDF","اسکین سے PDF","Word سے PDF","PPT سے PDF","ٹیمپلیٹ منتخب کریں","فائل کا نام","بنائیں","PDF میں تبدیل کریں","تبدیل ہو رہا ہے…","کامیابی سے تبدیل ہو گیا","رنگ الٹنا: آن","رنگ الٹنا: آف"],
"zh-rCN": ["创建文件","导入文件","使用模板","格式转换","图片转 PDF","扫描转 PDF","Word 转 PDF","PPT 转 PDF","选择模板","文件名","创建","转换为 PDF","转换中…","转换成功","反色：开","反色：关"],
}
def esc(s):
    out = s.replace("\\'", "\u0000").replace("'", "\\'").replace('"', '\\"')
    return out.replace("\u0000", "\\'")
for loc, vals in T.items():
    assert len(vals) == len(KEYS), (loc, len(vals))
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step4 -->.*?<!-- /step4 -->", "", s, flags=re.S)
    block = "\n    <!-- step4 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step4 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step4 translations:", len(T))
