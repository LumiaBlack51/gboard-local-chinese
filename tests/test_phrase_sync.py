import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
import http.client
from http.server import HTTPServer
import threading
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
from common import parse,encode,MAX_WORDS
from server import database,exchange,Handler
from desktop import local_words,powered,atomic
from backup import backup

class PhraseSyncTest(unittest.TestCase):
    def test_multiple_devices_idempotent_union(self):
        with tempfile.TemporaryDirectory() as folder, database(Path(folder)/'db') as db:
            phone={('手机词库','shoujiciku'):102}
            pc={('电脑词库','diannaociku'):105}
            self.assertEqual(parse(exchange(db,phone)),phone)
            both=parse(exchange(db,pc))
            self.assertEqual(both,phone|pc)
            for _ in range(3): self.assertEqual(parse(exchange(db,phone)),both)
            self.assertEqual(parse(exchange(db,{('手机词库','shoujiciku'):200}))[('手机词库','shoujiciku')],200)
    def test_http_authentication_revocation_and_protocol(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'db'
            with database(path) as db: db.execute('INSERT INTO devices VALUES(?,?)',('test',hashlib.sha256(b'synthetic-test-token').hexdigest()))
            server=HTTPServer(('127.0.0.1',0),Handler);server.db_path=path
            thread=threading.Thread(target=server.serve_forever);thread.start()
            try:
                def request(token,body,protocol='1'):
                    c=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=3)
                    c.request('POST','/v1/sync',body,{'Authorization':'Bearer '+token,'X-Phrase-Protocol':protocol})
                    r=c.getresponse();data=r.read();status=r.status;c.close();return status,data
                raw=encode({('合成词条','hechengcitiao'):100})
                self.assertEqual(request('wrong',raw)[0],401)
                self.assertEqual(request('synthetic-test-token',raw,'2')[0],400)
                self.assertEqual(parse(request('synthetic-test-token',raw)[1]),parse(raw))
                self.assertEqual(request('synthetic-test-token',b'malformed')[0],400)
                self.assertEqual(parse(request('synthetic-test-token',encode({}))[1]),parse(raw))
                with database(path) as db: db.execute('DELETE FROM devices')
                self.assertEqual(request('synthetic-test-token',raw)[0],401)
            finally: server.shutdown();server.server_close();thread.join()
    def test_invalid_records_reject_entire_batch(self):
        for raw in [b'bad\tcode\t10\n','中文\tpin yin\t1\n'.encode(),'中文\tzhongwen\t0\n'.encode(),b'\xff',b'x'*16777217]:
            with self.subTest(raw=raw[:32]),self.assertRaises((ValueError,UnicodeError)): parse(raw)
    def test_union_limit_preserves_database(self):
        with tempfile.TemporaryDirectory() as folder,database(Path(folder)/'db') as db:
            exchange(db,{('词库','ciku'):100})
            with patch('server.MAX_WORDS',1),self.assertRaises(ValueError):exchange(db,{('新词','xinci'):100})
            self.assertEqual(parse(exchange(db,{})),{('词库','ciku'):100})
    def test_snapshot_excludes_unlearned_deleted_non_chinese(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);snapshot=root/'sync/device/rime_ice.userdb.txt';snapshot.parent.mkdir(parents=True)
            (root/'installation.yaml').write_text('installation_id: device\n')
            snapshot.write_text('# comment\nce shi \t测试\tc=2 d=1 t=1\nshan chu \t删除\tc=-1 d=1\nwei xue \t未学\tc=0 d=1\nen \tEnglish\tc=2\n')
            self.assertEqual(local_words(root),{('测试','ceshi'):102})
    def test_power_policy(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);ac=root/'AC';ac.mkdir();(ac/'type').write_text('Mains');(ac/'online').write_text('0')
            battery=root/'BAT';battery.mkdir();(battery/'type').write_text('Battery');(battery/'online').write_text('1')
            self.assertFalse(powered(root));(ac/'online').write_text('1');self.assertTrue(powered(root))
    def test_migration_backup_preserves_words_and_device_auth(self):
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'source.sqlite3';destination=Path(folder)/'backup.sqlite3'
            with database(source) as db:
                db.execute('INSERT INTO devices VALUES(?,?)',('synthetic',hashlib.sha256(b'synthetic').hexdigest()))
                exchange(db,{('迁移测试','qianyiceshi'):100})
            backup(source,destination)
            with database(destination) as db:
                self.assertEqual(parse(exchange(db,{})),{('迁移测试','qianyiceshi'):100})
                self.assertEqual(db.execute('SELECT count(*) FROM devices').fetchone()[0],1)
            self.assertEqual(destination.stat().st_mode&0o777,0o600)
            with self.assertRaises(FileExistsError):backup(source,destination)
    def test_atomic_roundtrip(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'words';raw=encode({('测试','ceshi'):100});atomic(path,raw);self.assertEqual(parse(path.read_bytes()),{('测试','ceshi'):100});self.assertEqual(path.stat().st_mode&0o777,0o600)

if __name__=='__main__':unittest.main()
