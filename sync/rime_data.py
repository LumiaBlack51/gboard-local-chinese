"""Portable Rime text snapshots and local aggregates. Never open live LevelDB."""
import hashlib
import os
from pathlib import Path
import re
import tempfile
from common import parse_learning, valid_word, MAX_BYTES

def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix='.'+path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def snapshot(path, data):
    atomic(path, ('# snapshot '+hashlib.sha256(data).hexdigest()+'\n').encode()+data)

def rime_learning(root):
    result = {}
    installation = root/'installation.yaml'
    match = re.search(r'''^installation_id:\s*["']?([a-zA-Z0-9][a-zA-Z0-9_.-]{0,127})["']?\s*$''', installation.read_text('utf-8'), re.M) if installation.exists() else None
    if not match: return result
    for path in (root/'sync'/match[1]).glob('rime_ice.userdb.txt'):
        if path.stat().st_size > MAX_BYTES: raise ValueError('Rime snapshot too large')
        for line in path.read_text('utf-8').splitlines():
            fields = line.split('\t')
            if len(fields) != 3 or line.startswith('#'): continue
            code, word, stats = fields
            count = re.search(r'(?:^| )c=([0-9]+)(?: |$)', stats)
            code = re.sub(r"[\s'’]", '', code.lower()).replace('ü', 'v').replace('u:', 'v')
            if count and int(count[1]) > 0 and valid_word(word) and re.fullmatch('[a-z]{1,128}', code):
                key = (word, code)
                result[key] = (max(result.get(key, (0,0))[0], min(1000000, int(count[1]))), 0)
    return result

def local_learning(root, native=None):
    result = dict(rime_learning(root) if native is None else native)
    paths = set((root/'selection-learning').glob('*.tsv'))
    # Windows rename recovery files contain only the current/previous aggregate.
    paths.update((root/'selection-learning').glob('*.tsv.new'))
    paths.update((root/'selection-learning').glob('*.tsv.old'))
    for path in sorted(paths):
        code = path.name.split('.')[0]
        if not re.fullmatch('[a-z]{1,128}', code): continue
        try: raw = path.read_bytes()
        except FileNotFoundError: continue
        for key, (count, stamp) in parse_learning(raw, comments=False).items():
            if key[1] != code: raise ValueError('learning input does not match file')
            previous = result.get(key, (0,0))
            result[key] = (max(previous[0], count), max(previous[1], stamp))
    return result

def local_words(root, native=None):
    result = {key:min(1000000,100+value[0]) for key,value in (rime_learning(root) if native is None else native).items()}
    custom = root/'custom_phrase.txt'
    if custom.exists():
        for line in custom.read_text('utf-8-sig').splitlines():
            if line.startswith('#'): continue
            fields = line.split('\t')
            if len(fields) not in (2,3): continue
            word, code = fields[:2]
            if valid_word(word) and re.fullmatch('[a-z]{1,128}', code): result[(word,code)] = max(result.get((word,code),0),100)
    return result
