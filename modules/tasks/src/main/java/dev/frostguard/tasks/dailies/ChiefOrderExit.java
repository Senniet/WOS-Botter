package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Fast verified Book -> List -> City exit without generic screen scanning. */
final class ChiefOrderExit {
    enum Page { CITY, BOOK, LIST, UNKNOWN }
    enum Result { HOME, NOT_CHIEF_ORDER, UNVERIFIED }

    private static final PointData BACK = new PointData(44, 35);

    private ChiefOrderExit() {}

    static Page page(RawImageData frame, String order) {
        if (ChiefOrderLiveFrame.isCityHome(frame)) return Page.CITY;
        if (ChiefOrderLiveFrame.isChiefOrderList(frame)) return Page.LIST;
        if (ChiefOrderLiveFrame.isOrderBook(frame, order)) return Page.BOOK;
        return Page.UNKNOWN;
    }

    static Result returnHome(Supplier<RawImageData> capture,
                             Consumer<PointData> tap, Runnable settle, String order) {
        RawImageData current = capture.get();
        for (int backPresses = 0; backPresses < 2; backPresses++) {
            Page observed = page(current, order);
            if (observed == Page.CITY) return Result.HOME;
            if (observed == Page.UNKNOWN)
                return backPresses == 0 ? Result.NOT_CHIEF_ORDER : Result.UNVERIFIED;

            settle.run();
            if (page(capture.get(), order) != observed) return Result.UNVERIFIED;
            tap.accept(BACK);
            settle.run();
            current = capture.get();
        }
        return page(current, order) == Page.CITY ? Result.HOME : Result.UNVERIFIED;
    }
}
