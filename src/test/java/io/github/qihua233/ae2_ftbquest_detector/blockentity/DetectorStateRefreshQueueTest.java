package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorStateRefreshQueueTest {
    @Test
    void requestIsConsumedOnceWhileLoaded() {
        DetectorStateRefreshQueue queue = new DetectorStateRefreshQueue();
        queue.activate();

        queue.request();

        assertTrue(queue.consume());
        assertFalse(queue.consume());
    }

    @Test
    void unloadingCancelsPendingRefresh() {
        DetectorStateRefreshQueue queue = new DetectorStateRefreshQueue();
        queue.activate();
        queue.request();

        queue.deactivate();

        assertFalse(queue.consume());
    }

    @Test
    void requestsWhileUnloadedDoNotLeakIntoNextLoad() {
        DetectorStateRefreshQueue queue = new DetectorStateRefreshQueue();
        queue.request();

        queue.activate();

        assertFalse(queue.consume());
    }

    @Test
    void failedRefreshIsQueuedForRetry() {
        DetectorStateRefreshQueue queue = new DetectorStateRefreshQueue();
        queue.activate();
        queue.request();

        assertThrows(IllegalStateException.class,
                () -> queue.runPending(() -> { throw new IllegalStateException("temporary failure"); }));

        assertTrue(queue.runPending(() -> {}));
    }
}
