"""Verify actual audio lifecycle and disabled fixture access in the installed release."""
import argparse,json,subprocess,re
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an explicitly selected AVD is accepted.')
result=subprocess.check_output([a.adb,'-s',a.serial,'shell','am','instrument','-w','local.gboard.releasetest/.VoiceReleaseInstrumentation'],encoding='utf8',timeout=400)
match=re.search(r'^INSTRUMENTATION_RESULT: report=(.*)$',result,re.M)
if not match:raise RuntimeError(result)
report=json.loads(match.group(1));assert report.get('passed'),report
path=Path(__file__).resolve().parent/'results/avd-release-voice-v1.1.json'
path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Shipped service: audio start/stop/cancel passed; debug fixture access rejected.')
