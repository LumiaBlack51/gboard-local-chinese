"""Read-only verification of the explicitly selected phone after an ADB update."""
import argparse, json, re, subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--adb', default='adb')
p.add_argument('--serial', required=True)
p.add_argument('--before', required=True)
a = p.parse_args()
if a.serial.startswith('emulator-'):
    p.error('Use the AVD verifier for an emulator.')
root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'update-manifest.json').read_text('utf8'))
before = json.loads(Path(a.before).read_text('utf8'))

def adb(*args):
    return subprocess.check_output([a.adb, '-s', a.serial, *args], encoding='utf8', timeout=45)

checks = []
for component in manifest['components']+manifest.get('optionalComponents',[]):
    package = component['package']
    dump = adb('shell', 'dumpsys', 'package', package)
    assert 'versionCode=' + str(component['versionCode']) + ' ' in dump
    if package == 'com.localx.android.inputmethod.latin':
        first = re.search(r'firstInstallTime=([^\r\n]+)', dump).group(1).strip()
        assert first == before['firstInstallTime']
        assert 'versionName=18.4.1-local-' + manifest['version'] in dump
    paths = [line.removeprefix('package:') for line in adb('shell', 'pm', 'path', package).splitlines()
             if line.startswith('package:')]
    remaining = {asset['name']: asset for asset in component['assets']}
    assert len(paths) == len(remaining)
    for path in paths:
        name = path.rsplit('/', 1)[-1]
        asset = component['assets'][0] if name == 'base.apk' else remaining[name]
        assert adb('shell', 'sha256sum', path).split()[0] == asset['sha256'], asset['name']
        remaining.pop(asset['name'])
        checks.append({'asset': asset['name'], 'installed_sha256_matches': True})
    assert not remaining
ime = 'com.localx.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'
assert adb('shell', 'settings', 'get', 'secure', 'default_input_method').strip() == ime
report = {'version': manifest['version'],
          'method': 'Same-signature ADB replacement; no uninstall or data clearing.',
          'assets': checks, 'keyboard_first_install_time_preserved': before['firstInstallTime'],
          'default_input_method_is_enhanced_keyboard': True, 'passed': True}
(root / 'tests/results' / ('phone-installed-v' + manifest['version'] + '.json')).write_text(
    json.dumps(report, indent=2) + '\n', encoding='utf8')
print('Phone: seven installed APK hashes, versions, first installation time and default IME verified.')
