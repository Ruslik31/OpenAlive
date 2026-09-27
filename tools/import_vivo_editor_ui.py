"""Import self-contained vectors from the extracted Vivo theme editor, without theme services."""
from pathlib import Path
import hashlib
import json
import shutil
import sys

root = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]).resolve() / 'theme-src/resources/res/drawable'
names = ['ic_theme_editer_title_bg', 'ic_unlock_decorate', 'ic_decorate_refresh',
         'ic_decorate_dialog_close', 'ic_aod_add', 'ic_aod_edit']
records = []
for name in names:
    original = source / (name + '.xml')
    target = root / 'app/res/drawable' / ('vivo_' + name + '.xml')
    shutil.copyfile(original, target)
    records.append({'source': 'BBKTheme/res/drawable/' + original.name,
                    'target': str(target.relative_to(root)).replace('\\', '/'),
                    'sha256': hashlib.sha256(original.read_bytes()).hexdigest()})
(root / 'app/assets/vivo/ui-source.json').write_text(
    json.dumps(records, indent=2) + '\n', encoding='utf8')
