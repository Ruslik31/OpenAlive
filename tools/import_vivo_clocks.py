"""Bundle original Vivo clock layouts, constraint solver, fonts and shader assets."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'research/vivo-20260927'
OUT=ROOT/'app/assets/native-clock/vivo'
BUILD=ROOT/'build/vivo-clock-runtime'
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin/java.exe'
def main():
    OUT.mkdir(parents=True,exist_ok=True);BUILD.mkdir(parents=True,exist_ok=True)
    resources=(SRC/'clock-port/resources.txt').read_text(encoding='utf8')
    layouts=dict(re.findall(r'resource 0x\w+ layout/(view_time_s\d+)\s+\(\) \(file\) (\S+)',resources))
    with zipfile.ZipFile(SRC/'apk/SystemUIPlugin.apk') as original,zipfile.ZipFile(OUT/'layouts.apk','w',zipfile.ZIP_DEFLATED,compresslevel=9) as bundle:
        for name in ['resources.arsc',*layouts.values()]:
            info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            bundle.writestr(info,original.read(name))
        # R8 does not shrink a DEX-only APK. Preserve the original code archive;
        # the isolated loader instantiates only its constraint layout classes.
        for name in sorted(n for n in original.namelist() if re.fullmatch(r'classes\d*\.dex',n)):
            info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            bundle.writestr(info,original.read(name))
    for file in (SRC/'clock-port/fonts').glob('*'):shutil.copyfile(file,OUT/file.name)
    for name in ['clock_styles.json','clock_fonts.json','liquid_glass.agsl','shader_vibrancy_effect.agsl']:
        shutil.copyfile(SRC/'clock-port'/name,OUT/name)
    digest=hashlib.sha256((OUT/'layouts.apk').read_bytes()).hexdigest()
    (OUT/'layouts.sha256').write_text(digest+'\n',encoding='ascii')
    (OUT/'source.json').write_text(json.dumps(dict(source_apk_sha256=hashlib.sha256((SRC/'apk/SystemUIPlugin.apk').read_bytes()).hexdigest(),
        adaptation='Original binary layouts/resources and full original DEX archive, isolated to load constraint layout classes only. Vendor services are not started. Clock lifecycle and material hooks are adapted to the ColorOS host.',
        layouts=layouts,runtime_sha256=digest),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('Vivo clock runtime:',(OUT/'layouts.apk').stat().st_size,'bytes;',len(layouts),'original layouts')
if __name__=='__main__':main()
