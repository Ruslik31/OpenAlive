"""Compile/run the isolated live Vivo clock backdrop probe; no production app changes."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile
import time

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
BT=SDK/'build-tools/35.0.0'
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin'
ANDROID=SDK/'platforms/android-35/android.jar'
OUT=ROOT/'build/vivo-clock-material'
PACKAGE='org.aliveclean.vivoclockprobe'

def run(*cmd,**kw):return subprocess.run(list(map(str,cmd)),check=True,**kw)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--build-only',action='store_true')
    parser.add_argument('--production',action='store_true')
    args=parser.parse_args()
    for name in ['classes','dex','assets']:
        target=(OUT/name).resolve()
        assert target.is_relative_to((ROOT/'build').resolve())
        if target.exists():shutil.rmtree(target)
        target.mkdir(parents=True)
    source=ROOT/'research/vivo-20260927/clock-port'
    shutil.copyfile(source/'shader_vibrancy_effect.agsl',OUT/'assets/shader_vibrancy_effect.agsl')
    shutil.copyfile(source/'liquid_glass.agsl',OUT/'assets/liquid_glass.agsl')
    shutil.copyfile(ROOT/'research/vivo-20260927/dependencies/runtime/system/fonts/vivoSansClockHAVF-OS7.ttf',OUT/'assets/vivoSansClockHAVF-OS7.ttf')
    shutil.copytree(ROOT/'app/assets/native-clock/vivo',OUT/'assets/native-clock/vivo',dirs_exist_ok=True)
    (OUT/'AndroidManifest.xml').write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+PACKAGE+'">'
        '<uses-sdk android:minSdkVersion="33" android:targetSdkVersion="35"/>'
        '<application android:label="Vivo clock material test" android:debuggable="true" android:theme="@android:style/Theme.Material.NoActionBar">'
        '<activity android:name="org.aliveclean.VivoClockMaterialProbe$ProbeActivity" android:exported="true" android:launchMode="singleTop"/></application>'
        '<instrumentation android:name="org.aliveclean.VivoClockMaterialProbe" android:targetPackage="'+PACKAGE+'"/></manifest>',encoding='utf8')
    run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',ANDROID,
        '-classpath',BT/'core-lambda-stubs.jar','-d',OUT/'classes',ROOT/'tests/vivo/VivoClockMaterialProbe.java',ROOT/'app/java/org/aliveclean/VivoGlyphDistanceField.java',ROOT/'app/java/org/aliveclean/NativeVivoClockMaterial.java')
    run(JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','33','--lib',ANDROID,'--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
    run(BT/'aapt2.exe','link','-o',OUT/'base.apk','--manifest',OUT/'AndroidManifest.xml','-I',ANDROID,'-A',OUT/'assets')
    with zipfile.ZipFile(OUT/'base.apk') as src,zipfile.ZipFile(OUT/'unsigned.apk','w') as dst:
        for e in src.infolist():dst.writestr(e.filename.replace('\\','/'),src.read(e),compress_type=zipfile.ZIP_STORED)
        dst.write(OUT/'dex/classes.dex','classes.dex')
    run(BT/'zipalign.exe','-f','4',OUT/'unsigned.apk',OUT/'aligned.apk')
    run(JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',ROOT/'local/development.jks','--ks-pass','pass:android','--ks-key-alias','development','--out',OUT/'probe.apk',OUT/'aligned.apk')
    if args.build_only:return
    adb=[SDK/'platform-tools/adb.exe','-s',args.serial]
    run(*adb,'install','-r',OUT/'probe.apk')
    try:
        process=subprocess.Popen(list(map(str,[*adb,'shell','am','instrument','--no-hidden-api-checks',*(['-e','production','true'] if args.production else []),'-w',PACKAGE+'/org.aliveclean.VivoClockMaterialProbe'])),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf8')
        time.sleep(1.5)
        run(*adb,'shell','am','start','-W','-n',"'"+PACKAGE+"/org.aliveclean.VivoClockMaterialProbe$ProbeActivity'",*(['--ez','production','true'] if args.production else []),capture_output=True,timeout=15)
        try: output,_=process.communicate(timeout=35)
        except subprocess.TimeoutExpired:
            process.kill();output,_=process.communicate();raise
        (OUT/'result.txt').write_text(output,encoding='utf8');print(output)
        for name in ['blue.png','red.png','blur.png','blur-live.png','wallpaper.png','live-source.png','minute.png','scaled-glass.png','scaled-blur.png','relayout-glass.png','relayout-blur.png']:
            p=subprocess.run(list(map(str,[*adb,'exec-out','run-as',PACKAGE,'cat','files/'+name])),capture_output=True)
            if p.returncode==0:(OUT/name).write_bytes(p.stdout)
        assert any(marker in output for marker in ['VIVO_PORTABLE_SHADER_CAPTURED','VIVO_NATIVE_GLASS_CAPTURED','VIVO_PRODUCTION_GLASS_CAPTURED']),'See result.txt'
    finally:run(*adb,'uninstall',PACKAGE)

if __name__=='__main__':main()
