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
        # The checked-in root dictionary selects the tables shipped on Android.
        if rel.parts[0]=='cn_dicts' or rel.name=='LICENSE':
            dest=data/rel; dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(z.read(name))
manifest=json.loads((data/'source-hashes.json').read_text(encoding='utf8'))
for name,expected in manifest.items():
    if hashlib.sha256((data/name.replace('\\','/')).read_bytes()).hexdigest()!=expected:
        raise ValueError(f'Dictionary source hash mismatch: {name}')
print('native libraries',len(list(out.glob('*.so'))),'dictionary sources',len(manifest))
