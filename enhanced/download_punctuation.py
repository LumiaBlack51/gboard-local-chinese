"""Download the pinned upstream int8 model; check both archive and extracted bytes."""
from pathlib import Path
import hashlib, tarfile, urllib.request

ROOT=Path(__file__).resolve().parent
NAME='sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8'
URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/'+NAME+'.tar.bz2'
ARCHIVE_SHA='c0d5aa5f8eeb686032345e180bedf39319dc2e0556781c6264bcadba8328a6e1'
MODEL_SHA='65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    cache=ROOT.parent/'artifacts/punctuation';cache.mkdir(parents=True,exist_ok=True)
    archive=cache/'model.tar.bz2'
    if not archive.is_file():urllib.request.urlretrieve(URL,archive)
    if sha(archive)!=ARCHIVE_SHA:raise ValueError('Punctuation archive SHA-256 mismatch')
    out=ROOT/'punctuation-data';out.mkdir(exist_ok=True)
    with tarfile.open(archive,'r:bz2') as tar:
        (out/'model.int8.onnx').write_bytes(tar.extractfile(NAME+'/model.int8.onnx').read())
    if sha(out/'model.int8.onnx')!=MODEL_SHA:raise ValueError('Punctuation model SHA-256 mismatch')
    print('Verified punctuation model:',out/'model.int8.onnx')
if __name__=='__main__':main()
