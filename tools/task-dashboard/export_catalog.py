#!/usr/bin/env python3
"""Export the 61 live task enum definitions to a stable dashboard catalog."""
import importlib.util
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
audit=ROOT/"tools/task-audit/audit_tasks.py"
spec=importlib.util.spec_from_file_location("task_audit",audit)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
tasks=module.catalogue()
out=[{k:e[k] for k in ("key","code","label","category","factory")} for e in tasks]
target=Path(__file__).with_name("catalog.json")
target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+"\n")
print("Exported",len(out),"task types to",target)
