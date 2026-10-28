"""Build temporary headless checkers; candidate checks require an isolated editor/device."""
from pathlib import Path
import subprocess,sys,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_config import ROOT,JAVA,BT,ANDROID,SIGN_ARGS

def run(*args):subprocess.run([str(a) for a in args],check=True)
for package,klass,target in [('local.gboard.synccheck','PhraseSyncInstrumentation','local.gboard.updater'),('local.gboard.candidateregression','CandidateRegressionInstrumentation','com.localx.android.inputmethod.latin'),('local.gboard.phonecandidatecheck','PhoneCandidateInstrumentation','com.localx.android.inputmethod.latin')]:
    out=ROOT/'artifacts/sync-check'/package
    classes=out/'classes';dex=out/'dex';classes.mkdir(parents=True,exist_ok=True);dex.mkdir(exist_ok=True)
    manifest=out/'AndroidManifest.xml';manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="2"><uses-sdk android:minSdkVersion="32" android:targetSdkVersion="35" /><application android:allowBackup="false" /><instrumentation android:name=".{klass}" android:targetPackage="{target}" android:functionalTest="true" /></manifest>''')
    run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,ROOT/'tests/android'/(klass+'.java'))
    run(JAVA/'jar.exe','cf',out/'classes.jar','-C',classes,'.')
    run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'classes.jar')
    run(BT/'aapt.exe','package','-f','-M',manifest,'-I',ANDROID,'-F',out/'unsigned.apk')
    with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex')
    run(BT/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk')
    run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'check.apk',out/'aligned.apk')
