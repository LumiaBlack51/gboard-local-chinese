"""Build an isolated Android checker and prove Android <-> native Rime learning.

The temporary APK has its own UID/data, no Activity and no network permission.
It uses the ordinary SDK debug key, never the production keyboard signing key.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import http.client
from http.server import HTTPServer
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
sys.path.insert(0,str(ROOT/'sync'))
from release_version import VERSION
from common import parse_learning
from server import Handler,database

p=argparse.ArgumentParser()
p.add_argument('--serial',required=True)
p.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_SDK_ROOT',Path.home()/'Android/Sdk')))
p.add_argument('--debug-keystore',type=Path,default=Path.home()/'.android/debug.keystore')
a=p.parse_args()
sdk=a.sdk;bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar'
def tool(name):
    for candidate in [name,name+'.exe',name+'.bat']:
        if (bt/candidate).exists():return str(bt/candidate)
    raise FileNotFoundError(name)
adb=str(sdk/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
def run(*args,**kwargs):
    return subprocess.run(list(map(str,args)),check=True,**kwargs)
def instrument(mode,**options):
    cmd=[adb,'-s',a.serial,'shell','am','instrument','-w','-e','mode',mode]
    for key,value in options.items():cmd+=['-e',key,str(value)]
    cmd+=['local.gboard.learningcheck/.SharedLearningInstrumentation']
    output=subprocess.check_output(cmd,text=True,timeout=60)
    assert 'passed=true' in output,output
    result={}
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_RESULT: '):
            key,value=line.removeprefix('INSTRUMENTATION_RESULT: ').split('=',1);result[key]=value
    return result

with tempfile.TemporaryDirectory(prefix='shared-learning-check-') as folder:
    work=Path(folder);classes=work/'classes';classes.mkdir();dex=work/'dex';dex.mkdir()
    source=ROOT/'enhanced/src/local/gboard/enhanced'
    names=['DictionaryFile','LearningFile','LearningStore','SelectionLearning','SharedRanking']
    run('javac','-encoding','UTF-8','-source','8','-target','8','-cp',android,'-d',classes,
        *[source/(name+'.java') for name in names],ROOT/'tests/android/SharedLearningInstrumentation.java')
    run('jar','cf',work/'classes.jar','-C',classes,'.')
    run(tool('d8'),'--min-api','32','--lib',android,'--output',dex,work/'classes.jar')
    manifest=work/'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.gboard.learningcheck" android:versionCode="1"><uses-sdk android:minSdkVersion="32" android:targetSdkVersion="35"/><application android:debuggable="true" android:allowBackup="false"/><instrumentation android:name=".SharedLearningInstrumentation" android:targetPackage="local.gboard.learningcheck"/></manifest>')
    run(tool('aapt'),'package','-f','-M',manifest,'-I',android,'-F',work/'unsigned.apk')
    with zipfile.ZipFile(work/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex')
    run(tool('zipalign'),'-f','4',work/'unsigned.apk',work/'aligned.apk')
    run(tool('apksigner'),'sign','--ks',a.debug_keystore,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',work/'check.apk',work/'aligned.apk')
    installed=False;server=None;thread=None
    try:
        run(adb,'-s',a.serial,'install','--no-streaming','-r','-t',work/'check.apk');installed=True
        self_checks=instrument('self')
        path=work/'server.sqlite3'
        with database(path) as db:
            for name in ['android','desktop']:
                db.execute('INSERT INTO devices VALUES(?,?)',(name,hashlib.sha256(name.encode()).hexdigest()))
        server=HTTPServer(('127.0.0.1',0),Handler);server.db_path=path
        thread=threading.Thread(target=server.serve_forever);thread.start()
        def exchange(device,raw):
            c=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=5)
            c.request('POST','/v1/sync',raw,{'Authorization':'Bearer '+device,'X-Phrase-Protocol':'1'})
            response=c.getresponse();data=response.read();assert response.status==200 and response.getheader('X-Phrase-Learning')=='1';c.close();return data
        sent=base64.b64decode(instrument('send')['outgoing_base64'])
        reply=exchange('android',sent)
        native=json.loads(subprocess.check_output([sys.executable,'-B',str(ROOT/'tests/shared_rime_client.py'),base64.b64encode(reply).decode()],text=True))
        reply=exchange('desktop',base64.b64decode(native['outgoing_base64']))
        expected=parse_learning(reply)
        assert {key:value[0] for key,value in expected.items()}=={('值','zhi'):3,('知','zhi'):6}
        for _ in range(3):
            received=instrument('receive',learning=base64.b64encode(reply).decode(),expected='知')
            reply=exchange('android',base64.b64decode(received['outgoing_base64']))
            assert parse_learning(reply)==expected,'Retries/aggregate feedback inflated counters'
        sources=['sync/compact_tsv.lua','sync/common.py','sync/server.py','sync/desktop.py','sync/cloud_phrase.lua','sync/cloud_native_filter.lua','sync/selection_learning.lua']
        sources += ['enhanced/src/local/gboard/enhanced/'+name+'.java' for name in names]
        report={'version':VERSION,'android_device':a.serial,'isolated_test_uid':True,'personal_database_opened':False,
                'android_to_native_rime':{k:v for k,v in native.items() if k!='outgoing_base64'},
                'native_rime_to_android':{'native_word_promoted':'知','native_id_preserved':True,'survives_reload':True},
                'actual_android_atomic_file_checks':{k:v for k,v in self_checks.items() if k!='outgoing_base64'},
                'repeated_bidirectional_exchange_without_feedback':True,'passed':True,
                'sources':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in sources}}
        (ROOT/f'tests/results/shared-learning-integration-v{VERSION}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps({k:v for k,v in report.items() if k not in ['sources','actual_android_atomic_file_checks']},ensure_ascii=False,indent=2))
    finally:
        if server:server.shutdown();server.server_close()
        if thread:thread.join()
        if installed:subprocess.run([adb,'-s',a.serial,'uninstall','local.gboard.learningcheck'],check=True)
