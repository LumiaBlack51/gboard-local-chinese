import sys,subprocess,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,JAVA,BT,ANDROID,SIGN_ARGS
from release_version import VERSION,KEYBOARD_CODE,VOICE_CODE,UPDATER_CODE
def run(*args):subprocess.run([str(a) for a in args],check=True)
base=ROOT/'enhanced/updater';out=base/'out';classes=out/'classes';dex=out/'dex';classes.mkdir(parents=True,exist_ok=True);dex.mkdir(exist_ok=True)
identity=out/'ReleaseIdentity.java';identity.write_text('package local.gboard.updater; final class ReleaseIdentity {static final String VERSION="'+VERSION+'";static final long KEYBOARD='+str(KEYBOARD_CODE)+',VOICE='+str(VOICE_CODE)+',UPDATER='+str(UPDATER_CODE)+';}','utf8')
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,*base.glob('*.java'),identity)
run(JAVA/'jar.exe','cf',out/'helper.jar','-C',classes,'.');run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'helper.jar')
run(BT/'aapt.exe','package','-f','-M',base/'AndroidManifest.xml','-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
run(BT/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk');run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'updater.apk',out/'aligned.apk')
