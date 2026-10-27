"""Transport a rebuilt APK using unchanged compressed ZIP payloads from a known APK.
The recipe includes all changed bytes and signing blocks; SHA-256 verifies exact output.
"""
import argparse,hashlib,json,struct,zipfile
from pathlib import Path

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for part in iter(lambda:f.read(1024*1024),b''):h.update(part)
    return h.hexdigest()

def payload(stream,entry):
    stream.seek(entry.header_offset+26)
    name,extra=struct.unpack('<HH',stream.read(4))
    return entry.header_offset+30+name+extra

def create(old,new,patch):
    recipe=[];literal=bytearray()
    with zipfile.ZipFile(old) as oz,zipfile.ZipFile(new) as nz,old.open('rb') as a,new.open('rb') as b:
        previous={i.filename:i for i in oz.infolist()};cursor=0
        for entry in sorted(nz.infolist(),key=lambda i:i.header_offset):
            start=payload(b,entry);b.seek(cursor);chunk=b.read(start-cursor)
            recipe.append(['literal',len(literal),len(chunk)]);literal.extend(chunk)
            old_entry=previous.get(entry.filename)
            match=False
            if old_entry and old_entry.compress_type==entry.compress_type and old_entry.CRC==entry.CRC and old_entry.compress_size==entry.compress_size:
                offset=payload(a,old_entry);a.seek(offset);b.seek(start)
                remaining=entry.compress_size;match=True
                while remaining:
                    n=min(1024*1024,remaining)
                    if a.read(n)!=b.read(n):match=False;break
                    remaining-=n
            if match:recipe.append(['old',offset,entry.compress_size])
            else:
                b.seek(start);chunk=b.read(entry.compress_size);recipe.append(['literal',len(literal),len(chunk)]);literal.extend(chunk)
            cursor=start+entry.compress_size
        b.seek(cursor);chunk=b.read();recipe.append(['literal',len(literal),len(chunk)]);literal.extend(chunk)
    with zipfile.ZipFile(patch,'w',compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('recipe.json',json.dumps({'old':digest(old),'new':digest(new),'parts':recipe}))
        z.writestr('literal',literal)

def apply(old,patch,new):
    with zipfile.ZipFile(patch) as z:
        recipe=json.loads(z.read('recipe.json'));literal=z.read('literal')
    assert digest(old)==recipe['old'],'Unexpected source APK'
    with old.open('rb') as source,new.open('wb') as out:
        for kind,offset,size in recipe['parts']:
            if kind=='literal':out.write(literal[offset:offset+size])
            else:
                source.seek(offset)
                while size:
                    chunk=source.read(min(size,1024*1024));assert chunk;out.write(chunk);size-=len(chunk)
    assert digest(new)==recipe['new'],'Reconstructed APK failed verification'

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['create','apply']);p.add_argument('old',type=Path);p.add_argument('new_or_patch',type=Path);p.add_argument('patch_or_new',type=Path);a=p.parse_args()
    (create if a.mode=='create' else apply)(a.old,a.new_or_patch,a.patch_or_new)
