"""Host tools and user-owned signing material; private keys are never stored here."""
from pathlib import Path
import os,shutil,subprocess,hashlib,functools
ROOT=Path(__file__).resolve().parent
SDK=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'AppData/Local/Android/Sdk')
BT=SDK/'build-tools'/os.environ.get('ANDROID_BUILD_TOOLS_VERSION','36.1.0')
ANDROID=SDK/'platforms/android-36/android.jar'
JAVA=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'javac').resolve().parent
APKTOOL=Path(os.environ.get('APKTOOL_JAR',ROOT/'tools/apktool_2.12.1.jar'))
KEYSTORE=Path(os.environ.get('GBOARD_KEYSTORE',ROOT/'private/signing.p12'))
KEY_ALIAS=os.environ.get('GBOARD_KEY_ALIAS','probe')
SIGN_ARGS=['--ks',str(KEYSTORE),'--ks-key-alias',KEY_ALIAS,'--ks-pass','env:GBOARD_KEYSTORE_PASS','--key-pass','env:GBOARD_KEYSTORE_PASS']
@functools.lru_cache(maxsize=1)
def certificate_digest():
    if not os.environ.get('GBOARD_KEYSTORE_PASS'):raise RuntimeError('Set GBOARD_KEYSTORE_PASS and use your own keystore')
    der=subprocess.check_output([str(JAVA/'keytool.exe'),'-exportcert','-keystore',str(KEYSTORE),'-alias',KEY_ALIAS,'-storepass:env','GBOARD_KEYSTORE_PASS'])
    return hashlib.sha256(der).digest()
