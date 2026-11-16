#!/usr/bin/env python3
"""Whole-task Serve-EMU recognition review, grounded in actual Task Coach CSV.

This is intentionally read-only. No template is deleted automatically because
zero hits can mean an optional popup was absent. It is a triage/reporting input
for manually tested, emulator-scoped runtime policy changes.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import re
import sqlite3

# IDs that are safe to skip *only* under the explicitly selected Serve-EMU.
# Other emulators and the archive of original assets are not affected.
RUNTIME_RETIRED = {
    'SERVE_EMU_EXPLORATION_TITLE', 'EXPLORATION_SCREEN_TITLE',
    'SERVE_EMU_IDLE_INCOME_TITLE', 'EXPLORATION_IDLE_INCOME_TITLE',
    'SERVE_EMU_WORLD_ANCHOR', 'SERVE_EMU_DAILY_TITLE',
    'GAME_HOME_WORLD', 'ARENA_CHALLENGE_BUTTON',
}
# Guards cannot be auto-retired even if absent in every ordinary run.
SAFETY_GUARDS = {
    'GAME_HOME_RECONNECT', 'GAME_START_WELCOME_BACK_TITLE',
    'GAME_START_MANDATORY_UPDATE_TITLE',
    'SERVE_EMU_OFFER_RESPLENDENT_TITLE_2026',
    'SERVE_EMU_RESOURCES_OVERVIEW_TITLE_2026',
    'GAME_QUIT_CONFIRMATION_PROMPT',
    'SHARED_REWARDS_BANNER_CURRENT', 'SHARED_REWARDS_BANNER',
}


def load_task_ids(sqlite_file: Path | None) -> list[str]:
    if not sqlite_file or not sqlite_file.exists():
        return []
    with sqlite3.connect(f'file:{sqlite_file}?mode=ro', uri=True) as db:
        return sorted(r[0] for r in db.execute('select key from tasks'))


def source_profile(root: Path, catalog: Path) -> dict:
    """Static template and matching-call inventory of all dispatchable tasks.

    This is a source-code reference count, not a claim that every branch is
    executed. It complements sparse Task Coach screenshots.
    """
    items=json.loads(catalog.read_text()) if catalog.exists() else []
    by_name={}
    for file in (root/'modules/tasks/src/main/java').rglob('*.java'):
        by_name.setdefault(file.stem,file)
    enum_source=(root/'modules/api/src/main/java/dev/frostguard/api/configs/TemplatesEnum.java').read_text()
    names=set(re.findall(r'\b[A-Z][A-Z0-9_]{3,}\b',enum_source))
    out={}
    for item in items:
        task=item.get('key','')
        if not re.fullmatch(r'[A-Z][A-Z0-9_]+',task):continue
        factory=item.get('factory') or ''
        file=by_name.get(factory)
        if file is None:
            out[task]={'factory':factory,'source_found':False,
                       'source_template_count':0,'source_match_calls':0}
            continue
        src=file.read_text(errors='replace')
        refs=set(re.findall(r'\b[A-Z][A-Z0-9_]{3,}\b',src)) & names
        calls=sum(src.count(k+'(') for k in (
            'locatePattern','locatePatternFast','locateAction','locatePatternMono',
            'locatePatternMultiScale','locateAllPatterns','isTemplateFound'))
        out[task]={'factory':factory,'source_found':True,
                   'source_template_count':len(refs),'source_match_calls':calls}
    return out


def review(evidence: Path, all_tasks: list[str], source: dict | None=None) -> dict:
    records = defaultdict(lambda: defaultdict(lambda: dict(
        checks=0, hits=0, methods=Counter(), max_miss_score=0.0,
        sessions=set())))
    for path in sorted(evidence.glob('*/*/recognition-events.csv')):
        run=path.parent/'run.txt'
        task=next((s[5:].strip() for s in run.read_text(errors='replace').splitlines()
                   if s.startswith('task=')), 'UNKNOWN') if run.exists() else 'UNKNOWN'
        if not re.fullmatch(r'[A-Z][A-Z0-9_]+',task):task='UNKNOWN'
        with path.open(newline='',encoding='utf-8-sig') as f:
            for row in csv.DictReader(f):
                tpl=row.get('template','')
                if not re.fullmatch(r'[A-Z][A-Z0-9_]+',tpl):continue
                st=records[task][tpl]
                st['checks']+=1
                hit=str(row.get('hit','')).lower()=='true'
                st['hits']+=int(hit)
                st['methods'][row.get('method','unknown')]+=1
                st['sessions'].add(path.parent.name)
                if not hit:
                    try: st['max_miss_score']=max(st['max_miss_score'],float(row.get('score') or 0))
                    except ValueError:pass
    output={}
    for task in sorted(set(all_tasks)|set(records)):
        evidence_rows=records.get(task,{})
        ordered=sorted(evidence_rows.items(),key=lambda x:(-x[1]['checks'],x[0]))
        stats=[]
        for name,st in ordered:
            category=('retired_on_serve_emu' if name in RUNTIME_RETIRED else
                      'protected_safety_guard' if name in SAFETY_GUARDS else
                      'observed_positive' if st['hits'] else
                      'review_only_optional_or_stale')
            stats.append(dict(template=name,category=category,checks=st['checks'],
                              hits=st['hits'],sessions=len(st['sessions']),
                              max_miss_score=round(st['max_miss_score'],2),
                              methods=dict(st['methods'])))
        output[task]=dict(**(source or {}).get(task,{}),
                          total_checks=sum(x['checks'] for x in stats),
                          distinct_templates=len(stats),
                          coached_sessions=len(set().union(*(x['sessions'] for x in evidence_rows.values())))
                          if evidence_rows else 0,
                          review_required=[x for x in stats if x['category']=='review_only_optional_or_stale'],
                          templates=stats)
    return output


def to_markdown(profiles: dict) -> str:
    coached=sum(v['coached_sessions']>0 for v in profiles.values())
    checks=sum(v['total_checks'] for v in profiles.values())
    lines=[
        '# Serve-EMU: alle taken en daadwerkelijke herkenningspogingen', '',
        'Momentopname van de beschikbare Task Coach-runs. **Geen live snelheidsmeting**.',
        'De CSV telt kleur/schaal/grijswaarden als aparte metingen, niet als andere emulators.',
        f'**{len(profiles)} taken** in inventaris, **{coached} met run-bewijs**, '
        f'**{checks} herkenningsmetingen**. Een ontbrekende hit bewijst niet dat een optionele knop verouderd is.', '',
        '## Wat wordt wel en niet verwijderd?', '',
        '- Centrale Serve-EMU-route gebruikt een vastgestelde blacklist van acht bewezen oude varianten; PNG-bestanden blijven als archief behouden.',
        '- Reconnect wordt bij een bevestigd root-HUD overgeslagen, maar blijft verplicht als het scherm onbekend is.',
        '- City- en World-ankers behouden hun **multiscale en mono** fallback omdat er in de runs echte positieve treffers mee waren.',
        '- Rare pop-ups, aankopen, safety-dialogen, tierlevels en vrijgaveknoppen worden **niet** uitsluitend wegens lage scores uitgeschakeld.',
        '- Voor taken zonder Task Coach-evidence is een betrouwbare snelheid- of verwijderclaim onmogelijk.', '',
        '## Per taak', '',
        '| Taak | Bronklasse | Bron-templates | Zoekaanroepen in bron | Sessies | Werkelijke checks | Top zonder bevestigde hit (review) |',
        '|---|---|---:|---:|---:|---:|---|',
    ]
    for task,info in sorted(profiles.items(),key=lambda p:(-p[1]['total_checks'],p[0])):
        candidates=[x for x in info['review_required'] if x['checks']>=3]
        subset=', '.join(f"`{x['template']}` ({x['checks']})" for x in candidates[:4])
        name=info.get('factory') or 'onbekend'
        src_count=info.get('source_template_count',0)
        calls=info.get('source_match_calls',0)
        lines.append(f"| `{task}` | `{name}` | {src_count} | {calls} | {info['coached_sessions']} | {info['total_checks']} | {subset or 'geen / geen bewijs'} |")
    lines += ['', '## Legenda', '',
              'De bronkolommen tellen statische Java-verwijzingen/aanroepen, niet de uitgevoerde acties of dynamische helpers. Alle taken gebruiken bovendien gedeelde navigatie en herkenningsengine.',
              '`review_only_optional_or_stale`: nul treffers in deze runs. Niet automatisch verwijderen.',
              '`protected_safety_guard`: nul treffers zijn normaal tijdens gezond spel.',
              '`retired_on_serve_emu`: concrete bewezen oude variant, enkel voor Serve-EMU uitgeschakeld.', '',
              'Voor actuele details, inclusief per-template scores en methode, gebruik het Templates-tabblad van het QA-dashboard.', '']
    return '\n'.join(lines)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--tasks-db',type=Path)
    parser.add_argument('--catalog',type=Path)
    parser.add_argument('--repo-root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output',type=Path,required=True)
    a=parser.parse_args()
    a.output.parent.mkdir(parents=True,exist_ok=True)
    source=source_profile(a.repo_root,a.catalog) if a.catalog else {}
    result=review(a.evidence,sorted(set(load_task_ids(a.tasks_db))|set(source)),source)
    a.output.write_text(to_markdown(result),encoding='utf-8')
    print(f'tasks={len(result)}, measured_tasks={sum(s["coached_sessions"]>0 for s in result.values())}, checks={sum(s["total_checks"] for s in result.values())}')

if __name__=='__main__':main()
