package dev.frostguard.tasks.heroes;

/** Conservative rules for opt-in recruitment using already-owned keys. */
final class HeroRecruitmentKeyPolicy {
    private HeroRecruitmentKeyPolicy() {}

    /** OCR must show an isolated, plausible count. An unreadable count blocks spending. */
    static int countFromOcr(String text) {
        if (text == null) return -1;
        String normalized = text.trim().replace('I', '1').replace('l', '1').replace('|', '1');
        if (!normalized.matches("[0-9]{1,2}")) return -1;
        return Integer.parseInt(normalized);
    }

    static boolean mayRecruitWithKey(boolean enabled, int firstCount, int secondCount,
                                     boolean correctKeyButtonFound) {
        return enabled && correctKeyButtonFound && firstCount >= 1 && firstCount == secondCount;
    }

    static boolean verifiedSingleKeySpent(int before, int after) {
        return before >= 1 && after >= 0 && after == before - 1;
    }
}
