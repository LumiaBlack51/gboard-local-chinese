"""Offline reading resolution with public synthetic fixtures only."""
from pathlib import Path
import sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from resolve_vocab import resolve_readings

class ReadingTest(unittest.TestCase):
    def fixture(self,folder):
        (folder/'rime_ice.dict.yaml').write_text('---\nname: rime_ice\nimport_tables:\n  - base\n...\n',encoding='utf-8')
        (folder/'base.dict.yaml').write_text('---\nname: base\n...\n测试\tce shi\t100\n',encoding='utf-8')
        (folder/'inactive.dict.yaml').write_text('---\nname: inactive\n...\n新词字\txin ci zi\t100\n',encoding='utf-8')
        phrase=folder/'phrase.txt'
        phrase.write_text('新词: xīn cí\n乐字: lè zì\n乐字: yuè zì\n',encoding='utf-8')
        return phrase

    def test_skips_existing_words_and_inactive_tables(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);phrase=self.fixture(folder)
            rows=[{'word':w,'pinyin':'placeholder','generated':True} for w in ['测试','新词字']]
            result=resolve_readings(rows,folder,phrase)
            self.assertEqual(result['summary']['already_in_active_rime'],1)
            self.assertEqual(result['resolved'][0]['pinyin'],'xincizi')
            self.assertEqual(result['resolved'][0]['reading_source'],'dictionary_segments')

    def test_conflicting_whole_phrase_readings_are_not_selected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);phrase=self.fixture(folder)
            result=resolve_readings([{'word':'乐字','pinyin':'lezi'}],folder,phrase)
            self.assertEqual(result['resolved'],[])
            self.assertEqual(set(result['ambiguous'][0]['readings']),{'lezi','yuezi'})

    def test_single_character_reading_and_true_polyphone(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);phrase=self.fixture(folder)
            result=resolve_readings([{'word':'字','pinyin':'zi'},{'word':'重','pinyin':'zhong'}],folder,phrase)
            self.assertEqual([e['word'] for e in result['resolved']],['字'])
            self.assertEqual([e['word'] for e in result['ambiguous']],['重'])

if __name__=='__main__':unittest.main()
