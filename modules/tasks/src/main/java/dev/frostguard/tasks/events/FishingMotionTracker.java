package dev.frostguard.tasks.events;

import java.util.ArrayList;
import java.util.List;
import dev.frostguard.api.domain.AreaData;

/** Short-lived tracks preserve imminent threats during animation or a missed foreground detection. */
final class FishingMotionTracker {
    private static final int MAX_TRACKS = 256;
    private static final class Track {
        double x, y, w, h, vx, vy = -0.69;
        long observed;
        int scans;
    }

    private final List<Track> tracks = new ArrayList<>();
    private Double relativeVerticalVelocity;

    List<FishingAvoidancePlanner.Fish> update(List<AreaData> areas, long now, double hookVy) {
        if (areas.size() > MAX_TRACKS) throw new IllegalStateException("Fishing foreground detection overloaded");
        tracks.removeIf(track -> now - track.observed > 450);
        List<Track> matched = new ArrayList<>();
        for (AreaData area : areas) {
            // Vision may emit overlapping detections of the same fish in one frame.
            // Merge only near-identical boxes; distinct hazards remain independent.
            double cx = (area.topLeft().getX() + area.bottomRight().getX()) / 2.0;
            double cy = (area.topLeft().getY() + area.bottomRight().getY()) / 2.0;
            double aw = area.bottomRight().getX() - area.topLeft().getX();
            double ah = area.bottomRight().getY() - area.topLeft().getY();
            boolean duplicate = matched.stream().anyMatch(t ->
                    Math.abs(t.x - cx) <= Math.max(3, Math.min(aw, t.w) * 0.15)
                    && Math.abs(t.y - cy) <= Math.max(3, Math.min(ah, t.h) * 0.15)
                    && Math.abs(t.w - aw) <= Math.max(3, aw * 0.15)
                    && Math.abs(t.h - ah) <= Math.max(3, ah * 0.15));
            if (duplicate) continue;
            double x = (area.topLeft().getX() + area.bottomRight().getX()) / 2.0;
            double y = (area.topLeft().getY() + area.bottomRight().getY()) / 2.0;
            double width = area.bottomRight().getX() - area.topLeft().getX();
            double height = area.bottomRight().getY() - area.topLeft().getY();
            Track best = null;
            double bestDistance = 75;
            for (Track track : tracks) {
                if (matched.contains(track)) continue;
                long dt = now - track.observed;
                if (dt <= 0 || dt > 450) continue;
                double d = Math.hypot(x - track.x - track.vx * dt, y - track.y - track.vy * dt);
                if (d < bestDistance && width > track.w * 0.4 && width < track.w * 2.5) {
                    best = track;
                    bestDistance = d;
                }
            }
            if (best == null) {
                if (tracks.size() >= MAX_TRACKS) {
                    // A current detection must take precedence over a prediction from an
                    // earlier frame. Evict only tracks not observed in this frame.
                    Track oldestUnmatched = null;
                    for (Track previous : tracks) {
                        if (matched.contains(previous)) continue;
                        if (oldestUnmatched == null || previous.observed < oldestUnmatched.observed)
                            oldestUnmatched = previous;
                    }
                    if (oldestUnmatched == null) {
                        throw new IllegalStateException("Fishing foreground detection overloaded");
                    }
                    tracks.remove(oldestUnmatched);
                }
                best = new Track();
                tracks.add(best);
            } else {
                long dt = now - best.observed;
                best.vx = 0.5 * best.vx + 0.5 * (x - best.x) / dt;
                best.vy = 0.5 * best.vy + 0.5 * (y - best.y) / dt;
            }
            best.x = x;
            best.y = y;
            best.w = width;
            best.h = height;
            best.observed = now;
            best.scans++;
            matched.add(best);
        }
        tracks.removeIf(track -> now - track.observed > 450);
        var measured = matched.stream().filter(track -> track.scans >= 3)
                .mapToDouble(track -> track.vy - hookVy).filter(Double::isFinite).sorted().toArray();
        relativeVerticalVelocity = measured.length >= 3 ? measured[measured.length / 2] : null;
        List<FishingAvoidancePlanner.Fish> result = new ArrayList<>();
        for (Track track : tracks) {
            long dt = now - track.observed;
            double uncertainty = track.scans < 2 ? 25 : 8;
            result.add(new FishingAvoidancePlanner.Fish(
                    FishingAvoidancePlanner.reflectedX(track.x, track.vx, track.w / 2, dt),
                    track.y + track.vy * dt, track.w + uncertainty * 2, track.h,
                    track.vx, track.vy - hookVy));
        }
        return result;
    }

    Double relativeVerticalVelocity() { return relativeVerticalVelocity; }

    List<FishingHarvestPlanner.Opportunity> catchOpportunities(long now, double hookVy) {
        return tracks.stream().filter(track -> track.observed == now && track.scans >= 3)
                .map(track -> new FishingHarvestPlanner.Opportunity(track.x, track.y, track.w,
                        track.h, track.vx, track.vy - hookVy)).toList();
    }
}
