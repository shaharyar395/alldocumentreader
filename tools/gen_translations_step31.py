# -*- coding: utf-8 -*-
"""Step-31 strings: Google Drive app missing. Run from project root."""
import re
T = {
"ar":["تطبيق Google Drive مطلوب لحفظ الملفات في Drive. ثبّته من Google Play ثم حاول مرة أخرى.","تثبيت"],
"de":["Zum Speichern in Drive wird die Google Drive-App benötigt. Installiere sie über Google Play und versuche es erneut.","Installieren"],
"es":["Se necesita la app Google Drive para guardar archivos en Drive. Instálala desde Google Play e inténtalo de nuevo.","Instalar"],
"fa":["برای ذخیره فایل‌ها در Drive به برنامه Google Drive نیاز است. آن را از Google Play نصب کنید و دوباره امتحان کنید.","نصب"],
"fr":["L'application Google Drive est nécessaire pour enregistrer des fichiers sur Drive. Installez-la depuis Google Play, puis réessayez.","Installer"],
"in":["Aplikasi Google Drive diperlukan untuk menyimpan file ke Drive. Instal dari Google Play, lalu coba lagi.","Instal"],
"it":["Per salvare file su Drive serve l'app Google Drive. Installala da Google Play e riprova.","Installa"],
"ja":["Drive にファイルを保存するには Google ドライブ アプリが必要です。Google Play からインストールして、もう一度お試しください。","インストール"],
"ko":["Drive에 파일을 저장하려면 Google 드라이브 앱이 필요합니다. Google Play에서 설치한 후 다시 시도하세요.","설치"],
"ms":["Apl Google Drive diperlukan untuk menyimpan fail ke Drive. Pasang dari Google Play, kemudian cuba lagi.","Pasang"],
"pt":["O app Google Drive é necessário para salvar arquivos no Drive. Instale-o pelo Google Play e tente novamente.","Instalar"],
"ru":["Чтобы сохранять файлы на Диск, нужно приложение Google Диск. Установите его из Google Play и повторите попытку.","Установить"],
"tr":["Dosyaları Drive'a kaydetmek için Google Drive uygulaması gerekir. Google Play'den yükleyip tekrar deneyin.","Yükle"],
"vi":["Cần ứng dụng Google Drive để lưu tệp vào Drive. Hãy cài đặt từ Google Play rồi thử lại.","Cài đặt"],
"uz":["Fayllarni Drive'ga saqlash uchun Google Drive ilovasi kerak. Uni Google Play'dan o'rnating va qayta urinib ko'ring.","O'rnatish"],
"th":["ต้องใช้แอป Google ไดรฟ์เพื่อบันทึกไฟล์ลงไดรฟ์ ติดตั้งจาก Google Play แล้วลองอีกครั้ง","ติดตั้ง"],
"uk":["Щоб зберігати файли на Диск, потрібен додаток Google Диск. Установіть його з Google Play і спробуйте ще раз.","Установити"],
"pl":["Do zapisywania plików na Dysku potrzebna jest aplikacja Dysk Google. Zainstaluj ją z Google Play i spróbuj ponownie.","Zainstaluj"],
"tl":["Kailangan ang Google Drive app para mag-save ng mga file sa Drive. I-install ito mula sa Google Play, pagkatapos ay subukang muli.","I-install"],
"zh-rTW":["需要 Google 雲端硬碟應用程式才能將檔案儲存到雲端硬碟。請從 Google Play 安裝後再試一次。","安裝"],
"ur":["Drive میں فائلیں محفوظ کرنے کے لیے Google Drive ایپ درکار ہے۔ اسے Google Play سے انسٹال کریں، پھر دوبارہ کوشش کریں۔","انسٹال کریں"],
"zh-rCN":["需要 Google 云端硬盘应用才能将文件保存到云端硬盘。请从 Google Play 安装后重试。","安装"],
}
KEYS = ["drive_app_missing", "drive_install"]
def esc(s): return s.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"')
for loc, vals in T.items():
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step31 -->.*?<!-- /step31 -->", "", s, flags=re.S)
    block = "\n    <!-- step31 -->\n" + "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in zip(KEYS, vals)) + "    <!-- /step31 -->"
    s = s.replace("\n</resources>", block + "\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("step31 translations:", len(T))
