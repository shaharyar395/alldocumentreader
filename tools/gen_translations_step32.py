# -*- coding: utf-8 -*-
"""Step-32 strings: premium page (Get Ready screen, merge feature, User Reviews, Cancel anytime).
Review texts are placeholders and stay English. Run from project root."""
import re
KEYS = ["ready_top", "ready_bottom", "premium_slide_merge", "feat_merge", "user_reviews", "cancel_anytime"]
T = {
"ar":["استعد لتجربتك","المجانية لمدة 7 أيام","دمج\\nوتقسيم PDF","دمج وتقسيم ملفات PDF بسلاسة","آراء المستخدمين","يمكنك الإلغاء في أي وقت"],
"de":["Mach dich bereit für deine","7-tägige Gratis-Testversion","PDFs zusammenführen\\n& teilen","PDFs nahtlos zusammenführen & teilen","Nutzerbewertungen","Jederzeit kündbar"],
"es":["Prepárate para tu","prueba gratis de 7 días","Unir y\\ndividir PDF","Une y divide PDF sin esfuerzo","Opiniones de usuarios","Cancela cuando quieras"],
"fa":["آماده شوید برای","۷ روز آزمایش رایگان","ادغام و\\nتقسیم PDF","ادغام و تقسیم آسان PDF","نظرات کاربران","لغو در هر زمان"],
"fr":["Préparez-vous pour votre","essai gratuit de 7 jours","Fusionner &\\ndiviser PDF","Fusionnez et divisez vos PDF facilement","Avis des utilisateurs","Annulable à tout moment"],
"in":["Bersiaplah untuk","Uji Coba Gratis 7 Hari","Gabung &\\npisah PDF","Gabung & pisah PDF dengan mudah","Ulasan Pengguna","Batalkan kapan saja"],
"it":["Preparati alla tua","prova gratuita di 7 giorni","Unisci e\\ndividi PDF","Unisci e dividi PDF senza sforzo","Recensioni degli utenti","Annulla quando vuoi"],
"ja":["まもなく始まります","7日間の無料トライアル","PDFの\\n結合と分割","PDFをスムーズに結合・分割","ユーザーレビュー","いつでも解約できます"],
"ko":["곧 시작됩니다","7일 무료 체험","PDF\\n병합 및 분할","PDF를 손쉽게 병합 및 분할","사용자 리뷰","언제든지 취소 가능"],
"ms":["Bersedia untuk","Percubaan Percuma 7 Hari","Gabung &\\npisah PDF","Gabung & pisah PDF dengan lancar","Ulasan Pengguna","Batal bila-bila masa"],
"pt":["Prepare-se para o seu","teste grátis de 7 dias","Juntar e\\ndividir PDF","Junte e divida PDFs facilmente","Avaliações de usuários","Cancele quando quiser"],
"ru":["Приготовьтесь к","7-дневной бесплатной пробной версии","Объединение\\nи разделение PDF","Лёгкое объединение и разделение PDF","Отзывы пользователей","Отмена в любое время"],
"tr":["Hazır olun:","7 Günlük Ücretsiz Deneme","PDF birleştir\\n& böl","PDF'leri kolayca birleştirin ve bölün","Kullanıcı Yorumları","İstediğiniz zaman iptal edin"],
"vi":["Sẵn sàng cho","7 ngày dùng thử miễn phí","Gộp &\\ntách PDF","Gộp & tách PDF dễ dàng","Đánh giá của người dùng","Hủy bất cứ lúc nào"],
"uz":["Tayyor bo'ling:","7 kunlik bepul sinov","PDF birlashtirish\\nva bo'lish","PDF'larni oson birlashtirish va bo'lish","Foydalanuvchi sharhlari","Istalgan vaqtda bekor qiling"],
"th":["เตรียมพร้อมสำหรับ","ทดลองใช้ฟรี 7 วัน","รวมและ\\nแยก PDF","รวมและแยก PDF ได้อย่างราบรื่น","รีวิวจากผู้ใช้","ยกเลิกได้ทุกเมื่อ"],
"uk":["Приготуйтеся до","7-денної безкоштовної пробної версії","Об'єднання\\nй поділ PDF","Легке об'єднання й поділ PDF","Відгуки користувачів","Скасування будь-коли"],
"pl":["Przygotuj się na","7-dniowy bezpłatny okres próbny","Łączenie\\ni dzielenie PDF","Łatwe łączenie i dzielenie PDF","Opinie użytkowników","Anuluj w dowolnej chwili"],
"tl":["Maghanda para sa iyong","7-Araw na Libreng Trial","Pagsamahin at\\nhatiin ang PDF","Madaling pagsamahin at hatiin ang PDF","Mga Review ng User","Kanselahin anumang oras"],
"zh-rTW":["準備開始您的","7 天免費試用","合併與\\n分割 PDF","輕鬆合併與分割 PDF","使用者評價","可隨時取消"],
"ur":["تیار ہو جائیں اپنے","7 دن کے مفت ٹرائل کے لیے","PDF ملائیں\\nاور تقسیم کریں","PDF کو آسانی سے ملائیں اور تقسیم کریں","صارفین کی رائے","کسی بھی وقت منسوخ کریں"],
"zh-rCN":["准备开始您的","7 天免费试用","合并与\\n拆分 PDF","轻松合并与拆分 PDF","用户评价","可随时取消"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step32 -->.*?<!-- /step32 -->", "", s, flags=re.S)
    block = "\n    <!-- step32 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step32 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step32 translations:", len(T))
