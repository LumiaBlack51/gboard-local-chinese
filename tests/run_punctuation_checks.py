"""Check text guards on the host and the actual signed model on one chosen device."""
import argparse,hashlib,json,re,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,JAVA
from release_version import VERSION

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial');a=p.parse_args()
def run(*args):return subprocess.check_output([str(x) for x in args],encoding='utf8',errors='replace',timeout=180)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
out=ROOT/'artifacts/punctuation/java';out.mkdir(parents=True,exist_ok=True)
sources=[ROOT/'enhanced/src/local/gboard/enhanced'/n for n in ['VoicePunctuation.java','VoiceText.java']]
run(JAVA/'javac.exe','-encoding','UTF-8','-d',out,*sources,ROOT/'tests/VoicePunctuationTest.java',ROOT/'tests/VoiceTextTest.java')
logs=[run(JAVA/'java.exe','-cp',out,test).strip() for test in ['VoicePunctuationTest','VoiceTextTest']]
source_names=['enhanced/src/local/gboard/enhanced/VoicePunctuation.java','enhanced/src/local/gboard/enhanced/VoiceBridge.java','enhanced/native/voice.cpp','enhanced/voice-helper/OfflineRecognitionService.java']
source_hashes={n:hashlib.sha256((ROOT/n).read_bytes().replace(b'\r\n',b'\n')).hexdigest() for n in source_names}
report={'version':VERSION,'checks':logs,'sources':source_hashes,'passed':True}
(ROOT/'tests/results'/('punctuation-policy-v'+VERSION+'.json')).write_text(json.dumps(report,indent=2)+'\n','utf8')
print('\n'.join(logs))
if a.serial:
    def adb(*args):return run(a.adb,'-s',a.serial,*args)
    paths=adb('shell','pm','path','local.gboard.voice').splitlines();assert len(paths)==1,paths
    installed_hash=adb('shell','sha256sum',paths[0].removeprefix('package:')).split()[0]
    assert installed_hash==sha(ROOT/'enhanced/voice-helper/final/voice-helper.apk'),'Installed voice APK differs from formal local build'
    result=adb('shell','am','instrument','-w','local.gboard.punctuationcheck/.PunctuationInstrumentation')
    match=re.search(r'^INSTRUMENTATION_RESULT: report=(.*)$',result,re.M)
    if not match:raise RuntimeError(result)
    device=json.loads(match[1]);assert device['passed']
    device.update({'version':VERSION,'device_model':adb('shell','getprop','ro.product.model').strip(),'voice_apk_sha256':installed_hash,'sources':source_hashes})
    kind='avd' if a.serial.startswith('emulator-') else 'phone'
    (ROOT/'tests/results'/(kind+'-punctuation-v'+VERSION+'.json')).write_text(json.dumps(device,ensure_ascii=False,indent=2)+'\n','utf8')
    print(json.dumps(device,ensure_ascii=False,indent=2))
