"""Synthetic privacy/format/learning regression checks; no real user data."""
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
from microsoft_pinyin import extract, PINYIN, MAGIC
from common import encode, encode_learning, parse
from rime_data import local_learning, rime_learning, snapshot

def udl(word, syllables):
    raw=bytearray(0x2400+60)
    raw[:12]=MAGIC;struct.pack_into('<I',raw,12,1)
    raw[0x2400+10]=len(word)
    raw[0x2400+12:0x2400+12+len(word)*2]=word.encode('utf-16-le')
    for n,syllable in enumerate(syllables):
        struct.pack_into('<H',raw,0x2400+12+len(word)*2+n*2,PINYIN.index(syllable))
    return raw

class ImportChecks(unittest.TestCase):
    def test_exact_reading_without_invented_learning_counts(self):
        words,report=extract(udl('旅行',['lv','xing']))
        self.assertEqual(words,{('旅行','lvxing'):100})
        self.assertEqual(report['unique_words'],1)
    def test_non_chinese_and_invalid_syllable_are_excluded(self):
        self.assertFalse(extract(udl('中A',['zhong','a']))[0])
        raw=udl('中文',['zhong','wen'])
        struct.pack_into('<H',raw,0x2400+16,65535)
        self.assertFalse(extract(raw)[0])
    def test_bad_format_and_truncated_records(self):
        with self.assertRaises(ValueError):extract(b'other-format')
        words,report=extract(udl('中文',['zhong','wen'])[:-1])
        self.assertEqual(words,{})
        self.assertEqual(report['truncated'],1)

class AggregateChecks(unittest.TestCase):
    def test_cloud_totals_and_other_installations_never_become_local_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'installation.yaml').write_text('installation_id: mine\n')
            for name,count in [('mine',2),('other',99)]:
                p=root/'sync'/name;p.mkdir(parents=True)
                (p/'rime_ice.userdb.txt').write_text('zhong wen \t中文\tc='+str(count)+'\n',encoding='utf-8')
            snapshot(root/'cloud-learning.tsv',encode_learning({('中文','zhongwen'):(1000,123)},comments=False))
            local=root/'selection-learning';local.mkdir()
            (local/'zhongwen.tsv.new').write_bytes(encode_learning({('中文','zhongwen'):(3,456)},comments=False))
            self.assertEqual(rime_learning(root),{('中文','zhongwen'):(2,0)})
            self.assertEqual(local_learning(root),{('中文','zhongwen'):(3,456)})
    @unittest.skipUnless(sys.platform=='win32','Windows power and lock APIs')
    def test_battery_skips_network_and_duplicate_invocations_skip(self):
        import windows
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            with patch.object(windows,'powered',return_value=False),patch.object(windows,'exchange') as exchange:
                self.assertEqual(windows.sync(root/'config.json',root)['skipped'],'battery_or_unknown_power')
                exchange.assert_not_called()
            with windows.exclusive(root/'sync.lock') as first:
                self.assertTrue(first)
                with windows.exclusive(root/'sync.lock') as second:self.assertFalse(second)

if __name__=='__main__':unittest.main()
