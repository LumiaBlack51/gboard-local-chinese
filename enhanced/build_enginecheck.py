import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
from pathlib import Path
import subprocess,zipfile
ROOT=Path(__file__).resolve().parent.parent
def run(*args):subprocess.run([str(a) for a in args],check=True)
base=ROOT/'enhanced'/'enginecheck';out=base/'out';out.mkdir(exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,base/'EngineCheckActivity.java',*(ROOT/'enhanced/src').rglob('*.java'))
run(JAVA/'jar.exe','cf',out/'helper.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'helper.jar')
run(BT/'aapt.exe','package','-f','-M',base/'AndroidManifest.xml','-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
  z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
  for name in ['libgboard_local_rime.so','librime.so','libFcitx5Core.so','libFcitx5Config.so','libFcitx5Utils.so','libc++_shared.so','libvosk.so','libgboard_local_voice.so']:
    z.write(ROOT/'enhanced'/'native'/'lib'/'arm64-v8a'/name,'lib/arm64-v8a/'+name,compress_type=zipfile.ZIP_STORED)
  for f in (ROOT/'enhanced'/'rime-data').rglob('*'):
    if f.is_file() and f.name!='word-index.bin':z.write(f,'assets/local-rime/'+f.relative_to(ROOT/'enhanced'/'rime-data').as_posix(),compress_type=zipfile.ZIP_STORED if f.suffix=='.gram' else zipfile.ZIP_DEFLATED)
  for f in (ROOT/'enhanced'/'voice-data').rglob('*'):
    if f.is_file():z.write(f,'assets/local-voice/'+f.relative_to(ROOT/'enhanced'/'voice-data').as_posix(),compress_type=zipfile.ZIP_DEFLATED)
  z.write(ROOT/'enhanced'/'speech-fixture.wav','assets/speech-fixture.wav',compress_type=zipfile.ZIP_STORED)
run(BT/'zipalign.exe','-P','16','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'enginecheck.apk',out/'aligned.apk')
print(out/'enginecheck.apk')
