"""Build a temporary same-signature checker; never included in release APKs."""
from pathlib import Path
import subprocess, sys, zipfile
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from build_config import ROOT, JAVA, BT, ANDROID, SIGN_ARGS

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

out = ROOT / 'artifacts/toolbar/check'
classes, dex = out / 'classes', out / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(exist_ok=True)
manifest = out / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.gboard.toolbarcheck">
<uses-sdk android:minSdkVersion="32" android:targetSdkVersion="35" />
<queries><package android:name="local.gboard.probe" /></queries>
<application android:label="Temporary keyboard toolbar check" android:allowBackup="false" />
<instrumentation android:name=".ToolbarInstrumentation" android:targetPackage="com.localx.android.inputmethod.latin" android:functionalTest="true" />
</manifest>''', encoding='utf8')
run(JAVA/'javac.exe', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', ANDROID, '-d', classes, ROOT/'tests/android/ToolbarInstrumentation.java')
run(JAVA/'jar.exe', 'cf', out/'check.jar', '-C', classes, '.')
run(BT/'d8.bat', '--min-api', '32', '--lib', ANDROID, '--output', dex, out/'check.jar')
run(BT/'aapt.exe', 'package', '-f', '-M', manifest, '-I', ANDROID, '-F', out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk', 'a') as archive:
    archive.write(dex/'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
run(BT/'zipalign.exe', '-f', '4', out/'unsigned.apk', out/'aligned.apk')
run(BT/'apksigner.bat', 'sign', *SIGN_ARGS, '--out', out/'toolbarcheck.apk', out/'aligned.apk')
