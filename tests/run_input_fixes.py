"""Verify bottom-row touch and speech insertion in synthetic editors on an AVD."""
import argparse, hashlib, json, subprocess, sys, time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root))
from release_version import VERSION

p = argparse.ArgumentParser()
p.add_argument('--adb', default='adb')
p.add_argument('--serial', required=True)
p.add_argument('--voice-only', action='store_true', help='Reuse this version\'s already passing gesture report.')
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    p.error('Only an AVD is accepted.')

def adb(*args, check=True):
    return subprocess.run([a.adb, '-s', a.serial, *args], capture_output=True,
                          text=True, encoding='utf8', check=check, timeout=45)

def wait(package, filename):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        result = adb('shell', 'run-as', package, 'cat', 'files/' + filename, check=False)
        if result.returncode == 0:
            report = json.loads(result.stdout)
            if not report.get('passed'):
                raise RuntimeError(str(report))
            return report
        time.sleep(2)
    raise TimeoutError(filename)

def save(filename, report):
    (root / 'tests/results' / filename).write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')

keyboard = 'com.localx.android.inputmethod.latin'
voice = 'local.gboard.voicecheck'
previous = adb('shell', 'settings', 'get', 'secure', 'default_input_method').stdout.strip()
checks = {}
try:
    if a.voice_only:
        checks['gestures'] = json.loads((root / 'tests/results' / ('avd-gestures-v' + VERSION + '.json')).read_text('utf8'))
        assert checks['gestures']['passed']
    else:
        adb('shell', 'am', 'force-stop', keyboard)
        adb('shell', 'run-as', keyboard, 'rm', '-f', 'files/gesture-report.json')
        adb('shell', 'am', 'start', '-n', keyboard + '/local.gboard.enhanced.GestureCheckActivity')
        checks['gestures'] = wait(keyboard, 'gesture-report.json')
    assert all(checks['gestures'].get('lower_edge_' + c + '_' + str(y))
               for c in 'cvbn' for y in (290, 303, 325))
    save('avd-gestures-v' + VERSION + '.json', checks['gestures'])
    print('Bottom letter edges, space confirmation, caret dragging and handwriting: passed', flush=True)
    for label, extras in [('restart', ['--ez', 'restart', 'true']),
                          ('retry', ['--ez', 'retry', 'true']), ('normal', [])]:
        # Keep the test IME process alive; replace only the synthetic editor task.
        # Force-stopping its package also asynchronously replaces the selected IME.
        adb('shell', 'run-as', voice, 'rm', '-f', 'files/hook-report.json')
        adb('shell', 'ime', 'enable', voice + '/.HookCheckService')
        adb('shell', 'ime', 'set', voice + '/.HookCheckService')
        adb('shell', 'am', 'start', '-f', '0x10008000', '-n', voice + '/.HookCheckActivity', *extras)
        checks[label] = wait(voice, 'hook-report.json')
        save('avd-input-voice-' + label + '-v' + VERSION + '.json', checks[label])
        print('Speech insertion ' + label + ': passed', flush=True)
    save('avd-voice-hook-v' + VERSION + '.json', checks['normal'])
finally:
    if previous and previous != 'null':
        adb('shell', 'ime', 'set', previous, check=False)

sources = {str(path.relative_to(root)).replace('\\', '/'):
           hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()
           for path in (root / 'enhanced/src/local/gboard/enhanced/VoiceHook.java',
                        root / 'enhanced/src/local/gboard/enhanced/KeyboardGestures.java')}
save('avd-input-fixes-v' + VERSION + '.json',
     {'version': VERSION, 'sources': sources, 'checks': checks, 'passed': True})
