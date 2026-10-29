"""Windows Task Scheduler one-shot adapter for Weasel/Rime, protocol v1."""
import argparse
from contextlib import contextmanager
import ctypes
from ctypes import wintypes
import hashlib
import json
import msvcrt
import os
from pathlib import Path
import ssl
import threading
import time
import urllib.parse
import urllib.request
from common import parse, encode, parse_learning, encode_learning, packet, MAX_BYTES
from rime_data import atomic, snapshot, rime_learning, local_learning, local_words

class PowerStatus(ctypes.Structure):
    _fields_ = [('ac',wintypes.BYTE),('flags',wintypes.BYTE),('percent',wintypes.BYTE),('reserved',wintypes.BYTE),('remaining',wintypes.DWORD),('full',wintypes.DWORD)]

def powered():
    status = PowerStatus()
    return bool(ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status))) and status.ac == 1

@contextmanager
def exclusive(path):
    with path.open('a+b') as held:
        if held.seek(0,2) == 0: held.write(b'0'); held.flush()
        held.seek(0)
        try: msvcrt.locking(held.fileno(),msvcrt.LK_NBLCK,1)
        except OSError:
            yield False
            return
        try: yield True
        finally:
            held.seek(0); msvcrt.locking(held.fileno(),msvcrt.LK_UNLCK,1)

def exchange(config, raw, config_root):
    endpoint = urllib.parse.urlsplit(config['endpoint'])
    if endpoint.scheme != 'https' or not endpoint.hostname or endpoint.username is not None or endpoint.password is not None or endpoint.path != '/v1/sync' or endpoint.query or endpoint.fragment:
        raise ValueError('HTTPS /v1/sync endpoint required')
    if config.get('systemTrust',False):
        tls = ssl.create_default_context()
    else:
        cert = config_root/'server.crt'
        if hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text('ascii'))).hexdigest() != config['certificateSha256']:
            raise ValueError('certificate mismatch')
        tls = ssl.create_default_context(cafile=str(cert))
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*_): return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=tls),NoRedirect)
    request = urllib.request.Request(config['endpoint'],data=raw,headers={'Authorization':'Bearer '+config['token'],'X-Phrase-Protocol':'1','Content-Type':'text/tab-separated-values; charset=utf-8'},method='POST')
    with opener.open(request,timeout=15) as response:
        body = response.read(MAX_BYTES+1)
        supported = response.headers.get('X-Phrase-Learning') == '1'
    # Validate everything before replacing either last good cloud snapshot.
    return parse(body), parse_learning(body), supported

def sync(config_path, root):
    if not powered(): return {'success':False,'skipped':'battery_or_unknown_power'}
    config_path.parent.mkdir(parents=True,exist_ok=True)
    with exclusive(config_path.parent/'sync.lock') as acquired:
        if not acquired: return {'success':False,'skipped':'already_running'}
        stopped = threading.Event()
        def guard():
            while not stopped.wait(0.1):
                if not powered(): os._exit(0)
        watcher = threading.Thread(target=guard,daemon=True); watcher.start()
        # Bound the complete invocation, including DNS/TLS and local I/O.
        deadline = threading.Timer(55,lambda:os._exit(1)); deadline.daemon=True; deadline.start()
        try:
            config = json.loads(config_path.read_text('utf-8-sig'))
            native = rime_learning(root)
            own = local_learning(root,native)
            words = local_words(root,native)
            for source in [config_path.parent/'microsoft-phrases.tsv',root/'cloud-phrases.tsv']:
                if source.exists():
                    for key,weight in parse(source.read_bytes()).items(): words[key] = max(words.get(key,0),weight)
            for key in own: words[key] = max(words.get(key,0),100)
            # No global keys, foreground changes, deploys or live DB access.
            if not powered(): return {'success':False,'skipped':'battery'}
            merged, learned, supported = exchange(config,packet(words,own),config_path.parent)
            if not powered(): return {'success':False,'skipped':'battery'}
            snapshot(root/'local-native-learning.tsv',encode_learning(native,comments=False))
            snapshot(root/'cloud-phrases.tsv',encode(merged))
            if supported: snapshot(root/'cloud-learning.tsv',encode_learning(learned,comments=False))
            status = {'success':True,'words':len(merged),'uploaded_words':len(words),'local_learned_words':len(own),'learning_supported':supported,'learned_words':len(learned),'at':time.time()}
            atomic(config_path.parent/'status.json',json.dumps(status,indent=2).encode())
            return status
        finally:
            stopped.set(); watcher.join(timeout=1); deadline.cancel()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config',type=Path,default=Path.home()/'.config/gboard-phrase-sync/config.json')
    parser.add_argument('--rime',type=Path,default=Path(os.environ['APPDATA'])/'Rime')
    args = parser.parse_args()
    try:
        print(json.dumps(sync(args.config,args.rime)))
    except Exception as error:
        # Do not log URLs, authorization headers, words or response bodies.
        args.config.parent.mkdir(parents=True,exist_ok=True)
        atomic(args.config.parent/'status.json',json.dumps({'success':False,'error_type':type(error).__name__,'at':time.time()},indent=2).encode())
        raise SystemExit(1)
