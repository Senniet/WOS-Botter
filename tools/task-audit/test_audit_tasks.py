import importlib.util
import pathlib
import struct
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).with_name('audit_tasks.py')
spec = importlib.util.spec_from_file_location('audit_tasks', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TaskAuditTest(unittest.TestCase):
    def test_every_builtin_task_has_a_source_factory(self):
        entries = module.catalogue()
        self.assertEqual(61, len(entries))
        self.assertEqual(12, len({x['category'] for x in entries}))
        self.assertEqual(['CUSTOM_TASK'], [x['key'] for x in entries if x['factory'] is None])
        self.assertFalse([x['key'] for x in entries if x['factory'] and not x['source']])

    def test_read_only_screenshot_validates_png_without_sending_input(self):
        png = b'\x89PNG\r\n\x1a\n' + b'\0\0\0\rIHDR' + struct.pack('>II', 720, 1280) + b'\x08\x06\0\0\0'
        class FakeResponse:
            headers = type('Headers', (), {'get_content_type': lambda self: 'image/png'})()
            def read(self, n):
                return png[:n]
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return None
        with tempfile.TemporaryDirectory() as directory, patch.object(module.urllib.request, 'urlopen', return_value=FakeResponse()) as request:
            target = pathlib.Path(directory) / 'readonly.png'
            status = module.inspect_screen('http://example.invalid/api/screenshot', target)
            self.assertIn('720x1280', status)
            self.assertEqual(png, target.read_bytes())
            request.assert_called_once_with('http://example.invalid/api/screenshot', timeout=12)

    def test_report_does_not_claim_live_completion(self):
        entries = module.catalogue()
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / 'inventory.md'
            module.report(entries, 'disabled', output)
            contents = output.read_text()
            self.assertIn('Live E2E not yet proven', contents)
            self.assertIn('The programme is **not completed**', contents)
            self.assertIn('Storehouse Chest', contents)


if __name__ == '__main__':
    unittest.main()
