package io.github.qihua233.ae2_ftbquest_detector.utility;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class CoalescingLongQueue<K> {
    private final Map<K, Long> values = new IdentityHashMap<>();
    private final ArrayDeque<K> order = new ArrayDeque<>();

    public synchronized void offerMax(K key, long value) {
        Objects.requireNonNull(key, "key");
        Long previous = values.get(key);
        if (previous == null) {
            values.put(key, value);
            order.addLast(key);
        } else if (value > previous) {
            values.put(key, value);
        }
    }

    public synchronized int drain(int limit, LongHandler<K> handler) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(handler, "handler");

        int processed = 0;
        while (processed < limit && !order.isEmpty()) {
            K key = order.getFirst();
            long value = values.get(key);
            handler.accept(key, value);
            order.removeFirst();
            values.remove(key);
            processed++;
        }
        return processed;
    }

    public synchronized boolean isEmpty() {
        return order.isEmpty();
    }

    public synchronized int size() {
        return order.size();
    }

    public synchronized void clear() {
        order.clear();
        values.clear();
    }

    @FunctionalInterface
    public interface LongHandler<K> {
        void accept(K key, long value);
    }
}
