package dev.frostguard.tasks.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownGameScreenResumeSequenceTest {
    @Test void secondStartFromRecruitmentUsesExactlyTwoVerifiedBacks() {
        AtomicInteger backActions = new AtomicInteger();
        AtomicInteger nextPages = new AtomicInteger();
        var outcome = KnownGameScreenResume.returnHome(
                KnownGameScreenResume.Page.HERO_RECRUITMENT, 3,
                () -> true,
                backActions::incrementAndGet,
                () -> {},
                () -> backActions.get() == 2,
                () -> nextPages.incrementAndGet() == 1 ? KnownGameScreenResume.Page.HEROES : null);
        assertTrue(outcome.homeVerified());
        assertEquals(2, outcome.backActions());
        assertEquals(2, backActions.get());
        assertEquals(1, nextPages.get());
    }

    @Test void squadSettingsReturnsThroughKnownExplorationPageWithoutBlindTaps() {
        AtomicInteger clicks = new AtomicInteger();
        var outcome = KnownGameScreenResume.returnHome(
                KnownGameScreenResume.Page.SQUAD_SETTINGS,3,
                () -> true,clicks::incrementAndGet,() -> {},
                () -> clicks.get() == 2,
                () -> KnownGameScreenResume.Page.EXPLORATION);
        assertTrue(outcome.homeVerified());
        assertEquals(2,clicks.get());
    }

    @Test void unrecognizedNextScreenStopsWithoutAnotherBackOrGameRestart() {
        AtomicInteger clicks = new AtomicInteger();
        var outcome = KnownGameScreenResume.returnHome(
                KnownGameScreenResume.Page.SQUAD_SETTINGS,3,
                () -> true,clicks::incrementAndGet,() -> {},
                () -> false,() -> null);
        assertFalse(outcome.homeVerified());
        assertEquals(1,clicks.get());
        assertEquals("next page not recognized",outcome.reason());
    }

    @Test void notForegroundOrUnknownTitleNeverSendsAnyInput() {
        AtomicInteger clicks = new AtomicInteger();
        var unsafe = KnownGameScreenResume.returnHome(
                KnownGameScreenResume.Page.HERO_RECRUITMENT,3,
                () -> false,clicks::incrementAndGet,() -> {},() -> false,() -> null);
        var unknown = KnownGameScreenResume.returnHome(null,3,
                () -> true,clicks::incrementAndGet,() -> {},() -> false,() -> null);
        assertFalse(unsafe.homeVerified());
        assertFalse(unknown.homeVerified());
        assertEquals(0,clicks.get());
    }

    @Test void boundedLimitNeverProducesAnUnverifiedFourthBack() {
        AtomicInteger clicks = new AtomicInteger();
        var limit = KnownGameScreenResume.returnHome(
                KnownGameScreenResume.Page.SQUAD_SETTINGS,3,
                () -> true,clicks::incrementAndGet,() -> {},
                () -> false,() -> KnownGameScreenResume.Page.SQUAD_SETTINGS);
        assertFalse(limit.homeVerified());
        assertEquals(3,clicks.get());
        assertEquals("bounded Back limit reached",limit.reason());
    }
}
