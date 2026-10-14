# -*- coding: utf-8 -*-
"""Step-39 strings: "Loading ad…" in the Language screen's banner space. Run from project root."""
import re
T = {"ar":"جارٍ تحميل الإعلان…","de":"Anzeige wird geladen…","es":"Cargando anuncio…","fa":"در حال بارگیری تبلیغ…",
"fr":"Chargement de l'annonce…","in":"Memuat iklan…","it":"Caricamento annuncio…","ja":"広告を読み込み中…","ko":"광고 로드 중…",
"ms":"Memuatkan iklan…","pt":"Carregando anúncio…","ru":"Загрузка рекламы…","tr":"Reklam yükleniyor…","vi":"Đang tải quảng cáo…",
"uz":"Reklama yuklanmoqda…","th":"กำลังโหลดโฆษณา…","uk":"Завантаження реклами…","pl":"Wczytywanie reklamy…","tl":"Nilo-load ang ad…",
"zh-rTW":"正在載入廣告…","ur":"اشتہار لوڈ ہو رہا ہے…","zh-rCN":"正在加载广告…"}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, v in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step39 -->.*?<!-- /step39 -->", "", s, flags=re.S)
    s = s.replace("\n</resources>", f'\n    <!-- step39 -->\n    <string name="ad_loading">{esc(v)}</string>\n    <!-- /step39 -->\n</resources>')
    open(p, "w", encoding="utf-8").write(s)
print("step39 translations:", len(T))
