# -*- coding: utf-8 -*-
"""Appends step-20 strings (bookmark message) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["bm_pill_added", "bm_pill_removed"]
T = {
"ar": ["تمت الإضافة إلى الإشارات المرجعية","تمت إزالة الإشارة المرجعية"], "de": ["Lesezeichen gesetzt","Lesezeichen entfernt"],
"es": ["Añadido a marcadores","Marcador eliminado"], "fa": ["نشانک‌گذاری شد","نشانک حذف شد"],
"fr": ["Ajouté aux favoris","Favori supprimé"], "in": ["Ditandai","Penanda dihapus"],
"it": ["Aggiunto ai segnalibri","Segnalibro rimosso"], "ja": ["ブックマークしました","ブックマークを解除しました"],
"ko": ["북마크됨","북마크 삭제됨"], "ms": ["Ditanda buku","Penanda buku dialih keluar"],
"pt": ["Adicionado aos favoritos","Favorito removido"], "ru": ["Добавлено в закладки","Закладка удалена"],
"tr": ["Yer işaretlendi","Yer işareti kaldırıldı"], "vi": ["Đã đánh dấu","Đã xóa dấu trang"],
"uz": ["Xatcho'pga qo'shildi","Xatcho'p olib tashlandi"], "th": ["บุ๊กมาร์กแล้ว","นำบุ๊กมาร์กออกแล้ว"],
"uk": ["Додано в закладки","Закладку видалено"], "pl": ["Dodano zakładkę","Usunięto zakładkę"],
"tl": ["Na-bookmark","Inalis ang bookmark"], "zh-rTW": ["已加入書籤","已移除書籤"],
"ur": ["بک مارک کر دیا گیا","بک مارک ہٹا دیا گیا"], "zh-rCN": ["已添加书签","已移除书签"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step20 -->.*?<!-- /step20 -->", "", s, flags=re.S)
    block = "\n    <!-- step20 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step20 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step20 translations:", len(T))
