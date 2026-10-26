"""Converter checks use public synthetic words, never a personal export."""
import json
from pathlib import Path
import sys
import struct
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_vocab


class ConversionTest(unittest.TestCase):
    def test_explicit_pinyin_and_deduplication(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'words.txt', Path(temp) / 'words.zip'
            source.write_text('离线词库\tli xian ci ku\n离线词库\tlixianciku\n', encoding='utf-8-sig')
            result = local_vocab.convert(source, output, 'tsv', 12)
            self.assertEqual(result['words'], 1)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.namelist(), ['dictionary.txt'])
                self.assertIn('lixianciku\t离线词库\tzh-CN', archive.read('dictionary.txt').decode())

    def test_utf16_word_list_and_review(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'words.txt', Path(temp) / 'words.zip'
            source.write_text('离线词库 1\n陟罚臧否 1\n', encoding='utf-16')
            result = local_vocab.convert(source, output, 'ifly-text', 12, 'utf-16')
            self.assertEqual(result['needs_pronunciation_review'], 1)
            review = json.loads(output.with_suffix('.review.json').read_text('utf-8'))
            self.assertEqual(review['entries'][1]['pinyin'], 'zhifazangpi')

    def test_binary_does_not_create_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'backup.dat', Path(temp) / 'words.zip'
            source.write_bytes(b'\x00\xff\x80binary')
            with self.assertRaises(ValueError):
                local_vocab.convert(source, output, 'ifly-text', 12)
            self.assertFalse(output.exists())

    def test_shipped_index_uses_highest_weight_phrase_reading(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            records = [('hechengci', '合成词', 200), ('hechengsi', '合成词', 10)]
            blob, offsets = bytearray(), []
            for code, word, weight in records:
                offsets.append(len(blob))
                for value in (code, word):
                    data = value.encode('utf-8')
                    blob.extend(struct.pack('>H', len(data)) + data)
                blob.extend(struct.pack('>I', weight))
            index = folder / 'index.bin'
            index.write_bytes(b'GBI3' + struct.pack('>II', 2, 2)
                              + struct.pack('>II', *offsets) + struct.pack('>II', 0, 1)
                              + bytes(32) + blob)
            source, output = folder / 'words.txt', folder / 'words.zip'
            source.write_text('合成词 1\n', encoding='utf-8')
            result = local_vocab.convert(source, output, 'ifly-text', 32, word_index=index)
            self.assertEqual(result['readings_from_local_dictionary'], 1)
            self.assertEqual(result['needs_pronunciation_review'], 0)
            with zipfile.ZipFile(output) as archive:
                self.assertIn('hechengci\t合成词', archive.read('dictionary.txt').decode())

    def test_export_body_symbol_is_counted_as_unsupported(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'words.txt', Path(temp) / 'words.zip'
            source.write_text('###以下为正文内容###\n# 0\n合成词 0\n', encoding='utf-8')
            result = local_vocab.convert(source, output, 'ifly-text', 32)
            self.assertEqual(result['skipped'], 1)
            self.assertEqual(result['words'], 1)

    def test_pronunciation_longer_than_android_import_limit_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'words.txt', Path(temp) / 'words.zip'
            source.write_text('合成词\thechengci\n合成词\t' + 'a' * 129 + '\n', encoding='utf-8')
            result = local_vocab.convert(source, output, 'tsv', 32)
            self.assertEqual(result['words'], 1)
            self.assertEqual(result['skipped'], 1)


if __name__ == '__main__':
    unittest.main()
