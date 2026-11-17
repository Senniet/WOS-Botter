package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderScopedExitTest {
    @BeforeAll static void nativeOpenCv() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    private static RawImageData list() throws Exception {
        return ChiefOrderLiveFrameTest.frame("order-list-active-redacted.jpg");
    }

    private static RawImageData city() throws Exception {
        return ChiefOrderLiveFrameTest.resourceFrame("/initialize/home-city-anchor-redacted.png");
    }

    @Test void urgentMobilizationActiveAndProductivityCooldownNeverLeadToEnact() throws Exception {
        RawImageData board = list();
        var urgent = ChiefOrderLiveFrame.inspectList(board, "Urgent Mobilization");
        var productivity = ChiefOrderLiveFrame.inspectList(board, "Productivity Day");
        assertEquals(ChiefOrderLiveFrame.ListState.ACTIVE, urgent.state(), urgent.toString());
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN, productivity.state(),
                productivity.toString());
        assertEquals(ChiefOrderRoutine.OrderGate.ALREADY_USED,
                ChiefOrderRoutine.orderGate(urgent.state()));
        assertEquals(ChiefOrderRoutine.OrderGate.ALREADY_USED,
                ChiefOrderRoutine.orderGate(productivity.state()));
        assertEquals(ChiefOrderRoutine.OrderGate.TRY_ICON,
                ChiefOrderRoutine.orderGate(ChiefOrderLiveFrame.ListState.AVAILABLE));
        assertEquals(ChiefOrderRoutine.OrderGate.UNKNOWN,
                ChiefOrderRoutine.orderGate(ChiefOrderLiveFrame.ListState.UNKNOWN));
    }

    @Test void urgentMobilizationAndProductivityUseOneVerifiedMenuBackToCity() throws Exception {
        for (String order : List.of("Urgent Mobilization", "Productivity Day")) {
            var screens = List.of(list(), list(), city());
            var taps = new ArrayList<String>();
            var reads = new AtomicInteger();
            var result = ChiefOrderExit.returnHome(
                    () -> screens.get(reads.getAndIncrement()),
                    point -> taps.add(point.getX() + "," + point.getY()), () -> {}, order);
            assertEquals(ChiefOrderExit.Result.HOME, result, order);
            assertEquals(List.of("44,35"), taps, order);
            assertEquals(3, reads.get(), order);
        }
    }

    @Test void productivityBookGoesBackToListThenCityWithTwoVerifiedBackPresses() throws Exception {
        var book = ChiefOrderLiveFrameTest.frame("productivity-cost.png");
        var screens = List.of(book, book, list(), list(), city());
        var reads = new AtomicInteger();
        var taps = new ArrayList<String>();
        var result = ChiefOrderExit.returnHome(
                () -> screens.get(reads.getAndIncrement()),
                point -> taps.add(point.getX() + "," + point.getY()), () -> {}, "Productivity Day");
        assertEquals(ChiefOrderExit.Result.HOME, result);
        assertEquals(List.of("44,35", "44,35"), taps);
        assertEquals(5, reads.get());
    }

    @Test void noBackTapOnAlreadyCityOrChangedUnknownPage() throws Exception {
        var taps = new AtomicInteger();
        var home = city();
        assertEquals(ChiefOrderExit.Result.HOME,
                ChiefOrderExit.returnHome(() -> home,
                        point -> taps.incrementAndGet(), () -> {}, "Urgent Mobilization"));
        assertEquals(0, taps.get());

        var screens = List.of(list(), city());
        var reads = new AtomicInteger();
        assertEquals(ChiefOrderExit.Result.UNVERIFIED,
                ChiefOrderExit.returnHome(() -> screens.get(reads.getAndIncrement()),
                        point -> taps.incrementAndGet(), () -> {}, "Productivity Day"));
        assertEquals(0, taps.get(), "Never tap when the list changed before confirmation");
    }

    @Test void doNotUseBackOnTheWrongOrdersBook() throws Exception {
        var wrongBook = ChiefOrderLiveFrameTest.frame("productivity-cost.png");
        var taps = new AtomicInteger();
        assertEquals(ChiefOrderExit.Result.NOT_CHIEF_ORDER,
                ChiefOrderExit.returnHome(() -> wrongBook, point -> taps.incrementAndGet(),
                        () -> {}, "Urgent Mobilization"));
        assertEquals(0, taps.get());
    }

    @Test void countdownNeedsTwoConsistentPositiveFramesBeforePreciseScheduling() {
        var now = LocalDateTime.of(2026, 11, 17, 20, 15);
        var first = Duration.ofHours(11).plusMinutes(47).plusSeconds(52);
        var second = Duration.ofHours(11).plusMinutes(47).plusSeconds(51);
        assertEquals(second, ChiefOrderRoutine.confirmedCountdown(first, second));
        assertEquals(now.plus(second).plusMinutes(1),
                ChiefOrderRoutine.nextAvailableCheck(now,
                        ChiefOrderRoutine.confirmedCountdown(first, second)));
        assertNull(ChiefOrderRoutine.confirmedCountdown(first,
                Duration.ofHours(7).plusMinutes(47).plusSeconds(51)));
        assertNull(ChiefOrderRoutine.confirmedCountdown(first, null));
        assertEquals(now.plusMinutes(30), ChiefOrderRoutine.nextAvailableCheck(now,
                ChiefOrderRoutine.confirmedCountdown(first, null)));
    }
}
