"""Verify the signed outputs and metadata before publishing."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,BT
def run(*args):return subprocess.check_output([str(x) for x in args],encoding='utf8',errors='replace')
def sha(data):return hashlib.sha256(data).hexdigest()
manifest=json.loads((ROOT/'update-manifest.json').read_text('utf8'))
paths={p.name:p for folder in ['enhanced/final','enhanced/voice-helper/final','enhanced/updater/out'] for p in (ROOT/folder).glob('*.apk')}
checks=[]
for component in manifest['components']:
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
    assert 'AdapterCheckActivity' not in xml
    assert re.search(r'android:debuggable[^\n]*0x0',xml)
    assert re.search(r'android:allowBackup[^\n]*0x0',xml)
    internet="name='android.permission.INTERNET'" in badging
    assert internet==(component['package']=='local.gboard.updater')
    if component['package']=='local.gboard.updater':
     assert 'android.permission.RECORD_AUDIO' not in badging and 'android.view.InputMethod' not in xml
    for n in z.namelist():
     if n.endswith('.dex'):assert b'AdapterCheckActivity' not in z.read(n)
   if path.name=='base.apk':
    with zipfile.ZipFile(ROOT/'gboard-18.4.1/base.apk') as original:
     name='lib/arm64-v8a/libintegrated_shared_object.so'
     assert sha(z.read(name))==sha(original.read(name))
  checks.append({'asset':path.name,'versionCode':component['versionCode'],'sha256':asset['sha256'],'signature_verified':True,'zipalign_16k_verified':True})
result={'release':'v1.1.0','apks':checks,'keyboard_native_decoder_unchanged':True,'keyboard_and_voice_have_no_internet':True,'debug_and_backup_disabled':True,'debug_test_activity_and_fixtures_excluded':True,'passed':True}
(ROOT/'tests/results/release-v1.1.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('Verified all six signed APKs, versions, metadata, permissions and native decoder.')
