"""Replace ZIP entries without decompressing large unchanged offline models."""
import copy,struct,zipfile
def replace(source,target,changes,skip_signature=True,skip_names=(),skip_prefixes=()):
  with zipfile.ZipFile(source) as old,open(source,'rb') as raw,zipfile.ZipFile(target,'w') as new:
    for original in old.infolist():
      if skip_signature and original.filename.startswith('META-INF/'):continue
      if original.filename in skip_names or any(original.filename.startswith(p) for p in skip_prefixes):continue
      info=copy.copy(original)
      if info.filename in changes:
        new.writestr(info,changes[info.filename]);continue
      raw.seek(original.header_offset);header=raw.read(30)
      assert header[:4]==b'PK\x03\x04'
      filename,extra=struct.unpack_from('<HH',header,26)
      length=30+filename+extra+original.compress_size
      if original.flag_bits&8:
        raw.seek(original.header_offset+length);length+=16 if raw.read(4)==b'PK\x07\x08' else 12
      raw.seek(original.header_offset);new.fp.seek(new.start_dir);info.header_offset=new.fp.tell()
      remaining=length
      while remaining:
        block=raw.read(min(8388608,remaining));assert block;new.fp.write(block);remaining-=len(block)
      new.start_dir=new.fp.tell();new.filelist.append(info);new.NameToInfo[info.filename]=info;new._didModify=True
    for name,content in changes.items():
      if name not in new.NameToInfo:new.writestr(name,content,compress_type=zipfile.ZIP_DEFLATED)
