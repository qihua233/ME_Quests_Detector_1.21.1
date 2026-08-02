package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DetectorTeamMigrationStoreTest {
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final DetectorTeamMigrationStore.MigrationKey DETECTOR =
            new DetectorTeamMigrationStore.MigrationKey("minecraft:overworld", 42L);

    @Test
    void keepsPendingMigrationUntilItIsConsumed() {
        DetectorTeamMigrationStore.PendingMigrationLedger ledger =
                new DetectorTeamMigrationStore.PendingMigrationLedger();

        ledger.retain(DETECTOR, TEAM_ID);

        assertEquals(TEAM_ID, ledger.get(DETECTOR));
        ledger.remove(DETECTOR);
        assertNull(ledger.get(DETECTOR));
    }

    @Test
    void discardsMigrationsTargetingDeletedTeams() {
        DetectorTeamMigrationStore.PendingMigrationLedger ledger =
                new DetectorTeamMigrationStore.PendingMigrationLedger();
        DetectorTeamMigrationStore.MigrationKey otherDetector =
                new DetectorTeamMigrationStore.MigrationKey("minecraft:the_nether", 43L);

        ledger.retain(DETECTOR, TEAM_ID);
        ledger.retain(otherDetector, OTHER_TEAM_ID);

        ledger.discardTarget(TEAM_ID);

        assertNull(ledger.get(DETECTOR));
        assertEquals(OTHER_TEAM_ID, ledger.get(otherDetector));
    }
}
