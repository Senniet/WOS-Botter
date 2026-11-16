from pathlib import Path
import json
import tempfile
import unittest
from unittest.mock import patch
import inspector

class InspectorTests(unittest.TestCase):
 def test_real_training_flow_and_coach_evidence(self):
  flow=inspector.java_flow('TRAINING_TROOPS')
  self.assertEqual('TrainingRoutine',flow['factory'])
  self.assertGreaterEqual(len(flow['annotated_stages']),10)
  self.assertTrue(any(s['method']=='collectCompletedTraining' for s in flow['annotated_stages']))
  self.assertTrue(any(s['method']=='trainAllReadyQueuesFlow' for s in flow['steps']))
  self.assertGreaterEqual(len(flow['templates']),10)
  run=inspector.run_detail('20261116-213911-1wbczde')
  if not run:self.skipTest('Live Coach archive not mounted')
  self.assertFalse(run['completion_proved'])
  self.assertEqual('TRAINING_TROOPS',run['task'])
  self.assertGreater(len(run['records']),15)
  self.assertTrue(any(e['name']=='TRAINING_TRAIN_BUTTON' and e['hits']>0 for e in run['templates']))
  self.assertTrue(any(e['name']=='REPLENISH_ALL_BUTTON' and e['hits']==0 for e in run['templates']))

 def test_template_preview_allowlist_and_reversible_review(self):
  name='TRAINING_TRAIN_BUTTON'
  self.assertTrue(inspector.template_png(name).startswith(b'\x89PNG'))
  self.assertIsNone(inspector.template_png('../../etc/passwd'))
  self.assertIsNone(inspector.template_png('MISSING_TEMPLATE'))
  with tempfile.TemporaryDirectory() as tmp,patch.object(inspector,'DB',Path(tmp)/'reviews.json'):
   self.assertTrue(inspector.review_update(name,'hidden')['production_unchanged'])
   self.assertEqual('hidden',inspector.review()[name])
   self.assertEqual('removal_review',inspector.review_update(name,'removal_review')['review_state'])
   self.assertEqual('active_in_catalog',inspector.review_update(name,'restore')['review_state'])
   self.assertNotIn(name,inspector.review())
  self.assertIsNotNone(inspector.template_png(name))

 def test_extra_pngs_only_from_official_audit_allowlist(self):
  files=inspector.unregistered_assets()
  if files:self.assertTrue(inspector.extra_png(files[0]).startswith(b'\x89PNG'))
  self.assertIsNone(inspector.extra_png('../../etc/passwd'))
  self.assertIsNone(inspector.extra_png('modules/vision/src/main/resources/templates/../manifest.json'))

 def test_run_image_cannot_escape_capture_directory(self):
  run='20261116-213911-1wbczde'
  self.assertIsNone(inspector.coach_image(run,'../../secret'))
  self.assertIsNone(inspector.coach_image('../20261116-213911-1wbczde','001-anything-hit.jpg'))
  self.assertIsNone(inspector.run_detail('../../etc/passwd'))

 def test_ocr_diagnostics_distinguish_blank_rejection_from_engine_failure(self):
  with tempfile.TemporaryDirectory() as tmp,patch.object(inspector,'INBOX',Path(tmp)):
   p=Path(tmp)/'2026-11-16';p.mkdir()
   (p/'evidence.json').write_text(json.dumps({'log_tail': '\n'.join([
      'OCR not accepted: status=FAILED attempts=1 readableText=false transportFailure=none',
      'OCR not accepted: status=REJECTED attempts=1 readableText=true transportFailure=none',
      'OCR trial 1 failed: OcrException: missing tessdata',
      'OCR not accepted: status=FAILED attempts=1 readableText=false transportFailure=OcrException: missing tessdata',
      'OCR trial 1 failed: RuntimeException: ADB screencap failed',
      'OCR not accepted: status=FAILED attempts=1 readableText=false transportFailure=RuntimeException: ADB screencap failed'])}))
   result=inspector.ocr_health()
   for k in ('empty_text','text_rejected','engine_exception','capture_or_transport_error','adb_screencap_failures'):
    self.assertEqual(1,result['counts'][k],k)
   self.assertEqual(2,result['counts']['trial_exceptions'])

if __name__=='__main__':unittest.main()
