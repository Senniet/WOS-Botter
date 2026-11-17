package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live 720x1280 Chief Orders screen classifier (Nov 2026).
 * No paid Enact is allowed without explicit authorization.
 * Unknown OCR, wrong order or a generic blue button never means FREE.
 */
public final class ChiefOrderLiveFrame {
    public enum Action { FREE, COSTS_POINTS, UNVERIFIED }
    public enum ListState { ON_COOLDOWN, ACTIVE, AVAILABLE, UNKNOWN }

    public record ListInspection(ListState state, Duration remaining, String label,
                          String status, String timer) {}

    public record Inspection(Action action, long cost, String order, String button, String price) {}

    private static final OcrSettingsData TEXT = OcrSettingsData.assembler()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
    private static final OcrSettingsData AMOUNTS = OcrSettingsData.assembler()
            .charWhitelist("0123456789,.")
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
    private static final Pattern AMOUNT = Pattern.compile("\\d[\\d,.]*");
    private static final Pattern COUNTDOWN = Pattern.compile(
            "(\\d{1,2})\\s*[:.]\\s*(\\d{2})\\s*[:.]\\s*(\\d{2})");

    private ChiefOrderLiveFrame() {}

    public static boolean isChiefOrderList(RawImageData frame) {
        return valid(frame) && norm(read(frame, 252, 97, 490, 164, TEXT))
                .equals("CHIEF ORDER");
    }

    public static boolean isOrderBook(RawImageData frame, String expectedOrder) {
        return valid(frame) && expectedOrder != null
                && norm(read(frame, 200, 552, 540, 630, TEXT)).equals(norm(expectedOrder));
    }

    /** Read the banner on the named tile, not an arbitrary cooldown elsewhere in the list. */
    public static ListInspection inspectList(RawImageData frame, String expectedOrder) {
        if (!isChiefOrderList(frame) || expectedOrder == null)
            return new ListInspection(ListState.UNKNOWN, null, "", "", "");
        // Positions observed on the real 720x1280 Chief Order board.
        int left;
        int titleTop;
        int bannerTop;
        if (norm(expectedOrder).equals("RUSH JOB")) {
            left = 389; titleTop = 445; bannerTop = 302;
        } else if (norm(expectedOrder).equals("URGENT MOBILIZATION")) {
            left = 105; titleTop = 445; bannerTop = 302;
        } else if (norm(expectedOrder).equals("DOUBLE TIME")) {
            left = 389; titleTop = 764; bannerTop = 623;
        } else if (norm(expectedOrder).equals("PRODUCTIVITY DAY")) {
            left = 105; titleTop = 1086; bannerTop = 948;
        } else {
            return new ListInspection(ListState.UNKNOWN, null, "", "", "");
        }
        int right = left + 225;
        String label = read(frame, left, titleTop, right, titleTop + 52, TEXT);
        String status = read(frame, left, bannerTop, right, bannerTop + 38, TEXT);
        String timer = read(frame, left, bannerTop + 31, right, bannerTop + 70, TEXT);
        return classifyList(label, status, timer, expectedOrder);
    }

    public static Long availableOrderPoints(RawImageData frame) {
        if (!isChiefOrderList(frame)) return null;
        return parseOrderPoints(read(frame, 584, 15, 675, 67, TEXT));
    }

    /** Compact displayed Chief Order points, e.g. 4.2M. Unclear OCR never authorizes spending. */
    public static Long parseOrderPoints(String value) {
        if (value == null) return null;
        String clean=value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        Matcher matcher=Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)([KMB]?)$").matcher(clean);
        if (!matcher.matches()) return null;
        try {
            String raw=matcher.group(1);
            String suffix=matcher.group(2);
            // Without a compact K/M/B suffix, a 3-digit group after a
            // thousands separator is NOT a decimal fraction: 800,000.
            String digits=suffix.isEmpty() && raw.matches("[0-9]{1,3}[,.][0-9]{3}")
                    ? raw.replaceAll("[,.]","") : raw.replace(',', '.');
            long multiplier=switch(suffix) {
                case "K" -> 1_000L;
                case "M" -> 1_000_000L;
                case "B" -> 1_000_000_000L;
                default -> 1L;
            };
            return java.math.BigDecimal.valueOf(multiplier)
                    .multiply(new java.math.BigDecimal(digits)).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            return null;
        }
    }

    static ListInspection classifyList(String label, String status, String timer,
                                        String expectedOrder) {
        if (expectedOrder == null || !norm(expectedOrder).equals(norm(label)))
            return new ListInspection(ListState.UNKNOWN, null, label, status, timer);
        String visibleStatus = norm(status);
        Duration remaining = countdown(timer);
        if (visibleStatus.replace(" ", "").contains("ONCOOLDOWN"))
            return new ListInspection(ListState.ON_COOLDOWN, remaining, label, status, timer);
        if (visibleStatus.contains("ACTIVE") && !visibleStatus.contains("INACTIVE"))
            return new ListInspection(ListState.ACTIVE, remaining, label, status, timer);
        // OCR sees tiny scraps of the icon on an unwrapped, selectable tile.
        // This is only a candidate: the actual icon template must still match
        // before the routine is allowed to open its book.
        if (visibleStatus.length() <= 3)
            return new ListInspection(ListState.AVAILABLE, null, label, status, timer);
        return new ListInspection(ListState.UNKNOWN, null, label, status, timer);
    }

    private static Duration countdown(String text) {
        Matcher matcher = COUNTDOWN.matcher(text == null ? "" : text);
        if (!matcher.find()) return null;
        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        int seconds = Integer.parseInt(matcher.group(3));
        if (hours > 23 || minutes > 59 || seconds > 59) return null;
        return Duration.ofSeconds(hours * 3600L + minutes * 60L + seconds);
    }

    public static Inspection inspect(RawImageData frame, String expectedOrder) {
        if (!valid(frame)) return unknown();
        String order = read(frame, 200, 552, 540, 630, TEXT);
        String button = read(frame, 296, 886, 432, 926, TEXT);
        String digits = read(frame, 295, 917, 465, 965, AMOUNTS);
        String priceText = read(frame, 295, 917, 465, 965, TEXT);
        return classify(order, button, digits, priceText, expectedOrder);
    }

    static Inspection classify(String order, String button, String digits, String priceText,
                               String expectedOrder) {
        if (expectedOrder == null || !norm(order).equals(norm(expectedOrder))
                || !norm(button).contains("ENACT"))
            return new Inspection(Action.UNVERIFIED, -1, order, button, priceText);

        String costString = digits == null ? "" : digits;
        Matcher number = AMOUNT.matcher(costString);
        if (number.find()) {
            try {
                long amount = Long.parseLong(number.group().replaceAll("[^0-9]", ""));
                if (amount > 0)
                    return new Inspection(Action.COSTS_POINTS, amount, order, button, costString);
            } catch (NumberFormatException ignored) {
                return new Inspection(Action.UNVERIFIED, -1, order, button, costString);
            }
        }
        // A plain 0, missing OCR result, or a blue Enact without a verified
        // explicit "Free" is UNKNOWN. Never mistake missing price for free.
        if (norm(priceText).equals("FREE"))
            return new Inspection(Action.FREE, 0, order, button, priceText);
        return new Inspection(Action.UNVERIFIED, -1, order, button, priceText);
    }

    public static boolean isCityHome(RawImageData frame) {
        return valid(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                new PointData(590, 1145), new PointData(715, 1275), 90).isFound();
    }

    static boolean valid(RawImageData frame) {
        return frame != null && frame.isValid()
                && frame.getWidth() == 720 && frame.getHeight() == 1280;
    }

    private static Inspection unknown() {
        return new Inspection(Action.UNVERIFIED, -1, "", "", "");
    }

    private static String read(RawImageData frame, int left, int top, int right, int bottom,
                               OcrSettingsData settings) {
        try {
            return OcrEngine.recognizeText(frame, new PointData(left, top),
                    new PointData(right, bottom), settings);
        } catch (OcrException ignored) {
            return "";
        } catch (RuntimeException unexpected) {
            TaskControlSignals.rethrowControlSignal(unexpected);
            return "";
        }
    }

    private static String norm(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ").replaceAll(" +", " ").trim();
    }
}
