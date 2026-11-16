"""Rebuild a reviewable, task-specific workflow inventory from QA catalog.

No visual element is deemed safe for clicking merely because a Java class
mentions its template. This inventory is a starting plan, NOT a verified bot.
"""
from __future__ import annotations
import json
from pathlib import Path
from controller import tasks,scenario

HERE=Path(__file__).resolve().parent

def export(dest=HERE/'workflows.json'):
    plans=[scenario(t['key']) for t in tasks()]
    dest.write_text(json.dumps({'schema':1,'policy':{'single_emulator':True,
        'max_concurrent_tasks':1,'purchases':False,'unsafe_clicks':False,
        'auto_code_edits':False,'auto_qa_signoff':False},
        'workflows':plans},ensure_ascii=False,indent=2)+'\n')
    return len(plans)

if __name__=='__main__':print('Exported',export(),'task plans')
