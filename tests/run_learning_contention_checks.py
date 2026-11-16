"""Compare sync/typing contention in a disposable UID with synthetic files only."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--serial',required=True)
p.add_argument('--baseline-ref')
p.add_argument('--output',type=Path,required=True)
p.add_argument('--allow-isolated-phone',action='store_true',help='Allow the no-permission, no-Activity checker in its own temporary phone UID')
p.add_argument('--test-min-api',type=int,default=32,choices=[24,32],help='API 24 can check the portable store/monitor logic on an older test image; it does not validate the production IME')
a=p.parse_args()
physical=not a.serial.startswith('emulator-')
if physical and not a.allow_isolated_phone:p.error('Use a disposable AVD, or explicitly allow the isolated phone checker')
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',Path.home()/'Android/Sdk'))
bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar'
def run(*args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)
def adb(*args):return subprocess.check_output([str(sdk/'platform-tools/adb'),'-s',a.serial,*map(str,args)],text=True,timeout=180)
with tempfile.TemporaryDirectory(prefix='ime-contention-') as folder:
    w=Path(folder);(w/'classes').mkdir();(w/'dex').mkdir()
    sources=[];hashes={}
    for name in ['DictionaryFile','LearningFile','LearningStore','SelectionLearning','DictionaryStore','WordIndex']:
        relative=f'enhanced/src/local/gboard/enhanced/{name}.java'
        raw=subprocess.check_output(['git','show',f'{a.baseline_ref}:{relative}'],cwd=ROOT) if a.baseline_ref else (ROOT/relative).read_bytes()
        target=w/(name+'.java');target.write_bytes(raw);sources.append(target);hashes[relative]=hashlib.sha256(raw).hexdigest()
    checker=ROOT/'tests/android/LearningContentionInstrumentation.java';sources.append(checker);hashes[str(checker.relative_to(ROOT))]=hashlib.sha256(checker.read_bytes()).hexdigest()
    for source in sorted((ROOT/'enhanced/updater').glob('*.java')):
        sources.append(source);hashes[str(source.relative_to(ROOT))]=hashlib.sha256(source.read_bytes()).hexdigest()
    identity=w/'ReleaseIdentity.java';identity.write_text('package local.gboard.updater; final class ReleaseIdentity {static final String VERSION="test";static final long KEYBOARD=0,VOICE=0,UPDATER=0;}');sources.append(identity)
    run('javac','-encoding','UTF-8','-source','8','-target','8','-cp',android,'-d',w/'classes',*sources)
    run('jar','cf',w/'classes.jar','-C',w/'classes','.')
    run(bt/'d8','--min-api',a.test_min_api,'--lib',android,'--output',w/'dex',w/'classes.jar')
    (w/'AndroidManifest.xml').write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.gboard.contentioncheck"><uses-sdk android:minSdkVersion="{a.test_min_api}" android:targetSdkVersion="35"/><application android:debuggable="true" android:allowBackup="false"/><instrumentation android:name=".LearningContentionInstrumentation" android:targetPackage="local.gboard.contentioncheck"/></manifest>')
    run(bt/'aapt','package','-f','-M',w/'AndroidManifest.xml','-I',android,'-F',w/'unsigned.apk')
    with zipfile.ZipFile(w/'unsigned.apk','a') as z:z.write(w/'dex/classes.dex','classes.dex')
    run(bt/'zipalign','-f','4',w/'unsigned.apk',w/'aligned.apk')
    run(bt/'apksigner','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--out',w/'check.apk',w/'aligned.apk')
    installed=False
    try:
        print(adb('install','--no-streaming','-t',w/'check.apk').strip(),flush=True);installed=True
        out=adb('shell','am','instrument','-w','-e','require_fixed','false' if a.baseline_ref else 'true','local.gboard.contentioncheck/.LearningContentionInstrumentation')
        prefix='INSTRUMENTATION_RESULT: report='
        assert prefix in out,out
        report=json.loads(out.split(prefix)[1].splitlines()[0]);assert report['passed']
        report.update(source_ref=a.baseline_ref or 'working tree',device=adb('shell','getprop','ro.product.model').strip(),android_api=int(adb('shell','getprop','ro.build.version.sdk').strip()),physical_phone_tested=physical,isolated_test_uid=True,installed_keyboard_modified=False,physical_keyboard_first_key_measured=False,sources=hashes)
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
    finally:
        if installed:print(adb('uninstall','local.gboard.contentioncheck').strip(),flush=True)
