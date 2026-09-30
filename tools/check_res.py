"""Static resource checks: missing refs, duplicates, unknown locale keys. Run from project root."""
import re,glob,os,collections,sys
res='app/src/main/res'
bad=0
for f in glob.glob(res+'/values*/*.xml'):
    names=re.findall(r'<(?:string|plurals|style|color) name="([^"]+)"',open(f,encoding='utf-8').read())
    d=[n for n,c in collections.Counter(names).items() if c>1]
    if d: print('DUP',f,d); bad+=1
have={k:set() for k in ['drawable','string','color','style','layout','menu','mipmap','xml','plurals','id','anim']}
for t in ['drawable','color','layout','menu','xml','anim']:
    for f in glob.glob(f'{res}/{t}/*.xml'): have[t].add(os.path.basename(f)[:-4])
for f in glob.glob(res+'/mipmap-*/*'): have['mipmap'].add(os.path.basename(f).split('.')[0])
for f in glob.glob(res+'/values/*.xml'):
    for t,n in re.findall(r'<(string|color|style|plurals)\s+name="([^"]+)"',open(f,encoding='utf-8').read()): have[t].add(n)
for f in glob.glob(res+'/values-*/strings.xml'):
    for n in re.findall(r'<(?:string|plurals) name="([^"]+)"',open(f,encoding='utf-8').read()):
        if n not in have['string'] and n not in have['plurals']: print('EXTRA',f,n); bad+=1
for f in glob.glob(res+'/**/*.xml',recursive=True):
    for n in re.findall(r'@\+id/(\w+)',open(f,encoding='utf-8').read()): have['id'].add(n)
missing=set()
for f in glob.glob(res+'/**/*.xml',recursive=True)+['app/src/main/AndroidManifest.xml']:
    s=open(f,encoding='utf-8').read()
    for t,n in re.findall(r'@(drawable|string|color|style|layout|menu|mipmap|xml|anim)/([\w.]+)',s):
        if n not in have[t]: missing.add((t,n,os.path.basename(f)))
styles={x.replace('.','_') for x in have['style']}
for f in glob.glob('app/src/main/java/**/*.kt',recursive=True):
    s=open(f,encoding='utf-8').read()
    for t,n in re.findall(r'(?<![\w.])R\.(drawable|string|color|style|layout|menu|id|plurals|mipmap|anim)\.(\w+)',s):
        ok = n in styles if t=='style' else n in have[t]
        if not ok: missing.add((t,n,os.path.basename(f)))
for m in sorted(missing): print('MISSING',*m)
print('done', 'issues:', bad+len(missing))
