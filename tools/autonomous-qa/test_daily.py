from pathlib import Path
import tempfile
import unittest
import daily
import controller

ROOT=Path.home()/'survivalops-diagnostics/taskcoach/2026-11-16/20261116-182434-c40b3e/captures'
class DailyTest(unittest.TestCase):
 def test_real_claim_and_post_reward_screens_are_distinct(self):
  if not ROOT.is_dir():self.skipTest('Real Coach fixtures not mounted')
  start=daily.state(ROOT/'008-daily_mission_claimall_button-hit.jpg')
  rewards=daily.state(ROOT/'010-serve_emu_rewards_banner-hit.jpg')
  post=daily.state(ROOT/'012-daily_mission_claimall_button-miss.jpg')
  self.assertTrue(start['ready_to_claim'],start)
  self.assertFalse(start['reward_visible'])
  self.assertTrue(rewards['reward_visible'],rewards)
  self.assertFalse(rewards['ready_to_claim'])
  self.assertTrue(post['post_claim'],post)
  self.assertFalse(post['ready_to_claim'])
  self.assertTrue(daily.claim_is_stable(start,start))
  self.assertFalse(daily.claim_is_stable(start,post))
  self.assertTrue(daily.reward_is_stable(rewards,rewards))
  self.assertFalse(daily.reward_is_stable(rewards,post))
 def test_no_unauthorized_modes(self):
  self.assertEqual(len(controller.scenario('DAILY_MISSIONS')['reviewed_action_steps']),5)
  with tempfile.TemporaryDirectory() as t:
   from unittest.mock import patch
   with patch.object(controller,'DATA',Path(t)),patch.object(controller,'configuration',return_value={'bound':{'serial':'emulator-5554'}}):
    self.assertRegex(controller.new_run('DAILY_MISSIONS','claim_free'),'[0-9a-f]{16}')
    with self.assertRaises(ValueError):controller.new_run('HERO_RECRUITMENT','claim_free')
if __name__=='__main__':unittest.main()

class ClaimFlowTest(unittest.TestCase):
 def test_rejects_unrecognized_daily_page_without_a_tap(self):
  from unittest.mock import patch
  import tempfile
  with tempfile.TemporaryDirectory() as root:
   d=Path(root)
   def snapshot(rid,name,device):
    p=d/'evidence'/rid;p.mkdir(parents=True,exist_ok=True)
    (p/(name+'.png')).write_bytes(b'not a screenshot')
    return {'name':name,'file':name+'.png'}
   with patch.object(controller,'DATA',d),\
        patch.object(controller,'bound_device',return_value={'serial':'emulator-5554'}),\
        patch.object(controller,'snapshot',side_effect=snapshot),\
        patch.object(controller.recognition,'analyze_file',return_value={'state':'unknown','score':0.1,'matches':{}}),\
        patch.object(daily,'state',return_value={'ready_to_claim':False}),\
        patch.object(controller,'record_board_event'),\
        patch.object(controller,'adb') as adb:
    report=controller.execute({'id':'1234567890abcdef','task':'DAILY_MISSIONS',
                               'device':'emulator-5554','mode':'claim_free'})
    self.assertEqual('blocked',report['outcome'])
    self.assertEqual(0,report['actions'])
    adb.assert_not_called()

 def test_complete_free_claim_tracks_two_confirmed_safe_actions(self):
  from unittest.mock import patch
  import tempfile
  with tempfile.TemporaryDirectory() as root:
   d=Path(root)
   names=[]
   def snapshot(rid,name,device):
    p=d/'evidence'/rid;p.mkdir(parents=True,exist_ok=True)
    (p/(name+'.png')).write_bytes(b'not a screenshot')
    names.append(name)
    return {'name':name,'file':name+'.png'}
   ready={'ready_to_claim':True,'claim_point':[364,1020],
          'reward_visible':False,'post_claim':False,'title':1,'claim':0.9}
   reward={'ready_to_claim':False,'claim_point':None,'reward_visible':True,
           'post_claim':False,'title':0.2,'claim':0.1}
   post={'ready_to_claim':False,'claim_point':None,'reward_visible':False,
         'post_claim':True,'title':0.99,'claim':0.1}
   frames=iter([ready,ready,reward,reward,post])
   with patch.object(controller,'DATA',d),\
        patch.object(controller,'bound_device',return_value={'serial':'emulator-5554'}),\
        patch.object(controller,'snapshot',side_effect=snapshot),\
        patch.object(controller.recognition,'analyze_file',return_value={'state':'unknown','score':0.1,'matches':{}}),\
        patch.object(daily,'state',side_effect=lambda _:next(frames)),\
        patch.object(controller,'record_board_event'),\
        patch.object(controller.time,'sleep'),\
        patch.object(controller,'adb') as adb:
    report=controller.execute({'id':'1234567890abcdef','task':'DAILY_MISSIONS',
                               'device':'emulator-5554','mode':'claim_free'})
    self.assertEqual('free_claim_verified',report['outcome'])
    self.assertEqual(2,report['actions'])
    self.assertEqual(adb.call_count,2)
    self.assertEqual(names[-1],'post-0')
    self.assertEqual(sum(e['type']=='tap' for e in report['events']),2)
