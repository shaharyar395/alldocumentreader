# -*- coding: utf-8 -*-
"""Appends step-27 strings (search: no results) to values-XX/strings.xml. Run from project root."""
import re
T = {"ar":"لم يتم العثور على نتائج","de":"Keine Ergebnisse gefunden","es":"No se encontraron resultados","fa":"نتیجه‌ای یافت نشد",
"fr":"Aucun résultat trouvé","in":"Tidak ada hasil","it":"Nessun risultato trovato","ja":"結果が見つかりません","ko":"결과가 없습니다",
"ms":"Tiada hasil ditemui","pt":"Nenhum resultado encontrado","ru":"Ничего не найдено","tr":"Sonuç bulunamadı","vi":"Không tìm thấy kết quả",
"uz":"Hech narsa topilmadi","th":"ไม่พบผลลัพธ์","uk":"Нічого не знайдено","pl":"Nie znaleziono wyników","tl":"Walang nahanap na resulta",
"zh-rTW":"找不到結果","ur":"کوئی نتیجہ نہیں ملا","zh-rCN":"未找到结果"}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, v in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step27 -->.*?<!-- /step27 -->", "", s, flags=re.S)
    s = s.replace("\n</resources>", f'\n    <!-- step27 -->\n    <string name="search_no_results">{esc(v)}</string>\n    <!-- /step27 -->\n</resources>')
    open(p, "w", encoding="utf-8").write(s)
print("step27 translations:", len(T))
