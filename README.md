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

## Step 19 – Small UI fixes
* Feedback ("Rate our app"): the text box fills the page and the screenshot (camera) button sits just above Submit, like the original.
* Bottom tabs (All files / Tools / Settings): the selected tab's icon is filled, the others are outlined (`ic_nav_*` selectors → `_on` / `_off`).
* Tools tab: section headings (Convert & Create, Edit, Others) are darker, bold and slightly larger (`@color/tools_section`).

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
