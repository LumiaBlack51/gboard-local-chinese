"""Check the formal AVD installation against all signed release assets."""
import argparse,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an explicitly selected AVD is accepted.')
root=Path(__file__).resolve().parent.parent
manifest=json.loads((root/'update-manifest.json').read_text('utf8'))
def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],encoding='utf8')
checks=[]
for part in manifest['components']:
 dump=adb('shell','dumpsys','package',part['package']);assert 'versionCode='+str(part['versionCode'])+' ' in dump
 paths=[s.removeprefix('package:') for s in adb('shell','pm','path',part['package']).splitlines() if s.startswith('package:')]
 assert len(paths)==len(part['assets'])
 remaining={x['name']:x for x in part['assets']}
 for path in paths:
  name=path.rsplit('/',1)[-1];asset=part['assets'][0] if name=='base.apk' else remaining[name]
  assert adb('shell','sha256sum',path).split()[0]==asset['sha256'];remaining.pop(asset['name'])
  checks.append({'package':part['package'],'asset':asset['name'],'installed_sha256_matches_release':True})
 assert not remaining
result={'release':manifest['version'],'method':'ADB replacement install on AVD; no application data cleared.','checks':checks,'passed':True}
(root/'tests/results'/('avd-installed-release-v'+manifest['version']+'.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('All six formally installed APKs match the release and version codes.')
