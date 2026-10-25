"""Converter checks use public synthetic words, never a personal export."""
import json
from pathlib import Path
import sys
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
            self.assertEqual(result['needs_pronunciation_review'], 2)
            review = json.loads(output.with_suffix('.review.json').read_text('utf-8'))
            self.assertEqual(review['entries'][1]['pinyin'], 'zhifazangpi')

    def test_binary_does_not_create_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / 'backup.dat', Path(temp) / 'words.zip'
            source.write_bytes(b'\x00\xff\x80binary')
            with self.assertRaises(ValueError):
                local_vocab.convert(source, output, 'ifly-text', 12)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
