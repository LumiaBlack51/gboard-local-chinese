"""Compare all server asset digests and sizes with the local signed release."""
import argparse,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--gh',default='gh');a=p.parse_args()
root=Path(__file__).resolve().parent.parent
def gh(*args):return json.loads(subprocess.check_output([a.gh,*args],encoding='utf8'))
release=gh('release','view','v1.1.1','--repo','LumiaBlack51/gboard-local-chinese','--json','assets,isDraft,tagName,url')
repo=gh('repo','view','LumiaBlack51/gboard-local-chinese','--json','visibility')
assert repo['visibility']=='PUBLIC'
local={x['name']:root/x['file'] for x in json.loads((root/'release-manifest.json').read_text('utf8'))['assets']}
local.update({name:root/name for name in ['update-manifest.json','release-manifest.json','SHA256SUMS.txt']})
assets={x['name']:x for x in release['assets']};assert assets.keys()==local.keys()
for name,path in local.items():
 asset=assets[name];assert asset['state']=='uploaded' and asset['size']==path.stat().st_size
 assert asset['digest']=='sha256:'+hashlib.sha256(path.read_bytes()).hexdigest(),name
print('All nine uploaded assets match local SHA-256 and size; repository is public.')
