package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live 720x1280 Chief Orders screen classifier (Nov 2026).
 * No paid Enact is allowed without explicit authorization.
 * Unknown OCR, wrong order or a generic blue button never means FREE.
 */
final class ChiefOrderLiveFrame {
    enum Action { FREE, COSTS_POINTS, UNVERIFIED }

    record Inspection(Action action, long cost, String order, String button, String price) {}

    private static final OcrSettingsData TEXT = OcrSettingsData.assembler()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
    private static final OcrSettingsData AMOUNTS = OcrSettingsData.assembler()
            .charWhitelist("0123456789,.")
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
    private static final Pattern AMOUNT = Pattern.compile("\\d[\\d,.]*");

    private ChiefOrderLiveFrame() {}

    static boolean isChiefOrderList(RawImageData frame) {
        return valid(frame) && norm(read(frame, 252, 97, 490, 164, TEXT))
                .equals("CHIEF ORDER");
    }

    static boolean isOrderBook(RawImageData frame, String expectedOrder) {
        return valid(frame) && expectedOrder != null
                && norm(read(frame, 200, 552, 540, 630, TEXT)).equals(norm(expectedOrder));
    }

    static Inspection inspect(RawImageData frame, String expectedOrder) {
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
