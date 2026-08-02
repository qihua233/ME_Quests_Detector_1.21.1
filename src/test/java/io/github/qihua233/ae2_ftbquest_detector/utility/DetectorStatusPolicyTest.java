package io.github.qihua233.ae2_ftbquest_detector.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectorStatusPolicyTest {
    @Test
    void ownerStateHasPriorityOverNetworkConflict() {
        assertEquals(
                DetectorStatusPolicy.State.NO_OWNER_TEAM,
                DetectorStatusPolicy.resolve(
                        TeamOwnershipValidator.Status.NONE, true, false));
        assertEquals(
                DetectorStatusPolicy.State.INVALID_OWNER_TEAM,
                DetectorStatusPolicy.resolve(
                        TeamOwnershipValidator.Status.INVALID, true, false));
    }

    @Test
    void temporaryTeamStateIsNotPresentedAsAConflict() {
        assertEquals(
                DetectorStatusPolicy.State.TEMPORARILY_UNAVAILABLE,
                DetectorStatusPolicy.resolve(
                        TeamOwnershipValidator.Status.TEMPORARILY_UNAVAILABLE, true, false));
    }

    @Test
    void usableTeamUsesNetworkAndPowerState() {
        assertEquals(
                DetectorStatusPolicy.State.NETWORK_CONFLICT,
                DetectorStatusPolicy.resolve(TeamOwnershipValidator.Status.USABLE, true, true));
        assertEquals(
                DetectorStatusPolicy.State.OFFLINE,
                DetectorStatusPolicy.resolve(TeamOwnershipValidator.Status.USABLE, false, false));
        assertEquals(
                DetectorStatusPolicy.State.ACTIVE,
                DetectorStatusPolicy.resolve(TeamOwnershipValidator.Status.USABLE, false, true));
    }
}
