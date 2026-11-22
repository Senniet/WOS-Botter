package dev.frostguard.tasks.city;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative quantity/price quote from the actual modern "Obtain more"
 * panel. Nothing is bought unless the resource type and the pack contents
 * agree and the smallest whole-pack top-up fits the verified wallet/budget.
 */
final class CityGemReplenishQuote {
    private static final Pattern FRACTION = Pattern.compile(
            "^\\s*([\\d, .]+)\\s*/\\s*([\\d, .]+)\\s*$");
    private static final Pattern PRODUCT = Pattern.compile(
            "^\\s*(\\d+)\\s*[kK]\\s+(Iron|Wood|Coal|Meat)\\s*(?:\\(\\s*Secured\\s*\\))?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DETAILS = Pattern.compile(
            "^\\s*Gives\\s+([\\d, ]+)\\s+(Iron|Wood|Coal|Meat)\\s*(?:\\(\\s*Secured\\s*\\))?\\s*\\.?\\s*$",
            Pattern.CASE_INSENSITIVE);
    record Quote(String resource, long have, long required,
                 long packSize, int unitGems, int packsNeeded,
                 long totalGems, long diamondsAvailable) {
        long deficit() { return required - have; }
    }

    record Progress(long have, long required) {}
    static Optional<Progress> readProgress(String text) {
        if (text == null) return Optional.empty();
        Matcher ratio = FRACTION.matcher(text);
        if (!ratio.matches()) return Optional.empty();
        Long have = positiveNumber(ratio.group(1), true);
        Long required = positiveNumber(ratio.group(2), false);
        if (have == null || required == null || have < 0)
            return Optional.empty();
        return Optional.of(new Progress(have, required));
    }

    private CityGemReplenishQuote() {}

    static Optional<Quote> parse(String ratio, String product, String details,
                                 String buttonText, String priceText, String walletText,
                                 int dailyRemaining) {
        if (dailyRemaining < 1 || ratio == null || product == null || details == null
                || buttonText == null || priceText == null || walletText == null)
            return Optional.empty();
        if (!buttonText.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals("buyuse"))
            return Optional.empty();

        Matcher fraction = FRACTION.matcher(ratio);
        Matcher pack = PRODUCT.matcher(product);
        Matcher content = DETAILS.matcher(details);
        if (!fraction.matches() || !pack.matches() || !content.matches())
            return Optional.empty();

        Long have = positiveNumber(fraction.group(1), true);
        Long required = positiveNumber(fraction.group(2), false);
        Long contentSize = positiveNumber(content.group(1), false);
        Long price = positiveNumber(priceText, false);
        Long wallet = positiveNumber(walletText, true);
        if (have == null || required == null || contentSize == null
                || price == null || wallet == null || price > Integer.MAX_VALUE
                || required <= have || have < 0) return Optional.empty();

        String resource = pack.group(2);
        if (!resource.equalsIgnoreCase(content.group(2)))
            return Optional.empty();
        long productQuantity = Long.parseLong(pack.group(1));
        if (productQuantity > Long.MAX_VALUE / 1_000L
                || productQuantity * 1_000L != contentSize)
            return Optional.empty();
        if (contentSize < 1_000 || contentSize > 1_000_000)
            return Optional.empty();

        long deficit = required - have;
        long count = deficit / contentSize + ((deficit % contentSize) != 0 ? 1 : 0);
        if (count <= 0 || count > 10 || count > Long.MAX_VALUE / price)
            return Optional.empty();
        long total = count * price;
        if (total > dailyRemaining || total > wallet)
            return Optional.empty();
        return Optional.of(new Quote(resource.toLowerCase(Locale.ROOT), have, required,
                contentSize, price.intValue(), (int)count, total, wallet));
    }

    private static Long positiveNumber(String value, boolean allowZero) {
        if (value == null) return null;
        String normalized = value.trim().replace(",", "").replace(" ", "");
        if (!normalized.matches("\\d{1,12}")) return null;
        try {
            long number = Long.parseLong(normalized);
            return allowZero || number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
