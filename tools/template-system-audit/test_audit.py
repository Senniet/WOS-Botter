"""No game interactions: validate audit classification and private evidence handling."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('template_system_audit', Path(__file__).with_name('audit.py'))
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)

class TemplateAuditTests(unittest.TestCase):
    def test_enum_and_manifest_have_registered_keys(self):
        found = audit.enum_names((audit.ROOT/audit.ENUM).read_text())
        registry = audit.manifest_entries((audit.ROOT/audit.MANIFEST).read_text())
        self.assertGreaterEqual(len(found), 500)
        self.assertEqual(set(found), set(registry))
        self.assertEqual(len(found), len(set(found)))

    def test_png_is_validated_before_image_is_counted(self):
        self.assertIsNone(audit.png_details(b'broken'))
        self.assertIsNone(audit.png_details(audit.PNG_SIG+b'bad'))
        fake = audit.PNG_SIG + b'\0\0\0\x0dIHDR' + (20).to_bytes(4,'big') + (24).to_bytes(4,'big') + b'\x08\x02\x00\x00\x00'+b'\0'*4
        self.assertEqual((20,24),audit.png_details(fake))
        self.assertIsNone(audit.png_details(fake[:16]+b'\0'*8+fake[24:]))

    def test_coach_rows_are_aggregated_without_exposing_account_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            session = Path(tmp)/'2026-11-15'/'sessionid'
            session.mkdir(parents=True)
            (session/'run.txt').write_text('task=DAILY_MISSIONS\nprofile=private\n')
            (session/'recognition-events.csv').write_text(
                'time_utc,template,method,threshold,score,hit,x,y,roi_left,roi_top,roi_right,roi_bottom\n'
                'x,DAILY_MISSION_CLAIM_BUTTON,color,90,75,false,-1,-1,0,0,720,1280\n'
                'x,DAILY_MISSION_CLAIM_BUTTON,mono,90,99,true,50,50,0,0,720,1280\n')
            records, sessions, checks=audit.coach_observations(Path(tmp))
            self.assertEqual((1,2),(sessions,checks))
            self.assertEqual(1,records['DAILY_MISSION_CLAIM_BUTTON']['hits'])
            self.assertEqual(['DAILY_MISSIONS'],records['DAILY_MISSION_CLAIM_BUTTON']['tasks'])
            self.assertNotIn('private', str(records))

    def test_upstream_blob_hash_matches_git_content_semantics(self):
        self.assertEqual('e69de29bb2d1d6434b8b29ae775ad8c2e48c5391', audit.git_hash(b''))
        self.assertNotEqual(audit.git_hash(b'a'),audit.git_hash(b'b'))

if __name__ == '__main__':unittest.main()
