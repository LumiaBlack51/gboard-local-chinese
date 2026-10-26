"""Find genuinely new words and resolve readings using offline phrase dictionaries.

No network requests. Private source words and reports should stay in private/.
Dictionary-supported readings are not recovered iFlytek input history.
"""
from pathlib import Path
import functools,json,re,unicodedata,zipfile,hashlib
from pypinyin import pinyin,Style
from pypinyin.phrases_dict import phrases_dict


def resolve_readings(records,rime_dir,phrase_data):
    pending={e['word']:e for e in records}
    needles={word[i:j] for word in pending for i in range(len(word)) for j in range(i+2,len(word)+1)}
    phrases={}
    def normal(value):
        for char in 'üǖǘǚǜ':value=value.replace(char,'v')
        return ''.join(c for c in unicodedata.normalize('NFD',value.lower()) if not unicodedata.combining(c))
    def add(word,code,source):
        if word not in needles or not re.fullmatch('[a-z]+',code):return
        phrases.setdefault(word,{}).setdefault(code,set()).add(source)
    for word in needles:
        if word in phrases_dict:
            values=pinyin(word,style=Style.NORMAL,heteronym=True)
            if all(len(set(row))==1 for row in values):add(word,''.join(row[0] for row in values),'pypinyin phrase dictionary')
    for line in Path(phrase_data).read_text('utf-8-sig').splitlines():
        content=line.split('#',1)[0].strip()
        if not content or ':' not in content:continue
        word,reading=content.split(':',1)
        add(word.strip(),normal(''.join(reading.split())),'large_pinyin phrase dictionary')
    root=Path(rime_dir); main=root/'rime_ice.dict.yaml'; existing=set()
    tables=re.findall(r'^\s+-\s+([A-Za-z0-9_/]+)',main.read_text('utf-8').split('...')[0],re.M)
    for file in [main]+[root/(name+'.dict.yaml') for name in tables]:
        body=False
        for line in file.read_text('utf-8-sig').splitlines():
            if line=='...':body=True;continue
            if not body or not line or line.startswith('#'):continue
            fields=line.split('\t')
            if len(fields)>1 and fields[0] in pending and re.fullmatch('[a-zvü ]+',fields[1]):existing.add(fields[0])
            if len(fields)>1 and fields[0] in needles and re.fullmatch('[a-zvü ]+',fields[1]):
                add(fields[0],fields[1].replace(' ','').replace('ü','v'),'active Rime phrase dictionary')
    @functools.lru_cache(None)
    def chars(char):return {normal(code) for code in pinyin(char,style=Style.NORMAL,heteronym=True)[0]}
    def resolve(word):
        if word in phrases:
            codes=set(phrases[word]);return codes,'whole_phrase_dictionary'
        @functools.lru_cache(None)
        def at(start):
            if start==len(word):return 0,{''},False
            options=[(start+1,chars(word[start]),0)]
            for end in range(start+2,len(word)+1):
                text=word[start:end]
                if text in phrases:options.append((end,set(phrases[text]),(end-start)**2))
            best=-1;codes=set();overflow=False
            for end,heads,score in options:
                tail_score,tails,cut=at(end);score+=tail_score
                if score<best:continue
                if score>best:best=score;codes=set();overflow=False
                overflow|=cut
                for head in heads:
                    for tail in tails:
                        codes.add(head+tail)
                        if len(codes)>128:overflow=True;break
                    if len(codes)>128:break
            return best,codes,overflow
        score,codes,overflow=at(0)
        if overflow:return set(),'ambiguous_overflow'
        return codes,'dictionary_segments' if score else 'single_character_dictionary'
    resolved=[];ambiguous=[]
    all_count=len(pending);pending={word:entry for word,entry in pending.items() if word not in existing}
    for word,entry in pending.items():
        codes,source=resolve(word)
        valid=sorted(code for code in codes if re.fullmatch('[a-z]{1,128}',code))
        row=dict(entry,readings=valid,reading_source=source)
        if len(valid)==1 and len(codes)==1:
            row['pinyin']=valid[0];resolved.append(row)
        else:ambiguous.append(row)
    summary=dict(source_words=all_count,already_in_active_rime=len(existing),not_in_active_rime=len(pending),resolved_new_words=len(resolved),unresolved_new_words=len(ambiguous))
    return dict(summary=summary,resolved=resolved,ambiguous=ambiguous,source_sha256=hashlib.sha256(Path(phrase_data).read_bytes()).hexdigest())


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--review',type=Path,required=True,help='local_vocab.py review JSON')
    parser.add_argument('--rime-data',type=Path,required=True)
    parser.add_argument('--phrase-data',type=Path,required=True,help='phrase-pinyin-data large_pinyin.txt')
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    records=json.loads(args.review.read_text('utf-8'))['entries']
    if len(records)>50000:parser.error('More than 50000 source records')
    result=resolve_readings(records,args.rime_data,args.phrase_data)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('dictionary.txt','# Gboard Dictionary version:1\n'+''.join(entry['pinyin']+'\t'+entry['word']+'\tzh-CN\n' for entry in result['resolved']))
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result['summary']))

if __name__=='__main__':main()
