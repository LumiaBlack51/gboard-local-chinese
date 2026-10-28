"""Formal updater/provider HTTPS integration on an explicitly selected test AVD.

Requires signed sync checker and formal keyboard/updater already installed.
Uses only a temporary local server, synthetic data and temporary credentials.
"""
from pathlib import Path
import argparse,base64,hashlib,json,ssl,subprocess,sys,tempfile,threading
from http.server import HTTPServer
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'sync'))
from server import Handler,database,exchange
from common import parse_learning
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Only an isolated test AVD is accepted')
def adb(*args):return subprocess.check_output(['adb','-s',a.serial,*args],text=True,timeout=60)
with tempfile.TemporaryDirectory(prefix='installed-learning-') as folder:
    root=Path(folder);key=root/'key.pem';cert=root/'cert.pem'
    subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    dbpath=root/'words.sqlite3';token=hashlib.sha256(b'isolated-android-fixture').hexdigest()
    with database(dbpath) as db:
        db.execute('INSERT INTO devices VALUES(?,?)',('android',hashlib.sha256(token.encode()).hexdigest()))
        exchange(db,{('凪','zhi'):1000000},{('值','zhi'):(12,1)},'synthetic-source')
    server=HTTPServer(('127.0.0.1',0),Handler);server.db_path=dbpath
    tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(cert,key)
    server.socket=tls.wrap_socket(server.socket,server_side=True)
    port=server.server_port;thread=threading.Thread(target=server.serve_forever);thread.start()
    try:
        adb('reverse',f'tcp:{port}',f'tcp:{port}')
        pin=hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).hexdigest()
        config=base64.b64encode(json.dumps({'endpoint':f'https://localhost:{port}/v1/sync','systemTrust':False,'certificateSha256':pin,'token':token}).encode()).decode()
        for i in range(2):
            args=['shell','am','instrument','-w','-e','mode','exchange','-e','expected_learned','1']
            if i==0:args+=['-e','config',config]
            output=adb(*args,'local.gboard.synccheck/.PhraseSyncInstrumentation')
            assert 'actual_learning_exchange=true' in output and 'passed=true' in output,output
            with database(dbpath) as db:
                assert parse_learning(exchange(db,{},{},'android'))=={('值','zhi'):(12,1)}
                assert db.execute('SELECT count(*) FROM learning WHERE device=?',('android',)).fetchone()[0]==0,'Downloaded totals fed back as local selections'
        result={'version':'1.3.7','isolated_avd':True,'formal_updater_https_exchange':True,'signature_protected_keyboard_provider_applied_learning':True,'repeat_exchange_without_count_feedback':True,'temporary_local_tls_and_credentials':True,'production_endpoint_used':False,'passed':True}
        (ROOT/'tests/results/installed-learning-exchange-v1.3.7.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,indent=2))
    finally:
        adb('reverse','--remove',f'tcp:{port}')
        server.shutdown();server.server_close();thread.join()
