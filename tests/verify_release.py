"""Verify the signed outputs and metadata before publishing."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,BT
def run(*args):return subprocess.check_output([str(x) for x in args],encoding='utf8',errors='replace')
def sha(data):return hashlib.sha256(data).hexdigest()
manifest=json.loads((ROOT/'update-manifest.json').read_text('utf8'))
paths={p.name:p for folder in ['enhanced/final','enhanced/voice-helper/final','enhanced/updater/out','enhanced/proofread/final'] for p in (ROOT/folder).glob('*.apk')}
checks=[]
for component in manifest['components']+manifest.get('optionalComponents',[]):
 for asset in component['assets']:
  path=paths[asset['name']]
  assert path.stat().st_size==asset['size'] and sha(path.read_bytes())==asset['sha256']
  signing=run(BT/'apksigner.bat','verify','--print-certs',path)
  cert=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signing).group(1)
  assert cert==manifest['certificateSha256']
  run(BT/'zipalign.exe','-c','-P','16','4',path)
  badging=run(BT/'aapt.exe','dump','badging',path)
  assert "name='"+component['package']+"'" in badging.splitlines()[0]
  assert "versionCode='"+str(component['versionCode'])+"'" in badging.splitlines()[0]
  with zipfile.ZipFile(path) as z:
   assert not any(n.startswith('assets/speech-') for n in z.namelist())
   if not path.name.startswith('split_'):
    xml=run(BT/'aapt.exe','dump','xmltree',path,'AndroidManifest.xml')
    assert all(name not in xml for name in ['AdapterCheckActivity','GestureCheckActivity','FontSizeCheckActivity','KeyboardModesCheckActivity'])
    assert re.search(r'android:debuggable[^\n]*0x0',xml)
    assert re.search(r'android:allowBackup[^\n]*0x0',xml)
    internet="name='android.permission.INTERNET'" in badging
    assert internet==(component['package']=='local.gboard.updater')
    if component['package']=='local.gboard.updater':
     assert 'android.permission.RECORD_AUDIO' not in badging and 'android.view.InputMethod' not in xml
    for n in z.namelist():
     if n.endswith('.dex'):assert all(name not in z.read(n) for name in [b'AdapterCheckActivity',b'GestureCheckActivity',b'FontSizeCheckActivity',b'KeyboardModesCheckActivity'])
   if path.name=='base.apk':
    with zipfile.ZipFile(ROOT/'gboard-18.4.1/base.apk') as original:
     name='lib/arm64-v8a/libintegrated_shared_object.so'
     assert sha(z.read(name))==sha(original.read(name))
    assert sha(z.read('assets/local-handwriting/handwriting-zh_CN.model'))=='e16153d1ff267cd479aea260d6f71a3edda8b4ba06db2d121513adda65a4449e'
    assert 'lib/arm64-v8a/libgboard_local_handwriting.so' in z.namelist()
    helper=z.read('classes5.dex');assert '结束录音'.encode() in helper and '点击开始后录音'.encode() not in helper
   if path.name=='voice-helper.apk':
    assert sha(z.read('assets/local-punctuation/model.int8.onnx'))=='65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1'
    assert 'assets/licenses/PUNCTUATION-LICENSE' in z.namelist()
  checks.append({'asset':path.name,'versionCode':component['versionCode'],'sha256':asset['sha256'],'signature_verified':True,'zipalign_16k_verified':True})
result={'release':'v'+manifest['version'],'apks':checks,'keyboard_native_decoder_unchanged':True,'keyboard_and_voice_have_no_internet':True,'debug_and_backup_disabled':True,'debug_test_activity_and_fixtures_excluded':True,'bundled_handwriting_model_hash_verified':True,'voice_has_no_start_confirmation':True,'passed':True}
(ROOT/'tests/results'/('release-v'+manifest['version']+'.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('Verified all seven signed APKs, versions, metadata, permissions and native decoder.')
