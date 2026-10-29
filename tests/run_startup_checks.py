"""Android AtomicFile/preload/emoji regression on an explicitly selected test AVD."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from release_version import VERSION
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Use an isolated AVD, never a personal phone')
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',Path.home()/'Android/Sdk'));bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar'
def tool(n):return bt/(n+'.bat' if os.name=='nt' and n in ('d8','apksigner') else n+'.exe' if os.name=='nt' else n)
def run(*args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def adb(*args):return subprocess.check_output([str(sdk/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')),'-s',a.serial,*args],text=True,timeout=120)
with tempfile.TemporaryDirectory(prefix='ime-startup-') as folder:
 w=Path(folder);(w/'classes').mkdir();(w/'dex').mkdir();(w/'assets').mkdir();(w/'assets/emoji.txt').write_bytes((ROOT/'enhanced/rime-data/emoji.txt').read_bytes())
 names=['DictionaryFile','LearningFile','LearningStore','SelectionLearning','EmojiSuggestions'];sources=[ROOT/'enhanced/src/local/gboard/enhanced'/(n+'.java') for n in names]+[ROOT/'tests/android/StartupInstrumentation.java']
 run('javac','-encoding','UTF-8','-source','8','-target','8','-cp',android,'-d',w/'classes',*sources)
 run('jar','cf',w/'classes.jar','-C',w/'classes','.')
 run(tool('d8'),'--min-api','32','--lib',android,'--output',w/'dex',w/'classes.jar')
 (w/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.gboard.startupcheck"><uses-sdk android:minSdkVersion="32" android:targetSdkVersion="35"/><application android:allowBackup="false"/><instrumentation android:name=".StartupInstrumentation" android:targetPackage="local.gboard.startupcheck"/></manifest>')
 run(tool('aapt'),'package','-f','-M',w/'AndroidManifest.xml','-A',w/'assets','-I',android,'-F',w/'unsigned.apk')
 with zipfile.ZipFile(w/'unsigned.apk','a') as z:z.write(w/'dex/classes.dex','classes.dex')
 run(tool('zipalign'),'-f','4',w/'unsigned.apk',w/'aligned.apk')
 run(tool('apksigner'),'sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--out',w/'check.apk',w/'aligned.apk')
 installed=False
 try:
  print(adb('install','--no-streaming','-r','-t',str(w/'check.apk')).strip());installed=True
  out=adb('shell','am','instrument','-w','local.gboard.startupcheck/.StartupInstrumentation')
  prefix='INSTRUMENTATION_RESULT: report='
  assert prefix in out,out
  report=json.loads(out.split(prefix)[1].splitlines()[0]);assert report['passed']
  report.update(version=VERSION,device='isolated x86_64 AVD',physical_phone_tested=False,real_energy_consumption_measured=False,sources={str(n.relative_to(ROOT)):hashlib.sha256(n.read_bytes()).hexdigest() for n in sources})
  (ROOT/f'tests/results/startup-preload-v{VERSION}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
 finally:
  if installed:print(adb('uninstall','local.gboard.startupcheck').strip())
