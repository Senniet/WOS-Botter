#!/usr/bin/env python3
"""Manage the exact same QA statuses as the web UI, via SSH. No separate database."""
import argparse
import json
import os
from pathlib import Path
from server import Board,connect,STATUS_VALUES

ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument("--db",type=Path,default=Path.home()/".local/share/survivalops-qa/dashboard.sqlite3")
p.add_argument("--catalog",type=Path,default=ROOT/"catalog.json")
p.add_argument("--diagnostics",type=Path,default=Path.home()/"survivalops-diagnostics/inbox")
p.add_argument("--auth",type=Path,default=Path.home()/".config/survivalops-qa-dashboard/auth.json")
sub=p.add_subparsers(dest="action",required=True)
sub.add_parser("summary")
details=sub.add_parser("show");details.add_argument("task")
ls=sub.add_parser("list");ls.add_argument("--status",choices=sorted(STATUS_VALUES));ls.add_argument("--category")
upd=sub.add_parser("mark");upd.add_argument("task");upd.add_argument("status",choices=sorted(STATUS_VALUES));upd.add_argument("--notes");upd.add_argument("--evidence",help="Mandatory before marking VERIFIED in a CLI")
issue=sub.add_parser("issue");issue.add_argument("task");issue.add_argument("title");issue.add_argument("--detail",default="");issue.add_argument("--severity",choices=["low","medium","high","critical"],default="medium")
fix=sub.add_parser("resolve");fix.add_argument("issue_id",type=int)
sub.add_parser("sync")
args=p.parse_args()
board=Board(args.db,args.catalog,args.diagnostics,args.auth)
if args.action=="summary":
 d=board.summary()
 print(json.dumps({"total":d["total"],"by_status":d["counts"],
      "open_issues":sum(t["open_issues"] for t in d["tasks"]),
      "unmapped_diagnostics":d["unmapped_diagnostics"]},ensure_ascii=False,indent=2))
elif args.action=="list":
 for t in board.summary()["tasks"]:
  if args.status and t["status"]!=args.status:continue
  if args.category and t["category"]!=args.category:continue
  print(f'{t["key"]:36} {t["status"]:11} issues={t["open_issues"]} diagnostics={t["diagnostics"]} {t["label"]}')
elif args.action=="show":
 data=board.detail(args.task)
 if not data:raise SystemExit("Unknown task")
 print(json.dumps(data,ensure_ascii=False,indent=2))
elif args.action=="mark":
 if args.status=="verified" and not (args.evidence and len(args.evidence)>=12):
  raise SystemExit("VERIFIED requires --evidence describing actual live proof.")
 row=board.detail(args.task)
 if not row:raise SystemExit("Unknown task")
 current=row["task"]
 notes=args.notes if args.notes is not None else current["notes"]
 if args.evidence:
  notes=(notes+"\nLive bewijs: "+args.evidence).strip()
 state,result=board.edit_task(args.task,{"status":args.status,"notes":notes,"version":current["version"]},"ChatGPT")
 print(json.dumps({"state":state,"task":result},ensure_ascii=False))
 if state!="ok":raise SystemExit(1)
elif args.action=="issue":
 result=board.add_issue(args.task,{"title":args.title,"detail":args.detail,"severity":args.severity},"ChatGPT")
 if not result:raise SystemExit("Invalid issue or task")
 print("Created issue",result)
elif args.action=="resolve":
 if not board.edit_issue(args.issue_id,{"state":"closed"},"ChatGPT"):
  raise SystemExit("Unknown issue")
 print("Closed issue",args.issue_id)
elif args.action=="sync":
 print("New diagnostics:",board.sync_diagnostics(force=True))
