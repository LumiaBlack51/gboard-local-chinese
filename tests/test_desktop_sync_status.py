"""A failed exchange must not leave the desktop reporting an old success."""
import contextlib
import io
import json
from pathlib import Path
import ssl
import sys
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'sync'))
import desktop


class DesktopStatusTest(unittest.TestCase):
    def test_failure_keeps_last_confirmed_success_and_never_logs_exception_text(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            (root/'status.json').write_text(json.dumps({'success':True,'at':100,'words':42}))
            secret='sensitive endpoint token or response'
            error=urllib.error.URLError(ssl.SSLEOFError(secret))
            output=io.StringIO()
            with patch.object(sys,'argv',['desktop.py','--config',str(root/'config.json')]),patch.object(desktop,'sync',side_effect=error),patch.object(desktop.time,'time',return_value=200),contextlib.redirect_stdout(output):
                self.assertEqual(desktop.main(),1)
            status=json.loads((root/'status.json').read_text())
            self.assertFalse(status['success'])
            self.assertEqual(status['last_success'],100)
            self.assertEqual(status['at'],200)
            self.assertEqual(status['reason_type'],'SSLEOFError')
            self.assertNotIn(secret,(root/'status.json').read_text()+output.getvalue())
            # A second failure must not mistake the previous attempt for success.
            with patch.object(desktop.time,'time',return_value=300):
                desktop.save_status(root,{'success':False,'error_type':'TimeoutError'})
            self.assertEqual(json.loads((root/'status.json').read_text())['last_success'],100)
            with patch.object(desktop.time,'time',return_value=400):
                desktop.save_status(root,{'success':True,'words':43})
            self.assertEqual(json.loads((root/'status.json').read_text())['last_success'],400)


if __name__=='__main__':unittest.main()
