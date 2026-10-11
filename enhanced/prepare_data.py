from pathlib import Path
import zipfile, hashlib, json
ROOT=Path(__file__).resolve().parent.parent
out=ROOT/'enhanced'/'native'/'lib'/'arm64-v8a'
out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(ROOT/'org.fcitx.fcitx5.android-0.1.3-0-g048f581c-arm64-v8a-release.apk') as z:
    for name in z.namelist():
        if name.startswith('lib/arm64-v8a/'):
            (out/Path(name).name).write_bytes(z.read(name))
with zipfile.ZipFile(ROOT/'org.fcitx.fcitx5.android.plugin.rime-0.1.3-0-g048f581c-arm64-v8a-release.apk') as z:
    (out/'librime.so').write_bytes(z.read('lib/arm64-v8a/librime.so'))

data=ROOT/'enhanced'/'rime-data'
data.mkdir(exist_ok=True)
with zipfile.ZipFile(ROOT/'enhanced'/'rime-ice.zip') as z:
    for name in z.namelist():
        if name.endswith('/'):
            continue
        rel=Path(*Path(name).parts[1:])
        if rel.parts[0]=='cn_dicts' or rel.name in ('rime_ice.dict.yaml','LICENSE'):
            dest=data/rel; dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(z.read(name))
manifest={str(p.relative_to(data)):hashlib.sha256(p.read_bytes()).hexdigest() for p in data.rglob('*') if p.is_file()}
(data/'source-hashes.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
print('native libraries',len(list(out.glob('*.so'))),'dictionary sources',len(manifest))
