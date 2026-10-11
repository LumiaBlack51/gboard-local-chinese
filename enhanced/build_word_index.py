from pathlib import Path
import struct,re,json,hashlib,math,array
ROOT=Path(__file__).resolve().parent/'rime-data'
entries={}
for file in (ROOT/'cn_dicts').glob('*.dict.yaml'):
  if file.stem in ('41448.dict','8105.dict'):continue
  data=False
  for line in file.read_text(encoding='utf8').splitlines():
    if line=='...':data=True;continue
    if not data or not line or line.startswith('#'):continue
    cols=line.split('\t')
    if len(cols)<2 or not re.fullmatch(r'[\u3400-\u9fff]{2,12}',cols[0]):continue
    if len(cols)>=3 and re.fullmatch(r'[a-zvü ]+',cols[1]):
      code=cols[1].replace(' ','').replace('ü','v')
      try:weight=int(float(cols[2].rstrip('%')))
      except ValueError:weight=100
      key=(code,cols[0])
      entries[key]=max(entries.get(key,0),max(1,min(2147483647,weight)))
records=sorted((code,word,weight) for (code,word),weight in entries.items())
out=ROOT/'word-index-v3.bin'
offsets=[];blob=bytearray()
for code,word,weight in records:
  offsets.append(len(blob))
  for value in (code,word):
    b=value.encode('utf8');blob+=struct.pack('>H',len(b))+b
  blob+=struct.pack('>I',weight)
han=sorted(range(len(records)),key=lambda i:records[i][1])
size=1
while size<len(records):size*=2
def tree(chinese):
  values=array.array('f',[-float('inf')])*(size*2)
  for i in range(len(records)):
    code,word,weight=records[han[i] if chinese else i]
    values[size+i]=math.log(weight+1)-0.24*len(word if chinese else code)
  for i in range(size-1,0,-1):values[i]=max(values[i*2],values[i*2+1])
  if __import__('sys').byteorder=='little':values.byteswap()
  return values.tobytes()
with out.open('wb') as f:
  f.write(struct.pack('>III',0x47424933,len(records),size))
  for offset in offsets:f.write(struct.pack('>I',offset))
  for index in han:f.write(struct.pack('>I',index))
  f.write(tree(False));f.write(tree(True))
  f.write(blob)
print(json.dumps({'entries':len(records),'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest()}))
