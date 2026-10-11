"""Snapshot/check synthetic AVD Rime files and preferences across updater installs."""
import argparse,json,re,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from release_version import VERSION
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--phase',choices=['before','after'],required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an explicitly selected AVD is accepted.')
output=subprocess.check_output([a.adb,'-s',a.serial,'shell','am','instrument','-w','-e','phase',a.phase,'local.gboard.releasetest/.RetentionInstrumentation'],encoding='utf8',timeout=60)
match=re.search(r'^INSTRUMENTATION_RESULT: report=(.*)$',output,re.M)
if not match:raise RuntimeError(output)
report=json.loads(match.group(1));assert report.get('passed'),report
if a.phase=='after':
 (Path(__file__).resolve().parent/'results'/('avd-update-retention-v'+VERSION+'.json')).write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('AVD local state '+a.phase+': '+json.dumps(report))
