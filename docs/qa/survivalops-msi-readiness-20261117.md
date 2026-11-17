# SurvivalOps - ADB evidence and MSI source readiness

Prepared on 17 November 2026 from source commit e5e25e6. No installer or update feed has been published.

## Verified build gates

- All outstanding source changes for Arena, Alliance Tech/Chest, Troop Training, Mail, Daily Missions, Exploration and Hero Recruitment were committed on the build candidate branch.
- Full task Java reactor: 1,445 tests, 0 failures, 0 errors, 4 skipped.
- Full Linux reactor package with skipTests: PASS, including Java desktop bundle.
- Shipped binary check: PASS (7 real files).
- Python packaging verification and release utility unit tests: PASS.
- Repository working tree clean and branch pushed: fix/exploration-chest-fast-city-entry-20261117.

## Tasks with direct, traceable live ADB evidence

Each row records a source implementation or tested change. A manually performed ADB action is NOT evidence of a complete Windows SurvivalOps bot run.

| Task | Code / tested source | Validation still pending |
|---|---|---|
| EXPLORATION_CHEST | d3e31e8 | Windows bot end-to-end pending |
| CITY_SURVIVORS | 5b72a7a | Windows bot end-to-end pending |
| LIFE_ESSENCE | 121dcf3 | Windows bot end-to-end pending |
| LIFE_ESSENCE_CARING | 121dcf3 | Windows bot end-to-end pending |
| ALLIANCE_PET_TREASURE | a2fd698 | Windows bot end-to-end pending |
| PET_ADVENTURE | 099495c | Windows bot end-to-end pending |
| PET_SKILLS | 0c49a70 | Windows bot end-to-end pending |
| VIP_POINTS | a2ce083 | Windows bot end-to-end pending |
| WAR_ACADEMY | 1af18cd | Blocked by game condition or no-spend policy |
| RESEARCH | 8047342 | Windows bot end-to-end pending |
| MAIL_REWARDS | 69a55a6 | Windows bot end-to-end pending |
| ALLIANCE_CHESTS | bfb66c9 | Windows bot end-to-end pending |
| ALLIANCE_HELP_REQUEST | existing verified task logic | Windows bot end-to-end pending |
| CHIEF_ORDER_PRODUCTIVITY_DAY | e0807b3 | Blocked by game condition or no-spend policy |
| CHIEF_ORDER_URGENT_MOBILIZATION | e0807b3 | Windows bot end-to-end pending |

## Additional task fixes with screenshot/Task Coach or code-level proof only

- ARENA: f2739bc (live bot E2E not yet verified)
- TRAINING_TROOPS: 67a546f (live bot E2E not yet verified)
- DAILY_MISSIONS: 630a86e (live bot E2E not yet verified)
- HERO_RECRUITMENT: e5e25e6 (live bot E2E not yet verified)
- ALLIANCE_TECH: f6e17e6 (live bot E2E not yet verified)
- GATHER_RESOURCES: 2c167bc (live bot E2E not yet verified)
- BEAST_HUNTING: 0de1613 (live bot E2E not yet verified)
- GAME_ANALYTICS_LABYRINTH: 79a39cd (live bot E2E not yet verified)
- ALLIANCE_AUTOJOIN: 230bae8 (live bot E2E not yet verified)
- EXPERT_AGNES_INTEL: 379a071 (live bot E2E not yet verified)
- EXPERT_ROMULUS_TAG: c3dbbcc (live bot E2E not yet verified)
- EXPERT_ROMULUS_TROOPS: c3dbbcc (live bot E2E not yet verified)
- EXPERT_SKILL_TRAINING: 0021ead (live bot E2E not yet verified)

## Real MSI publication gates still open

1. Windows MSI creation via WiX/jpackage has NOT been run here. This Ubuntu host cannot produce a verified Windows installer.
2. Headless Linux desktop UI tests cannot start JavaFX: Unable to open DISPLAY. Desktop GUI behavior requires Windows or an X display.
3. The real Windows SurvivalOps client is postponed by the user: 0 full Windows production-bot end-to-end passes recorded in the tracker.
4. The dedicated SurvivalOps signed updater manifest, MSI hash/size, upgrade smoke test and signing secret must be verified on Windows after packaging.
5. No free-only exception: paid gems/Chief Order points/FC actions remain prohibited, and unavailable events or locked features are not falsely marked passed.

## Build procedure when Windows stage is enabled

- Use GitHub Actions workflow .github/workflows/survivalops-release.yml with reviewed branch/tag only after Windows checks pass.
- Validate app image includes SurvivalOps.exe, SurvivalOpsWatcher.exe and Java runtime before creating the installer.
- Verify installer version, SHA-256 and file size against the project-signed stable update manifest, and test actual MSI update on Windows.
- Do not publish a stable channel from this source candidate before those Windows gates are complete.

