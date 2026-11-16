"""Propose, never activate, a screenshot-derived candidate UI template.

If both independently captured screens show an unknown UI, save a small
header crop for a reviewer. No automatic change to TemplatesEnum or PNG
resources is permitted; true/false samples are required before promotion.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import subprocess

from recognition import grayscale,normalized_match

# Central modal/header panel from recorded 720x1280 game screenshots.
CROP=(75,195,580,125)

def propose_unknown(folder:Path,first:Path,second:Path)->dict|None:
    if not first.is_file() or not second.is_file():return None
    folder.mkdir(parents=True,exist_ok=True)
    x,y,w,h=CROP
    candidate=folder/'candidate.png'
    try:
        subprocess.run(['/usr/bin/magick',str(second),'-crop',f'{w}x{h}+{x}+{y}',
                        '+repage','-strip',str(candidate)],check=True,capture_output=True,timeout=12)
        match=normalized_match(grayscale(first),grayscale(candidate),(65,180,665,345))
        if match['score']<0.80:
            candidate.unlink(missing_ok=True)
            return None
        info={'file':'candidate.png','status':'pending_manual_review',
              'source':'unknown-screen-header','source_shots':[first.name,second.name],
              'stability_score':match['score'],
              'requirements':['one independently confirmed positive frame',
                              'one negative (different-screen) frame',
                              'reviewed visual pre/postcondition before any tap'],
              'sha256':hashlib.sha256(candidate.read_bytes()).hexdigest()}
        (folder/'candidate.json').write_text(json.dumps(info,indent=2))
        return info
    except (OSError,ValueError,subprocess.CalledProcessError):
        candidate.unlink(missing_ok=True)
        return None
