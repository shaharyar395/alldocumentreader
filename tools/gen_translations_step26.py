# -*- coding: utf-8 -*-
"""Appends step-26 strings ("Is it helpful?" sheet) to values-XX/strings.xml. Run from project root."""
import re
KEYS = ["helpful_title", "helpful_no", "helpful_yes"]
T = {
"ar": ["هل تعتقد أن %s مفيد؟","😞 ليس حقًا","🥰 مفيد"], "de": ["Findest du %s hilfreich?","😞 Nicht wirklich","🥰 Hilfreich"],
"es": ["¿Crees que %s es útil?","😞 No mucho","🥰 Útil"], "fa": ["آیا %s را مفید می‌دانید؟","😞 نه واقعاً","🥰 مفید"],
"fr": ["Trouvez-vous %s utile ?","😞 Pas vraiment","🥰 Utile"], "in": ["Apakah menurut Anda %s bermanfaat?","😞 Tidak juga","🥰 Bermanfaat"],
"it": ["Pensi che %s sia utile?","😞 Non proprio","🥰 Utile"], "ja": ["%s は役に立ちましたか？","😞 あまり","🥰 役に立った"],
"ko": ["%s이(가) 도움이 되었나요?","😞 별로요","🥰 도움이 돼요"], "ms": ["Adakah anda rasa %s berguna?","😞 Tidak juga","🥰 Berguna"],
"pt": ["Você acha que o %s é útil?","😞 Não muito","🥰 Útil"], "ru": ["Считаете ли вы %s полезным?","😞 Не очень","🥰 Полезно"],
"tr": ["%s sizce faydalı mı?","😞 Pek değil","🥰 Faydalı"], "vi": ["Bạn thấy %s có hữu ích không?","😞 Không hẳn","🥰 Hữu ích"],
"uz": ["%s sizga foydali deb o'ylaysizmi?","😞 Unchalik emas","🥰 Foydali"], "th": ["คุณคิดว่า %s มีประโยชน์ไหม","😞 ไม่ค่อย","🥰 มีประโยชน์"],
"uk": ["Чи вважаєте ви %s корисним?","😞 Не дуже","🥰 Корисно"], "pl": ["Czy uważasz, że %s jest pomocny?","😞 Nie bardzo","🥰 Pomocny"],
"tl": ["Sa tingin mo ba ay nakakatulong ang %s?","😞 Hindi masyado","🥰 Nakakatulong"], "zh-rTW": ["你覺得 %s 有幫助嗎？","😞 不太有","🥰 有幫助"],
"ur": ["کیا آپ کے خیال میں %s مددگار ہے؟","😞 زیادہ نہیں","🥰 مددگار"], "zh-rCN": ["你觉得 %s 有帮助吗？","😞 不太有","🥰 有帮助"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step26 -->.*?<!-- /step26 -->", "", s, flags=re.S)
    block = "\n    <!-- step26 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step26 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step26 translations:", len(T))
