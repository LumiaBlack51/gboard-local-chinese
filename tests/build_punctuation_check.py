"""Build disposable instrumentation signed for the formal voice helper."""
import sys,subprocess,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,JAVA,BT,ANDROID,SIGN_ARGS
def run(*args):subprocess.run([str(x) for x in args],check=True)
out=ROOT/'artifacts/punctuation/check';classes=out/'classes';dex=out/'dex'
classes.mkdir(parents=True,exist_ok=True);dex.mkdir(exist_ok=True)
manifest=out/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.gboard.punctuationcheck">
<uses-sdk android:minSdkVersion="32" android:targetSdkVersion="35" />
<application android:allowBackup="false" android:label="Synthetic punctuation check" />
<instrumentation android:name=".PunctuationInstrumentation" android:targetPackage="local.gboard.voice" />
</manifest>''','utf8')
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',str(ANDROID)+';'+str(ROOT/'enhanced/voice-helper/final/helper.jar'),'-d',classes,ROOT/'tests/android/PunctuationInstrumentation.java')
run(JAVA/'jar.exe','cf',out/'check.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'check.jar')
run(BT/'aapt.exe','package','-f','-M',manifest,'-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
    z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
    z.write(ROOT/'enhanced/speech-fixture.wav','assets/speech-fixture.wav',compress_type=zipfile.ZIP_STORED)
run(BT/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'check.apk',out/'aligned.apk')
print(out/'check.apk')
