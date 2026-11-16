# Serve-EMU: alle taken en daadwerkelijke herkenningspogingen

Momentopname van de beschikbare Task Coach-runs. **Geen live snelheidsmeting**.
De CSV telt kleur/schaal/grijswaarden als aparte metingen, niet als andere emulators.
**62 taken** in inventaris, **19 met run-bewijs**, **2386 herkenningsmetingen**. Een ontbrekende hit bewijst niet dat een optionele knop verouderd is.

## Wat wordt wel en niet verwijderd?

- Centrale Serve-EMU-route gebruikt een vastgestelde blacklist van acht bewezen oude varianten; PNG-bestanden blijven als archief behouden.
- Reconnect wordt bij een bevestigd root-HUD overgeslagen, maar blijft verplicht als het scherm onbekend is.
- City- en World-ankers behouden hun **multiscale en mono** fallback omdat er in de runs echte positieve treffers mee waren.
- Rare pop-ups, aankopen, safety-dialogen, tierlevels en vrijgaveknoppen worden **niet** uitsluitend wegens lage scores uitgeschakeld.
- Voor taken zonder Task Coach-evidence is een betrouwbare snelheid- of verwijderclaim onmogelijk.

## Per taak

| Taak | Bronklasse | Bron-templates | Zoekaanroepen in bron | Sessies | Werkelijke checks | Top zonder bevestigde hit (review) |
|---|---|---:|---:|---:|---:|---|
| `TRAINING_TROOPS` | `TrainingRoutine` | 45 | 17 | 2 | 395 | `TRAINING_INFANTRY_T1` (10), `TRAINING_INFANTRY_T10` (10), `TRAINING_INFANTRY_T11` (10), `TRAINING_INFANTRY_T2` (10) |
| `ALLIANCE_TRIUMPH` | `TriumphRoutine` | 4 | 6 | 3 | 345 | `SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026` (15), `SERVE_EMU_REWARDS_BANNER` (15), `ALLIANCE_TRIUMPH_DAILY_CLAIMED` (12), `SERVE_EMU_HEADER_BACK` (12) |
| `DO_EXPLORATION` | `DoExplorationRoutine` | 9 | 9 | 5 | 303 | `EXPLORATION_DEFEAT` (15), `SERVE_EMU_REWARDS_BANNER` (15), `GAME_HOME_FURNACE` (7) |
| `DAILY_MISSIONS` | `DailyMissionRoutine` | 10 | 8 | 4 | 231 | `GAME_HOME_FURNACE` (5), `SERVE_EMU_WORLD_ANCHOR_CURRENT` (5), `DAILY_MISSION_CLAIM_BUTTON` (3) |
| `ALLIANCE_TECH` | `AllianceTechRoutine` | 2 | 2 | 4 | 186 | `SERVE_EMU_REWARDS_BANNER` (12), `GAME_HOME_FURNACE` (8), `SERVE_EMU_WORLD_ANCHOR_CURRENT` (8) |
| `EXPLORATION_CHEST` | `ExplorationRoutine` | 10 | 8 | 2 | 174 | `SERVE_EMU_REWARDS_BANNER` (11), `EXPLORATION_BUTTON` (8), `EXPLORATION_CLAIM_DISABLED` (8) |
| `RESEARCH` | `ResearchRoutine` | 10 | 14 | 2 | 141 | `SERVE_EMU_RESEARCH_TITLE` (12), `BUILDING_BUTTON_RESEARCH` (6), `SERVE_EMU_REWARDS_BANNER` (6), `REPLENISH_ALL_BUTTON` (4) |
| `MAIL_REWARDS` | `MailRewardsRoutine` | 9 | 12 | 2 | 129 | geen / geen bewijs |
| `ARENA` | `ArenaRoutine` | 12 | 13 | 1 | 100 | `SERVE_EMU_ARENA_TITLE` (12), `SERVE_EMU_ALLIANCE_HEADER_LIVE_2026` (3), `SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026` (3), `SERVE_EMU_REWARDS_BANNER` (3) |
| `CITY_SURVIVORS` | `NewSurvivorsRoutine` | 8 | 9 | 3 | 83 | geen / geen bewijs |
| `ALLIANCE_CHESTS` | `AllianceChestRoutine` | 3 | 4 | 3 | 62 | `ALLIANCE_CHEST_CLAIM_ALL_BUTTON` (9) |
| `CHIEF_ORDER_RUSH_JOB` | `ChiefOrderRoutine` | 5 | 3 | 1 | 44 | geen / geen bewijs |
| `STOREHOUSE_CHEST` | `StorehouseChestRoutine` | 9 | 9 | 1 | 38 | `STOREHOUSE_SELECTED_NAME_MODERN` (6) |
| `ALLIANCE_HELP_REQUEST` | `AllianceHelpRoutine` | 2 | 4 | 3 | 35 | geen / geen bewijs |
| `CHIEF_ORDER_PRODUCTIVITY_DAY` | `ChiefOrderRoutine` | 5 | 3 | 2 | 30 | geen / geen bewijs |
| `CHIEF_ORDER_URGENT_MOBILIZATION` | `ChiefOrderRoutine` | 5 | 3 | 2 | 30 | geen / geen bewijs |
| `LIFE_ESSENCE_CARING` | `LifeEssenceCaringRoutine` | 4 | 4 | 1 | 23 | `SERVE_EMU_CITY_ANCHOR` (4), `GAME_HOME_FURNACE` (3), `SIDEBAR_DAILY_LIFE_ESSENCE` (3) |
| `LIFE_ESSENCE` | `LifeEssenceRoutine` | 3 | 4 | 1 | 21 | `GAME_HOME_FURNACE` (3), `SERVE_EMU_CITY_ANCHOR` (3), `SIDEBAR_DAILY_LIFE_ESSENCE` (3) |
| `GATHER_BOOST` | `GatherSpeedRoutine` | 0 | 0 | 2 | 16 | geen / geen bewijs |
| `ALLIANCE_AUTOJOIN` | `AllianceAutojoinRoutine` | 0 | 0 | 0 | 0 | geen / geen bewijs |
| `ALLIANCE_CHAMPIONSHIP` | `AllianceChampionshipRoutine` | 5 | 4 | 0 | 0 | geen / geen bewijs |
| `ALLIANCE_MOBILIZATION` | `AllianceMobilizationRoutine` | 20 | 10 | 0 | 0 | geen / geen bewijs |
| `ALLIANCE_PET_TREASURE` | `PetAllianceTreasuresRoutine` | 3 | 2 | 0 | 0 | geen / geen bewijs |
| `ALLIANCE_SHOP` | `AllianceShopRoutine` | 9 | 4 | 0 | 0 | geen / geen bewijs |
| `BANK` | `BankRoutine` | 4 | 5 | 0 | 0 | geen / geen bewijs |
| `BEAR_TRAP` | `BearTrapRoutine` | 12 | 14 | 0 | 0 | geen / geen bewijs |
| `BEAST_HUNTING` | `BeastSlayRoutine` | 4 | 4 | 0 | 0 | geen / geen bewijs |
| `CITY_UPGRADE_FURNACE` | `UpgradeBuildingsRoutine` | 13 | 13 | 0 | 0 | geen / geen bewijs |
| `CITY_UPGRADE_PRIORITISE_FURNACE` | `PrioritiseFurnaceRoutine` | 6 | 10 | 0 | 0 | geen / geen bewijs |
| `CREATE_CHARACTER` | `CreateCharacterRoutine` | 0 | 0 | 0 | 0 | geen / geen bewijs |
| `CRYSTAL_LABORATORY` | `CrystalLaboratoryRoutine` | 3 | 4 | 0 | 0 | geen / geen bewijs |
| `CUSTOM_TASK` | `onbekend` | 0 | 0 | 0 | 0 | geen / geen bewijs |
| `DUMMY_TASK` | `DummyRoutine` | 1 | 1 | 0 | 0 | geen / geen bewijs |
| `EVENT_BERSERK_CRYPTID` | `ManualRallyJoinRoutine` | 7 | 6 | 0 | 0 | geen / geen bewijs |
| `EVENT_HERO_MISSION` | `HeroMissionEventRoutine` | 7 | 5 | 0 | 0 | geen / geen bewijs |
| `EVENT_JOURNEY_OF_LIGHT` | `JourneyofLightRoutine` | 2 | 2 | 0 | 0 | geen / geen bewijs |
| `EVENT_MYRIAD_BAZAAR` | `MyriadBazaarEventRoutine` | 2 | 3 | 0 | 0 | geen / geen bewijs |
| `EVENT_POLAR_TERROR` | `PolarTerrorHuntingRoutine` | 9 | 9 | 0 | 0 | geen / geen bewijs |
| `EVENT_TUNDRA_TRUCK` | `TundraTruckEventRoutine` | 8 | 12 | 0 | 0 | geen / geen bewijs |
| `EXPERT_AGNES_INTEL` | `ExpertsAgnesIntelRoutine` | 1 | 1 | 0 | 0 | geen / geen bewijs |
| `EXPERT_ROMULUS_TAG` | `ExpertsRomulusTagRoutine` | 3 | 2 | 0 | 0 | geen / geen bewijs |
| `EXPERT_ROMULUS_TROOPS` | `ExpertsRomulusTroopsRoutine` | 5 | 2 | 0 | 0 | geen / geen bewijs |
| `EXPERT_SKILL_TRAINING` | `ExpertSkillTrainingRoutine` | 9 | 8 | 0 | 0 | geen / geen bewijs |
| `FISHING_MINIGAME` | `FishingMinigameRoutine` | 14 | 7 | 0 | 0 | geen / geen bewijs |
| `GAME_ANALYTICS_LABYRINTH` | `GameAnalyticsRoutine` | 0 | 0 | 0 | 0 | geen / geen bewijs |
| `GAME_ANALYTICS_POWER` | `GameAnalyticsRoutine` | 0 | 0 | 0 | 0 | geen / geen bewijs |
| `GATHER_RESOURCES` | `GatherRoutine` | 15 | 14 | 0 | 0 | geen / geen bewijs |
| `HERO_RECRUITMENT` | `HeroRecruitmentRoutine` | 15 | 19 | 0 | 0 | geen / geen bewijs |
| `INITIALIZE` | `InitializeRoutine` | 10 | 10 | 0 | 0 | geen / geen bewijs |
| `INTEL` | `IntelligenceRoutine` | 28 | 22 | 0 | 0 | geen / geen bewijs |
| `LABYRINTH` | `DailyLabyrinthRoutine` | 7 | 6 | 0 | 0 | geen / geen bewijs |
| `MERCENARY_EVENT` | `MercenaryEventRoutine` | 13 | 9 | 0 | 0 | geen / geen bewijs |
| `NOMADIC_MERCHANT` | `NomadicMerchantRoutine` | 3 | 2 | 0 | 0 | geen / geen bewijs |
| `PET_ADVENTURE` | `PetAdventureChestRoutine` | 11 | 9 | 0 | 0 | geen / geen bewijs |
| `PET_SKILLS` | `PetSkillsRoutine` | 11 | 9 | 0 | 0 | geen / geen bewijs |
| `SHOP_MYSTERY` | `MysteryShopRoutine` | 6 | 7 | 0 | 0 | geen / geen bewijs |
| `SKIP_TUTORIAL` | `SkipTutorialRoutine` | 3 | 3 | 0 | 0 | geen / geen bewijs |
| `TEST_HOOK_LOOP` | `TestHookLoopRoutine` | 1 | 0 | 0 | 0 | geen / geen bewijs |
| `TREK_AUTOMATION` | `TundraTrekAutoRoutine` | 7 | 10 | 0 | 0 | geen / geen bewijs |
| `TREK_SUPPLIES` | `TundraTrekRoutine` | 2 | 2 | 0 | 0 | geen / geen bewijs |
| `VIP_POINTS` | `VipRoutine` | 7 | 11 | 0 | 0 | geen / geen bewijs |
| `WAR_ACADEMY` | `WarAcademyRoutine` | 3 | 3 | 0 | 0 | geen / geen bewijs |

## Legenda

De bronkolommen tellen statische Java-verwijzingen/aanroepen, niet de uitgevoerde acties of dynamische helpers. Alle taken gebruiken bovendien gedeelde navigatie en herkenningsengine.
`review_only_optional_or_stale`: nul treffers in deze runs. Niet automatisch verwijderen.
`protected_safety_guard`: nul treffers zijn normaal tijdens gezond spel.
`retired_on_serve_emu`: concrete bewezen oude variant, enkel voor Serve-EMU uitgeschakeld.

Voor actuele details, inclusief per-template scores en methode, gebruik het Templates-tabblad van het QA-dashboard.
