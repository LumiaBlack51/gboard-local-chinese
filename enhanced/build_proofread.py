"""Build an isolated, optional text corrector. QNN v79 is for Snapdragon 8 Elite."""
import sys, subprocess, zipfile, hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,SIGN_ARGS
ROOT=Path(__file__).resolve().parent.parent
release='--release' in sys.argv
base=ROOT/'enhanced/proofread';out=base/('final' if release else 'out');out.mkdir(exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
deps=ROOT/'artifacts/proofreading';ort=deps/'ort-qnn';qnn=deps/'qnn-runtime'
def run(*args):subprocess.run([str(a) for a in args],check=True)
sources=list(base.glob('*.java'))+[ROOT/'enhanced/src/local/gboard/enhanced/VoiceText.java']
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',str(ANDROID)+';'+str(ort/'classes.jar'),'-d',classes,*sources)
run(JAVA/'jar.exe','cf',out/'proof.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'proof.jar',ort/'classes.jar')
run(BT/'aapt.exe','package','-f','-M',base/'AndroidManifest.xml','-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
  z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
  for name in ['libonnxruntime.so','libonnxruntime4j_jni.so']:
    z.write(ort/'jni/arm64-v8a'/name,'lib/arm64-v8a/'+name,compress_type=zipfile.ZIP_DEFLATED)
    if not release:z.write(deps/'ort-cpu/jni/x86_64'/name,'lib/x86_64/'+name,compress_type=zipfile.ZIP_DEFLATED)
  for name in ['libQnnHtp.so','libQnnHtpPrepare.so','libQnnHtpV79Stub.so','libQnnHtpV79Skel.so','libQnnSystem.so']+([] if release else ['libQnnGpu.so']):
    z.write(qnn/'jni/arm64-v8a'/name,'lib/arm64-v8a/'+name,compress_type=zipfile.ZIP_DEFLATED)
  for f in sorted((ROOT/'enhanced/proofread-data').iterdir()):
    if f.is_file():z.write(f,'assets/models/'+f.name,compress_type=zipfile.ZIP_DEFLATED)
  for folder,prefix in [(qnn,'qnn'),(ort,'onnxruntime')]:
    for f in folder.rglob('*'):
      if f.is_file() and ('license' in f.name.lower() or 'notice' in f.name.lower()):z.write(f,'assets/licenses/'+prefix+'/'+f.name,compress_type=zipfile.ZIP_DEFLATED)
  for model in ['t5tiny','macbert']:
    z.write(deps/model/'README.md','assets/licenses/'+model+'-model-card.md',compress_type=zipfile.ZIP_DEFLATED)
  for f in (ROOT/'enhanced/proofread-licenses').iterdir():z.write(f,'assets/licenses/'+f.name,compress_type=zipfile.ZIP_DEFLATED)
  for source,name in [('enhanced/rime-data/LICENSE','RIME-ICE-LICENSE'),('enhanced/rime-data/cn_dicts/8105.dict.yaml','rime-8105-source.yaml'),('enhanced/PROOFREADING-SOURCES.md','PROOFREADING-SOURCES.md')]:
    z.write(ROOT/source,'assets/licenses/'+name,compress_type=zipfile.ZIP_DEFLATED)
run(BT/'zipalign.exe','-P','16','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'proofread.apk',out/'aligned.apk')
run(BT/'apksigner.bat','verify',out/'proofread.apk')
print(out/'proofread.apk')
