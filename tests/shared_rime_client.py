"""One isolated native Rime process for the two-client learning regression."""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
from common import parse_learning,encode_learning,packet
from desktop import local_learning
from rime_host import RimeHost

root=Path(__file__).resolve().parents[1]
incoming=base64.b64decode(sys.argv[1])
def configure(user,config):
    (user/'selection-learning').mkdir()
    for name in ['compact_tsv.lua','cloud_phrase.lua','cloud_native_filter.lua','selection_learning.lua']:
        shutil.copyfile(root/'sync'/name,user/'lua'/name)

with RimeHost(learning=True,configure=configure) as engine:
    before=next(i for i,row in enumerate(engine.candidates('zhi')) if row[0]=='值')
    data=encode_learning(parse_learning(incoming),comments=False)
    (engine.user/'cloud-learning.tsv').write_bytes(('# snapshot '+hashlib.sha256(data).hexdigest()+'\n').encode()+data)
    rows=engine.candidates('zhi');assert rows[0]==('值','phrase'),rows[:3]
    # Native identity is retained and a real selection counter is recorded.
    for _ in range(6):
        rows=engine.candidates('zhi');index=next(i for i,row in enumerate(rows) if row[0]=='知')
        assert engine.select(index)=='知'
    own=local_learning(engine.user)
    assert own.get(('知','zhi'),(0,0))[0]==6,own
    assert ('值','zhi') not in own,'Downloaded counts fed back as this device learning'
    engine.restart();assert engine.candidates('zhi')[0][0]=='知'
    data=encode_learning({('字','zi'):(5,0)},comments=False)
    (engine.user/'local-native-learning.tsv').write_bytes(('# snapshot '+hashlib.sha256(data).hexdigest()+'\n').encode()+data)
    rows=engine.candidates('zi');index=next(i for i,row in enumerate(rows) if row[0]=='字')
    assert engine.select(index)=='字'
    assert local_learning(engine.user)[('字','zi')][0]==6,'Native historical counter was not continued'
    print(json.dumps({'before_rank':before+1,'remote_rank':1,'native_identity_preserved':True,
        'local_choice_persisted':True,'native_history_continued':True,'outgoing_base64':base64.b64encode(packet({},own)).decode(),'passed':True}))
