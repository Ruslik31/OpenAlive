"""Inventory original Vivo clocks, fonts, layouts and shaders without changing the app."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'research/vivo-20260927'
OUT = SOURCE / 'clock-port'
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
AAPT = SDK / 'build-tools/35.0.0/aapt2.exe'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    apk = SOURCE / 'apk/SystemUIPlugin.apk'
    resources = subprocess.run([str(AAPT), 'dump', 'resources', str(apk)],
                               check=True, capture_output=True).stdout.decode('utf8')
    (OUT / 'resources.txt').write_text(resources, encoding='utf8')
    names = dict(re.findall(r'resource (0x\w+) (\S+)', resources))
    records = []

    def save(name, data, origin):
        target = OUT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        records.append(dict(file=name, source=origin, bytes=len(data),
                            sha256=hashlib.sha256(data).hexdigest()))

    with zipfile.ZipFile(apk) as z:
        styles = json.loads(z.read('assets/clock_styles.json'))
        fonts = json.loads(z.read('assets/clock_fonts.json'))
        for name in ['clock_styles.json', 'clock_fonts.json']:
            save(name, z.read('assets/' + name), 'SystemUIPlugin.apk/assets/' + name)
        for name, entry in re.findall(
                r'resource 0x\w+ (?:layout|raw)/(view_time_s\d+|shader_vibrancy_effect)\s+\(\) \(file\) (\S+)', resources):
            if name.startswith('shader'):
                save(name + '.agsl', z.read(entry), 'SystemUIPlugin.apk/' + entry)
            else:
                xml = subprocess.run([str(AAPT), 'dump', 'xmltree', str(apk), '--file', entry],
                                     check=True, capture_output=True).stdout.decode('utf8')
                save('layouts/' + name + '.bin', z.read(entry), 'SystemUIPlugin.apk/' + entry)
                resolved = re.sub(r'@(0x[0-9a-f]+)', lambda m: '@' + names.get(m[1], m[1]), xml)
                (OUT / 'layouts' / (name + '.txt')).write_text(resolved, encoding='utf8')
    framework_resources = SOURCE / 'dependencies/framework/vivo-res.apk'
    with zipfile.ZipFile(framework_resources) as z:
        entry = 'res/raw/shader_liquid_glass_effect.agsl'
        save('liquid_glass.agsl', z.read(entry), 'vivo-res.apk/' + entry)
    from fontTools.ttLib import TTFont
    paths = {f['font_path'] for f in fonts} | {s['default_font']['font_path'] for s in styles}
    axes = {}
    for path in sorted(paths):
        source = SOURCE / 'dependencies/runtime' / path.lstrip('/')
        if not source.is_file():
            raise FileNotFoundError('Required original clock font: ' + path)
        save('fonts/' + source.name, source.read_bytes(), path)
        with TTFont(source) as font:
            axes[source.name] = [dict(tag=a.axisTag, min=a.minValue, default=a.defaultValue, max=a.maxValue)
                                 for a in font['fvar'].axes] if 'fvar' in font else []
    # Preserve every density/size qualifier. Choosing an arbitrary first value
    # here would corrupt the personalized clocks' ytde and grid proportions.
    dimension_blocks = [b for b in re.split(r'(?=    resource )', resources)
                        if re.match(r'    resource 0x\w+ dimen/vivo_keyguard_(?:s\d+|time_s\d+)', b)]
    (OUT / 'clock-dimensions.txt').write_text(''.join(dimension_blocks), encoding='utf8')
    manifest = dict(source_apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                    framework_resources_sha256=hashlib.sha256(framework_resources.read_bytes()).hexdigest(),
                    style_count=len(styles), groups=sorted({s['style_group'] for s in styles}),
                    font_axes=axes, files=records)
    (OUT / 'inventory.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(f"Extracted {len(styles)} configurations, {len(manifest['groups'])} groups, {len(paths)} fonts; all hashes recorded.")


if __name__ == '__main__':
    main()
