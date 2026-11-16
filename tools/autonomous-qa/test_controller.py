"""Offline safety and deterministic workflow regression tests."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import controller
import recognition
import numpy as np

class ControllerTest(unittest.TestCase):
    def test_full_catalog_has_isolated_workflows_and_no_default_taps(self):
        plans=[controller.scenario(t['key']) for t in controller.tasks()]
        self.assertGreaterEqual(len(plans),60)
        self.assertEqual(len(plans),len({x['task'] for x in plans}))
        for plan in plans:
            self.assertEqual(plan['mode'],'observe')
            self.assertTrue(plan['requires_review_for_actions'])
        self.assertEqual(controller.scenario('DO_EXPLORATION')['reviewed_action_steps'],
                         ['verify_city_world_twice','tap_exploration_entry_once',
                          'verify_exploration_title_and_explore_button'])
        self.assertEqual(controller.scenario('TRAINING_TROOPS')['reviewed_action_steps'],[])

    def test_no_anonymous_or_non_reviewed_actions(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(controller,'DATA',Path(d)),\
                mock.patch.object(controller,'configuration',return_value={
                    'bound':{'serial':'emulator-5554'}}):
            with self.assertRaises(ValueError):controller.new_run('DAILY_MISSIONS','navigate')
            with self.assertRaises(ValueError):controller.new_run('DO_EXPLORATION','tap')
            with self.assertRaises(ValueError):controller.new_run('../evil','observe')
            run=controller.new_run('DO_EXPLORATION','navigate')
            self.assertRegex(run,'^[0-9a-f]{16}$')
            self.assertEqual(controller.status()['runs'][0]['state'],'queued')

    def test_pin_prevents_silent_emulator_switch_or_fingerprint_change(self):
        pinned={'serial':'emulator-5554','fingerprint_hash':'abc','size':'720x1280',
                'density':'320','api':'36'}
        with mock.patch.object(controller,'configuration',return_value={'bound':pinned}),\
                mock.patch.object(controller,'devices',return_value=[
                    {'serial':'emulator-5584','ready':True,**{k:v for k,v in pinned.items() if k!='serial'}}]):
            with self.assertRaisesRegex(RuntimeError,'failover'):controller.bound_device()
        changed={**pinned,'ready':True,'fingerprint_hash':'changed'}
        with mock.patch.object(controller,'configuration',return_value={'bound':pinned}),\
                mock.patch.object(controller,'devices',return_value=[changed]):
            with self.assertRaisesRegex(RuntimeError,'fingerprint'):controller.bound_device()

    def test_executor_refuses_unknown_root_without_any_action(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(controller,'DATA',Path(d)),\
                mock.patch.object(controller,'bound_device',return_value={'serial':'emulator-5554'}),\
                mock.patch.object(controller,'snapshot',return_value={'file':'before.png','name':'before'}),\
                mock.patch.object(recognition,'analyze_file',return_value={
                    'state':'unknown','score':0.12,'matches':{}}),\
                mock.patch.object(controller,'adb') as adb,                mock.patch.object(controller,'record_board_event'):
            run={'id':'abcd0123456789ef','task':'DO_EXPLORATION','device':'emulator-5554','mode':'navigate'}
            report=controller.execute(run)
            self.assertEqual(report['outcome'],'blocked')
            self.assertEqual(report['actions'],0)
            adb.assert_not_called()

    def test_uniform_ui_region_cannot_generate_a_false_100_percent_match(self):
        rng=np.random.default_rng(31)
        flat=np.full((140,190),41,dtype=np.uint8)
        textured=rng.integers(20,240,size=(36,49),dtype=np.uint8)
        result=recognition.normalized_match(flat,textured,(10,10,180,130))
        self.assertLess(result['score'],0.1)

    def test_visual_correlation_prefers_identical_template(self):
        rng=np.random.default_rng(14)
        image=rng.integers(0,255,size=(85,115),dtype=np.uint8)
        patch=image[30:49,50:73].copy()
        match=recognition.normalized_match(image,patch,(20,10,110,82))
        self.assertGreater(match['score'],0.75)
        self.assertLessEqual(match['score'],1.0)

if __name__=='__main__':unittest.main()
