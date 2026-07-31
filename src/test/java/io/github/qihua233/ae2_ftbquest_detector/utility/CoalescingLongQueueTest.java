package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingLongQueueTest {
    @Test
    void coalescesUpdatesByIdentityAndKeepsHighestValue() {
        CoalescingLongQueue<Object> queue = new CoalescingLongQueue<>();
        Object key = new Object();
        List<Long> values = new ArrayList<>();

        queue.offerMax(key, 4L);
        queue.offerMax(key, 9L);
        queue.offerMax(key, 6L);

        assertEquals(1, queue.drain(8, (ignored, value) -> values.add(value)));
        assertEquals(List.of(9L), values);
        assertTrue(queue.isEmpty());
    }

    @Test
    void limitsWorkPerDrain() {
        CoalescingLongQueue<Object> queue = new CoalescingLongQueue<>();
        queue.offerMax(new Object(), 1L);
        queue.offerMax(new Object(), 2L);
        queue.offerMax(new Object(), 3L);

        assertEquals(2, queue.drain(2, (key, value) -> {}));
        assertEquals(1, queue.size());
    }

    @Test
    void failedUpdateRemainsQueuedForRetry() {
        CoalescingLongQueue<Object> queue = new CoalescingLongQueue<>();
        Object key = new Object();
        queue.offerMax(key, 7L);

        assertThrows(IllegalStateException.class,
                () -> queue.drain(1, (ignored, value) -> { throw new IllegalStateException("temporary"); }));

        assertFalse(queue.isEmpty());
        assertEquals(1, queue.drain(1, (ignored, value) -> assertEquals(7L, value)));
    }

    @Test
    void failedHeadDoesNotBlockFollowingEntries() {
        CoalescingLongQueue<Object> queue = new CoalescingLongQueue<>();
        Object failed = new Object();
        Object following = new Object();
        List<Object> processed = new ArrayList<>();
        queue.offerMax(failed, 1L);
        queue.offerMax(following, 2L);

        assertThrows(IllegalStateException.class, () -> queue.drain(2, (key, value) -> {
            if (key == failed) {
                throw new IllegalStateException("temporary");
            }
            processed.add(key);
        }));

        assertEquals(List.of(following), processed);
        assertEquals(1, queue.size());
        assertEquals(1, queue.drain(1, (key, value) -> {
            assertEquals(failed, key);
            assertEquals(1L, value);
        }));
    }

    @Test
    void equalButDistinctKeysRemainIndependent() {
        CoalescingLongQueue<String> queue = new CoalescingLongQueue<>();
        String first = new String("task");
        String second = new String("task");

        queue.offerMax(first, 1L);
        queue.offerMax(second, 2L);

        assertEquals(2, queue.size());
    }
}
