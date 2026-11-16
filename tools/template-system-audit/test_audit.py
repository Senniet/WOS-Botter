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

    def test_task_method_profile_distinguishes_modes_from_emulator_instances(self):
        with tempfile.TemporaryDirectory() as tmp:
            session=Path(tmp)/'2026-11-16'/'private-session'
            session.mkdir(parents=True)
            (session/'run.txt').write_text('task=TRAINING_TROOPS\nprofile=secret\n')
            (session/'recognition-events.csv').write_text(
                'template,method,hit\nGAME_HOME_RECONNECT,color,false\n'
                'GAME_HOME_RECONNECT,multiscale,false\n'
                'GAME_HOME_RECONNECT,mono,false\n'
                'TRAINING_TRAIN_BUTTON,color,true\n')
            profiles=audit.task_method_profiles(Path(tmp))
            data=profiles['TRAINING_TROOPS']
            self.assertEqual(1,data['sessions'])
            self.assertEqual(4,data['observations'])
            self.assertEqual({'color':2,'mono':1,'multiscale':1},data['method_counts'])
            self.assertEqual('GAME_HOME_RECONNECT',data['most_checked'][0]['template'])
            self.assertNotIn('secret',str(profiles))

    def test_outdated_exploration_and_income_templates_stay_out_of_hot_paths(self):
        # 20 real Task Coach sessions in Nov 2026 show the active 720x1280
        # NOV_2026 titles with 100% matches, old same-screen alternatives
        # with 20-50% misses. Do not reintroduce the retry cascade.
        base=audit.ROOT/'modules/tasks/src/main/java/dev/frostguard/tasks'
        files=[base/'exploration/DoExplorationRoutine.java',
               base/'exploration/ExplorationRoutine.java',
               base/'lifecycle/KnownGameScreenResume.java']
        for path in files:
            content=path.read_text(encoding='utf-8')
            self.assertIn('TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026',
                          content, path.name)
            for legacy in ('SERVE_EMU_EXPLORATION_TITLE', 'EXPLORATION_SCREEN_TITLE',
                           'SERVE_EMU_IDLE_INCOME_TITLE', 'EXPLORATION_IDLE_INCOME_TITLE'):
                self.assertNotRegex(content,
                    r'TemplatesEnum\.'+legacy+r'\b',
                    'Old same-screen fallback was reintroduced in '+path.name)
        # This change MUST NOT remove global safety checks for absent screens.
        navigation=(audit.ROOT/'modules/automation/src/main/java/dev/frostguard/engine/helper/NavigationHelper.java').read_text()
        self.assertIn('TemplatesEnum.GAME_HOME_RECONNECT',navigation)
        self.assertIn('dismissVerifiedWelcomeBack',navigation)

    def test_all_runtime_decisions_are_accounted_for_and_no_old_world_checks_remain(self):
        result=audit.audit(audit.ROOT,Path('/nonexistent'))
        template_by_name={entry['name']:entry for entry in result['templates']}
        self.assertEqual(523,len(template_by_name))
        self.assertEqual(5,result['summary']['retired_from_runtime'])
        self.assertEqual(7,result['summary']['protected_safety_guards'])
        self.assertEqual(523,sum(result['summary'][name] for name in (
            'retired_from_runtime','protected_safety_guards',
            'active_source_referenced','registered_unused_review')))
        for name in audit.json.loads((audit.ROOT/audit.DECISIONS).read_text())['retired']:
            self.assertEqual('retired',template_by_name[name]['runtime_role'])
            self.assertEqual([],template_by_name[name]['source_uses'])
            self.assertTrue(template_by_name[name]['decision_note'])
        for name in ('GAME_HOME_RECONNECT','GAME_START_WELCOME_BACK_TITLE'):
            self.assertEqual('protected_guard',template_by_name[name]['runtime_role'])
        assertSource=[
            'modules/automation/src/main/java/dev/frostguard/engine/helper/NavigationHelper.java',
            'modules/automation/src/main/java/dev/frostguard/engine/helper/StaminaHelper.java',
            'modules/automation/src/main/java/dev/frostguard/engine/helper/SidebarNavigator.java',
            'modules/automation/src/main/java/dev/frostguard/engine/helper/DeploymentHelper.java',
            'modules/tasks/src/main/java/dev/frostguard/tasks/lifecycle/InitializeRoutine.java']
        import re
        for name in assertSource:
            source=(audit.ROOT/name).read_text()
            self.assertIsNone(re.search(r'TemplatesEnum\.SERVE_EMU_WORLD_ANCHOR\b',source),name)
            self.assertIn('TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT',source)

    def test_retired_reintroduced_fails_audit_instead_of_silent_mislabel(self):
        # Audit's entry point must guard against accidental reactivation after
        # a code regression. Live screenshots have proven the replacement.
        source=(audit.ROOT/'tools/template-system-audit/audit.py').read_text()
        self.assertIn('Retired template has become active again',source)

    def test_upstream_blob_hash_matches_git_content_semantics(self):
        self.assertEqual('e69de29bb2d1d6434b8b29ae775ad8c2e48c5391', audit.git_hash(b''))
        self.assertNotEqual(audit.git_hash(b'a'),audit.git_hash(b'b'))

if __name__ == '__main__':unittest.main()
