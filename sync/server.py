#!/usr/bin/env python3
"""Socket-activated HTTPS phrase union, with independently revocable device tokens."""
import argparse
import hashlib
from http.server import HTTPServer, BaseHTTPRequestHandler
import os
from pathlib import Path
import secrets
import socket
import sqlite3
import ssl
import time
from common import parse, encode, parse_learning, packet, MAX_BYTES, MAX_WORDS

def database(path):
    db = sqlite3.connect(path, timeout=10)
    db.execute('CREATE TABLE IF NOT EXISTS devices (name TEXT PRIMARY KEY, token_hash TEXT UNIQUE NOT NULL)')
    db.execute('CREATE TABLE IF NOT EXISTS words (word TEXT, code TEXT, weight INTEGER, PRIMARY KEY(word,code))')
    db.execute('CREATE TABLE IF NOT EXISTS learning (device TEXT, word TEXT, code TEXT, count INTEGER NOT NULL, last INTEGER NOT NULL, PRIMARY KEY(device,word,code))')
    return db

def exchange(db, words, learned=None, device=None, now=None):
    # Check the full union before writing; retries cannot inflate weights.
    with db:
        total = dict(((w, c), n) for w, c, n in db.execute('SELECT word,code,weight FROM words'))
        for key, weight in words.items():
            total[key] = max(total.get(key, 0), weight)
        if len(total) > MAX_WORDS:
            raise ValueError('union exceeds limit')
        if learned:
            if not device:
                raise ValueError('authenticated device required for learning')
            keys = set(db.execute('SELECT DISTINCT word,code FROM learning'))
            if len(keys | learned.keys()) > MAX_WORDS:
                raise ValueError('learning union exceeds limit')
            ceiling = int((time.time() if now is None else now) * 1000) + 300000
            db.executemany('INSERT INTO learning VALUES(?,?,?,?,?) ON CONFLICT(device,word,code) DO UPDATE SET count=max(count,excluded.count),last=max(last,excluded.last)',
                           [(device, w, c, n, min(stamp, ceiling)) for (w, c), (n, stamp) in learned.items()])
        db.executemany('INSERT INTO words VALUES(?,?,?) ON CONFLICT(word,code) DO UPDATE SET weight=max(weight,excluded.weight)', [(w, c, n) for (w, c), n in words.items()])
        if learned is None:
            return encode(total)
        # Max per authenticated device, sum across devices: retries and stale
        # snapshots cannot inflate counters or feed the aggregate back as local use.
        combined = {(w, c): (n, stamp) for w, c, n, stamp in db.execute(
            'SELECT word,code,min(1000000,sum(count)),max(last) FROM learning GROUP BY word,code')}
        return packet(total, combined)

class Server(HTTPServer):
    def get_request(self):
        client,address=self.socket.accept()
        client.settimeout(15)
        try: return self.tls.wrap_socket(client,server_side=True),address
        except Exception:
            client.close(); raise

class Handler(BaseHTTPRequestHandler):
    server_version = 'PhraseSync/1'
    def setup(self):
        self.request.settimeout(20)
        super().setup()
    def log_message(self, *_):
        pass  # No addresses, words, authorization headers or request contents in logs.
    def reply(self, status, data=b''):
        self.send_response(status)
        self.send_header('Content-Type', 'text/tab-separated-values; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Phrase-Learning', '1')
        self.end_headers()
        self.wfile.write(data)
    def do_POST(self):
        if self.path != '/v1/sync':
            self.reply(404); return
        if self.headers.get('X-Phrase-Protocol') != '1':
            self.reply(400); return
        auth = self.headers.get('Authorization', '')
        if not auth.startswith('Bearer ') or len(auth) > 256:
            self.reply(401); return
        with database(self.server.db_path) as db:
            token_hash = hashlib.sha256(auth[7:].encode()).hexdigest()
            device = db.execute('SELECT name FROM devices WHERE token_hash=?', (token_hash,)).fetchone()
            if not device:
                self.reply(401); return
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 <= length <= MAX_BYTES or self.headers.get('Transfer-Encoding'):
                    self.reply(413); return
                raw = self.rfile.read(length)
                if len(raw) != length:
                    self.reply(400); return
                body = exchange(db, parse(raw), parse_learning(raw), device[0])
            except (ValueError, UnicodeError):
                self.reply(400); return
        self.reply(200, body)

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--database', type=Path, required=True)
    p.add_argument('--add-device')
    p.add_argument('--revoke-device')
    p.add_argument('--cert', type=Path)
    p.add_argument('--key', type=Path)
    p.add_argument('--port', type=int, default=8444)
    args = p.parse_args()
    os.umask(0o077)
    with database(args.database) as db:
        if args.add_device:
            token = secrets.token_urlsafe(32)
            db.execute('INSERT OR REPLACE INTO devices VALUES(?,?)', (args.add_device, hashlib.sha256(token.encode()).hexdigest()))
            print(token); return
        if args.revoke_device:
            db.execute('DELETE FROM devices WHERE name=?', (args.revoke_device,)); return
    server = Server(('0.0.0.0', args.port), Handler, bind_and_activate=False)
    if os.environ.get('LISTEN_FDS') == '1' and int(os.environ.get('LISTEN_PID', '0')) == os.getpid():
        server.socket.close()
        server.socket = socket.socket(fileno=3)
        server.server_address = server.socket.getsockname()
        server.server_name = 'phrase-sync'
        server.server_port = server.server_address[1]
    else:
        server.server_bind(); server.server_activate()
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.key)
    server.tls = tls
    server.db_path = args.database
    server.timeout = 10
    try:
        while True:
            # Socket activation leaves only the system socket alive between sessions.
            import select
            if not select.select([server.socket], [], [], 10)[0]:
                break
            server.handle_request()
    finally:
        server.server_close()

if __name__ == '__main__':
    main()
