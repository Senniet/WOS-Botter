"""Restricted QA web bridge: observes only, no emulator tap endpoints."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parent
SRC=(Path.home()/'survivalops-autonomous-qa/controller.py')
if not SRC.is_file():SRC=HERE.parent/'autonomous-qa/controller.py'
sys.path.insert(0,str(SRC.parent))
_spec=importlib.util.spec_from_file_location('survivalops_autoqa_controller',SRC)
controller=importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(controller)


def status():return controller.status()
def enqueue(task,mode='observe'):return {'id':controller.new_run(task,mode)}
def detail(run_id):return controller.details(run_id)
def image(run_id,name):
    if not controller.ID.fullmatch(run_id) or name not in ('before','after','preflight2','candidate','research-preflight') and not __import__('re').fullmatch(r'(?:transition-[0-7]|research-exit-[0-5])',name):
        return None
    item=controller.details(run_id)
    if not item:return None
    path=controller.DATA/'evidence'/run_id/(name+'.png')
    if not path.is_file() or path.stat().st_size>controller.MAX_PNG:return None
    data=path.read_bytes()
    return data if data.startswith(b'\x89PNG\r\n\x1a\n') else None
