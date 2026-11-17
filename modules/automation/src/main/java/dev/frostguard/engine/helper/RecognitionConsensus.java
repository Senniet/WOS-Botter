package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;

/** Conservative confirmation policy shared by all actionable template matches. */
public final class RecognitionConsensus {
    private RecognitionConsensus() {}

    public static boolean agrees(ImageSearchResultData first,
                                 ImageSearchResultData second, int maxManhattanDistance) {
        if (maxManhattanDistance < 0 || first == null || second == null
                || !first.isFound() || !second.isFound()
                || first.getPoint() == null || second.getPoint() == null
                || !Double.isFinite(first.getMatchScore())
                || !Double.isFinite(second.getMatchScore())) return false;
        if (first.hasMatchedArea() && second.hasMatchedArea()) {
            int width = Math.max(first.getMatchWidth(), second.getMatchWidth());
            int height = Math.max(first.getMatchHeight(), second.getMatchHeight());
            if (Math.abs(first.getMatchWidth() - second.getMatchWidth()) > Math.max(3, width / 4)
                    || Math.abs(first.getMatchHeight() - second.getMatchHeight()) > Math.max(3, height / 4))
                return false;
        }
        return first.getPoint().manhattanDistanceTo(second.getPoint()) <= maxManhattanDistance;
    }
}
