package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpMessageSeverityEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.engine.emulator.EmulatorController;
import dev.frostguard.engine.service.LoggingService;
import dev.frostguard.vision.logging.ProfileContextLogger;

import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import dev.frostguard.engine.diagnostics.DiagnosticSnapshotStore;
import java.util.Objects;

/**
 * Retry-aware template matcher that delegates to the emulator's
 * image-search subsystem.  Supports colour and grayscale matching,
 * single and multi-result modes, and optional preemption hooks.
 */
public class TemplateSearchHelper {

    private static final String TAG = "TemplateSearchHelper";

    private final EmulatorController emu;
    private final String device;
    private final ProfileContextLogger log;
    private final String accountName;
    private final AccountDescriptor profile;
    private final LoggingService logs;
    private int missCount;
    private static final ConcurrentHashMap<String, Instant> SNAPSHOT_RATE_LIMIT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> FAILURE_COUNTS = new ConcurrentHashMap<>();
    private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(10);
    private static final int FALLBACK_CONFIRMATION_DISTANCE = 18;

    private Runnable preemptionHook = () -> {};

    public TemplateSearchHelper(EmulatorController emuManager, String emulatorNumber,
                                AccountDescriptor profile) {
        this.emu         = emuManager;
        this.device      = emulatorNumber;
        this.log         = new ProfileContextLogger(TemplateSearchHelper.class, profile);
        this.accountName = profile.getName();
        this.profile     = profile;
        this.logs        = LoggingService.obtain();
    }

    public int getFailedSearches() { return missCount; }

    public void setPreemptionCheck(Runnable check) {
        this.preemptionHook = Objects.requireNonNull(check, "preemptionCheck");
    }

    // ── single-match (colour) ────────────────────────────────────────

    public ImageSearchResultData locatePattern(TemplatesEnum tpl, SearchConfig cfg) {
        return retrySearch(tpl, cfg, false, false);
    }

    // ── single-match (greyscale) ─────────────────────────────────────

    public ImageSearchResultData locatePatternMono(TemplatesEnum tpl, SearchConfig cfg) {
        return retrySearch(tpl, cfg, true, false);
    }

    public ImageSearchResultData locatePatternMultiScale(TemplatesEnum tpl, SearchConfig cfg) {
        return retrySearch(tpl, cfg, false, true);
    }

    /**
     * Recognize a semantic action using several known visual variants. The
     * preferred template uses the standard resilient matcher; alternatives
     * need two independent agreeing frames before they may be tapped.
     * Callers must choose variants for the SAME action and screen context.
     */
    public ImageSearchResultData locateAction(TemplatesEnum preferred, SearchConfig cfg,
                                               TemplatesEnum... alternatives) {
        Objects.requireNonNull(preferred, "preferred");
        Objects.requireNonNull(cfg, "cfg");
        ImageSearchResultData primary = locatePattern(preferred, cfg);
        if (confirmedAction(preferred, cfg, primary, false)) return primary;
        if (alternatives != null) {
            for (TemplatesEnum alternative : alternatives) {
                if (alternative == null || alternative == preferred || !alternative.existsAtPath()) continue;
                preemptionHook.run();
                ImageSearchResultData exact = doSearch(alternative, cfg);
                if (confirmedAction(alternative, cfg, exact, false)) {
                    dbg("Action " + preferred.name() + " confirmed via " + alternative.name() + " exact");
                    return exact;
                }
                // The same button artwork often changes size under BlueStacks scaling.
                ImageSearchResultData scaled = doSearchMultiScale(alternative, cfg);
                if (confirmedAction(alternative, cfg, scaled, true)) {
                    dbg("Action " + preferred.name() + " confirmed via " + alternative.name() + " multiscale");
                    return scaled;
                }
            }
        }
        recordMiss(preferred, cfg, primary);
        return ImageSearchResultData.miss();
    }

    private boolean confirmedAction(TemplatesEnum template, SearchConfig cfg,
                                    ImageSearchResultData first, boolean multiScale) {
        if (first == null || !first.isFound() || first.getPoint() == null) return false;
        sleepWithPreemption(180);
        preemptionHook.run();
        ImageSearchResultData second = multiScale
                ? doSearchMultiScale(template, cfg) : doSearch(template, cfg);
        if (RecognitionConsensus.agrees(first, second, FALLBACK_CONFIRMATION_DISTANCE)) return true;
        // locatePattern may have found the first frame via its verified
        // multiscale/greyscale fallback. Confirm against a fresh frame using
        // those same methods rather than rejecting it with exact-only search.
        if (!multiScale) {
            preemptionHook.run();
            second = doSearchMultiScale(template, cfg);
            if (RecognitionConsensus.agrees(first, second, FALLBACK_CONFIRMATION_DISTANCE)) {
                dbg("Action " + template.name() + " confirmed via multiscale second frame");
                return true;
            }
            preemptionHook.run();
            second = doSearchGrey(template, cfg);
            if (RecognitionConsensus.agrees(first, second, FALLBACK_CONFIRMATION_DISTANCE)) {
                dbg("Action " + template.name() + " confirmed via greyscale second frame");
                return true;
            }
        }
        dbg("Action " + template.name() + " failed two-frame confirmation");
        return false;
    }

    // ── multi-match (colour) ─────────────────────────────────────────

    public List<ImageSearchResultData> locateAllPatterns(TemplatesEnum tpl, SearchConfig cfg) {
        return retryMultiSearch(tpl, cfg, false);
    }

    // ── multi-match (greyscale) ──────────────────────────────────────

    public List<ImageSearchResultData> locateAllPatternsMono(TemplatesEnum tpl, SearchConfig cfg) {
        return retryMultiSearch(tpl, cfg, true);
    }

    // ── retry loops ──────────────────────────────────────────────────

    private ImageSearchResultData retrySearch(TemplatesEnum tpl, SearchConfig cfg,
                                              boolean mono, boolean multiScale) {
        ImageSearchResultData last = null;
        for (int a = 1; a <= cfg.getMaxAttempts(); a++) {
            preemptionHook.run();
            last = multiScale ? doSearchMultiScale(tpl, cfg) : mono ? doSearchGrey(tpl, cfg) : doSearch(tpl, cfg);
            if (last != null && last.isFound()) {
                dbg(tpl.name() + (multiScale ? " (multi-scale)" : "") + " found @ attempt " + a);
                return last;
            }
            if (a < cfg.getMaxAttempts()) sleepWithPreemption(cfg.getDelayBetweenAttempts());
        }
        // Only the general colour matcher gets a shared, conservative
        // fallback. Explicit mono or multiscale callers retain their semantics.
        if (!mono && !multiScale && cfg.getThreshold() >= 90) {
            ImageSearchResultData fallback = verifiedFallback(tpl, cfg);
            if (fallback != null && fallback.isFound()) return fallback;
        }
        dbg(tpl.name() + (multiScale ? " (multi-scale)" : "") + " not found after "
                + cfg.getMaxAttempts() + " attempts; score=" + score(last));
        recordMiss(tpl, cfg, last);
        return last;
    }

    /**
     * Shared secondary recognition with two-frame confirmation. Never click
     * using a single weak match. Both methods retain the caller's search area.
     */
    private ImageSearchResultData verifiedFallback(TemplatesEnum tpl, SearchConfig cfg) {
        for (boolean greyscale : new boolean[]{false, true}) {
            preemptionHook.run();
            ImageSearchResultData first = greyscale ? doSearchGrey(tpl, cfg) : doSearchMultiScale(tpl, cfg);
            if (first == null || !first.isFound() || first.getPoint() == null) continue;
            sleepWithPreemption(160);
            preemptionHook.run();
            ImageSearchResultData second = greyscale ? doSearchGrey(tpl, cfg) : doSearchMultiScale(tpl, cfg);
            if (RecognitionConsensus.agrees(first, second, FALLBACK_CONFIRMATION_DISTANCE)) {
                dbg(tpl.name() + " verified via " + (greyscale ? "greyscale" : "multiscale")
                        + " scores=" + score(first) + "/" + score(second));
                return second;
            }
            dbg(tpl.name() + " secondary match unconfirmed via "
                    + (greyscale ? "greyscale" : "multiscale"));
        }
        return null;
    }

    private static String score(ImageSearchResultData match) {
        return match == null ? "unavailable" : String.format(Locale.ROOT, "%.1f", match.getMatchScore());
    }

    /**
     * Capture one anonymized-by-filename frame per device/template per 10 minutes.
     * The snapshot store limits retained PNGs and respects the user's diagnostics toggle.
     */
    private void recordMiss(TemplatesEnum tpl, SearchConfig cfg, ImageSearchResultData last) {
        String key = device + ":" + tpl.name();
        // Optional/missing UI templates are common. Avoid a screenshot storm
        // when scanning large screens: persist only recurring failures.
        if (FAILURE_COUNTS.merge(key, 1, Integer::sum) < 3) return;
        Instant now = Instant.now();
        Instant previous = SNAPSHOT_RATE_LIMIT.get(key);
        if (previous != null && Duration.between(previous, now).compareTo(SNAPSHOT_INTERVAL) < 0) return;
        DiagnosticSnapshotStore store = DiagnosticSnapshotStore.forCurrentWorkspace();
        if (!store.isEnabled()) return;
        if (SNAPSHOT_RATE_LIMIT.put(key, now) != null
                && previous != null && Duration.between(previous, now).compareTo(SNAPSHOT_INTERVAL) < 0) return;
        try {
            String note = store.write(emu.captureScreen(device), "recognition",
                    tpl.name().toLowerCase(Locale.ROOT), now).orElse("not saved");
            warn("Recognition diagnostics: template=" + tpl.name() + " threshold=" + cfg.getThreshold()
                    + " score=" + score(last) + " snapshot=" + note);
        } catch (RuntimeException failure) {
            if (failure instanceof java.util.concurrent.CancellationException
                    || Thread.currentThread().isInterrupted()) throw failure;
            dbg("Recognition diagnostics unavailable for " + tpl.name() + ": "
                    + failure.getClass().getSimpleName());
        }
    }

    private List<ImageSearchResultData> retryMultiSearch(TemplatesEnum tpl, SearchConfig cfg,
                                                         boolean mono) {
        List<ImageSearchResultData> last = null;
        for (int a = 1; a <= cfg.getMaxAttempts(); a++) {
            preemptionHook.run();
            last = mono ? doMultiGrey(tpl, cfg) : doMulti(tpl, cfg);
            if (last != null && !last.isEmpty()) {
                dbg(tpl.name() + ": " + last.size() + " matches");
                return last;
            }
            if (a < cfg.getMaxAttempts()) sleepWithPreemption(cfg.getDelayBetweenAttempts());
        }
        dbg(tpl.name() + " multi not found after " + cfg.getMaxAttempts() + " attempts");
        return last;
    }

    // ── single-shot delegates ────────────────────────────────────────

    private ImageSearchResultData doSearch(TemplatesEnum tpl, SearchConfig c) {
        if (c.hasArea())        return emu.locatePattern(device, tpl, c.getArea().topLeft(), c.getArea().bottomRight(), c.getThreshold());
        if (c.hasCoordinates()) return emu.locatePattern(device, tpl, c.getStartPoint(), c.getEndPoint(), c.getThreshold());
        return emu.locatePattern(device, tpl, c.getThreshold());
    }

    private ImageSearchResultData doSearchGrey(TemplatesEnum tpl, SearchConfig c) {
        if (c.hasArea())        return emu.locatePatternMono(device, tpl, c.getArea().topLeft(), c.getArea().bottomRight(), c.getThreshold());
        if (c.hasCoordinates()) return emu.locatePatternMono(device, tpl, c.getStartPoint(), c.getEndPoint(), c.getThreshold());
        return emu.locatePatternMono(device, tpl, c.getThreshold());
    }

    private ImageSearchResultData doSearchMultiScale(TemplatesEnum tpl, SearchConfig c) {
        if (c.hasArea())        return emu.locatePatternMultiScale(device, tpl, c.getArea().topLeft(), c.getArea().bottomRight(), c.getThreshold());
        if (c.hasCoordinates()) return emu.locatePatternMultiScale(device, tpl, c.getStartPoint(), c.getEndPoint(), c.getThreshold());
        return emu.locatePatternMultiScale(device, tpl, c.getThreshold());
    }

    private List<ImageSearchResultData> doMulti(TemplatesEnum tpl, SearchConfig c) {
        if (c.hasArea())        return emu.locateAllPatterns(device, tpl, c.getArea().topLeft(), c.getArea().bottomRight(), c.getThreshold(), c.getMaxResults());
        if (c.hasCoordinates()) return emu.locateAllPatterns(device, tpl, c.getStartPoint(), c.getEndPoint(), c.getThreshold(), c.getMaxResults());
        return emu.locateAllPatterns(device, tpl, c.getThreshold(), c.getMaxResults());
    }

    private List<ImageSearchResultData> doMultiGrey(TemplatesEnum tpl, SearchConfig c) {
        if (c.hasArea())        return emu.locateAllPatternsMono(device, tpl, c.getArea().topLeft(), c.getArea().bottomRight(), c.getThreshold(), c.getMaxResults());
        if (c.hasCoordinates()) return emu.locateAllPatternsMono(device, tpl, c.getStartPoint(), c.getEndPoint(), c.getThreshold(), c.getMaxResults());
        return emu.locateAllPatternsMono(device, tpl, c.getThreshold(), c.getMaxResults());
    }

    // ── sleep / preemption ───────────────────────────────────────────

    private void sleepWithPreemption(long ms) {
        preemptionHook.run();
        try { Thread.sleep(ms); }
        catch (InterruptedException e) {
            warn("Sleep interrupted");
            Thread.currentThread().interrupt();
            preemptionHook.run();
            throw new java.util.concurrent.CancellationException("Template search interrupted");
        }
    }

    // ── SearchConfig ─────────────────────────────────────────────────

    public static class SearchConfig {
        private final int maxAttempts;
        private final long delayBetweenAttempts;
        private final int threshold;
        private final int maxResults;
        private final PointData startPoint;
        private final PointData endPoint;
        private final AreaData area;

        private SearchConfig(Builder b) {
            this.maxAttempts = b.maxAttempts;
            this.delayBetweenAttempts = b.delay;
            this.threshold = b.threshold;
            this.maxResults = b.maxResults;
            this.startPoint = b.sp;
            this.endPoint = b.ep;
            this.area = b.area;
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int maxAttempts = 1;
            private long delay = 300;
            private int threshold = 90;
            private int maxResults = 1;
            private PointData sp, ep;
            private AreaData area;

            public Builder withMaxAttempts(int v) { this.maxAttempts = v; return this; }
            public Builder withDelay(long v)      { this.delay = v; return this; }
            public Builder withThreshold(int v)   { this.threshold = v; return this; }
            public Builder withMaxResults(int v)  { this.maxResults = v; return this; }
            public Builder withArea(AreaData a)   { this.area = a; sp = ep = null; return this; }
            public Builder withCoordinates(PointData s, PointData e) { sp = s; ep = e; area = null; return this; }
            public SearchConfig build() { return new SearchConfig(this); }
        }

        public int getMaxAttempts()          { return maxAttempts; }
        public long getDelayBetweenAttempts(){ return delayBetweenAttempts; }
        public int getThreshold()            { return threshold; }
        public int getMaxResults()           { return maxResults; }
        public PointData getStartPoint()     { return startPoint; }
        public PointData getEndPoint()       { return endPoint; }
        public AreaData getArea()            { return area; }
        public boolean hasCoordinates()      { return startPoint != null && endPoint != null; }
        public boolean hasArea()             { return area != null; }
    }

    // ── logging bridge ───────────────────────────────────────────────

    private void dbg(String msg) {
        String full = accountName + " - " + msg;
        log.debug(full);
        logs.emit(TpMessageSeverityEnum.DEBUG, TAG, accountName, msg);
        if (msg.contains("not found after")) missCount++;
    }

    private void warn(String msg) {
        String full = accountName + " - " + msg;
        log.warn(full);
        logs.emit(TpMessageSeverityEnum.WARNING, TAG, accountName, msg);
    }
}
