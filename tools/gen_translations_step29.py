# -*- coding: utf-8 -*-
"""Step-29 strings: "New" tag, Save to Drive for one file. Run from project root."""
import re
KEYS = ["new_badge", "save_to_drive_short", "drive_saving", "drive_saved", "drive_save_failed_title", "drive_not_allowed"]
NA = {  # drive_not_allowed
"ar":"لم يمنح Google الوصول إلى Google Drive. اختر حسابك واضغط سماح.",
"de":"Google hat keinen Zugriff auf Google Drive erteilt. Wähle dein Konto und tippe auf Zulassen.",
"es":"Google no dio acceso a Google Drive. Elige tu cuenta y toca Permitir.",
"fa":"Google اجازه دسترسی به Google Drive را نداد. حساب خود را انتخاب کنید و اجازه دادن را بزنید.",
"fr":"Google n'a pas autorisé l'accès à Google Drive. Choisissez votre compte et appuyez sur Autoriser.",
"in":"Google tidak memberi akses ke Google Drive. Pilih akun Anda lalu ketuk Izinkan.",
"it":"Google non ha concesso l'accesso a Google Drive. Scegli il tuo account e tocca Consenti.",
"ja":"Google ドライブへのアクセスが許可されませんでした。アカウントを選んで「許可」をタップしてください。",
"ko":"Google 드라이브 접근이 허용되지 않았습니다. 계정을 선택하고 허용을 누르세요.",
"ms":"Google tidak memberi akses ke Google Drive. Pilih akaun anda dan ketik Benarkan.",
"pt":"O Google não deu acesso ao Google Drive. Escolha sua conta e toque em Permitir.",
"ru":"Google не предоставил доступ к Google Диску. Выберите аккаунт и нажмите «Разрешить».",
"tr":"Google, Google Drive erişimi vermedi. Hesabınızı seçip İzin ver'e dokunun.",
"vi":"Google chưa cấp quyền truy cập Google Drive. Hãy chọn tài khoản và nhấn Cho phép.",
"uz":"Google Google Drive'ga ruxsat bermadi. Hisobingizni tanlang va Ruxsat berish tugmasini bosing.",
"th":"Google ไม่ได้ให้สิทธิ์เข้าถึง Google ไดรฟ์ เลือกบัญชีแล้วแตะอนุญาต",
"uk":"Google не надав доступу до Google Диска. Виберіть обліковий запис і натисніть «Дозволити».",
"pl":"Google nie przyznał dostępu do Dysku Google. Wybierz konto i dotknij Zezwól.",
"tl":"Hindi nagbigay ang Google ng access sa Google Drive. Piliin ang iyong account at i-tap ang Payagan.",
"zh-rTW":"Google 未授予 Google 雲端硬碟的存取權。請選擇帳戶並輕觸「允許」。",
"ur":"Google نے Google Drive تک رسائی نہیں دی۔ اپنا اکاؤنٹ منتخب کریں اور اجازت دیں پر ٹیپ کریں۔",
"zh-rCN":"Google 未授予 Google 云端硬盘的访问权限。请选择帐号并点按“允许”。",
}
T = {
"ar":["جديد","حفظ في Drive","جارٍ الحفظ في Google Drive…","تم حفظ %1$s في Google Drive (مجلد All Document Reader)","تعذّر الحفظ في Google Drive"],
"de":["Neu","In Drive speichern","Wird in Google Drive gespeichert…","%1$s in Google Drive gespeichert (Ordner All Document Reader)","Speichern in Google Drive fehlgeschlagen"],
"es":["Nuevo","Guardar en Drive","Guardando en Google Drive…","%1$s guardado en Google Drive (carpeta All Document Reader)","No se pudo guardar en Google Drive"],
"fa":["جدید","ذخیره در Drive","در حال ذخیره در Google Drive…","%1$s در Google Drive ذخیره شد (پوشه All Document Reader)","ذخیره در Google Drive ممکن نشد"],
"fr":["Nouveau","Enregistrer sur Drive","Enregistrement sur Google Drive…","%1$s enregistré sur Google Drive (dossier All Document Reader)","Impossible d'enregistrer sur Google Drive"],
"in":["Baru","Simpan ke Drive","Menyimpan ke Google Drive…","%1$s disimpan ke Google Drive (folder All Document Reader)","Tidak dapat menyimpan ke Google Drive"],
"it":["Nuovo","Salva su Drive","Salvataggio su Google Drive…","%1$s salvato su Google Drive (cartella All Document Reader)","Impossibile salvare su Google Drive"],
"ja":["新規","ドライブに保存","Google ドライブに保存しています…","%1$s を Google ドライブに保存しました（All Document Reader フォルダ）","Google ドライブに保存できませんでした"],
"ko":["새 파일","Drive에 저장","Google 드라이브에 저장 중…","%1$s을(를) Google 드라이브에 저장했습니다 (All Document Reader 폴더)","Google 드라이브에 저장할 수 없습니다"],
"ms":["Baharu","Simpan ke Drive","Menyimpan ke Google Drive…","%1$s disimpan ke Google Drive (folder All Document Reader)","Tidak dapat menyimpan ke Google Drive"],
"pt":["Novo","Salvar no Drive","Salvando no Google Drive…","%1$s salvo no Google Drive (pasta All Document Reader)","Não foi possível salvar no Google Drive"],
"ru":["Новый","Сохранить в Drive","Сохранение в Google Диск…","%1$s сохранён в Google Диске (папка All Document Reader)","Не удалось сохранить в Google Диск"],
"tr":["Yeni","Drive'a kaydet","Google Drive'a kaydediliyor…","%1$s Google Drive'a kaydedildi (All Document Reader klasörü)","Google Drive'a kaydedilemedi"],
"vi":["Mới","Lưu vào Drive","Đang lưu vào Google Drive…","Đã lưu %1$s vào Google Drive (thư mục All Document Reader)","Không thể lưu vào Google Drive"],
"uz":["Yangi","Drive'ga saqlash","Google Drive'ga saqlanmoqda…","%1$s Google Drive'ga saqlandi (All Document Reader jildi)","Google Drive'ga saqlab bo'lmadi"],
"th":["ใหม่","บันทึกลงไดรฟ์","กำลังบันทึกลง Google ไดรฟ์…","บันทึก %1$s ลง Google ไดรฟ์แล้ว (โฟลเดอร์ All Document Reader)","บันทึกลง Google ไดรฟ์ไม่ได้"],
"uk":["Новий","Зберегти в Drive","Збереження в Google Диск…","%1$s збережено в Google Диску (папка All Document Reader)","Не вдалося зберегти в Google Диск"],
"pl":["Nowy","Zapisz na Dysku","Zapisywanie na Dysku Google…","Zapisano %1$s na Dysku Google (folder All Document Reader)","Nie udało się zapisać na Dysku Google"],
"tl":["Bago","I-save sa Drive","Sine-save sa Google Drive…","Na-save ang %1$s sa Google Drive (folder na All Document Reader)","Hindi ma-save sa Google Drive"],
"zh-rTW":["新","儲存到雲端硬碟","正在儲存到 Google 雲端硬碟…","已將 %1$s 儲存到 Google 雲端硬碟（All Document Reader 資料夾）","無法儲存到 Google 雲端硬碟"],
"ur":["نیا","Drive میں محفوظ کریں","Google Drive میں محفوظ ہو رہا ہے…","%1$s Google Drive میں محفوظ ہو گیا (All Document Reader فولڈر)","Google Drive میں محفوظ نہیں ہو سکا"],
"zh-rCN":["新","保存到云端硬盘","正在保存到 Google 云端硬盘…","已将 %1$s 保存到 Google 云端硬盘（All Document Reader 文件夹）","无法保存到 Google 云端硬盘"],
}
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    vals = vals + [NA[loc]]
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step29 -->.*?<!-- /step29 -->", "", s, flags=re.S)
    block = "\n    <!-- step29 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step29 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step29 translations:", len(T))
