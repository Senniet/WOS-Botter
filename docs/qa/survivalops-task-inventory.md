# SurvivalOps task inventory and coverage baseline

Generated from `TpDailyTaskEnum`, `TaskRegistrations`, source tests and local diagnostics.
**Evidence levels must not be conflated:** a source test is not a live task completion.

- Registered catalogue: **61** task types across **12** categories.
- Factory omissions (excluding custom task): **0**; missing source: **0**.
- Source-test candidates found: **36**; without candidate tests: **25**.
- Frame/recognition test candidates found: **16** task types.
- Current serve-emu screenshot: **720x1280, PNG, captured read-only (private local cache)**.

The test numbers below are static reference heuristics, not proof of successful UI actions.
Incidents are counts of relevant diagnostic snapshots in the last 24 hours, not all failures.
A safe end-to-end pass requires before/after screen evidence, verified clicks, actual claimed result,
bounded timeout, correct cooldown/retry schedule and no unintended spending.

## Execution policy

1. Verify active ADB serial, physical 720x1280 screenshot, configured UI distance and account ownership.
2. Gate each task by its verified screen state and availability; do not use stale coordinates or blind Back.
3. Check relevant button with two independent confirmations when ambiguous, then perform one action.
4. Classify observed result as confirmed, unavailable, blocked by game conditions, or unknown.
5. For unavailable features, report the actual gate and do not repeatedly attempt a tap.
6. Never test purchases, premium currency, special events, alliance membership changes or FC upgrades without an explicit safe budget.
7. Retain redacted before/after frames with expected and observed state; recheck scheduling after failures.

## Catalogue

### Alliance (9)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Alliance Autojoin (`ALLIANCE_AUTOJOIN`) | `AllianceAutojoinRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Alliance Championship (`ALLIANCE_CHAMPIONSHIP`) | `AllianceChampionshipRoutine` | 2 | 0 | 0 | Live E2E not yet proven |
| Alliance Chests (`ALLIANCE_CHESTS`) | `AllianceChestRoutine` | 1 | 0 | 0 | Live E2E not yet proven |
| Alliance Mobilization (`ALLIANCE_MOBILIZATION`) | `AllianceMobilizationRoutine` | 1 | 0 | 0 | Live E2E not yet proven |
| Alliance Pet Treasure (`ALLIANCE_PET_TREASURE`) | `PetAllianceTreasuresRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Alliance Shop (`ALLIANCE_SHOP`) | `AllianceShopRoutine` | 2 | 0 | 0 | Live E2E not yet proven |
| Alliance Tech (`ALLIANCE_TECH`) | `AllianceTechRoutine` | 3 | 0 | 0 | Live E2E not yet proven |
| Alliance Triumph (`ALLIANCE_TRIUMPH`) | `TriumphRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Bear Trap Event (`BEAR_TRAP`) | `BearTrapRoutine` | 2 | 0 | 0 | Live E2E not yet proven |


### Analytics (2)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Collect Alliance Labyrinth Ranking (`GAME_ANALYTICS_LABYRINTH`) | `GameAnalyticsRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Collect Alliance Power Ranking (`GAME_ANALYTICS_POWER`) | `GameAnalyticsRoutine` | 0 | 0 | 0 | Live E2E not yet proven |


### Chief Order (3)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Chief Order: Productivity Day (`CHIEF_ORDER_PRODUCTIVITY_DAY`) | `ChiefOrderRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Chief Order: Rush Job (`CHIEF_ORDER_RUSH_JOB`) | `ChiefOrderRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Chief Order: Urgent Mobilization (`CHIEF_ORDER_URGENT_MOBILIZATION`) | `ChiefOrderRoutine` | 0 | 0 | 0 | Live E2E not yet proven |


### City (4)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| City Survivors (`CITY_SURVIVORS`) | `NewSurvivorsRoutine` | 0 | 0 | 2 | Live E2E not yet proven |
| City Upgrade Furnace (`CITY_UPGRADE_FURNACE`) | `UpgradeBuildingsRoutine` | 2 | 1 | 47 | Live E2E not yet proven |
| Prioritise Furnace (`CITY_UPGRADE_PRIORITISE_FURNACE`) | `PrioritiseFurnaceRoutine` | 0 | 0 | 22 | Live E2E not yet proven |
| Research (`RESEARCH`) | `ResearchRoutine` | 8 | 2 | 1 | Live E2E not yet proven |


### Custom (1)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Custom Task (`CUSTOM_TASK`) | `—` | 0 | 0 | 0 | User-defined workflow; no built-in factory |


### Daily Objective (8)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Daily Missions (`DAILY_MISSIONS`) | `DailyMissionRoutine` | 3 | 1 | 1 | Live E2E not yet proven |
| Expert Agnes Intel (`EXPERT_AGNES_INTEL`) | `ExpertsAgnesIntelRoutine` | 0 | 0 | 7 | Live E2E not yet proven |
| Expert Romulus Tag (`EXPERT_ROMULUS_TAG`) | `ExpertsRomulusTagRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Expert Romulus Troops (`EXPERT_ROMULUS_TROOPS`) | `ExpertsRomulusTroopsRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Expert Skill Training (`EXPERT_SKILL_TRAINING`) | `ExpertSkillTrainingRoutine` | 0 | 0 | 12 | Live E2E not yet proven |
| Intel (`INTEL`) | `IntelligenceRoutine` | 11 | 4 | 7 | Live E2E not yet proven |
| Mail Rewards (`MAIL_REWARDS`) | `MailRewardsRoutine` | 1 | 0 | 0 | Live E2E not yet proven |
| Storehouse Chest (`STOREHOUSE_CHEST`) | `StorehouseChestRoutine` | 5 | 2 | 29 | Live E2E not yet proven |


### Event (9)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Manual Rally Join (`EVENT_BERSERK_CRYPTID`) | `ManualRallyJoinRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |
| Hero Mission Event (`EVENT_HERO_MISSION`) | `HeroMissionEventRoutine` | 2 | 0 | 0 | Restricted / active-state dependent |
| Journey of Light Event (`EVENT_JOURNEY_OF_LIGHT`) | `JourneyofLightRoutine` | 1 | 0 | 0 | Restricted / active-state dependent |
| Myriad Bazaar Event (`EVENT_MYRIAD_BAZAAR`) | `MyriadBazaarEventRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |
| Polar Terror Hunting (`EVENT_POLAR_TERROR`) | `PolarTerrorHuntingRoutine` | 1 | 0 | 0 | Restricted / active-state dependent |
| Tundra Truck Event (`EVENT_TUNDRA_TRUCK`) | `TundraTruckEventRoutine` | 3 | 0 | 4 | Restricted / active-state dependent |
| Fishing Minigame (`FISHING_MINIGAME`) | `FishingMinigameRoutine` | 5 | 2 | 0 | Restricted / active-state dependent |
| Mercenary Event (`MERCENARY_EVENT`) | `MercenaryEventRoutine` | 1 | 0 | 0 | Restricted / active-state dependent |
| Test Hook Loop (`TEST_HOOK_LOOP`) | `TestHookLoopRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |


### Lifecycle (6)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Create Character (`CREATE_CHARACTER`) | `CreateCharacterRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |
| Dummy Task (`DUMMY_TASK`) | `DummyRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |
| Gather Speed Boost (`GATHER_BOOST`) | `GatherSpeedRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |
| Gather Resources (`GATHER_RESOURCES`) | `GatherRoutine` | 9 | 2 | 0 | Restricted / active-state dependent |
| Initialize (`INITIALIZE`) | `InitializeRoutine` | 4 | 0 | 5 | Restricted / active-state dependent |
| Skip Tutorial (`SKIP_TUTORIAL`) | `SkipTutorialRoutine` | 0 | 0 | 0 | Restricted / active-state dependent |


### Military (1)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Training (`TRAINING_TROOPS`) | `TrainingRoutine` | 8 | 3 | 12 | Live E2E not yet proven |


### Pet (1)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Pet Skills (`PET_SKILLS`) | `PetSkillsRoutine` | 2 | 1 | 0 | Live E2E not yet proven |


### Resource (16)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Arena (`ARENA`) | `ArenaRoutine` | 8 | 3 | 0 | Live E2E not yet proven |
| Bank (`BANK`) | `BankRoutine` | 1 | 0 | 0 | Live E2E not yet proven |
| Beast Hunting (`BEAST_HUNTING`) | `BeastSlayRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Crystal Laboratory (`CRYSTAL_LABORATORY`) | `CrystalLaboratoryRoutine` | 3 | 1 | 0 | Live E2E not yet proven |
| Do Exploration (`DO_EXPLORATION`) | `DoExplorationRoutine` | 1 | 0 | 11 | Live E2E not yet proven |
| Exploration Chest (`EXPLORATION_CHEST`) | `ExplorationRoutine` | 8 | 3 | 11 | Live E2E not yet proven |
| Hero Recruitment (`HERO_RECRUITMENT`) | `HeroRecruitmentRoutine` | 8 | 2 | 10 | Live E2E not yet proven |
| Labyrinth (`LABYRINTH`) | `DailyLabyrinthRoutine` | 2 | 0 | 0 | Live E2E not yet proven |
| Life Essence (`LIFE_ESSENCE`) | `LifeEssenceRoutine` | 4 | 0 | 0 | Live E2E not yet proven |
| Life Essence Caring (`LIFE_ESSENCE_CARING`) | `LifeEssenceCaringRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Nomadic Merchant (`NOMADIC_MERCHANT`) | `NomadicMerchantRoutine` | 3 | 1 | 0 | Live E2E not yet proven |
| Pet Adventure (`PET_ADVENTURE`) | `PetAdventureChestRoutine` | 2 | 0 | 0 | Live E2E not yet proven |
| Tundra Trek Automation (`TREK_AUTOMATION`) | `TundraTrekAutoRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| Trek Supplies (`TREK_SUPPLIES`) | `TundraTrekRoutine` | 0 | 0 | 0 | Live E2E not yet proven |
| VIP Points (`VIP_POINTS`) | `VipRoutine` | 2 | 1 | 0 | Live E2E not yet proven |
| War Academy Shards (`WAR_ACADEMY`) | `WarAcademyRoutine` | 1 | 0 | 0 | Live E2E not yet proven |


### Shop (1)

| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |
|---|---|---:|---:|---:|---|
| Shop Mystery (`SHOP_MYSTERY`) | `MysteryShopRoutine` | 3 | 1 | 0 | Live E2E not yet proven |


## Prioritisation

- P0: live recurring incidents involving UI ownership, modal recognition, stamina/storehouse, home recovery.
- P1: all daily routines, training, gathering, mail, Intel, arena and survivors; require real-account replay evidence.
- P2: alliance, beasts, pets, research, exploration and labyrinth with explicit cooldown and action budgets.
- P3: conditional/limited-time events and account/lifecycle workflows; run only when their screen safely exists.

The programme is **not completed** by this inventory: it only establishes complete scope and auditable coverage gaps.
