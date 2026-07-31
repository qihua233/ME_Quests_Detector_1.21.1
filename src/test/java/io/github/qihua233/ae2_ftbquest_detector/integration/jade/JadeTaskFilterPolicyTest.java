package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JadeTaskFilterPolicyTest {
    @Test
    void keepsAllTasksByDefault() {
        assertTrue(JadeTaskFilterPolicy.include(false, true, false, false));
    }

    @Test
    void ignoresHiddenTasksWhenConfigured() {
        assertFalse(JadeTaskFilterPolicy.include(false, false, true, false));
        assertTrue(JadeTaskFilterPolicy.include(true, false, true, false));
    }

    @Test
    void ignoresRepeatableTasksWhenConfigured() {
        assertFalse(JadeTaskFilterPolicy.include(true, true, false, true));
        assertTrue(JadeTaskFilterPolicy.include(true, false, false, true));
    }

    @Test
    void appliesBothFiltersTogether() {
        assertFalse(JadeTaskFilterPolicy.include(false, true, true, true));
        assertTrue(JadeTaskFilterPolicy.include(true, false, true, true));
    }
}
