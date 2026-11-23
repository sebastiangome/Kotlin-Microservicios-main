package com.induztek.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginAttemptTrackerTest {
    @Test
    void locksOnlyAfterTheMaximumNumberOfFailures() {
        LoginAttemptTracker tracker = new LoginAttemptTracker(3, Duration.ofMinutes(15));

        tracker.recordFailure("a@x.cl");
        tracker.recordFailure("a@x.cl");
        assertFalse(tracker.isLocked("a@x.cl"));

        tracker.recordFailure("a@x.cl");
        assertTrue(tracker.isLocked("a@x.cl"));
        assertFalse(tracker.isLocked("b@x.cl"));
    }

    @Test
    void successfulLoginClearsTheFailureCounter() {
        LoginAttemptTracker tracker = new LoginAttemptTracker(3, Duration.ofMinutes(15));
        tracker.recordFailure("a@x.cl");
        tracker.recordFailure("a@x.cl");

        tracker.recordSuccess("a@x.cl");
        tracker.recordFailure("a@x.cl");
        tracker.recordFailure("a@x.cl");

        assertFalse(tracker.isLocked("a@x.cl"));
    }

    @Test
    void lockExpiresWhenTheLockDurationHasPassed() throws InterruptedException {
        LoginAttemptTracker tracker = new LoginAttemptTracker(1, Duration.ofMillis(50));
        tracker.recordFailure("a@x.cl");
        assertTrue(tracker.isLocked("a@x.cl"));

        Thread.sleep(80);

        assertFalse(tracker.isLocked("a@x.cl"));
    }
}
