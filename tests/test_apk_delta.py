"""The build host receives fingerprints, not the complete source APK or a key."""
from pathlib import Path
import sys,tempfile,unittest,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import apk_delta

class ApkDeltaTest(unittest.TestCase):
    def test_indexed_transport_reconstructs_exact_zip_with_new_headers_and_payloads(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);old=p/'old.apk';new=p/'new.apk'
            with zipfile.ZipFile(old,'w') as z:
                z.writestr('model',b'large unchanged data'*10000,compress_type=zipfile.ZIP_STORED)
                z.writestr('code',b'old code',compress_type=zipfile.ZIP_DEFLATED)
                z.writestr('same',b'compressed unchanged'*100,compress_type=zipfile.ZIP_DEFLATED)
            with zipfile.ZipFile(new,'w') as z:
                z.comment=b'new signing/metadata analogue'
                z.writestr('same',b'compressed unchanged'*100,compress_type=zipfile.ZIP_DEFLATED)
                z.writestr('code',b'fixed code',compress_type=zipfile.ZIP_DEFLATED)
                z.writestr('model',b'large unchanged data'*10000,compress_type=zipfile.ZIP_STORED)
                z.writestr('added',b'new entry')
            apk_delta.index(old,p/'index.json');apk_delta.from_index(p/'index.json',new,p/'delta.zip')
            apk_delta.apply(old,p/'delta.zip',p/'recovered.apk')
            self.assertEqual(new.read_bytes(),(p/'recovered.apk').read_bytes())
            self.assertLess((p/'delta.zip').stat().st_size,new.stat().st_size//10)
            wrong=p/'wrong.apk';wrong.write_bytes(old.read_bytes()+b'changed')
            with self.assertRaisesRegex(AssertionError,'Unexpected source APK'):
                apk_delta.apply(wrong,p/'delta.zip',p/'rejected.apk')

if __name__=='__main__':unittest.main()
