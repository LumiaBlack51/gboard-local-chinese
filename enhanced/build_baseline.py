import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
import pathlib, subprocess, zipfile
import sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent.parent))
import clone_apk
ROOT=clone_apk.ROOT
CP=str(ROOT/'tools'/'dexlib2-2.5.2.jar')+';'+str(ROOT/'tools'/'guava-27.1-android.jar')+';'+str(APKTOOL)+';'+str(ROOT/'tools')
def run(*args): subprocess.run([str(a) for a in args],check=True)
run(JAVA/'javac.exe','-cp',CP,ROOT/'tools'/'DexTool.java')
clone_apk.main()
for apk in (ROOT/'enhanced'/'unsigned').glob('*.apk'):
    tmp=ROOT/'enhanced'/'dex-work'/apk.stem
    raw=tmp/'raw'; fixed=tmp/'fixed'
    raw.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(apk) as z:
        names=[n for n in z.namelist() if n.endswith('.dex')]
        for n in names: (raw/n).write_bytes(z.read(n))
    if names:
        run(JAVA/'java.exe','-Xmx2G','-cp',CP,'DexTool',raw,fixed)
        rebuilt=tmp/'rebuilt.apk'
        with zipfile.ZipFile(apk) as z, zipfile.ZipFile(rebuilt,'w') as out:
            for info in z.infolist():
                data=(fixed/info.filename).read_bytes() if info.filename in names else z.read(info)
                out.writestr(info,data)
    else: rebuilt=apk
    dest=ROOT/'enhanced'/'baseline'; dest.mkdir(exist_ok=True)
    aligned=dest/('aligned-'+apk.name)
    run(BT/'zipalign.exe','-P','16','-f','4',rebuilt,aligned)
    run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',dest/apk.name,aligned)
