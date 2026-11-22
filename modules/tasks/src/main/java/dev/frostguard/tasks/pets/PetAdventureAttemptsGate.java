package dev.frostguard.tasks.pets;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Persisted proof of exhausted adventure starts; a green Daily badge is not a new attempt. */
public final class PetAdventureAttemptsGate {
    private PetAdventureAttemptsGate() {}

    public static boolean blockedUntilReset(String exhaustedUntil, LocalDateTime now) {
        if (exhaustedUntil == null || exhaustedUntil.isBlank() || now == null) return false;
        try {
            return now.isBefore(LocalDateTime.parse(exhaustedUntil));
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }
}
