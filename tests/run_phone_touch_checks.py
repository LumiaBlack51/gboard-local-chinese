"""Opt-in checks on this phone's observed 1272 x 2800 keyboard geometry.

Uses only local.gboard.probe's disposable private editor and the formal IME.
No test IME, instrumentation, chat app, dictionary reads, or test audio.
"""
import argparse, hashlib, json, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
p = argparse.ArgumentParser()
p.add_argument('--adb', default='adb')
p.add_argument('--serial', required=True)
a = p.parse_args()

def adb(*args):
    return subprocess.check_output([a.adb, '-s', a.serial, *args], timeout=45)

def guard():
    state = adb('shell', 'dumpsys', 'input_method').decode('utf8', 'replace')
    if not re.search(r'currentEditorInfo packageName: local\.gboard\.probe,', state):
        raise RuntimeError('The private probe editor must be the current editor.')
    windows = adb('shell', 'dumpsys', 'window').decode('utf8', 'replace')
    if not re.search(r'mCurrentFocus=Window\{[^\r\n]*local\.gboard\.probe/', windows):
        raise RuntimeError('The probe must remain the focused app.')

def tap(x, y):
    guard()
    adb('shell', 'input', 'tap', str(x), str(y))

def fresh():
    adb('shell', 'am', 'start', '-f', '0x10008000', '-n',
        'local.gboard.probe/.MainActivity', '--ez', 'private', 'true')
    time.sleep(.65)
    guard()

def text():
    guard()
    adb('shell', 'uiautomator', 'dump', '/sdcard/local-probe-check.xml')
    xml = ET.fromstring(adb('shell', 'cat', '/sdcard/local-probe-check.xml'))
    items = [n for n in xml.iter('node') if n.get('package') == 'local.gboard.probe'
             and n.get('class') == 'android.widget.EditText']
    if len(items) != 1:
        raise RuntimeError('Probe editor not visible.')
    value = items[0].get('text', '')
    return '' if value == '仅输入测试句子' else value

def snapshot(name):
    guard()
    (root / 'artifacts' / name).write_bytes(adb('exec-out', 'screencap', '-p'))

size = adb('shell', 'wm', 'size').decode()
assert re.search(r'(?:Physical|Override) size: 1272x2800', size), size
version = (root / 'release_version.py').read_text('utf8')
version = re.search(r"VERSION='([^']+)'", version).group(1)
report = {'version': version, 'method': 'Formal keyboard, private disposable probe editor, observed phone geometry.',
          'before_fix': {'n_center_produced_j': True, 'evidence': 'artifacts/phone-probe-n-before.png'},
          'checks': [], 'passed': False}
destination = root / 'tests/results' / ('phone-touch-v' + version + '.json')
def save():
    report['keyboard_gestures_sha256'] = hashlib.sha256((root / 'enhanced/src/local/gboard/enhanced/KeyboardGestures.java').read_bytes().replace(b'\r\n', b'\n')).hexdigest()
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')

try:
    for label, y in [('upper', 2240), ('center', 2300), ('lower', 2380), ('gap', 2410), ('space_edge', 2460)]:
        for letter, x in [('c', 510), ('v', 635), ('b', 760), ('n', 890)]:
            fresh()
            tap(x, y)
            tap(1170, 2520)  # The formal keyboard's literal-pinyin Enter.
            actual = text()
            check = {'key': letter, 'position': label, 'screen': [x, y], 'actual': actual, 'passed': actual == letter}
            report['checks'].append(check)
            save()
            print(letter, label, repr(actual), flush=True)
            if not check['passed']:
                snapshot('phone-touch-failure-v' + version + '.png')
                raise AssertionError(check)
    # Space center must still select Chinese, rather than redirecting to V.
    fresh()
    for x, y in [(890, 2240), (955, 1840)]:
        tap(x, y)
    tap(635, 2520)
    actual = text()
    report['space_center_selects_chinese'] = actual == '你'
    assert report['space_center_selects_chinese'], actual
    fresh()
    for x, y in [(510, 2300), (635, 2300), (760, 2300), (890, 2300)]:
        tap(x, y)
    snapshot('phone-cvbn-v' + version + '.png')
    tap(1170, 2520)
    report['consecutive_cvbn'] = text() == 'cvbn'
    assert report['consecutive_cvbn']
    report['passed'] = True
finally:
    save()
