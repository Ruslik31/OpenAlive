"""Bundle unchanged Vivo renderer code and its downloaded lookup textures.

Usage: python tools/import_vivo_runtime.py PATH_TO_EXTRACTION
The extraction must contain apk/LiveWallpaperBox.apk and engine-resources/apk_res.
No device files, account data, system framework, or download service are bundled.
"""
from pathlib import Path
import hashlib
import json
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    extraction = Path(sys.argv[1]).resolve()
    source = extraction / 'apk/LiveWallpaperBox.apk'
    downloaded = extraction / 'engine-resources/apk_res'
    flash = downloaded / '200115356/livewallpaper/video_livewallpaper/content/wallpaper_res'
    raster = downloaded / '200098137/livewallpaper/video_livewallpaper/content/anim_res'
    if not source.is_file() or not flash.is_dir() or not raster.is_dir():
        raise SystemExit('The original APK and both downloaded texture directories are required')
    output = ROOT / 'app/assets/vivo/runtime'
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, output / 'engine.apk')
    for name in ['libpag.so', 'libffavc.so']:
        shutil.copyfile(extraction / 'native/box' / name, output / name)
        (output / (name + '.sha256')).write_text(digest(output / name) + '\n', encoding='ascii')
    records = []
    with zipfile.ZipFile(output / 'textures.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for folder, prefix in [(flash, 'flash_card/res'), (raster, 'raster')]:
            for path in sorted(folder.rglob('*')):
                if not path.is_file():
                    continue
                # The original renderer uses the tiled mini textures. Full-size originals
                # stay in the verified extraction, rather than duplicating ~30 MB in the app.
                if folder == raster and '_' not in path.stem:
                    continue
                relative = path.relative_to(folder).as_posix()
                name = 'assets/' + prefix + '/' + relative
                entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                bundle.writestr(entry, path.read_bytes())
                records.append({'asset': prefix + '/' + relative,
                                'source': path.relative_to(extraction).as_posix(),
                                'sha256': digest(path), 'bytes': path.stat().st_size})
        additional = []
        icons = downloaded / '200098137/livewallpaper/video_livewallpaper/content/anim_icon'
        additional.extend((p, 'vivo/ui/raster/' + p.name) for p in sorted(icons.glob('*.pag')))
        for number, category in [('200115352', 'liquid'), ('200115356', 'flash')]:
            folder = downloaded / number / 'livewallpaper/video_livewallpaper/content'
            for sub in ['effect_type', 'effect_area', 'pattern_style', 'doodle_res']:
                additional.extend((p, 'vivo/ui/' + category + '/' + p.relative_to(folder).as_posix())
                                  for p in sorted((folder / sub).rglob('*')) if p.is_file())
            additional.append((folder / 'panelConfig.json', 'vivo/ui/' + category + '/panelConfig.json'))
        liquid = extraction / 'effects/105/liquidspace1/content/live_res/9001/1'
        flash_photo = extraction / 'effects/105/flashcard1/content/live_res/10001/1'
        mountains = list((extraction / 'effects/105/图集光栅-山脉').glob('content/res/origin_lock_2'))
        if len(mountains) != 1:
            raise ValueError('Expected one verified default raster image set')
        for folder, category in [(liquid, 'liquid'), (flash_photo, 'flash')]:
            additional.extend([(folder / 'origin_lock', 'vivo/default/' + category + '.jpg'),
                               (folder / 'wallpaper_mask_lock.png', 'vivo/default/' + category + '-mask.png')])
        additional.append((flash_photo / 'wallpaper_doodle_mask.png', 'vivo/default/flash-doodle.png'))
        for i, name in enumerate(['origin_lock', 'origin_lock_1', 'origin_lock_2']):
            additional.append((mountains[0].parent / name, f'vivo/default/raster-{i}.jpg'))
        for path, name in additional:
            entry = zipfile.ZipInfo('assets/' + name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            bundle.writestr(entry, path.read_bytes())
            records.append({'asset': name, 'source': path.relative_to(extraction).as_posix(),
                            'sha256': digest(path), 'bytes': path.stat().st_size})
    for name in ['engine.apk', 'textures.zip']:
        (output / (name + '.sha256')).write_text(digest(output / name) + '\n', encoding='ascii')
    manifest = {
        'source': 'Vivo LiveWallpaperBox 7.0.1.02, downloaded liquid/flash/raster resources',
        'apk': {'file': 'engine.apk', 'sha256': digest(source), 'modified': False},
        'native': [{'file': name, 'source': 'native/box/' + name, 'sha256': digest(output / name)}
                   for name in ['libpag.so', 'libffavc.so']],
        'textures': {'file': 'textures.zip', 'sha256': digest(output / 'textures.zip'),
                     'files': records},
        'adaptation': 'Original APK remains byte-identical. Downloaded texture bytes remain unchanged; '
                      'asset paths replace the vendor-specific download cache paths.',
    }
    (output / 'source.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(f'Bundled unchanged renderer APK and {len(records)} offline textures')


if __name__ == '__main__':
    main()
