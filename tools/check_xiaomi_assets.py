"""Validate the preserved Xiaomi UI and the separate runtime pack catalog."""
from pathlib import Path
import hashlib, json, zipfile

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
assert not any((ASSETS / (p['id'] + '.apk')).exists() for p in catalog)
print('Xiaomi original UI entries verified:', len(expected), '; separate scene packs:', len(catalog))
