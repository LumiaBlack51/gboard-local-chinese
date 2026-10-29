"""Exercise installed Windows librime in a fresh profile; no UI or live DB writes."""
import ctypes.util
import json
import os
from pathlib import Path
import shutil
import sys
import time
import winreg
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
from common import encode, encode_learning, parse
from rime_data import local_learning, snapshot
from rime_host import RimeHost

def main():
    with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r'SOFTWARE\Rime\Weasel',0,winreg.KEY_READ|winreg.KEY_WOW64_32KEY) as key:
        directory=Path(winreg.QueryValueEx(key,'WeaselRoot')[0])
    dll_directory=os.add_dll_directory(str(directory))
    os.environ['PATH']=str(directory)+os.pathsep+os.environ['PATH']
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER,r'Software\Rime\Weasel') as key:
        live=Path(winreg.QueryValueEx(key,'RimeUserDir')[0])
    imported=parse((Path.home()/'.config/gboard-phrase-sync/microsoft-phrases.tsv').read_bytes())
    selected=list(key for key in sorted(imported) if 3<=len(key[0])<=5)[:3]
    word,code='同步麒麟','tongbuqilin'
    def setup(user,config):
        # The running Weasel also searches its shared OpenCC directory.
        link=user/'opencc'
        if link.is_symlink(): link.unlink()
        shutil.copytree(directory/'data/opencc',link,dirs_exist_ok=True)
        shutil.copytree(live/'opencc',link,dirs_exist_ok=True)
        snapshot(user/'cloud-phrases.tsv',encode({**{key:100 for key in selected},(word,code):100}))
        snapshot(user/'cloud-learning.tsv',encode_learning({(word,code):(7,1000)},comments=False))
        (user/'selection-learning').mkdir()
    report={}
    with RimeHost(live,learning=True,configure=setup) as host:
        host.r.RimeSetOption(host.session,b'incognito',1)
        assert not any(row[1]=='cloud_phrase' for row in host.candidates(code,100)), 'Cloud candidates leaked into incognito mode'
        host.r.RimeSetOption(host.session,b'incognito',0)
        rows=host.candidates(code,100)
        assert rows[0][0]==word, 'Shared learned word did not reach the first candidate'
        host.select(0)
        rows=host.candidates(code,100)
        host.select(next(i for i,row in enumerate(rows) if row[0]==word))
        own=local_learning(host.user,{})
        assert own[(word,code)][0]>=2,'Second Windows selection did not persist'
        # Disk, rather than the Lua module cache, must preserve the second write.
        data=(host.user/'selection-learning'/f'{code}.tsv').read_bytes()
        assert b'\t2\t' in data or own[(word,code)][0]>2
        report['repeated_selection_persisted']=True
        report['shared_learning_ranks_candidate']=True
        report['personal_import_candidates_checked']=len(selected)
        for key in selected:
            assert any(row[0]==key[0] for row in host.candidates(key[1],100)), 'Imported Microsoft word absent from candidates'
        host.r.RimeSetOption(host.session,b'incognito',1)
        rows=host.candidates(code,100)
        host.select(next(i for i,row in enumerate(rows) if row[0]==word))
        assert local_learning(host.user,{})==own, 'Incognito selection changed local aggregates'
        report['incognito_excludes_cloud']=True
    report['success']=True
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
