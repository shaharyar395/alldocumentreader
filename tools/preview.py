import sys, re, xml.etree.ElementTree as ET, os
A='{http://schemas.android.com/apk/res/android}'
def col(c):
    if not c: return 'none'
    if c.startswith('@'): return '#222'
    c=c.lstrip('#')
    if len(c)==8: c=c[2:]
    return '#'+c
def conv(path, out, scale=4):
    r=ET.parse(path).getroot()
    vw=float(r.get(A+'viewportWidth')); vh=float(r.get(A+'viewportHeight'))
    def walk(e):
        s=''
        for ch in e:
            if ch.tag=='path':
                s+=f'<path d="{ch.get(A+"pathData")}" fill="{col(ch.get(A+"fillColor"))}" fill-opacity="{ch.get(A+"fillAlpha","1")}" stroke="{col(ch.get(A+"strokeColor"))}" stroke-width="{ch.get(A+"strokeWidth","0")}" stroke-opacity="{ch.get(A+"strokeAlpha","1")}" stroke-linecap="round" stroke-linejoin="round"/>'
            elif ch.tag=='group':
                tx=ch.get(A+'translateX','0'); ty=ch.get(A+'translateY','0'); sx=ch.get(A+'scaleX','1'); sy=ch.get(A+'scaleY','1')
                s+=f'<g transform="translate({tx},{ty}) scale({sx},{sy})">'+walk(ch)+'</g>'
        return s
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}" width="{vw*scale}" height="{vh*scale}"><rect width="100%" height="100%" fill="#dddddd"/>{walk(r)}</svg>'
    return svg

import glob
html='<html><body style="margin:0;background:#fff;display:flex;flex-wrap:wrap;gap:8px;width:1300px">'
names=sys.argv[2:] or [os.path.basename(f)[:-4] for f in sorted(glob.glob("app/src/main/res/drawable/ic_*.xml"))]
for n in names:
    html+='<div style="font:10px sans-serif;text-align:center">'+conv(f"app/src/main/res/drawable/{n}.xml",None,1).replace('width="','data-w="').replace('height="','data-h="',1).replace('<svg ','<svg width="110" height="110" ')+'<br>'+n+'</div>'
open(sys.argv[1],'w').write(html+'</body></html>')
