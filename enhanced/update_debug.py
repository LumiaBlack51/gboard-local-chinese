import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
"""Incremental local builds reusing the already packaged model assets."""
from pathlib import Path
import subprocess,sys,zipfile
from repack import replace
ROOT=Path(__file__).resolve().parent.parent
engine=len(sys.argv)>1 and sys.argv[1]=='enginecheck'
directory=ROOT/'enhanced'/('enginecheck/out' if engine else 'build/update');directory.mkdir(parents=True,exist_ok=True)
classes=directory/'classes';classes.mkdir(exist_ok=True);dex=directory/'dex';dex.mkdir(exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
if engine:
  sources=[ROOT/'enhanced/enginecheck/EngineCheckActivity.java']+[ROOT/'enhanced/src/local/gboard/enhanced'/n for n in ['RimeBridge.java','WordIndex.java','LocalWords.java','VoiceBridge.java']]
else:sources=list((ROOT/'enhanced/src').rglob('*.java'))
if '--test' in sys.argv:sources+=list((ROOT/'enhanced/testsrc').rglob('*.java'))
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,*sources)
run(JAVA/'jar.exe','cf',directory/'helper.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,directory/'helper.jar',*([] if engine else [ROOT/'enhanced/build/patched.dex']))
apk=ROOT/'enhanced'/('enginecheck/out/enginecheck.apk' if engine else 'release/base.apk')
changes={'classes.dex' if engine else 'classes5.dex':(dex/'classes.dex').read_bytes(),'lib/arm64-v8a/libgboard_local_rime.so':(ROOT/'enhanced/native/lib/arm64-v8a/libgboard_local_rime.so').read_bytes()}
if not engine:
  sys.path.insert(0,str(ROOT));from add_activity import add_voice_access
  with zipfile.ZipFile(apk) as z:changes['AndroidManifest.xml']=add_voice_access(z.read('AndroidManifest.xml'))
for name in ['default.yaml','local_pinyin.schema.yaml','local_pinyin_private.schema.yaml']:
  changes['assets/local-rime/'+name]=(ROOT/'enhanced/rime-data'/name).read_bytes()
replace(apk,directory/'unsigned-update.apk',changes)
run(BT/'zipalign.exe','-P','16','-f','4',directory/'unsigned-update.apk',directory/'aligned-update.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',apk,directory/'aligned-update.apk')
run(BT/'apksigner.bat','verify',apk);print(apk)
