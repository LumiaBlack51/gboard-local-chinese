"""Verify public release digests against signed metadata and optional desktop assets."""
import argparse,hashlib,json,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--gh',default='gh');a=p.parse_args()
root=Path(__file__).resolve().parent.parent
def gh(*args):return json.loads(subprocess.check_output([a.gh,*args],encoding='utf8'))
release=gh('release','view',json.loads((root/'release-manifest.json').read_text('utf8'))['tag'],'--repo','LumiaBlack51/gboard-local-chinese','--json','assets,isDraft,tagName,url')
repo=gh('repo','view','LumiaBlack51/gboard-local-chinese','--json','visibility')
assert repo['visibility']=='PUBLIC'
expected={x['name']:(x['bytes'],x['sha256'],root/x['file']) for x in json.loads((root/'release-manifest.json').read_text('utf8'))['assets']}
for name in ['update-manifest.json','release-manifest.json','SHA256SUMS.txt']:
 raw=(root/name).read_bytes().replace(b'\r\n',b'\n')
 expected[name]=(len(raw),hashlib.sha256(raw).hexdigest(),None)
assets={x['name']:x for x in release['assets']}
if 'windows-phrase-sync-release.json' in assets:
 with tempfile.TemporaryDirectory(prefix='release-metadata-check-') as folder:
  subprocess.run([a.gh,'release','download',release['tagName'],'--repo','LumiaBlack51/gboard-local-chinese','--pattern','windows-phrase-sync-release.json','--dir',folder],check=True)
  raw=(Path(folder)/'windows-phrase-sync-release.json').read_bytes()
  desktop=json.loads(raw)
  assert desktop['tag']==release['tagName']
  assert len(desktop['sourceCommit'])==40
  for item in desktop['assets']:expected[item['name']]=(item['bytes'],item['sha256'],None)
  expected['windows-phrase-sync-release.json']=(len(raw),hashlib.sha256(raw).hexdigest(),None)
assert assets.keys()==expected.keys(),(assets.keys(),expected.keys())
for name,(size,digest,path) in expected.items():
 asset=assets[name];assert asset['state']=='uploaded' and asset['size']==size,name
 assert asset['digest']=='sha256:'+digest,name
 if path is not None and path.exists():
  assert path.stat().st_size==size and hashlib.sha256(path.read_bytes()).hexdigest()==digest,name
print('All',len(expected),'public release assets match integrity metadata; available local signed APKs also match.')
