#!/usr/bin/env python3
"""Deterministic original procedural LUT pack generator for ApexStudio."""
from __future__ import annotations
import hashlib, json, math, zipfile, sys
from pathlib import Path
OUT = (Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent / 'generated')
OUT.mkdir(parents=True, exist_ok=True)
SIZE = 17
ATTRIBUTION = 'Original procedural LUTs generated for ApexStudio; no third-party LUT files were used.'
FILTERS = [
 ('amber-drift','Amber Drift','warm'), ('apricot-haze','Apricot Haze','warm'), ('copper-glow','Copper Glow','warm'), ('sunlit-clay','Sunlit Clay','warm'), ('rosewood-air','Rosewood Air','warm'), ('cinder-bloom','Cinder Bloom','warm'),
 ('moss-window','Moss Window','nature'), ('fern-shadow','Fern Shadow','nature'), ('olive-mist','Olive Mist','nature'), ('pine-afterglow','Pine Afterglow','nature'), ('meadow-fade','Meadow Fade','nature'), ('sage-current','Sage Current','nature'),
 ('coastal-glass','Coastal Glass','cool'), ('blue-hour','Blue Hour','cool'), ('ink-tide','Ink Tide','cool'), ('violet-slate','Violet Slate','cool'), ('frosted-lagoon','Frosted Lagoon','cool'), ('plum-current','Plum Current','cool'),
 ('chalk-soft','Chalk Soft','mono'), ('silver-paper','Silver Paper','mono'), ('graphite-air','Graphite Air','mono'), ('pearl-shadow','Pearl Shadow','mono'), ('charcoal-lift','Charcoal Lift','mono'), ('linen-gray','Linen Gray','mono'),
 ('pastel-pulse','Pastel Pulse','creative'), ('citrus-echo','Citrus Echo','creative'), ('berry-film','Berry Film','creative'), ('teal-coral','Teal Coral','creative'), ('violet-sun','Violet Sun','creative'), ('quiet-neon','Quiet Neon','creative')]
def clamp(x): return max(0.0, min(1.0, x))
def smoothstep(x): return x*x*(3.0-2.0*x)
def luma(r,g,b): return 0.2126*r + 0.7152*g + 0.0722*b
def transform(r,g,b, idx):
    cat = idx // 6; local = idx % 6; phase = (idx + 1) * 0.731
    exposure = [-0.035, 0.018, 0.045, -0.012, 0.028, 0.006][local] + (cat-2)*0.006
    contrast = [0.92, 1.06, 1.13, 0.98, 1.18, 0.88][local] + (cat-2)*0.025
    saturation = [1.08, 0.92, 1.18, 1.02, 0.78, 1.28][local] + (cat-2)*0.035
    q = [clamp(c + exposure) for c in (r,g,b)]
    q = [smoothstep(c) * 0.08 + c * 0.92 for c in q]
    q = [clamp((c-0.5)*contrast + 0.5) for c in q]
    y = luma(*q); q = [clamp(y + (c-y)*saturation) for c in q]
    a = 0.012 * math.sin(phase); d = 0.010 * math.cos(phase*1.7); e = 0.008 * math.sin(phase*0.63)
    r2 = q[0]*(1.0-a) + q[1]*a + e*q[2]
    g2 = q[1]*(1.0-d) + q[2]*d + a*q[0]
    b2 = q[2]*(1.0-e) + q[0]*e + d*q[1]
    biases = [(0.055, 0.018, -0.012), (0.010, 0.045, 0.020), (-0.018, 0.016, 0.052), (0.018, 0.022, 0.018), (0.032, -0.008, 0.040)][cat]
    wobble = 0.006 * math.sin((r*1.7 + g*2.3 + b*3.1 + phase) * math.pi)
    out = [r2+biases[0]+wobble, g2+biases[1]-wobble*0.35, b2+biases[2]+wobble*0.2]
    toe = 0.012 * (local-2.5)
    out = [clamp(c + toe*(1.0-c)*0.55 - toe*c*0.20) for c in out]
    if cat == 4: out = [clamp(c + 0.018*math.sin((k+1)*phase + c*4.0)) for k,c in enumerate(out)]
    return tuple(out)
def sha256(path):
    h=hashlib.sha256(); h.update(path.read_bytes()); return h.hexdigest()
def write_lut(path, ident, name, category, idx):
    lines = [f'TITLE "{name}"', '# Original procedural transform; generated deterministically by generate_luts.py', f'# id={ident} category={category}', 'LUT_3D_SIZE 17', 'DOMAIN_MIN 0.0 0.0 0.0', 'DOMAIN_MAX 1.0 1.0 1.0']
    for b_i in range(SIZE):
      b=b_i/(SIZE-1)
      for g_i in range(SIZE):
        g=g_i/(SIZE-1)
        for r_i in range(SIZE):
          r=r_i/(SIZE-1); lines.append(' '.join(f'{v:.9f}' for v in transform(r,g,b,idx)))
    path.write_text('\n'.join(lines)+'\n', encoding='ascii')
entries=[]
for idx,(ident,name,category) in enumerate(FILTERS):
    fn=f'{ident}.cube'; p=OUT/fn; write_lut(p,ident,name,category,idx)
    entries.append({'id':ident,'name':name,'category':category,'file':fn,'sizeBytes':p.stat().st_size,'sha256':sha256(p)})
manifest={'schemaVersion':1,'attribution':ATTRIBUTION,'filters':entries}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n', encoding='utf-8')
archive=OUT/'apexstudio-lut-filters-vol-01.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    members=[(OUT/'manifest.json','manifest.json')]+[(OUT/e['file'],e['file']) for e in entries]
    for source, arcname in members:
        info=zipfile.ZipInfo(arcname,date_time=(2020,1,1,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        info.create_system=3
        info.external_attr=0o100644 << 16
        z.writestr(info,source.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
issues=[]; sample=[(0,0,0),(0.25,0.5,0.75),(0.5,0.5,0.5),(1,0.2,0.8),(1,1,1)]; sigs=[]
for idx,e in enumerate(entries):
    p=OUT/e['file']; lines=p.read_text(encoding='ascii').splitlines(); size=int(next(x for x in lines if x.startswith('LUT_3D_SIZE')).split()[1])
    vals=[]
    for line in lines:
      if line and not line.startswith(('#','TITLE','DOMAIN_','LUT_')): vals.extend(float(x) for x in line.split())
    parsed={'size':size,'dataTriples':len(vals)//3,'finite':all(math.isfinite(x) for x in vals),'inRange':all(0<=x<=1 for x in vals)}
    identity=[]
    for q in range(SIZE**3):
      r=(q%SIZE)/(SIZE-1); g=((q//SIZE)%SIZE)/(SIZE-1); b=(q//(SIZE*SIZE))/(SIZE-1); identity.extend((r,g,b))
    parsed['nonIdentity']=any(abs(x-y)>1e-8 for x,y in zip(vals,identity))
    if parsed != {'size':17,'dataTriples':4913,'finite':True,'inRange':True,'nonIdentity':True}: issues.append(f'validation mismatch: {e["file"]}: {parsed}')
    sigs.append(tuple(round(v,8) for rgb in sample for v in transform(*rgb,idx)))
if len(set(sigs)) != 30: issues.append('sample-grid transform signatures are not all distinct')
with zipfile.ZipFile(archive) as z:
    members=z.namelist(); expected=['manifest.json']+[e['file'] for e in entries]
    safe=all(n and not n.startswith('/') and '..' not in Path(n).parts and '\\' not in n for n in members)
    if members != expected: issues.append(f'archive members mismatch: {members}')
    if not safe: issues.append('unsafe archive member path')
archive_hash=sha256(archive)
report={'generatedAtIndependentOfWallClock':True,'outputDirectory':'.','count':len(entries),'lut3DSize':17,'perFileValidation':{'everyFile':{'dataTriples':4913,'finite':True,'inRange':True,'nonIdentity':True},'distinctSampleGridSignatures':len(set(sigs))},'files':entries,'archive':{'path':archive.name,'sizeBytes':archive.stat().st_size,'sha256':archive_hash,'memberCount':len(members),'members':members,'safeMemberPaths':safe},'deterministicGenerationInstructions':['Run: python3 generate_luts.py [OUTPUT_DIRECTORY] (defaults to ./generated)','The generator uses only Python standard-library math, json, hashlib, pathlib, and zipfile.','It enumerates RGB samples in blue-major, green-middle, red-minor order for LUT_3D_SIZE 17 (17^3 = 4913 triples).','Each transform applies explicit exposure, smoothstep toe shaping, contrast, luminance-based saturation, index-seeded channel cross-talk, category palette biases, and bounded creative modulation; all output channels are clipped to [0,1].','No network access, third-party files, brand/vendor LUTs, timestamps, or random sources are used.'],'validation':{'allPassed':not issues,'issues':issues,'archiveMembersSafe':safe,'archiveMembersExact':members==expected}}
(OUT/'report.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8')
(OUT/'VALIDATION.txt').write_text(('PASS\n' if not issues else 'FAIL\n')+json.dumps(report['validation'],indent=2)+'\n', encoding='utf-8')
if issues: raise SystemExit('\n'.join(issues))
print(json.dumps({'output':str(OUT),'count':len(entries),'archive':str(archive),'archiveSizeBytes':archive.stat().st_size,'archiveSha256':archive_hash,'distinctSampleGridSignatures':len(set(sigs)),'allPassed':True},indent=2))
