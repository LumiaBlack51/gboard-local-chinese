"""Version 1 additive phrase exchange. No raw keystrokes, models or opaque databases."""
import re
import unicodedata

MAX_BYTES = 16 * 1024 * 1024
MAX_WORDS = 50000
HEADER = '# Local Chinese dictionary version:1\n'

def valid_word(word):
    return 1 <= len(word) <= 32 and all(unicodedata.name(c, '').startswith(('CJK UNIFIED IDEOGRAPH', 'CJK COMPATIBILITY IDEOGRAPH')) for c in word)

def parse(raw):
    if len(raw) > MAX_BYTES:
        raise ValueError('dictionary too large')
    result = {}
    for line in raw.decode('utf-8-sig', errors='strict').splitlines():
        if not line or line.startswith('#'):
            continue
        fields = line.split('\t')
        if len(fields) != 3:
            raise ValueError('invalid record')
        word, code, weight = fields
        if not valid_word(word) or not re.fullmatch('[a-z]{1,128}', code) or not re.fullmatch('[0-9]{1,7}', weight) or not 1 <= int(weight) <= 1000000:
            raise ValueError('invalid record')
        key = (word, code)
        result[key] = max(result.get(key, 0), int(weight))
        if len(result) > MAX_WORDS:
            raise ValueError('too many records')
    return result

def encode(words):
    return (HEADER + ''.join(f'{word}\t{code}\t{weight}\n' for (word, code), weight in sorted(words.items()))).encode('utf-8')
