"""Run all original Vivo clock layouts in an isolated ColorOS test host."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
ADB=[str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'),'-s',sys.argv[1]]
if '--reuse' not in sys.argv:
    subprocess.run([sys.executable,str(ROOT/'tools/check_native_clock_fonts.py'),sys.argv[1],'--prepare-only'],check=True)
out=ROOT/'build/vivo-clocks';out.mkdir(exist_ok=True)
run=subprocess.run([*ADB,'shell','am','instrument','--no-hidden-api-checks','-w','org.aliveclean.nativeclocktest/org.aliveclean.NativeVivoClocksInstrumentation'],capture_output=True,timeout=120)
text=run.stdout.decode('utf8',errors='replace');(out/'result.txt').write_text(text,encoding='utf8');print(text)
for name in ['vivo-clock-sheet.png']:
    p=subprocess.run([*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/'+name],capture_output=True)
    if p.returncode==0:(out/name).write_bytes(p.stdout)
if 'VIVO_LAYOUTS_PASS' not in text:raise SystemExit(1)
if '--export-previews' in sys.argv:
    import json,io
    from PIL import Image
    assets=ROOT/'app/assets/native-clock/vivo'
    previews=assets/'previews';previews.mkdir(exist_ok=True)
    for style in json.loads((assets/'clock_styles.json').read_text(encoding='utf8')):
        key=style['style_id']
        p=subprocess.run([*ADB,'exec-out','run-as','org.aliveclean.nativeclocktest','cat','files/vivo-'+key+'.png'],capture_output=True,check=True)
        image=Image.open(io.BytesIO(p.stdout)).convert('RGBA')
        image.thumbnail((500,810),Image.Resampling.LANCZOS)
        thumb=Image.new('RGBA',(540,900))
        thumb.alpha_composite(image,((540-image.width)//2,45))
        thumb.save(previews/(key+'.png'))
