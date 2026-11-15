package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fast, task-specific screen checks on ONE captured frame. A Task Coach run
 * must not have to search unrelated Alliance, Rewards or offer templates just
 * to leave a confirmed troop-training screen.
 */
final class TrainingScopedScreen {
    private static final PointData TAB_START = new PointData(0,1160);
    private static final PointData TAB_END = new PointData(248,1275);
    private static final PointData BACK_START = new PointData(0,0);
    private static final PointData BACK_END = new PointData(105,105);
    private static final PointData CITY_START = new PointData(590,1145);
    private static final PointData CITY_END = new PointData(715,1275);
    private static final int MAX_BACK_ERROR = 14;

    private TrainingScopedScreen() { }

    static boolean isTrainingPanel(RawImageData frame) {
        if (!is720Frame(frame))return false;
        // The modern selected Infantry tab has the same artwork but requires
        // a slightly different scale from the legacy 37x37 sprite. Testing
        // just the narrow footer ROI avoids general-purpose screen scans.
        if (OpenCvPatternLocator.locatePatternMultiScale(frame,
                TemplatesEnum.TRAINING_CAMP_INFANTRY_TAB_ACTIVE.getTemplate(),
                TAB_START, TAB_END, 89).isFound())return true;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.TRAINING_CAMP_INFANTRY_TAB_LABEL_2026.getTemplate(),
                TAB_START, TAB_END, 90).isFound();
    }

    static ImageSearchResultData backArrowOnTrainingPanel(RawImageData frame) {
        if (!isTrainingPanel(frame))return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.GAME_HEADER_BACK_ARROW_CURRENT.getTemplate(),
                BACK_START, BACK_END, 90);
    }

    static boolean isCityHome(RawImageData frame) {
        return is720Frame(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                CITY_START, CITY_END, 90).isFound();
    }

    /**
     * A single safe Back from the known troop panel. No taps on unknown pages.
     * When the panel cannot be verified, the caller may use the original
     * generic navigator. When the verified panel is still present after a tap,
     * the caller MUST abort instead of probing unrelated screens indefinitely.
     */
    static ExitResult tryExit(Supplier<RawImageData> capture, Consumer<PointData> tap,
                              Runnable pause) {
        RawImageData firstFrame = capture.get();
        if (!isTrainingPanel(firstFrame))return ExitResult.NOT_TRAINING;
        ImageSearchResultData first = backArrowOnTrainingPanel(firstFrame);
        if (!first.isFound())return ExitResult.UNVERIFIED;
        pause.run();
        RawImageData secondFrame = capture.get();
        ImageSearchResultData second = backArrowOnTrainingPanel(secondFrame);
        if (!second.isFound() || !sameArrow(first,second))return ExitResult.UNVERIFIED;
        tap.accept(second.getPoint());
        pause.run();
        RawImageData result = capture.get();
        if (isCityHome(result))return ExitResult.RETURNED_HOME;
        pause.run();
        return isCityHome(capture.get())?ExitResult.RETURNED_HOME:ExitResult.UNVERIFIED;
    }

    enum ExitResult { NOT_TRAINING, RETURNED_HOME, UNVERIFIED }

    private static boolean sameArrow(ImageSearchResultData first, ImageSearchResultData second) {
        return Math.abs(first.getPoint().getX()-second.getPoint().getX())<=MAX_BACK_ERROR
                && Math.abs(first.getPoint().getY()-second.getPoint().getY())<=MAX_BACK_ERROR;
    }

    private static boolean is720Frame(RawImageData frame) {
        return frame!=null&&frame.isValid()&&frame.getWidth()==720&&frame.getHeight()==1280;
    }
}
