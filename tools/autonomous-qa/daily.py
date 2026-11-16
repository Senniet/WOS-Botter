"""Free Daily Missions Claim All: task-scoped visual pre/postconditions.

Does not collect paid rewards, 160-point chests, claim individual missions,
perform training/recruiting or navigate from unrelated pages.
"""
from pathlib import Path
from recognition import REPO,grayscale,normalized_match,template

FIXTURES=REPO/'tools/autonomous-qa/fixtures'
TITLE=FIXTURES/'daily-title-current.png'
CLAIM=FIXTURES/'daily-claimall-current.png'
REWARD_HINT=FIXTURES/'rewards-tap-hint-current.png'

def state(path:Path)->dict:
    img=grayscale(path)
    title=normalized_match(img,grayscale(TITLE),(120,70,600,195))
    claim=normalized_match(img,grayscale(CLAIM),(195,920,530,1090))
    banner=normalized_match(img,template('SERVE_EMU_REWARDS_BANNER'),(0,160,720,550))
    hint=normalized_match(img,grayscale(REWARD_HINT),(120,1040,620,1250))
    return {'title':title['score'],'claim':claim['score'],'claim_point':claim['point'],
            'reward_banner':banner['score'],'reward_hint':hint['score'],
            'ready_to_claim':title['score']>=0.93 and claim['score']>=0.82 and
                     claim['point'] is not None and
                     245<=claim['point'][0]<=460 and 970<=claim['point'][1]<=1065,
            'reward_visible':banner['score']>=0.85 and hint['score']>=0.88,
            'post_claim':title['score']>=0.93 and claim['score']<0.55}

def claim_is_stable(a:dict,b:dict)->bool:
    if not a['ready_to_claim'] or not b['ready_to_claim']:return False
    return max(abs(a['claim_point'][i]-b['claim_point'][i]) for i in (0,1))<=10

def reward_is_stable(a:dict,b:dict)->bool:
    return a['reward_visible'] and b['reward_visible']
