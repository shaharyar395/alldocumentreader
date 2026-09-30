# -*- coding: utf-8 -*-
"""Appends step-12 strings (Welcome back, purchase messages) to values-XX/strings.xml. Paywall texts stay English. Run from project root."""
import re
KEYS = ["welcome_back", "subscribe_failed", "premium_welcome", "premium_restored"]
T = {
"ar": ["مرحبًا بعودتك…","فشل الاشتراك","مرحبًا بك في Premium!","تمت استعادة Premium"],
"de": ["Willkommen zurück…","Abo fehlgeschlagen","Willkommen bei Premium!","Premium wiederhergestellt"],
"es": ["Bienvenido de nuevo…","Error en la suscripción","¡Bienvenido a Premium!","Premium restaurado"],
"fa": ["خوش برگشتید…","اشتراک ناموفق بود","به Premium خوش آمدید!","Premium بازیابی شد"],
"fr": ["Bon retour…","Échec de l'abonnement","Bienvenue dans Premium !","Premium restauré"],
"in": ["Selamat datang kembali…","Langganan gagal","Selamat datang di Premium!","Premium dipulihkan"],
"it": ["Bentornato…","Abbonamento non riuscito","Benvenuto in Premium!","Premium ripristinato"],
"ja": ["おかえりなさい…","購読に失敗しました","Premium へようこそ！","Premium を復元しました"],
"ko": ["다시 오신 것을 환영합니다…","구독 실패","Premium에 오신 것을 환영합니다!","Premium이 복원되었습니다"],
"ms": ["Selamat kembali…","Langganan gagal","Selamat datang ke Premium!","Premium dipulihkan"],
"pt": ["Bem-vindo de volta…","Falha na assinatura","Bem-vindo ao Premium!","Premium restaurado"],
"ru": ["С возвращением…","Не удалось оформить подписку","Добро пожаловать в Premium!","Premium восстановлен"],
"tr": ["Tekrar hoş geldiniz…","Abonelik başarısız","Premium'a hoş geldiniz!","Premium geri yüklendi"],
"vi": ["Chào mừng trở lại…","Đăng ký thất bại","Chào mừng đến với Premium!","Đã khôi phục Premium"],
"uz": ["Qaytganingiz bilan…","Obuna amalga oshmadi","Premium'ga xush kelibsiz!","Premium tiklandi"],
"th": ["ยินดีต้อนรับกลับ…","สมัครสมาชิกไม่สำเร็จ","ยินดีต้อนรับสู่ Premium!","กู้คืน Premium แล้ว"],
"uk": ["З поверненням…","Не вдалося оформити підписку","Ласкаво просимо до Premium!","Premium відновлено"],
"pl": ["Witaj ponownie…","Subskrypcja nie powiodła się","Witaj w Premium!","Przywrócono Premium"],
"tl": ["Maligayang pagbabalik…","Nabigo ang subscription","Maligayang pagdating sa Premium!","Naibalik ang Premium"],
"zh-rTW": ["歡迎回來…","訂閱失敗","歡迎使用 Premium！","已恢復 Premium"],
"ur": ["خوش آمدید دوبارہ…","سبسکرپشن ناکام ہو گئی","Premium میں خوش آمدید!","Premium بحال ہو گیا"],
"zh-rCN": ["欢迎回来…","订阅失败","欢迎使用 Premium！","已恢复 Premium"],
}
def esc(s):
    return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    assert len(vals) == len(KEYS), loc
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step12 -->.*?<!-- /step12 -->", "", s, flags=re.S)
    block = "\n    <!-- step12 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step12 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step12 translations:", len(T))
