#!/usr/bin/env python3
"""Install the cloud components without replacing user dictionaries or key bindings."""
import argparse
from pathlib import Path
import shutil
import yaml

STOCK_SHORTCUTS={('呣','m'),('呒','m'),('噷','hm')}

def configure(root,dynamic_ranking=False):
    source=Path(__file__).resolve().parent
    path=root/'rime_ice.custom.yaml'
    settings=yaml.safe_load(path.read_text('utf-8-sig')) if path.exists() else {}
    settings=settings or {};patch=settings.setdefault('patch',{})
    backup=path.with_name(path.name+'.before-cloud-setup')
    if path.exists() and not backup.exists():shutil.copy2(path,backup)
    desired={'engine/translators/@before 0':'lua_translator@*cloud_phrase','engine/filters/@before 0':'lua_filter@*cloud_native_filter'}
    for key,value in desired.items():
        if key in patch and patch[key]!=value:raise ValueError('Merge existing custom component explicitly: '+key)
        patch[key]=value
    # The previous punctuation customization replaced the final uniquifier.
    if patch.get('engine/filters/@last')=='lua_filter@*punctuation_shape':
        del patch['engine/filters/@last'];patch['engine/filters/@before last']='lua_filter@*punctuation_shape'
    if dynamic_ranking:
        if 'pin_cand_filter' not in patch:patch['pin_cand_filter']=[]
        phrases=root/'custom_phrase.txt'
        if phrases.exists():
            previous=phrases.with_name(phrases.name+'.before-candidate-repair')
            if not previous.exists():shutil.copy2(phrases,previous)
            rows=phrases.read_text('utf-8-sig').splitlines()
            phrases.write_text('\n'.join(row for row in rows if tuple(row.split('\t')[:2]) not in STOCK_SHORTCUTS)+'\n',encoding='utf-8')
    (root/'lua').mkdir(exist_ok=True)
    (root/'selection-learning').mkdir(exist_ok=True,mode=0o700)
    for name in ['cloud_phrase.lua','cloud_native_filter.lua','selection_learning.lua']:shutil.copyfile(source/name,root/'lua'/name)
    path.write_text(yaml.safe_dump(settings,allow_unicode=True,sort_keys=False),encoding='utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--rime',type=Path,default=Path.home()/'.local/share/fcitx5/rime');p.add_argument('--dynamic-ranking',action='store_true');a=p.parse_args();configure(a.rime,a.dynamic_ranking)
