package dev.frostguard.tasks.pets;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.ocr.OcrEngine;
import java.util.Locale;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Current 720x1280 Pet Skill overlay, recorded on live Serve-EMU in Nov 2026.
 * The tile order reflects unlocked pets, not the old fixed Stamina/Food/... order.
 * Read the selected skill and bottom action before any free activation.
 */
final class PetSkillsCurrentScreen {
    enum Skill {
        STAMINA, GATHERING, FOOD, TREASURE, OTHER, UNKNOWN
    }
    enum State {
        LOCKED, COOLDOWN, READY, UNKNOWN
    }

    private static final OcrSettingsData TEXT = OcrSettingsData.assembler()
            .charWhitelist("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz' .0123456789:")
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();

    private PetSkillsCurrentScreen() {}

    static boolean isCurrentMenu(RawImageData frame) {
        if (frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return false;
        return normalize(read(frame,240,123,485,182)).contains("PET SKILL");
    }

    static Skill selectedSkill(RawImageData frame) {
        String title = normalize(read(frame,65,770,432,825));
        // These are the actual current skill names; never infer identity
        // from a grid index (Builder's Aide is now the first tile).
        if (title.contains("ARCTIC EMBRACE")) return Skill.STAMINA;
        // Unknown new pet skills must be inspected before enabling actions.
        if (title.contains("BUILDER") || title.contains("AIDE")) return Skill.OTHER;
        return Skill.UNKNOWN;
    }

    static State selectedState(RawImageData frame) {
        String footer = normalize(read(frame,70,1050,675,1145));
        if (footer.contains("UNLOCK")) return State.LOCKED;
        if (footer.contains("COOLDOWN")) return State.COOLDOWN;
        if (footer.equals("USE") || footer.startsWith("USE ")) return State.READY;
        return State.UNKNOWN;
    }

    static Duration remainingCooldown(RawImageData frame) {
        var timer = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})")
                .matcher(read(frame,70,1050,675,1145));
        if (!timer.find()) return null;
        try {
            return Duration.ofHours(Integer.parseInt(timer.group(1)))
                    .plusMinutes(Integer.parseInt(timer.group(2)))
                    .plusSeconds(Integer.parseInt(timer.group(3)));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    static PointData[] visibleTileCenters() {
        return new PointData[]{
                new PointData(140,303),new PointData(285,303),
                new PointData(430,303),new PointData(575,303),
                new PointData(140,452),new PointData(285,452),
                new PointData(430,452),new PointData(575,452),
                new PointData(140,602),new PointData(285,602),
                new PointData(430,602),new PointData(575,602)
        };
    }

    static String normalize(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]"," ").replaceAll(" +"," ").trim();
    }

    private static String read(RawImageData frame,int left,int top,int right,int bottom) {
        if (frame==null || !frame.isValid()) return "";
        try {
            return OcrEngine.recognizeText(frame,
                    new PointData(left,top),new PointData(right,bottom),TEXT);
        } catch (dev.frostguard.vision.ocr.OcrException unrecognized) {
            // Unknown text must never authorize a free-skill activation.
            return "";
        }
    }
}
