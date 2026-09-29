"""Validate the preserved Xiaomi UI and lossless built-in runtime packs."""
from pathlib import Path
import hashlib, json, lzma, zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/assets/xiaomi'
manifest = json.loads((ROOT / 'vendor/xiaomi/ui-manifest.json').read_text(encoding='utf8'))
sha = lambda data: hashlib.sha256(data).hexdigest()
digest = sha((ASSETS / 'ui.apk').read_bytes())
assert digest == manifest['bundle_sha256'] == (ASSETS / 'ui.sha256').read_text().strip()
with zipfile.ZipFile(ASSETS / 'ui.apk') as apk:
    expected = {e['path']: e['sha256'] for e in manifest['entries']}
    assert len(expected) == len(apk.namelist())
    assert set(apk.namelist()) == set(expected)
    for name, checksum in expected.items():
        assert sha(apk.read(name)) == checksum, name
    assert 'classes.dex' in expected and 'resources.arsc' in expected
    assert not any(n.startswith('lib/') for n in expected)
catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf8'))
assert [p['id'] for p in catalog] == ['moon', 'snowmountain', 'geometry', 'saturn', 'earth', 'mars']
for pack in catalog:
    assert len(pack['sha256']) == 64 and int(pack['sha256'], 16) > 0
    assert pack['bytes'] > 0 and pack['lands'] == (5 if pack['id'] in ('moon','earth','mars') else 1)
    assert (ASSETS / (pack['id'] + '.banner')).stat().st_size > 0
bundle=json.loads((ASSETS/'packs.json').read_text(encoding='utf-8'))
assert bundle['format']==1 and bundle['max_chunk_bytes']==4*1024*1024
assert set(bundle['packs'])=={p['id'] for p in catalog}
referenced=set();original_bytes=0
for pack in catalog:
    recipe=bundle['packs'][pack['id']]
    assert recipe['sha256']==pack['sha256'] and recipe['bytes']==pack['bytes']
    digest=hashlib.sha256();size=0
    for key,length in recipe['chunks']:
        assert len(key)==64 and int(key,16)>=0 and 0<length<=bundle['max_chunk_bytes']
        data=lzma.decompress((ASSETS/'packs'/(key+'.xz')).read_bytes(),memlimit=32*1024*1024)
        assert len(data)==length and sha(data)==key
        digest.update(data);size+=length;referenced.add(key)
    assert size==pack['bytes'] and digest.hexdigest()==pack['sha256'],pack['id']
    original_bytes+=size
assert {p.name for p in (ASSETS/'packs').iterdir()}=={key+'.xz' for key in referenced}
compressed_bytes=sum((ASSETS/'packs'/(key+'.xz')).stat().st_size for key in referenced)
assert bundle['storage']['original_apk_bytes']==original_bytes
assert bundle['storage']['compressed_unique_bytes']==compressed_bytes
assert compressed_bytes<original_bytes
print('Xiaomi original UI entries verified:', len(expected), '; built-in scene packs:', len(catalog),
      '; original bytes:',original_bytes,'; stored bytes:',compressed_bytes)
