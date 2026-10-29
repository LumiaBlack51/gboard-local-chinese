"""Broad real-engine regression: initials, abbreviations, pinyin, dedup and selection learning."""
from pathlib import Path
import string,json,hashlib,time,statistics
from rime_host import RimeHost
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from release_version import VERSION

codes=list(string.ascii_lowercase)+['zh','ch','sh','hm','nh','mt','zg','bj','sj','ni','hao','shi','de','ma','mei','nihao','mingtian','zhongguo','beijing','lv','nv','lve','nue','hello','rq','sj','2026','.',',','?','chongqing','zhongxing','changzhang','yinhang','zhonghang','xing','hang','le','yue','Hello','NASA','abc123','zhongguorenmingongheguo']
codes=list(dict.fromkeys(codes))
with RimeHost(learning=True,lua_source=Path(__file__).resolve().parents[1]/'sync') as engine:
    counts={};duplicates=[];timings=[]
    for code in codes:
        start=time.perf_counter();rows=engine.candidates(code,50);timings.append((time.perf_counter()-start)*1000);texts=[w for w,_ in rows]
        counts[code]=len(rows)
        if len(texts)!=len(set(texts)):duplicates.append(code)
    assert not duplicates,('duplicate candidate inputs',duplicates)
    m=engine.candidates('m',30)
    assert m and m[0][0] not in {'呣','呒'},'Stock rare-character shortcut still overrides ordinary input'
    learning=[]
    for code,word in [('m','米'),('shi','诗'),('hao','号')]:
        rows=engine.candidates(code);before=next(i for i,x in enumerate(rows) if x[0]==word)
        assert rows[before][1]!='cloud_phrase','Native dictionary identity masked by Lua candidate'
        for _ in range(12):
            rows=engine.candidates(code);index=next(i for i,x in enumerate(rows) if x[0]==word)
            assert engine.select(index)==word
        rows=engine.candidates(code);after=next(i for i,x in enumerate(rows) if x[0]==word)
        assert after<before or after==0,(code,before,after)
        learning.append({'code':code,'before_rank':before+1,'after_rank':after+1})
    result={'release':'v'+VERSION,'inputs_checked':len(codes),'initials_checked':26,'candidate_counts':counts,'input_processing_median_ms':round(statistics.median(timings),2),'input_processing_max_ms':round(max(timings),2),'duplicate_inputs':duplicates,'stock_shortcut_no_longer_overrides_m':True,'native_selection_learning':learning,'live_user_database_opened':False,'front_input_events_sent':False,'sources':{p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in ['sync/compact_tsv.lua','sync/cloud_phrase.lua','sync/cloud_native_filter.lua','sync/selection_learning.lua','tests/rime_host.py','tests/verify_desktop_candidates.py']},'passed':True}
    Path(f'tests/results/desktop-candidates-comprehensive-v{VERSION}.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:result[k] for k in ['inputs_checked','initials_checked','duplicate_inputs','native_selection_learning','passed']}))
