"""Package pinned original Xiaomi UI unchanged; scene APKs stay separate.

Usage: python tools/import_xiaomi_ui.py ThemeManager.apk scene-apk-directory
No decompiled XML or vendor-specific absolute path is used in a release build.
"""
from pathlib import Path
import argparse, hashlib, json, zipfile, subprocess, re, os

ROOT = Path(__file__).resolve().parents[1]
PIN = 'd27f5cd0babd10566f6fccb0903b581487a8c5aadbb909c99efb8d415c0a1d7e'

def clock_metadata(apk):
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home()/'AppData/Local/Android/Sdk')))
    dump = subprocess.check_output([str(sdk/'build-tools/35.0.0'/('aapt2.exe' if os.name=='nt' else 'aapt2')), 'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True,encoding='utf8')
    result = {}
    for key in ('clock_position_x','clock_position_y','dual_clock_position_x_anchor_right','dual_clock_position_y','support_change_with_time'):
        values = re.findall(r'android:name\([^\n]*\)="'+key+r'"[^\n]*\n\s*A: [^\n]*android:value\([^\n]*\)=([0-9.]+)',dump)
        if not values:
            if key.startswith('support_'): result[key]=0; continue
            raise ValueError('Missing original clock metadata: '+key)
        if len(set(values)) != 1: raise ValueError('Preview/service clock metadata differs: '+key)
        result[key] = int(values[0]) if key.startswith('support_') else float(values[0])
    return result

def main():
    p = argparse.ArgumentParser()
    p.add_argument('theme', type=Path)
    p.add_argument('scenes', type=Path)
    a = p.parse_args()
    if hashlib.sha256(a.theme.read_bytes()).hexdigest() != PIN:
        raise ValueError('Unexpected original Xiaomi UI version')
    out = ROOT / 'app/assets/xiaomi'
    out.mkdir(parents=True, exist_ok=True)
    # Preserve every DEX, asset and compiled resource. Only APK signing metadata
    # and native video/editor libraries not used by the hosted UI are excluded.
    entries = []
    with zipfile.ZipFile(a.theme) as src, zipfile.ZipFile(out/'ui.apk', 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as dst:
        for e in sorted(src.infolist(), key=lambda e: e.filename):
            n = e.filename
            if e.is_dir() or n.startswith(('META-INF/', 'lib/')): continue
            data = src.read(e)
            info = zipfile.ZipInfo(n, (2026, 9, 28, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            dst.writestr(info, data)
            entries.append({'path': n, 'sha256': hashlib.sha256(data).hexdigest()})
    digest = hashlib.sha256((out/'ui.apk').read_bytes()).hexdigest()
    (out/'ui.sha256').write_text(digest+'\n', encoding='ascii')
    catalog = []
    titles = [('moon','月球'),('snowmountain','雪山'),('geometry','几何'),('saturn','土星环'),('earth','地球家园'),('mars','红色火星')]
    for family,title in titles:
        apk = a.scenes / (family+'.apk')
        catalog.append({'id':family,'title':title,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'lands':5 if family in ('earth','moon','mars') else 1,'clock':clock_metadata(apk)})
        # Catalog banners are decoded from each original APK at runtime when
        # installed/imported; bundled originals allow browsing without a pack.
        with zipfile.ZipFile(apk) as z:
            names = [n for n in z.namelist() if n.startswith('res/') and n.rsplit('/',1)[-1] in ('banner.png','banner.jpg','banner.webp',family+'_banner.png')]
            if not names:
                sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home()/'AppData/Local/Android/Sdk')))
                dump = subprocess.check_output([str(sdk/'build-tools/35.0.0'/('aapt2.exe' if os.name=='nt' else 'aapt2')), 'dump','resources', str(apk)], text=True,encoding='utf8')
                match = re.search(r'resource \S+ drawable/(?:'+family+r'_)?banner\s+\([^)]*\) \(file\) (\S+)', dump)
                if match: names = [match.group(1)]
            if len(names) != 1: raise ValueError((family, names))
            (out/(family+'.banner')).write_bytes(z.read(names[0]))
    (out/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n', encoding='utf8')
    provenance = ROOT/'vendor/xiaomi'
    provenance.mkdir(parents=True,exist_ok=True)
    (provenance/'ui-manifest.json').write_text(json.dumps({'original_sha256':PIN,'bundle_sha256':digest,'entries':entries},indent=2)+'\n',encoding='utf8')
    print('Original UI bundle:', (out/'ui.apk').stat().st_size, 'bytes')

if __name__ == '__main__': main()
