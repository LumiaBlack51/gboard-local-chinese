"""Add one private permission activity to the existing compiled manifest."""
import struct
from clone_apk import decode_pool,encode_pool,U32
def add_activity(raw,class_name):
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
  attrs=[struct.pack('<IIIHBBI',ns,name,value,8,0,3,value),struct.pack('<IIIHBBI',ns,exported,0xffffffff,8,0,0x12,0)]
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
  additions=[start('uses-permission','local.gboard.voice.ACCESS'),end('uses-permission'),start('queries'),start('package','local.gboard.voice'),end('package'),end('queries')]
  ci=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x102 and strings[U32(c,20)]=='application')
  chunks[pi]=encode_pool(strings,flags);chunks[ci:ci]=additions
  body=b''.join(chunks);return struct.pack('<HHI',3,8,8+len(body))+body
