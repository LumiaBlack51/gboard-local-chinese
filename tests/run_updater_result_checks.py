"""Check the actual updater receiver refreshes the open Activity after system failure."""
import argparse,json,re,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from release_version import VERSION
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an explicitly selected AVD is accepted.')
output=subprocess.check_output([a.adb,'-s',a.serial,'shell','am','instrument','-w','local.gboard.releasetest/.UpdaterResultInstrumentation'],encoding='utf8',timeout=60)
match=re.search(r'^INSTRUMENTATION_RESULT: report=(.*)$',output,re.M)
if not match:raise RuntimeError(output)
report=json.loads(match.group(1));assert report.get('passed'),report
(Path(__file__).resolve().parent/'results'/('avd-updater-result-v'+VERSION+'.json')).write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('Verification refusal and cancellation immediately refresh the open updater.')
