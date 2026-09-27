"""Exercise external-provider acquisition failures without a vendor ROM."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt'
                           else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
EXE = '.exe' if os.name == 'nt' else ''
OUT = ROOT / 'build/provider-test'
OUT.mkdir(parents=True, exist_ok=True)
sources = [ROOT / 'app/java/org/aliveclean/PlatformProvider.java',
           *sorted((ROOT / 'tests/provider').rglob('*.java'))]
subprocess.run([str(JAVA / ('javac' + EXE)), '-encoding', 'UTF-8', '-d', str(OUT),
                *map(str, sources)], check=True)
subprocess.run([str(JAVA / ('java' + EXE)), '-cp', str(OUT),
                'org.aliveclean.PlatformProviderTest'], check=True)
