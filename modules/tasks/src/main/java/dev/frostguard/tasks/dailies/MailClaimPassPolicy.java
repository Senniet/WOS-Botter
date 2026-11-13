package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import java.util.Comparator;
import java.util.List;

final class MailClaimPassPolicy {

    private static final int MARKER_POSITION_TOLERANCE_PIXELS = 4;
    private static final int OVERLAPPING_MARKER_RADIUS_PIXELS = 24;

    private MailClaimPassPolicy() {
    }

    static Decision decide(Evidence before, Evidence after, int completedPasses, int maxPasses) {
        if (after.isEmpty()) {
            return Decision.COMPLETE;
        }
        if (before.isEmpty() || before.sameVisualStateAs(after)) {
            return Decision.STOP_NO_PROGRESS;
        }
        if (completedPasses >= maxPasses) {
            return Decision.STOP_BUDGET_EXHAUSTED;
        }
        return Decision.RETRY_AFTER_PROGRESS;
    }

    enum Decision {
        COMPLETE,
        RETRY_AFTER_PROGRESS,
        STOP_NO_PROGRESS,
        STOP_BUDGET_EXHAUSTED
    }

    record Evidence(List<PointData> markers) {

        Evidence {
            markers = markers == null
                    ? List.of()
                    : markers.stream()
                            .sorted(Comparator.comparingInt(PointData::getY).thenComparingInt(PointData::getX))
                            .toList();
        }

        static Evidence fromMatches(List<ImageSearchResultData> matches) {
            if (matches == null) {
                return new Evidence(List.of());
            }
            List<PointData> distinct = new java.util.ArrayList<>();
            for (PointData candidate : matches.stream()
                    .filter(ImageSearchResultData::isFound)
                    .map(ImageSearchResultData::getPoint)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingInt(PointData::getY)
                            .thenComparingInt(PointData::getX)).toList()) {
                boolean duplicate = distinct.stream().anyMatch(existing ->
                        Math.abs(existing.getX() - candidate.getX()) <= OVERLAPPING_MARKER_RADIUS_PIXELS
                        && Math.abs(existing.getY() - candidate.getY()) <= OVERLAPPING_MARKER_RADIUS_PIXELS);
                if (!duplicate) distinct.add(candidate);
            }
            return new Evidence(distinct);
        }

        boolean isEmpty() {
            return markers.isEmpty();
        }

        int count() {
            return markers.size();
        }

        boolean sameVisualStateAs(Evidence other) {
            if (other == null || markers.size() != other.markers.size()) {
                return false;
            }
            for (int i = 0; i < markers.size(); i++) {
                PointData left = markers.get(i);
                PointData right = other.markers.get(i);
                if (Math.abs(left.getX() - right.getX()) > MARKER_POSITION_TOLERANCE_PIXELS
                        || Math.abs(left.getY() - right.getY()) > MARKER_POSITION_TOLERANCE_PIXELS) {
                    return false;
                }
            }
            return true;
        }
    }
}
