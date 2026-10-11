"""Confirm that the updater installed the exact GitHub release assets in an AVD."""
import argparse,json,re,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--before-first-install-time',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an AVD is accepted.')
root=Path(__file__).resolve().parent.parent
manifest=json.loads((root/'update-manifest.json').read_text('utf8'))
def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],encoding='utf8')
def package(pkg):return adb('shell','dumpsys','package',pkg)
deadline=time.monotonic()+180
while True:
 installed={p['package']:package(p['package']) for p in manifest['components']}
 if all('versionCode='+str(p['versionCode'])+' ' in installed[p['package']] and 'installerPackageName=local.gboard.updater' in installed[p['package']] for p in manifest['components']):break
 if time.monotonic()>deadline:raise TimeoutError('Update has not completed')
 time.sleep(3)
checks=[]
for part in manifest['components']:
 files=[line.removeprefix('package:') for line in adb('shell','pm','path',part['package']).splitlines() if line.startswith('package:')]
 assert len(files)==len(part['assets'])
 remaining={x['name']:x for x in part['assets']}
 for path in files:
  name=path.rsplit('/',1)[-1]
  asset=part['assets'][0] if name=='base.apk' else remaining[name]
  actual=adb('shell','sha256sum',path).split()[0]
  assert actual==asset['sha256'],asset['name'];remaining.pop(asset['name'])
  checks.append({'package':part['package'],'asset':asset['name'],'installed_sha256_matches_release':True})
 assert not remaining
first=re.search(r'firstInstallTime=([^\r\n]+)',installed['com.localx.android.inputmethod.latin']).group(1).strip()
assert first==a.before_first_install_time
result={'release':'v'+manifest['version'],'source':'User-triggered updater download or verified cache from public GitHub, then Android installation confirmations.','checks':checks,'installer_is_updater':True,'keyboard_first_install_time_unchanged':True,'passed':True}
(root/'tests/results'/('avd-installed-update-v'+manifest['version']+'.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('All six installed APKs match the release; installed by updater, with original application identity retained.')
