# All Document Reader (replica)

Kotlin + XML Android project. Covers the flow from the screen recording:
**Splash → Language → Onboarding (3 pages) → All files (home)**.

## Open & run
1. Android Studio (Koala / Ladybug or newer) → *Open* this folder.
2. Let Gradle sync (AGP 8.5.2, Kotlin 1.9.24, Gradle 8.9 wrapper, JDK 17).
3. Run the `app` configuration. minSdk 24, targetSdk 34.

## What's implemented
| Screen | Details |
|---|---|
| Splash | Logo, title, subtitle, faint isometric background, sliding blue progress pill. Routes to Language → Onboarding on first launch, then straight to home. |
| Language | Blue toolbar with ✓, 24 options (Default + 23 languages) as white cards with radio buttons. Applies the per-app locale via `AppCompatDelegate.setApplicationLocales`. Asks for notification permission (Android 13+) like the original. Main UI strings are translated in all 23 languages. |
| Onboarding | 3 dark pages ("All-in-one Document Reader", "Professional PDF Editor", "Easy & Practical Templates") with phone mock-ups built from real views, floating animated icons, pill page indicator, Next / Start. |
| All files (home) | Header with crown + search, 8 category tiles with live counts (All, PDF, Word, Excel, PPT, TXT, Image, Directories with used/total storage), "Loading files…" paper-plane state, Recent / Bookmarks tabs with empty state, FAB (+), "Discover new feature: Create PDF" tooltip, first-run coach mark ("Click the folders above to view" + OK), bottom navigation (All files / Tools / Settings). |
| Category list | Tapping a tile opens the file list for that type: search, sort (date / name / size), bookmark, ⋮ menu (open, share, bookmark, info). |
| Directories | Folder browser for internal storage with breadcrumb path. |
| Search | Search every scanned file by name. |
| Tools / Settings | Tools grid (placeholders), Settings with Language, Share, Rate, Feedback, Privacy, version. |

Files open through an installed viewer app for now (in-app readers come in a later step). Recent and bookmarks are saved in SharedPreferences.

## Step 2 – All files screen
| Feature | Details |
|---|---|
| List | White toolbar (back, **search**, **select**, **filter**). Rows fade/slide in on load. Each row: icon, name, date · size, bookmark, ⋮. Spinner while the first scan runs. |
| Search | Search box + Cancel, and All / PDF / Word / Excel / PPT / TXT / Image tabs (All files only). Live filtering. |
| Select | Tap select (or long-press a row) → "N selected", select-all, checkboxes, bottom bar **Share** (multi-file share) / **Delete**. |
| Delete | "Move to recycle bin or delete?" → *Move to recycle bin* (restorable for 30 days, stored in `Android/data/<pkg>/files/RecycleBin`, expired items purged on start) or *Delete directly*. |
| Filter by | Name / Date / File size + Ascending / Descending, saved between launches. |
| ⋮ menu | Header (icon, name, path, ›) opens a details sheet (size, storage path, last modified). Actions: **Share**, **Rename** (keeps extension, validates name, updates recents/bookmarks), **To home screen** (pinned launcher shortcut that opens the file; on Xiaomi/Redmi/POCO first shows the "Permission required – Home screen shortcuts" sheet), **Delete**. |
| Rating | "Do you like our app?" sheet with tappable stars appears once after returning home from a file list. 4–5★ opens Play Store. |

The same ⋮ menu is used in Recent / Bookmarks, category lists, search and directories.

## Step 3 – Directories, document viewer, PDF pages
| Feature | Details |
|---|---|
| Directories | White toolbar (back, search) + **Storage / All documents / Recycle bin** tabs. |
| Storage | One row per volume ("0" = phone, "0000-000C" = SD card) with item counts → folder browser titled "Storage" with a path line, folders (blue, "N files") then files (unknown types show "?"), newest first, hidden items included, search in folder, long-press a file for the ⋮ actions. |
| All documents | Every file on the device from MediaStore, newest first. |
| Recycle bin | Deleted files with "N days left"; tap → Restore / Delete permanently; "Empty bin". Empty state "Files in the recycle bin can be restored within 30 days." |
| Unsupported files | "File type not supported" sheet with OK and FAQ. |
| Viewer (PDF, Word .docx, TXT) | Loading screen, pages with pinch/double-tap zoom and fast-scroll handle, "1 / 6" page chip (tap → Go to page) with first-time tip, **Vertical/Horizontal**, **Rotate**, **Search** (next/prev match pages), **Invert** (night mode), **Share**, ⋮ sheet (Rename, Bookmark, To home screen, Pages, Print, Delete; header opens file details). |
| Convert | Word/TXT → PDF (toolbar button): "Converting… (x%)" → "Saved successfully [Open]". PDF → Word (text). Output: `Documents/AllDocumentReader/convert/`. |
| PDF Pages | Grid of page thumbnails: long-press & drag to reorder, select / select all, **Insert** (Blank pages / Images), **Rotate**, **Extract** (new PDF), **Delete** (confirm), **Setup** (duplicate, move to start/end), undo/redo, **Done** saves into the same file. |
| Insert blank pages | Template carousel (Blank, Line 1, Line 2, Notebook, Cornell, Grid, Graph), Page size sheet (Follow previous, A5, A4, A3, B5, B4, Letter, Legal), page colour, orientation, page count (±, or tap to type). |
| Home | "Recently added" row (files from the last 30 days). |

Word support is a built-in .docx reader (text styles, headings, lists, tables, pictures); complex layouts are simplified. Excel/PowerPoint/images still open in another app. PDF editing uses [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0).

## Step 4 – "+" Create files
| Feature | Details |
|---|---|
| Create files sheet | "+" button on home: **Import files**, **Use templates**, **Create PDF**, and **Convert format**: Image to PDF, Scan to PDF, Word to PDF, PDF to Word, PPT to PDF. The "Discover new feature: Create PDF" bubble opens Create PDF directly. |
| Import files | System file picker (Recent, Downloads, Drive…) → copied to `Documents/AllDocumentReader/import` → opened. |
| Use templates | "Select a template": built-in, offline Resume, Cover letter, Invoice, Meeting notes, To-do list, Weekly planner → file name → new PDF in `Documents/AllDocumentReader/create` → opens in the viewer. |
| Create PDF | Template carousel + File name, Page size (A5…Legal, default A4), page colour, orientation, page count → Create → opens the new PDF. |
| Image to PDF | Photo picker (multi-select) → grid with drag-to-reorder, remove, add more; page size A4 / Letter / Fit image; file name → PDF. |
| Scan to PDF | Camera capture page by page (camera button adds pages), "Document" black & white filter or Original, same options → PDF. |
| Word / PDF / PPT conversions | File picker listing matching files (with search, or Browse other apps) → progress ring with % → "Converted successfully" with Open / Share. Word (.docx, .txt) → PDF, PDF → Word, PowerPoint (.pptx) → PDF. |
| Viewer | Now also opens PowerPoint (.pptx) slides in-app; Invert shows "Color inversion: On/Off". |
| Home | "Recently added +N" badge for new files since you last opened the list. |

Converted files go to `Documents/AllDocumentReader/convert`.

## Step 5 – Import files: open, convert, edit
| Area | Behaviour |
|---|---|
| Import files | System file browser → the chosen file goes through the app's splash (logo, "View all documents in one place", sliding bar) → opens in the viewer. Files in shared storage open in place; cloud / attachment files are copied to `Documents/AllDocumentReader/import`. |
| Open with / Share | The app is registered for PDF, Word (.docx), PowerPoint (.pptx) and TXT, so it appears in Android's "Open with" and share sheets and uses the same splash → viewer flow. |
| Toolbar icons | Word/TXT/PPT: **PDF-with-arrows** icon = convert to PDF. PDF: **W-with-arrows** icon = convert to Word, **page-with-hexagon** icon = Pages (organizer). |
| Conversion fidelity | Word → PDF keeps the document's own page size, margins, styles, spacing, indents, fonts, colours, table column widths and positioned pictures, so the PDF looks like the Word file. PDF → Word now keeps every page exactly as it looks (each page is placed full-page in the .docx at the PDF's page size) instead of extracting only text. Only the file format changes. |
| Viewer | "Converting… (x%)" → "Saved successfully [Open]"; Vertical ⇄ Horizontal shows "Continuous pages" / "Page by page"; Rotate turns the screen to landscape / portrait; Invert shows "Color inversion: On/Off". |
| PDF edit (red pen) | Pen, highlighter, eraser, brush size, 6 colours, undo / redo, page arrows; Done writes the ink into the PDF as vector strokes. |
| Pages organizer | Opens with the current page selected; checkbox selects, tapping a page opens "All pages (n/N)" preview; hidden undo/redo until the first change; Extract → "Extracted successfully – Saved as "Extract_name.pdf". View / Share"; Setup → "Page setup" (preview, size in cm, page size ⊘ A5 A4 A3 B5 B4 Letter Legal, page colour, Apply to selected); Done saves and stays on the screen with "Saved successfully". |

## Step 6 – Image to PDF / Scan to PDF
| Screen | Behaviour |
|---|---|
| Start | Image to PDF → "Take a photo / Choose from gallery / Cancel". Scan to PDF (and Tools → Scan) opens the camera directly. |
| Camera | In-app camera (CameraX) after Android's camera permission prompt: X, flash off/auto/on, grid, shutter sound, 2× zoom, tap to focus, shutter with flash + the shot flying into the thumbnail (with count), Next arrow. Several pages can be shot in a row. |
| Choose cropping method | Illustration, Auto crop / No crop, OK, "Don't ask again" (change later in Settings → Scan settings). Auto crop finds the sheet of paper and straightens it. |
| Editor | Pages side by side (neighbours peek in), × to remove, "< 1/2 >", hold the compare button to see the original, filters Original · Auto · Docs · Color · Image · Super · Enhance · Enhance2 · B&W · B&W2 · Gray · Invert with "Apply to all", Retake, Pages (add more photos / gallery), Rotate, Crop (frame with corner and side handles, Left / Right, No crop ⇄ Auto crop, ✓). Done → "Processing… (x%)". |
| Image to PDF | Grid with numbers, × remove, long-press drag to reorder, tap to edit again, Add pages tile / top-right icon, Convert → "Converting… (x%)". Saved as `Image_PDF_yyyyMMdd_HHmm.pdf` in `Documents/AllDocumentReader/convert`. |
| Converted successfully! | Notebook preview with sparkles, file name with rename, View locally, Open / Share, Tools: Edit text, Annotate, Add text, PDF to Word, Print PDF, Sign. |

## Step 7 – Scan to PDF
* "+" → Scan to PDF opens the camera straight away (no source sheet).
* Camera: "+" button shows a centre guide, grid lines, pinch to zoom, tap to focus. Taking a shot freezes the picture and runs a blue scanning sweep with a spinner before the shot drops into the thumbnail.
* Next → Choose cropping method → editor; scanned photos start on the **Gray** filter (gallery pictures on Auto), and the filter strip scrolls to the page's filter.
* Crop: a round magnifier appears while dragging a corner or side handle; the ✓ stays grey until something is changed.
* Output list and file name are the same as Image to PDF ("Image to PDF", `Image_PDF_yyyyMMdd_HHmm.pdf`), then the Converted successfully screen.

## Step 8 – Word to PDF
* "+" → Word to PDF (also PDF to Word / PPT to PDF) → "Select a file": newest first, rows fade in, date · size under each name, search icon → "Search for files…" + Cancel.
* Tapping a file opens its preview (file name as title, pages on grey) with **Convert to PDF** (or Convert to Word) at the bottom; back returns to the list.
* Convert → "Converting… (x%)" pill → the same "Converted successfully!" screen as Image to PDF (notebook preview, rename, View locally, Open / Share, Tools). The PDF keeps the Word file's name (`Scan_09300939.docx` → `Scan_09300939.pdf`), appears as "Just now" in Recent, and Back goes home.

## Step 9 – Tools tab and PDF editor
* **Tools tab** (same header as All files): **Convert & Create** – Image to PDF, Scan to PDF, Word to PDF, PDF to Word, PPT to PDF, Templates, Create PDF (same flows as the "+" sheet); **Edit** – Edit text, Annotate, Add text, Sign; **Others** – Import files, Manage pages, Recycle bin, Print PDF; card "Don't have the feature you want? Tell us".
* Edit tools, Manage pages and Print PDF open "Select a file" (PDFs). Edit tools open the PDF in the reader with the **PDF editor** on top (X goes back to the reader). The reader's red pen button and the Converted successfully tools open the same editor.
* **PDF editor**: X and (i) at the top; tools Edit text · Add text · Add image · Annotate · Sign. While editing: X, undo, redo, Done.
  * Add text: "Tap anywhere to add text", 15 colours, size slider (72 by default). Tapping the page opens the dark "Enter text" screen (X, ✓, Clear). Text boxes can be moved, rotated (bottom-left handle), resized (bottom-right handle), made wider/narrower (side dots), deleted (red ×) and edited again (tap a selected box).
  * Edit text: real text lines of the PDF are outlined; tap one to retype it (the old line is whited out). Scanned PDFs have no text, so the tool is greyed out, like the original app.
  * Add image: photo picker → picture placed on the page, same handles.
  * Annotate: Color / Size tabs, eraser, highlight, underline, strikethrough, pen.
  * Sign: "+ Add signature" → landscape signature pad (colours, width, undo/redo, clear, Done); signatures are kept for next time (tap to place, long-press to delete).
  * Done writes everything into the PDF, shows "Saved successfully" and stays in the editor.
  * (i) opens the animated how-to with Edit PDF / Sign tabs, Try now and Later.

## Permissions
* **All files access** (`MANAGE_EXTERNAL_STORAGE`) on Android 11+, `READ_EXTERNAL_STORAGE` below. The home screen asks once and shows a "Grant access" button until allowed.
  Google Play only allows `MANAGE_EXTERNAL_STORAGE` for apps whose core purpose needs it (document readers qualify, but you must fill in the permission declaration form in Play Console).
* `POST_NOTIFICATIONS` (Android 13+), requested on the Language screen.
* `CAMERA`, requested the first time the in-app camera opens.

## Step 10 – Settings tab
* **Settings tab**: header with crown (Premium) and search; blue "Remove ads · Unlock all premium features" banner; File manager, FAQ, Share; **General** – Scan settings (Ask every time / Auto crop / No crop), App theme (System default / Light / Dark), Language, Feedback or suggestion; **Others** – Add widget, Explore more apps, Terms of use, Privacy policy, Manage subscriptions (Go to Google Play); version at the bottom.
* **Dark theme**: the app now uses a DayNight theme. Colours for dark mode live in `values-night/colors.xml`; document pages, thumbnails and home-screen widgets stay white on purpose. Line icons use `@color/icon_main` so they switch automatically.
* **FAQ**: chips (View & edit · Manage files · About app) that jump to their section; expandable questions with "Was this helpful?" Yes/No; answers link to Feedback, File manager, Premium and Terms; "Feedback" button at the bottom.
* **Feedback**: 5-star rating, problem chips, details box, up to 4 screenshots; Submit opens the user's e-mail app addressed to `Links.SUPPORT_EMAIL`.
* **Premium screen**: auto-sliding feature pager, feature list, Yearly (free trial, Best value) and Monthly plans, Continue, Restore, legal text.
* **Widgets**: four home-screen widgets (Quick tools, Document reader, PDF editor large/small) shown in the Add widget sheet; "Add to home screen" pins them on Android 8+.
* **To do before release**
  * Billing is connected in step 12 (see below); it still needs the Play Console setup.
  * **Terms of use / Privacy policy** (`terms_html`, `privacy_html` in `values/strings.xml`) are generic placeholders. Have them reviewed and replace them with your real policies (Play also needs a hosted privacy-policy URL).
  * **Explore more apps**: add your apps to `ExploreAppsSheet.APPS`; `Links.DEVELOPER` must match your Google Play developer name.
  * FAQ, legal and premium texts are English only; the short Settings labels are translated into all 22 languages (`tools/gen_translations_step10.py`).

## Step 11 – Tools tab fixes (Annotate, Add text, Sign, Import, Manage pages, Recycle bin, Print)
* **Saved files now appear in Recent.** Everything the app writes (edited PDFs, extracted pages, created, converted, imported and restored files) goes through `data/SavedFiles.kt`: the file goes to the top of Recent ("Just now") and, when you come back to All files, the home screen shows "Loading files…" → "Loaded successfully" while it reloads the counts. Files opened from Tools are added to Recent too.
* **"Tap anywhere to add text" bar works**: tapping it opens the Enter text screen and puts the text in the middle of the page on screen.
* **Text size**: slider 6–72 plus a "72 ▾" list (6, 8 … 56, 72) like the original.
* **Handles** on added text / pictures / signatures: × delete, duplicate (bottom-left), resize + rotate (bottom-right), side dots for text width, pencil on pictures / signatures to replace them.
* **Sign**: saved signatures show a × to delete; signature pad starts on black with width 20 and a check on the selected colour; the (i) button stays visible while signing.
* **Done** shows a spinner while saving, then "Saved successfully".
* **Recycle bin** is its own screen: info bar, "30 days" per file, select mode ("N selected", select all) with Restore / Delete.
* **Manage pages**: Delete is disabled when every page is selected; extracted files are named `Extract_AllDocReader_MMddHHmm.pdf`.
* Crown buttons open Premium; the Tools "Tell us" card opens Feedback.

## Step 12 – Welcome back and Premium (Google Play Billing)
* **Opening the app from its icon** (after closing it with Back, or after the system closed it) always shows the normal "All Document Reader" loading screen, then the **tab the user was on last time**.
* **Welcome back…**: only when the user leaves the main screen with Home / Recents while the app stays open and then comes back (3 s or more later) — not when the app itself opened another app (share, file picker …).
* **Get Premium** (crown on All files / Tools / Settings, Remove ads banner, FAQ link): auto-sliding pictures (Remove ads, Edit & sign PDFs, Convert format, Templates, Manage pages), Restore, X after a moment, "Get Premium", benefits list, **7-Day Free Trial yearly** plan with "Save N%" badge and per-day price, **Monthly** plan, Continue, legal text.
* **Continue → Google Play payment sheet** (add credit / debit card, confirm) via Play Billing Library 7.1.1 (`premium/Billing.kt`). Closing the sheet without buying opens **Start Free Trial** (Today → Get full access → Day 7, "Try 7 Days for Free – Then …/year, only …/day", Continue). If that purchase does not go through, an orange **"Subscribe failed"** pill shows; X goes back to the paywall.
* When Premium is active the Settings "Remove ads" banner is hidden and Continue becomes "Manage subscriptions". The subscription is re-checked with Google Play on every launch; Restore checks it on demand.
* **Play Console setup needed** (prices come from Google Play in the user's currency):
  1. Upload a signed build to a testing track (internal testing is enough).
  2. Monetize → Subscriptions → create product id **`premium`**.
  3. Add base plan **`yearly`** (auto-renewing, 1 year) with an offer that has a **7-day free trial** for new customers, and base plan **`monthly`** (auto-renewing, 1 month). Set prices (e.g. Rs 5,600 / Rs 1,400).
  4. Add your Google account as a license tester to test purchases without being charged.
  Until Play returns the prices (e.g. debug builds not installed from Play), the fallback texts `fallback_*` in `values/strings.xml` are shown and Continue shows "Subscribe failed".
* The laurel badge text (`premium_trusted_top/count/bottom`) says "Loved by our users worldwide"; put your real user count there if you want a number like the original.

## Step 12b – Premium fixes
* Hero pictures no longer use ViewPager2: two opaque slide views take turns sliding in (auto every 3 s, swipe works), so no grey/white box shows over the pictures.
* Whenever Google Play's payment sheet closes without a purchase (or cannot open), **Start Free Trial** is shown once; after that a failure shows "Subscribe failed".
* Flow like the original: Continue → Google Play payment sheet → closed without buying → **Start Free Trial** (every time) → its Continue → payment sheet → closed → orange **Subscribe failed**; its X appears after 3 s and goes back to Get Premium (pictures restart from "Remove ads"). Both Continue buttons have a light streak sweeping across.
* Billing now finds the subscription by product id (`premium`, or `premium_yearly` / `premium_monthly`, list in `Billing.PRODUCT_IDS`) and recognises the yearly / monthly plans by their billing period, so base-plan ids can be anything.
* **Debug builds only – TEST MODE sheet** (`PaywallUi.buy`, `sheet_test_purchase.xml`): when Google Play cannot open its payment sheet (app not installed from Play / subscription not active), a clearly marked TEST MODE sheet appears instead, with the reason in red. "Subscribe (test)" turns Premium on (until the next launch re-checks Google Play); back / tap outside counts as cancelled, so the Free Trial page and "Subscribe failed" can be tested. No card is asked for and nothing is charged. Release builds never show it and always use Google Play.
* Billing problems are logged under the Logcat tag `Billing` (e.g. "Subscription 'premium' not found …").
* Google Play's payment sheet (card / payment method) only opens when the app is **installed from Google Play** (internal testing track is fine) and the subscription **`premium`** with base plans **`yearly`** and **`monthly`** is **active** in Play Console. A build installed from Android Studio cannot open it.

## Step 13 – Image to PDF / Scan to PDF fixes
* **Photo = what you saw**: the camera now saves exactly the area shown on screen (CameraX view port) at maximum quality, so the page in the editor matches the shot.
* **Auto by default**: every new page (camera or gallery) opens on **Auto** — a clean scan: shadows and uneven light removed (paper white), darker ink, sharper text, natural colours; non-paper pictures just get levels + sharpness.
* **Auto crop works much more reliably**: new page-edge detection (text is wiped out with a closing filter, then the page is grown from the middle until its outline, trying several edge strengths; falls back to bright/dark region and edge frame). Also used by the Auto button on the Crop screen. If no page is found a message says so and the whole photo is kept. **No crop** keeps the full photo.

## Step 14 – Saved files in phone Documents and Google Drive
* **Phone storage**: every created / converted / imported / extracted file is saved in the phone's **Documents/AllDocumentReader** folder (`convert`, `create`, `import`) and shows up in the Files app and in the app's lists. Edited PDFs are saved in place (where the file already was).
* **Save to Google Drive** (Settings → General): when On, every file that goes through `SavedFiles` is also uploaded to a Drive folder **"All Document Reader"** (`data/DriveBackup.kt`, Drive REST API with the `drive.file` permission). Edited files replace their earlier Drive copy. Uploads that fail (no internet) are retried on the next save and on the next app start.
* **One-time Google Cloud setup (required for Drive):**
  1. console.cloud.google.com → create a project → APIs & Services → **Enable "Google Drive API"**.
  2. **OAuth consent screen**: External, app name, support e-mail, scope `.../auth/drive.file`; while it is in *Testing*, add your Gmail under **Test users**.
  3. **Credentials → Create credentials → OAuth client ID → Android**: package `com.theoccess.alldocreader` + SHA-1. Make one for the **debug** key (`./gradlew signingReport`) and one for the **Play app-signing** key (Play Console → Setup → App signing).
  Without this, turning the switch on shows "Google sign-in is not set up for this app yet".

## Step 15
* Signature pad: choosing another colour after drawing recolours the whole signature (and new strokes use it too).

## Step 16 – Templates (select → edit → save)
* **Select a template** (Tools → Templates, or Create → Use templates): tabs Resume / Letter / Briefing / Poster over one scrolling list of sections (the tab follows the scroll; tapping a tab jumps to its section), 2-column page thumbnails, and a "Need more templates? Tell us" card at the end (opens Feedback). 16 original templates: 4 resumes, 4 letters (company letter, handwritten letter, fax, business proposal), 3 briefings (meeting minutes, work blueprint, weekly schedule), 5 posters (grads party, marketing agency, webinar, Black Friday, Christmas).
* **Editor** (`ui/templates/TemplateEditorActivity` + `TemplateCanvasView`):
  * Top bar: back, undo, redo, download, share. Pinch to zoom, drag the page to move around.
  * Tap any paragraph / line / heading / picture to select it: thin blue box, duplicate + delete above it, turn-and-resize handle below it; drag to move (a red guide line shows when it is centred on the page). Tap the selected text again to edit its words (full-screen dark text entry with X / ✓ / Clear).
  * Nothing selected: **Add text**, **Add image**.
  * Text selected: **Fonts** (current font + list shown in each font, A–Z index), **Size / Color** (size slider 6–128 + colour circles), **Format** (Bold, Italic, Underline, Strikethrough, bulleted / numbered list), **Delete**, **X**.
  * Picture selected: **Replace**, **Crop**, **H Flip**, **V Flip**, **Delete**, **X**.
  * **Download** → "Select a format" (PNG / JPG / PDF) → Save → "Saving…" → "Saved successfully · Open" banner. Files go to Documents/AllDocumentReader/create, appear in Recent and (if switched on) Google Drive.
  * **Share** sends the page as a PDF.
  * Back with unsaved changes → "Quit now?" (Discard / Save); Save returns to the template list with "Saved successfully".
* Photos picked anywhere in the app are now turned the right way up (EXIF orientation).

## Step 17 – Saved templates everywhere + picture viewer
* Templates saved as **PNG, JPG or PDF** go to the phone's **Documents/AllDocumentReader/create**, show at the top of **Recent**, and are copied to **Google Drive** when that is switched on. If the app has no "All files access" (Android 10+), the file is saved through MediaStore instead, so saving never fails for lack of permission.
* New in-app **picture viewer** (`ui/viewer/ImageViewerActivity` + `ZoomImageView`) for JPG / PNG (also WEBP, BMP, GIF, HEIC): pinch or double-tap to zoom, drag to move, tap to hide the top bar; bookmark, share and the file menu. Pictures open in it from Recent, Bookmarks, the Image category, search, the template "Saved successfully · Open" banner, Import files, and "Open with" from other apps (JPG / PNG).

## Step 18 – Better "Auto crop" page detection
* `ImageOps.detectDocument` rewritten. It assumes the page covers the middle of the photo and, on a 360-px copy, builds four "paper" measures (brightness, whiteness, coolness — white paper is less warm than wood/tables — and a mix), wipes out text with a closing filter, grows the region like the middle at several tolerances, fills its holes and takes the largest 4-corner shape inside it. Candidates are scored by how well their sides sit on real edges and how much they stand out; the winner's sides are then snapped onto the page's edges.
* Works for white paper on light wood (previously it cropped the table), on dark tables, under warm light, rotated pages and pages running off the photo; if no page is found the photo is kept whole ("Couldn't find the page edges").
* Verified by compiling the same Kotlin code on the JVM against frames from the recordings (~0.1 s per photo).
* Pages that run off the photo on several sides (close-up shots on a busy table) are accepted when the page clearly stands out from what is around it, instead of keeping the whole photo.

## Step 19 – Small UI fixes
* Feedback ("Rate our app"): the text box fills the page and the screenshot (camera) button sits just above Submit, like the original.
* Bottom tabs (All files / Tools / Settings): the selected tab's icon is filled, the others are outlined (`ic_nav_*` selectors → `_on` / `_off`).
* Tools tab: section headings (Convert & Create, Edit, Others) are darker, bold and slightly larger (`@color/tools_section`).

## Step 20 – Bookmark message
* Tapping the bookmark icon (file lists on Recent / Bookmarks / All files / categories / search, the reader's menu and the picture viewer) shows a dark rounded pill near the bottom, like the original app: filled bookmark + "Bookmarked", or crossed-out bookmark + "Bookmark removed" (`util/BookmarkPill`). Translated into all 22 languages.

## Step 21 – Ads (Google AdMob), placed like the original app
* **Ad bar** (`ads/AdBar`, anchored adaptive banner): above the bottom tabs on All files / Tools / Settings, at the bottom of file lists, and under the reader's bottom toolbar. Hidden until an ad loads.
* **Full-screen ads** (`ads/Ads.showInterstitial`), at most one every 45 s:
  * after converting (before the "Converted successfully" page – Image to PDF, Scan, Word/PPT/PDF converters),
  * after saving: edited PDF (Edit text / Annotate / Add text / Sign), Manage pages, the reader's "Convert to PDF / Word", templates (Download and Quit → Save).
  * **on app start** (after the "All Document Reader" splash – not on the very first start with language / onboarding) and **after "Welcome back…"** – the splash waits up to 4 s (Welcome back 2.5 s) for the ad, otherwise the app just opens. These two places take turns between the full-screen ad and Google's **app open ad** ("Continue to app ›", unit `AdIds.APP_OPEN`);
  * when opening a file category from the home screen; when that ad closes the list opens and the **Get Premium** page slides over it (once per app run, X appears after a few seconds) – like the original.
* Launch ads are only opened once the screen is fully in front (resumed + focused) and full-screen ads older than 50 min are thrown away – both cause black "Test Ad" screens otherwise. The SDK starts in `App.onCreate` when consent is already known, so ads load during the splash.
* **Premium users see no ads** (bars disappear as soon as the purchase is active).
* **Consent:** Google's UMP consent form is shown automatically to users in the EEA / UK before ads are requested.
* **Before publishing:** replace the TEST IDs – the app ID in `AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`) and the unit IDs in `ads/AdIds.kt` – with your own from the AdMob console, and set up the consent message (Privacy & messaging) there. Test IDs show "Test Ad" and never pay. The close (X) timing on full-screen ads is decided by the ad network (Google), not by the app.

## Step 22 – "Thank you for your support!" (Explore more apps)
* Same look as the original: gift box, "Thank you for your support!", "Don't miss out on our top-rated apps 👇", rows of apps **3 per page** in a swiping pager that turns by itself every 3.5 s, page dots, an **AD** tag and **Open** on every row.
* In the original these rows are the developer's **own apps** (cross-promotion to Google Play). Put yours in `ExploreAppsSheet.APPS` (name, description, package, icon). The rest of the rows are filled with **Google native ads** (`AdIds.NATIVE`, not for premium users). While the ads load a small spinner shows, then the rows appear (up to 5 ads = 2 pages, plus your own apps). Offline with no own apps listed: "Nothing to show right now".
* Opened from Settings → "Explore more apps", and **on every 3rd time the user leaves the app with Back** from the home screen; closing the sheet then closes the app.

## Privacy policy
* Settings → Privacy policy opens the online policy `Links.PRIVACY_POLICY` (https://sites.google.com/view/mob-apps-inc/privacy-policy) in the browser. Use the same link in Play Console → App content → Privacy policy.

## Step 23 – Cleaner "Auto" scans + "No crop" by default
* New **Auto** filter (`ImageOps.autoScan`), like the original's clean scan look: the paper's colour and lighting are estimated on a 1/6 copy (wide max filter removes the text, then min + mean smoothing), every pixel is divided by it → paper pure white, shadows / creases / yellow cast gone; whites clipped and ink deepened; grey pixels made neutral (no colour fringes around letters) while real coloured ink (blue / red pen) is kept. Only areas that look like paper are cleaned that hard, so with **No crop** the table, laptop, mat… just get brighter, even levels instead of turning into strange colours.
* "Choose cropping method" now starts on **No crop** every time after taking photos (Settings → Scan settings still shows the saved choice); the default choice is No crop.

## Step 24 – Google ML Kit Document Scanner for "Take a photo"
* "Take a photo" (Scan to PDF, Image to PDF, Add pages, Retake) now opens **Google's ML Kit Document Scanner** (`ui/scan/DocScanner.kt`, dependency `play-services-mlkit-document-scanner:16.0.0`) – the scanner Google Drive uses: live page-edge detection, auto-capture, automatic crop + straightening, shadow / finger / stain clean-up and its own filters, all on the phone (no data leaves it).
* Its pages come back already cropped, so "Choose cropping method" is skipped for them; they then open in the app's editor (Auto filter, Rotate, Crop, Pages, Done) as before.
* **Fallback:** the scanner needs Google Play services and at least 1.7 GB RAM, and downloads its module the first time it is used (needs internet once). If it can't start, the app's own camera + auto-crop opens instead, so scanning always works. "Choose from gallery" is unchanged.

## Step 25 – Edit screen fixes (scanned PDFs, signatures)
* The edit screen no longer restarts when the phone rotates back from the landscape signature pad (`configChanges` on PdfEditActivity) – before, the restart threw away the signature just placed and showed "This PDF has no editable text" again.
* Opening Edit text on a scanned PDF goes quietly to Add text (no message). The message only shows when the greyed-out Edit text button is tapped.
* Texts, pictures and signatures can be dragged from one page onto another: while dragged the page is lifted above its neighbours (no clipping), and on release `onMovedOffPage` moves the item to the page under the finger (or keeps it at the edge of its page when dropped in the gap).
* Signatures, pictures and text from the bar go on the page that fills most of the screen (the upper one when two pages show about equally), not on the page that happens to be nearest the middle.

## Step 26 – "Do you think All Document Reader is helpful?"
* `ui/main/HelpfulSheet`: mascot with a beating heart, "Do you think All Document Reader is helpful?", **Not really** (→ Feedback screen) / **Helpful** (→ the 5-star rating sheet → Google Play), X to close.
* Shown sometimes when the user comes back to the main screen (any tab) after saving / converting files: after the 1st saved file, then after every 3 more, at most once a day; never again once answered. Every save goes through `SavedFiles.onSaved`, which counts them (`Prefs.savesSinceHelpful`). The 5-star rating sheet still shows once after coming back from a file list (never at the same moment).

## Project layout
```
app/src/main/java/com/theoccess/alldocreader/
  App.kt
  data/      Prefs, FileType/Category, DocFile, FileRepository (MediaStore scan), LibraryStore (recent/bookmarks), Languages
  util/      Ext (formatting, dp), StorageAccess, FileActions, ActionSheet
  ui/splash, ui/language, ui/onboarding, ui/main (MainActivity, HomeFragment, CoachMarkOverlay, Tools, Settings),
  ui/files (FileListActivity, FileAdapter, FileSheets), ui/directories, ui/search
  ui/viewer  (ViewerActivity, OpenDocumentActivity, AnnotateActivity, DocxParser/DocLayout, PptxParser, Converters)
  ui/pages   (PageOrganizerActivity, InsertBlankPagesActivity, PdfEditor)
  ui/scan    (ScanStartActivity, CameraActivity, ScanEditActivity, CropView, ImageOps filters/auto-crop, ImageToPdfActivity, ConvertResultActivity)
  ui/create  (CreateFilesSheet, ImportActivity, TemplatesActivity = template picker, ConvertActivity)
  ui/templates (TplModel, TplLibrary, TplRenderer, TemplateCanvasView, TemplateEditorActivity, TplCropDialog)
  ui/settings (AppTheme, ChoiceSheet, FaqActivity, FeedbackActivity, PolicyActivity, PremiumActivity, WidgetSheet, ExploreAppsSheet)
  widget     (home-screen widget providers)
tools/       gen_vectors.py (regenerates all icons), gen_translations*.py (values-xx strings), check_res.py (resource sanity check)
```

## Notes
* Package / applicationId: `com.theoccess.alldocreader` – change in `app/build.gradle.kts` if needed.
* All icons and illustrations are original vector drawables (no assets copied from the reference app).
* Ads are intentionally left out.

## Step 27 — Search fix, highlights, Excel viewer
- **Search in Document** now works in DOCX/TXT/PPTX as well as PDF: text is normalised (non-breaking
  spaces, curly quotes, dashes) before matching, multi-word queries match across extra spaces.
- Matches are **highlighted** (yellow; current match orange) by `HighlightView` over each page, and the
  list **scrolls to the exact spot** of the match (PDF positions from PDFBox `TextPosition`s, documents
  from `StaticLayout` line/char positions). "No results found" toast when nothing matches.
- **Excel** opens in-app (`SheetParser`): .xlsx/.xlsm/.xltx, .ods, .csv/.tsv — every sheet as a grid with
  column letters, row numbers, the sheet's column widths, shared strings, numbers, dates, %, booleans.
  Old binary **.xls** still opens in another installed app.

## Step 28 — Search hint per type, faster opening, refresh files
- Viewer search hint follows the file: "Search in PDF / PPT / Excel / Document" (22 languages).
- **Faster opening of long files**
  - Word / TXT / Excel: only the first 3 pages are laid out before the document shows; the rest is
    laid out in the background (`DocLayout(eager = false)`, `layoutMore`, `PageSource.finishLoading`)
    and the page list grows as pages are ready. Search waits for the full layout; Convert finishes it.
  - Pictures in Word / PowerPoint are no longer decoded while opening: `LazyBitmap` reads only the
    size, decodes when the page is drawn and keeps decoded pictures in a shared LRU cache.
  - PDF: only page 1 is measured before showing; other page sizes are read in the background and
    pages with a different size are re-measured (`onPagesResized`).
- **New files without restarting**
  - Pull down on the Home list and on every file list to rescan; refresh button in the file-list toolbar.
  - `FileRepository.refresh`: media index first (lists fill at once), then a folder walk for files the
    index doesn't know yet (USB copies, downloads, Bluetooth, Android/media WhatsApp/Telegram); those are
    added and handed to the media scanner.
  - Auto: rescans when the media store changes (ContentObserver, debounced) and when returning to the
    app / a list after 8 s (`refreshIfStale`).

## Step 29 — "New" files, Save to Drive for one file
- `NewFiles`: each complete scan is compared with the paths already known (first scan after install =
  baseline). Newly found files get a **New** tag next to the name and a light-gray row (`new_file_bg`)
  in every file list until opened (`LibraryStore.addRecent` → `markOpened`); app-saved / opened files
  are never new; renames keep the state; tags expire after 30 days. Recently added shows new files first
  (also copies with old dates) and the home "+N" counts unopened new files.
- **Save to Drive** in the file ⋮ menu and the viewer ⋮ menu (`DriveSaveActivity`): Google account /
  permission screen, then the file is uploaded at once to "All Document Reader" in that account's Drive,
  with progress and a result message. Failures are no longer silent: the reason is shown (OAuth client /
  SHA-1 missing, Drive API not enabled, access not allowed / not a test user, offline, Drive full).
  Uploads are streamed (big files are not read into memory).

## Step 30 — Fixes: New rows, Save to Drive, TXT hint, exact highlights
- New-file rows: tags/gray background are re-drawn whenever a file is opened (Recently added re-sorted
  without re-binding unchanged rows before) and on returning to a list.
- Save to Drive (one file) now hands the file to the **Google Drive app's own "Save to Drive" screen**
  (account + folder → Save; no Google Cloud setup needed). Direct API upload only when Drive isn't installed.
- TXT viewer search hint: "Search in TXT".
- Exact search highlights: `HighlightView` translated by the page frame padding twice (boxes 4dp low and
  8dp right) — fixed; Word/TXT/Excel boxes now cover the letters only (no line spacing); PDF boxes use
  the font size around the baseline.

## Step 31 — Cursor fixes merged
- From Cursor: **Image to PDF** picks an image file (`ConvertKind.IMAGE_TO_PDF`, `Converters.imagesToPdf`);
  Edit / Pages / Print also accept pictures (converted to PDF first); **Convert to PDF** button in the
  picture viewer; files opened by convert / edit / pages / print / scan results go to Recent; the app
  accepts shared pictures and text; "+N" next to Recently added clears once that list is visited
  (combined with the per-file "New" tags).
- From Cursor: the Settings auto-copy "Save to Google Drive" (Google sign-in, `DriveBackup`,
  play-services-auth) is removed. "Save to Drive" in the file / viewer ⋮ menus uses the Google Drive
  app's own "Save to Drive" screen (offers to install Drive when it is missing).

## Step 32 — Premium page like the original
- Opening Premium first shows **"Get Ready for Your 7-Day Free Trial"** (dark screen, glowing red check that
  pops in, twinkling dots, orange gradient italic text) for ~1.7 s, then fades into the page (`readyView`).
- The page scrolls like the original: picture slides (6 now, + **Merge & split PDFs**), **Get Premium**,
  all 6 benefits (+ **Merge & split PDFs seamlessly**) and a **User Reviews** section with 3 cards
  (title, 5 stars, name, text, faint quote mark). Plans, **Start Free Trial →** (Continue → for Monthly),
  **Cancel anytime** and **Terms of use | Privacy policy** stay fixed at the bottom; content fades out above them.
- ⚠ The 3 review texts (strings `review1_…`–`review3_…`) are placeholders copied from the original —
  replace them with real reviews of your app before publishing (Google Play policy on misleading content).

## Step 33 — Premium unlocks exactly the "Get Premium" points
`premium/PremiumGate.kt` (free users: **2 free tries per tool per day**, then the Get Premium page;
Premium / free trial: unlimited, crowns hidden). Viewing, scanning, printing, importing stay free.
| Get Premium point | What is gated |
|---|---|
| Ad-free experience | all ads (Ads.kt, already) |
| Edit & sign PDFs easily | Tools → Edit text / Annotate / Add text / Sign, viewer ✎, result-screen edit tools, widget |
| Convert files instantly | Word/PPT/Image → PDF, PDF → Word (Tools, "+" sheet, viewer convert, picture viewer, result screen) |
| Unlock premium templates | 8 of 16 templates have a crown (second half of each category) |
| Complete page management | Manage pages (Tools, viewer Pages button / menu) |
| Merge & split PDFs seamlessly | **new** Tools → Merge PDF (pick PDFs in order, numbered) and Split PDF (every page / every N pages / custom ranges), `ui/pages/PdfTools.kt` |
A free try is counted when the tool really starts (file chosen / Convert tapped); a toast says how many are left.
Change `FREE_USES_PER_DAY` or `PREMIUM_TEMPLATES` in PremiumGate to tune it.

## Step 34 — Tools are free (like the original)
- No crown and no daily limit on any tool: Convert (Image/Word/PPT → PDF, PDF → Word), Edit text, Annotate,
  Add text, Sign, Manage pages, Merge PDF, Split PDF — also from the viewer, picture viewer and result screen.
  `PremiumGate.Feature.limited = false` for these; only the crowned templates (and ads) depend on Premium.
- Tools tab: **Manage pages** moved from Others into Edit, before Merge PDF.

## Step 35 — Manage pages, Merge PDF, Split PDF exactly like the original
- Tools: section renamed **Edit & Manage** (Edit text, Annotate, Add text, Sign, Manage pages, Merge PDF, Split PDF).
- **Manage pages** (Tools): page editor as before; Insert → Images now asks **Take a photo / Choose from gallery /
  Cancel**; after **Done**: "Saved successfully" → ad → the saved PDF opens in the reader.
- **Merge PDF**: "Select a file" with a checkbox on every row ("2 selected", "Continue (2)", needs 2) →
  `MergePdfActivity`: ⋮⋮ drag to reorder, "2 pages · 8.7 KB", ⊖ remove, file+ adds more (current ones ticked),
  back → "Quit merging?" → "Merge (n)" → "Merging… (x%)" → result screen "Merged successfully!".
  Output `Documents/AllDocumentReader/merged/Merged_yyyyMMdd_HHmm.pdf`.
- **Split PDF**: pick a PDF → `SplitPdfActivity` (notebook preview, page count, Split method):
  Custom range → "Select pages" (`SelectPagesActivity`, page grid, select all, "Continue (n)") → part
  "Split_yyyyMMdd_HHmm ✎ / Page: 1,2" (rename, ⊖, file+ adds another part) → "Split (n)";
  Fixed range → Interval settings [− n +] → "Split". "Splitting… (x%)" → ad → "Split successfully!".
  Output in `Documents/AllDocumentReader/split/`.
- Result screen: page-count badge, tools in the original order (+ Manage pages) and
  "Are you satisfied with our "Merge PDF" / "Split PDF" feature?" 😞 Not good / 🥰 Good (asked once per feature).
- Viewer ⋮ menu (PDF): **Merge PDF** (this file pre-ticked) and **Split PDF**.

## Step 36 — Search inside pictures (Word picture pages, scanned PDFs)
- Some Word files (made from PDFs or scans) have pages that are pictures, so "Search in Document" found nothing (0/0).
- New `ui/viewer/PictureOcr.kt`: Google ML Kit text recognition reads the words in those pictures on the phone (no upload).
- `DocPageSource` adds the recognised words to `findMatches`/`pageText`; `PdfPageSource` does the same for PDF pages without text.
- Matches are highlighted on exactly the matching letters, and ▲/▼ jump between them like normal text.
- `ViewerActivity` starts reading the pictures in the background as soon as search opens.
- Dependency `play-services-mlkit-text-recognition:19.0.1`; manifest meta-data `com.google.mlkit.vision.DEPENDENCIES = ocr`. Google Play services downloads the model.

## Step 37 — App Open ad at app start and after "Welcome back…"
- App start (after the splash, not on the very first run) and "Welcome back…" now always show the **App Open** ad ("Continue to app ›"). Before, they took turns and app start used the full-screen interstitial.
- The splash waits up to 5 s for the ad, and "Welcome back…" up to 4 s. Only if no App Open ad arrives in time is a ready interstitial shown instead.
- The App Open ad loads first when the SDK starts, again whenever the app goes to the background, and after a failed load it retries in 5 s, 15 s, 45 s … (up to 2 min apart).
- If Android closed the app in the background and the user reopens it from Recents, "Welcome back…" + the App Open ad now show too (before, nothing showed).
- The app never gets stuck on the loading screen; there's never two ads at once. Logcat tag `Ads` shows why an ad didn't load (e.g. code 3 = no fill).

## Step 38 — Language screen: bottom banner ad, names aligned left
- A banner ad (`AdBar`) at the bottom of the Language screen (first run and from Settings); hidden for premium users and until an ad loads. The ads SDK now starts on this screen, so the banner also shows on the very first run.
- Arabic (العربية) and Persian (فارسی) names are aligned to the left like the other languages (`textDirection="locale"`, `textAlignment="viewStart"`); in an RTL app language all rows align right together.

## Step 39 — The 4 issues, done again carefully (version 1.0.39)
Settings shows "Version 1.0.39", so you can check that the phone runs this build.
1. **App Open ad at app start**
   - Every start, including the very first, goes: loading screen → App Open ad → language / intro / home.
   - The ads SDK and consent start on the splash. The ad waits for the EEA/UK consent form if it is shown.
   - A Premium test purchase ("Subscribe (test)", debug builds) now lasts for one app run only. Before, it was saved, so later starts were treated as Premium and skipped the ad.
2. **App Open ad after "Welcome back…"**
   - Leaving the app with Home, Recents or another app from ANY screen (reader, tools, lists …) and coming back after 3 s shows "Welcome back…" then the App Open ad. Before, this only worked from the main screen.
   - It doesn't show when the app itself opened something (Files app, share, sign-in, settings). Every screen now extends `ui/BaseActivity`, which records that.
   - It also shows when Android had closed the app in the background.
3. **Language screen banner:** an `AdBar` at the bottom shows "Loading ad…" in the banner's space right away, then the ad. It hides only if no ad comes; it's never shown for Premium.
4. **Language names:** each row is laid out left-to-right in code and XML, so Arabic, Persian and Urdu names start on the left like the others.
- Debug builds show a short "Ads: …" message when an App Open ad doesn't show, with the reason (Premium on, no consent, failed with code 3 = no ad to show …). Release builds never show it.

## Step 40 — Reader: swipe tutorial and tap to hide the bars (version 1.0.40)
- **Swipe tutorial:** the first time Horizontal (page by page) is switched on, a dimmed overlay shows a hand moving left and right between yellow arrows, and "Swipe left/right to turn pages" [OK]. It's shown once (`viewer_swipe_tip_shown`).
  - `layout/view_swipe_tutorial.xml`, `ic_swipe_arrows`, `ic_swipe_hand`, `ic_tip_pointer_up`, `bg_tutorial_card`, `bg_outline_pill_large`.
- **Tap to hide the bars:** one tap on the document hides the toolbar, the bottom tools, the page number, the edit button and the status bar, so only the pages show. Another tap brings them back.
  - The ad stays, like the original. Double-tap zoom, pinch and scrolling don't toggle it, and it's off while searching.
  - `ZoomLayout.onSingleTap` (`onSingleTapConfirmed`), `ViewerActivity.setBarsHidden`; the top bar has the id `topBar`.

## Step 41 — Page slider stops above the Edit button (version 1.0.41)
- The ≡ page slider is now `ui/viewer/PageScrollBar` (same handle picture, `ic_fast_scroll_thumb`) instead of RecyclerView's built-in fast scroller, whose track always ran to the very bottom and covered the Edit button.
- Its lowest position (the last page) is just above the Edit button. When there is no Edit button (Word / Excel / PPT / TXT) or the bars are hidden by a tap, it goes down to the bottom.
- Like before: it appears while scrolling, fades after 1.5 s and can be dragged. Touches outside the handle go to the pages. It is shown in Vertical mode only.

## Step 42 — App Open ad after the loading screen every time (version 1.0.42)
- The cause was "none loaded in time": the App Open ad was only requested after `MobileAds.initialize` had finished, which can take several seconds on a cold start, so the ad often arrived after the splash had given up.
- Now the App Open ad is requested right away when the ads SDK starts (allowed without mediation) and the loading screen waits up to 10 s, but only while the ad is still loading. It shows the moment it's ready and goes on at once if it fails.
- If the first request fails during the loading screen, it's tried once more straight away.

## Step 43 — Sharp text in big picture-page Word files (version 1.0.43)
- Pictures in .docx / .pptx were decoded by halving (inSampleSize 1, 2, 4 …) until they fit 1600 px, which could overshoot far below it.
  - A 2480×3508 page scan ended up 620×877, smaller than the screen, so whole-page pictures (e.g. en_Sahih_Al-Bukhari.docx, 1700 pages) looked blurry.
- `LazyBitmap.zipEntry` now halves only while the result stays ≥ 1600 px, then resizes smoothly to 1600 px. That example is now 1131×1600, so the text is clear.

## Step 44 — Page slider: bigger, easier to find, works in long documents (version 1.0.44)
- **Bigger handle:** `ic_page_slider_thumb` is 30 × 46 dp (was 22 × 26 dp), with a soft edge so it shows on white and dark pages.
- **Easier to find:** it shows for a moment when a long document opens, appears while scrolling and fades 2 s after.
- **Dragging by page:** the handle now jumps by page position (`scrollToPositionWithOffset`) instead of scrolling by pixels.
  - Pixel scrolling had to lay out every page in between, so it was slow and jumpy in long files such as a 1700-page DOCX.
  - The top of the track is page 1 and the bottom is the end of the last page. The page number chip follows along.
- Older builds (before 1.0.41) used RecyclerView's own fast scroller, which shrank to thin dashes in long documents. That one is no longer used.

## Step 45 — One page slider for every file type, tap and swipe (version 1.0.45)
- PDF, Word, Excel, PowerPoint and TXT all open in `ViewerActivity`, so they share the same `PageScrollBar`: same 30 × 46 dp handle, same position and the same rule for when it's there.
  - The rule: more than one page and longer than the screen. Before, it needed a document at least 2 screens long, so short files had none.
- **Swipe:** grab the handle and move up or down; it goes page by page, and the page chip follows.
- **Tap:** while the slider is showing, a tap on its strip at the right edge jumps there, and the finger can keep swiping from that point. While it's hidden, taps go to the pages as before.

## Step 46 — "Go to page" while searching no longer reloads / freezes (version 1.0.46)
- **Cause:** the search ran on the single page-drawing thread and went through every page (1000 in sample-1000pages.pdf).
  - After "Go to page", no page could be drawn until the search had finished, which showed blank pages with spinners, freezes and white or black screens.
  - Replaced searches (each typed letter) weren't stopped, so they queued up.
  - When the search ended it scrolled back to the first match on page 1.
- **Fix:**
  - Search has its own thread (`searchDispatcher`), so pages keep drawing while it runs.
  - A replaced or closed search stops at once (`yield()` per page).
  - If "Go to page" was used during a search, the result starts at the first match from that page on.
  - The keyboard stays hidden after the jump.

## Step 47 — Tools → Image to PDF: Take a photo / Choose from gallery / Cancel (version 1.0.47)
- The Tools tile now opens `ScanStartActivity` (Image to PDF mode) instead of the file list, like the original.
- A white screen with ← shows the "Take a photo / Choose from gallery / Cancel" sheet:
  - **Take a photo:** Google ML Kit document scanner (the in-app camera if ML Kit isn't available), the same as Scan to PDF and Add pages.
  - **Choose from gallery:** photo picker (several pictures) → "Choose cropping method".
  - Then the page editor (filters, crop, rotate) → Done → Image to PDF list (reorder, add pages) → Convert → result screen.
  - **Cancel:** back to Tools.

## Step 48 — "Recently added" bar follows dark mode (version 1.0.48)
- `bg_recent_row` had a fixed 70 % white fill, so the bar stayed light in dark mode.
- It now uses `@color/recent_row_bg`: 70 % white in light mode (unchanged) and #23262D in dark mode, the same as other dark cards. The text was already themed.

## Step 49 — Switching theme without a black screen (version 1.0.49)
- **Cause:** choosing a theme in Settings → App theme made Android tear the main screen down and rebuild it, which showed a black screen in between.
- **Fix:**
  - `MainActivity` now handles `uiMode` itself (manifest `configChanges="uiMode"`).
  - When day/night changes it opens a fresh copy of itself with a quick cross-fade and closes the old one. The old screen stays visible until the new one is drawn, so the new theme appears at once, back on the Settings tab.
  - If the main screen isn't in front (e.g. the system dark mode changes while a document is open) it is rebuilt quietly in the background.

## Step 50 — Swipe tutorial hand points with the index finger (version 1.0.50)
- `ic_swipe_hand` is redrawn like the original: the raised finger is on the left of the hand, the thumb sticks out on the lower left, and the three curled fingers step down to the right. Before, there were folded fingers on both sides of the raised one, so it looked like the middle finger.
- The finger tip is still at the top centre, on the yellow touch point. Nothing else changed.

## Step 51 — Swipe tutorial: the original's hand (version 1.0.51)
- The hand is now the picture from the original app (`drawable/img_swipe_hand.png`, cut out with a transparent background): index finger up, thumb on the left, the other fingers curled.
- It's the same size as there (58 × 86 dp, was 64 × 110 dp) and the finger tip sits on the yellow dot. `ic_swipe_hand.xml` was removed. Nothing else changed.

## Step 52 — Excel / PPT / TXT counted after "All files access" is granted (version 1.0.52)
- A scan made before "All files access" was on only sees pictures and the app's own files. Android hides other apps' documents, so the Excel / PPT / TXT tiles showed 0.
- Such a scan could stay in memory after access was granted, because `refresh()` without force kept a finished scan.
- `FileRepository` now remembers whether the last scan had access, and scans again as soon as access is there. This also applies when access arrives while a scan is running.
- The file-index query uses `_data LIKE ?` (already case-insensitive) instead of `LOWER(_data) LIKE ?`.

## Step 53 — Faster search and sharper pages in big picture-page Word files (version 1.0.53)
**Search**
- Reading the words in the page pictures (ML Kit) now starts when the Word file opens, on a low-priority background thread, not when search is opened.
  - It follows the layout as the document grows; the old loop stopped at the pages that were ready when it started.
  - A per-page lock means the background reading and a search never read the same page twice.
- Results now appear while the search is still running. The first match shows at once, the count grows ("1/12…"), and pages still being laid out are searched as soon as they're ready.
  - Before, nothing showed until every page had been laid out and read.

**Sharpness**
- Pictures are no longer shrunk by skipping pixels (`inSampleSize`) below twice the target size, which broke up thin letter strokes in page scans. They're shrunk by smooth halving plus one final resize instead; there's a memory guard for huge pictures.
- Word pages are drawn at 1.5× the screen width (at most 2000 px) and shown scaled down, so text stays sharp, also when zoomed a little.

## Step 54 — Fast tab switching under rapid taps (version 1.0.54)
- **Duplicate tabs:** tab changes were queued (`commit()`), so fast taps could find a not-yet-added tab and add it again. Stacked duplicate screens made each switch slower.
  - `showTab` now runs queued changes first and applies its own change at once (`commitNow()`), and ignores taps after the state is saved.
- **All files tab:** the Recent / Bookmarks list checked every file on storage (exists, size, date) on the main thread each time the tab came back. That now happens in the background, and the list on screen stays until the new one is ready.
- **Tools / Settings:** both are built hidden as soon as the app is idle after start (`prebuildTabs`), so the first tap on them is instant. All files is still built only when it's opened, because it may ask for storage access.

## Step 55 — Premium stays on after restart; no "Premium is on" message (version 1.0.55)
- A test subscription ("Subscribe (test)", debug builds) is now saved with its end date: Monthly 30 days, Yearly 365 days (`test_premium_until`). Since 1.0.39 it lasted for one app run only, so ads came back after a restart.
- Premium = a real Google Play subscription or a test one still running. Google Play's check at start no longer cancels a running test subscription.
- When the period ends (at start, or while the app is open) Premium switches off and ads load again. A real subscription that ends is switched off by Google Play's check at start, as before.
- The debug message "Ads: no App Open ad: Premium is on" is gone: with Premium the app simply goes on, no ad and no message.

## Step 56 — No "Ads: …" messages; Restore message like the original (version 1.0.56)
- The on-screen "Ads: …" notes (e.g. "no App Open ad: none loaded in time") are gone in every build. The reason is still written to Logcat (tag `Ads`). The ad after the loading screen works as before.
- **Restore** (Get Premium and Start Free Trial screens) asks Google Play for an active subscription; a running test subscription counts too.
  - Found: "Premium restored", as before.
  - Not found: the orange pill at the top with the ⓘ icon, "No valid subscription found.", in the same style as the "subscription failed" pill (`PaywallUi.noSubscription`).

## Step 57 — App theme switches instantly (version 1.0.57)
- Choosing a theme opened the new main screen with a fade-in / fade-out, which looked like loading.
- It now swaps with no animation (`FLAG_ACTIVITY_NO_ANIMATION`, `overridePendingTransition(0, 0)`), like the original. Android still shows the new screen only once it's drawn, so there's no black frame.

## Step 58 — App theme switches instantly, in place (version 1.0.58)
- Opening a fresh main screen for the new theme took a moment. Meanwhile the old screen already redrew with half-changed colours, so text disappeared and came back.
- `MainActivity.rebuildForTheme()` now rebuilds the same screen at once, before anything is drawn:
  - it sets the new window, status bar and navigation bar colours;
  - it rebuilds the tabs: the visible one at once, Tools / Settings again when idle;
  - nothing new opens, so there's no loading and no black frame — like the original.
- If the main screen isn't in front (system dark mode changed while a document is open), it's rebuilt quietly in the background as before.

## Step 59 — The whole app in every language (version 1.0.59)
- **Audit:** 270 texts and 7 plurals had no translation in any of the 22 languages, so those parts stayed English: FAQ, Premium / Start Free Trial pages, terms of use, scanner and editor labels, file info, rename / delete / restore messages, widgets, templates editor, etc.
- **Added:** translations of the 193 texts and 7 plurals that are used, for ar, de, es, fa, fr, in, it, ja, ko, ms, pl, pt, ru, th, tl, tr, uk, ur, uz, vi, zh-rCN and zh-rTW. Each language has its own plural forms (Arabic zero/one/two/few/many, Russian / Ukrainian / Polish one/few/many).
  - Source: `tools/i18n_step59/<locale>.json`; `tools/gen_translations_step59.py` writes them in.
- **Checked:** placeholders (%1$s, %d, %%), escapes (\' \" \n), HTML tags and XML validity in every file.
- **Kept the same in every language:** 57 texts no longer used anywhere, format-only texts (e.g. "%1$d / %2$d"), file-type names (PDF, Word, Excel, PPT, TXT, PNG, JPG), "B&W" filter names, price fallbacks and reviewer names.
- The tiny "Search…" in the first intro picture now uses `@string/search_for_files`.
- **Not translated on purpose:** sample text inside document templates (it's document content) and the debug-only test purchase sheet.
