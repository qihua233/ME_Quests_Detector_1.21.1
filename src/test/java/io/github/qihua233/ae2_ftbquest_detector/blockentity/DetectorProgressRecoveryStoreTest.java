package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectorProgressRecoveryStoreTest {
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void keepsTheHighestTargetForEachTaskAndTeam() {
        DetectorProgressRecoveryStore.PendingProgressLedger ledger =
                new DetectorProgressRecoveryStore.PendingProgressLedger(4);

        ledger.merge(TEAM_ID, 10L, 2L);
        ledger.merge(TEAM_ID, 10L, 5L);
        ledger.merge(OTHER_TEAM_ID, 10L, 3L);

        assertEquals(1, ledger.copyFor(TEAM_ID).size());
        assertEquals(5L, ledger.copyFor(TEAM_ID).getFirst().targetProgress());
        assertEquals(2, ledger.size());
    }

    @Test
    void evictsOldestEntryWhenTheBoundIsReached() {
        DetectorProgressRecoveryStore.PendingProgressLedger ledger =
                new DetectorProgressRecoveryStore.PendingProgressLedger(2);

        ledger.merge(TEAM_ID, 10L, 1L);
        ledger.merge(TEAM_ID, 11L, 1L);
        ledger.merge(TEAM_ID, 12L, 1L);

        assertEquals(0, ledger.copyFor(TEAM_ID).stream()
                .filter(progress -> progress.taskId() == 10L).count());
        assertEquals(2, ledger.size());
    }

    @Test
    void doesNotRemoveARecoveryEntryAheadOfTheCommittedProgress() {
        DetectorProgressRecoveryStore.PendingProgressLedger ledger =
                new DetectorProgressRecoveryStore.PendingProgressLedger(4);

        ledger.merge(TEAM_ID, 10L, 8L);
        ledger.removeIfAtMost(TEAM_ID, 10L, 7L);

        assertEquals(8L, ledger.copyFor(TEAM_ID).getFirst().targetProgress());

        ledger.removeIfAtMost(TEAM_ID, 10L, 8L);

        assertEquals(0, ledger.copyFor(TEAM_ID).size());
    }
}
