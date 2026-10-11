"""Validate UI snapshots captured during the real AVD update installation."""
import argparse,json,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from release_version import VERSION
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--repair',action='store_true',help='Validate snapshots after a completed same-version repair.');a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an AVD is accepted.')
def snapshot(name):
 xml=subprocess.check_output([a.adb,'-s',a.serial,'shell','cat','/data/local/tmp/'+name],encoding='utf8')
 return list(ET.fromstring(xml).iter('node'))
management=snapshot('management-release-ui.xml');success=snapshot('repaired-updater-ui.xml' if a.repair else 'updated-updater-ui.xml');latest=snapshot('latest-updater-ui.xml')
assert any(n.get('text')=='Gboard 中文本地增强 '+VERSION for n in management)
assert any(n.get('text')=='检查更新' for n in management)
assert any('已安装并核验 '+VERSION in n.get('text','') for n in success)
assert any('已是最新版本' in n.get('text','') for n in latest)
assert any(n.get('text')=='重新安装此版本' and n.get('enabled')=='true' for n in latest)
assert any('手机已安装：' in n.get('text','') and '18.4.1-local-'+VERSION in n.get('text','') and '更新器：'+VERSION in n.get('text','') for n in latest)
path=Path(__file__).resolve().parent/('results/'+('avd-repaired-install-v' if a.repair else 'avd-installed-update-v')+VERSION+'.json')
result=json.loads(path.read_text('utf8'));result.update({'management_launcher_opens_updater':True,'verified_install_success_message_shown':True,'checking_again_reports_latest_verified_version':True,'same_version_repair_available':True,'actual_installed_versions_shown':True})
path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print('Management launcher, install success and latest-version UI verified.')
