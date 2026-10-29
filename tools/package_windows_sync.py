"""Build a deterministic, source-only release from an explicit tracked allowlist."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
FILES=[
    'sync/common.py','sync/rime_data.py','sync/windows.py','sync/windows_setup.pyw',
    'sync/install_windows.ps1','sync/microsoft_pinyin.py','sync/configure_rime.py',
    'sync/cloud_phrase.lua','sync/cloud_native_filter.lua','sync/selection_learning.lua','sync/phrase_lookup.lua',
    'sync/desktop.py','docs/DICTIONARY-SYNC-WINDOWS.md','docs/DICTIONARY-SYNC.md',
    'tests/test_windows_sync.py','tests/verify_windows_rime.py','tests/verify_windows_latency.py','tests/rime_host.py',
]

def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT)

def build(output):
    commit=git('rev-parse','HEAD').decode().strip()
    version=re.search(r"VERSION='([^']+)'",git('show','HEAD:release_version.py').decode())[1]
    output.mkdir(parents=True,exist_ok=True)
    files={name:git('show','HEAD:'+name) for name in FILES}
    files['README.md']=files['docs/DICTIONARY-SYNC-WINDOWS.md']
    provenance={'format':1,'version':version,'sourceCommit':commit,
        'sourceUrl':'https://github.com/LumiaBlack51/gboard-local-chinese/tree/'+commit,
        'files':{name:hashlib.sha256(data).hexdigest() for name,data in sorted(files.items())}}
    files['source-manifest.json']=(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n').encode()
    archive=output/('windows-phrase-sync-'+version+'.zip')
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as package:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644<<16
            package.writestr(info,data)
    digest=hashlib.sha256(archive.read_bytes()).hexdigest()
    checksum=archive.with_suffix('.sha256')
    checksum.write_text(digest+'  '+archive.name+'\n',encoding='ascii',newline='\n')
    assets=[{'name':p.name,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [archive,checksum]]
    manifest=output/'windows-phrase-sync-release.json'
    manifest.write_text(json.dumps({'format':1,'version':version,'tag':'v'+version,'sourceCommit':commit,'assets':assets},indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({'archive':str(archive),'bytes':archive.stat().st_size,'sha256':digest,'sourceCommit':commit}))
    return archive,checksum,manifest

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=ROOT/'artifacts/windows-release')
    build(parser.parse_args().output)
