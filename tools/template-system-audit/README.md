# Complete template inventory (read-only)

`audit.py` inspects every `TemplatesEnum` key, mapped PNG file, direct Java source
references, the locally checked-out upstream Git tree and optional Task Coach
CSV evidence. It never opens the emulator, changes template thresholds, accepts
AI proposals or marks a task as verified. No screenshots, device IDs, profile
names or credentials are included in the output. Counts of template hits and
misses are observations only, not action or task completion evidence.

```sh
python3 tools/template-system-audit/audit.py \
  --root "$HOME/projects/WOS-Botter" \
  --evidence "$HOME/survivalops-diagnostics/taskcoach" \
  --output "$HOME/.local/share/survivalops-template-audit"
python3 -m unittest discover -s tools/template-system-audit -p 'test_*.py' -v
```

The optional authenticated QA dashboard reads `report.json` from this private
output directory. Re-run the script after new Task Coach uploads or checkout
changes. When using a user systemd timer, give the audit service read-only repo
access and avoid touching the existing webserver authentication/database.

**Limitations:** The upstream comparison uses the locally checked-out
`upstream/main` ref. Static source-usage search may miss dynamic enum iteration
and string-based access. A template detected on an incidental/incorrect screen
is not necessarily a true positive. A missing match may be a correct negative.
Do not replace legacy templates without a labelled frame showing the *expected*
control, a meaningful false-positive control and a verified after-action state.
