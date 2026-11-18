package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict read-only classifiers for the actual Serve-Emu 720x1280 Endless Trial screens.
 * The attack and Claim buttons are NEVER inferred from an arbitrary blue/green rectangle.
 */
public final class EndlessTrialLiveFrame {
    private static final OcrSettingsData TEXT = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
    private static final OcrSettingsData GO_TEXT = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.TEXT_BLOCK).build();
    private static final OcrSettingsData NUMBERS = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
            .allowedGlyphs("0123456789/:").build();
    private static final Pattern COUNTER = Pattern.compile("([0-3])\\s*/\\s*3");
    private static final Pattern TIMER = Pattern.compile("(\\d{1,2})\\s*[:.]\\s*(\\d{2})\\s*[:.]\\s*(\\d{2})");

    public enum GoState { READY, TIMED_GO, UNKNOWN }
    public record Availability(GoState state, Duration countdown, String caption) {}
    public record DailyProgress(boolean verified, int completedAttacks, String raw) {}

    private EndlessTrialLiveFrame() {}

    static boolean valid(RawImageData frame) {
        return frame != null && frame.isValid() && frame.getWidth() == 720 && frame.getHeight() == 1280;
    }

    /** Event header is specifically in the center-left of the event page, not the carousel. */
    public static boolean isTrialPage(RawImageData frame) {
        return valid(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_TITLE.getTemplate(),
                new PointData(12, 182), new PointData(550, 335), 88).isFound();
    }

    /** Event index after leaving a trial page; never press Back twice on the City/World HUD. */
    public static boolean isEventCarousel(RawImageData frame) {
        return valid(frame) && !isTrialPage(frame)
                && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_TAB.getTemplate(),
                new PointData(0,80),new PointData(720,210), 88).isFound();
    }

    public static boolean isDailyChallenges(RawImageData frame) {
        return valid(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_DAILY_CHALLENGES_TITLE.getTemplate(),
                new PointData(110, 275), new PointData(630, 415), 88).isFound();
    }

    /** The bottom-left progress on the main page displays e.g. 0/3. */
    public static DailyProgress readMainProgress(RawImageData frame) {
        if (!isTrialPage(frame)) return new DailyProgress(false, -1, "unverified-main-page");
        String text = read(frame, 50, 1165, 180, 1240, NUMBERS);
        return parseDailyProgress(text);
    }

    /** The *third* row of Daily Challenges shows n/3. Earlier rows are n/1, n/2. */
    public static DailyProgress readModalProgress(RawImageData frame) {
        if (!isDailyChallenges(frame)) return new DailyProgress(false, -1, "unverified-daily-challenges");
        String text = read(frame, 590, 785, 700, 850, NUMBERS);
        DailyProgress progress = parseDailyProgress(text);
        if (progress.verified()) return progress;
        if (allDailyRewardsClaimed(frame))
            return new DailyProgress(true, 3, "all three claimed check marks");
        // Each successive challenge becomes claimable at 1, 2 and 3 attacks.
        // An enabled third-row Claim is independent visual evidence of 3/3.
        // Priority matters: a user may have already claimed earlier tiers.
        if (claimButtonEnabled(frame, 3)) return new DailyProgress(true, 3, "third Claim is active");
        if (claimButtonEnabled(frame, 2)) return new DailyProgress(true, 2, "second Claim is active");
        if (claimButtonEnabled(frame, 1)) return new DailyProgress(true, 1, "first Claim is active");
        // If no active Claim is present, only the observed zero-progress frame
        // can establish 0/3. Already-claimed rewards MUST NOT be mistaken for 0.
        boolean zero = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_PROGRESS_0_OF_3.getTemplate(),
                new PointData(578, 775),new PointData(708, 858), 91).isFound();
        return zero ? new DailyProgress(true, 0, "0/3 visual template") : progress;
    }

    static DailyProgress parseDailyProgress(String text) {
        String normalized = text == null ? "" : text.replace('\\', '/').replace('I', '1');
        Matcher match = COUNTER.matcher(normalized);
        if (!match.find()) return new DailyProgress(false, -1, normalized);
        return new DailyProgress(true, Integer.parseInt(match.group(1)), normalized);
    }

    /** Page must be positively identified; Go is only actionable if no countdown is present. */
    public static Availability readGo(RawImageData frame) {
        if (!isTrialPage(frame)) return new Availability(GoState.UNKNOWN, null, "not-on-trial-page");
        String caption = read(frame, 218, 1136, 505, 1242, GO_TEXT);
        Availability ocr = parseGo(caption);
        // The timer under Go is how long the event is active, NOT an attack
        // cooldown. Go remains available and navigates to Windhowler.
        // Check the timer visual before trusting an OCR result of just "Go".
        boolean timed = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_TIMER_COLON.getTemplate(),
                new PointData(284, 1174),new PointData(451, 1230), 88).isFound();
        if (timed) return new Availability(GoState.TIMED_GO, null, "event duration countdown visible");
        if (ocr.state() != GoState.UNKNOWN) return ocr;
        boolean goLabel = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.ENDLESS_TRIAL_GO_LABEL.getTemplate(),
                new PointData(288, 1130),new PointData(445, 1225), 90).isFound();
        return goLabel ? new Availability(GoState.READY,null,"Go label without countdown") : ocr;
    }

    static Availability parseGo(String text) {
        String clean = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        Matcher time = TIMER.matcher(clean);
        if (time.find()) {
            int hours = Integer.parseInt(time.group(1));
            int minutes = Integer.parseInt(time.group(2));
            int seconds = Integer.parseInt(time.group(3));
            if (minutes < 60 && seconds < 60 && hours <= 24)
                return new Availability(GoState.TIMED_GO,
                        Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds), clean);
        }
        if (clean.matches("(?s).*\\bGO\\b.*") && !clean.matches(".*\\d{1,2}:\\d{2}.*"))
            return new Availability(GoState.READY, null, clean);
        return new Availability(GoState.UNKNOWN, null, clean);
    }

    /** The Claim control is in the third row, but never tap without 3/3 and a verified dialog. */
    public static boolean thirdClaimReady(RawImageData frame) {
        DailyProgress progress = readModalProgress(frame);
        if (!progress.verified() || progress.completedAttacks() != 3) return false;
        if (allDailyRewardsClaimed(frame)) return false;
        return claimButtonEnabled(frame, 3);
    }

    /**
     * The actual 0/3 screenshot has three grey disabled Claim buttons.
     * Eligible buttons change to green/blue; sample only the named row's
     * button interior. No unrelated panel can authorize this without
     * the caller's positive Daily Challenges screen check.
     */
    static boolean claimButtonEnabled(RawImageData frame, int row) {
        if (!valid(frame) || row < 1 || row > 3) return false;
        int centerY = switch (row) {case 1 -> 552; case 2 -> 717; default -> 888;};
        var pixels = ImageConverter.toBufferedImage(frame);
        int saturated = 0;
        for (int y=centerY-20;y<centerY+20;y+=2) for (int x=534;x<646;x+=2) {
            int rgb=pixels.getRGB(x,y);
            int red=(rgb>>16)&255, green=(rgb>>8)&255, blue=rgb&255;
            if ((green>133 && green>red+28 && green>blue*0.82)
                    || (blue>162 && blue>red+48 && green>red+18)) saturated++;
        }
        return saturated >= 55;
    }

    /** The three green checks prove Claim All has already completed. */
    public static boolean allDailyRewardsClaimed(RawImageData frame) {
        if (!isDailyChallenges(frame)) return false;
        int[] rowY = {543, 711, 882};
        for (int centerY : rowY) {
            if (!OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.ENDLESS_TRIAL_CLAIMED_MARK.getTemplate(),
                    new PointData(505,centerY-65),new PointData(655,centerY+65),90).isFound())
                return false;
        }
        return true;
    }

    static String read(RawImageData frame, int left, int top, int right, int bottom,
                       OcrSettingsData settings) {
        if (!valid(frame)) return "";
        try {
            return OcrEngine.recognizeText(frame, new PointData(left, top),
                    new PointData(right, bottom), settings);
        } catch (OcrException expected) {
            return "";
        } catch (RuntimeException exception) {
            TaskControlSignals.rethrowControlSignal(exception);
            return "";
        }
    }
}
