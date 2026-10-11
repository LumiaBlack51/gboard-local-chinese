"""Create release integrity metadata from the signed build outputs."""
from pathlib import Path
import hashlib,json,sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,certificate_digest
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def save(name,data):(ROOT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
groups=[('com.localx.android.inputmethod.latin',176027080,[ROOT/'enhanced/final'/n for n in ['base.apk','split_brella_feature_split.apk','split_config.xxxhdpi.apk','split_tenoranimation_feature_split.apk']]),('local.gboard.voice',3,[ROOT/'enhanced/voice-helper/final/voice-helper.apk']),('local.gboard.updater',3,[ROOT/'enhanced/updater/out/updater.apk'])]
components=[];assets=[]
for package,version,paths in groups:
 items=[]
 for path in paths:
  sha=digest(path);size=path.stat().st_size
  items.append({'name':path.name,'sha256':sha,'size':size})
  assets.append({'file':path.relative_to(ROOT).as_posix(),'bytes':size,'sha256':sha,'name':path.name})
 components.append({'package':package,'versionCode':version,'assets':items})
save('update-manifest.json',{'format':1,'version':'1.1.1','certificateSha256':certificate_digest().hex(),'notes':'语音点即开始、停顿后直接填入；展开候选与关闭联想分离，未确认拼音时不显示 ×。','components':components})
save('release-manifest.json',{'tag':'v1.1.1','assets':assets})
(ROOT/'SHA256SUMS.txt').write_text(''.join(a['sha256']+'  '+a['name']+'\n' for a in assets),encoding='ascii')
source={}
for folder in ['enhanced/src','enhanced/native','enhanced/voice-helper','enhanced/voicecheck','enhanced/testsrc','enhanced/updater','enhanced/releasetest','tools','probe/src','enhanced/enginecheck']:
 for path in (ROOT/folder).rglob('*'):
  if any(p in ('out','final','lib') for p in path.relative_to(ROOT).parts):continue
  if path.suffix in ('.java','.cpp','.h'):source[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
save('source-manifest.json',source)
deps=json.loads((ROOT/'dependency-manifest.json').read_text('utf8'))
deps=[d for d in deps if 'vosk' not in d['file'].lower() and not d['file'].startswith('enhanced/voice-data/') and not d['file'].endswith(('libonnxruntime.so','libsherpa-onnx-c-api.so'))]
for base in ['enhanced/voice-data','enhanced/native/lib/arm64-v8a','enhanced/native/lib/x86_64']:
 for path in (ROOT/base).iterdir():
  if not path.is_file() or (path.suffix=='.so' and path.name not in ('libonnxruntime.so','libsherpa-onnx-c-api.so')):continue
  deps.append({'file':path.relative_to(ROOT).as_posix(),'bytes':path.stat().st_size,'sha256':digest(path)})
save('dependency-manifest.json',deps)
print('Metadata ready for',len(assets),'APKs')
