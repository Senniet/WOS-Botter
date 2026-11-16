"""Read-only pixel matching of current game templates with source screenshots.
Uses numpy and ImageMagick already available on the QA host; no remote LLM/API.
Recognition is NOT an authorization to tap without separate verified workflow.
"""
from __future__ import annotations
from functools import lru_cache
import math
import os
from pathlib import Path
import re
import subprocess
import numpy as np

_repo_root=Path(__file__).resolve().parents[2]
REPO=Path(os.environ.get('SURVIVALOPS_REPO',
    str(_repo_root if (_repo_root/'modules').is_dir() else Path.home()/'projects/WOS-Botter')))
RESOURCES=REPO/'modules/vision/src/main/resources'
PROPERTIES=REPO/'modules/api/src/main/resources/config/templates.properties'
# Explicit local current 2026 templates; deliberately no global legacy sweep.
STATES={
 'city':('SERVE_EMU_CITY_ANCHOR',(575,1125,720,1280)),
 'world':('SERVE_EMU_WORLD_ANCHOR_CURRENT',(575,1125,720,1280)),
 'exploration':('SERVE_EMU_EXPLORATION_TITLE_NOV_2026',(65,0,660,230)),
 'daily_missions':('DAILY_MISSION_SCREEN_TITLE',(110,65,625,240)),
}

@lru_cache(maxsize=1)
def template_paths()->dict:
    names={}
    for line in PROPERTIES.read_text().splitlines():
        if line.startswith('#') or '=' not in line:continue
        k,v=line.split('=',1)
        if not re.fullmatch('[A-Z][A-Z0-9_]+',k):continue
        p=RESOURCES/v.lstrip('/')
        if p.is_file():names[k]=p
    return names


def grayscale(path:Path)->np.ndarray:
    head=subprocess.run(['/usr/bin/magick','identify','-format','%w %h',str(path)],
                        check=True,capture_output=True,timeout=10).stdout.decode()
    width,height=[int(x) for x in head.split()]
    if width>1600 or height>1800 or width<10 or height<10:raise ValueError('Unsupported image size')
    raw=subprocess.run(['/usr/bin/magick',str(path),'-colorspace','Gray','-depth','8','gray:-'],
                       check=True,capture_output=True,timeout=12).stdout
    if len(raw)!=width*height:raise ValueError('Unexpected grayscale bytes')
    return np.frombuffer(raw,dtype=np.uint8).reshape(height,width)


@lru_cache(maxsize=24)
def template(template_id:str)->np.ndarray:
    p=template_paths().get(template_id)
    if p is None:raise ValueError('Unknown template '+template_id)
    return grayscale(p)


def normalized_match(image:np.ndarray,patch:np.ndarray,roi:tuple[int,int,int,int])->dict:
    x0,y0,x1,y1=roi
    x0=max(0,x0);y0=max(0,y0)
    x1=min(image.shape[1],x1);y1=min(image.shape[0],y1)
    region=image[y0:y1,x0:x1]
    # Downsampling uses shared full-frame pixel coordinates. It is intentionally
    # a coarse diagnostic matcher, never permitted to authorize game clicks.
    scale=2
    region=region[::scale,::scale].astype(np.float32)
    small=patch[::scale,::scale].astype(np.float32)
    ih,iw=region.shape;ph,pw=small.shape
    if ph>ih or pw>iw:return {'score':0.,'point':None,'reason':'template_outside_roi'}
    centered=small-small.mean()
    energy=float(np.sum(centered*centered))
    if energy<1e-5:return {'score':0.,'point':None,'reason':'uniform_template'}
    # Valid normalized cross-correlation, computed without materializing a
    # potentially huge 4-D sliding-window tensor.
    fftshape=(ih+ph-1,iw+pw-1)
    flipped=centered[::-1,::-1]
    numerator=np.fft.irfft2(np.fft.rfft2(region,s=fftshape)*
                              np.fft.rfft2(flipped,s=fftshape),s=fftshape)
    numerator=numerator[ph-1:ih,pw-1:iw]
    padded=np.pad(region.astype(np.float64),((1,0),(1,0)))
    integral=padded.cumsum(axis=0).cumsum(axis=1)
    squared=np.pad(region.astype(np.float64)**2,((1,0),(1,0))).cumsum(axis=0).cumsum(axis=1)
    sums=integral[ph:,pw:]-integral[:-ph,pw:]-integral[ph:,:-pw]+integral[:-ph,:-pw]
    sq=squared[ph:,pw:]-squared[:-ph,pw:]-squared[ph:,:-pw]+squared[:-ph,:-pw]
    variance=np.maximum(sq-sums*sums/(ph*pw),1e-8)
    ncc=numerator/np.sqrt(variance*energy)
    # A flat UI background has zero local variance. Floating-point FFT noise
    # divided by epsilon could otherwise yield a spurious 100% match.
    # Reject near-uniform windows BEFORE considering the highest score.
    ncc[variance < (ph*pw)*64.0]=-1.0
    ncc[~np.isfinite(ncc)]=-1.0
    y,x=np.unravel_index(np.argmax(ncc),ncc.shape)
    return {'score':round(float(np.clip(ncc[y,x],-1.,1.)),4),
            'point':[int(x0+x*scale+patch.shape[1]//2),
                     int(y0+y*scale+patch.shape[0]//2)],
            'reason':'diagnostic_match_only'}


def exploration_ready(path:Path)->dict:
    image=grayscale(path)
    title=normalized_match(image,template('SERVE_EMU_EXPLORATION_TITLE_NOV_2026'),(65,0,660,230))
    button=normalized_match(image,template('EXPLORATION_BUTTON'),(140,1080,550,1250))
    return {'ready':title['score']>=0.82 and button['score']>=0.82,
            'title':title,'explore_button':button}


def analyze_file(path:Path)->dict:
    image=grayscale(path)
    results={}
    for name,(tpl,roi) in STATES.items():
        try:results[name]={'template':tpl,**normalized_match(image,template(tpl),roi)}
        except (OSError,ValueError,subprocess.CalledProcessError) as e:
            results[name]={'template':tpl,'score':0.,'point':None,'reason':str(e)[:120]}
    best=max(results,key=lambda k:results[k]['score'])
    confidence=results[best]['score']
    return {'state':best if confidence>=0.84 else 'unknown',
            'score':round(confidence,4),'matches':results,
            'safety':'observe_only_not_action_authorization'}
