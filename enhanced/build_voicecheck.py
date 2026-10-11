import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
from pathlib import Path
import subprocess,zipfile
ROOT=Path(__file__).resolve().parent.parent
def run(*args):subprocess.run([str(a) for a in args],check=True)
base=ROOT/'enhanced/voicecheck';out=base/'out';out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
sources=list(base.glob('*.java'))+list((ROOT/'enhanced/src').rglob('*.java'))
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,*sources)
run(JAVA/'jar.exe','cf',out/'client.jar','-C',classes,'.')
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,out/'client.jar')
run(BT/'aapt.exe','package','-f','-M',base/'AndroidManifest.xml','-S',base/'res','-I',ANDROID,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
run(BT/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'voicecheck.apk',out/'aligned.apk')
