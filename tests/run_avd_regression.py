"""Run debug integration activities only on an explicitly selected emulator."""
import argparse,json,subprocess,time
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--voice-only',action='store_true')
a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an AVD is accepted; no global input is sent to a phone.')
root=Path(__file__).resolve().parent/'results';root.mkdir(exist_ok=True)
def adb(*args,check=True):
 return subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf8',check=check)
def read(pkg,name):
 r=adb('shell','run-as',pkg,'cat','files/'+name,check=False)
 try:return json.loads(r.stdout) if r.returncode==0 else None
 except json.JSONDecodeError:return None
def wait(pkg,name,limit=600):
 end=time.monotonic()+limit
 while time.monotonic()<end:
  report=read(pkg,name)
  if report is not None:return report
  time.sleep(1)
 raise TimeoutError(name)
def save(name,value):
 (root/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
pkg='com.localx.android.inputmethod.latin'
if not a.voice_only:
 adapter=read(pkg,'adapter-report.json')
 if not adapter or not adapter.get('passed'):raise RuntimeError('Run AdapterCheckActivity and obtain a passing report first.')
 save('avd-adapter-v1.1.1.json',adapter)
hook=read('local.gboard.voicecheck','hook-report.json')
if not hook or not hook.get('passed'):raise RuntimeError('Run HookCheckActivity with the test IME and obtain a passing voice hook report first.')
save('avd-voice-hook-v1.1.1.json',hook)
voices={}
for label,extras in [('mixed',['--ez','fixture','true','--ez','mixed','true']),('mandarin',['--ez','fixture','true']),('auto_finish',['--ez','fixture','true','--ez','auto_finish','true']),('audio_stop',['--ez','fixture','false']),('audio_cancel',['--ez','fixture','false','--ez','cancel','true'])]:
 pkg='local.gboard.voicecheck';adb('shell','am','force-stop',pkg)
 adb('shell','run-as',pkg,'rm','-f','files/voice-report.json')
 adb('shell','am','start','-n',pkg+'/.VoiceCheckActivity',*extras)
 report=wait(pkg,'voice-report.json',120)
 if not report.get('ready') or 'error' in report:raise RuntimeError(label+': '+str(report))
 if label=='mixed' and ('ALWAYS' not in report.get('recognized','').upper() or '这是' not in report['recognized']):raise RuntimeError('Mixed language fixture failed')
 if label=='mandarin' and report.get('recognized')!='今天下午召开会议请注意保护隐私':raise RuntimeError('Mandarin fixture failed')
 if label=='auto_finish' and (not report.get('endpoint_finished') or report.get('recognized')!='今天下午召开会议请注意保护隐私'):raise RuntimeError('Speech endpoint did not finish automatically')
 if label=='audio_stop' and not (report.get('audio_started') and report.get('ended') and report.get('results')):raise RuntimeError('Audio stop failed')
 if label=='audio_cancel' and not (report.get('audio_started') and report.get('cancel_requested')):raise RuntimeError('Audio cancel failed')
 voices[label]=report;print(label+': passed',flush=True)
save('avd-voice-v1.1.1.json',{'model':'sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile','fixtures':'Official model test_wavs/1.wav and locally synthesized Mandarin PCM16; no private recordings.','checks':voices,'passed':True})
