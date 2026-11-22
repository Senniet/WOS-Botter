package dev.frostguard.tasks.economy;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Coordinates bounded Storehouse reward collection and the final cooldown decision. */
final class StorehouseVisitFlow<T> {

    // A valid 1h47 timer previously got truncated to 1h, causing unnecessary
    // repeated Storehouse/Claim visits while the game was still on cooldown.
    // Keep a 2h safety cap to revisit independently available stamina rewards.
    static final Duration COOLDOWN_VISIT_CAP = Duration.ofHours(2);
    // Only valid OCR can justify a longer delay. Unknown countdowns retain a
    // one-hour conservative recheck so we do not miss real chest claims.
    static final Duration UNKNOWN_COOLDOWN_RETRY = Duration.ofHours(1);
    static final Duration ERROR_RETRY_DELAY = Duration.ofMinutes(5);
    // Match the 75-minute retry policy used when a stamina Claim was refused.
    // A shorter overall retry would repeatedly reopen the same full-capacity popup.
    static final Duration CAPACITY_RETRY_DELAY = Duration.ofMinutes(75);
    static final Duration VISIT_TIME_LIMIT = Duration.ofSeconds(90);
    static final int MAX_COLLECTIONS_PER_VISIT = 4;

    enum VisitState {
        READY,
        WAITING_COOLDOWN,
        WAITING_CAPACITY,
        RETRY_ON_ERROR
    }

    enum ActivityPhase {
        SEARCHING_CHEST,
        SEARCHING_STAMINA,
        COLLECTING_CHEST,
        COLLECTING_STAMINA,
        READING_COOLDOWN,
        RESCHEDULING
    }

    enum ObservationState {
        FOUND,
        ABSENT,
        UNKNOWN
    }

    enum CollectionState {
        CONFIRMED,
        UNCONFIRMED,
        BLOCKED_CAPACITY,
        DEFERRED_UNACCEPTED,
        UNKNOWN
    }

    enum CooldownState {
        VALID,
        UNREADABLE,
        INVALID
    }

    record Observation<T>(ObservationState state, T value) {
        Observation {
            Objects.requireNonNull(state);
            if ((state == ObservationState.FOUND) != (value != null)) {
                throw new IllegalArgumentException("Only a found observation carries a value.");
            }
        }

        static <T> Observation<T> found(T value) {
            return new Observation<>(ObservationState.FOUND, Objects.requireNonNull(value));
        }

        static <T> Observation<T> absent() {
            return new Observation<>(ObservationState.ABSENT, null);
        }

        static <T> Observation<T> unknown() {
            return new Observation<>(ObservationState.UNKNOWN, null);
        }
    }

    record CooldownRead(CooldownState state, Duration duration, String detail) {
        CooldownRead {
            Objects.requireNonNull(state);
            detail = detail == null ? "" : detail;
            if (state == CooldownState.VALID && (duration == null || duration.isZero()
                    || duration.isNegative())) {
                throw new IllegalArgumentException("A valid cooldown must be positive.");
            }
        }

        static CooldownRead valid(Duration duration) {
            return new CooldownRead(CooldownState.VALID, duration, "");
        }

        static CooldownRead unreadable(String detail) {
            return new CooldownRead(CooldownState.UNREADABLE, null, detail);
        }

        static CooldownRead invalid(String detail) {
            return new CooldownRead(CooldownState.INVALID, null, detail);
        }
    }

    record VisitDecision(
            VisitState state,
            Duration delay,
            String reason,
            int confirmedChestCollections,
            int confirmedStaminaCollections) {
        VisitDecision {
            Objects.requireNonNull(state);
            Objects.requireNonNull(delay);
            Objects.requireNonNull(reason);
        }
    }

    interface Actions<T> {
        Observation<T> findChest();

        Observation<T> findStamina();

        CollectionState collectChest(T candidate);

        CollectionState collectStamina(T candidate);

        CooldownRead readCooldown();

        void onPhase(ActivityPhase phase);
    }

    private final Actions<T> actions;
    private final LongSupplier nanoTime;
    private final long startNanos;
    private int chestCollections;
    private int staminaCollections;
    private int collectionActions;

    private StorehouseVisitFlow(Actions<T> actions, LongSupplier nanoTime) {
        this.actions = Objects.requireNonNull(actions);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.startNanos = nanoTime.getAsLong();
    }

    static <T> VisitDecision execute(Actions<T> actions) {
        return execute(actions, System::nanoTime);
    }

    static <T> VisitDecision execute(Actions<T> actions, LongSupplier nanoTime) {
        return new StorehouseVisitFlow<>(actions, nanoTime).run();
    }

    private VisitDecision run() {
        while (withinVisitLimit()) {
            boolean collectedThisPass = false;

            actions.onPhase(ActivityPhase.SEARCHING_CHEST);
            Observation<T> chest = Objects.requireNonNull(actions.findChest());
            if (chest.state() == ObservationState.UNKNOWN) {
                return retry("Chest scan was uncertain.");
            }
            if (chest.state() == ObservationState.FOUND) {
                actions.onPhase(ActivityPhase.COLLECTING_CHEST);
                CollectionState result = Objects.requireNonNull(actions.collectChest(chest.value()));
                if (result != CollectionState.CONFIRMED) {
                    return retry("Chest collection was " + result.name().toLowerCase() + ".");
                }
                chestCollections++;
                collectionActions++;
                collectedThisPass = true;
                if (collectionLimitReached()) {
                    return retry("Storehouse collection action limit reached.");
                }
            }

            if (!withinVisitLimit()) {
                return retry("Storehouse visit time limit reached.");
            }

            actions.onPhase(ActivityPhase.SEARCHING_STAMINA);
            Observation<T> stamina = Objects.requireNonNull(actions.findStamina());
            if (stamina.state() == ObservationState.UNKNOWN) {
                return retry("Stamina scan was uncertain.");
            }
            if (stamina.state() == ObservationState.FOUND) {
                actions.onPhase(ActivityPhase.COLLECTING_STAMINA);
                CollectionState result = Objects.requireNonNull(actions.collectStamina(stamina.value()));
                if (result == CollectionState.BLOCKED_CAPACITY
                        || result == CollectionState.DEFERRED_UNACCEPTED) {
                    // A proven full-capacity notice is neither a collected reward
                    // nor an uncertain click. Do not repeat this action immediately.
                    return decision(VisitState.WAITING_CAPACITY, CAPACITY_RETRY_DELAY,
                            result == CollectionState.BLOCKED_CAPACITY
                                ? "Stamina at verified capacity; reward not collected."
                                : "Stamina Claim not accepted; full capacity is possible; no immediate retry.");
                }
                if (result != CollectionState.CONFIRMED) {
                    return retry("Stamina collection was " + result.name().toLowerCase() + ".");
                }
                staminaCollections++;
                collectionActions++;
                collectedThisPass = true;
                if (collectionLimitReached()) {
                    return retry("Storehouse collection action limit reached.");
                }
            }

            if (collectedThisPass) {
                continue;
            }

            if (!withinVisitLimit()) {
                return retry("Storehouse visit time limit reached before cooldown reading.");
            }

            actions.onPhase(ActivityPhase.READING_COOLDOWN);
            CooldownRead cooldown = Objects.requireNonNull(actions.readCooldown());
            if (!withinVisitLimit()) {
                return retry("Storehouse visit time limit reached while reading cooldown.");
            }
            if (cooldown.state() != CooldownState.VALID) {
                return waiting(UNKNOWN_COOLDOWN_RETRY,
                        "Cooldown " + cooldown.state().name().toLowerCase() + ": " + cooldown.detail());
            }
            Duration boundedDelay = cooldown.duration().compareTo(COOLDOWN_VISIT_CAP) > 0
                    ? COOLDOWN_VISIT_CAP
                    : cooldown.duration();
            String reason = boundedDelay.equals(cooldown.duration())
                    ? "Cooldown recognized"
                    : "Cooldown recognized and capped at two hours";
            return waiting(boundedDelay, reason);
        }

        return retry("Storehouse visit time limit reached.");
    }

    private boolean collectionLimitReached() {
        return collectionActions >= MAX_COLLECTIONS_PER_VISIT;
    }

    private boolean withinVisitLimit() {
        return nanoTime.getAsLong() - startNanos < VISIT_TIME_LIMIT.toNanos();
    }

    private VisitDecision waiting(Duration delay, String reason) {
        return decision(VisitState.WAITING_COOLDOWN, delay, reason);
    }

    private VisitDecision retry(String reason) {
        return decision(VisitState.RETRY_ON_ERROR, ERROR_RETRY_DELAY, reason);
    }

    private VisitDecision decision(VisitState state, Duration delay, String reason) {
        return new VisitDecision(state, delay, reason, chestCollections, staminaCollections);
    }

    static VisitDecision retryBeforeFlow(String reason) {
        return new VisitDecision(VisitState.RETRY_ON_ERROR, ERROR_RETRY_DELAY, reason, 0, 0);
    }
}
