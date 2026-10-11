"""Package a debuggable AVD adapter build without modifying the formal release outputs."""
import sys,struct,zipfile,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import ROOT,BT,SIGN_ARGS
from release_version import VERSION,KEYBOARD_CODE
from clone_apk import decode_pool,encode_pool,U32
from add_activity import set_version
from repack import replace
out=ROOT/'enhanced/build/test-apks';out.mkdir(parents=True,exist_ok=True)
for source in (ROOT/'enhanced/release').glob('*.apk'):
 if source.name.startswith('aligned-'):continue
 with zipfile.ZipFile(source) as z:raw=z.read('AndroidManifest.xml')
 if source.name=='base.apk':
  chunks=[];offset=8
  while offset<len(raw):size=U32(raw,offset+4);chunks.append(bytearray(raw[offset:offset+size]));offset+=size
  pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);strings,flags=decode_pool(chunks[pi])
  found=False
  for c in chunks:
   if struct.unpack_from('<H',c)[0]!=0x102 or strings[U32(c,20)]!='application':continue
   start,width,count=struct.unpack_from('<HHH',c,24)
   for i in range(count):
    p=16+start+i*width
    if strings[U32(c,p+4)]=='debuggable':struct.pack_into('<I',c,p+16,0xffffffff);found=True
  assert found
  body=b''.join(chunks);raw=struct.pack('<HHI',3,8,len(body)+8)+body
 raw=set_version(raw,KEYBOARD_CODE,'18.4.1-local-'+VERSION+'-debug')
 unsigned=out/('unsigned-'+source.name);aligned=out/('aligned-'+source.name)
 replace(source,unsigned,{'AndroidManifest.xml':raw})
 subprocess.run([str(BT/'zipalign.exe'),'-P','16','-f','4',str(unsigned),str(aligned)],check=True)
 subprocess.run([str(BT/'apksigner.bat'),'sign',*SIGN_ARGS,'--out',str(out/source.name),str(aligned)],check=True)
print('AVD debug APKs:',out)
