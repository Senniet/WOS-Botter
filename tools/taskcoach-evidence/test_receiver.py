"""Tests for the private HTTPS receiver's bounded ZIP extraction."""
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from taskcoach_receiver import UploadRejected, store_taskcoach_archive

RUN = "20261115-163819-1ij1e19"


def bundle(extra=None, manifest_session=RUN):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('summary.txt', 'scheduler_outcome=FAILED\n')
        archive.writestr('recognition-events.csv', 'template,score\nX,80\n')
        archive.writestr('upload-manifest.json', json.dumps({'schema':1, 'session_id':manifest_session}))
        for key, value in (extra or {}).items():
            archive.writestr(key, value)
    return output.getvalue()


class ReceiverTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self): self.tmp.cleanup()

    def test_stores_named_session_and_is_idempotent(self):
        raw = bundle()
        status, path = store_taskcoach_archive(self.root, RUN, raw)
        self.assertEqual(status, 201)
        self.assertEqual((self.root / path / 'summary.txt').read_text(), 'scheduler_outcome=FAILED\n')
        self.assertTrue((self.root / path / 'receipt.json').exists())
        self.assertEqual(store_taskcoach_archive(self.root, RUN, raw)[0], 200)

    def test_rejects_path_traversal_and_symlink(self):
        for bad in ['../private/upload-token', '/etc/passwd', 'captures/../../private.png', 'evil.jar']:
            with self.subTest(bad=bad):
                with self.assertRaises(UploadRejected):
                    store_taskcoach_archive(self.root, RUN, bundle({bad:'bad'}))
        with self.assertRaises(UploadRejected):
            store_taskcoach_archive(self.root, RUN, bundle({'captures/bad.png':'not-png'}))

    def test_rejects_session_spoofing_and_duplicate_different_payload(self):
        with self.assertRaises(UploadRejected):
            store_taskcoach_archive(self.root, '../../private', bundle())
        with self.assertRaises(UploadRejected):
            store_taskcoach_archive(self.root, RUN, bundle(manifest_session='20261115-163819-other'))
        store_taskcoach_archive(self.root, RUN, bundle())
        with self.assertRaises(UploadRejected) as case:
            store_taskcoach_archive(self.root, RUN, bundle({'run.txt': 'changed'}))
        self.assertEqual(case.exception.status, 409)

    def test_accepts_images_and_rejects_duplicate_paths(self):
        with self.assertRaises(UploadRejected):
            store_taskcoach_archive(self.root, RUN, bundle({'captures/test.png': b'not a PNG'}))
        fake_png = bytes.fromhex('89504e470d0a1a0a') + b'fake-beyond-header'
        _, path = store_taskcoach_archive(self.root, RUN, bundle({'captures/test.png': fake_png}))
        self.assertEqual((self.root/path/'captures/test.png').read_bytes(), fake_png)
        out = io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:
            z.writestr('summary.txt','first')
            z.writestr('summary.txt','second')
            z.writestr('recognition-events.csv','x')
            z.writestr('upload-manifest.json',json.dumps({'schema':1,'session_id':RUN}))
        with self.assertRaises(UploadRejected):
            store_taskcoach_archive(self.root,'20261115-163819-extra',out.getvalue())


if __name__ == '__main__': unittest.main()
