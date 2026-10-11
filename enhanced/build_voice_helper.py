import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
from pathlib import Path
import subprocess,zipfile,sys
ROOT=Path(__file__).resolve().parent.parent
def run(*args):subprocess.run([str(a) for a in args],check=True)
base=ROOT/'enhanced/voice-helper';release='--release' in sys.argv
out=base/('final' if release else 'out');out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
sources=list(base.glob('*.java'))+[ROOT/'enhanced/src/local/gboard/enhanced/VoiceBridge.java']
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,*sources)
run(JAVA/'jar.exe','cf',out/'helper.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'helper.jar')
manifest=out/'AndroidManifest.xml';manifest_source=(base/'AndroidManifest.xml').read_text('utf8')
if release:manifest_source=manifest_source.replace('android:debuggable="true"','android:debuggable="false"').replace('<package android:name="local.gboard.voicecheck" />','')
manifest.write_text(manifest_source,encoding='utf8')
run(BT/'aapt.exe','package','-f','-M',manifest,'-S',base/'res','-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
  z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
  for abi in ['arm64-v8a','x86_64']:
    for name in ['libonnxruntime.so','libsherpa-onnx-c-api.so','libgboard_local_voice.so']:z.write(ROOT/'enhanced/native/lib'/abi/name,'lib/'+abi+'/'+name,compress_type=zipfile.ZIP_STORED)
  for f in (ROOT/'enhanced/voice-data').rglob('*'):
    if f.is_file():z.write(f,'assets/local-voice/'+f.relative_to(ROOT/'enhanced/voice-data').as_posix(),compress_type=zipfile.ZIP_DEFLATED)
  for name in ['SHERPA-LICENSE','VOICE-MODEL-LICENSE.md']:z.write(ROOT/'enhanced'/name,'assets/licenses/'+name,compress_type=zipfile.ZIP_DEFLATED)
  if not release:z.write(ROOT/'enhanced/speech-fixture.wav','assets/speech-fixture.wav',compress_type=zipfile.ZIP_STORED)
  if not release:z.write(ROOT/'enhanced/speech-mixed.wav','assets/speech-mixed.wav',compress_type=zipfile.ZIP_STORED)
run(BT/'zipalign.exe','-P','16','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'voice-helper.apk',out/'aligned.apk')
run(BT/'apksigner.bat','verify',out/'voice-helper.apk');print(out/'voice-helper.apk')
