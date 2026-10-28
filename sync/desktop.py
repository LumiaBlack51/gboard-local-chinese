#!/usr/bin/env python3
"""Fcitx5/Rime one-shot adapter. Nothing stays running between timer invocations."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import ssl
import subprocess
import tempfile
import threading
import urllib.request
import urllib.parse
from common import parse, encode, parse_learning, encode_learning, packet, valid_word, MAX_BYTES

def powered(root=Path('/sys/class/power_supply')):
    for supply in root.iterdir():
        try:
            if (supply/'type').read_text().strip() != 'Battery' and (supply/'online').read_text().strip() == '1':
                return True
        except OSError:
            continue
    return False

def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix='.'+path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def rime_learning(root):
    result = {}
    # Preserve the engine's database lock. Read only Rime-created text snapshots.
    installation=root/'installation.yaml'
    match=re.search(r'''^installation_id:\s*["']?([a-zA-Z0-9][a-zA-Z0-9_.-]{0,127})["']?\s*$''',installation.read_text('utf-8'),re.M) if installation.exists() else None
    if not match:
        return result
    for path in (root/'sync'/match[1]).glob('rime_ice.userdb.txt'):
        if path.stat().st_size > MAX_BYTES:
            raise ValueError('Rime snapshot too large')
        for line in path.read_text('utf-8').splitlines():
            fields = line.split('\t')
            if len(fields) != 3 or line.startswith('#'): continue
            code, word, stats = fields
            count = re.search(r'(?:^| )c=([0-9]+)(?: |$)', stats)
            code = re.sub(r"[\s'’]", '', code.lower()).replace('ü', 'v').replace('u:', 'v')
            if count and int(count[1]) > 0 and valid_word(word) and re.fullmatch('[a-z]{1,128}', code):
                key = (word, code)
                result[key] = (max(result.get(key, (0, 0))[0], min(1000000, int(count[1]))), 0)
    return result

def local_learning(root, native=None):
    result = dict(rime_learning(root) if native is None else native)
    for path in (root/'selection-learning').glob('*.tsv'):
        if not re.fullmatch('[a-z]{1,128}', path.stem):
            continue
        for key, (count, stamp) in parse_learning(path.read_bytes(), comments=False).items():
            if key[1] != path.stem:
                raise ValueError('learning input does not match file')
            previous = result.get(key, (0, 0))
            result[key] = (max(previous[0], count), max(previous[1], stamp))
    # A downloaded total is never re-exported as this device's own selections.
    return result

def local_words(root, native=None):
    result = {key: min(1000000, 100+value[0]) for key, value in (rime_learning(root) if native is None else native).items()}
    custom = root/'custom_phrase.txt'
    if custom.exists():
        for line in custom.read_text('utf-8-sig').splitlines():
            if line.startswith('#'): continue
            fields = line.split('\t')
            if len(fields) not in (2,3): continue
            word, code = fields[:2]
            if valid_word(word) and re.fullmatch('[a-z]{1,128}', code):
                result[(word, code)] = max(result.get((word, code), 0), 100)
    return result

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--config',type=Path,default=Path.home()/'.config/gboard-phrase-sync/config.json')
    p.add_argument('--rime',type=Path,default=Path.home()/'.local/share/fcitx5/rime')
    args=p.parse_args()
    if not powered(): return
    os.umask(0o077)
    config=json.loads(args.config.read_text())
    endpoint=urllib.parse.urlsplit(config['endpoint'])
    if endpoint.scheme!='https' or endpoint.username is not None or endpoint.password is not None or endpoint.path!='/v1/sync' or endpoint.query or endpoint.fragment:
        raise ValueError('HTTPS /v1/sync endpoint required')
    lock=args.config.parent/'sync.lock'
    with lock.open('w') as held:
        try: fcntl.flock(held,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError: return
        # Save uses the engine's supported snapshot operation, only while Chinese
        # input is inactive. No global keys, focus changes, UI or process restart.
        state=subprocess.run(['fcitx5-remote'],capture_output=True,text=True,timeout=3)
        if state.returncode == 0 and state.stdout.strip() in ('0','1') and powered():
            subprocess.run(['busctl','--user','call','org.fcitx.Fcitx5','/controller','org.fcitx.Fcitx.Controller1','Save'],capture_output=True,timeout=5)
        native=rime_learning(args.rime)
        data=encode_learning(native,comments=False)
        atomic(args.rime/'local-native-learning.tsv',('# snapshot '+hashlib.sha256(data).hexdigest()+'\n').encode()+data)
        words=local_words(args.rime,native)
        cloud=args.rime/'cloud-phrases.tsv'
        if cloud.exists():
            for key,n in parse(cloud.read_bytes()).items(): words[key]=max(words.get(key,0),n)
        raw=packet(words,local_learning(args.rime,native))
        if config.get('systemTrust',False):
            tls=ssl.create_default_context()
        else:
            cert=args.config.parent/'server.crt'
            if hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).hexdigest()!=config['certificateSha256']:
                raise ValueError('certificate mismatch')
            tls=ssl.create_default_context(cafile=str(cert))
        request=urllib.request.Request(config['endpoint'],data=raw,headers={'Authorization':'Bearer '+config['token'],'X-Phrase-Protocol':'1','Content-Type':'text/tab-separated-values; charset=utf-8'},method='POST')
        stopped=threading.Event()
        def guard_power():
            while not stopped.wait(0.1):
                if not powered():
                    # The dedicated one-shot process has no other tasks to preserve.
                    os._exit(0)
        watcher=threading.Thread(target=guard_power,daemon=True);watcher.start()
        try:
            if not powered(): return
            # Disable redirects so the bearer token never follows another endpoint.
            class NoRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self,*_): return None
            opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=tls),NoRedirect)
            with opener.open(request,timeout=15) as incoming:
                body=incoming.read(MAX_BYTES+1)
                learning_supported=incoming.headers.get('X-Phrase-Learning')=='1'
            merged=parse(body)
            learned=parse_learning(body)
            if not powered(): return
            data=encode(merged)
            fingerprint=hashlib.sha256(data).hexdigest()
            atomic(cloud,('# snapshot '+fingerprint+'\n').encode()+data)
            if learning_supported:
                data=encode_learning(learned,comments=False)
                atomic(args.rime/'cloud-learning.tsv',('# snapshot '+hashlib.sha256(data).hexdigest()+'\n').encode()+data)
            atomic(args.config.parent/'status.json',json.dumps({'success':True,'words':len(merged),'learning_supported':learning_supported,'learned_words':len(learned),'at':__import__('time').time()},indent=2).encode())
            print('Phrase sync complete:',len(merged),'words;',len(learned),'learned entries')
        finally:
            stopped.set();watcher.join(timeout=1)

if __name__=='__main__':
    main()
