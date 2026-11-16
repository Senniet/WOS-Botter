# SurvivalOps: complete review of all operator-tested tasks (16 November 2026)

**Status: source-level repairs and regression evidence, NOT a claim that every
task is fixed in the running Windows application.** This report is based on
all 62 task records, all 16 stored QA rounds (including the operator's seven
steps and notes), open issues and the Task Coach sessions uploaded to the
private diagnostic server. In total, **21 task records had an operator-relevant
working/testing/broken/verified status**, plus two additional Life Essence Task
Coach sessions with no QA round. Existing operator signoffs and verified
histories remain untouched; the independent reviewer signed only the
fully-supported CHIEF_ORDER_URGENT_MOBILIZATION round #9.

## Reproduced issues repaired in source

| Task | Existing live observation | Specific source change and acceptance status |
|---|---|---|
| RESEARCH, round #15, `20261116-000112-1x5qg6q` | Research + Help works, but bot stays on Tech Research; `SERVE_EMU_RESEARCH_TITLE` actually matches 99.4% near (211,39), not in its previous y65..155 search area | Moved title ROI to y0..100, added exact two-frame title+header Back and verified return Home. Header replay fixture 2 tests. **Needs new Windows task run.** |
| ALLIANCE_TRIUMPH, round #16, `20261116-000555-h4bu5x` | Daily chest claimed; bot stuck on distinct **Weekly Rewards — Tap anywhere to close**, dozens of irrelevant retries, scheduler FAILED | New OCR proof of exact Weekly Rewards title and Tap-anywhere-to-close footer on two independent frames, one safe dismissal then disappearance check; do not infer weekly reward claimed. Screenshot/OCR and negative-click tests pass. **Needs retest, including Daily claimed and weekly state.** |
| EXPLORATION_CHEST, round #12, `20261115-231442-w0t3nb` | Chest and rewards claimed, but bot remains on Exploration and reports SUCCEEDED | Old hardcoded Back rectangle y78..135 misses actual header arrow at y~36. New same-frame NOV_2026 title+Back, two-frame arrow confirmation, one tap, then positive City Home verification. Failure marks task incomplete instead of SUCCEEDED. **Needs retest.** |
| ARENA, round #13, `20261115-233256-1cigeo4` | Eventually enters Arena, Challenge not recognized; scheduler SUCCEEDED despite zero fights | Actual Arena title scores 99.28% at y0..105, only 31.93% at old y70..150; current and Serve-EMU Challenge CTA templates both ~99.7% on matching frame. Corrected ROI; unmet navigation/Challenge/attempts/Home now mark unfinished. **A successful fight, daily chest and Home return are NOT yet proved.** |
| ALLIANCE_TECH, round/history issue #14 | Contribution can be applied but >40 unrelated global checks before failing Home recovery | New two-step, two-frame verified `Tech → Alliance → City` header path; refuses blind back on other screens. Existing real Tech, Alliance, City frames pass. Incomplete exit is deferred. **Needs new Windows run after donations.** |
| MAIL_REWARDS, round #10, `20261115-230422-1ekgstl` | Mail tabs and Read & Claim All worked, but slowed by old title checks | Real current Mail title 99.50% at y0..100; old y75..150 ROI scored 44.32%. Corrected two source locations and Initialize recovery; current header checked first. Existing claiming behaviour preserved. **Needs timing comparison.** |
| GATHER_BOOST, round #11 and runs `20261115-231136-b8irdo`, `20261115-231221-1d95den` | Opens Chief HUD only, no boost applied, yet scheduler reports SUCCEEDED | Refuses to advance from the legacy blind coordinates without verifying Growth and Gathering Speed text, and requires positive *active/remaining* boost state. On missed gate marks incomplete and captures diagnostics. Automatic gem purchase and replacing existing boost disabled. **No real Growth screenshot / positive boost action yet; this is a safe stop, not a working boost.** |
| LIFE_ESSENCE and LIFE_ESSENCE_CARING | Unattached Coach runs `20261115-233221-1gxalt` and `20261115-233143-dq8rcm` returned SUCCEEDED with no Life Essence sidebar match, 74.x%-score candidates remain REVIEW_ONLY | Navigation and unverified caring/claim retries mark incomplete instead of returning SUCCEEDED; no speculative 74%-confidence auto-click added. **The correct current sidebar row remains unresolved pending an actual positive screen reference.** |
| ALLIANCE_HELP_REQUEST, round #14, `20261115-235448-hvzmoc` | Operator says it clicked requests but coach records City, no Help-button recognition events | Kept task logic, but now routes **Help button before/after** recognition via controller with Task Coach evidence instead of unlogged direct OpenCV. **Old coach evidence cannot independently establish button tap; fresh run needed before reviewer signs.** |

## Tasks with genuine success observations, no unwarranted rework

- **CHIEF_ORDER_URGENT_MOBILIZATION**, round #9, `20261115-230002-1eyjewq`: operator signed all 7 stages; independent screenshots show Urgent Mobilization selection, Enact 50,000, then City with explicit "Enacted! Your survivors will keep working for 48 hours straight". No open issue. Reviewer signed independently; **jointly verified**.
- **CHIEF_ORDER_PRODUCTIVITY_DAY**, round #6: already operator+reviewer verified with a separate Enact/City run; retained.
- **DAILY_MISSIONS**, verified in previous analysis from two runs; retained historical status (different signoff history).
- **CHIEF_ORDER_RUSH_JOB**, round #8: Enact works, Rewards dismissal slow; previously implemented dedicated safe Rewards logic (PR #43, release 1.1.70). **Live performance still not remeasured.**
- **ALLIANCE_CHESTS**, round #4: Alliance Gift claimed, Loot Chest absent; second half still untested. **Not full verified.**
- **CITY_SURVIVORS**, round #5: before/after Working 17→26, Resting 14→5, Allocate All disabled and acknowledgment. Legacy screen-context issue #9 remains open. **Not full verified.**
- **STOREHOUSE_CHEST**, round #3: chest claimed, Chief Stamina at max 500 was correctly skipped/closed; a real stamina claim at non-max capacity is untested. **Not full verified.**
- **TRAINING_TROOPS**, round #1: operator reports all three training buttons work, returns Home in one run; older completed-troop claim / unlock overlay / tab recovery issues #2/#10/#11/#12 still need positive regression evidence. Existing task-scoped runtime patch retained. **Not full verified.**
- **DO_EXPLORATION**, round #7: actual Squad Settings detected but Fight not clicked, prior false SUCCEEDED. Earlier latest release corrected false Quick-dialog hero-card detection and marks unverified outcomes as DEFERRED. **Positive Fight, battle outcome and Home return still require new run.**
- **RESEARCH** and **ALLIANCE_TRIUMPH** marked working/failed by operator, but return-to-Home regressions described above remain open until retested.

## Tested or reviewed but not reproducibly repaired

- **INTEL**, issue #3: Beasts, Rescue and A Hero's Journey have historically distinct View/Deploy failures. Previous code safely defers unknown UI; no new positive Rescue/Journey frame in this QA batch. Don't guess coordinates.
- **HERO_RECRUITMENT**, issue #7: historical blue Rewards overlay after Free/Key claim (`20261115T095457Z-f267f44c8b61`), old exit template 15.9%. Existing recruitment avoids repeated key taps, but a safe replacement identity/exit is not yet proven. **Do not spend keys/gems without verification.**
- **CITY_UPGRADE_FURNACE**, issues #4/#8: multiple historical unresolved building / Alliance-Tech Home failures. Dedicated Alliance Tech navigator added this round, but furnace recognition itself not reproduced on live frame. **Not verified.**
- **EVENT_TUNDRA_TRUCK**, issue #5: no new evidence the event is actually running; never deploy/spend on old screenshot geometry. **Not verified.**
- **ARENA**: code now sees header/Challenge, but battle opponent selection and win/loss postcondition still unproven.

## QA and code safety

- No blanket threshold reduction, no automatic promotion of Task Coach proposals, no blind spending. Existing manual status and QA steps/notes preserved. Reported scheduler `SUCCEEDED` is *not* proof of a successful game action.
- Masked fixtures retain only game headers, relevant button/overlay labels or safe static regions; no user account names, rankings or private chat are checked into the repository.
- Tests prove positive and negative recognition on recorded frames, bounded single-tap exits and static policy. Live performance improvement and correct visual navigation on the Windows ADB emulator must be measured separately.
- Jointly verified status is reserved for genuine evidence and both signoffs. Where a user's past QA round contains a FAIL or has no operator signature, it remains open.

## New-build live acceptance plan

After distributing the new Windows build, use Task Coach for **Research,
Alliance Triumph, Exploration Chest, Arena, Alliance Tech, Mail Rewards,
Gather Boost** first. Include one actual active-help request, completed troop
collection, unavailable/available Alliance Loot Chest, non-maxed Stamina claim,
Exploration Fight and a live Rescue/Journey Intel scenario as each becomes
available. Compare recognition counts in Templates → Methods per task. Record
and close each individual issue only when the correct game state is seen.
