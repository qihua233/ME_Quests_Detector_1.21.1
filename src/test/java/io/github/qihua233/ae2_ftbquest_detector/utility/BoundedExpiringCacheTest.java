package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoundedExpiringCacheTest {
    @Test
    void expiredEntryIsRemovedWhenRead() {
        BoundedExpiringCache<String, String> cache = new BoundedExpiringCache<>(2);
        cache.put("team", "stats", 10L, 0L);

        assertNull(cache.get("team", 10L));
        assertEquals(0, cache.size());
    }

    @Test
    void capacityEvictsEntryExpiringFirst() {
        BoundedExpiringCache<String, String> cache = new BoundedExpiringCache<>(2);
        cache.put("first", "a", 20L, 0L);
        cache.put("second", "b", 30L, 0L);
        cache.put("third", "c", 40L, 0L);

        assertNull(cache.get("first", 0L));
        assertEquals("b", cache.get("second", 0L));
        assertEquals("c", cache.get("third", 0L));
        assertEquals(2, cache.size());
    }

    @Test
    void expiredEntriesAreClearedBeforeLiveEntriesAreEvicted() {
        BoundedExpiringCache<String, String> cache = new BoundedExpiringCache<>(2);
        cache.put("expired", "a", 5L, 0L);
        cache.put("live", "b", 30L, 0L);
        cache.put("new", "c", 40L, 10L);

        assertEquals("b", cache.get("live", 10L));
        assertEquals("c", cache.get("new", 10L));
        assertEquals(2, cache.size());
    }
}
