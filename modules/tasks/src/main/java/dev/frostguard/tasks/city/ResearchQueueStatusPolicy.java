package dev.frostguard.tasks.city;

import java.util.Locale;

/** Conservative interpretation of the research queue's OCR status label. */
final class ResearchQueueStatusPolicy {
    private ResearchQueueStatusPolicy() {
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
