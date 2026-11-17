package io.github.qihua233.ae2_ftbquest_detector.utility;

import java.util.HashMap;
import java.util.Map;

/** Limits accepted requests using game ticks rather than wall-clock time. */
public final class TickCooldown<K> {
    private final long durationTicks;
    private final Map<K, Long> nextAllowedTicks = new HashMap<>();

    public TickCooldown(long durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Cooldown duration must be positive");
        }
        this.durationTicks = durationTicks;
    }

    public boolean tryAcquire(K key, long currentTick) {
        Long nextAllowed = nextAllowedTicks.get(key);
        if (nextAllowed != null && currentTick < nextAllowed) {
            return false;
        }
        nextAllowedTicks.put(key, currentTick + durationTicks);
        return true;
    }

    public void removeExpired(long currentTick) {
        nextAllowedTicks.values().removeIf(nextAllowed -> currentTick >= nextAllowed);
    }
}
