package com.induztek.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite simple de intentos de login fallidos por email (en memoria, por instancia).
 * Tras {@code maxFailures} fallos seguidos el email queda bloqueado {@code lockDuration}.
 */
class LoginAttemptTracker {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record State(int failures, Instant lockedUntil, Instant lastFailure) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;

    LoginAttemptTracker(int maxFailures, Duration lockDuration) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    boolean isLocked(String key) {
        State state = states.get(key);
        if (state == null || state.lockedUntil() == null) {
            return false;
        }
        if (state.lockedUntil().isAfter(Instant.now())) {
            return true;
        }
        states.remove(key);
        return false;
    }

    void recordFailure(String key) {
        Instant now = Instant.now();
        if (states.size() >= MAX_TRACKED_KEYS) {
            states.entrySet().removeIf(entry -> entry.getValue().lastFailure().plus(lockDuration).isBefore(now));
        }
        states.compute(key, (k, current) -> {
            int failures = current == null || isStale(current, now) ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : null;
            return new State(failures, lockedUntil, now);
        });
    }

    void recordSuccess(String key) {
        states.remove(key);
    }

    private boolean isStale(State state, Instant now) {
        return state.lastFailure().plus(lockDuration).isBefore(now);
    }
}
