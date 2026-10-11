"""Validate UI snapshots captured during the real AVD update installation."""
import argparse,json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an AVD is accepted.')
def snapshot(name):
 xml=subprocess.check_output([a.adb,'-s',a.serial,'shell','cat','/data/local/tmp/'+name],encoding='utf8')
 return list(ET.fromstring(xml).iter('node'))
management=snapshot('management-release-ui.xml');success=snapshot('updated-updater-ui.xml');latest=snapshot('latest-updater-ui.xml')
assert any(n.get('text')=='Gboard 中文本地增强 1.1.0' for n in management)
assert any(n.get('text')=='检查更新' for n in management)
assert any(n.get('text')=='Gboard 中文增强已更新' for n in success)
assert any('已是最新版本' in n.get('text','') for n in latest)
assert any(n.get('text')=='下载并安装更新' and n.get('enabled')=='false' for n in latest)
path=Path(__file__).resolve().parent/'results/avd-installed-update-v1.1.json'
result=json.loads(path.read_text('utf8'));result.update({'management_launcher_opens_updater':True,'install_success_message_shown':True,'checking_again_reports_latest_version':True,'redundant_install_disabled':True})
path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('Management launcher, install success and latest-version UI verified.')
