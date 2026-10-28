"""Real-engine regression for imported rare characters and durable learning.

All writes use an isolated Rime directory; never opens the live user database.
"""
from pathlib import Path
import hashlib
import json
import shutil
import string
import time
import yaml
from rime_host import RimeHost

ROOT = Path(__file__).resolve().parents[1]


def configure(user, config):
    for name in ('cloud_phrase.lua', 'cloud_native_filter.lua'):
        shutil.copyfile(ROOT / 'sync' / name, user / 'lua' / name)


def rank(engine, code, word):
    return next(i for i, row in enumerate(engine.candidates(code, 200)) if row[0] == word)


def main():
    started = time.monotonic()
    results = {}
    with RimeHost(learning=True, configure=configure) as engine:
        # Inject the exact regression independently of the user's synced data,
        # including the maximum permitted server count.
        cloud = engine.user / 'cloud-phrases.tsv'
        original = cloud.read_bytes()
        fixtures = '凪\tzhi\t1000000\n徳\tde\t1000000\n禹珂凌璐彦寿祠\tyukelingluyanshouci\t1000000\n'
        cloud.write_bytes(b'# snapshot regression-1\n' + original + fixtures.encode())
        codes = set(string.ascii_lowercase)
        for line in original.decode().splitlines():
            fields = line.split('\t')
            if len(fields) == 3 and len(fields[0]) == 1:
                codes.add(fields[1])
        codes.update(('zhi', 'de', 'shi', 'nihao', 'mingtian', 'lv', 'nv', 'zh', 'ch', 'sh'))
        for code in sorted(codes):
            rows = engine.candidates(code, 80, details=True)
            assert len({row[0] for row in rows}) == len(rows), ('duplicate', code)
            if rows and rows[0][1].startswith('cloud_phrase|'):
                assert not any(row[1] and int(row[1].split('|')[2]) == len(code)
                               and not row[1].startswith('cloud_phrase|') for row in rows), ('cloud masks native', code)
        results['inputs_checked'] = len(codes)
        for code, word in [('zhi', '凪'), ('de', '徳')]:
            assert rank(engine, code, word) >= 5, (code, 'static rare import masks native page')
        results['rare_imports_do_not_pin_first_page'] = True

        # Native learning must win over an unselected import on every query.
        for code, word in [('zhi', '值'), ('de', '得'), ('m', '米')]:
            before = rank(engine, code, word)
            for _ in range(4):
                assert engine.select(rank(engine, code, word)) == word
            assert rank(engine, code, word) == 0, (code, 'native learning failed')
            results[code] = {'before_rank': before + 1, 'after_rank': 1}

        # New shared phrases also become actual Rime user phrases.
        for code, word in [('zhi', '凪'), ('yukelingluyanshouci', '禹珂凌璐彦寿祠')]:
            for _ in range(12):
                assert engine.select(rank(engine, code, word)) == word
            rows = engine.candidates(code, 30)
            assert rows[0] == (word, 'user_phrase'), (code, rows[:2])
        for _ in range(16):
            assert engine.select(rank(engine, 'zhi', '值')) == '值'
        assert engine.candidates('zhi')[0] == ('值', 'user_phrase')
        results['new_shared_words_use_native_learning'] = True
        results['later_native_choice_replaces_cloud_choice'] = True

        # File refreshes must never restore the old pinned ranking.
        cloud.write_bytes(b'# snapshot regression-2\n' + original + fixtures.encode())
        assert engine.candidates('zhi')[0] == ('值', 'user_phrase')
        engine.restart()
        assert engine.candidates('zhi')[0] == ('值', 'user_phrase')
        assert engine.candidates('yukelingluyanshouci')[0] == ('禹珂凌璐彦寿祠', 'user_phrase')
        results['learning_survives_sync_and_engine_restart'] = True
        engine.r.RimeSetOption(engine.session, b'incognito', 1)
        assert all(kind != 'cloud_phrase' for _, kind in engine.candidates('de'))
        results['private_mode_excludes_cloud'] = True

        schema = engine.user / 'build/rime_ice.schema.yaml'
        config = yaml.safe_load(schema.read_text())
        config['engine']['translators'] = ['lua_translator@*cloud_phrase']
        schema.write_text(yaml.safe_dump(config, allow_unicode=True))
        engine.restart()
        assert engine.candidates('zhi')[0] == ('凪', 'cloud_phrase'), 'cloud-only fallback lost'
    results['shared_word_available_without_native_candidates'] = True

    results.update(passed=True, live_user_database_opened=False,
                   elapsed_seconds=round(time.monotonic()-started, 2),
                   sources={name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                            for name in ['sync/cloud_phrase.lua', 'sync/cloud_native_filter.lua']})
    (ROOT / 'tests/results/cloud-learning-v1.3.6.json').write_text(json.dumps(results, indent=2)+'\n')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
