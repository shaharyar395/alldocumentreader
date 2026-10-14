# -*- coding: utf-8 -*-
"""Step-40 strings: horizontal mode swipe tutorial. Run from project root."""
import re
T = {"ar":"اسحب لليسار/لليمين لتقليب الصفحات","de":"Nach links/rechts wischen, um zu blättern","es":"Desliza a la izquierda/derecha para pasar páginas",
"fa":"برای ورق زدن صفحات به چپ/راست بکشید","fr":"Balayez vers la gauche/droite pour tourner les pages","in":"Geser ke kiri/kanan untuk membalik halaman",
"it":"Scorri a sinistra/destra per sfogliare le pagine","ja":"左右にスワイプしてページをめくります","ko":"좌우로 스와이프하여 페이지를 넘기세요",
"ms":"Leret ke kiri/kanan untuk menyelak halaman","pt":"Deslize para a esquerda/direita para virar as páginas","ru":"Проведите влево/вправо, чтобы листать страницы",
"tr":"Sayfa çevirmek için sola/sağa kaydırın","vi":"Vuốt sang trái/phải để lật trang","uz":"Sahifalarni varaqlash uchun chapga/o'ngga suring",
"th":"ปัดซ้าย/ขวาเพื่อเปลี่ยนหน้า","uk":"Проведіть ліворуч/праворуч, щоб гортати сторінки","pl":"Przesuń w lewo/prawo, aby przewracać strony",
"tl":"Mag-swipe pakaliwa/pakanan para lumipat ng pahina","zh-rTW":"左右滑動即可翻頁","ur":"صفحات پلٹنے کے لیے بائیں/دائیں سوائپ کریں","zh-rCN":"左右滑动即可翻页"}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, v in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step40 -->.*?<!-- /step40 -->", "", s, flags=re.S)
    s = s.replace("\n</resources>", f'\n    <!-- step40 -->\n    <string name="swipe_pages_tip">{esc(v)}</string>\n    <!-- /step40 -->\n</resources>')
    open(p, "w", encoding="utf-8").write(s)
print("step40 translations:", len(T))
