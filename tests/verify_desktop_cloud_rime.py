"""Check actual installed Rime consumer and hot reload without live UI or databases."""
import argparse,hashlib,json
from pathlib import Path
from rime_host import RimeHost
p=argparse.ArgumentParser();p.add_argument('--expected-entry');args=p.parse_args()
live=Path.home()/'.local/share/fcitx5/rime';root=Path(__file__).resolve().parents[1]
for name in ['cloud_phrase.lua','cloud_native_filter.lua','selection_learning.lua','phrase_lookup.lua']:
    assert (live/'lua'/name).read_bytes()==(root/'sync'/name).read_bytes(),'Installed component differs from source'
with RimeHost(learning=True) as engine:
    original=(live/'cloud-phrases.tsv').read_bytes()
    phone_origin=False;native_identity_preserved=False
    if args.expected_entry:
        expected=json.loads(Path(args.expected_entry).read_text())
        assert any(row.split('\t')[:2]==[expected['word'],expected['code']] for row in original.decode().splitlines())
        found=[kind for word,kind in engine.candidates(expected['code']) if word==expected['word']]
        assert found,'Phone-origin entry unavailable in the real engine'
        phone_origin=True;native_identity_preserved=any(kind!='cloud_phrase' for kind in found)
    actual=False
    for row in original.decode().splitlines():
        if row.startswith('#'):continue
        word,code,weight=row.split('\t')
        if len(word)>=4 and len(code)>=8 and any(text==word for text,_ in engine.candidates(code)):
            actual=True;break
    assert actual,'Synced word unavailable'
    word='禹珂凌璐彦寿祠';code='yukelingluyanshouci'
    assert not any(text==word for text,_ in engine.candidates(code))
    updated=original+f'{word}\t{code}\t999999\n'.encode()
    (engine.user/'cloud-phrases.tsv').write_bytes(('# snapshot '+hashlib.sha256(updated).hexdigest()+'\n').encode()+updated)
    assert any(text==word and kind=='cloud_phrase' for text,kind in engine.candidates(code)),'New cloud word not read without restart'
    result={'actual_synced_file_candidate_verified':True,'new_word_hot_reload_verified':True,'phone_origin_candidate_verified':phone_origin,'native_identity_preserved_for_shared_known_word':native_identity_preserved,'front_input_events_sent':False,'personal_words_printed':False,'passed':True}
    print(json.dumps(result))
