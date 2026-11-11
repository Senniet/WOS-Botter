package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.engine.emulator.AndroidFrameStream;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.ImageSearchResultData;
import java.io.IOException;

/** Shared live/test navigation: suspension never selects a replacement Normal Cast. */
final class FishingRetryNavigator {
    interface Port {
        AndroidFrameStream.Frame frame();
        void fresh();
        void release() throws IOException;
        void tap(AreaData area);
        void checkCancellation();
    }
    private static final AreaData PAUSE = AreaData.of(645, 0, 720, 85);
    private static final AreaData CONTINUE = AreaData.of(390, 570, 535, 710);
    private static final AreaData EXIT = AreaData.of(185, 570, 330, 710);
    private static final AreaData GO_FISH = AreaData.of(270, 1040, 465, 1130);
    private static final AreaData TITLE = AreaData.of(85, 0, 500, 80);
    // The 2026 stage-resume overlay has a bright, centred Go Fish button.
    // Its artwork is animated; require a title anchor and the white-labelled
    // blue button before accepting the fallback on two different frames.
    private static final BlueLabelActionDetector.Region GO_FISH_BLUE =
            new BlueLabelActionDetector.Region(225, 1042, 495, 1135, 299, 1065, 425, 1110);

    private FishingRetryNavigator() { }

    static void suspend(Port port) throws IOException {
        port.checkCancellation();
        port.release();
        port.fresh();
        tapVerified(port, TemplatesEnum.FISHING_PAUSE, PAUSE);
        await(port, TemplatesEnum.FISHING_CONTINUE, CONTINUE);
        tapVerified(port, TemplatesEnum.FISHING_PAUSE_EXIT, EXIT);
        await(port, TemplatesEnum.FISHING_GO_FISH, GO_FISH);
        if (!hit(port, TemplatesEnum.FISHING_TITLE, TITLE))
            throw new IllegalStateException("Suspended Fishing page unverified; no replacement bait authorized");
    }

    static void resume(Port port) {
        port.checkCancellation();
        port.fresh();
        if (!hit(port, TemplatesEnum.FISHING_TITLE, TITLE))
            throw new IllegalStateException("Suspended Fishing title unverified");
        ImageSearchResultData first = scan(port, TemplatesEnum.FISHING_GO_FISH, GO_FISH);
        if (!first.isFound())
            throw new IllegalStateException("Go Fish resume action unverified; no bait selected");
        port.fresh();
        if (!hit(port, TemplatesEnum.FISHING_TITLE, TITLE))
            throw new IllegalStateException("Fishing title lost before resume tap");
        ImageSearchResultData second = scan(port, TemplatesEnum.FISHING_GO_FISH, GO_FISH);
        if (!RecognitionConsensus.agrees(first, second, 12))
            throw new IllegalStateException("Go Fish not stable across fresh frames; no bait selected");
        tapVerified(port, TemplatesEnum.FISHING_GO_FISH, GO_FISH);
        // This resumes the existing paid stage. Never select a new Normal Cast.
        await(port, TemplatesEnum.FISHING_PAUSE, PAUSE, 15000);
    }

    // Only Go Fish is allowed a slightly lower threshold (real 2026 screen
    // scored 85.48%). The title and two matching frames must also be present.
    static int thresholdFor(TemplatesEnum template) {
        return template == TemplatesEnum.FISHING_GO_FISH ? 85 : 90;
    }

    static ImageSearchResultData goFishControl(RawImageData frame) {
        var template = OpenCvPatternLocator.locatePattern(frame, TemplatesEnum.FISHING_GO_FISH.getTemplate(),
                GO_FISH.topLeft(), GO_FISH.bottomRight(), thresholdFor(TemplatesEnum.FISHING_GO_FISH));
        if (template.isFound()) return template;
        if (!OpenCvPatternLocator.locatePattern(frame, TemplatesEnum.FISHING_TITLE.getTemplate(),
                TITLE.topLeft(), TITLE.bottomRight(), 90).isFound()) return ImageSearchResultData.miss();
        return BlueLabelActionDetector.detect(ImageConverter.toBufferedImage(frame), GO_FISH_BLUE);
    }

    private static ImageSearchResultData scan(Port port, TemplatesEnum template, AreaData area) {
        if (template == TemplatesEnum.FISHING_GO_FISH) return goFishControl(port.frame().image());
        return OpenCvPatternLocator.locatePattern(port.frame().image(), template.getTemplate(),
                area.topLeft(), area.bottomRight(), thresholdFor(template));
    }

    private static boolean hit(Port port, TemplatesEnum template, AreaData area) {
        return scan(port, template, area).isFound();
    }

    private static void tapVerified(Port port, TemplatesEnum template, AreaData area) {
        var match = scan(port, template, area);
        if (!match.isFound() || !match.hasMatchedArea())
            throw new IllegalStateException("Fishing retry control unverified: " + template);
        port.checkCancellation();
        long age = System.nanoTime() - port.frame().receivedNanos();
        if (age < 0 || age > 250_000_000L) throw new IllegalStateException("Fishing retry frame stale; no input sent");
        port.tap(match.getMatchedArea());
    }

    private static void await(Port port, TemplatesEnum template, AreaData area) {
        await(port, template, area, 5000);
    }

    private static void await(Port port, TemplatesEnum template, AreaData area, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        do {
            port.checkCancellation();
            port.fresh();
            if (hit(port, template, area)) return;
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Fishing retry control timed out: " + template);
    }
}
