package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTransitionPolicyTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARTY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void partyDetectorFollowsLeavingOwner() {
        assertTrue(TeamTransitionPolicy.shouldFollowPlayer(
                PARTY_ID, false, true, true, PLAYER_ID
        ));
    }

    @Test
    void partyDetectorDoesNotFollowOrdinaryMember() {
        assertFalse(TeamTransitionPolicy.shouldFollowPlayer(
                PARTY_ID, false, true, false, PLAYER_ID
        ));
    }

    @Test
    void personalDetectorFollowsItsPlayerIntoParty() {
        assertTrue(TeamTransitionPolicy.shouldFollowPlayer(
                PLAYER_ID, true, false, true, PLAYER_ID
        ));
    }

    @Test
    void personalDetectorDoesNotFollowAnotherPlayer() {
        assertFalse(TeamTransitionPolicy.shouldFollowPlayer(
                PLAYER_ID, true, false, true, OTHER_PLAYER_ID
        ));
    }
}
