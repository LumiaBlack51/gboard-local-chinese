"""Create release integrity metadata from the signed build outputs."""
from pathlib import Path
import hashlib,json,sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,certificate_digest
from release_version import VERSION,KEYBOARD_CODE,VOICE_CODE,UPDATER_CODE,PROOF_CODE
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def save(name,data):(ROOT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
groups=[('com.localx.android.inputmethod.latin',KEYBOARD_CODE,[ROOT/'enhanced/final'/n for n in ['base.apk','split_brella_feature_split.apk','split_config.xxxhdpi.apk','split_tenoranimation_feature_split.apk']]),('local.gboard.voice',VOICE_CODE,[ROOT/'enhanced/voice-helper/final/voice-helper.apk']),('local.gboard.updater',UPDATER_CODE,[ROOT/'enhanced/updater/out/updater.apk'])]
groups.append(('local.gboard.proofread',PROOF_CODE,[ROOT/'enhanced/proofread/final/proofread.apk']))
components=[];assets=[]
for package,version,paths in groups:
 items=[]
 for path in paths:
  sha=digest(path);size=path.stat().st_size
  items.append({'name':path.name,'sha256':sha,'size':size})
  assets.append({'file':path.relative_to(ROOT).as_posix(),'bytes':size,'sha256':sha,'name':path.name})
 components.append({'package':package,'versionCode':version,'assets':items})
save('update-manifest.json',{'format':1,'version':VERSION,'certificateSha256':certificate_digest().hex(),'notes':'中英离线语音校对、英文大小写整理；候选栏支持叉、展开及两者并列，默认替换为叉。包含独立校对组件；首次打开「中英本地校对」完成准备。旧更新器先升级核心组件，再检查一次安装校对组件。','components':components[:3],'optionalComponents':components[3:]})
save('release-manifest.json',{'tag':'v'+VERSION,'assets':assets})
(ROOT/'SHA256SUMS.txt').write_text(''.join(a['sha256']+'  '+a['name']+'\n' for a in assets),encoding='ascii')
source={}
for folder in ['enhanced/src','enhanced/native','enhanced/voice-helper','enhanced/voicecheck','enhanced/testsrc','enhanced/updater','enhanced/releasetest','tools','probe/src','enhanced/enginecheck','enhanced/proofread']:
 for path in (ROOT/folder).rglob('*'):
  if any(p in ('out','final','lib') for p in path.relative_to(ROOT).parts):continue
  if path.suffix in ('.java','.cpp','.h'):source[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
save('source-manifest.json',source)
deps=json.loads((ROOT/'dependency-manifest.json').read_text('utf8'))
deps=[d for d in deps if 'vosk' not in d['file'].lower() and not d['file'].startswith(('enhanced/voice-data/','enhanced/handwriting-data/','enhanced/proofread-data/')) and not d['file'].endswith(('libonnxruntime.so','libsherpa-onnx-c-api.so','libgboard_local_handwriting.so'))]
for base in ['enhanced/voice-data','enhanced/handwriting-data','enhanced/proofread-data','enhanced/native/lib/arm64-v8a','enhanced/native/lib/x86_64']:
 for path in (ROOT/base).iterdir():
  if not path.is_file() or (path.suffix=='.so' and path.name not in ('libonnxruntime.so','libsherpa-onnx-c-api.so','libgboard_local_handwriting.so')):continue
  deps.append({'file':path.relative_to(ROOT).as_posix(),'bytes':path.stat().st_size,'sha256':digest(path)})
save('dependency-manifest.json',deps)
print('Metadata ready for',len(assets),'APKs')
