package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A one-tap Rewards exit guarded by two independent frames. Never press
 * unknown overlays, other task menus, Enact or any premium/purchase control.
 */
final class ChiefOrderRewardFrame {
    private static final PointData TITLE_START = new PointData(30, 210);
    private static final PointData TITLE_END = new PointData(690, 490);
    private static final PointData SAFE_EXIT_POINT = new PointData(360, 1190);
    private ChiefOrderRewardFrame() {}

    static boolean isRewards(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280) return false;
        // On the 1.1.68 Rush Job frame the original banner matches at 98.85%;
        // the alternate CURRENT template scored 89.75 below its 92% threshold.
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SHARED_REWARDS_BANNER.getTemplate(),
                TITLE_START, TITLE_END, 90).isFound();
    }

    enum Exit { NOT_PRESENT, DISMISSED, UNVERIFIED }

    static Exit dismissIfVisible(Supplier<RawImageData> capture,
                                 Consumer<PointData> tap,
                                 Runnable settle) {
        if (!isRewards(capture.get())) return Exit.NOT_PRESENT;
        settle.run();
        if (!isRewards(capture.get())) return Exit.UNVERIFIED;
        tap.accept(SAFE_EXIT_POINT);
        settle.run();
        return isRewards(capture.get()) ? Exit.UNVERIFIED : Exit.DISMISSED;
    }
}
