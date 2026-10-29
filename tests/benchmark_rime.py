"""Compare deployed Rime configuration with a prior revision in isolated engines.

Copies databases/learning snapshots to temporary directories, never sends desktop
keys. Each revision runs in its own process to avoid shared Lua caches.
"""
import argparse,ctypes as C,hashlib,json,os,re,shutil,statistics,subprocess,sys,tempfile,time
from pathlib import Path
from rime_host import RimeHost,Iterator
ROOT=Path(__file__).resolve().parents[1]
PHRASES=['nihao','zhongguo','shurufa','zhongwenshurufa','jintianxiawukaohui','wozhengzaiceshizhongwenshurufadeyanchi','tianjiangdarenyusishiren','zhi','shi','xian','xianyang','changan']
def summary(values):
 s=sorted(values);return {'n':len(s),'median_ms':round(statistics.median(s),3),'p95_ms':round(s[min(len(s)-1,int(len(s)*.95))],3),'max_ms':round(s[-1],3)}
def worker(args):
 live=Path.home()/'.local/share/fcitx5/rime'
 def configure(user,config):
  for name in ['cloud-learning.tsv','local-native-learning.tsv']:
   if (live/name).exists():shutil.copyfile(live/name,user/name)
  for name in ['selection-learning','rime_ice.userdb']:
   if (live/name).exists():shutil.copytree(live/name,user/name)
 rows=[]
 with RimeHost(learning=True,configure=configure,lua_source=args.lua_source,annotate=False) as h:
  # Function-pointer layout is append-only in the official RimeApi. Read names
  # from the checked-in ABI header instead of requiring a throwaway native shim.
  header=(ROOT/'enhanced/native/rime_api.h').read_text().split('typedef struct RIME_FLAVORED(rime_api_t) {')[1].split('} RIME_FLAVORED(RimeApi);')[0]
  names=re.findall(r'\(\*(\w+)\)',header)
  class Api(C.Structure):_fields_=[('data_size',C.c_int)]+[(n,C.c_void_p) for n in names]
  h.r.rime_get_api.restype=C.POINTER(Api);get_input=C.CFUNCTYPE(C.c_char_p,C.c_size_t)(h.r.rime_get_api().contents.get_input)
  for rep in range(args.iterations):
   for code in PHRASES:
    h.r.RimeClearComposition(h.session)
    for phase in ['input','delete']:
     for index in range(len(code)):
      t=time.perf_counter_ns();h.r.RimeProcessKey(h.session,ord(code[index]) if phase=='input' else 0xff08,0)
      it=Iterator();count=0
      if h.r.RimeCandidateListBegin(h.session,C.byref(it)):
       while count<5 and h.r.RimeCandidateListNext(C.byref(it)):count+=1
       h.r.RimeCandidateListEnd(C.byref(it))
      duration=(time.perf_counter_ns()-t)/1e6
      expected=code[:index+1] if phase=='input' else code[:len(code)-index-1]
      assert (get_input(h.session) or b'').decode()==expected
      rows.append({'phase':phase,'rep':rep,'total_ms':duration})
 return {p:summary([r['total_ms'] for r in rows if r['rep']>0 and r['phase']==p]) for p in ['input','delete']}
def main():
 p=argparse.ArgumentParser();p.add_argument('--baseline-ref',default='v1.3.7');p.add_argument('--iterations',type=int,default=4);p.add_argument('--output',type=Path,default=ROOT/'tests/results/desktop-latency-v1.3.8.json');p.add_argument('--lua-source',type=Path,default=ROOT/'sync');p.add_argument('--worker',action='store_true');a=p.parse_args()
 if a.worker:print(json.dumps(worker(a)));return
 if a.iterations<2:p.error('At least one warm-up and one measured pass required')
 revision=subprocess.check_output(['git','rev-parse',a.baseline_ref+'^{commit}'],cwd=ROOT,text=True).strip()
 with tempfile.TemporaryDirectory(prefix='rime-baseline-lua-') as folder:
  baseline=Path(folder)
  paths=subprocess.check_output(['git','ls-tree','-r','--name-only',revision,'sync'],cwd=ROOT,text=True).splitlines()
  for name in paths:
   if name.endswith('.lua'):(baseline/Path(name).name).write_bytes(subprocess.check_output(['git','show',revision+':'+name],cwd=ROOT))
  result={}
  for name,source in [('before',baseline),('after',a.lua_source)]:
   result[name]=json.loads(subprocess.check_output([sys.executable,__file__,'--worker','--lua-source',str(source),'--iterations',str(a.iterations)],cwd=ROOT,text=True))
  result.update(baseline_revision=revision,method='Isolated copies of deployed schema/database/shared data; normal mode, each key followed by first five candidates; one warm-up pass excluded; no input-window/display timing.',front_input_events_sent=False,sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'sync').glob('*.lua')})
  a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
