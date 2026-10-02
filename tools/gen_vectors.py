"""Generates the app's vector drawables. Run from project root: python3 tools/gen_vectors.py"""
import os
OUT = "app/src/main/res/drawable"
os.makedirs(OUT, exist_ok=True)

def p(d, fill=None, stroke=None, sw=None, alpha=None, cap="round", join="round", falpha=None):
    a = [f'android:pathData="{d}"']
    if fill: a.append(f'android:fillColor="{fill}"')
    if falpha is not None: a.append(f'android:fillAlpha="{falpha}"')
    if stroke:
        a.append(f'android:strokeColor="{stroke}"')
        a.append(f'android:strokeWidth="{sw}"')
        a.append(f'android:strokeLineCap="{cap}"')
        a.append(f'android:strokeLineJoin="{join}"')
        if alpha is not None: a.append(f'android:strokeAlpha="{alpha}"')
    return "    <path " + "\n        ".join(a) + " />"

def vec(name, size, vp, paths, tint=None, h=None, vph=None):
    h = h or size; vph = vph or vp
    t = f'\n    android:tint="{tint}"' if tint else ""
    body = "\n".join(paths)
    with open(f"{OUT}/{name}.xml", "w") as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{h}dp"
    android:viewportWidth="{vp}"
    android:viewportHeight="{vph}"{t}>
{body}
</vector>
''')

W = "#FFFFFF"
# ---------------- Logo (4 quadrants) ----------------
def logo_paths(ox=0, oy=0, s=1.0):
    # all coords authored in 100x100 space; used directly
    return [
        p("M10,0 H49 V49 H0 V10 A10,10 0 0 1 10,0 Z", fill="#1560D6"),
        p("M51,0 H90 A10,10 0 0 1 100,10 V49 H51 Z", fill="#E31B1B"),
        p("M0,51 H49 V100 H10 A10,10 0 0 1 0,90 Z", fill="#0E9F48"),
        p("M51,51 H100 V90 A10,10 0 0 1 90,100 H51 Z", fill="#F46A0A"),
        # side sheets (pale document)
        p("M31,12 H43 V37 H31 Z", fill=W, falpha="0.28"),
        p("M31,63 H43 V88 H31 Z", fill=W, falpha="0.28"),
        p("M84,63 H95 V88 H84 Z", fill=W, falpha="0.28"),
        # W
        p("M8,15 L12.5,35 L18.5,22 L24.5,35 L29,15", stroke=W, sw="4.2"),
        p("M34,18 H41 M34,24.5 H41 M34,31 H41", stroke=W, sw="2.2", alpha="0.9"),
        # PDF
        p("M55,34 V16 H59.5 A4.5,4.5 0 0 1 59.5,25 H55", stroke=W, sw="2.8"),
        p("M67,34 V16 H69 A6.5,9 0 0 1 69,34 Z", stroke=W, sw="2.8"),
        p("M81,34 V16 H88 M81,25 H86.5", stroke=W, sw="2.8"),
        # X
        p("M8,66 L26,86 M26,66 L8,86", stroke=W, sw="4.2"),
        p("M34,69 H41 M34,75.5 H41 M34,82 H41", stroke=W, sw="2.2", alpha="0.9"),
        # P
        p("M59,87 V65 H66.5 A6.5,6.5 0 0 1 66.5,78 H59", stroke=W, sw="4.2"),
        p("M89.5,70 A5.5,5.5 0 1 0 95,75.5 H89.5 Z", fill=W, falpha="0.9"),
    ]
vec("ic_logo", 72, 100, logo_paths())

# launcher foreground: logo placed in 108 safe zone (66dp) -> scale via group
with open(f"{OUT}/ic_launcher_foreground.xml", "w") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="25" android:translateY="25" android:scaleX="0.58" android:scaleY="0.58">
''' + "\n".join(logo_paths()) + '''
    </group>
</vector>
''')

# ---------------- Category document icons ----------------
DOC = "M6,2 H15 L20.5,7.5 V20.5 A1.5,1.5 0 0 1 19,22 H6 A1.5,1.5 0 0 1 4.5,20.5 V3.5 A1.5,1.5 0 0 1 6,2 Z"
FOLD = "M15,2 V6 A1.5,1.5 0 0 0 16.5,7.5 H20.5 Z"
def doc(name, color, glyph, size=40):
    vec(name, size, 24, [p(DOC, fill=color), p(FOLD, fill=W, falpha="0.45")] + glyph)

G = dict(
    all=[p("M8,11 V17.5 H17 V11.5 H12.8 L11.6,10 H8.8 Z", stroke=W, sw="1.4")],
    pdf=[p("M7.6,17 V12.2 H8.9 A1.2,1.2 0 0 1 8.9,14.6 H7.6", stroke=W, sw="0.95"),
         p("M11.2,17 V12.2 H11.8 A1.8,2.4 0 0 1 11.8,17 Z", stroke=W, sw="0.95"),
         p("M15,17 V12.2 H17.3 M15,14.6 H16.8", stroke=W, sw="0.95")],
    word=[p("M7.8,10.5 L9.5,17 L12.5,12.3 L15.5,17 L17.2,10.5", stroke=W, sw="1.7")],
    excel=[p("M8.8,10.5 L16.2,17.5 M16.2,10.5 L8.8,17.5", stroke=W, sw="1.8")],
    ppt=[p("M10,18 V10.5 H13.2 A2.6,2.6 0 0 1 13.2,15.7 H10", stroke=W, sw="1.8")],
    txt=[p("M8.3,10.8 H16.7 M12.5,10.8 V18", stroke=W, sw="1.8")],
    image=[p("M7.5,17.5 L10.5,13.2 L12.6,15.8 L14.2,14 L17.5,17.5 Z", fill=W),
           p("M15.3,11.3 m-1.3,0 a1.3,1.3 0 1 0 2.6,0 a1.3,1.3 0 1 0 -2.6,0", fill=W)],
    dir=[p("M8,11 H12.5 M8,14 H11 M8,17 H11.5", stroke=W, sw="1.3"),
         p("M15,14.2 m-2.2,0 a2.2,2.2 0 1 0 4.4,0 a2.2,2.2 0 1 0 -4.4,0", stroke=W, sw="1.3"),
         p("M16.6,15.8 L18,17.3", stroke=W, sw="1.4")],
)
doc("ic_cat_all", "#1A6FE0", G["all"])
doc("ic_cat_pdf", "#D10000", G["pdf"])
doc("ic_cat_word", "#1560D6", G["word"])
doc("ic_cat_excel", "#008A3B", G["excel"])
doc("ic_cat_ppt", "#F26201", G["ppt"])
doc("ic_cat_txt", "#5F6B90", G["txt"])
doc("ic_cat_image", "#FDA800", G["image"])
doc("ic_cat_dir", "#1A6FE0", G["dir"])
# unknown file
doc("ic_file_other", "#8E949E", [p("M8.5,12 H16.5 M8.5,15 H16.5 M8.5,18 H13", stroke=W, sw="1.3")])

# ---------------- Folder icon (directory browser) ----------------
vec("ic_folder_filled", 40, 24, [
    p("M3,6.5 A1.5,1.5 0 0 1 4.5,5 H9.6 L11.6,7 H19.5 A1.5,1.5 0 0 1 21,8.5 V18.5 A1.5,1.5 0 0 1 19.5,20 H4.5 A1.5,1.5 0 0 1 3,18.5 Z", fill="#FFB938"),
    p("M3,9.5 H21 V18.5 A1.5,1.5 0 0 1 19.5,20 H4.5 A1.5,1.5 0 0 1 3,18.5 Z", fill="#FFCA5C")])

# ---------------- Line icons (24dp, tintable) ----------------
LC = "@color/icon_main"  # black in light theme, light grey in dark theme
def line(name, paths, size=24, tint=None):
    vec(name, size, 24, paths, tint=tint)

line("ic_check", [p("M4.5,12.5 L9.5,17.5 L19.5,6.5", stroke=LC, sw="2.2")])
line("ic_search", [p("M11,11 m-6.5,0 a6.5,6.5 0 1 0 13,0 a6.5,6.5 0 1 0 -13,0", stroke=LC, sw="2.1"),
                   p("M16,16 L20.5,20.5", stroke=LC, sw="2.2")])
line("ic_add", [p("M12,5 V19 M5,12 H19", stroke=LC, sw="2.6")])
line("ic_close", [p("M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5", stroke=LC, sw="2.2")])
line("ic_back", [p("M20,12 H5 M11,5.5 L4.5,12 L11,18.5", stroke=LC, sw="2.2")])
line("ic_more_vert", [p("M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z", fill=LC)])
line("ic_bookmark", [p("M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z", fill=LC)])
line("ic_bookmark_border", [p("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z", fill=LC)])
line("ic_sort", [p("M4,7 H20 M4,12 H15 M4,17 H10", stroke=LC, sw="2")])
line("ic_share", [p("M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z", fill=LC)])
line("ic_info", [p("M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z", fill=LC)])
line("ic_open", [p("M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z", fill=LC)])
line("ic_chevron_right", [p("M9.5,6 L15.5,12 L9.5,18", stroke=LC, sw="2")])
line("ic_history", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="2"), p("M12,7.5 V12 L15,14", stroke=LC, sw="2")])
line("ic_language", [p("M12,12 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", stroke=LC, sw="1.8"),
                     p("M3,12 H21 M12,3 C8.5,7 8.5,17 12,21 M12,3 C15.5,7 15.5,17 12,21", stroke=LC, sw="1.8")])
line("ic_star", [p("M12,3.5 L14.6,8.9 L20.5,9.7 L16.2,13.8 L17.3,19.7 L12,16.9 L6.7,19.7 L7.8,13.8 L3.5,9.7 L9.4,8.9 Z", stroke=LC, sw="1.8")])
line("ic_shield", [p("M12,2.8 L19.5,5.8 V11.2 C19.5,15.9 16.3,19.8 12,21.2 C7.7,19.8 4.5,15.9 4.5,11.2 V5.8 Z", stroke=LC, sw="1.8"), p("M8.8,12 L11.2,14.3 L15.4,9.9", stroke=LC, sw="1.8")])
line("ic_feedback", [p("M4,5 H20 V16 H9 L5,19.5 V16 H4 Z", stroke=LC, sw="1.8"), p("M8,9.5 H16 M8,12.5 H13", stroke=LC, sw="1.8")])
line("ic_camera", [p("M4,8 H7.5 L9,5.5 H15 L16.5,8 H20 V19 H4 Z", stroke=LC, sw="1.8"), p("M12,13.3 m-3.3,0 a3.3,3.3 0 1 0 6.6,0 a3.3,3.3 0 1 0 -6.6,0", stroke=LC, sw="1.8")])
line("ic_image_line", [p("M4,5 H20 V19 H4 Z", stroke=LC, sw="1.8"), p("M4.5,17 L9.5,11.5 L13,15.5 L15.5,13 L19.5,17", stroke=LC, sw="1.8"), p("M15.5,8.5 m-1.2,0 a1.2,1.2 0 1 0 2.4,0 a1.2,1.2 0 1 0 -2.4,0", fill=LC)])
line("ic_text_file", [p("M6,3 H14 L19,8 V21 H6 Z", stroke=LC, sw="1.8"), p("M9,12 H16 M12.5,12 V18", stroke=LC, sw="1.8")])

# bottom navigation
# bottom navigation: outline when not selected, filled when selected (state_checked selector)
def eo(path_str): return path_str.replace("<path ", '<path android:fillType="evenOdd"\n        ', 1)
FOLDER = "M3,6.5 A1.5,1.5 0 0 1 4.5,5 H9.6 L11.6,7 H19.5 A1.5,1.5 0 0 1 21,8.5 V18.5 A1.5,1.5 0 0 1 19.5,20 H4.5 A1.5,1.5 0 0 1 3,18.5 Z"
line("ic_nav_files_off", [p(FOLDER, stroke=LC, sw="1.8"), p("M8,15.5 H13", stroke=LC, sw="1.8")])
line("ic_nav_files_on", [eo(p(FOLDER + " M8,14.6 H13 A0.9,0.9 0 0 1 13,16.4 H8 A0.9,0.9 0 0 1 8,14.6 Z", fill=LC))])
SQ = "M4,4 H10.5 V10.5 H4 Z M13.5,4 H20 V10.5 H13.5 Z M4,13.5 H10.5 V20 H4 Z M13.5,13.5 H20 V20 H13.5 Z"
line("ic_nav_tools_off", [p(SQ, stroke=LC, sw="1.8")])
line("ic_nav_tools_on", [p(SQ, fill=LC, stroke=LC, sw="1.8")])
HEX = "M12,2.8 L20,7.4 V16.6 L12,21.2 L4,16.6 V7.4 Z"
RING = "M12,12 m-3,0 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0"
line("ic_nav_settings_off", [p(HEX, stroke=LC, sw="1.8"), p(RING, stroke=LC, sw="1.8")])
line("ic_nav_settings_on", [eo(p(HEX + " " + RING + " Z", fill=LC, stroke=LC, sw="1.8"))])
for nm in ("files", "tools", "settings"):
    with open(f"{OUT}/ic_nav_{nm}.xml", "w") as fh:
        fh.write(f'''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_nav_{nm}_on" android:state_checked="true" />
    <item android:drawable="@drawable/ic_nav_{nm}_off" />
</selector>
''')

# tools grid icons (colored)
def tool(name, color, glyph):
    vec(name, 44, 24, [p("M12,12 m-11,0 a11,11 0 1 0 22,0 a11,11 0 1 0 -22,0", fill=color, falpha="0.12")] + glyph)
tool("ic_tool_merge", "#D10000", [p("M7,6 V11 C7,13 9,14 12,14 C15,14 17,13 17,11 V6 M12,14 V19 M9.5,16.5 L12,19 L14.5,16.5", stroke="#D10000", sw="1.7")])
tool("ic_tool_split", "#F26201", [p("M12,5 V10 C12,12 9,13 7,15 V19 M12,10 C12,12 15,13 17,15 V19 M4.5,19 H9.5 M14.5,19 H19.5", stroke="#F26201", sw="1.7")])
tool("ic_tool_img2pdf", "#1560D6", [p("M5.5,6 H18.5 V18 H5.5 Z", stroke="#1560D6", sw="1.7"), p("M6,16 L10,11.5 L13,14.5 L15,12.5 L18,16", stroke="#1560D6", sw="1.7")])
tool("ic_tool_lock", "#008A3B", [p("M6.5,11 H17.5 V19 H6.5 Z M8.8,11 V8.5 A3.2,3.2 0 0 1 15.2,8.5 V11", stroke="#008A3B", sw="1.7")])
tool("ic_tool_unlock", "#5F6B90", [p("M6.5,11 H17.5 V19 H6.5 Z M8.8,11 V8.5 A3.2,3.2 0 0 1 14.8,7", stroke="#5F6B90", sw="1.7")])
tool("ic_tool_compress", "#FDA800", [p("M12,4 V10 M9,7.5 L12,10.5 L15,7.5 M12,20 V14 M9,16.5 L12,13.5 L15,16.5 M5,12 H19", stroke="#E09500", sw="1.7")])
tool("ic_tool_scan", "#1A6FE0", [p("M5,9 V5 H9 M15,5 H19 V9 M19,15 V19 H15 M9,19 H5 V15 M4,12 H20", stroke="#1A6FE0", sw="1.7")])
tool("ic_tool_templates", "#9C27B0", [p("M5.5,5 H18.5 V19 H5.5 Z M5.5,10 H18.5 M10.5,10 V19", stroke="#9C27B0", sw="1.7")])

# ---------------- Colored illustrations ----------------
vec("ic_crown", 28, 24, [
    p("M3,8.5 L7.5,12 L12,5 L16.5,12 L21,8.5 L19.3,18.5 H4.7 Z", fill="#FFC21A"),
    p("M4.7,18.5 H19.3 V20.5 H4.7 Z", fill="#F59E0B"),
    p("M9.3,11.8 L12,16 L14.7,11.8", stroke="#E53935", sw="1.6"),
    p("M12,5 m-1.2,0 a1.2,1.2 0 1 0 2.4,0 a1.2,1.2 0 1 0 -2.4,0", fill="#FFE082")])

vec("ic_hand_pointer", 56, 64, [
    p("M22,8 A4.5,4.5 0 0 1 31,8 V30 A4,4 0 0 1 39,30 V32 A3.75,3.75 0 0 1 46.5,33 V35 A3.5,3.5 0 0 1 53.5,36 V46 C53.5,55 47,61 38,61 H33 C26,61 22,58 18,52 L10,41 C8,38 12,34.5 15,37 L22,43 Z", fill="#FFE3D3", stroke="#D9967A", sw="1.6"),
    p("M31,30 V38 M39,32 V39 M46.5,35 V40", stroke="#D9967A", sw="1.3")], h=64, vph=64)

vec("ic_paper_plane", 64, 64, [
    p("M6,30 L58,10 L40,52 L30,38 Z", fill="#2F80ED"),
    p("M30,38 L58,10 L26,50 Z", fill="#1557C0"),
    p("M30,38 L40,52 L58,10 Z", fill="#5BA0FF")], h=64, vph=64)

vec("ic_empty_state", 150, 150, [
    p("M28,78 C22,52 44,34 70,38 C92,20 130,30 128,62 C140,82 124,108 98,106 C80,120 46,116 38,100 C28,98 26,88 28,78 Z", fill="#E8F1FC"),
    p("M40,52 C52,44 62,46 70,40", stroke="#9FB6D6", sw="1", alpha="0.8"),
    # yellow doc
    p("M34,40 L46,36 L50,50 L38,54 Z", fill="#FFC21A"),
    p("M38,43 L45,41 M39,47 L46,45", stroke="#FFFFFF", sw="1.4"),
    # hands
    p("M28,96 C30,80 40,70 52,68 L64,78 L60,98 C50,104 36,104 28,96 Z", fill="#F9B8A0"),
    p("M122,96 C120,80 110,70 98,68 L86,78 L90,98 C100,104 114,104 122,96 Z", fill="#F9B8A0"),
    p("M52,68 C56,58 62,58 66,64", stroke="#F9B8A0", sw="6"),
    p("M98,68 C94,58 88,58 84,64", stroke="#F9B8A0", sw="6"),
    # binoculars
    p("M62,64 H88 V74 H62 Z", fill="#1557C0"),
    p("M60,82 m-15,0 a15,15 0 1 0 30,0 a15,15 0 1 0 -30,0", fill="#1A6FE0"),
    p("M90,82 m-15,0 a15,15 0 1 0 30,0 a15,15 0 1 0 -30,0", fill="#1A6FE0"),
    p("M60,82 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", fill="#FFFFFF"),
    p("M90,82 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", fill="#FFFFFF"),
    # sparkles
    p("M116,36 L118,41 L123,43 L118,45 L116,50 L114,45 L109,43 L114,41 Z", fill="#FFC21A"),
    p("M80,110 L82,115 L87,117 L82,119 L80,124 L78,119 L73,117 L78,115 Z", fill="#1A6FE0")], h=150, vph=150)

vec("ic_create_pdf_badge", 44, 44, [
    p("M8,4 H28 L36,12 V38 A2,2 0 0 1 34,40 H8 A2,2 0 0 1 6,38 V6 A2,2 0 0 1 8,4 Z", fill="#E53935"),
    p("M11,15 H26", stroke="#FFFFFF", sw="0.1"),
    p("M11,24 V16 H13.5 A2.2,2.2 0 0 1 13.5,20.4 H11 M18,24 V16 H19 A3,4 0 0 1 19,24 Z M25,24 V16 H29 M25,20 H28", stroke="#FFFFFF", sw="1.6"),
    p("M11,30 H22 M11,34 H18", stroke="#FFFFFF", sw="1.6", alpha="0.7"),
    p("M34,34 m-8,0 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0", fill="#1A6FE0"),
    p("M34,30 V38 M30,34 H38", stroke="#FFFFFF", sw="2")], h=44, vph=44)

# splash isometric background decoration (very faint)
vec("bg_splash_deco", 360, 360, [
    p("M40,250 L120,210 L200,250 L120,290 Z", fill="#EEF2FA"),
    p("M40,250 V300 L120,340 V290 Z", fill="#E6ECF7"),
    p("M200,250 V300 L120,340 V290 Z", fill="#F1F4FB"),
    p("M190,170 L260,135 L330,170 L260,205 Z", fill="#EEF2FA"),
    p("M190,170 V230 L260,265 V205 Z", fill="#E8EDF8"),
    p("M330,170 V230 L260,265 V205 Z", fill="#F2F5FB"),
    p("M92,150 L130,131 L168,150 L130,169 Z", fill="#F1F4FB"),
    p("M92,150 V186 L130,205 V169 Z", fill="#EBF0F9"),
    p("M168,150 V186 L130,205 V169 Z", fill="#F4F6FC"),
    p("M280,282 m-26,0 a26,26 0 1 0 52,0 a26,26 0 1 0 -52,0", stroke="#E4EAF6", sw="4")], h=360, vph=360)

# onboarding floating tiles (rounded square with white document glyph)
def tile(name, color, glyph):
    vec(name, 64, 24, [p("M4.5,1 H19.5 A3.5,3.5 0 0 1 23,4.5 V19.5 A3.5,3.5 0 0 1 19.5,23 H4.5 A3.5,3.5 0 0 1 1,19.5 V4.5 A3.5,3.5 0 0 1 4.5,1 Z", fill=color, falpha="0.92"),
                       p("M8,5 H15 L18,8 V19 H8 Z", fill=W, falpha="0.95"),
                       p("M15,5 V8 H18", fill="#DDDDDD")] + glyph)
tile("ic_tile_word", "#1560D6", [p("M5,9.5 H12 V16.5 H5 Z", fill="#1560D6"), p("M6.2,11 L7.3,15.2 L8.5,12.4 L9.7,15.2 L10.8,11", stroke=W, sw="0.8"), p("M13,11 H16.5 M13,13 H16.5 M13,15 H16.5", stroke="#1560D6", sw="0.7")])
tile("ic_tile_pdf", "#E53935", [p("M5,9.5 H13 V15 H5 Z", fill="#E53935"), p("M6.2,14 V10.6 H7 A0.8,0.8 0 0 1 7,12.2 H6.2 M8.6,14 V10.6 H9 A1.2,1.7 0 0 1 9,14 Z M10.9,14 V10.6 H12.3 M10.9,12.3 H12", stroke=W, sw="0.55"), p("M13.8,11 H16.5 M13.8,13 H16.5 M9,17 H16.5", stroke="#E53935", sw="0.7")])
tile("ic_tile_excel", "#16A34A", [p("M5,9.5 H12 V16.5 H5 Z", fill="#16A34A"), p("M6.5,11 L10.5,15 M10.5,11 L6.5,15", stroke=W, sw="0.9"), p("M13,11 H16.5 M13,13 H16.5 M13,15 H16.5", stroke="#16A34A", sw="0.7")])
tile("ic_tile_ppt", "#F46A0A", [p("M5,9.5 H12 V16.5 H5 Z", fill="#F46A0A"), p("M7.2,15.3 V10.8 H8.8 A1.3,1.3 0 0 1 8.8,13.4 H7.2", stroke=W, sw="0.9"), p("M13,11 H16.5 M13,13 H16.5 M13,15 H16.5", stroke="#F46A0A", sw="0.7")])
tile("ic_tile_text", "#E53935", [p("M8.5,8 H15.5 M12,8 V17", stroke="#E53935", sw="1.4"), p("M5.5,5 H9 V8.5 H5.5 Z", fill="#E53935")])
print("ok")

# ---- onboarding extras ----
line("ic_crop", [p("M6,3 V18 H21 M3,6 H18 V21", stroke=LC, sw="2")])
line("ic_undo", [p("M9,8 H15 A4.5,4.5 0 0 1 15,17 H8 M9,8 L6,5 M9,8 L6,11", stroke=LC, sw="1.8")])
line("ic_redo", [p("M15,8 H9 A4.5,4.5 0 0 0 9,17 H16 M15,8 L18,5 M15,8 L18,11", stroke=LC, sw="1.8")])
line("ic_download", [p("M12,4 V14 M8,10.5 L12,14.5 L16,10.5 M5,15.5 V19 H19 V15.5", stroke=LC, sw="1.8")])
line("ic_menu", [p("M4,7 H20 M4,12 H20 M4,17 H20", stroke=LC, sw="2")])
line("ic_pen", [p("M5,19 L6,15 L15.5,5.5 L18.5,8.5 L9,18 Z M13.5,7.5 L16.5,10.5", stroke=LC, sw="1.8")])
line("ic_underline", [p("M7,4 V11 A5,5 0 0 0 17,11 V4 M5,20 H19", stroke=LC, sw="2")])
line("ic_strike", [p("M16.5,7 C16,5.2 14.3,4 12,4 C9.5,4 7.5,5.4 7.5,7.5 C7.5,9.2 9,10.3 12,11 M4,12 H20 M8,16.5 C8.6,18.5 10.3,20 12.3,20 C15,20 17,18.4 17,16.2", stroke=LC, sw="1.8")])
vec("ic_avatar", 80, 80, [
    p("M40,40 m-40,0 a40,40 0 1 0 80,0 a40,40 0 1 0 -80,0", fill="#DCE3EC"),
    p("M40,34 m-13,0 a13,13 0 1 0 26,0 a13,13 0 1 0 -26,0", fill="#C99A7C"),
    p("M27,31 C27,20 53,20 53,31 C50,25 32,25 27,31 Z", fill="#3A2A20"),
    p("M14,72 C16,56 28,51 40,51 C52,51 64,56 66,72 A40,40 0 0 1 14,72 Z", fill="#1F2E4A"),
    p("M35,51 L40,62 L45,51 Z", fill="#FFFFFF"),
    p("M39,55 L40,70 L41,55 Z", fill="#3B6FB6")], h=80, vph=80)
vec("ic_pen_circle", 48, 48, [
    p("M24,24 m-22,0 a22,22 0 1 0 44,0 a22,22 0 1 0 -44,0", fill="#E53935"),
    p("M15,33 L16,29 L27.5,17.5 L30.5,20.5 L19,32 Z", stroke="#FFFFFF", sw="2.2"),
    p("M14,35 H24", stroke="#FFFFFF", sw="2.2")], h=48, vph=48)
vec("ic_tooltip_tail", 16, 10, [p("M0,0 H16 L10,9 Q8,11 6,9 Z", fill="#CCE4FF")], h=10, vph=10)
print("extras ok")

# ---- step 2: file list / file actions ----
line("ic_select", [p("M4,4 H20 V20 H4 Z", stroke=LC, sw="2"), p("M8,12 L11,15 L16.5,9", stroke=LC, sw="2")])
line("ic_filter", [p("M4,6 H20 M4,11.5 H14 M4,17 H11", stroke=LC, sw="2"), p("M18,11 V19 M15.3,16.5 L18,19.3 L20.7,16.5", stroke=LC, sw="2")])
line("ic_select_all", [p("M8,3.5 H20.5 V16 H8 Z", stroke=LC, sw="1.8"), p("M4,7.5 V20.5 H16.5", stroke=LC, sw="1.8"), p("M11,9.8 L13.3,12.1 L17.5,7.9", stroke=LC, sw="1.8")])
line("ic_rename", [p("M7,4 H4 V20 H7 M17,4 H20 V20 H17", stroke=LC, sw="1.8"), p("M8.5,8.5 H15.5 M12,8.5 V16", stroke=LC, sw="1.8")])
line("ic_home_add", [p("M7,2.5 H17 A1.5,1.5 0 0 1 18.5,4 V20 A1.5,1.5 0 0 1 17,21.5 H7 A1.5,1.5 0 0 1 5.5,20 V4 A1.5,1.5 0 0 1 7,2.5 Z", stroke=LC, sw="1.8"), p("M12,8.5 V15.5 M8.5,12 H15.5", stroke=LC, sw="1.8")])
line("ic_delete", [p("M4,6.5 H20 M9.5,6.5 V4 H14.5 V6.5 M6,6.5 L7,20.5 H17 L18,6.5", stroke=LC, sw="1.8"), p("M10,10.5 V16.5 M14,10.5 V16.5", stroke=LC, sw="1.8")])
line("ic_clear_circle", [p("M12,12 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", fill=LC), p("M9,9 L15,15 M15,9 L9,15", stroke="#FFFFFFFF", sw="1.8")])

vec("ic_star_filled", 40, 24, [p("M12,2.8 L14.8,8.6 L21.1,9.4 L16.5,13.8 L17.7,20.1 L12,17.1 L6.3,20.1 L7.5,13.8 L2.9,9.4 L9.2,8.6 Z", fill="#FFC21A", stroke="#F5A300", sw="0.8")])
vec("ic_star_empty", 40, 24, [p("M12,2.8 L14.8,8.6 L21.1,9.4 L16.5,13.8 L17.7,20.1 L12,17.1 L6.3,20.1 L7.5,13.8 L2.9,9.4 L9.2,8.6 Z", stroke="#C9CDD4", sw="1.2")])
vec("ic_star_hint", 40, 24, [p("M12,2.8 L14.8,8.6 L21.1,9.4 L16.5,13.8 L17.7,20.1 L12,17.1 L6.3,20.1 L7.5,13.8 L2.9,9.4 L9.2,8.6 Z", fill="#FFF4D1", stroke="#FFB300", sw="1.3"), p("M9.5,12.5 Q12,15 14.5,12.5", stroke="#FFB300", sw="1")])
vec("ic_curved_arrow", 36, 24, [p("M3,19 C10,19 16,15 20,7 M15.5,8 L20.3,6.3 L21.3,11.2", stroke="#FFA000", sw="2")])

vec("ic_mascot", 120, 120, [
    p("M60,20 m-6,0 a6,6 0 1 0 12,0 a6,6 0 1 0 -12,0", fill="#FFD54F"),
    p("M60,26 V34", stroke="#8C9BB5", sw="2"),
    p("M20,70 C20,44 38,32 60,32 C82,32 100,44 100,70 C100,94 82,104 60,104 C38,104 20,94 20,70 Z", fill="#EEF1FF"),
    p("M30,68 C30,52 44,44 60,44 C76,44 90,52 90,68 C90,82 76,88 60,88 C44,88 30,82 30,68 Z", fill="#FFFFFF"),
    p("M50,64 m-3,0 a3,3.6 0 1 0 6,0 a3,3.6 0 1 0 -6,0 M70,64 m-3,0 a3,3.6 0 1 0 6,0 a3,3.6 0 1 0 -6,0", fill="#2B3350"),
    p("M55,73 Q60,77 65,73", stroke="#2B3350", sw="2"),
    p("M18,76 C10,72 12,60 22,62 C30,64 40,76 44,86 Z", fill="#C9D1FF"),
    p("M102,76 C110,72 108,60 98,62 C90,64 80,76 76,86 Z", fill="#C9D1FF"),
    p("M60,100 C44,90 42,78 52,76 C56,75.5 59,78 60,81 C61,78 64,75.5 68,76 C78,78 76,90 60,100 Z", fill="#F0364A"),
    p("M16,40 C12,36 18,31 21,35 C24,31 30,36 26,40 L21,45 Z", fill="#F0364A"),
    p("M100,32 L102,37 L107,39 L102,41 L100,46 L98,41 L93,39 L98,37 Z", fill="#FFC21A"),
    p("M38,16 L39.5,19.5 L43,21 L39.5,22.5 L38,26 L36.5,22.5 L33,21 L36.5,19.5 Z", fill="#FFC21A")], h=120, vph=120)

vec("ic_shortcut_permission", 140, 110, [
    p("M70,55 m-50,0 a50,44 0 1 0 100,0 a50,44 0 1 0 -100,0", fill="#EAF3FF"),
    p("M40,34 L78,22 L96,78 L58,90 Z", fill="#2F6FE4"),
    p("M44,38 L76,28 L90,74 L58,84 Z", fill="#FFFFFF"),
    p("M50,46 L70,40 M52,54 L74,47 M54,62 L70,57", stroke="#BFD4FA", sw="3"),
    p("M84,20 H110 V46 H84 Z", fill="#FFD54F"),
    p("M88,24 H106 V42 H88 Z", fill="#FFE9A0"),
    p("M30,70 C24,64 28,56 36,58 L60,68 C64,70 64,76 60,78 L44,92 C38,96 30,92 30,86 Z", fill="#F9B8A0"),
    p("M96,62 L100,66 M104,58 L110,58 M100,52 L104,48", stroke="#2F6FE4", sw="2.4"),
    p("M24,30 L26,34 L30,36 L26,38 L24,42 L22,38 L18,36 L22,34 Z", fill="#FFC21A")], h=110, vph=110)
print("step2 ok")

# ---- step 3: directories, viewer, page organizer ----
vec("ic_folder_blue", 40, 24, [
    p("M3,6.5 A1.5,1.5 0 0 1 4.5,5 H9.6 L11.6,7 H19.5 A1.5,1.5 0 0 1 21,8.5 V18.5 A1.5,1.5 0 0 1 19.5,20 H4.5 A1.5,1.5 0 0 1 3,18.5 Z", fill="#1D5FE0"),
    p("M3,9.5 H21 V18.5 A1.5,1.5 0 0 1 19.5,20 H4.5 A1.5,1.5 0 0 1 3,18.5 Z", fill="#2F74F0"),
    p("M8,15.5 H12.5", stroke="#FFFFFF", sw="1.6")])
vec("ic_file_unknown", 40, 24, [
    p("M6,2.5 H18 A2,2 0 0 1 20,4.5 V19.5 A2,2 0 0 1 18,21.5 H6 A2,2 0 0 1 4,19.5 V4.5 A2,2 0 0 1 6,2.5 Z", fill="#EEF1F6"),
    p("M9.6,9.6 A2.4,2.4 0 1 1 13.2,11.7 C12.4,12.2 12,12.7 12,13.8", stroke="#5F6B7E", sw="1.6"),
    p("M12,16.6 m-0.9,0 a0.9,0.9 0 1 0 1.8,0 a0.9,0.9 0 1 0 -1.8,0", fill="#5F6B7E")])
vec("ic_unsupported", 140, 120, [
    p("M70,62 m-52,0 a52,46 0 1 0 104,0 a52,46 0 1 0 -104,0", fill="#EAF3FF"),
    p("M58,20 L90,30 L80,62 L48,52 Z", fill="#FFC21A"),
    p("M66,34 L70,42 L78,43 L72,48 L74,56 L66,52 L59,56 L60,48 L55,42 L63,41 Z", fill="#FFFFFF", falpha="0.9"),
    p("M50,58 C46,70 44,84 48,100 L66,102 C70,92 74,82 74,70 L70,52 C68,46 62,48 62,54 L62,64 C60,56 52,52 50,58 Z", fill="#F9B8A0"),
    p("M62,54 V66", stroke="#E39A7E", sw="1.5"),
    p("M84,18 L86,22 L90,24 L86,26 L84,30 L82,26 L78,24 L82,22 Z", fill="#1A6FE0"),
    p("M100,70 L101.5,73 L104.5,74.5 L101.5,76 L100,79 L98.5,76 L95.5,74.5 L98.5,73 Z", fill="#1A6FE0")], h=120, vph=120)
line("ic_vertical", [p("M5,4 H19 V10.5 H5 Z M5,13.5 H19 V20 H5 Z", stroke=LC, sw="1.8")])
line("ic_horizontal", [p("M3.5,5 H10.5 V19 H3.5 Z M13.5,5 H20.5 V19 H13.5 Z", stroke=LC, sw="1.8")])
line("ic_rotate", [p("M6,9 H14 V20 H6 Z", stroke=LC, sw="1.8"), p("M13,3.5 C17,3.5 19.5,6 19.5,10 M17.3,8.2 L19.5,10.4 L21.6,8.2", stroke=LC, sw="1.6")])
line("ic_invert", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="1.8"), p("M12,3.5 A8.5,8.5 0 0 1 12,20.5 Z", fill=LC)])
line("ic_search_text", [p("M11,11 m-6.5,0 a6.5,6.5 0 1 0 13,0 a6.5,6.5 0 1 0 -13,0", stroke=LC, sw="1.8"), p("M16,16 L20.5,20.5", stroke=LC, sw="2"), p("M8.3,8.6 H13.7 M11,8.6 V14", stroke=LC, sw="1.6")])
PAGE_CUT = "M13.3,21 H6.5 A2,2 0 0 1 4.5,19 V5 A2,2 0 0 1 6.5,3 H14 L19,8 V13.3"
CONV_BADGE = [p("M15.3,17.4 A2.9,2.9 0 0 1 20.5,16.5", stroke=LC, sw="1.6"), p("M20.9,14.8 L20.6,16.6 L18.8,16.3", stroke=LC, sw="1.4"),
              p("M20.7,18.6 A2.9,2.9 0 0 1 15.5,19.5", stroke=LC, sw="1.6"), p("M15.1,21.2 L15.4,19.4 L17.2,19.7", stroke=LC, sw="1.4")]
line("ic_convert_pdf", [p(PAGE_CUT, stroke=LC, sw="1.8"),
                        p("M7.2,13 V8.2 H8.7 A1.3,1.3 0 0 1 8.7,10.8 H7.2 M10.6,13 V8.2 H11.2 A2.1,2.4 0 0 1 11.2,13 Z M14.4,13 V8.2 H16.6 M14.4,10.6 H16.2", stroke=LC, sw="1.35")] + CONV_BADGE)
line("ic_convert_word", [p(PAGE_CUT, stroke=LC, sw="1.8"),
                         p("M7.3,8 L8.9,14 L10.7,10.2 L12.5,14 L14.1,8", stroke=LC, sw="1.8")] + CONV_BADGE)
line("ic_pages", [p(PAGE_CUT, stroke=LC, sw="1.8"), p("M8,8.8 H12 M8,12.2 H15", stroke=LC, sw="1.8"),
                  p("M18,14.9 L20.7,16.45 V19.55 L18,21.1 L15.3,19.55 V16.45 Z", stroke=LC, sw="1.6")])
line("ic_insert_page", [p("M6,3 H14 L18,7 V21 H6 Z", stroke=LC, sw="1.8"), p("M12,10 V17 M8.5,13.5 H15.5", stroke=LC, sw="1.8")])
line("ic_extract", [p("M13,3 H6 V21 H18 V14", stroke=LC, sw="1.8"), p("M11,12 L20,3 M14.5,3 H20 V8.5", stroke=LC, sw="1.8")])
line("ic_setup", [p("M12,12 m-3,0 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0", stroke=LC, sw="1.8"), p("M12,3 V5.5 M12,18.5 V21 M3,12 H5.5 M18.5,12 H21 M5.6,5.6 L7.4,7.4 M16.6,16.6 L18.4,18.4 M5.6,18.4 L7.4,16.6 M16.6,7.4 L18.4,5.6", stroke=LC, sw="1.8")])
line("ic_reorder", [p("M4,4 H20 V20 H4 Z", stroke=LC, sw="1.6"), p("M9.5,8 V16 M7.5,10 L9.5,8 L11.5,10 M14.5,16 V8 M12.5,14 L14.5,16 L16.5,14", stroke=LC, sw="1.5")])
line("ic_orient_portrait", [p("M7,3 H14 L17,6 V21 H7 Z", stroke=LC, sw="1.7"), p("M9.5,11 H14.5 M9.5,14.5 H14.5", stroke=LC, sw="1.5")])
line("ic_orient_landscape", [p("M3,6.5 H18 L21,9.5 V17.5 H3 Z", stroke=LC, sw="1.7"), p("M6.5,11 H15 M6.5,14 H15", stroke=LC, sw="1.5")])
line("ic_minus", [p("M5,12 H19", stroke=LC, sw="2.2")])
line("ic_help", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="1.7"), p("M9.7,9.6 A2.4,2.4 0 1 1 13.2,11.7 C12.4,12.2 12,12.7 12,13.8", stroke=LC, sw="1.7"), p("M12,16.7 m-1,0 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0", fill=LC)])
line("ic_restore", [p("M4.5,12 A7.5,7.5 0 1 1 7,17.6", stroke=LC, sw="1.8"), p("M2.5,9.8 L4.5,12.3 L7,10", stroke=LC, sw="1.8"), p("M12,8 V12 L14.8,13.8", stroke=LC, sw="1.8")])
line("ic_delete_forever", [p("M4,6.5 H20 M9.5,6.5 V4 H14.5 V6.5 M6,6.5 L7,20.5 H17 L18,6.5", stroke=LC, sw="1.8"), p("M9.8,11 L14.2,15.4 M14.2,11 L9.8,15.4", stroke=LC, sw="1.8")])
line("ic_print", [p("M7,9 V3.5 H17 V9 M7,17 H4 V9 H20 V17 H17 M7,14 H17 V20.5 H7 Z", stroke=LC, sw="1.7")])
line("ic_arrow_up", [p("M12,19 V5 M6,11 L12,5 L18,11", stroke=LC, sw="2")])
line("ic_arrow_down", [p("M12,5 V19 M6,13 L12,19 L18,13", stroke=LC, sw="2")])
vec("ic_check_circle", 24, 24, [p("M12,12 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", fill="#1FB35A"), p("M7.8,12.3 L10.7,15.1 L16.3,9.3", stroke="#FFFFFF", sw="2")])
vec("ic_fast_scroll_thumb", 22, 24, [p("M3,1 H19 A3,3 0 0 1 22,4 V20 A3,3 0 0 1 19,23 H3 A3,3 0 0 1 0,20 V4 A3,3 0 0 1 3,1 Z", fill="#2B303A"), p("M6,8 H16 M6,12 H16 M6,16 H16", stroke="#FFFFFF", sw="1.6")], h=26, vph=24)

# blank-page template icons (for the strip)
TC = "#7A8494"
def tpl(name, paths): vec(name, 26, 24, [p("M5,2.5 H19 V21.5 H5 Z", stroke=TC, sw="1.4")] + paths)
tpl("ic_tpl_blank", [])
tpl("ic_tpl_line1", [p("M7.5,7 H16.5 M7.5,10 H16.5 M7.5,13 H16.5 M7.5,16 H16.5 M7.5,19 H16.5", stroke=TC, sw="1")])
tpl("ic_tpl_line2", [p("M7.5,7.5 H16.5 M7.5,12 H16.5 M7.5,16.5 H16.5", stroke=TC, sw="1")])
tpl("ic_tpl_notebook", [p("M7.5,7 H16.5 M7.5,10 H16.5 M7.5,13 H16.5 M7.5,16 H16.5 M7.5,19 H16.5", stroke=TC, sw="1"), p("M9.2,3 V21", stroke="#E57373", sw="1")])
tpl("ic_tpl_cornell", [p("M9.5,3 V17 M5,17 H19", stroke=TC, sw="1.2")])
tpl("ic_tpl_grid", [p("M9.7,2.5 V21.5 M14.3,2.5 V21.5 M5,7.2 H19 M5,12 H19 M5,16.8 H19", stroke=TC, sw="1")])
tpl("ic_tpl_graph", [p("M7.8,2.5 V21.5 M10.6,2.5 V21.5 M13.4,2.5 V21.5 M16.2,2.5 V21.5 M5,5.3 H19 M5,8.1 H19 M5,10.9 H19 M5,13.7 H19 M5,16.5 H19 M5,19.3 H19", stroke=TC, sw="0.7")])
print("step3 ok")
vec("ic_clock_badge", 24, 24, [p("M12,12 m-10,0 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0", fill="#1A6FE0"), p("M12,6.8 V12.3 L15.4,14.3", stroke="#FFFFFF", sw="1.9")])
print("clock ok")

# ---- step 4: create files sheet ----
def badge_pdf(x, y):  # small red PDF badge at x,y (size 11)
    return [p(f"M{x},{y+1.5} A1.5,1.5 0 0 1 {x+1.5},{y} H{x+9.5} A1.5,1.5 0 0 1 {x+11},{y+1.5} V{y+8.5} A1.5,1.5 0 0 1 {x+9.5},{y+10} H{x+1.5} A1.5,1.5 0 0 1 {x},{y+8.5} Z", fill="#E53935"),
            p(f"M{x+2},{y+7.5} V{y+2.5} H{x+3.2} A1.1,1.1 0 0 1 {x+3.2},{y+4.7} H{x+2} M{x+5},{y+7.5} V{y+2.5} H{x+5.4} A1.3,2.5 0 0 1 {x+5.4},{y+7.5} Z M{x+8},{y+7.5} V{y+2.5} H{x+9.6} M{x+8},{y+5} H{x+9.3}", stroke="#FFFFFF", sw="0.7")]
def badge_word(x, y):
    return [p(f"M{x},{y+1.5} A1.5,1.5 0 0 1 {x+1.5},{y} H{x+9.5} A1.5,1.5 0 0 1 {x+11},{y+1.5} V{y+8.5} A1.5,1.5 0 0 1 {x+9.5},{y+10} H{x+1.5} A1.5,1.5 0 0 1 {x},{y+8.5} Z", fill="#1560D6"),
            p(f"M{x+2.2},{y+2.6} L{x+3.6},{y+7.6} L{x+5.5},{y+4.3} L{x+7.4},{y+7.6} L{x+8.8},{y+2.6}", stroke="#FFFFFF", sw="1")]
TILE = "M5,2 H19 A3,3 0 0 1 22,5 V19 A3,3 0 0 1 19,22 H5 A3,3 0 0 1 2,19 V5 A3,3 0 0 1 5,2 Z"
vec("ic_create_import", 32, 24, [p("M2.5,6 A1.5,1.5 0 0 1 4,4.5 H9.6 L11.6,6.5 H20 A1.5,1.5 0 0 1 21.5,8 V18.5 A1.5,1.5 0 0 1 20,20 H4 A1.5,1.5 0 0 1 2.5,18.5 Z", fill="#1E88E5"),
                                p("M2.5,10 H21.5 V18.5 A1.5,1.5 0 0 1 20,20 H4 A1.5,1.5 0 0 1 2.5,18.5 Z", fill="#42A5F5")])
vec("ic_create_templates", 32, 24, [p(TILE, fill="#1FA75A"), p("M6,6 H18 M6,6 V18 M6,11 H18 M11,11 V18", stroke="#FFFFFF", sw="1.6"),
                                   p("M19,19 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0", fill="#1A6FE0"), p("M19,16.8 V21.2 M16.8,19 H21.2", stroke="#FFFFFF", sw="1.3")])
vec("ic_cv_image2pdf", 32, 24, [p(TILE, fill="#FFB300"), p("M5.5,16.5 L9.5,11 L12.5,14.5 L14.5,12.5 L17,16.5 Z", fill="#FFFFFF"),
                                p("M15.5,8 m-1.6,0 a1.6,1.6 0 1 0 3.2,0 a1.6,1.6 0 1 0 -3.2,0", fill="#FFFFFF")] + badge_pdf(12.5, 13.5))
vec("ic_cv_scan", 32, 24, [p(TILE, fill="#26C6DA"), p("M6,9.5 V6 H9.5 M14.5,6 H18 V9.5 M18,14.5 V18 H14.5 M9.5,18 H6 V14.5", stroke="#FFFFFF", sw="1.7"),
                           p("M6,12 H18", stroke="#FFFFFF", sw="1.7")])
vec("ic_cv_word2pdf", 32, 24, [p(TILE, fill="#1E6FE6"), p("M5.5,7 L7.5,15 L10.5,9.5 L13.5,15 L15.5,7", stroke="#FFFFFF", sw="1.8")] + badge_pdf(12.5, 13.5))
vec("ic_cv_pdf2word", 32, 24, [p(TILE, fill="#E53935"), p("M5.5,15 V7 H7.7 A2,2 0 0 1 7.7,11 H5.5 M10.5,15 V7 H11.2 A2.3,4 0 0 1 11.2,15 Z M15,15 V7 H18 M15,11 H17.5", stroke="#FFFFFF", sw="1.2")] + badge_word(12.5, 13.5))
vec("ic_cv_ppt2pdf", 32, 24, [p(TILE, fill="#F4511E"), p("M8,17 V7 H11.8 A3,3 0 0 1 11.8,13 H8", stroke="#FFFFFF", sw="1.9")] + badge_pdf(12.5, 13.5))
vec("ic_camera_fab", 24, 24, [p("M4,8 H7.5 L9,5.5 H15 L16.5,8 H20 V19 H4 Z", stroke="#FFFFFF", sw="1.8"), p("M12,13.3 m-3.3,0 a3.3,3.3 0 1 0 6.6,0 a3.3,3.3 0 1 0 -6.6,0", stroke="#FFFFFF", sw="1.8")])
vec("ic_convert_done", 96, 24, [p("M12,12 m-10,0 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0", fill="#E7F7EE"), p("M12,12 m-7,0 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0", fill="#1FB35A"), p("M8.6,12.2 L11,14.5 L15.5,9.8", stroke="#FFFFFF", sw="1.8")])
vec("ic_convert_fail", 96, 24, [p("M12,12 m-10,0 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0", fill="#FDECEC"), p("M12,12 m-7,0 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0", fill="#E53935"), p("M9.5,9.5 L14.5,14.5 M14.5,9.5 L9.5,14.5", stroke="#FFFFFF", sw="1.8")])
print("step4 ok")

# ---- step 5 ----
vec("ic_none", 24, 24, [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke="#FF0A5FD9", sw="1.6"), p("M6,18 L18,6", stroke="#FF0A5FD9", sw="1.6")])
line("ic_eraser", [p("M8.5,20 H20 M4.8,14.2 L13.8,5.2 A1.6,1.6 0 0 1 16.1,5.2 L19.8,8.9 A1.6,1.6 0 0 1 19.8,11.2 L11,20 H8.5 L4.8,16.3 A1.5,1.5 0 0 1 4.8,14.2 Z M9.2,9.8 L15.2,15.8", stroke=LC, sw="1.7")])
line("ic_highlighter", [p("M5,20 H19 M8,16.5 L6.5,18 H10 M8,16.5 L15.8,4.8 A1.5,1.5 0 0 1 17.9,4.4 L19.1,5.2 A1.5,1.5 0 0 1 19.5,7.3 L12,17.5 Z", stroke=LC, sw="1.7")])
print("step5 ok")

# ---- step 6: Image to PDF / Scan ----
BOLT = "M13.2,2.8 L6,13.4 H11.6 L10.6,21.2 L18,10.4 H12.4 Z"
line("ic_flash_on", [p(BOLT, stroke=LC, sw="1.7")])
line("ic_flash_off", [p(BOLT, stroke=LC, sw="1.7"), p("M3.5,3.5 L20.5,20.5", stroke=LC, sw="1.8")])
line("ic_flash_auto", [p("M11.2,2.8 L4.5,12.6 H9.6 L8.7,19.8 L15.4,10 H10.4 Z", stroke=LC, sw="1.6"), p("M15.6,21.4 L18.2,14.6 L20.8,21.4 M16.5,19.2 H19.9", stroke=LC, sw="1.4")])
line("ic_grid", [p("M9,3.5 V20.5 M15,3.5 V20.5 M3.5,9 H20.5 M3.5,15 H20.5", stroke=LC, sw="1.8")])
SPK = "M4,9.5 H7.5 L12,5.5 V18.5 L7.5,14.5 H4 Z"
line("ic_sound_on", [p(SPK, stroke=LC, sw="1.7"), p("M15.2,9 A4,4 0 0 1 15.2,15 M17.6,6.6 A7.4,7.4 0 0 1 17.6,17.4", stroke=LC, sw="1.7")])
line("ic_sound_off", [p(SPK, stroke=LC, sw="1.7"), p("M15.5,9.5 L20.5,14.5 M20.5,9.5 L15.5,14.5", stroke=LC, sw="1.7")])
line("ic_zoom_in", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="1.7"), p("M12,8 V16 M8,12 H16", stroke=LC, sw="1.7")])
line("ic_zoom_out", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="1.7"), p("M8,12 H16", stroke=LC, sw="1.7")])
vec("ic_focus_frame", 64, 64, [p("M4,18 V4 H18 M46,4 H60 V18 M60,46 V60 H46 M18,60 H4 V46", stroke="#FFFFFFFF", sw="2.5")])
line("ic_rotate_right", [p("M9.5,9 H19 A1,1 0 0 1 20,10 V20 A1,1 0 0 1 19,21 H9.5 A1,1 0 0 1 8.5,20 V10 A1,1 0 0 1 9.5,9 Z", stroke=LC, sw="1.7"),
                        p("M4,12.5 V9 A5,5 0 0 1 9,4 H12.5", stroke=LC, sw="1.7"), p("M10.5,2 L12.8,4 L10.5,6", stroke=LC, sw="1.7")])
line("ic_rotate_left", [p("M14.5,9 H5 A1,1 0 0 0 4,10 V20 A1,1 0 0 0 5,21 H14.5 A1,1 0 0 0 15.5,20 V10 A1,1 0 0 0 14.5,9 Z", stroke=LC, sw="1.7"),
                         p("M20,12.5 V9 A5,5 0 0 0 15,4 H11.5", stroke=LC, sw="1.7"), p("M13.5,2 L11.2,4 L13.5,6", stroke=LC, sw="1.7")])
line("ic_no_crop", [p("M4,9 V4 H9 M15,4 H20 V9 M20,15 V20 H15 M9,20 H4 V15", stroke=LC, sw="1.7"),
                    p("M4.5,4.5 L9.5,9.5 M19.5,4.5 L14.5,9.5 M19.5,19.5 L14.5,14.5 M4.5,19.5 L9.5,14.5", stroke=LC, sw="1.5")])
line("ic_auto_crop", [p("M4,9 V4 H9 M15,4 H20 V9 M20,15 V20 H15 M9,20 H4 V15", stroke=LC, sw="1.7"),
                      p("M8.8,16 L12,8 L15.2,16 M10,13.2 H14", stroke=LC, sw="1.6")])
line("ic_compare", [p("M5,5 H19 A1.5,1.5 0 0 1 20.5,6.5 V17.5 A1.5,1.5 0 0 1 19,19 H5 A1.5,1.5 0 0 1 3.5,17.5 V6.5 A1.5,1.5 0 0 1 5,5 Z", stroke=LC, sw="1.6"),
                    p("M3.5,6.5 A1.5,1.5 0 0 1 5,5 H12 V19 H5 A1.5,1.5 0 0 1 3.5,17.5 Z", fill=LC, falpha="0.25"), p("M12,3 V21", stroke=LC, sw="1.6")])
line("ic_check_circle_outline", [p("M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0", stroke=LC, sw="1.8"), p("M8,12.3 L10.8,15 L16,9.3", stroke=LC, sw="1.8")])
line("ic_folder_outline", [p("M3.5,7 A1.5,1.5 0 0 1 5,5.5 H9.6 L11.6,7.5 H19 A1.5,1.5 0 0 1 20.5,9 V17.5 A1.5,1.5 0 0 1 19,19 H5 A1.5,1.5 0 0 1 3.5,17.5 Z", stroke=LC, sw="1.7")])
vec("ic_sparkle", 24, 24, [p("M12,1 C12.8,7.5 16.5,11.2 23,12 C16.5,12.8 12.8,16.5 12,23 C11.2,16.5 7.5,12.8 1,12 C7.5,11.2 11.2,7.5 12,1 Z", fill="#FFFFC53D")])
rings = []
for i in range(12):
    y = 8 + i * 12.8
    rings.append(p(f"M2,{y} A5,2.6 0 1 1 12,{y} A5,2.6 0 1 1 2,{y} Z", stroke="#FF5C6270", sw="1.3"))
    rings.append(p(f"M10,{y} m-1.3,0 a1.3,1.3 0 1 0 2.6,0 a1.3,1.3 0 1 0 -2.6,0", fill="#FF3A3F4A"))
vec("ic_spiral", 14, 14, rings, h=160, vph=160)
RT = "M7,2 H23 A5,5 0 0 1 28,7 V23 A5,5 0 0 1 23,28 H7 A5,5 0 0 1 2,23 V7 A5,5 0 0 1 7,2 Z"
vec("ic_rt_edit_text", 30, 30, [p(RT, fill="#FFF0483E"), p("M8,8 H22 V22 H8 Z", stroke="#FFFFFFFF", sw="1.6"), p("M11,11.5 H19 M15,11.5 V19", stroke="#FFFFFFFF", sw="2")])
vec("ic_rt_annotate", 30, 30, [p(RT, fill="#FFFFA41B"), p("M9,21 L10,17.5 L18.5,9 A1.8,1.8 0 0 1 21,11.5 L12.5,20 Z", fill="#FFFFFFFF"), p("M8,23.5 H22", stroke="#FFFFFFFF", sw="1.6")])
vec("ic_rt_add_text", 30, 30, [p(RT, fill="#FF8B5CF6"), p("M8.5,21.5 L13,9 L17.5,21.5 M10.1,17.2 H15.9", stroke="#FFFFFFFF", sw="2"), p("M21,7.5 V12.5 M18.5,10 H23.5", stroke="#FFFFFFFF", sw="1.7")])
vec("ic_rt_pdf2word", 30, 30, [p("M6,2 H18 L25,9 V25 A3,3 0 0 1 22,28 H6 A3,3 0 0 1 3,25 V5 A3,3 0 0 1 6,2 Z", fill="#FFF0483E"),
    p("M7,17 V10.5 H8.8 A1.6,1.6 0 0 1 8.8,13.7 H7 M11.2,17 V10.5 H12 A2.2,3.25 0 0 1 12,17 Z M16,17 V10.5 H18.8 M16,13.5 H18.4", stroke="#FFFFFFFF", sw="1.2")] + [
    p("M17,18 H27.5 A1.5,1.5 0 0 1 29,19.5 V27.5 A1.5,1.5 0 0 1 27.5,29 H17 A1.5,1.5 0 0 1 15.5,27.5 V19.5 A1.5,1.5 0 0 1 17,18 Z", fill="#FF1E6FE6"),
    p("M17.8,20.3 L19.3,26.6 L21.2,22.8 L23.1,26.6 L24.6,20.3", stroke="#FFFFFFFF", sw="1.2")])
vec("ic_rt_print", 30, 30, [p("M9,4 H21 V10 H9 Z", fill="#FFFFC08A"), p("M4,11 A2,2 0 0 1 6,9 H24 A2,2 0 0 1 26,11 V21 A2,2 0 0 1 24,23 H4 Z", fill="#FFFF7A1A"),
    p("M9,18 H21 V26 H9 Z", fill="#FFFFFFFF"), p("M11.5,21 H18.5 M11.5,23.5 H16.5", stroke="#FFFF7A1A", sw="1.2")])
vec("ic_rt_sign", 30, 30, [p(RT, fill="#FF1EA7FF"), p("M9.5,20.5 L10.5,16.5 L18,9 A2,2 0 0 1 20.8,11.8 L13.3,19.3 Z", fill="#FFFFFFFF"), p("M8.5,23 C11,21.8 13,24.2 15.5,23 C17.5,22 19.5,23.4 21.5,22.6", stroke="#FFFFFFFF", sw="1.3")])

def ill(name, auto):
    paths = [p("M8,0 H312 A8,8 0 0 1 320,8 V142 A8,8 0 0 1 312,150 H8 A8,8 0 0 1 0,142 V8 A8,8 0 0 1 8,0 Z", fill="#FFE6E9ED")]
    # earbuds
    paths += [p("M28,18 m-9,0 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", fill="#FFFDFDFD"), p("M34,24 L52,40", stroke="#FFFDFDFD", sw="4"),
              p("M58,12 m-7,0 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0", fill="#FFF5F6F7"), p("M62,17 L74,33", stroke="#FFF5F6F7", sw="3.5"),
              p("M22,52 C40,40 60,60 80,44", stroke="#FFCED3D9", sw="1.2")]
    # keyboard
    paths += [p("M250,2 L320,-20 L320,70 L276,84 Z", fill="#FFFAFAFB")]
    for r in range(4):
        for c in range(4):
            x = 262 + c * 13 + r * 4; y = 16 + r * 13 - c * 4
            paths.append(p(f"M{x},{y} h9 v8 h-9 Z", stroke="#FFC9CED6", sw="1"))
    # paper (slightly turned)
    paths += [p("M136,26 L200,20 L208,122 L140,128 Z", fill="#FFFFFFFF")]
    for i in range(9):
        y0 = 40 + i * 9
        paths.append(p(f"M{146 + i*0.3},{y0 - 0.2 * i} L{196 + i*0.4},{y0 - 4 - 0.2 * i}", stroke="#FFB4BAC4", sw="1.6" if i else "2.4"))
    paths.append(p("M176,112 C180,106 184,116 190,108 C193,105 196,110 199,106", stroke="#FF5B6270", sw="1"))
    if auto:
        q = [(136, 26), (200, 20), (208, 122), (140, 128)]
    else:
        q = [(96, 8), (236, 8), (236, 142), (96, 142)]
    d = f"M{q[0][0]},{q[0][1]} L{q[1][0]},{q[1][1]} L{q[2][0]},{q[2][1]} L{q[3][0]},{q[3][1]} Z"
    paths.append(p(d, stroke="#FF1E6FE6", sw="1.8"))
    for (x, y) in q:
        paths.append(p(f"M{x},{y} m-3.5,0 a3.5,3.5 0 1 0 7,0 a3.5,3.5 0 1 0 -7,0", fill="#FFFFFFFF"))
        paths.append(p(f"M{x},{y} m-3.5,0 a3.5,3.5 0 1 0 7,0 a3.5,3.5 0 1 0 -7,0", stroke="#FF1E6FE6", sw="1.4"))
    for i in range(4):
        a = q[i]; b = q[(i + 1) % 4]
        mx = (a[0] + b[0]) / 2; my = (a[1] + b[1]) / 2
        horiz = abs(b[0] - a[0]) > abs(b[1] - a[1])
        if horiz:
            paths.append(p(f"M{mx-6},{my-2} h12 a2,2 0 0 1 0,4 h-12 a2,2 0 0 1 0,-4 Z", fill="#FFFFFFFF"))
            paths.append(p(f"M{mx-6},{my-2} h12 a2,2 0 0 1 0,4 h-12 a2,2 0 0 1 0,-4 Z", stroke="#FF1E6FE6", sw="1.2"))
        else:
            paths.append(p(f"M{mx-2},{my-6} v12 a2,2 0 0 0 4,0 v-12 a2,2 0 0 0 -4,0 Z", fill="#FFFFFFFF"))
            paths.append(p(f"M{mx-2},{my-6} v12 a2,2 0 0 0 4,0 v-12 a2,2 0 0 0 -4,0 Z", stroke="#FF1E6FE6", sw="1.2"))
    vec(name, 320, 320, paths, h=150, vph=150)
ill("ill_crop_auto", True)
ill("ill_crop_none", False)
print("step6 ok")

# ---- step 7: scan ----
vec("ic_crosshair", 44, 44, [p("M22,4 V40 M4,22 H40", stroke="#FFFFFFFF", sw="1.6")])
print("step7 ok")

# ---- step 9: tools tab + PDF editor ----
line("ic_et_edit_text", [p("M4,8 V4 H8 M16,4 H20 V8 M20,16 V20 H16 M8,20 H4 V16", stroke=LC, sw="1.7"), p("M8.5,8.5 H15.5 M12,8.5 V16", stroke=LC, sw="1.8")])
line("ic_et_add_text", [p("M3,19.5 L8.5,5 L14,19.5 M5.1,14.6 H11.9", stroke=LC, sw="1.8"), p("M18.5,3.5 V9.5 M15.5,6.5 H21.5", stroke=LC, sw="1.7")])
line("ic_et_add_image", [p("M13,4.5 H5 A1.5,1.5 0 0 0 3.5,6 V18 A1.5,1.5 0 0 0 5,19.5 H18 A1.5,1.5 0 0 0 19.5,18 V11.5", stroke=LC, sw="1.7"),
                         p("M3.8,16.5 L8.5,11.8 L12.5,15.8 L14.5,13.8 L19.2,18.4", stroke=LC, sw="1.6"), p("M18,2.5 V8.5 M15,5.5 H21", stroke=LC, sw="1.7")])
line("ic_et_annotate", [p("M6.5,15.5 L12,3.5 L17.5,15.5 M8.6,11 H15.4", stroke=LC, sw="1.8"), p("M4,20 H20", stroke=LC, sw="2")])
line("ic_et_sign", [p("M14.8,4.2 L19.8,9.2 L10.5,18.5 L5.2,19.2 L5.5,13.5 Z M12.8,6.2 L17.8,11.2", stroke=LC, sw="1.7"), p("M4,22 C8,20.5 11,23 15,21.5", stroke=LC, sw="1.4")])
line("ic_text_highlight", [p("M7,14.5 L12,3.5 L17,14.5 M8.8,10.5 H15.2", stroke=LC, sw="1.8"), p("M4,17.5 H20 V21 H4 Z", fill=LC, falpha="0.35")])
vec("ic_feedback_note", 28, 28, [p("M6,3 H19 L24,8 V24 A2,2 0 0 1 22,26 H6 A2,2 0 0 1 4,24 V5 A2,2 0 0 1 6,3 Z", fill="#FFDCE8FD"),
    p("M6,3 H19 L24,8 V24 A2,2 0 0 1 22,26 H6 A2,2 0 0 1 4,24 V5 A2,2 0 0 1 6,3 Z", stroke="#FF5B8DEF", sw="1.4"),
    p("M8.5,11 H18 M8.5,15 H16 M8.5,19 H13", stroke="#FF5B8DEF", sw="1.5"), p("M17,22 L25.5,13.5 L27.5,15.5 L19,24 L16.3,24.7 Z", fill="#FF1E6FE6")])
vec("ic_rt_manage_pages", 30, 30, [p(RT, fill="#FF8B5CF6"), p("M9,8 H18 L21,11 V22 H9 Z", stroke="#FFFFFFFF", sw="1.6"),
    p("M12,13 H18 M12,16.5 H16", stroke="#FFFFFFFF", sw="1.5"), p("M21,17.5 m-4,0 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0", fill="#FFFFFFFF"),
    p("M19,17.5 H23 M21,15.5 V19.5", stroke="#FF8B5CF6", sw="1.4")])
vec("ic_rt_recycle_bin", 30, 30, [p(RT, fill="#FF1FB35A"), p("M9,10 H21 M12.5,10 V8 H17.5 V10 M10.5,10 L11.5,22 H18.5 L19.5,10", stroke="#FFFFFFFF", sw="1.6"),
    p("M13.5,14 V19 M16.5,14 V19", stroke="#FFFFFFFF", sw="1.4")])
print("step9 ok")

# ---- step 10: settings, FAQ, premium, widgets ----
C = "M12,12 m-{r},0 a{r},{r} 0 1 0 {d},0 a{r},{r} 0 1 0 -{d},0"
def circ(cx, cy, r): return f"M{cx},{cy} m-{r},0 a{r},{r} 0 1 0 {2*r},0 a{r},{r} 0 1 0 -{2*r},0"
# settings rows (tinted text_primary in layout)
line("ic_st_file_manager", [p("M3.5,6.5 A1.5,1.5 0 0 1 5,5 H9.5 L11.5,7 H19 A1.5,1.5 0 0 1 20.5,8.5 V17.5 A1.5,1.5 0 0 1 19,19 H5 A1.5,1.5 0 0 1 3.5,17.5 Z", stroke=LC, sw="1.7"),
                            p("M3.8,10 H20.2", stroke=LC, sw="1.5")])
line("ic_st_faq", [p(circ(12, 12, 8.5), stroke=LC, sw="1.7"), p("M9.6,9.6 A2.5,2.5 0 1 1 12.9,12 C12.3,12.3 12,12.8 12,13.6 V14", stroke=LC, sw="1.7"), p(circ(12, 17, 1), fill=LC)])
line("ic_st_scan", [p("M4,8.5 V5.5 A1.5,1.5 0 0 1 5.5,4 H8.5 M15.5,4 H18.5 A1.5,1.5 0 0 1 20,5.5 V8.5 M20,15.5 V18.5 A1.5,1.5 0 0 1 18.5,20 H15.5 M8.5,20 H5.5 A1.5,1.5 0 0 1 4,18.5 V15.5", stroke=LC, sw="1.7"),
                    p("M4,12 H20", stroke=LC, sw="1.7")])
line("ic_st_theme", [p(circ(12, 12, 8.5), stroke=LC, sw="1.7"), p("M12,3.5 A8.5,8.5 0 0 1 12,20.5 Z", fill=LC)])
line("ic_st_feedback", [p("M4.5,5.5 A1.5,1.5 0 0 1 6,4 H18 A1.5,1.5 0 0 1 19.5,5.5 V15 A1.5,1.5 0 0 1 18,16.5 H10 L6,20 V16.5 H6 A1.5,1.5 0 0 1 4.5,15 Z", stroke=LC, sw="1.7"),
                        p("M8.5,9 H15.5 M8.5,12 H13", stroke=LC, sw="1.6")])
line("ic_st_widget", [p("M4,5 A1,1 0 0 1 5,4 H10 A1,1 0 0 1 11,5 V10 A1,1 0 0 1 10,11 H5 A1,1 0 0 1 4,10 Z M4,14 A1,1 0 0 1 5,13 H10 A1,1 0 0 1 11,14 V19 A1,1 0 0 1 10,20 H5 A1,1 0 0 1 4,19 Z M13,14 A1,1 0 0 1 14,13 H19 A1,1 0 0 1 20,14 V19 A1,1 0 0 1 19,20 H14 A1,1 0 0 1 13,19 Z", stroke=LC, sw="1.6"),
                      p("M16.5,3.8 V11.2 M12.8,7.5 H20.2", stroke=LC, sw="1.7")])
line("ic_st_more_apps", [p(circ(7, 7, 2.6), stroke=LC, sw="1.6"), p(circ(17, 7, 2.6), stroke=LC, sw="1.6"), p(circ(7, 17, 2.6), stroke=LC, sw="1.6"), p(circ(17, 17, 2.6), stroke=LC, sw="1.6")])
line("ic_st_terms", [p("M6,3.5 H14.5 L19,8 V19 A1.5,1.5 0 0 1 17.5,20.5 H6 A1.5,1.5 0 0 1 4.5,19 V5 A1.5,1.5 0 0 1 6,3.5 Z M14,3.8 V8.5 H18.7", stroke=LC, sw="1.6"),
                     p("M8,12 H15.5 M8,15.5 H13.5", stroke=LC, sw="1.6")])
line("ic_st_subscriptions", [p("M4.5,6 A1.5,1.5 0 0 1 6,4.5 H18 A1.5,1.5 0 0 1 19.5,6 V18 A1.5,1.5 0 0 1 18,19.5 H6 A1.5,1.5 0 0 1 4.5,18 Z", stroke=LC, sw="1.7"),
                             p("M8,9.5 L10.2,12.5 L12,8.5 L13.8,12.5 L16,9.5 L15.2,15.5 H8.8 Z", stroke=LC, sw="1.5")])
line("ic_expand", [p("M6.5,9.5 L12,15 L17.5,9.5", stroke=LC, sw="2")])
# FAQ question icons
line("ic_q_edit", [p("M14.5,4.5 L19.5,9.5 L9,20 H4 V15 Z M12.5,6.5 L17.5,11.5", stroke=LC, sw="1.7")])
line("ic_q_display", [p("M3.5,5.5 A1.5,1.5 0 0 1 5,4 H19 A1.5,1.5 0 0 1 20.5,5.5 V15 A1.5,1.5 0 0 1 19,16.5 H5 A1.5,1.5 0 0 1 3.5,15 Z M9,20 H15 M12,16.5 V20", stroke=LC, sw="1.7")])
line("ic_q_copy", [p("M9,8 A1.5,1.5 0 0 1 10.5,6.5 H18 A1.5,1.5 0 0 1 19.5,8 V19 A1.5,1.5 0 0 1 18,20.5 H10.5 A1.5,1.5 0 0 1 9,19 Z", stroke=LC, sw="1.7"),
                   p("M6,17.5 H5.5 A1,1 0 0 1 4.5,16.5 V5 A1.5,1.5 0 0 1 6,3.5 H14.5 A1,1 0 0 1 15.5,4.5 V5", stroke=LC, sw="1.7")])
line("ic_q_search", [p(circ(10.5, 10.5, 6), stroke=LC, sw="1.8"), p("M15,15 L20,20", stroke=LC, sw="2")])
line("ic_q_open", [p("M3.5,7 A1.5,1.5 0 0 1 5,5.5 H9.5 L11.5,7.5 H18 A1.5,1.5 0 0 1 19.5,9 V10.5", stroke=LC, sw="1.7"),
                   p("M3.5,7 V18 A1,1 0 0 0 4.5,19 H17.5 L20.8,11.8 A0.9,0.9 0 0 0 20,10.5 H7.3 A1,1 0 0 0 6.4,11.1 L3.6,18.2", stroke=LC, sw="1.7")])
line("ic_q_create", [p("M6,3.5 H14.5 L19,8 V19 A1.5,1.5 0 0 1 17.5,20.5 H6 A1.5,1.5 0 0 1 4.5,19 V5 A1.5,1.5 0 0 1 6,3.5 Z", stroke=LC, sw="1.6"), p("M11.75,10 V16.5 M8.5,13.25 H15", stroke=LC, sw="1.7")])
line("ic_q_ads", [p("M3.5,6.5 A1.5,1.5 0 0 1 5,5 H19 A1.5,1.5 0 0 1 20.5,6.5 V17.5 A1.5,1.5 0 0 1 19,19 H5 A1.5,1.5 0 0 1 3.5,17.5 Z", stroke=LC, sw="1.7"),
                  p("M7,15.5 L9.4,8.5 L11.8,15.5 M7.8,13.2 H11 M14,8.5 V15.5 H15 A3,3.5 0 0 0 15,8.5 Z", stroke=LC, sw="1.5")])
line("ic_q_slow", [p("M4,16 A8,8 0 1 1 20,16", stroke=LC, sw="1.8"), p("M12,16 L8.5,10.5", stroke=LC, sw="2"), p(circ(12, 16, 1.4), fill=LC)])
# banner (white on blue)
line("ic_no_ads", [p(circ(12, 12, 8.5), stroke="#FFFFFFFF", sw="1.8"), p("M7,15 L9,9 L11,15 M7.6,13.3 H10.4 M13,9 V15 H13.8 A2.6,3 0 0 0 13.8,9 Z", stroke="#FFFFFFFF", sw="1.4"), p("M6,6 L18,18", stroke="#FFFFFFFF", sw="1.8")], size=20)
vec("ic_banner_clouds", 96, 96, [p("M10,64 A12,12 0 0 1 22,52 A16,16 0 0 1 52,46 A13,13 0 0 1 76,54 A10,10 0 0 1 86,64 Z", fill="#FFFFFFFF", falpha="0.16"),
    p("M40,40 A8,8 0 0 1 48,32 A11,11 0 0 1 68,30 A9,9 0 0 1 82,38 A7,7 0 0 1 88,44 H40 Z", fill="#FFFFFFFF", falpha="0.12"),
    p("M60,8 L63,16 L71,19 L63,22 L60,30 L57,22 L49,19 L57,16 Z", fill="#FFFFE08A", falpha="0.9"),
    p("M24,22 L25.5,26 L29.5,27.5 L25.5,29 L24,33 L22.5,29 L18.5,27.5 L22.5,26 Z", fill="#FFFFFFFF", falpha="0.7")], h=64, vph=64)
# premium
vec("ic_check_badge", 22, 24, [p(circ(12, 12, 10), fill="#FFFFB300"), p("M7.5,12.3 L10.6,15.3 L16.5,9", stroke="#FF1B1F2A", sw="2.2")])
vec("ic_no_ads_art", 120, 120, [p(circ(60, 60, 50), fill="#FF22324F"), p("M32,38 H88 A6,6 0 0 1 94,44 V78 A6,6 0 0 1 88,84 H32 A6,6 0 0 1 26,78 V44 A6,6 0 0 1 32,38 Z", fill="#FFFFFFFF"),
    p("M38,72 L45,50 L52,72 M40.5,65 H49.5", stroke="#FF1E6FE6", sw="4"), p("M60,50 V72 H63 A9,11 0 0 0 63,50 Z", stroke="#FF1E6FE6", sw="4"),
    p(circ(86, 84, 16), fill="#FFE53935"), p("M79,77 L93,91 M93,77 L79,91", stroke="#FFFFFFFF", sw="4")])
# explore apps
vec("ic_gift_box", 72, 72, [p("M12,30 H60 V40 H12 Z", fill="#FFFF6B6B"), p("M15,40 H57 V62 A3,3 0 0 1 54,65 H18 A3,3 0 0 1 15,62 Z", fill="#FFFF8787"),
    p("M32,30 H40 V65 H32 Z", fill="#FFFFC53D"), p("M36,30 C28,16 16,20 22,28 C24,30 30,30 36,30 Z M36,30 C44,16 56,20 50,28 C48,30 42,30 36,30 Z", fill="#FFFFC53D"),
    p("M8,14 L10,19 L15,21 L10,23 L8,28 L6,23 L1,21 L6,19 Z", fill="#FF5B8DEF"), p("M64,8 L65.5,12 L69.5,13.5 L65.5,15 L64,19 L62.5,15 L58.5,13.5 L62.5,12 Z", fill="#FFFFC53D")])
# widgets (fixed light colours: widgets always sit on a white card)
WD = "#FF1B1F2A"
line("ic_w_search", [p(circ(10.5, 10.5, 6), stroke="#FF8E949E", sw="2"), p("M15,15 L20,20", stroke="#FF8E949E", sw="2.2")])
line("ic_w_edit", [p("M14.5,4.5 L19.5,9.5 L9,20 H4 V15 Z", stroke="#FFFFFFFF", sw="2")])
def wtile(name, color, glyph):
    vec(name, 28, 28, [p("M8,1 H20 A7,7 0 0 1 27,8 V20 A7,7 0 0 1 20,27 H8 A7,7 0 0 1 1,20 V8 A7,7 0 0 1 8,1 Z", fill=color)] + glyph)
wtile("ic_w_home", "#FF1E6FE6", [p("M8,13.5 L14,8.5 L20,13.5 V20 H16 V16 H12 V20 H8 Z", stroke="#FFFFFFFF", sw="1.7")])
wtile("ic_w_recent", "#FF1FB35A", [p(circ(14, 14, 6.5), stroke="#FFFFFFFF", sw="1.7"), p("M14,10.5 V14 L16.5,15.5", stroke="#FFFFFFFF", sw="1.7")])
wtile("ic_w_bookmark", "#FFFFA000", [p("M9.5,7.5 H18.5 V21 L14,17.5 L9.5,21 Z", stroke="#FFFFFFFF", sw="1.7")])
wtile("ic_w_edit_tile", "#FFE53935", [p("M16.5,7.5 L20.5,11.5 L12,20 H8 V16 Z", stroke="#FFFFFFFF", sw="1.7")])
vec("ic_w_edit_red", 36, 36, [p("M10,2 H26 A8,8 0 0 1 34,10 V26 A8,8 0 0 1 26,34 H10 A8,8 0 0 1 2,26 V10 A8,8 0 0 1 10,2 Z", fill="#FFE53935"),
    p("M11,8 H21 L26,13 V28 H11 Z", stroke="#FFFFFFFF", sw="1.8"), p("M22.5,17 L25,19.5 L18.5,26 H16 V23.5 Z", fill="#FFFFFFFF")])
line("ic_w_arrow", [p("M5,12 H19 M13,6 L19,12 L13,18", stroke="#FFFFFFFF", sw="2.2")])
vec("ic_w_editor_art", 100, 100, [p("M22,12 H58 L70,24 V80 A4,4 0 0 1 66,84 H22 A4,4 0 0 1 18,80 V16 A4,4 0 0 1 22,12 Z", fill="#FFFFFFFF"),
    p("M26,30 H56 M26,40 H62 M26,50 H50 M26,60 H58", stroke="#FFD3D8E0", sw="3"), p("M26,70 H44", stroke="#FFE53935", sw="3"),
    p("M84,20 L90,26 L64,52 L56,54 L58,46 Z", fill="#FFFFC53D"), p("M58,46 L64,52 L56,54 Z", fill="#FF1B1F2A")], h=90, vph=90)
print("step10 ok")

# ---- step 11 ----
vec("ic_loaded_ok", 18, 24, [p(circ(12, 12, 10), fill="#FF1FB35A"), p("M7.5,12.3 L10.6,15.3 L16.5,9", stroke="#FFFFFFFF", sw="2.2")])
print("step11 ok")
line("ic_caret_down", [p("M7,10 L12,15 L17,10 Z", fill=LC)], size=14)
line("ic_caret_up", [p("M7,14 L12,9 L17,14 Z", fill=LC)], size=14)
print("step11b ok")
vec("ic_sign_remove", 18, 24, [p(circ(12, 12, 9), fill="#FFB9BEC7"), p("M8.8,8.8 L15.2,15.2 M15.2,8.8 L8.8,15.2", stroke="#FFFFFFFF", sw="2")])
print("step11c ok")

# ---- step 12: paywall ----
def rr(x, y, w, h, r):
    return f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z"

def laurel(name, flip):
    paths = [p("M16,4 C8,10 6,22 12,34" if not flip else "M8,4 C16,10 18,22 12,34", stroke="#FFE2B04A", sw="1.4")]
    leaves = [(13.5, 9, -35), (11.2, 15, -20), (10.4, 21, -8), (11, 27, 8), (13, 32, 22)]
    for (x, y, a) in leaves:
        if flip: x = 24 - x
        dx = -4 if not flip else 4
        paths.append(p(f"M{x},{y} q{dx},-3 {dx*1.6},-1 q{-dx*0.6},3 {-dx*1.6},1 Z", fill="#FFE2B04A"))
    vec(name, 16, 24, paths, h=24, vph=36)
laurel("ic_laurel_left", False)
laurel("ic_laurel_right", True)
vec("ic_check_blue", 20, 24, [p(circ(12, 12, 10), fill="#FF5865FF"), p("M7.5,12.3 L10.6,15.3 L16.5,9", stroke="#FFFFFFFF", sw="2.2")])
vec("ic_alert_circle", 18, 24, [p(circ(12, 12, 9), stroke="#FFFFFFFF", sw="1.8"), p("M12,7.5 V13", stroke="#FFFFFFFF", sw="2"), p(circ(12, 16.3, 1.1), fill="#FFFFFFFF")])
OR = "#FFFFA41B"
vec("ic_trial_lock", 22, 24, [p(rr(5, 10.5, 14, 10, 2), fill=OR), p("M8,10.5 V8 A4,4 0 0 1 16,8", stroke=OR, sw="2"), p(circ(12, 15.3, 1.4), fill="#FF2A2410")])
vec("ic_trial_shield", 22, 24, [p("M12,3 L19,6 V11.5 C19,16 16,19.5 12,21 C8,19.5 5,16 5,11.5 V6 Z", fill=OR), p(circ(12, 11.5, 2.2), fill="#FF2A2410"), p("M12,13.5 V16", stroke="#FF2A2410", sw="1.6")])
vec("ic_trial_calendar", 22, 24, [p(rr(4, 5.5, 16, 15, 2.5), fill=OR), p("M4,10 H20", stroke="#FF2A2410", sw="1.4"), p("M8,3.5 V7 M16,3.5 V7", stroke=OR, sw="2"),
    p("M10,13 H14 L11.5,18", stroke="#FF2A2410", sw="1.6")])

# hero art: a phone with a feature badge, 180 x 150 viewport
PH = "#FF0D0E11"; SC = "#FFF5F7FB"
def phone(extra, tilt=0):
    base = [p("M40,150 C44,118 60,104 78,100 L96,96 C104,110 108,130 110,150 Z", fill="#FFC99A7B", falpha="0.55"),
            p(rr(74, 10, 72, 138, 12), fill=PH), p(rr(78, 16, 64, 128, 8), fill=SC),
            p("M84,24 H112", stroke="#FF1B1F2A", sw="2.2"), p("M84,31 H102", stroke="#FFB0B6C2", sw="1.6")]
    return base + extra
def glow(cx, cy, r, color):
    return [p(circ(cx, cy, r), fill=color, falpha="0.18"), p(circ(cx, cy, r * 0.6), fill=color, falpha="0.18")]
tiles = []
cols = ["#FFE8453C", "#FF2B7BF0", "#FF18A558", "#FFF5A623", "#FF8B5CF6", "#FF22C3E6", "#FFFF7043", "#FF36B37E", "#FF5C6BC0"]
k = 0
for r_ in range(4):
    for c_ in range(3):
        x = 84 + c_ * 18; y = 40 + r_ * 22
        tiles.append(p(rr(x, y, 12, 12, 3), fill=cols[k % len(cols)], falpha="0.9")); k += 1
        tiles.append(p(f"M{x},{y+16} H{x+12}", stroke="#FFC4C9D2", sw="1.2"))
vec("ill_prem_ads", 180, 180, glow(140, 50, 30, "#FF3D8BFF") + phone(tiles + [p(circ(140, 50, 17), fill="#FF3D8BFF"), p(circ(140, 50, 17), stroke="#FFFFFFFF", sw="1.5", alpha="0.6"),
    p("M131,56 L134.5,44 L138,56 M132.2,52.5 H136.8 M141,44 V56 H142.8 A4.2,6 0 0 0 142.8,44 Z", stroke="#FFFFFFFF", sw="1.6"), p("M129,40 L151,60", stroke="#FFFFFFFF", sw="2")]), h=150, vph=150)
doc_lines = [p(f"M86,{y} H{134 - (i % 3) * 8}", stroke="#FFC4C9D2", sw="1.6") for i, y in enumerate(range(44, 100, 8))]
vec("ill_prem_sign", 180, 180, glow(60, 60, 26, "#FF8B5CF6") + phone(doc_lines + [
    p("M88,120 C94,106 100,128 106,114 C110,106 114,126 124,112 C128,108 132,116 138,112", stroke="#FF1B1F2A", sw="1.8"),
    p("M150,70 L166,118 L160,121 L146,74 Z", fill="#FFF1F1F1"), p("M160,121 L163,128 L166,118 Z", fill="#FF2A2F3A"),
    p(rr(20, 40, 44, 50, 6), fill="#FFFFFFFF", falpha="0.95"), p("M28,54 H56 M28,62 H50 M28,70 H54", stroke="#FF9AA3B2", sw="2"), p("M28,80 L34,76 L38,82 L46,74", stroke="#FF6C5CE7", sw="2")]), h=150, vph=150)
vec("ill_prem_convert", 180, 180, glow(150, 110, 28, "#FFFF8A3D") + phone(doc_lines + [
    p(rr(58, 34, 34, 22, 5), fill="#FFE8453C"), p("M64,50 V40 H68 A3,3 0 0 1 68,46 H64 M74,50 V40 H76 A4,5 0 0 1 76,50 Z M84,50 V40 H89 M84,45 H88", stroke="#FFFFFFFF", sw="1.5"),
    p("M104,64 C130,62 146,80 140,104", stroke="#FFFFA41B", sw="7", cap="round"), p("M132,100 L141,112 L150,99 Z", fill="#FFFFA41B"),
    p(circ(146, 124, 12), fill="#FF2B7BF0"), p("M139,119 L142,130 L146,122 L150,130 L153,119", stroke="#FFFFFFFF", sw="1.8"),
    p(circ(164, 132, 10), fill="#FFFF7043"), p("M161,137 V127 H165 A3,3 0 0 1 165,133 H161", stroke="#FFFFFFFF", sw="1.6")]), h=150, vph=150)
vec("ill_prem_templates", 180, 180, glow(60, 60, 26, "#FF22C3E6") + phone([
    p(circ(96, 52, 10), fill="#FF4A6FA5"), p(circ(96, 48, 4), fill="#FFF2D0B8"), p("M89,59 C91,54 101,54 103,59", fill="#FF1B1F2A"),
    p("M112,46 H134 M112,53 H128", stroke="#FF1B1F2A", sw="1.8"),
    p("M86,72 H134 M86,80 H126 M86,88 H132 M86,96 H120 M86,104 H130", stroke="#FFC4C9D2", sw="1.6"),
    p(rr(118, 110, 36, 12, 3), fill="#FFE8EEFF"), p(rr(118, 110, 36, 12, 3), stroke="#FF2B7BF0", sw="1"), p("M123,116 H149", stroke="#FF2B7BF0", sw="1.4"),
    p(rr(84, 128, 60, 12, 4), fill="#FF1B1F2A", falpha="0.85"), p("M90,134 H96 M102,134 H108 M114,134 H120 M126,134 H132", stroke="#FFFFFFFF", sw="1.4")]), h=150, vph=150)
thumbs = []
for r_ in range(3):
    for c_ in range(2):
        x = 86 + c_ * 26; y = 40 + r_ * 32
        thumbs += [p(rr(x, y, 22, 28, 2), fill="#FFFFFFFF"), p(rr(x, y, 22, 28, 2), stroke="#FFC4C9D2", sw="1"),
                   p(f"M{x+4},{y+8} H{x+18} M{x+4},{y+13} H{x+15} M{x+4},{y+18} H{x+17}", stroke="#FFB0B6C2", sw="1.2")]
vec("ill_prem_pages", 180, 180, glow(150, 96, 26, "#FF8B5CF6") + phone(thumbs + [
    p(rr(134, 80, 30, 30, 8), fill="#FF8B5CF6"), p("M142,90 H156 M142,96 H152 M142,102 H154", stroke="#FFFFFFFF", sw="2"),
    p(circ(160, 108, 7), fill="#FFFFFFFF"), p("M157,108 H163 M160,105 V111", stroke="#FF8B5CF6", sw="1.6")]), h=150, vph=150)
print("step12 ok")
line("ic_st_drive", [p("M7,18.5 H17.5 A4,4 0 0 0 18.2,10.6 A6,6 0 0 0 6.6,9.4 A4.6,4.6 0 0 0 7,18.5 Z", stroke=LC, sw="1.7"),
                     p("M12,16 V11 M9.8,13 L12,10.8 L14.2,13", stroke=LC, sw="1.6")])
print("step14 ok")

# ---- step 16: template artwork (tpl_*) ----
SKIN = "#FFF2C9A8"; HAIR = "#FF3B2A20"
def avatar(name, shirt, bg):
    vec(name, 120, 120, [p(rr(0, 0, 120, 120, 0), fill=bg),
        p("M20,120 C22,92 40,82 60,82 C80,82 98,92 100,120 Z", fill=shirt),
        p("M50,70 H70 V86 C66,90 54,90 50,86 Z", fill=SKIN),
        p(circ(60, 52, 22), fill=SKIN),
        p("M37,50 C36,30 50,24 62,26 C76,27 86,36 83,52 C80,44 74,38 66,37 C58,42 46,44 37,50 Z", fill=HAIR),
        p("M52,55 h1 M68,55 h1", stroke="#FF3B2A20", sw="3"), p("M54,64 C58,67 62,67 66,64", stroke="#FFB5725A", sw="2")])
avatar("tpl_avatar_1", "#FF2B5FA8", "#FFE8EEF8")
avatar("tpl_avatar_2", "#FF3B3F4A", "#FFF1E9DF")
TI = "#FF6B7380"
vec("tpl_ic_pin", 16, 24, [p("M12,21 C8,16 5.5,12.5 5.5,9.5 A6.5,6.5 0 0 1 18.5,9.5 C18.5,12.5 16,16 12,21 Z", stroke=TI, sw="1.6"), p(circ(12, 9.5, 2.3), stroke=TI, sw="1.5")])
vec("tpl_ic_mail", 16, 24, [p(rr(3.5, 6, 17, 12, 1.8), stroke=TI, sw="1.6"), p("M4,7 L12,13 L20,7", stroke=TI, sw="1.6")])
vec("tpl_ic_phone", 16, 24, [p("M6.5,3.8 L9.5,3.8 L11,8.2 L9,9.6 C10,12 12,14 14.4,15 L15.8,13 L20.2,14.5 V17.5 C20.2,18.6 19.3,19.5 18.2,19.5 C10.4,19 5,13.6 4.5,5.8 C4.5,4.7 5.4,3.8 6.5,3.8 Z", stroke=TI, sw="1.5")])
vec("tpl_ic_web", 16, 24, [p(circ(12, 12, 8.5), stroke=TI, sw="1.5"), p("M3.5,12 H20.5 M12,3.5 C8.5,8 8.5,16 12,20.5 C15.5,16 15.5,8 12,3.5", stroke=TI, sw="1.3")])
# graduation cap
NAVY = "#FF24407A"
vec("tpl_art_cap", 200, 200, [p("M100,40 L185,78 L100,116 L15,78 Z", fill=NAVY), p("M52,95 V140 C70,158 130,158 148,140 V95 L100,116 Z", fill="#FF2E4F92"),
    p("M100,78 L160,92 V132", stroke="#FFF2B233", sw="3"), p(circ(160, 138, 6), fill="#FFF2B233"),
    p("M40,170 C60,150 80,176 100,160 C120,146 140,176 160,160", stroke="#FF9FB6D8", sw="3"),
    p("M30,60 L36,52 M170,40 L176,32 M22,120 L14,116", stroke="#FF9FB6D8", sw="3")], h=200, vph=200)
# christmas tree
vec("tpl_art_tree", 200, 240, [p("M100,20 L150,90 H125 L165,150 H135 L180,210 H20 L65,150 H35 L75,90 H50 Z", fill="#FF1F7A45"),
    p("M88,210 H112 V236 H88 Z", fill="#FF7A4A2A"), p("M100,6 L105,18 L118,19 L108,27 L111,40 L100,33 L89,40 L92,27 L82,19 L95,18 Z", fill="#FFF7C23C"),
    p(circ(90, 80, 5), fill="#FFE8453C"), p(circ(115, 118, 5), fill="#FFF7C23C"), p(circ(80, 140, 5), fill="#FF3C8BE8"),
    p(circ(125, 170, 5), fill="#FFE8453C"), p(circ(70, 190, 5), fill="#FFF7C23C"), p(circ(140, 196, 5), fill="#FF3C8BE8")], h=240, vph=240)
# shopping bags
vec("tpl_art_bags", 220, 200, [p(rr(20, 70, 90, 110, 6), fill="#FFFFFFFF"), p("M45,70 V52 A20,20 0 0 1 85,52 V70", stroke="#FF1B1B1B", sw="5"),
    p(rr(95, 50, 100, 130, 6), fill="#FF1B1B1B"), p("M122,50 V32 A23,23 0 0 1 168,32 V50", stroke="#FFFFFFFF", sw="5"),
    p(rr(60, 110, 70, 80, 5), fill="#FFE8453C"), p("M78,110 V98 A17,17 0 0 1 112,98 V110", stroke="#FFFFFFFF", sw="4")], h=200, vph=200)
# megaphone / marketing
vec("tpl_art_megaphone", 200, 160, [p("M30,70 L120,30 V130 L30,100 Z", fill="#FFF26B1D"), p(rr(12, 66, 22, 38, 4), fill="#FF24407A"),
    p("M48,100 L58,140 H78 L70,106", fill="#FF24407A"), p("M140,56 L170,40 M144,80 H178 M140,104 L170,120", stroke="#FFF2B233", sw="6")], h=160, vph=160)
# sun burst
sun = [p(circ(100, 100, 42), fill="#FFFF7A1A")]
import math as _m
for k in range(12):
    a = _m.radians(k * 30)
    x1, y1 = 100 + 52 * _m.cos(a), 100 + 52 * _m.sin(a)
    x2, y2 = 100 + 90 * _m.cos(a), 100 + 90 * _m.sin(a)
    sun.append(p(f"M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}", stroke="#FFFF7A1A", sw="12"))
vec("tpl_art_sun", 200, 200, sun, h=200, vph=200)
# flowers (letter decoration)
fl = []
for (cx, cy, col) in [(40, 60, "#FFF4A3B5"), (90, 40, "#FFF7C873"), (140, 70, "#FFB7A6E8"), (70, 100, "#FFF4A3B5"), (170, 30, "#FFF7C873")]:
    for k in range(5):
        a = _m.radians(k * 72)
        fl.append(p(circ(round(cx + 9 * _m.cos(a), 1), round(cy + 9 * _m.sin(a), 1), 7), fill=col))
    fl.append(p(circ(cx, cy, 4.5), fill="#FFF2B233"))
fl += [p("M20,120 C60,95 120,125 190,90", stroke="#FF8DBF8A", sw="3"), p("M60,108 C66,98 76,96 84,100 C76,106 68,110 60,108 Z", fill="#FF8DBF8A"),
       p("M130,108 C136,98 146,96 154,100 C146,106 138,110 130,108 Z", fill="#FF8DBF8A")]
vec("tpl_art_flowers", 200, 130, fl, h=130, vph=130)
# logo mark
vec("tpl_logo", 40, 40, [p(circ(20, 20, 18), fill="#FF1E6FE6"), p("M12,26 L18,12 L24,24 L28,16", stroke="#FFFFFFFF", sw="3")])
print("step16 ok")

# ---- step 16: template editor icons ----
S = "1.8"
line("ic_tpl_fonts", [p("M2.5,18 L7.5,5.5 L12.5,18 M4.3,13.6 H10.7", stroke=LC, sw=S),
    p("M20.5,12 V18 M20.5,14.8 A3,3 0 1 0 20.5,15.2", stroke=LC, sw="1.6")])
line("ic_tpl_size", [p("M2.5,19 L8,5 L13.5,19 M4.5,14.2 H11.5", stroke=LC, sw=S), p("M13.5,19 L17,10 L20.5,19 M14.8,16 H19.2", stroke=LC, sw="1.6")])
line("ic_tpl_color", [p("M6,15 L12,3.5 L18,15 M8.2,11 H15.8", stroke=LC, sw=S), p(rr(4, 18, 16, 3, 1), fill=LC)])
line("ic_tpl_format", [p("M3,18 L8,5.5 L13,18 M4.8,13.6 H11.2", stroke=LC, sw=S), p("M15,7 H21 M16.5,11.5 H21 M15,16 H21", stroke=LC, sw="1.6")])
line("ic_tpl_replace", [p(rr(3.5, 7, 12, 12, 1.6), stroke=LC, sw="1.7"), p("M5,17 L8.5,13 L11,15.5 L12.5,14 L14.5,17", stroke=LC, sw="1.5"),
    p("M11,4 H19.5 V12 M17.2,9.8 L19.5,12 L21.8,9.8", stroke=LC, sw="1.7")])
line("ic_tpl_flip_h", [p("M12,3 V21", stroke=LC, sw="1.6"), p("M9.5,6.5 L3,17.5 H9.5 Z", stroke=LC, sw="1.7"), p("M14.5,6.5 L21,17.5 H14.5 Z", fill=LC, stroke=LC, sw="1.7")])
line("ic_tpl_flip_v", [p("M3,12 H21", stroke=LC, sw="1.6"), p("M6.5,9.5 L17.5,3 V9.5 Z", stroke=LC, sw="1.7"), p("M6.5,14.5 L17.5,21 V14.5 Z", fill=LC, stroke=LC, sw="1.7")])
line("ic_tpl_rotate", [p("M18.5,9 A7,7 0 0 0 6,8 M5.5,15 A7,7 0 0 0 18,16", stroke=LC, sw="1.8"),
    p("M6,4.5 V8 H9.5 M18,19.5 V16 H14.5", stroke=LC, sw="1.8")])
line("ic_tpl_bold", [p("M7,4.5 H12.8 A3.7,3.7 0 0 1 12.8,11.9 H7 Z M7,11.9 H13.8 A3.8,3.8 0 0 1 13.8,19.5 H7 Z", stroke=LC, sw="2.4")])
line("ic_tpl_italic", [p("M10,4.5 H18 M6,19.5 H14 M14,4.5 L10,19.5", stroke=LC, sw="2")])
line("ic_tpl_underline", [p("M7,4 V11 A5,5 0 0 0 17,11 V4 M5,20 H19", stroke=LC, sw="2")])
line("ic_tpl_strike", [p("M16.5,6.5 C15.6,5 14,4.2 12,4.2 C9.5,4.2 7.6,5.5 7.6,7.6 C7.6,9 8.6,9.9 10.3,10.5 M15.8,14 C16.2,14.6 16.4,15.3 16.4,16 C16.4,18.4 14.4,19.8 11.9,19.8 C9.6,19.8 7.8,18.8 7,17.2 M4,12 H20", stroke=LC, sw="2")])
line("ic_tpl_bullets", [p(circ(5, 6.5, 1.5), fill=LC), p(circ(5, 12, 1.5), fill=LC), p(circ(5, 17.5, 1.5), fill=LC), p("M9.5,6.5 H20.5 M9.5,12 H20.5 M9.5,17.5 H20.5", stroke=LC, sw="1.8")])
line("ic_tpl_numbers", [p("M3.6,5 L5,4.2 V9 M3.2,13 C3.4,12.2 4,11.8 4.7,11.8 C5.5,11.8 6.1,12.3 6.1,13 C6.1,14 3.3,15.2 3.3,16.2 H6.2 M3.4,18.4 H6 L4.5,19.8 C5.6,19.8 6.2,20.3 6.2,21 C6.2,21.8 5.5,22.3 4.7,22.3 C4,22.3 3.5,22 3.3,21.5", stroke=LC, sw="1.2"),
    p("M9.5,6.5 H20.5 M9.5,13.5 H20.5 M9.5,20 H20.5", stroke=LC, sw="1.8")])
line("ic_tpl_chat", [p("M4,5.5 A1.5,1.5 0 0 1 5.5,4 H18.5 A1.5,1.5 0 0 1 20,5.5 V15 A1.5,1.5 0 0 1 18.5,16.5 H10 L6,20 V16.5 H5.5 A1.5,1.5 0 0 1 4,15 Z", stroke="#FF1E6FE6", sw="1.7"),
    p("M8.5,10.2 h0.01 M12,10.2 h0.01 M15.5,10.2 h0.01", stroke="#FF1E6FE6", sw="2.4")])
line("ic_tpl_add_image", [p(rr(3.5, 4.5, 17, 15, 2), stroke=LC, sw="1.7"), p(circ(9, 9.5, 1.6), stroke=LC, sw="1.5"), p("M4,17 L9.5,12.5 L13,15.5 L15.5,13.5 L20,17", stroke=LC, sw="1.6")])
vec("tpl_handle_rotate", 26, 26, [p(circ(13, 13, 12), fill="#FFFFFFFF"), p(circ(13, 13, 12), stroke="#FFD5DAE2", sw="1"),
    p("M18.2,11 A5.5,5.5 0 0 0 8.4,10.2 M7.8,15 A5.5,5.5 0 0 0 17.6,15.8", stroke="#FF1B1F2A", sw="1.6"), p("M8.2,7.2 V10.4 H11.4 M17.8,18.8 V15.6 H14.6", stroke="#FF1B1F2A", sw="1.6")])
print("step16b ok")

# ---- step 20: bookmark message pill icons (white, not tinted) ----
BM = "M7,3.5 H17 A1.5,1.5 0 0 1 18.5,5 V20.5 L12,16.5 L5.5,20.5 V5 A1.5,1.5 0 0 1 7,3.5 Z"
vec("ic_bm_pill_on", 20, 24, [p(BM, fill="#FFFFFFFF")])
vec("ic_bm_pill_off", 20, 24, [p(BM, stroke="#FFFFFFFF", sw="1.8"), p("M3.5,3.5 L20.5,20.5", stroke="#FFFFFFFF", sw="1.8")])
print("step20 ok")

# ---- step 26: heart for the "Is it helpful?" sheet ----
vec("ic_helpful_heart", 44, 24, [p("M12,21 C11.6,21 11.2,20.85 10.9,20.6 C5.5,15.9 2.5,13.1 2.5,9.2 C2.5,6.2 4.8,4 7.6,4 C9.3,4 10.9,4.8 12,6.1 C13.1,4.8 14.7,4 16.4,4 C19.2,4 21.5,6.2 21.5,9.2 C21.5,13.1 18.5,15.9 13.1,20.6 C12.8,20.85 12.4,21 12,21 Z", fill="#FFE53935"),
    p("M7,7.5 C6,8 5.4,9 5.5,10", stroke="#FFFFFFFF", sw="1.4", alpha="0.6")])
print("step26 ok")
