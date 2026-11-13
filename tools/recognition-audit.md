# SurvivalOps recognition audit

The inventory below counts source call sites, not successful recognitions.
Production acceptance still requires recorded BlueStacks test runs for each task.

| Task class | Recognition call sites | Tap call sites |
|---|---:|---:|
| AllianceChampionshipRoutine.java | 4 | 11 |
| AllianceChestRoutine.java | 4 | 9 |
| AllianceMobilizationRoutine.java | 7 | 13 |
| AllianceShopRoutine.java | 4 | 10 |
| AllianceTechRoutine.java | 2 | 5 |
| CrystalLaboratoryRoutine.java | 4 | 3 |
| PrioritiseFurnaceRoutine.java | 10 | 22 |
| ResearchRoutine.java | 12 | 12 |
| TrainingRoutine.java | 12 | 25 |
| UpgradeBuildingsRoutine.java | 13 | 15 |
| WarAcademyRoutine.java | 2 | 7 |
| ArenaRoutine.java | 7 | 12 |
| BearTrapRoutine.java | 14 | 22 |
| BeastSlayRoutine.java | 5 | 7 |
| ManualRallyJoinRoutine.java | 3 | 4 |
| PolarTerrorHuntingRoutine.java | 9 | 13 |
| ChiefOrderRoutine.java | 3 | 3 |
| DailyMissionRoutine.java | 6 | 5 |
| IntelligenceRoutine.java | 16 | 27 |
| MailRewardsRoutine.java | 3 | 4 |
| NewSurvivorsRoutine.java | 4 | 4 |
| TriumphRoutine.java | 4 | 4 |
| VipRoutine.java | 2 | 6 |
| BankRoutine.java | 5 | 5 |
| GatherRoutine.java | 11 | 15 |
| MyriadBazaarEventRoutine.java | 3 | 2 |
| MysteryShopRoutine.java | 6 | 5 |
| NomadicMerchantRoutine.java | 2 | 6 |
| StorehouseChestRoutine.java | 3 | 5 |
| TemplateStorehouseSearch.java | 2 | 0 |
| FishingDepthMonitor.java | 2 | 0 |
| FishingMinigameRoutine.java | 4 | 5 |
| FishingRetryNavigator.java | 2 | 0 |
| HeroMissionEventRoutine.java | 4 | 6 |
| JourneyofLightRoutine.java | 2 | 3 |
| MercenaryEventRoutine.java | 9 | 9 |
| DailyLabyrinthRoutine.java | 6 | 9 |
| DoExplorationRoutine.java | 2 | 6 |
| ExplorationRoutine.java | 3 | 4 |
| TundraTrekAutoRoutine.java | 10 | 9 |
| TundraTrekRoutine.java | 2 | 2 |
| TundraTruckEventRoutine.java | 10 | 15 |
| ExpertSkillTrainingRoutine.java | 8 | 14 |
| ExpertsAgnesIntelRoutine.java | 1 | 1 |
| ExpertsRomulusTagRoutine.java | 2 | 3 |
| ExpertsRomulusTroopsRoutine.java | 2 | 2 |
| HeroRecruitmentRoutine.java | 1 | 3 |
| DummyRoutine.java | 1 | 0 |
| InitializeRoutine.java | 8 | 5 |
| SkipTutorialRoutine.java | 3 | 2 |
| LifeEssenceCaringRoutine.java | 4 | 6 |
| LifeEssenceRoutine.java | 4 | 11 |
| PetAdventureChestRoutine.java | 6 | 6 |
| PetAllianceTreasuresRoutine.java | 2 | 2 |
| PetSkillsRoutine.java | 9 | 10 |

The template asset audit runs in CI. Known missing assets are tracked by `template-missing-baseline.json` and **must be repaired**, not suppressed as successes.
