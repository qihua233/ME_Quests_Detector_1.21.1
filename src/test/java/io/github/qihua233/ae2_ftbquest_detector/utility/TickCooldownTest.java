package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickCooldownTest {
    @Test
    void allowsTheNextRequestAtExactlySixtyTicks() {
        var cooldown = new TickCooldown<String>(60);
        assertTrue(cooldown.tryAcquire("player", 100));
        assertFalse(cooldown.tryAcquire("player", 100));
        assertFalse(cooldown.tryAcquire("player", 159));
        assertTrue(cooldown.tryAcquire("player", 160));
    }

    @Test
    void rejectedClicksDoNotExtendTheCooldown() {
        var cooldown = new TickCooldown<String>(60);
        assertTrue(cooldown.tryAcquire("player", 0));
        for (int tick = 1; tick < 60; tick++) {
            assertFalse(cooldown.tryAcquire("player", tick));
        }
        assertTrue(cooldown.tryAcquire("player", 60));
    }

    @Test
    void tracksPlayersIndependentlyAndOnlyPrunesExpiredEntries() {
        var cooldown = new TickCooldown<String>(60);
        assertTrue(cooldown.tryAcquire("first", 0));
        assertTrue(cooldown.tryAcquire("second", 30));
        cooldown.removeExpired(60);
        assertTrue(cooldown.tryAcquire("first", 60));
        assertFalse(cooldown.tryAcquire("second", 89));
        assertTrue(cooldown.tryAcquire("second", 90));
    }

    @Test
    void rejectsANonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new TickCooldown<>(0));
        assertThrows(IllegalArgumentException.class, () -> new TickCooldown<>(-1));
    }
}
