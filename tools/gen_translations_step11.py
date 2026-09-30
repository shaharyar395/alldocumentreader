# -*- coding: utf-8 -*-
"""Appends step-11 strings (home reload banner, recycle bin screen) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["loaded_successfully", "back", "bin_info", "bin_empty"]
T = {
"ar": ["تم التحميل بنجاح","رجوع","سيتم حذف الملفات نهائيًا بعد 30 يومًا من نقلها إلى هنا.","سلة المحذوفات فارغة"],
"de": ["Erfolgreich geladen","Zurück","Dateien werden 30 Tage nach dem Verschieben hierher endgültig gelöscht.","Der Papierkorb ist leer"],
"es": ["Cargado correctamente","Atrás","Los archivos se eliminarán definitivamente 30 días después de moverlos aquí.","La papelera está vacía"],
"fa": ["با موفقیت بارگیری شد","بازگشت","فایل‌ها ۳۰ روز پس از انتقال به اینجا برای همیشه حذف می‌شوند.","سطل بازیافت خالی است"],
"fr": ["Chargé avec succès","Retour","Les fichiers seront définitivement supprimés 30 jours après avoir été déplacés ici.","La corbeille est vide"],
"in": ["Berhasil dimuat","Kembali","File akan dihapus permanen 30 hari setelah dipindahkan ke sini.","Keranjang sampah kosong"],
"it": ["Caricato correttamente","Indietro","I file verranno eliminati definitivamente 30 giorni dopo essere stati spostati qui.","Il cestino è vuoto"],
"ja": ["読み込みました","戻る","ここに移動したファイルは30日後に完全に削除されます。","ごみ箱は空です"],
"ko": ["불러오기 완료","뒤로","이곳으로 이동한 파일은 30일 후 영구 삭제됩니다.","휴지통이 비어 있습니다"],
"ms": ["Berjaya dimuatkan","Kembali","Fail akan dipadam kekal 30 hari selepas dipindahkan ke sini.","Tong kitar semula kosong"],
"pt": ["Carregado com sucesso","Voltar","Os arquivos serão excluídos permanentemente 30 dias após serem movidos para cá.","A lixeira está vazia"],
"ru": ["Загружено","Назад","Файлы будут безвозвратно удалены через 30 дней после перемещения сюда.","Корзина пуста"],
"tr": ["Başarıyla yüklendi","Geri","Dosyalar buraya taşındıktan 30 gün sonra kalıcı olarak silinir.","Geri dönüşüm kutusu boş"],
"vi": ["Đã tải xong","Quay lại","Tệp sẽ bị xóa vĩnh viễn sau 30 ngày kể từ khi chuyển vào đây.","Thùng rác trống"],
"uz": ["Muvaffaqiyatli yuklandi","Orqaga","Fayllar bu yerga ko'chirilgandan 30 kun o'tib butunlay o'chiriladi.","Savat bo'sh"],
"th": ["โหลดสำเร็จ","กลับ","ไฟล์จะถูกลบถาวรหลังจากย้ายมาที่นี่ 30 วัน","ถังรีไซเคิลว่างเปล่า"],
"uk": ["Завантажено","Назад","Файли буде остаточно видалено через 30 днів після переміщення сюди.","Кошик порожній"],
"pl": ["Wczytano pomyślnie","Wstecz","Pliki zostaną trwale usunięte 30 dni po przeniesieniu tutaj.","Kosz jest pusty"],
"tl": ["Matagumpay na na-load","Bumalik","Permanenteng mabubura ang mga file 30 araw matapos ilipat dito.","Walang laman ang recycle bin"],
"zh-rTW": ["載入成功","返回","檔案移至此處 30 天後將被永久刪除。","資源回收筒是空的"],
"ur": ["کامیابی سے لوڈ ہو گیا","واپس","یہاں منتقل ہونے کے 30 دن بعد فائلیں مستقل طور پر حذف ہو جائیں گی۔","ری سائیکل بن خالی ہے"],
"zh-rCN": ["加载成功","返回","文件移至此处 30 天后将被永久删除。","回收站是空的"],
}
def esc(s):
    return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    assert len(vals) == len(KEYS), loc
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step11 -->.*?<!-- /step11 -->", "", s, flags=re.S)
    block = "\n    <!-- step11 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step11 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step11 translations:", len(T))
