"""Version 1 additive phrase exchange. No raw keystrokes, models or opaque databases."""
import re
import unicodedata

MAX_BYTES = 16 * 1024 * 1024
MAX_WORDS = 50000
HEADER = '# Local Chinese dictionary version:1\n'
LEARNING_PREFIX = '# learned\t'

def valid_word(word):
    return 1 <= len(word) <= 32 and all(c == '〇' or unicodedata.name(c, '').startswith(('CJK UNIFIED IDEOGRAPH', 'CJK COMPATIBILITY IDEOGRAPH')) for c in word)

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

def parse_learning(raw, comments=True):
    """Actual per-device selections, independent of imported dictionary weights.

    Comment records keep old v1 word-only clients compatible. Counts are local
    cumulative counters, never the downloaded sum from other devices.
    """
    if len(raw) > MAX_BYTES:
        raise ValueError('learning data too large')
    result = {}
    for line in raw.decode('utf-8-sig', errors='strict').splitlines():
        if comments:
            if not line.startswith(LEARNING_PREFIX):
                continue
            line = line[len(LEARNING_PREFIX):]
        elif not line or line.startswith('#'):
            continue
        fields = line.split('\t')
        if len(fields) != 4:
            raise ValueError('invalid learning record')
        word, code, count, stamp = fields
        if (not valid_word(word) or not re.fullmatch('[a-z]{1,128}', code)
                or not re.fullmatch('[0-9]{1,7}', count) or not 1 <= int(count) <= 1000000
                or not re.fullmatch('[0-9]{1,16}', stamp) or not 0 <= int(stamp) <= 9000000000000000):
            raise ValueError('invalid learning record')
        key = (word, code)
        previous = result.get(key, (0, 0))
        result[key] = (max(previous[0], int(count)), max(previous[1], int(stamp)))
        if len(result) > MAX_WORDS:
            raise ValueError('too many learning records')
    return result

def encode_learning(learned, comments=True):
    prefix = LEARNING_PREFIX if comments else ''
    return ''.join(f'{prefix}{word}\t{code}\t{count}\t{stamp}\n'
                   for (word, code), (count, stamp) in sorted(learned.items())).encode('utf-8')

def packet(words, learned):
    data = encode(words) + encode_learning(learned)
    if len(data) > MAX_BYTES:
        raise ValueError('combined dictionary and learning data too large')
    return data
