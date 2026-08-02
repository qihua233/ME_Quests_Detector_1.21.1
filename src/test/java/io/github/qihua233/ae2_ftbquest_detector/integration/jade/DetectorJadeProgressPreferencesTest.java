package io.github.qihua233.ae2_ftbquest_detector.integration.jade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorJadeProgressPreferencesTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void clearPreferences() {
        DetectorJadeProgressPreferences.clear();
    }

    @Test
    void unknownPlayersKeepTheExistingEnabledBehavior() {
        assertTrue(DetectorJadeProgressPreferences.shouldCompute(PLAYER_ID));
    }

    @Test
    void disabledClientPreferenceSkipsTaskStatistics() {
        DetectorJadeProgressPreferences.update(PLAYER_ID, false);

        assertFalse(DetectorJadeProgressPreferences.shouldCompute(PLAYER_ID));
    }
}
