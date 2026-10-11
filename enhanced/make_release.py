import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
from pathlib import Path
import sys,zipfile,struct,subprocess
from repack import replace
ROOT=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(ROOT))
from clone_apk import decode_pool,encode_pool,U32
from add_activity import set_version
source=ROOT/'enhanced/release/base.apk';dest=ROOT/'enhanced/final';dest.mkdir(exist_ok=True)
with zipfile.ZipFile(source) as z:manifest=z.read('AndroidManifest.xml')
chunks=[];offset=8
while offset<len(manifest):size=U32(manifest,offset+4);chunks.append(bytearray(manifest[offset:offset+size]));offset+=size
pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);strings,flags=decode_pool(chunks[pi])
strings=[s.replace('Gboard 中文增强（测试）','Gboard 中文本地增强') for s in strings]
for c in chunks:
  if struct.unpack_from('<H',c)[0]!=0x102 or strings[U32(c,20)]!='application':continue
  start,width,count=struct.unpack_from('<HHH',c,24)
  for i in range(count):
    position=16+start+i*width
    if strings[U32(c,position+4)]=='debuggable':struct.pack_into('<I',c,position+16,0)
chunks[pi]=encode_pool(strings,flags);body=b''.join(chunks);manifest=struct.pack('<HHI',3,8,8+len(body))+body
changes={'AndroidManifest.xml':set_version(manifest,176027079,'18.4.1-local-1.1.0')}
for name in ['RIME-LMDG-LICENSE','LIBRIME-LICENSE','THIRD_PARTY_SOURCES.txt']:
  path=ROOT/'enhanced'/name
  if path.is_file():changes['assets/licenses/'+name]=path.read_bytes()
replace(source,dest/'unsigned.apk',changes,skip_names=('lib/arm64-v8a/libvosk.so','lib/arm64-v8a/libgboard_local_voice.so','assets/speech-fixture.wav'),skip_prefixes=('assets/local-voice/',))
def run(*args):subprocess.run([str(a) for a in args],check=True)
run(BT/'zipalign.exe','-P','16','-f','4',dest/'unsigned.apk',dest/'aligned.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',dest/'base.apk',dest/'aligned.apk')
for split in source.parent.glob('split_*.apk'):
  with zipfile.ZipFile(split) as z:raw=z.read('AndroidManifest.xml')
  temp=dest/('unsigned-'+split.name);aligned=dest/('aligned-'+split.name)
  replace(split,temp,{'AndroidManifest.xml':set_version(raw,176027079,'18.4.1-local-1.1.0')})
  run(BT/'zipalign.exe','-P','16','-f','4',temp,aligned)
  run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',dest/split.name,aligned)
run(BT/'apksigner.bat','verify',dest/'base.apk');print(dest/'base.apk')
