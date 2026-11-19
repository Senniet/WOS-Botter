package dev.frostguard.tasks.city;

import java.util.Locale;

/** Conservative interpretation of the research queue's OCR status label. */
final class ResearchQueueStatusPolicy {
    private ResearchQueueStatusPolicy() {
    }


    /** The Tech Research row shows ONLY this word when Research Center itself
     * is under construction; it is not a duration and must not be retried as one.
     * Construction must still be proven independently in another queue. */
    static boolean isBareUpgrading(String text) {
        if (text == null) return false;
        return text.strip().toLowerCase(Locale.ROOT)
                .matches("[^a-z0-9]*upgrading[^a-z0-9]*");
    }

    /** Only an explicit standalone "idle" label proves the queue is available. */
    static boolean isExplicitlyIdle(String text) {
        if (text == null) {
            return false;
        }
        String[] tokens = text.strip().toLowerCase(Locale.ROOT).split("\\s+");
        if (tokens.length != 1) {
            return false;
        }
        String label = tokens[0].replaceAll("^[^a-z]+|[^a-z]+$", "");
        return label.equals("idle");
    }
}
