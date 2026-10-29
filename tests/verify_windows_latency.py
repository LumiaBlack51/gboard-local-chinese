"""Measure typing/deletion with 33k synthetic phrases; never open a live user DB."""
import argparse
import ctypes as C
import json
import os
from pathlib import Path
import shutil
import statistics
import sys
import time
import winreg
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
from common import encode,encode_learning
from rime_data import snapshot
from rime_host import RimeHost,Iterator

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--components-from',type=Path)
    parser.add_argument('--max-p95-ms',type=float,default=33)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r'SOFTWARE\Rime\Weasel',0,winreg.KEY_READ|winreg.KEY_WOW64_32KEY) as key:
        directory=Path(winreg.QueryValueEx(key,'WeaselRoot')[0])
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER,r'Software\Rime\Weasel') as key:
        live=Path(winreg.QueryValueEx(key,'RimeUserDir')[0])
    dll_directory=os.add_dll_directory(str(directory))
    os.environ['PATH']=str(directory)+os.pathsep+os.environ['PATH']
    chars='零一二三四五六七八九'
    codes=['ling','yi','er','san','si','wu','liu','qi','ba','jiu']
    words={}
    for n in range(33000):
        digits=list(map(int,f'{n:05d}'))
        key=('测试'+''.join(chars[d] for d in digits),'ceshi'+''.join(codes[d] for d in digits))
        words[key]=100+n%100
    learned={key:(1+n%20,0) for n,key in enumerate(list(words)[:17000])}
    def configure(user,config):
        link=user/'opencc'
        if link.is_symlink():link.unlink()
        shutil.copytree(directory/'data/opencc',link,dirs_exist_ok=True)
        shutil.copytree(live/'opencc',link,dirs_exist_ok=True)
        (user/'selection-learning').mkdir(exist_ok=True)
        snapshot(user/'cloud-phrases.tsv',encode(words))
        snapshot(user/'cloud-learning.tsv',encode_learning(learned,comments=False))
        if args.components_from:
            for name in ['cloud_phrase.lua','cloud_native_filter.lua','selection_learning.lua','phrase_lookup.lua']:
                shutil.copyfile(args.components_from/name,user/'lua'/name)
    durations={'typing':[],'backspace':[]}
    with RimeHost(live,learning=True,configure=configure) as host:
        def page():
            iterator=Iterator()
            if host.r.RimeCandidateListBegin(host.session,C.byref(iterator)):
                for _ in range(5):
                    if not host.r.RimeCandidateListNext(C.byref(iterator)):break
                host.r.RimeCandidateListEnd(C.byref(iterator))
        for repeat in range(2):
            for sample in ['zhonghuarenmingongheguo','woxiangceshichangantuihuipinyindesudu']:
                host.r.RimeClearComposition(host.session)
                for ch in sample:
                    start=time.perf_counter();host.r.RimeProcessKey(host.session,ord(ch),0);page()
                    if repeat:durations['typing'].append((time.perf_counter()-start)*1000)
                for _ in sample:
                    start=time.perf_counter();host.r.RimeProcessKey(host.session,0xff08,0);page()
                    if repeat:durations['backspace'].append((time.perf_counter()-start)*1000)
    report={'synthetic_phrases':len(words),'synthetic_learning_rows':len(learned),'scope':'installed librime key processing and first candidate page; excludes UI/IPC and OS repeat delay'}
    for name,values in durations.items():
        ordered=sorted(values)
        report[name]={'keys':len(values),'mean_ms':round(statistics.mean(values),2),'p50_ms':round(statistics.median(values),2),'p95_ms':round(ordered[int(len(values)*.95)],2),'max_ms':round(max(values),2)}
    report['success']=all(report[name]['p95_ms']<args.max_p95_ms for name in durations)
    raw=json.dumps(report,indent=2)+'\n'
    if args.output:args.output.write_text(raw,encoding='utf-8')
    print(raw)
    assert report['success'],'Candidate processing exceeds the configured latency budget'

if __name__=='__main__':main()
