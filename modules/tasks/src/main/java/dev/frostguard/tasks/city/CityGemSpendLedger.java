package dev.frostguard.tasks.city;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Durable, conservative reservation written before the paid tap.
 * If Android crashes, the button fails, or a purchase confirmation is unknown,
 * keep the reservation: retries must not accidentally spend twice.
 */
final class CityGemSpendLedger {
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Europe/Amsterdam");
    private CityGemSpendLedger() {}

    static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), ".survivalops", "city-upgrade-gem-reservations");
    }

    static synchronized int remaining(long profileId, int dailyLimit) throws IOException {
        return remaining(defaultDirectory(), profileId, LocalDate.now(LOCAL_ZONE), dailyLimit);
    }

    static synchronized int remaining(Path directory, long profileId, LocalDate date,
                                      int dailyLimit) throws IOException {
        if (dailyLimit <= 0 || dailyLimit > 10_000 || profileId < 1)
            return 0;
        int used = readUsed(directory, profileId, date);
        return Math.max(0, dailyLimit - used);
    }

    static synchronized boolean reserve(long profileId, int dailyLimit, int amount) throws IOException {
        return reserve(defaultDirectory(), profileId, LocalDate.now(LOCAL_ZONE), dailyLimit, amount);
    }

    static synchronized boolean reserve(Path directory, long profileId, LocalDate date,
                                        int dailyLimit, int amount) throws IOException {
        if (dailyLimit < 1 || dailyLimit > 10_000
                || amount < 1 || amount > dailyLimit || profileId < 1)
            return false;
        Files.createDirectories(directory);
        int used = readUsed(directory, profileId, date);
        if (used > dailyLimit - amount) return false;
        Path dest = filename(directory, profileId, date);
        Path temp = Files.createTempFile(directory, "reserve-", ".tmp");
        try {
            Files.writeString(temp, Integer.toString(used + amount),
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    private static int readUsed(Path dir, long id, LocalDate day) throws IOException {
        Path file = filename(dir, id, day);
        if (!Files.exists(file)) return 0;
        String s = Files.readString(file).trim();
        if (!s.matches("\\d{1,7}")) {
            throw new IOException("City gem reservation journal is unreadable; purchases blocked");
        }
        int value = Integer.parseInt(s);
        if (value < 0 || value > 100_000) {
            throw new IOException("City gem reservation journal invalid; purchases blocked");
        }
        return value;
    }

    private static Path filename(Path dir,long id,LocalDate day) {
        return dir.resolve("profile-" + id + "-" + day + ".txt");
    }
}
