import unittest
import tempfile
from pathlib import Path
from importlib.machinery import SourceFileLoader

mod=SourceFileLoader('serveemu_runtime_review',str(Path(__file__).with_name('serveemu_runtime_review.py'))).load_module()

class RuntimeReviewTest(unittest.TestCase):
    def test_empty_database_list_is_kept_without_invented_checks(self):
        with tempfile.TemporaryDirectory() as root:
            result=mod.review(Path(root),['DO_EXPLORATION','INTEL'])
            self.assertEqual(set(result),{'DO_EXPLORATION','INTEL'})
            self.assertEqual(result['INTEL']['total_checks'],0)
            self.assertIn('geen bewijs',mod.to_markdown(result))

    def test_static_factory_inventory_works_without_live_evidence(self):
        root=Path(__file__).resolve().parents[2]
        src=mod.source_profile(root,root/'tools/task-dashboard/catalog.json')
        self.assertGreater(len(src),50)
        self.assertTrue(src['DO_EXPLORATION']['source_found'])
        self.assertGreater(src['DO_EXPLORATION']['source_template_count'],5)
        self.assertGreater(src['TRAINING_TROOPS']['source_match_calls'],0)
        snapshot=mod.review(Path('/nonexistent'),['DO_EXPLORATION'],src)
        self.assertGreater(snapshot['DO_EXPLORATION']['source_match_calls'],0)
        self.assertEqual(snapshot['DO_EXPLORATION']['total_checks'],0)

    def test_serveemu_world_handoffs_no_longer_depend_on_retired_frostguard_button(self):
        root=Path(__file__).resolve().parents[2]
        files=[
            root/'modules/tasks/src/main/java/dev/frostguard/tasks/dailies/IntelligenceRoutine.java',
            root/'modules/tasks/src/main/java/dev/frostguard/tasks/combat/PolarTerrorHuntingRoutine.java',
            root/'modules/tasks/src/main/java/dev/frostguard/tasks/city/PrioritiseFurnaceRoutine.java',
        ]
        for path in files:
            text=path.read_text()
            self.assertIn('SERVE_EMU_WORLD_ANCHOR_CURRENT',text,path.name)
            self.assertNotIn('TemplatesEnum.GAME_HOME_WORLD,',text,path.name)
            self.assertNotIn('locatePattern(GAME_HOME_WORLD,',text,path.name)

    def test_missing_optional_button_is_never_auto_retired(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/'2026-11-16'/'one';p.mkdir(parents=True)
            (p/'run.txt').write_text('task=DO_EXPLORATION\n')
            (p/'recognition-events.csv').write_text('template,method,score,hit\nEXPLORATION_VICTORY,mono,23.4,false\nGAME_HOME_RECONNECT,color,43,false\nSERVE_EMU_EXPLORATION_TITLE,color,39,false\nEXPLORATION_VICTORY,color,99,true\n')
            data=mod.review(Path(root),[])
            by={x['template']:x for x in data['DO_EXPLORATION']['templates']}
            self.assertEqual(by['EXPLORATION_VICTORY']['category'],'observed_positive')
            self.assertEqual(by['GAME_HOME_RECONNECT']['category'],'protected_safety_guard')
            self.assertEqual(by['SERVE_EMU_EXPLORATION_TITLE']['category'],'retired_on_serve_emu')
            self.assertNotIn('EXPLORATION_VICTORY',mod.RUNTIME_RETIRED)
            self.assertNotIn('TRAINING_INFANTRY_T11',mod.RUNTIME_RETIRED)

if __name__=='__main__':unittest.main()
