package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmulatorRecognitionPolicyTest {
    @Test void serveEmuAvoidsConfirmedOldVariants() {
        var policy=EmulatorRecognitionPolicy.forEmulator(EmulatorType.SERVE_EMU);
        for(var template:new TemplatesEnum[]{
                TemplatesEnum.EXPLORATION_SCREEN_TITLE,
                TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE,
                TemplatesEnum.EXPLORATION_IDLE_INCOME_TITLE,
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE,
                TemplatesEnum.SERVE_EMU_WORLD_ANCHOR,
                TemplatesEnum.SERVE_EMU_DAILY_TITLE,
                TemplatesEnum.GAME_HOME_WORLD,
                TemplatesEnum.ARENA_CHALLENGE_BUTTON}) {
            assertFalse(policy.shouldProbe(template),template.name());
        }
    }

    @Test void allOtherEmulatorsRetainOriginalTemplateBehaviour() {
        for(var emulator:EmulatorType.values()) {
            if(emulator==EmulatorType.SERVE_EMU)continue;
            var policy=EmulatorRecognitionPolicy.forEmulator(emulator);
            assertTrue(policy.shouldProbe(TemplatesEnum.SERVE_EMU_WORLD_ANCHOR),emulator.name());
            assertTrue(policy.shouldProbe(TemplatesEnum.GAME_HOME_WORLD),emulator.name());
            assertTrue(policy.shouldProbe(TemplatesEnum.SERVE_EMU_DAILY_TITLE),emulator.name());
            assertTrue(policy.shouldProbe(TemplatesEnum.ARENA_CHALLENGE_BUTTON),emulator.name());
            assertTrue(policy.useImplicitFallback(
                    TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026),emulator.name());
            assertTrue(policy.useScaledAlternative(
                    TemplatesEnum.SERVE_EMU_CITY_ANCHOR),emulator.name());
        }
    }

    @Test void serveEmuExactAnchorsSkipExpensiveImplicitFallback() {
        var policy=EmulatorRecognitionPolicy.forEmulator(EmulatorType.SERVE_EMU);
        for(var t:new TemplatesEnum[]{
                TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026,
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026,
                TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026,
                TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026}) {
            assertTrue(policy.shouldProbe(t),t.name());
            assertFalse(policy.useImplicitFallback(t),t.name());
        }
    }

    @Test void safetyGuardsAndActionButtonsNeverLoseFallbacks() {
        var policy=EmulatorRecognitionPolicy.forEmulator(EmulatorType.SERVE_EMU);
        for(var t:new TemplatesEnum[]{
                TemplatesEnum.GAME_HOME_RECONNECT,
                TemplatesEnum.DAILY_MISSION_SCREEN_TITLE,
                TemplatesEnum.ARENA_CHALLENGE_BUTTON_CURRENT,
                TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                TemplatesEnum.GAME_START_WELCOME_BACK_TITLE,
                TemplatesEnum.SERVE_EMU_OFFER_RESPLENDENT_TITLE_2026,
                TemplatesEnum.SHARED_REWARDS_BANNER,
                TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT,
                TemplatesEnum.TRAINING_TRAIN_BUTTON,
                TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE}) {
            assertTrue(policy.shouldProbe(t),t.name());
            assertTrue(policy.useImplicitFallback(t),t.name());
            assertTrue(policy.useScaledAlternative(t),t.name());
        }
    }

    @Test void unknownBackendDefaultsToUnmodifiedFrostguard() {
        var policy=EmulatorRecognitionPolicy.forEmulator(null);
        assertTrue(policy.shouldProbe(TemplatesEnum.EXPLORATION_SCREEN_TITLE));
        assertTrue(policy.useImplicitFallback(TemplatesEnum.SERVE_EMU_CITY_ANCHOR));
    }
}
