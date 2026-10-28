"""Protocol invariants for actual selection counters; no device or private data."""
import hashlib
import http.client
import sqlite3
import sys
import tempfile
import threading
import unittest
from http.server import HTTPServer
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'sync'))
from common import parse, parse_learning, packet, encode_learning
from server import Handler, database, exchange
from desktop import local_learning
from backup import backup


class SharedLearningTest(unittest.TestCase):
    def test_per_device_max_and_cross_device_sum_without_retry_inflation(self):
        with tempfile.TemporaryDirectory() as folder, database(Path(folder)/'db') as db:
            word = ('值', 'zhi')
            phone = {word: (3, 1000)}
            pc = {word: (2, 2000)}
            exchange(db, {}, phone, 'phone')
            for _ in range(4):
                self.assertEqual(parse_learning(exchange(db, {}, pc, 'pc')), {word: (5, 2000)})
                self.assertEqual(parse_learning(exchange(db, {}, phone, 'phone')), {word: (5, 2000)})
            # A stale upload is not a deletion or a new selection.
            self.assertEqual(parse_learning(exchange(db, {}, {word: (1, 500)}, 'phone')), {word: (5, 2000)})
            self.assertEqual(parse_learning(exchange(db, {}, {word: (4, 3000)}, 'phone')), {word: (6, 3000)})

    def test_import_frequency_is_not_a_selection_counter(self):
        with tempfile.TemporaryDirectory() as folder, database(Path(folder)/'db') as db:
            raw=exchange(db, {('凪', 'zhi'): 1000000}, {('只', 'zhi'): (1, 1000)}, 'phone')
            self.assertEqual(parse(raw), {('凪', 'zhi'): 1000000})
            self.assertEqual(parse_learning(raw), {('只', 'zhi'): (1, 1000)})

    def test_legacy_reader_ignores_learning_extension(self):
        words={('新词', 'xinci'): 100};learning={('只', 'zhi'): (7, 0)}
        raw=packet(words, learning)
        self.assertEqual(parse(raw), words)
        self.assertEqual(parse_learning(raw), learning)
        self.assertEqual(parse_learning(encode_learning(learning, comments=False), comments=False), learning)

    def test_bad_learning_record_rejects_entire_transaction(self):
        for row in ['# learned\t只\tzhi\t0\t1\n', '# learned\t只\tzhi\t2\t-1\n',
                    '# learned\tEnglish\tzhi\t2\t1\n', '# learned\t只\tz h i\t2\t1\n']:
            with self.assertRaises(ValueError):
                parse_learning(row.encode())
        with tempfile.TemporaryDirectory() as folder, database(Path(folder)/'db') as db:
            exchange(db, {('保留', 'baoliu'): 100}, {('只', 'zhi'): (1, 0)}, 'pc')
            with patch('server.MAX_WORDS', 1), self.assertRaises(ValueError):
                exchange(db, {('改动', 'gaidong'): 100}, {('值', 'zhi'): (1, 0)}, 'phone')
            self.assertEqual(parse(exchange(db, {}, {}, 'pc')), {('保留', 'baoliu'): 100})
            self.assertEqual(parse_learning(exchange(db, {}, {}, 'pc')), {('只', 'zhi'): (1, 0)})

    def test_only_local_sources_are_uploaded(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);own=root/'selection-learning/zhi.tsv';own.parent.mkdir()
            (root/'installation.yaml').write_text('installation_id: device\n')
            own.write_bytes(encode_learning({('只', 'zhi'): (4, 2000)}, comments=False))
            (root/'cloud-learning.tsv').write_bytes(encode_learning({('只', 'zhi'): (99999, 3000), ('凪', 'zhi'): (12, 4000)}, comments=False))
            snapshot=root/'sync/device/rime_ice.userdb.txt';snapshot.parent.mkdir(parents=True)
            snapshot.write_text('zhi \t只\tc=6 d=1 t=1\nzhi \t凪\tc=0 d=1\nzhi \t知\tc=-1 d=1\n')
            foreign=root/'sync/other-device/rime_ice.userdb.txt';foreign.parent.mkdir();foreign.write_text('zhi \t只\tc=99999 d=1 t=1\n')
            self.assertEqual(local_learning(root), {('只', 'zhi'): (6, 2000)})

    def test_clock_skew_is_bounded_and_backup_preserves_device_counters(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'db';saved=Path(folder)/'backup'
            with database(path) as db:
                raw=exchange(db, {}, {('只', 'zhi'): (2, 9000000000000000)}, 'phone', now=100)
                self.assertEqual(parse_learning(raw), {('只', 'zhi'): (2, 400000)})
            backup(path, saved)
            with database(saved) as db:
                self.assertEqual(parse_learning(exchange(db, {}, {('只', 'zhi'): (3, 0)}, 'pc')), {('只', 'zhi'): (5, 400000)})

    def test_authenticated_source_cannot_replace_another_device_counter(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'db'
            with database(path) as db:
                for name in ['phone', 'pc']:
                    db.execute('INSERT INTO devices VALUES(?,?)', (name, hashlib.sha256(name.encode()).hexdigest()))
            server=HTTPServer(('127.0.0.1', 0), Handler);server.db_path=path
            thread=threading.Thread(target=server.serve_forever);thread.start()
            try:
                def call(device, count):
                    c=http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
                    c.request('POST','/v1/sync',packet({}, {('只', 'zhi'): (count, 0)}),
                              {'Authorization':'Bearer '+device,'X-Phrase-Protocol':'1'})
                    response=c.getresponse();data=response.read()
                    self.assertEqual(response.getheader('X-Phrase-Learning'),'1');self.assertEqual(response.status,200)
                    c.close();return parse_learning(data)
                self.assertEqual(call('phone',3),{('只','zhi'):(3,0)})
                self.assertEqual(call('pc',2),{('只','zhi'):(5,0)})
                self.assertEqual(call('phone',3),{('只','zhi'):(5,0)})
            finally:server.shutdown();server.server_close();thread.join()


if __name__=='__main__':unittest.main()
