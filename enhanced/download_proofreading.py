"""Download pinned public build inputs; never run remote model code."""
from pathlib import Path
import json,hashlib,urllib.request,zipfile
root=Path(__file__).resolve().parent.parent
folder=root/'artifacts/proofreading';folder.mkdir(parents=True,exist_ok=True)
for item in json.loads((root/'enhanced/proofread-inputs.json').read_text('utf8')):
    path=folder/item['file'];path.parent.mkdir(parents=True,exist_ok=True)
    def valid():return path.is_file() and path.stat().st_size==item['bytes'] and hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256']
    if not valid():
        temp=path.with_suffix(path.suffix+'.download');urllib.request.urlretrieve(item['url'],temp)
        if temp.stat().st_size!=item['bytes'] or hashlib.sha256(temp.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Dependency hash mismatch: '+item['file'])
        temp.replace(path)
    if item.get('extract'):
        destination=folder/item['extract'];destination.mkdir(exist_ok=True)
        with zipfile.ZipFile(path) as z:
            for member in z.infolist():
                target=(destination/member.filename).resolve()
                if not target.is_relative_to(destination.resolve()):raise RuntimeError('Unsafe archive path')
            z.extractall(destination)
    print('verified',item['file'])
