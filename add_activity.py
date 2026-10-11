"""Add one private permission activity to the existing compiled manifest."""
import struct
from clone_apk import decode_pool,encode_pool,U32
def add_activity(raw,class_name,exported_value=False):
  chunks=[];offset=8
  while offset<len(raw):
    size=U32(raw,offset+4);chunks.append(raw[offset:offset+size]);offset+=size
  pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1)
  strings,flags=decode_pool(chunks[pi])
  def string(value):
    if value not in strings:strings.append(value)
    return strings.index(value)
  ns=string('http://schemas.android.com/apk/res/android');activity=string('activity');name=string('name');exported=string('exported');value=string(class_name)
  mi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
  ids=[U32(chunks[mi],j) for j in range(8,len(chunks[mi]),4)];ids += [0]*(max(name,exported)+1-len(ids))
  ids[name]=0x01010003;ids[exported]=0x01010010
  chunks[mi]=struct.pack('<HHI',0x180,8,8+len(ids)*4)+b''.join(struct.pack('<I',n) for n in ids)
  attrs=[struct.pack('<IIIHBBI',ns,name,value,8,0,3,value),struct.pack('<IIIHBBI',ns,exported,0xffffffff,8,0,0x12,0xffffffff if exported_value else 0)]
  start=struct.pack('<HHIII',0x102,16,36+len(attrs)*20,0,0xffffffff)+struct.pack('<IIHHHHHH',0xffffffff,activity,20,20,len(attrs),0,0,0)+b''.join(attrs)
  end=struct.pack('<HHIIIII',0x103,16,24,0,0xffffffff,0xffffffff,activity)
  ci=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x103 and strings[U32(c,20)]=='application')
  chunks[pi]=encode_pool(strings,flags);chunks[ci:ci]=[start,end]
  body=b''.join(chunks);return struct.pack('<HHI',3,8,8+len(body))+body

def add_voice_access(raw):
  chunks=[];offset=8
  while offset<len(raw):size=U32(raw,offset+4);chunks.append(raw[offset:offset+size]);offset+=size
  pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);strings,flags=decode_pool(chunks[pi])
  if 'local.gboard.voice.ACCESS' in strings:return raw
  def string(value):
    if value not in strings:strings.append(value)
    return strings.index(value)
  ns=string('http://schemas.android.com/apk/res/android');name=string('name')
  def start(tag,value=None):
    attrs=[] if value is None else [struct.pack('<IIIHBBI',ns,name,string(value),8,0,3,string(value))]
    return struct.pack('<HHIII',0x102,16,36+len(attrs)*20,0,0xffffffff)+struct.pack('<IIHHHHHH',0xffffffff,string(tag),20,20,len(attrs),0,0,0)+b''.join(attrs)
  def end(tag):return struct.pack('<HHIIIII',0x103,16,24,0,0xffffffff,0xffffffff,string(tag))
  additions=[start('uses-permission','local.gboard.voice.ACCESS'),end('uses-permission'),start('queries'),start('package','local.gboard.voice'),end('package'),start('package','local.gboard.updater'),end('package'),end('queries')]
  ci=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x102 and strings[U32(c,20)]=='application')
  chunks[pi]=encode_pool(strings,flags);chunks[ci:ci]=additions
  body=b''.join(chunks);return struct.pack('<HHI',3,8,8+len(body))+body

def management_launcher(raw):
  chunks=[];offset=8
  while offset<len(raw):size=U32(raw,offset+4);chunks.append(bytearray(raw[offset:offset+size]));offset+=size
  pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);strings,flags=decode_pool(chunks[pi])
  old='com.google.android.libraries.inputmethod.launcher.LauncherActivity';new='local.gboard.enhanced.ManagementActivity'
  assert old in strings;strings[strings.index(old)]=new
  for c in chunks:
    if struct.unpack_from('<H',c)[0]!=0x102 or strings[U32(c,20)]!='activity':continue
    start,width,count=struct.unpack_from('<HHH',c,24);attrs=[16+start+i*width for i in range(count)]
    if not any(strings[U32(c,p+4)]=='name' and strings[U32(c,p+16)]==new for p in attrs if c[p+15]==3):continue
    for p in attrs:
      if strings[U32(c,p+4)]=='enabled':c[p+15]=0x12;struct.pack_into('<I',c,p+8,0xffffffff);struct.pack_into('<I',c,p+16,0xffffffff)
  chunks[pi]=encode_pool(strings,flags);body=b''.join(chunks);return struct.pack('<HHI',3,8,len(body)+8)+body

def set_version(raw,code,name):
  chunks=[];offset=8
  while offset<len(raw):size=U32(raw,offset+4);chunks.append(bytearray(raw[offset:offset+size]));offset+=size
  pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);strings,flags=decode_pool(chunks[pi])
  if name not in strings:strings.append(name)
  for c in chunks:
    if struct.unpack_from('<H',c)[0]!=0x102 or strings[U32(c,20)]!='manifest':continue
    start,width,count=struct.unpack_from('<HHH',c,24)
    for i in range(count):
      p=16+start+i*width;key=strings[U32(c,p+4)]
      if key=='versionCode':struct.pack_into('<I',c,p+16,code)
      if key=='versionName':c[p+15]=3;struct.pack_into('<I',c,p+8,strings.index(name));struct.pack_into('<I',c,p+16,strings.index(name))
  chunks[pi]=encode_pool(strings,flags);body=b''.join(chunks);return struct.pack('<HHI',3,8,len(body)+8)+body
