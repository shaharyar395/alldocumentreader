# -*- coding: utf-8 -*-
"""Step-59: translations of the 193 texts (+7 plurals) that were still English in every language.
Reads tools/i18n_step59/<locale>.json; writes a <!-- step59 --> block into each values-<locale>/strings.xml.
Texts that are CDATA in values/strings.xml are written as CDATA, the others XML-escaped. Run from project root."""
import json, re, glob, os
base = open("app/src/main/res/values/strings.xml", encoding="utf-8").read()
cdata = set(re.findall(r'<string name="([^"]+)"[^>]*><!\[CDATA\[', base))
def enc(key, text):
    if key in cdata:
        return "<![CDATA[" + text + "]]>"
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
n = 0
for f in sorted(glob.glob("tools/i18n_step59/*.json")):
    loc = os.path.basename(f)[:-5]
    d = json.load(open(f, encoding="utf-8"))
    p = f"app/src/main/res/values-{loc}/strings.xml"
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"\n    <!-- step59 -->.*?<!-- /step59 -->", "", s, flags=re.S)
    have = set(re.findall(r'<string name="([^"]+)"', s)) | set(re.findall(r'<plurals name="([^"]+)"', s))
    body = ""
    for k, v in d["strings"].items():
        if k not in have:
            body += f'    <string name="{k}">{enc(k, v)}</string>\n'
    for k, q in d["plurals"].items():
        if k in have:
            continue
        body += f'    <plurals name="{k}">\n'
        for quantity in ["zero", "one", "two", "few", "many", "other"]:
            if quantity in q:
                body += f'        <item quantity="{quantity}">{enc(k, q[quantity])}</item>\n'
        body += "    </plurals>\n"
    s = s.replace("\n</resources>", "\n    <!-- step59 -->\n" + body + "    <!-- /step59 -->\n</resources>")
    open(p, "w", encoding="utf-8").write(s)
    n += 1
print("step59 translations:", n)
