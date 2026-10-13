"""Exercise real cross-process PCM descriptors, EOF and early stop with synthetic audio."""
import argparse, hashlib, json, subprocess, sys, time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root))
from release_version import VERSION
p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    p.error('Use the isolated AVD test client.')

def adb(*args, check=True):
    return subprocess.run([a.adb, '-s', a.serial, *args], check=check,
                          capture_output=True, text=True, encoding='utf8', timeout=45)

checks = {}
for label, extras in [('eof', []), ('stop_during_model_start', ['--ez', 'early_stop', 'true']),
                      ('endpoint', ['--ez', 'auto_finish', 'true'])]:
    adb('shell', 'am', 'force-stop', 'local.gboard.voicecheck')
    adb('shell', 'am', 'force-stop', 'local.gboard.voice')
    adb('shell', 'run-as', 'local.gboard.voicecheck', 'rm', '-f', 'files/voice-report.json')
    adb('shell', 'am', 'start', '-n', 'local.gboard.voicecheck/.VoiceCheckActivity',
        '--ez', 'pipe', 'true', *extras)
    deadline = time.monotonic() + 120
    while True:
        result = adb('shell', 'run-as', 'local.gboard.voicecheck', 'cat', 'files/voice-report.json', check=False)
        if result.returncode == 0:
            report = json.loads(result.stdout)
            assert report.get('pipe') and report.get('ready'), report
            assert report.get('recognized') == '今天下午召开会议请注意保护隐私', report
            assert 'error' not in report, report
            checks[label] = report
            print(label + ': passed', flush=True)
            break
        if time.monotonic() > deadline:
            raise TimeoutError(label)
        time.sleep(1)
sources = {}
for name in ['enhanced/voice-helper/OfflineRecognitionService.java', 'enhanced/voicecheck/VoiceCheckActivity.java']:
    sources[name] = hashlib.sha256((root / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
(root / 'tests/results' / ('avd-voice-pipe-v' + VERSION + '.json')).write_text(
    json.dumps({'version': VERSION, 'sources': sources, 'checks': checks, 'passed': True},
               ensure_ascii=False, indent=2) + '\n', encoding='utf8')
