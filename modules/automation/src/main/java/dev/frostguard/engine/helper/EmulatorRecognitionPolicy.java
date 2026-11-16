package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.engine.emulator.EmulatorType;
import java.util.EnumSet;
import java.util.Set;

/**
 * The selected emulator must be a first-class input to screen recognition.
 *
 * An obsolete variant is blocked ONLY for SERVE_EMU where a CURRENT or
 * NOV_2026 counterpart was proven on the same actual 720x1280 screen. The
 * historical image remains available to other emulator families.
 *
 * The automatic multi-scale/monochrome retry is optional for *precisely*
 * calibrated 2026 passive screen titles. City/World HUD require fallback:
 * real Task Coach sessions have successful multiscale/mono matches. For action buttons, reconnect,
 * offers, unknown overlays and other unvalidated templates the resilient
 * legacy search and two-frame action verification remain unchanged.
 */
public final class EmulatorRecognitionPolicy {
    private static final Set<TemplatesEnum> SERVE_EMU_RETIRED = EnumSet.of(
            TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE,
            TemplatesEnum.EXPLORATION_SCREEN_TITLE,
            TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE,
            TemplatesEnum.EXPLORATION_IDLE_INCOME_TITLE,
            TemplatesEnum.SERVE_EMU_WORLD_ANCHOR);

    private static final Set<TemplatesEnum> SERVE_EMU_EXACT_HEADERS = EnumSet.of(
            TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026,
            TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026,
            // Current Alliance titles were observed at high-confidence color
            // matches while repeated fallback passes never contributed a hit.
            TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026,
            TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026);

    private final EmulatorType emulator;

    private EmulatorRecognitionPolicy(EmulatorType emulator) { this.emulator=emulator; }

    public static EmulatorRecognitionPolicy forEmulator(EmulatorType type) {
        return new EmulatorRecognitionPolicy(type);
    }

    public boolean shouldProbe(TemplatesEnum template) {
        return emulator != EmulatorType.SERVE_EMU || !SERVE_EMU_RETIRED.contains(template);
    }

    public boolean useImplicitFallback(TemplatesEnum template) {
        return emulator != EmulatorType.SERVE_EMU
                || !SERVE_EMU_EXACT_HEADERS.contains(template);
    }

    public boolean useScaledAlternative(TemplatesEnum template) {
        return emulator != EmulatorType.SERVE_EMU
                || !SERVE_EMU_EXACT_HEADERS.contains(template);
    }

    public boolean isServeEmu() { return emulator == EmulatorType.SERVE_EMU; }
}
