# -*- coding: utf-8 -*-
"""Step-30 strings: TXT search hint. Run from project root."""
import re
T = {"ar":"البحث في TXT","de":"In TXT suchen","es":"Buscar en TXT","fa":"جستجو در TXT","fr":"Rechercher dans le TXT","in":"Cari di TXT",
"it":"Cerca nel TXT","ja":"TXT内を検索","ko":"TXT에서 검색","ms":"Cari dalam TXT","pt":"Pesquisar no TXT","ru":"Поиск в TXT","tr":"TXT'de ara",
"vi":"Tìm trong TXT","uz":"TXT da qidirish","th":"ค้นหาใน TXT","uk":"Пошук у TXT","pl":"Szukaj w TXT","tl":"Maghanap sa TXT",
"zh-rTW":"在 TXT 中搜尋","ur":"TXT میں تلاش کریں","zh-rCN":"在 TXT 中搜索"}
for loc, v in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step30 -->.*?<!-- /step30 -->", "", s, flags=re.S)
    s = s.replace("\n</resources>", f'\n    <!-- step30 -->\n    <string name="search_in_txt">{v.replace(chr(39), chr(92)+chr(39))}</string>\n    <!-- /step30 -->\n</resources>')
    open(p, "w", encoding="utf-8").write(s)
print("step30 translations:", len(T))
